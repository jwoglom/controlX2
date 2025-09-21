package com.jwoglom.controlx2.build.compose

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class RenderComposePreviewsTask : DefaultTask() {
    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun render() {
        val metadata = metadataFile.get().asFile
        if (!metadata.exists()) {
            throw IllegalStateException(
                "Expected metadata file at ${metadata.absolutePath} for ${modulePath.get()}#${variantName.get()}"
            )
        }

        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val marker = File(outputDir, "PLACEHOLDER.txt")
        if (!marker.exists()) {
            marker.writeText(
                buildString {
                    appendLine("Compose preview rendering is not yet implemented.")
                    appendLine("Module: ${modulePath.get()}")
                    appendLine("Variant: ${variantName.get()}")
                    appendLine("Metadata: ${metadata.absolutePath}")
                }
            )
        }

        logger.lifecycle(
            "Skipping Compose preview rendering for ${modulePath.get()}#${variantName.get()} (placeholder implementation)."
        )
    }
}
