package com.jwoglom.controlx2.build.compose

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment as PaparazziEnvironment
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import androidx.compose.runtime.Composer
import androidx.compose.runtime.reflect.ComposableMethod
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import com.android.ide.common.rendering.api.SessionParams
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.TypeNotPresentException
import java.net.URLClassLoader
import java.time.Instant
import java.util.Locale
import java.util.Enumeration
import java.util.stream.Stream
import javax.imageio.ImageIO
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.junit.runner.Description
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.sequences.asSequence
import kotlin.sequences.sequence

abstract class RenderComposePreviewsTask : DefaultTask() {
    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val modulePath: Property<String>

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedResources: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledRClassJar: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    abstract val layoutlibJar: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val resourcePackageNames: ListProperty<String>

    @get:Input
    abstract val compileSdkVersion: Property<Int>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleAssets: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraryResources: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraryAssets: ConfigurableFileCollection

    @TaskAction
    fun render() {
        val metadataSource = metadataFile.get().asFile
        if (!metadataSource.exists()) {
            throw IllegalStateException(
                "Expected metadata file at ${metadataSource.absolutePath} for ${modulePath.get()}#${variantName.get()}"
            )
        }

        val outputDir = outputDirectory.get().asFile
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()

        val manifestOutput = manifestFile.orNull?.asFile
            ?: File(outputDir, "manifest.json").also { candidate ->
                manifestFile.set(project.objects.fileProperty().fileValue(candidate))
            }

        val metadataDocument = parseMetadata(metadataSource)
        val environment = buildRenderingEnvironment()
        reportEnvironment(metadataDocument, environment)
        val previews = metadataDocument.previews

        if (previews.isEmpty()) {
            val manifest = writeManifest(
                manifestOutput,
                metadataDocument,
                environment,
                emptyList()
            )
            logger.lifecycle(
                "No Compose previews discovered for ${metadataDocument.modulePath}#${metadataDocument.variant}. " +
                    "Wrote empty manifest to ${manifest.toPrettyPath(project.rootDir)}."
            )
            return
        }

        if (System.getProperty(HEADLESS_PROPERTY) == null) {
            System.setProperty(HEADLESS_PROPERTY, "true")
        }

        val packageNameValue = packageName.orNull?.takeIf { it.isNotBlank() }
            ?: metadataDocument.modulePath.trim(':', '/').replace(':', '.').ifBlank { project.name }
        val resourcePackages = resourcePackageNames.orNull
            ?.mapNotNull { it.takeIf(String::isNotBlank) }
            ?.ifEmpty { null }
            ?: listOf(packageNameValue)
        val compileSdk = compileSdkVersion.orNull ?: DEFAULT_COMPILE_SDK
        val paparazziEnvironment = environment.toPaparazziEnvironment(
            packageName = packageNameValue,
            compileSdk = compileSdk,
            resourcePackages = resourcePackages,
            appTestDir = outputDir
        )
        val classLoader = createPreviewClassLoader(environment)

        val renderedPreviews = mutableListOf<RenderedPreview>()
        val usedFileNames = mutableSetOf<String>()

        try {
            previews.forEach { preview ->
                val resolution = resolveComposable(preview, classLoader)
                val invocationSpecs = when (resolution) {
                    is ComposableResolution.Success -> preparePreviewInvocations(
                        preview = preview,
                        resolved = resolution.composable,
                        classLoader = classLoader
                    )
                    is ComposableResolution.Unsupported -> listOf(
                        PreviewInvocationSpec(
                            parameterInstanceIndex = null,
                            args = emptyList(),
                            renderedParameters = emptyList(),
                            label = null,
                            skipReason = resolution.reason
                        )
                    )
                }

                invocationSpecs.forEach { invocation ->
                    var dedupeIndex = 0
                    var relativePath: String
                    while (true) {
                        relativePath = buildOutputPath(
                            preview = preview,
                            parameterIndex = invocation.parameterInstanceIndex,
                            duplicateIndex = dedupeIndex,
                            parameterLabel = invocation.label
                        )
                        if (usedFileNames.add(relativePath)) {
                            break
                        }
                        dedupeIndex++
                    }

                    val imageFile = File(outputDir, relativePath)
                    imageFile.parentFile?.mkdirs()

                    val renderResult = when {
                        invocation.skipReason != null -> RenderResult.failure(invocation.skipReason)
                        resolution !is ComposableResolution.Success -> RenderResult.failure(
                            (resolution as ComposableResolution.Unsupported).reason
                        )
                        else -> renderPreviewInvocation(
                            preview = preview,
                            metadata = metadataDocument,
                            environment = environment,
                            paparazziEnvironment = paparazziEnvironment,
                            resolved = resolution.composable,
                            classLoader = classLoader,
                            args = invocation.args.toTypedArray(),
                            imageFile = imageFile
                        )
                    }

                    if (!renderResult.success) {
                        writePlaceholderImage(imageFile, preview, metadataDocument, renderResult.errorMessage)
                    }

                    val normalizedPath = outputDir.toPath().relativize(imageFile.toPath()).toString()
                        .replace(File.separatorChar, '/')

                    renderedPreviews += RenderedPreview(
                        id = preview.id,
                        fqcn = preview.fqcn,
                        packageName = preview.packageName,
                        simpleClassName = preview.simpleClassName,
                        methodName = preview.methodName,
                        previewIndex = preview.previewIndex,
                        displayName = preview.displayName,
                        configurationSummary = preview.configurationSummary,
                        source = preview.source,
                        annotation = preview.annotation,
                        relativeOutputPath = normalizedPath,
                        parameterInstanceIndex = invocation.parameterInstanceIndex,
                        parameters = invocation.renderedParameters,
                        placeholder = !renderResult.success,
                        renderError = invocation.skipReason ?: renderResult.errorMessage
                    )
                }
            }
        } finally {
            runCatching { classLoader.close() }
        }

        val manifest = writeManifest(
            manifestOutput,
            metadataDocument,
            environment,
            renderedPreviews
        )

        val placeholderCount = renderedPreviews.count { it.placeholder }
        val placeholderSuffix = if (placeholderCount > 0) " (placeholders: $placeholderCount)" else ""
        logger.lifecycle(
            "Generated ${renderedPreviews.size} Compose preview image(s)$placeholderSuffix for " +
                "${metadataDocument.modulePath}#${metadataDocument.variant}. Manifest: ${manifest.toPrettyPath(project.rootDir)}"
        )
    }

    private fun extractProviderClassName(annotation: Annotation): String? {
        val annotationClass = annotation.annotationClass.java
        val providerMethod = runCatching { annotationClass.getMethod("provider") }.getOrNull() ?: return null
        return try {
            when (val value = providerMethod.invoke(annotation)) {
                is Class<*> -> value.name
                is KClass<*> -> value.qualifiedName
                else -> null
            }
        } catch (throwable: Throwable) {
            val target = (throwable as? InvocationTargetException)?.targetException
            if (target is TypeNotPresentException) {
                target.typeName()
            } else {
                null
            }
        }
    }

    private fun extractPreviewParameterLimit(annotation: Annotation): Int {
        val annotationClass = annotation.annotationClass.java
        val limitMethod = runCatching { annotationClass.getMethod("limit") }.getOrNull() ?: return Int.MAX_VALUE
        val value = runCatching { limitMethod.invoke(annotation) as? Int }.getOrNull()
        return value?.takeIf { it > 0 } ?: Int.MAX_VALUE
    }

    private fun preparePreviewInvocations(
        preview: PreviewMetadata,
        resolved: ResolvedComposable,
        classLoader: URLClassLoader
    ): List<PreviewInvocationSpec> {
        if (resolved.previewParameters.isEmpty()) {
            return listOf(
                PreviewInvocationSpec(
                    parameterInstanceIndex = null,
                    args = emptyList(),
                    renderedParameters = emptyList(),
                    label = null,
                    skipReason = null
                )
            )
        }

        val providerValues = mutableListOf<PreviewParameterValues>()
        for (binding in resolved.previewParameters) {
            when (val result = instantiatePreviewParameterValues(binding, classLoader)) {
                is PreviewParameterResolution.Success -> providerValues += result.values
                is PreviewParameterResolution.Failure -> {
                    return listOf(
                        PreviewInvocationSpec(
                            parameterInstanceIndex = null,
                            args = emptyList(),
                            renderedParameters = emptyList(),
                            label = null,
                            skipReason = result.reason
                        )
                    )
                }
            }
        }

        if (providerValues.isEmpty()) {
            return listOf(
                PreviewInvocationSpec(
                    parameterInstanceIndex = null,
                    args = emptyList(),
                    renderedParameters = emptyList(),
                    label = null,
                    skipReason = "No preview parameter providers resolved for ${preview.id}"
                )
            )
        }

        val invocationCount = providerValues.minOfOrNull { it.values.size } ?: 0
        if (invocationCount <= 0) {
            return listOf(
                PreviewInvocationSpec(
                    parameterInstanceIndex = null,
                    args = emptyList(),
                    renderedParameters = emptyList(),
                    label = null,
                    skipReason = "Preview parameter providers returned no values for ${preview.id}"
                )
            )
        }

        val totalParameters = resolved.parameterCount
        val invocations = mutableListOf<PreviewInvocationSpec>()
        for (index in 0 until invocationCount) {
            val args = MutableList<Any?>(totalParameters) { null }
            val rendered = mutableListOf<RenderedParameter>()

            providerValues.forEach { provider ->
                val valueEntry = provider.values[index]
                args[provider.binding.index] = valueEntry.value
                rendered += RenderedParameter(
                    index = provider.binding.index,
                    name = provider.binding.name,
                    providerClassName = provider.binding.providerClassName,
                    valueSummary = summariseParameterValue(valueEntry.value),
                    valueClassName = valueEntry.value?.javaClass?.name,
                    displayName = valueEntry.displayName
                )
            }

            if (args.any { it == null }) {
                return listOf(
                    PreviewInvocationSpec(
                        parameterInstanceIndex = null,
                        args = emptyList(),
                        renderedParameters = emptyList(),
                        label = null,
                        skipReason = "Preview parameters missing values for ${preview.id}"
                    )
                )
            }

            val label = buildParameterLabel(rendered)
            invocations += PreviewInvocationSpec(
                parameterInstanceIndex = index + 1,
                args = args,
                renderedParameters = rendered,
                label = label,
                skipReason = null
            )
        }

        return invocations
    }

    private fun instantiatePreviewParameterValues(
        binding: PreviewParameterBinding,
        classLoader: URLClassLoader
    ): PreviewParameterResolution {
        val providerClass = try {
            classLoader.loadClass(binding.providerClassName)
        } catch (exception: ClassNotFoundException) {
            val summary = "Preview parameter provider ${binding.providerClassName} not found"
            logger.warn(summary)
            return PreviewParameterResolution.Failure(summary)
        }

        val constructor = try {
            providerClass.getDeclaredConstructor().apply { isAccessible = true }
        } catch (exception: NoSuchMethodException) {
            val summary = "Preview parameter provider ${binding.providerClassName} requires a no-arg constructor"
            logger.warn(summary, exception)
            return PreviewParameterResolution.Failure(summary)
        }

        val instance = try {
            constructor.newInstance()
        } catch (exception: Throwable) {
            val summary = "Unable to instantiate preview parameter provider ${binding.providerClassName}: ${exception.renderSummary()}"
            logger.warn(summary, exception)
            return PreviewParameterResolution.Failure(summary)
        }

        val valuesAccessor = try {
            providerClass.getMethod("getValues")
        } catch (exception: NoSuchMethodException) {
            val summary = "Provider ${binding.providerClassName} does not expose a getValues() method"
            logger.warn(summary, exception)
            return PreviewParameterResolution.Failure(summary)
        }

        val sequence: Sequence<Any?> = try {
            val result = valuesAccessor.invoke(instance)
            coerceProviderValuesToSequence(result)
                ?: run {
                    val typeName = result?.javaClass?.name ?: "unknown"
                    val summary = "Provider ${binding.providerClassName} returned unsupported values type $typeName"
                    logger.warn(summary)
                    return PreviewParameterResolution.Failure(summary)
                }
        } catch (exception: Throwable) {
            val summary = "Provider ${binding.providerClassName} failed to supply values: ${exception.renderSummary()}"
            logger.warn(summary, exception)
            return PreviewParameterResolution.Failure(summary)
        }

        val displayAccessor = runCatching {
            providerClass.getMethod("getDisplayName", Int::class.javaPrimitiveType)
        }.getOrNull()

        val limit = if (binding.limit in 1 until Int.MAX_VALUE) binding.limit else Int.MAX_VALUE
        val iterator = sequence.iterator()
        val values = mutableListOf<PreviewValueInstance>()
        var index = 0
        while (iterator.hasNext() && index < limit) {
            val value = iterator.next()
            val display = displayAccessor?.let { accessor ->
                try {
                    accessor.invoke(instance, index) as? CharSequence
                } catch (exception: Throwable) {
                    logger.warn(
                        "Provider ${binding.providerClassName} failed to supply display name for index $index: ${exception.renderSummary()}",
                        exception
                    )
                    null
                }
            }?.toString()
            values += PreviewValueInstance(value, display)
            index++
        }

        if (values.isEmpty()) {
            val summary = "Provider ${binding.providerClassName} produced no preview values"
            logger.warn(summary)
            return PreviewParameterResolution.Failure(summary)
        }

        return PreviewParameterResolution.Success(PreviewParameterValues(binding, values))
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerceProviderValuesToSequence(result: Any?): Sequence<Any?>? {
        if (result == null) return emptySequence()
        return when (result) {
            is Sequence<*> -> result as Sequence<Any?>
            is Iterable<*> -> (result as Iterable<Any?>).asSequence()
            is Iterator<*> -> (result as Iterator<Any?>).asSequence()
            is Array<*> -> (result as Array<Any?>).asSequence()
            is Enumeration<*> -> enumerationAsSequence(result)
            is Stream<*> -> streamToSequence(result)
            else -> when {
                result.javaClass.isArray -> primitiveArrayAsSequence(result)
                else -> null
            }
        }
    }

    private fun streamToSequence(stream: Stream<*>): Sequence<Any?> {
        val values = try {
            @Suppress("UNCHECKED_CAST")
            val iterator = stream.iterator() as Iterator<Any?>
            iterator.asSequence().toList()
        } finally {
            try {
                stream.close()
            } catch (_: Exception) {
                // Swallow close failures so the caller receives the original render error instead.
            }
        }
        return values.asSequence()
    }

    private fun enumerationAsSequence(enumeration: Enumeration<*>): Sequence<Any?> = sequence {
        while (enumeration.hasMoreElements()) {
            yield(enumeration.nextElement())
        }
    }

    private fun primitiveArrayAsSequence(array: Any): Sequence<Any?> = sequence {
        val length = java.lang.reflect.Array.getLength(array)
        for (index in 0 until length) {
            yield(java.lang.reflect.Array.get(array, index))
        }
    }

    private fun buildParameterLabel(parameters: List<RenderedParameter>): String? {
        if (parameters.isEmpty()) return null
        val parts = parameters.mapNotNull { parameter ->
            val raw = parameter.displayName?.takeIf { it.isNotBlank() } ?: parameter.valueSummary
            sanitizeSegment(raw)
        }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val combined = parts.joinToString(separator = "-")
        return combined.take(MAX_SEGMENT_LENGTH).ifBlank { null }
    }

    private fun summariseParameterValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is CharSequence -> {
                val collapsed = value.toString().trim()
                val shortened = collapsed.replace(WHITESPACE_REGEX, " ").take(MAX_SOURCE_SEGMENT)
                "\"${shortened.ifBlank { "" }}\""
            }
            else -> {
                val collapsed = value.toString().trim().replace(WHITESPACE_REGEX, " ")
                collapsed.take(MAX_SOURCE_SEGMENT).ifBlank { value.javaClass.simpleName ?: "value" }
            }
        }
    }

    private fun buildRenderingEnvironment(): RenderingEnvironment {
        val runtimeEntries = runtimeClasspath.files.toList()
        val (runtimePresent, runtimeMissing) = runtimeEntries.partition { it.exists() }

        val mergedResourceEntries = mergedResources.files.toList()
        val (mergedPresent, mergedMissing) = mergedResourceEntries.partition { it.exists() }

        val rClassEntries = compiledRClassJar.files.toList()
        val (rClassPresent, rClassMissing) = rClassEntries.partition { it.exists() }

        val moduleAssetEntries = moduleAssets.files.toList()
        val (moduleAssetsPresent, moduleAssetsMissing) = moduleAssetEntries.partition { it.exists() }

        val libraryResourceEntries = libraryResources.files.toList()
        val (libraryResourcesPresent, libraryResourcesMissing) = libraryResourceEntries.partition { it.exists() }

        val libraryAssetEntries = libraryAssets.files.toList()
        val (libraryAssetsPresent, libraryAssetsMissing) = libraryAssetEntries.partition { it.exists() }

        val layoutlibFile = layoutlibJar.orNull?.asFile
        val resolvedLayoutlib = layoutlibFile?.takeIf { it.exists() }
        val missingLayoutlib = if (layoutlibFile != null && !layoutlibFile.exists()) layoutlibFile else null

        return RenderingEnvironment(
            runtimeClasspath = runtimePresent,
            missingRuntimeClasspath = runtimeMissing,
            mergedResources = mergedPresent,
            missingMergedResources = mergedMissing,
            compiledRClassJars = rClassPresent,
            missingCompiledRClassJars = rClassMissing,
            moduleAssets = moduleAssetsPresent,
            missingModuleAssets = moduleAssetsMissing,
            libraryResources = libraryResourcesPresent,
            missingLibraryResources = libraryResourcesMissing,
            libraryAssets = libraryAssetsPresent,
            missingLibraryAssets = libraryAssetsMissing,
            layoutlibJar = resolvedLayoutlib,
            missingLayoutlib = missingLayoutlib
        )
    }

    private fun reportEnvironment(metadata: MetadataDocument, environment: RenderingEnvironment) {
        logger.lifecycle(
            "Compose preview environment for ${metadata.modulePath}#${metadata.variant}: " +
                "${environment.runtimeClasspath.size} runtime classpath entries, " +
                "${environment.mergedResources.size} merged resource directories, " +
                "${environment.compiledRClassJars.size} R class jars."
        )

        if (environment.missingRuntimeClasspath.isNotEmpty()) {
            logger.warn(
                "Missing runtime classpath entries for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingRuntimeClasspath.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.missingMergedResources.isNotEmpty()) {
            logger.warn(
                "Missing merged resource inputs for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingMergedResources.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.missingCompiledRClassJars.isNotEmpty()) {
            logger.warn(
                "Missing compiled R class jars for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingCompiledRClassJars.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.missingModuleAssets.isNotEmpty()) {
            logger.warn(
                "Missing module asset directories for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingModuleAssets.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.missingLibraryResources.isNotEmpty()) {
            logger.warn(
                "Missing library resource inputs for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingLibraryResources.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.missingLibraryAssets.isNotEmpty()) {
            logger.warn(
                "Missing library asset directories for ${metadata.modulePath}#${metadata.variant}:\n" +
                    environment.missingLibraryAssets.joinToString(separator = "\n") { " - ${it.toPrettyPath(project.rootDir)}" }
            )
        }

        if (environment.layoutlibJar == null) {
            environment.missingLayoutlib?.let { candidate ->
                logger.warn(
                    "Layoutlib jar not found for ${metadata.modulePath}#${metadata.variant}. " +
                        "Expected at ${candidate.toPrettyPath(project.rootDir)}"
                )
            }
        }
    }

    private fun parseMetadata(file: File): MetadataDocument {
        val jsonText = file.readText()
        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.decodeFromString<JsonObject>(jsonText)
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Unable to parse Compose preview metadata at ${file.absolutePath}",
                exception
            )
        }

        val module = root["modulePath"]?.jsonPrimitive?.content ?: modulePath.get()
        val variant = root["variant"]?.jsonPrimitive?.content ?: variantName.get()
        val generatedAt = root["generatedAt"]?.jsonPrimitive?.content ?: ""

        val previewsArray = root["previews"]?.jsonArray ?: JsonArray(emptyList())
        val previews = previewsArray.mapNotNull { element ->
            element.jsonObject.let(::parsePreview)
        }

        return MetadataDocument(
            modulePath = module,
            variant = variant,
            metadataGeneratedAt = generatedAt,
            previews = previews,
            metadataFile = file
        )
    }

    private fun parsePreview(json: JsonObject): PreviewMetadata? {
        val id = json["id"]?.jsonPrimitive?.content ?: return null
        val fqcn = json["fqcn"]?.jsonPrimitive?.content ?: return null
        val methodName = json["methodName"]?.jsonPrimitive?.content ?: return null
        val simpleClass = json["simpleClassName"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?: fqcn.substringAfterLast('.', fqcn)
        val packageName = json["packageName"]?.jsonPrimitive?.content ?: fqcn.substringBeforeLast('.', "")
        val source = json["source"]?.jsonPrimitive?.content ?: ""
        val annotation = json["annotation"]?.jsonObject ?: JsonObject(emptyMap())

        return PreviewMetadata(
            id = id,
            fqcn = fqcn,
            packageName = packageName,
            simpleClassName = simpleClass,
            methodName = methodName,
            previewIndex = extractPreviewIndex(id),
            source = source,
            annotation = annotation
        )
    }

    private fun extractPreviewIndex(id: String): Int {
        val start = id.lastIndexOf('[')
        val end = id.lastIndexOf(']')
        if (start != -1 && end != -1 && end > start) {
            val value = id.substring(start + 1, end)
            return value.toIntOrNull()?.takeIf { it > 0 } ?: 1
        }
        return 1
    }

    private fun buildOutputPath(
        preview: PreviewMetadata,
        parameterIndex: Int?,
        duplicateIndex: Int,
        parameterLabel: String?
    ): String {
        val segments = linkedSetOf<String>()
        segments += sanitizeSegment(preview.packageName.replace('.', '-'))
        segments += sanitizeSegment(preview.simpleClassName)
        val methodSegment = sanitizeSegment(preview.methodName)
        if (methodSegment.isNotEmpty()) {
            segments += methodSegment
        }
        val displaySegment = sanitizeSegment(preview.displayName)
        if (displaySegment.isNotEmpty() && displaySegment != methodSegment) {
            segments += displaySegment
        }
        val groupSegment = preview.annotation["group"]?.jsonPrimitive?.contentOrNull
            ?.let(::sanitizeSegment)
        if (!groupSegment.isNullOrEmpty()) {
            segments += groupSegment
        }

        val base = segments.filter { it.isNotEmpty() }
            .joinToString(separator = "_")
            .ifEmpty { "preview" }

        val digest = stableDigest(preview.id)
        val indexSuffix = "p${preview.previewIndex.toString().padStart(2, '0')}"
        val dedupeSuffix = if (duplicateIndex > 0) "d${duplicateIndex.toString().padStart(2, '0')}" else null
        val suffixParts = mutableListOf(digest, indexSuffix)
        parameterIndex?.let { suffixParts += "param${it.toString().padStart(2, '0')}" }
        parameterLabel?.takeIf { it.isNotBlank() }?.let { suffixParts += sanitizeSegment(it) }
        dedupeSuffix?.let { suffixParts += it }
        val suffix = suffixParts.joinToString(separator = "_")

        val maxBaseLength = (MAX_FILE_STEM - suffix.length - 1).coerceAtLeast(MIN_BASE_LENGTH)
        val trimmedBase = base.take(maxBaseLength).trimEnd('_')
        val resolvedBase = if (trimmedBase.isNotEmpty()) trimmedBase else digest

        return "${resolvedBase}_$suffix.png"
    }

    private fun sanitizeSegment(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val collapsed = input.trim().replace(WHITESPACE_REGEX, " ")
        val sanitized = collapsed.replace(NON_FILENAME_REGEX, "_")
        return sanitized.replace(UNDERSCORE_RUN_REGEX, "_")
            .trim('_', '-', '.')
            .take(MAX_SEGMENT_LENGTH)
    }

    private fun writePlaceholderImage(
        file: File,
        preview: PreviewMetadata,
        metadata: MetadataDocument,
        errorMessage: String?
    ) {
        val width = PLACEHOLDER_WIDTH
        val height = PLACEHOLDER_HEIGHT
        val background = chooseBackgroundColor(preview.id)
        val textColor = chooseTextColor(background)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.use { g ->
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g.color = background
            g.fillRect(0, 0, width, height)

            val borderColor = blendColors(background, textColor, 0.2f)
            g.color = borderColor
            g.drawRoundRect(OUTER_MARGIN, OUTER_MARGIN, width - 2 * OUTER_MARGIN, height - 2 * OUTER_MARGIN, 32, 32)

            g.color = textColor
            val textBlocks = buildPlaceholderText(preview, metadata, errorMessage)
            drawTextBlocks(g, textBlocks, width, height, textColor, background)
        }

        ImageIO.write(image, "PNG", file)
    }

    private fun buildPlaceholderText(
        preview: PreviewMetadata,
        metadata: MetadataDocument,
        errorMessage: String?
    ): PlaceholderText {
        val headerLines = wrapLine(preview.displayName, HEADER_MAX_CHARS)
            .ifEmpty { listOf("Preview") }

        val details = mutableListOf<String>()
        val methodLine = "${preview.simpleClassName}.${preview.methodName} (#${preview.previewIndex})"
        details.addAll(wrapLine(methodLine, BODY_MAX_CHARS))

        preview.annotation["group"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { group ->
                details.addAll(wrapLine("Group: $group", BODY_MAX_CHARS))
            }

        preview.configurationSummary.takeIf { it.isNotBlank() }?.let { summary ->
            details.addAll(wrapLine("Config: $summary", BODY_MAX_CHARS))
        }

        val moduleLine = "Module ${metadata.modulePath} • ${metadata.variant}"
        details.addAll(wrapLine(moduleLine, BODY_MAX_CHARS))

        summariseSource(preview.source).takeIf { it.isNotBlank() }?.let { source ->
            details.addAll(wrapLine("Source: $source", BODY_MAX_CHARS))
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            details.addAll(wrapLine("Render error: $message", BODY_MAX_CHARS))
        }

        return PlaceholderText(headerLines, details)
    }

    private fun drawTextBlocks(
        graphics: Graphics2D,
        text: PlaceholderText,
        width: Int,
        height: Int,
        textColor: Color,
        background: Color
    ) {
        val headerFont = Font("SansSerif", Font.BOLD, 34)
        val bodyFont = Font("SansSerif", Font.PLAIN, 22)
        val margin = INNER_MARGIN
        var yPosition = margin + headerFont.size

        graphics.font = headerFont
        graphics.color = textColor
        text.headerLines.forEach { line ->
            val metrics = graphics.fontMetrics
            if (yPosition + metrics.descent >= height - margin) {
                return
            }
            graphics.drawString(line, margin, yPosition)
            yPosition += metrics.height
        }

        if (text.bodyLines.isNotEmpty()) {
            val separatorY = yPosition + SEPARATOR_PADDING
            if (separatorY + 1 < height - margin) {
                val accent = blendColors(textColor, background, 0.25f)
                val originalColor = graphics.color
                graphics.color = accent
                graphics.fillRect(margin, separatorY, width - margin * 2, 2)
                graphics.color = originalColor
            }
            yPosition += SEPARATOR_PADDING + 12
        }

        graphics.font = bodyFont
        graphics.color = textColor
        text.bodyLines.forEach { line ->
            val metrics = graphics.fontMetrics
            if (yPosition + metrics.descent >= height - margin) {
                return
            }
            graphics.drawString(line, margin, yPosition)
            yPosition += metrics.height
        }
    }

    private fun wrapLine(text: String, maxChars: Int): List<String> {
        val normalized = text.trim().replace(WHITESPACE_REGEX, " ")
        if (normalized.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var current = StringBuilder()

        normalized.split(' ').forEach { word ->
            if (word.length > maxChars) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                word.chunked(maxChars).forEach { chunk -> result.add(chunk) }
                return@forEach
            }

            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length > maxChars) {
                result.add(current.toString())
                current = StringBuilder(word)
            } else {
                current.append(' ').append(word)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    private fun summariseSource(source: String): String {
        if (source.isBlank()) return ""
        val normalized = source.replace('\\', '/').trim()
        val condensed = when {
            normalized.contains("!/") -> normalized.substringAfterLast("!/")
            else -> normalized.substringAfterLast('/')
        }
        val truncated = if (condensed.length > MAX_SOURCE_SEGMENT) {
            "…" + condensed.takeLast(MAX_SOURCE_SEGMENT)
        } else {
            condensed
        }
        return truncated
    }

    private fun chooseBackgroundColor(id: String): Color {
        val hash = id.hashCode()
        val red = 96 + ((hash ushr 16) and 0x7F)
        val green = 96 + ((hash ushr 8) and 0x7F)
        val blue = 96 + (hash and 0x7F)
        return Color(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
    }

    private fun chooseTextColor(background: Color): Color {
        val luminance = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
        return if (luminance < 0.55) Color(248, 248, 248) else Color(30, 30, 30)
    }

    private fun blendColors(foreground: Color, background: Color, ratio: Float): Color {
        val weight = ratio.coerceIn(0f, 1f)
        val inverse = 1f - weight
        val r = (foreground.red * weight + background.red * inverse).toInt().coerceIn(0, 255)
        val g = (foreground.green * weight + background.green * inverse).toInt().coerceIn(0, 255)
        val b = (foreground.blue * weight + background.blue * inverse).toInt().coerceIn(0, 255)
        return Color(r, g, b)
    }

    private fun Graphics2D.use(block: (Graphics2D) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }

    private fun writeManifest(
        manifestFile: File,
        metadata: MetadataDocument,
        environment: RenderingEnvironment,
        previews: List<RenderedPreview>
    ): File {
        val renderTimestamp = Instant.now().toString()
        val previewElements = JsonArray(previews.map { it.toJsonElement() })
        val json = Json { prettyPrint = true }
        val placeholdersOnly = previews.all { it.placeholder }

        val manifest = buildJsonObject {
            put("status", JsonPrimitive("ok"))
            put("modulePath", JsonPrimitive(metadata.modulePath))
            put("variant", JsonPrimitive(metadata.variant))
            put("renderedAt", JsonPrimitive(renderTimestamp))
            put("metadataGeneratedAt", JsonPrimitive(metadata.metadataGeneratedAt))
            put("metadataFile", JsonPrimitive(metadata.metadataFile.toPrettyPath(project.rootDir)))
            put("previewCount", JsonPrimitive(previews.size))
            put("metadataPreviewCount", JsonPrimitive(metadata.previews.size))
            put("placeholders", JsonPrimitive(placeholdersOnly))
            put("environment", environment.toJson(project.rootDir))
            put("previews", previewElements)
        }

        manifestFile.parentFile?.mkdirs()
        manifestFile.writeText(json.encodeToString(manifest))
        return manifestFile
    }

    private fun RenderedPreview.toJsonElement(): JsonElement = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("fqcn", JsonPrimitive(fqcn))
        put("packageName", JsonPrimitive(packageName))
        put("simpleClassName", JsonPrimitive(simpleClassName))
        put("methodName", JsonPrimitive(methodName))
        put("previewIndex", JsonPrimitive(previewIndex))
        put("displayName", JsonPrimitive(displayName))
        put("configurationSummary", JsonPrimitive(configurationSummary))
        put("output", JsonPrimitive(relativeOutputPath))
        put("source", JsonPrimitive(source))
        put("annotation", annotation)
        parameterInstanceIndex?.let { put("parameterInstanceIndex", JsonPrimitive(it)) }
        if (parameters.isNotEmpty()) {
            put("parameters", buildJsonArray { parameters.forEach { add(it.toJsonElement()) } })
        }
        put("placeholder", JsonPrimitive(placeholder))
        renderError?.takeIf { it.isNotBlank() }?.let { put("renderError", JsonPrimitive(it)) }
    }

    private fun RenderedParameter.toJsonElement(): JsonElement = buildJsonObject {
        put("index", JsonPrimitive(index))
        name?.takeIf { it.isNotBlank() }?.let { put("name", JsonPrimitive(it)) }
        put("providerClass", JsonPrimitive(providerClassName))
        displayName?.takeIf { it.isNotBlank() }?.let { put("displayName", JsonPrimitive(it)) }
        put("valueSummary", JsonPrimitive(valueSummary))
        valueClassName?.takeIf { it.isNotBlank() }?.let { put("valueClass", JsonPrimitive(it)) }
    }

    private fun stableDigest(id: String): String {
        val hash = id.hashCode()
        return String.format(Locale.US, "%08x", hash)
    }

    private data class MetadataDocument(
        val modulePath: String,
        val variant: String,
        val metadataGeneratedAt: String,
        val previews: List<PreviewMetadata>,
        val metadataFile: File
    )

    private data class PreviewMetadata(
        val id: String,
        val fqcn: String,
        val packageName: String,
        val simpleClassName: String,
        val methodName: String,
        val previewIndex: Int,
        val source: String,
        val annotation: JsonObject
    ) {
        val displayName: String
            get() = annotation["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: methodName

        val configurationSummary: String
            get() {
                val interesting = annotation.entries
                    .filter { (key, _) -> key != "name" && key != "group" }
                    .sortedBy { it.key }
                if (interesting.isEmpty()) return ""
                return interesting.joinToString(separator = ", ") { (key, value) ->
                    "$key=${value.toSummaryString()}"
                }
            }
    }

    private data class PlaceholderText(
        val headerLines: List<String>,
        val bodyLines: List<String>
    )

    private data class RenderingEnvironment(
        val runtimeClasspath: List<File>,
        val missingRuntimeClasspath: List<File>,
        val mergedResources: List<File>,
        val missingMergedResources: List<File>,
        val compiledRClassJars: List<File>,
        val missingCompiledRClassJars: List<File>,
        val moduleAssets: List<File>,
        val missingModuleAssets: List<File>,
        val libraryResources: List<File>,
        val missingLibraryResources: List<File>,
        val libraryAssets: List<File>,
        val missingLibraryAssets: List<File>,
        val layoutlibJar: File?,
        val missingLayoutlib: File?
    ) {
        fun toJson(root: File): JsonObject = buildJsonObject {
            put("runtimeClasspathPresent", runtimeClasspath.toJsonArray(root))
            put("runtimeClasspathMissing", missingRuntimeClasspath.toJsonArray(root))
            put("mergedResourcesPresent", mergedResources.toJsonArray(root))
            put("mergedResourcesMissing", missingMergedResources.toJsonArray(root))
            put("compiledRClassJarsPresent", compiledRClassJars.toJsonArray(root))
            put("compiledRClassJarsMissing", missingCompiledRClassJars.toJsonArray(root))
            put("moduleAssetsPresent", moduleAssets.toJsonArray(root))
            put("moduleAssetsMissing", missingModuleAssets.toJsonArray(root))
            put("libraryResourcesPresent", libraryResources.toJsonArray(root))
            put("libraryResourcesMissing", missingLibraryResources.toJsonArray(root))
            put("libraryAssetsPresent", libraryAssets.toJsonArray(root))
            put("libraryAssetsMissing", missingLibraryAssets.toJsonArray(root))
            layoutlibJar?.let { put("layoutlibJar", JsonPrimitive(it.toPrettyPath(root))) }
            missingLayoutlib?.let { put("layoutlibJarMissing", JsonPrimitive(it.toPrettyPath(root))) }
        }

        fun toPaparazziEnvironment(
            packageName: String,
            compileSdk: Int,
            resourcePackages: List<String>,
            appTestDir: File
        ): PaparazziEnvironment = PaparazziEnvironment(
            appTestDir = appTestDir.absolutePath,
            packageName = packageName,
            compileSdkVersion = compileSdk,
            resourcePackageNames = resourcePackages,
            localResourceDirs = mergedResources.map { it.absolutePath },
            moduleResourceDirs = mergedResources.map { it.absolutePath },
            libraryResourceDirs = libraryResources.map { it.absolutePath },
            allModuleAssetDirs = moduleAssets.map { it.absolutePath },
            libraryAssetDirs = libraryAssets.map { it.absolutePath }
        )
    }

    private fun createPreviewClassLoader(environment: RenderingEnvironment): URLClassLoader {
        val classpath = (environment.runtimeClasspath + environment.compiledRClassJars)
            .filter(File::exists)
            .distinctBy { it.absolutePath }
            .map { it.toURI().toURL() }
            .toTypedArray()
        return URLClassLoader(classpath, javaClass.classLoader)
    }

    private fun renderPreviewInvocation(
        preview: PreviewMetadata,
        metadata: MetadataDocument,
        environment: RenderingEnvironment,
        paparazziEnvironment: PaparazziEnvironment,
        resolved: ResolvedComposable,
        classLoader: URLClassLoader,
        args: Array<Any?>,
        imageFile: File
    ): RenderResult {
        val layoutlibJar = environment.layoutlibJar ?: return RenderResult.failure("Layoutlib jar not configured")
        val resourcesRoot = layoutlibJar.parentFile ?: return RenderResult.failure("Layoutlib resources unavailable")
        val runtimeRoot = resourcesRoot.parentFile ?: return RenderResult.failure("Layoutlib runtime unavailable")

        val showSystemUi = preview.annotation["showSystemUi"]?.jsonPrimitive?.booleanOrNull == true
        val localeTag = preview.annotation["locale"]?.jsonPrimitive?.contentOrNull
        val supportsRtl = localeTag?.let(::isRtlLocale) ?: false
        val renderingMode = if (showSystemUi) {
            SessionParams.RenderingMode.NORMAL
        } else {
            SessionParams.RenderingMode.SHRINK
        }
        val deviceConfig = resolveDeviceConfig(preview.annotation)

        val snapshotMethod = paparazziSnapshotMethod ?: run {
            val lookupError = paparazziSnapshotLookupError
            val reason = lookupError?.renderSummary()
            val baseMessage = "Paparazzi snapshot(String, Function2) method unavailable; unable to invoke Compose renderer"
            val message = if (reason != null) "$baseMessage ($reason)" else baseMessage
            if (lookupError != null) {
                logger.warn(message, lookupError)
            } else {
                logger.warn(message)
            }
            return RenderResult.failure(message)
        }

        val previousRuntime = System.getProperty(LAYOUTLIB_RUNTIME_PROPERTY)
        val previousResources = System.getProperty(LAYOUTLIB_RESOURCES_PROPERTY)
        val previousLoader = Thread.currentThread().contextClassLoader

        return try {
            System.setProperty(LAYOUTLIB_RUNTIME_PROPERTY, runtimeRoot.absolutePath)
            System.setProperty(LAYOUTLIB_RESOURCES_PROPERTY, resourcesRoot.absolutePath)
            Thread.currentThread().contextClassLoader = classLoader

            val snapshotHandler = SingleImageSnapshotHandler(imageFile)
            val paparazzi = Paparazzi(
                environment = paparazziEnvironment,
                deviceConfig = deviceConfig,
                renderingMode = renderingMode,
                appCompatEnabled = true,
                maxPercentDifference = 0.0,
                snapshotHandler = snapshotHandler,
                supportsRtl = supportsRtl,
                showSystemUi = showSystemUi
            )

            val description = buildTestDescription(metadata, preview)
            val renderResult = try {
                paparazzi.prepare(description)
                val snapshotComposable: (Composer, Int) -> Unit = { composer, _ ->
                    resolved.method.invoke(composer, resolved.receiver, *args)
                }
                snapshotMethod.invoke(paparazzi, preview.id, snapshotComposable)
                RenderResult.success()
            } catch (invokeError: InvocationTargetException) {
                val cause = invokeError.targetException ?: invokeError
                val summary = cause.asRenderFailureMessage()
                logRenderFailure(metadata, preview, summary, cause)
                RenderResult.failure(summary)
            } catch (throwable: Throwable) {
                val summary = throwable.asRenderFailureMessage()
                logRenderFailure(metadata, preview, summary, throwable)
                RenderResult.failure(summary)
            } finally {
                paparazzi.close()
            }

            renderResult
        } catch (throwable: Throwable) {
            val summary = throwable.asRenderFailureMessage()
            logRenderFailure(metadata, preview, summary, throwable)
            RenderResult.failure(summary)
        } finally {
            if (previousRuntime != null) {
                System.setProperty(LAYOUTLIB_RUNTIME_PROPERTY, previousRuntime)
            } else {
                System.clearProperty(LAYOUTLIB_RUNTIME_PROPERTY)
            }
            if (previousResources != null) {
                System.setProperty(LAYOUTLIB_RESOURCES_PROPERTY, previousResources)
            } else {
                System.clearProperty(LAYOUTLIB_RESOURCES_PROPERTY)
            }
            Thread.currentThread().contextClassLoader = previousLoader
        }
    }

    private fun logRenderFailure(
        metadata: MetadataDocument,
        preview: PreviewMetadata,
        summary: String,
        throwable: Throwable? = null
    ) {
        val context = "${metadata.modulePath}#${metadata.variant}"
        val message = "Failed to render preview ${preview.id} for $context: $summary"
        if (throwable != null) {
            logger.warn(message, throwable)
        } else {
            logger.warn(message)
        }
    }

    private fun resolveComposable(preview: PreviewMetadata, classLoader: ClassLoader): ComposableResolution {
        val clazz = try {
            classLoader.loadClass(preview.fqcn)
        } catch (exception: ClassNotFoundException) {
            logger.warn("Unable to load ${preview.fqcn} for preview ${preview.id}: ${exception.renderSummary()}")
            return ComposableResolution.Unsupported("Composable class ${preview.fqcn} was not found")
        }

        val method = try {
            clazz.getDeclaredComposableMethod(preview.methodName)
        } catch (exception: NoSuchMethodException) {
            logger.warn("Composable method ${preview.methodName} not found on ${preview.fqcn} for preview ${preview.id}.")
            return ComposableResolution.Unsupported("Composable method ${preview.methodName} not found")
        }

        val javaMethod = method.asMethod()
        val receiver = if (Modifier.isStatic(javaMethod.modifiers)) null else instantiateReceiver(clazz)
        if (!Modifier.isStatic(javaMethod.modifiers) && receiver == null) {
            val message = "Unable to instantiate receiver for preview ${preview.id} (${preview.fqcn}.${preview.methodName})."
            logger.warn(message)
            return ComposableResolution.Unsupported(message)
        }

        val parameterCount = method.parameterCountCompat()
        if (parameterCount == 0) {
            return ComposableResolution.Success(
                ResolvedComposable(
                    method = method,
                    receiver = receiver,
                    parameterCount = parameterCount,
                    previewParameters = emptyList()
                )
            )
        }

        val parameters = method.parametersCompat()
        if (parameters.size < parameterCount) {
            return ComposableResolution.Unsupported("Unable to inspect parameters for preview ${preview.id}")
        }

        val bindings = mutableListOf<PreviewParameterBinding>()
        for (index in 0 until parameterCount) {
            val parameter = parameters[index]
            val annotation = parameter.annotations
                .firstOrNull { it.annotationClass.java.name == PREVIEW_PARAMETER_ANNOTATION }
                as? Annotation
                ?: run {
                    val message = "Parameter ${parameter.name ?: "#${index + 1}"} on ${preview.fqcn}.${preview.methodName} is not annotated with @PreviewParameter"
                    logger.warn("$message; skipping preview ${preview.id}")
                    return ComposableResolution.Unsupported(message)
                }

            val providerClassName = extractProviderClassName(annotation)
                ?: return ComposableResolution.Unsupported(
                    "Unable to resolve provider class for preview parameter ${parameter.name ?: index}" +
                        " in ${preview.fqcn}.${preview.methodName}"
                )
            val limit = extractPreviewParameterLimit(annotation)

            bindings += PreviewParameterBinding(
                index = index,
                name = parameter.name,
                type = parameter.type,
                providerClassName = providerClassName,
                limit = limit
            )
        }

        return ComposableResolution.Success(
            ResolvedComposable(
                method = method,
                receiver = receiver,
                parameterCount = parameterCount,
                previewParameters = bindings
            )
        )
    }

    private fun instantiateReceiver(clazz: Class<*>): Any? {
        clazz.declaredFields.firstOrNull { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }?.let { field ->
            return runCatching {
                field.isAccessible = true
                field.get(null)
            }.getOrNull()
        }

        return runCatching {
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull()
    }

    private fun resolveDeviceConfig(annotation: JsonObject): DeviceConfig {
        val rawDevice = annotation["device"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.US)?.trim()
        if (rawDevice.isNullOrBlank()) {
            return DeviceConfig.PIXEL_5
        }

        val normalized = rawDevice
            .removePrefix("id:")
            .removePrefix("devices.")
            .removePrefix("device.")

        return when {
            normalized.contains("pixel_6_pro") -> DeviceConfig.PIXEL_6_PRO
            normalized.contains("pixel_6") -> DeviceConfig.PIXEL_6
            normalized.contains("pixel_5") -> DeviceConfig.PIXEL_5
            normalized.contains("pixel_4_xl") -> DeviceConfig.PIXEL_4_XL
            normalized.contains("pixel_4a") -> DeviceConfig.PIXEL_4A
            normalized.contains("pixel_4") -> DeviceConfig.PIXEL_4
            normalized.contains("pixel_3_xl") -> DeviceConfig.PIXEL_3_XL
            normalized.contains("pixel_3a_xl") -> DeviceConfig.PIXEL_3A_XL
            normalized.contains("pixel_3a") -> DeviceConfig.PIXEL_3A
            normalized.contains("pixel_3") -> DeviceConfig.PIXEL_3
            normalized.contains("pixel_2_xl") -> DeviceConfig.PIXEL_2_XL
            normalized.contains("pixel_2") -> DeviceConfig.PIXEL_2
            normalized.contains("pixel_xl") -> DeviceConfig.PIXEL_XL
            normalized.contains("pixel_c") -> DeviceConfig.PIXEL_C
            normalized.contains("pixel") -> DeviceConfig.PIXEL
            else -> DeviceConfig.PIXEL_5
        }
    }

    private fun isRtlLocale(locale: String): Boolean {
        val tag = locale.trim().takeIf { it.isNotBlank() } ?: return false
        val normalized = tag.replace('_', '-').lowercase(Locale.US)
        return try {
            val parsed = Locale.forLanguageTag(normalized)
            val language = parsed.language.lowercase(Locale.US)
            language in setOf("ar", "fa", "he", "iw", "ur") || normalized.endsWith("-rxb")
        } catch (_: Exception) {
            false
        }
    }

    private fun buildTestDescription(metadata: MetadataDocument, preview: PreviewMetadata): Description {
        val moduleSegment = metadata.modulePath.trim(':').replace(':', '.').ifBlank { "compose" }
        val classSegments = buildList {
            if (moduleSegment.isNotBlank()) add(sanitizeIdentifier(moduleSegment))
            if (metadata.variant.isNotBlank()) add(sanitizeIdentifier(metadata.variant))
        }
        val className = classSegments.joinToString(separator = ".").ifBlank { "ComposePreviews" }
        val methodName = sanitizeIdentifier(preview.id)
        return Description.createTestDescription(className, methodName)
    }

    private fun sanitizeIdentifier(value: String): String {
        val sanitized = value.replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
        return sanitized.ifBlank { "preview" }
    }

    private class SingleImageSnapshotHandler(private val outputFile: File) : SnapshotHandler {
        override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
            return object : SnapshotHandler.FrameHandler {
                private var written = false

                override fun handle(image: BufferedImage) {
                    if (!written) {
                        outputFile.parentFile?.mkdirs()
                        ImageIO.write(image, "PNG", outputFile)
                        written = true
                    }
                }

                override fun close() {
                    if (!written && outputFile.exists()) {
                        outputFile.delete()
                    }
                }
            }
        }

        override fun close() {}
    }

    private companion object {
        private const val HEADLESS_PROPERTY = "java.awt.headless"
        private const val LAYOUTLIB_RUNTIME_PROPERTY = "paparazzi.layoutlib.runtime.root"
        private const val LAYOUTLIB_RESOURCES_PROPERTY = "paparazzi.layoutlib.resources.root"
        private const val DEFAULT_COMPILE_SDK = 33
        private const val PLACEHOLDER_WIDTH = 1200
        private const val PLACEHOLDER_HEIGHT = 720
        private const val OUTER_MARGIN = 12
        private const val INNER_MARGIN = 48
        private const val SEPARATOR_PADDING = 16
        private const val HEADER_MAX_CHARS = 22
        private const val BODY_MAX_CHARS = 42
        private const val MAX_SEGMENT_LENGTH = 48
        private const val MAX_FILE_STEM = 160
        private const val MIN_BASE_LENGTH = 12
        private const val MAX_SOURCE_SEGMENT = 64
        private const val PREVIEW_PARAMETER_ANNOTATION = "androidx.compose.ui.tooling.preview.PreviewParameter"
        private val NON_FILENAME_REGEX = "[^A-Za-z0-9._-]+".toRegex()
        private val UNDERSCORE_RUN_REGEX = "_+".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}

private data class RenderResult(val success: Boolean, val errorMessage: String?) {
    companion object {
        fun success(): RenderResult = RenderResult(true, null)
        fun failure(message: String): RenderResult = RenderResult(false, message)
    }
}

private data class ResolvedComposable(
    val method: ComposableMethod,
    val receiver: Any?,
    val parameterCount: Int,
    val previewParameters: List<PreviewParameterBinding>
)

private data class PreviewParameterBinding(
    val index: Int,
    val name: String?,
    val type: Class<*>,
    val providerClassName: String,
    val limit: Int
)

private data class PreviewParameterValues(
    val binding: PreviewParameterBinding,
    val values: List<PreviewValueInstance>
)

private data class PreviewValueInstance(
    val value: Any?,
    val displayName: String?
)

private data class RenderedPreview(
    val id: String,
    val fqcn: String,
    val packageName: String,
    val simpleClassName: String,
    val methodName: String,
    val previewIndex: Int,
    val displayName: String,
    val configurationSummary: String,
    val source: String,
    val annotation: JsonObject,
    val relativeOutputPath: String,
    val parameterInstanceIndex: Int?,
    val parameters: List<RenderedParameter>,
    val placeholder: Boolean,
    val renderError: String?
)

private data class RenderedParameter(
    val index: Int,
    val name: String?,
    val providerClassName: String,
    val valueSummary: String,
    val valueClassName: String?,
    val displayName: String?
)

private data class PreviewInvocationSpec(
    val parameterInstanceIndex: Int?,
    val args: List<Any?>,
    val renderedParameters: List<RenderedParameter>,
    val label: String?,
    val skipReason: String?
)

private sealed class ComposableResolution {
    data class Success(val composable: ResolvedComposable) : ComposableResolution()
    data class Unsupported(val reason: String) : ComposableResolution()
}

private sealed class PreviewParameterResolution {
    data class Success(val values: PreviewParameterValues) : PreviewParameterResolution()
    data class Failure(val reason: String) : PreviewParameterResolution()
}

private fun ComposableMethod.parameterCountCompat(): Int =
    (parameterCountMethod.invoke(this) as Int)

private fun ComposableMethod.parametersCompat(): Array<Parameter> {
    @Suppress("UNCHECKED_CAST")
    return parameterArrayMethod.invoke(this) as Array<Parameter>
}

private val parameterCountMethod by lazy {
    ComposableMethod::class.java.getDeclaredMethod("getParameterCount").apply { isAccessible = true }
}

private val parameterArrayMethod by lazy {
    ComposableMethod::class.java.getDeclaredMethod("getParameters").apply { isAccessible = true }
}

private val paparazziSnapshotLookup by lazy {
    runCatching {
        Paparazzi::class.java.getMethod(
            "snapshot",
            String::class.java,
            kotlin.jvm.functions.Function2::class.java
        )
    }
}

private val paparazziSnapshotMethod: Method?
    get() = paparazziSnapshotLookup.getOrNull()

private val paparazziSnapshotLookupError: Throwable?
    get() = paparazziSnapshotLookup.exceptionOrNull()

private fun JsonElement.toSummaryString(): String = when (this) {
    is JsonPrimitive -> if (isString) "\"$content\"" else content
    is JsonObject -> entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=${value.toSummaryString()}" }
    is JsonArray -> joinToString(prefix = "[", postfix = "]") { it.toSummaryString() }
    else -> toString()
}

private fun File.toPrettyPath(root: File): String = try {
    relativeTo(root).path.replace(File.separatorChar, '/')
} catch (_: IllegalArgumentException) {
    absolutePath.replace(File.separatorChar, '/')
}

private fun Iterable<File>.toJsonArray(root: File): JsonArray =
    JsonArray(map { JsonPrimitive(it.toPrettyPath(root)) })

private fun Throwable.renderSummary(): String {
    val messagePart = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
    return "${this::class.java.simpleName}$messagePart"
}

private fun Throwable.asRenderFailureMessage(): String {
    val target = (this as? InvocationTargetException)?.targetException ?: this
    return when (target) {
        is NoClassDefFoundError, is ClassNotFoundException -> {
            val missing = target.message?.takeIf { it.isNotBlank() } ?: "unknown class"
            "Missing Compose runtime dependency ($missing)"
        }
        else -> target.renderSummary()
    }
}
