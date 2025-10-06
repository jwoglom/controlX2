package com.jwoglom.controlx2.build.compose

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GeneratePaparazziTestsTask : DefaultTask() {
    @get:InputFile
    abstract val metadataFile: RegularFileProperty

    @get:OutputFile
    abstract val outputTestFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun generate() {
        val metadata = metadataFile.get().asFile.readText()
        val json = Json.parseToJsonElement(metadata).jsonObject

        val previews = json["previews"]?.jsonArray
        if (previews == null) {
            logger.warn("No previews found in metadata for ${modulePath.get()}")
            return
        }

        val packageNameValue = packageName.get()

        val testClass = generateTestClass(packageNameValue, previews)

        val outputFile = outputTestFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(testClass)

        logger.lifecycle(
            "Generated Paparazzi test file with ${previews.size} preview(s) for ${modulePath.get()}"
        )
    }

    private fun generateTestClass(packageName: String, previews: kotlinx.serialization.json.JsonArray): String {
        val className = "ComposePreviewPaparazziTest"

        return buildString {
            appendLine("package $packageName.test")
            appendLine()
            appendLine("import app.cash.paparazzi.DeviceConfig")
            appendLine("import app.cash.paparazzi.Paparazzi")
            appendLine("import org.junit.Rule")
            appendLine("import org.junit.Test")
            appendLine()
            appendLine("class $className {")
            appendLine("    @get:Rule")
            appendLine("    val paparazzi = Paparazzi(")
            appendLine("        deviceConfig = ${if (packageName == "wear") "DeviceConfig.WEAR_OS_SMALL_ROUND" else "DeviceConfig.PIXEL_5"},")
            appendLine("        showSystemUi = false")
            appendLine("    )")
            appendLine()

            // Generate a test method for each preview
            previews.forEachIndexed { index, preview ->
                val previewObj = preview.jsonObject
                val id = previewObj["id"]?.jsonPrimitive?.content ?: "preview_$index"
                val methodName = previewObj["methodName"]?.jsonPrimitive?.content ?: "preview_$index"
                val packageNameValue = previewObj["packageName"]?.jsonPrimitive?.content ?: ""

                // Create a safe test method name
                val testMethodName = "test_${id.replace("#", "_").replace("[", "_").replace("]", "_").replace(".", "_")}"

                appendLine("    @Test")
                appendLine("    fun $testMethodName() {")
                appendLine("        paparazzi.snapshot(name = \"$testMethodName\") {")
                appendLine("            $packageNameValue.${methodName}()")
                appendLine("        }")
                appendLine("    }")
                appendLine()
            }

            appendLine("}")
        }
    }
}
