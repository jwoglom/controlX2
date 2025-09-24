package com.jwoglom.controlx2.build.compose

import java.io.File
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class AggregateComposePreviewManifestsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ConfigurableFileCollection

    @get:Input
    abstract val expectedManifestPaths: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun aggregate() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        val json = Json { prettyPrint = true }
        val rootDir = project.rootDir
        val issues = mutableListOf<String>()
        val manifests = mutableListOf<JsonObject>()

        val expected = expectedManifestPaths.orNull
            ?.map(::normalizePath)
            ?.toSet()
            ?: emptySet()

        val actualFiles = manifestFiles.files
        val actualByPath = actualFiles.associateBy { normalizePath(it.absolutePath) }
        val missing = expected - actualByPath.keys
        missing.forEach { path ->
            issues += "Missing Compose preview manifest at $path"
        }

        actualFiles.sortedBy { it.absolutePath }.forEach { file ->
            val parseResult = parseManifest(file, json, rootDir)
            if (parseResult == null) {
                issues += "Unable to parse Compose preview manifest ${file.toPrettyPath(rootDir)}"
            } else {
                manifests += buildModuleEntry(parseResult, file, rootDir, issues)
            }
        }

        val aggregated = buildJsonObject {
            put("status", JsonPrimitive(if (issues.isEmpty()) "ok" else "error"))
            put("generatedAt", JsonPrimitive(Instant.now().toString()))
            put("expectedCount", JsonPrimitive(expected.size))
            put("manifestCount", JsonPrimitive(manifests.size))
            if (missing.isNotEmpty()) {
                put("missingManifests", missing.toList().toJsonArray())
            }
            put("manifests", JsonArray(manifests))
            if (issues.isNotEmpty()) {
                put("errors", issues.distinct().toJsonArray())
            }
        }

        output.writeText(json.encodeToString(JsonObject.serializer(), aggregated))

        if (issues.isNotEmpty()) {
            val message = buildString {
                appendLine("Compose preview manifest aggregation encountered issues:")
                issues.distinct().forEach { append(" - ").append(it).appendLine() }
            }
            throw GradleException(message.trimEnd())
        }
    }

    private fun buildModuleEntry(
        manifest: ManifestParseResult,
        file: File,
        root: File,
        issues: MutableList<String>
    ): JsonObject {
        val moduleVariant = manifest.moduleVariantLabel
        val entryIssues = manifest.environmentIssues + manifest.missingImages
        if (entryIssues.isNotEmpty()) {
            issues += entryIssues
        }

        return buildJsonObject {
            put("modulePath", JsonPrimitive(manifest.modulePath))
            put("variant", JsonPrimitive(manifest.variant))
            put("manifestFile", JsonPrimitive(file.toPrettyPath(root)))
            put("previewCount", JsonPrimitive(manifest.previewCount))
            manifest.metadataPreviewCount?.let { put("metadataPreviewCount", JsonPrimitive(it)) }
            put("placeholders", JsonPrimitive(manifest.placeholders))
            put("manifest", manifest.manifestJson)
            put("previews", manifest.previewSummaries)
            if (manifest.environmentIssues.isNotEmpty()) {
                put("environmentIssues", manifest.environmentIssues.toJsonArray())
            }
            if (manifest.missingImages.isNotEmpty()) {
                put("missingImages", manifest.missingImages.toJsonArray())
            }
            if (manifest.placeholders && manifest.previewCount == 0) {
                issues += "${moduleVariant} produced no renderable previews"
            }
        }
    }

    private fun parseManifest(
        file: File,
        json: Json,
        root: File
    ): ManifestParseResult? {
        val text = runCatching { file.readText() }.getOrElse { return null }
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { return null }
        val obj = element as? JsonObject ?: return null

        val status = obj["status"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "ok") {
            return ManifestParseResult.fromInvalid(obj, "Manifest status '$status'")
        }

        val modulePath = obj["modulePath"]?.jsonPrimitive?.contentOrNull ?: return null
        val variant = obj["variant"]?.jsonPrimitive?.contentOrNull ?: return null
        val previewCount = obj["previewCount"]?.jsonPrimitive?.intOrNull ?: 0
        val metadataPreviewCount = obj["metadataPreviewCount"]?.jsonPrimitive?.intOrNull
        val placeholders = obj["placeholders"]?.jsonPrimitive?.booleanOrNull ?: false

        val previews = obj["previews"]?.jsonArray
        val previewDetails = previews
            ?.mapNotNull { summarisePreview(it, file, root, modulePath, variant) }
            ?: emptyList()

        val previewSummaries = buildJsonArray {
            previewDetails.forEach { add(it.summaryJson) }
        }

        val manifestJson = buildJsonObject {
            obj.entries.forEach { (key, value) ->
                if (key == "previews" && previews != null) {
                    put(key, buildJsonArray { previewDetails.forEach { add(it.fullJson) } })
                } else {
                    put(key, value)
                }
            }
        }

        val environmentIssues = collectEnvironmentIssues(obj["environment"], modulePath, variant)
        val missingImages = previewDetails.mapNotNull { it.missingMessage }

        return ManifestParseResult(
            modulePath = modulePath,
            variant = variant,
            previewCount = previewCount,
            metadataPreviewCount = metadataPreviewCount,
            placeholders = placeholders,
            manifestJson = manifestJson,
            previewSummaries = previewSummaries,
            environmentIssues = environmentIssues,
            missingImages = missingImages
        )
    }

    private fun summarisePreview(
        element: JsonElement,
        manifestFile: File,
        root: File,
        modulePath: String,
        variant: String
    ): PreviewSummary? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
        val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull
        val output = obj["output"]?.jsonPrimitive?.contentOrNull
        val placeholder = obj["placeholder"]?.jsonPrimitive?.booleanOrNull
        val renderError = obj["renderError"]?.jsonPrimitive?.contentOrNull

        val outputFile = output?.let { File(manifestFile.parentFile, it).normalize() }
        val resolvedOutput = outputFile?.toPrettyPath(root)
        val exists = outputFile?.exists() == true

        val summaryJson = buildJsonObject {
            id?.let { put("id", JsonPrimitive(it)) }
            displayName?.let { put("displayName", JsonPrimitive(it)) }
            output?.let { put("output", JsonPrimitive(it)) }
            resolvedOutput?.let { put("resolvedOutput", JsonPrimitive(it)) }
            placeholder?.let { put("placeholder", JsonPrimitive(it)) }
            renderError?.let { put("renderError", JsonPrimitive(it)) }
            put("outputExists", JsonPrimitive(exists))
        }

        val fullJson = buildJsonObject {
            obj.entries.forEach { (key, value) ->
                put(key, value)
            }
            resolvedOutput?.let { put("resolvedOutput", JsonPrimitive(it)) }
            put("outputExists", JsonPrimitive(exists))
        }

        val missingMessage = if (!exists) {
            buildMissingImageMessage(modulePath, variant, id, resolvedOutput)
        } else {
            null
        }

        return PreviewSummary(summaryJson, fullJson, missingMessage)
    }

    private fun collectEnvironmentIssues(
        environmentElement: JsonElement?,
        modulePath: String,
        variant: String
    ): List<String> {
        val env = environmentElement as? JsonObject ?: return emptyList()
        val issues = mutableListOf<String>()
        val moduleVariant = "$modulePath#$variant"

        env.entries.forEach { (key, value) ->
            when {
                key.endsWith("Missing") && value is JsonArray -> {
                    val missingValues = value
                        .mapNotNull { it.jsonPrimitiveOrNull()?.takeIf { candidate -> candidate.isNotBlank() } }
                        .map { it.replace('\\', '/') }
                    if (missingValues.isNotEmpty()) {
                        issues += "$moduleVariant missing ${key.removeSuffix("Missing")}: ${missingValues.joinToString(", ")}"
                    }
                }
                key == "layoutlibJarMissing" && value is JsonPrimitive -> {
                    val path = value.contentOrNull
                    if (!path.isNullOrBlank()) {
                        issues += "$moduleVariant missing layoutlib jar (expected $path)"
                    }
                }
            }
        }

        return issues
    }

    private fun buildMissingImageMessage(
        modulePath: String,
        variant: String,
        id: String?,
        resolvedOutput: String?
    ): String {
        val label = id ?: "<unknown>"
        return "$modulePath#$variant preview '$label' did not produce an image (${resolvedOutput ?: "output missing"})"
    }

    private fun List<String>.toJsonArray(): JsonArray =
        buildJsonArray { forEach { add(JsonPrimitive(it)) } }

    private fun normalizePath(path: String): String =
        File(path).absoluteFile.normalize().path.replace(File.separatorChar, '/')

    private fun File.toPrettyPath(root: File): String = try {
        absoluteFile.normalize().relativeTo(root).path.replace(File.separatorChar, '/')
    } catch (_: IllegalArgumentException) {
        absoluteFile.normalize().path.replace(File.separatorChar, '/')
    }

    private fun JsonElement.jsonPrimitiveOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private data class PreviewSummary(
        val summaryJson: JsonObject,
        val fullJson: JsonObject,
        val missingMessage: String?
    )

    private data class ManifestParseResult(
        val modulePath: String,
        val variant: String,
        val previewCount: Int,
        val metadataPreviewCount: Int?,
        val placeholders: Boolean,
        val manifestJson: JsonObject,
        val previewSummaries: JsonArray,
        val environmentIssues: List<String>,
        val missingImages: List<String>
    ) {
        val moduleVariantLabel: String
            get() = "$modulePath#$variant"

        companion object {
            fun fromInvalid(
                manifest: JsonObject,
                reason: String
            ): ManifestParseResult? {
                val modulePath = manifest["modulePath"]?.jsonPrimitive?.contentOrNull ?: return null
                val variant = manifest["variant"]?.jsonPrimitive?.contentOrNull ?: return null
                val placeholders = manifest["placeholders"]?.jsonPrimitive?.booleanOrNull ?: false
                val previewCount = manifest["previewCount"]?.jsonPrimitive?.intOrNull ?: 0

                return ManifestParseResult(
                    modulePath = modulePath,
                    variant = variant,
                    previewCount = previewCount,
                    metadataPreviewCount = manifest["metadataPreviewCount"]?.jsonPrimitive?.intOrNull,
                    placeholders = placeholders,
                    manifestJson = manifest,
                    previewSummaries = JsonArray(emptyList()),
                    environmentIssues = listOf("$modulePath#$variant reported invalid status: $reason"),
                    missingImages = emptyList()
                )
            }
        }
    }
}

