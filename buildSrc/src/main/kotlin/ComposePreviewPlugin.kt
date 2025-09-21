package com.jwoglom.controlx2.build.compose

import com.android.build.api.variant.AndroidComponentsExtension
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
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

        aggregateRender.configure { dependsOn(aggregateMetadata) }

        project.pluginManager.withPlugin("com.android.application") {
            configureAndroidVariants(project, aggregateMetadata, aggregateRender)
        }
        project.pluginManager.withPlugin("com.android.library") {
            configureAndroidVariants(project, aggregateMetadata, aggregateRender)
        }
    }

    private fun configureAndroidVariants(
        project: Project,
        aggregateMetadata: TaskProvider<Task>,
        aggregateRender: TaskProvider<Task>
    ) {
        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java) ?: return

        androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
            registerVariantTasks(project, variant.name, aggregateMetadata, aggregateRender)
        }
    }

    private fun registerVariantTasks(
        project: Project,
        variantName: String,
        aggregateMetadata: TaskProvider<Task>,
        aggregateRender: TaskProvider<Task>
    ) {
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
        }

        aggregateMetadata.configure { dependsOn(collectTask) }
        aggregateRender.configure { dependsOn(renderTask) }
        renderTask.configure { dependsOn(collectTask) }
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
    }
}
