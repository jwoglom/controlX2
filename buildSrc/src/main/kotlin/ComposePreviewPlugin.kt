package com.jwoglom.controlx2.build.compose

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import java.io.File
import java.util.Locale
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Attribute
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

class ComposePreviewPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val rootProject = project.rootProject
        val aggregateMetadata = ensureAggregateTask(
            rootProject,
            key = "controlx2.compose.aggregate.collect",
            name = "collectAllComposePreviewMetadata",
            description = "Collects Compose preview metadata across all modules."
        )
        val aggregateRender = ensureAggregateTask(
            rootProject,
            key = "controlx2.compose.aggregate.render",
            name = "renderAllComposePreviews",
            description = "Renders Compose previews across all modules."
        )

        val aggregateManifest = ensureAggregateManifestTask(rootProject)

        aggregateRender.configure { dependsOn(aggregateMetadata) }
        aggregateManifest.configure { dependsOn(aggregateRender) }
        aggregateRender.configure { finalizedBy(aggregateManifest) }

        project.pluginManager.withPlugin("com.android.application") {
            configureWhenKotlinReady(project, aggregateMetadata, aggregateRender, aggregateManifest)
        }
        project.pluginManager.withPlugin("com.android.library") {
            configureWhenKotlinReady(project, aggregateMetadata, aggregateRender, aggregateManifest)
        }
    }

    private fun configureWhenKotlinReady(
        project: Project,
        aggregateMetadata: TaskProvider<Task>,
        aggregateRender: TaskProvider<Task>,
        aggregateManifest: TaskProvider<AggregateComposePreviewManifestsTask>
    ) {
        val configured = AtomicBoolean(false)
        val configureIfNeeded = {
            if (configured.compareAndSet(false, true)) {
                configureAndroidVariants(project, aggregateMetadata, aggregateRender, aggregateManifest)
            }
        }

        val pluginManager = project.pluginManager
        if (pluginManager.hasPlugin("org.jetbrains.kotlin.android") || pluginManager.hasPlugin("kotlin-android")) {
            configureIfNeeded()
            return
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.android") { configureIfNeeded() }
        pluginManager.withPlugin("kotlin-android") { configureIfNeeded() }
    }

    private fun configureAndroidVariants(
        project: Project,
        aggregateMetadata: TaskProvider<Task>,
        aggregateRender: TaskProvider<Task>,
        aggregateManifest: TaskProvider<AggregateComposePreviewManifestsTask>
    ) {
        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java) ?: return

        androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
            registerVariantTasks(project, variant, aggregateMetadata, aggregateRender, aggregateManifest)
        }
    }

    private fun registerVariantTasks(
        project: Project,
        variant: Variant,
        aggregateMetadata: TaskProvider<Task>,
        aggregateRender: TaskProvider<Task>,
        aggregateManifest: TaskProvider<AggregateComposePreviewManifestsTask>
    ) {
        val variantName = variant.name
        val taskSuffix = variantName.toTaskSuffix()

        val collectTask = project.tasks.register<CollectComposePreviewsTask>(
            "collectComposePreviewMetadata$taskSuffix"
        ) {
            group = TASK_GROUP
            description = "Collect Compose preview metadata for the $variantName variant of ${project.path}."
            this.variantName.set(variantName)
            this.modulePath.set(project.path)
            outputFile.set(
                project.layout.buildDirectory.file("composePreviews/$variantName/metadata.json")
            )
        }

        val kotlinCompileTaskName = "compile${taskSuffix}Kotlin"
        val javaCompileTaskName = "compile${taskSuffix}JavaWithJavac"
        val kotlinClassesDir = project.layout.buildDirectory.dir("tmp/kotlin-classes/$variantName")
        val javaClassesDir = project.layout.buildDirectory.dir(
            "intermediates/javac/$variantName/compile${taskSuffix}JavaWithJavac/classes"
        )

        collectTask.configure {
            classDirectories.from(kotlinClassesDir.map { it.asFile })
            classDirectories.from(javaClassesDir.map { it.asFile })
            dependsOn(kotlinCompileTaskName)
            if (project.tasks.names.contains(javaCompileTaskName)) {
                dependsOn(javaCompileTaskName)
            }
        }

        val renderTask = project.tasks.register<RenderComposePreviewsTask>(
            "renderComposePreviews$taskSuffix"
        ) {
            group = TASK_GROUP
            description = "Render Compose previews for the $variantName variant of ${project.path}."
            this.variantName.set(variantName)
            this.modulePath.set(project.path)
            metadataFile.set(collectTask.flatMap { collect -> collect.outputFile })
            outputDirectory.set(
                project.layout.buildDirectory.dir("composePreviews/$variantName/images")
            )
            manifestFile.set(outputDirectory.file("manifest.json"))
        }

        val runtimeConfigurationName = "${variantName}RuntimeClasspath"
        val runtimeConfiguration = project.configurations.findByName(runtimeConfigurationName)
        val layoutlibCandidate = locateLayoutlibJar(project)
        val layoutlibFallback = layoutlibCandidate ?: layoutlibFallbackFromDependencies(project)
        val resolvedLayoutlib = layoutlibFallback
        val namespace: String = runCatching { variant.namespace.get() }.getOrElse { throwable ->
            val fallback = project.name
            val reason = throwable.message?.takeIf { it.isNotBlank() }
                ?: throwable::class.java.simpleName
            project.logger.warn(
                "Unable to determine namespace for ${project.path}#$variantName from Android Gradle APIs; " +
                    "defaulting to '$fallback'. Reason: $reason"
            )
            fallback
        }
        val compileSdk = determineCompileSdk(project) ?: DEFAULT_COMPILE_SDK
        val resourcePackages: List<String> = listOf(namespace)

        val processResourcesTaskName = "process${taskSuffix}Resources"
        val mergeResourcesTaskName = "merge${taskSuffix}Resources"
        val mergeAssetsTaskName = "merge${taskSuffix}Assets"

        renderTask.configure {
            runtimeClasspath.from(kotlinClassesDir.map { it.asFile })
            runtimeClasspath.from(javaClassesDir.map { it.asFile })
            runtimeClasspath.from(compiledRClassJar)
            runtimeConfiguration?.let { configuration ->
                val artifactType = Attribute.of("artifactType", String::class.java)

                val runtimeArtifacts = configuration.incoming.artifactView {
                    attributes.attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE)
                    lenient(true)
                }.files
                runtimeClasspath.from(runtimeArtifacts)

                val androidJarArtifacts = configuration.incoming.artifactView {
                    attributes.attribute(artifactType, "android-classes-jar")
                    lenient(true)
                }.files
                runtimeClasspath.from(androidJarArtifacts)

                val androidDirectoryArtifacts = configuration.incoming.artifactView {
                    attributes.attribute(artifactType, "android-classes-directory")
                    lenient(true)
                }.files
                runtimeClasspath.from(androidDirectoryArtifacts)
            }

            packageName.set(namespace)
            resourcePackageNames.set(resourcePackages.toMutableList())
            compileSdkVersion.set(compileSdk)

            mergedResources.from(
                project.layout.buildDirectory.dir(
                    "intermediates/merged_res/$variantName/merge${taskSuffix}Resources"
                ).map { it.asFile }
            )
            mergedResources.from(
                project.layout.buildDirectory.dir("intermediates/packaged_res/$variantName")
                    .map { it.asFile }
            )

            compiledRClassJar.from(
                project.layout.buildDirectory.file(
                    "intermediates/compile_and_runtime_not_namespaced_r_class_jar/$variantName/R.jar"
                ).map { it.asFile }
            )
            compiledRClassJar.from(
                project.layout.buildDirectory.file(
                    "intermediates/compile_and_runtime_not_namespaced_r_class_jar/$variantName/" +
                        "process${taskSuffix}Resources/R.jar"
                ).map { it.asFile }
            )

            libraryResources.from(
                project.layout.buildDirectory.dir(
                    "intermediates/packaged_res/$variantName/package${taskSuffix}Resources"
                ).map { it.asFile }
            )

            if (resolvedLayoutlib != null) {
                val fileProvider = project.providers.provider {
                    project.objects.fileProperty().fileValue(resolvedLayoutlib).get()
                }
                layoutlibJar.set(fileProvider)
                doFirst {
                    val source = if (layoutlibCandidate != null) "Android SDK" else "Maven"
                    logger.lifecycle(
                        "Compose preview task for ${project.path}#$variantName using $source layoutlib runtime at " +
                            resolvedLayoutlib.absolutePath
                    )
                }
            } else {
                doFirst {
                    logger.warn(
                        "Compose preview task for ${project.path}#$variantName could not locate a layoutlib runtime. " +
                            "Rendered previews will report the missing layoutlib."
                    )
                }
            }
        }

        aggregateMetadata.configure { dependsOn(collectTask) }
        aggregateRender.configure { dependsOn(renderTask) }
        renderTask.configure { dependsOn(collectTask) }
        renderTask.configure {
            if (project.tasks.names.contains(kotlinCompileTaskName)) {
                dependsOn(kotlinCompileTaskName)
            }
            if (project.tasks.names.contains(javaCompileTaskName)) {
                dependsOn(javaCompileTaskName)
            }
            if (project.tasks.names.contains(processResourcesTaskName)) {
                dependsOn(processResourcesTaskName)
            }
            if (project.tasks.names.contains(mergeResourcesTaskName)) {
                dependsOn(mergeResourcesTaskName)
            }
            if (project.tasks.names.contains(mergeAssetsTaskName)) {
                dependsOn(mergeAssetsTaskName)
            }
        }
        aggregateManifest.configure {
            manifestFiles.from(renderTask.flatMap { it.manifestFile })
            expectedManifestPaths.addAll(
                renderTask.flatMap { task ->
                    task.manifestFile.map { file ->
                        listOf(file.asFile.absoluteFile.normalize().path.replace(File.separatorChar, '/'))
                    }
                }
            )
        }
    }

    private fun ensureAggregateTask(
        rootProject: Project,
        key: String,
        name: String,
        description: String
    ): TaskProvider<Task> {
        val extra = rootProject.extensions.extraProperties
        if (extra.has(key)) {
            val existing = extra.get(key)
            if (existing is TaskProvider<*>) {
                @Suppress("UNCHECKED_CAST")
                return existing as TaskProvider<Task>
            }
        }

        val provider = rootProject.tasks.register<Task>(name) {
            group = TASK_GROUP
            this.description = description
        }
        extra.set(key, provider)
        return provider
    }

    private fun String.toTaskSuffix(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private companion object {
        const val TASK_GROUP = "compose previews"
        const val DEFAULT_COMPILE_SDK = 33
    }

    private fun ensureAggregateManifestTask(
        rootProject: Project
    ): TaskProvider<AggregateComposePreviewManifestsTask> {
        val key = "controlx2.compose.aggregate.manifest"
        val extra = rootProject.extensions.extraProperties
        if (extra.has(key)) {
            val existing = extra.get(key)
            if (existing is TaskProvider<*>) {
                @Suppress("UNCHECKED_CAST")
                return existing as TaskProvider<AggregateComposePreviewManifestsTask>
            }
        }

        val provider = rootProject.tasks.register<AggregateComposePreviewManifestsTask>(
            "aggregateComposePreviewManifests"
        ) {
            group = TASK_GROUP
            description = "Aggregates Compose preview manifests across all modules."
            expectedManifestPaths.convention(emptyList())
            outputFile.set(
                rootProject.layout.buildDirectory.file("composePreviews/aggregate/manifest.json")
            )
        }
        extra.set(key, provider)
        return provider
    }

    private fun determineCompileSdk(project: Project): Int? {
        val applicationExtension = project.extensions.findByType(ApplicationExtension::class.java)
        if (applicationExtension?.compileSdk != null) {
            return applicationExtension.compileSdk
        }
        val libraryExtension = project.extensions.findByType(LibraryExtension::class.java)
        if (libraryExtension?.compileSdk != null) {
            return libraryExtension.compileSdk
        }
        return null
    }

    private fun locateLayoutlibJar(project: Project): File? {
        val sdkDir = findAndroidSdkDirectory(project) ?: return null
        val platformsDir = File(sdkDir, "platforms")
        if (!platformsDir.exists()) {
            return null
        }

        val preferredApis = listOf("android-35", "android-34", "android-33")
        val preferredExisting = preferredApis
            .map { File(platformsDir, "$it/data/layoutlib.jar") }
            .firstOrNull(File::exists)
        if (preferredExisting != null) {
            return preferredExisting
        }

        val otherExisting = platformsDir.listFiles()
            ?.sortedByDescending { it.name }
            ?.map { File(it, "data/layoutlib.jar") }
            ?.firstOrNull(File::exists)
        if (otherExisting != null) {
            return otherExisting
        }

        return null
    }

    private fun layoutlibFallbackFromDependencies(project: Project): File? {
        val dependencyNotation = "com.android.tools.layoutlib:layoutlib:14.0.11"
        val dependency = project.dependencies.create(dependencyNotation)
        val configuration = project.configurations.detachedConfiguration(dependency).apply {
            isCanBeConsumed = false
            isVisible = false
        }

        return runCatching { configuration.singleFile }.getOrNull()
    }

    private fun findAndroidSdkDirectory(project: Project): File? {
        val localProperties = project.rootProject.file("local.properties")
        if (localProperties.exists()) {
            runCatching {
                Properties().apply {
                    localProperties.inputStream().use { load(it) }
                }
            }.getOrNull()?.getProperty("sdk.dir")
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.exists() }
                ?.let { return it }
        }

        val envCandidates = sequenceOf(
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("ANDROID_HOME")
        ).filterNotNull().map(String::trim).filter { it.isNotEmpty() }

        for (candidate in envCandidates) {
            val dir = File(candidate)
            if (dir.exists()) {
                return dir
            }
        }

        return null
    }
}
