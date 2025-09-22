package com.jwoglom.controlx2.build.compose

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.parseToJsonElement
import kotlinx.serialization.json.put
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

        val metadataDocument = parseMetadata(metadataSource)
        val previews = metadataDocument.previews

        if (previews.isEmpty()) {
            val manifest = writeManifest(outputDir, metadataDocument, emptyList())
            logger.lifecycle(
                "No Compose previews discovered for ${metadataDocument.modulePath}#${metadataDocument.variant}. " +
                    "Wrote empty manifest to ${manifest.toPrettyPath(project.rootDir)}."
            )
            return
        }

        if (System.getProperty(HEADLESS_PROPERTY) == null) {
            System.setProperty(HEADLESS_PROPERTY, "true")
        }

        val renderedPreviews = mutableListOf<RenderedPreview>()
        val usedFileNames = mutableSetOf<String>()

        previews.forEach { preview ->
            var dedupeIndex = 0
            var relativePath: String
            while (true) {
                relativePath = buildOutputPath(preview, dedupeIndex)
                if (usedFileNames.add(relativePath)) {
                    break
                }
                dedupeIndex++
            }

            val imageFile = File(outputDir, relativePath)
            imageFile.parentFile?.mkdirs()
            writePlaceholderImage(imageFile, preview, metadataDocument)

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
                relativeOutputPath = normalizedPath
            )
        }

        val manifest = writeManifest(outputDir, metadataDocument, renderedPreviews)

        logger.lifecycle(
            "Generated ${renderedPreviews.size} Compose preview placeholder image(s) for " +
                "${metadataDocument.modulePath}#${metadataDocument.variant}. Manifest: ${manifest.toPrettyPath(project.rootDir)}"
        )
    }

    private fun parseMetadata(file: File): MetadataDocument {
        val jsonText = file.readText()
        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.parseToJsonElement(jsonText).jsonObject
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

    private fun buildOutputPath(preview: PreviewMetadata, duplicateIndex: Int): String {
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

    private fun writePlaceholderImage(file: File, preview: PreviewMetadata, metadata: MetadataDocument) {
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
            val textBlocks = buildPlaceholderText(preview, metadata)
            drawTextBlocks(g, textBlocks, width, height, textColor, background)
        }

        ImageIO.write(image, "PNG", file)
    }

    private fun buildPlaceholderText(preview: PreviewMetadata, metadata: MetadataDocument): PlaceholderText {
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
        outputDir: File,
        metadata: MetadataDocument,
        previews: List<RenderedPreview>
    ): File {
        val manifestFile = File(outputDir, "manifest.json")
        val renderTimestamp = Instant.now().toString()
        val previewElements = JsonArray(previews.map { it.toJsonElement() })
        val json = Json { prettyPrint = true }

        val manifest = buildJsonObject {
            put("status", JsonPrimitive("ok"))
            put("modulePath", JsonPrimitive(metadata.modulePath))
            put("variant", JsonPrimitive(metadata.variant))
            put("renderedAt", JsonPrimitive(renderTimestamp))
            put("metadataGeneratedAt", JsonPrimitive(metadata.metadataGeneratedAt))
            put("metadataFile", JsonPrimitive(metadata.metadataFile.toPrettyPath(project.rootDir)))
            put("previewCount", JsonPrimitive(previews.size))
            put("metadataPreviewCount", JsonPrimitive(metadata.previews.size))
            put("placeholders", JsonPrimitive(true))
            put("previews", previewElements)
        }

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
        put("placeholder", JsonPrimitive(true))
    }

    private fun stableDigest(id: String): String {
        val hash = id.hashCode()
        return String.format(Locale.US, "%08x", hash)
    }

    private fun File.toPrettyPath(root: File): String = try {
        relativeTo(root).path.replace(File.separatorChar, '/')
    } catch (_: IllegalArgumentException) {
        absolutePath.replace(File.separatorChar, '/')
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
        val relativeOutputPath: String
    )

    private data class PlaceholderText(
        val headerLines: List<String>,
        val bodyLines: List<String>
    )

    private fun JsonElement.toSummaryString(): String = when (this) {
        is JsonPrimitive -> if (isString) content else content
        is JsonObject -> entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=${value.toSummaryString()}" }
        is JsonArray -> joinToString(prefix = "[", postfix = "]") { it.toSummaryString() }
        else -> toString()
    }

    private companion object {
        private const val HEADLESS_PROPERTY = "java.awt.headless"
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
        private val NON_FILENAME_REGEX = "[^A-Za-z0-9._-]+".toRegex()
        private val UNDERSCORE_RUN_REGEX = "_+".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
