package com.jwoglom.controlx2.build.compose

import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CollectComposePreviewsTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun collect() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        val payload = buildString {
            appendLine("{")
            appendLine("  \"status\": \"placeholder\",")
            appendLine("  \"message\": \"Compose preview discovery not yet implemented\",")
            appendLine("  \"modulePath\": \"${escape(modulePath.get())}\",")
            appendLine("  \"variant\": \"${escape(variantName.get())}\",")
            appendLine("  \"generatedAt\": \"${escape(Instant.now().toString())}\"")
            appendLine("}")
        }

        output.writeText(payload)
        logger.lifecycle(
            "Created placeholder Compose preview metadata for ${modulePath.get()}#${variantName.get()} at ${output.absolutePath}"
        )
    }

    private fun escape(input: String): String = input
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
