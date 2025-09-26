package com.jwoglom.controlx2.build.compose

import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.jar.JarFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class CollectComposePreviewsTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val modulePath: Property<String>

    @get:Classpath
    @get:SkipWhenEmpty
    abstract val classDirectories: ConfigurableFileCollection

    @TaskAction
    fun collect() {
        val previewDefinitions = mutableListOf<PreviewDefinition>()
        val artifacts = collectArtifacts(classDirectories.files)

        artifacts.forEach { artifact ->
            artifact.openStream().use { stream ->
                parseClass(stream, artifact.logicalPath, previewDefinitions)
            }
        }

        val manifest = buildManifest(previewDefinitions)
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(manifest)

        logger.lifecycle(
            "Discovered ${previewDefinitions.size} Compose preview(s) for ${modulePath.get()}#${variantName.get()}"
        )
    }

    private fun collectArtifacts(inputs: Set<File>): List<ClassArtifact> {
        val artifacts = mutableListOf<ClassArtifact>()
        inputs.forEach { input ->
            when {
                input.isDirectory -> input.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { file ->
                        val relative = input.toPath().relativize(file.toPath()).toString()
                        artifacts += DirectoryClassFile(file, relative)
                    }
                input.isFile && input.extension.equals("jar", ignoreCase = true) ->
                    JarFile(input).use { jar ->
                        jar.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class", ignoreCase = true) }
                            .forEach { entry ->
                                jar.getInputStream(entry)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    artifacts += JarClassEntry(bytes, "jar:${input.name}!/${entry.name}")
                                }
                            }
                    }
                input.exists() -> logger.warn("Ignoring unsupported class artifact: ${input.absolutePath}")
            }
        }
        return artifacts
    }

    private fun parseClass(
        inputStream: InputStream,
        logicalPath: String,
        previews: MutableList<PreviewDefinition>
    ) {
        val reader = ClassReader(inputStream)
        val visitor = PreviewClassVisitor(logicalPath) { definition ->
            previews += definition
        }
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private fun buildManifest(previews: List<PreviewDefinition>): String {
        val sorted = previews.sortedWith(
            compareBy<PreviewDefinition> { it.fqcn }
                .thenBy { it.methodName }
                .thenBy { it.previewIndex }
        )
        val now = Instant.now().toString()

        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"status\": \"ok\",")
        builder.appendLine("  \"modulePath\": \"${escape(modulePath.get())}\",")
        builder.appendLine("  \"variant\": \"${escape(variantName.get())}\",")
        builder.appendLine("  \"generatedAt\": \"${escape(now)}\",")
        builder.appendLine("  \"previewCount\": ${sorted.size},")
        builder.appendLine("  \"previews\": [")
        sorted.forEachIndexed { index, preview ->
            builder.append("    {")
            builder.append("\"id\": \"${escape(preview.id)}\",")
            builder.append(" \"fqcn\": \"${escape(preview.fqcn)}\",")
            builder.append(" \"packageName\": \"${escape(preview.packageName)}\",")
            builder.append(" \"simpleClassName\": \"${escape(preview.simpleClassName)}\",")
            builder.append(" \"methodName\": \"${escape(preview.methodName)}\",")
            builder.append(" \"isComposable\": ${preview.isComposable},")
            builder.append(" \"source\": \"${escape(preview.origin)}\",")
            builder.append(" \"annotation\": ")
            appendJsonObject(builder, preview.annotationValues)
            builder.append(" }")
            if (index != sorted.lastIndex) {
                builder.append(",")
            }
            builder.appendLine()
        }
        builder.appendLine("  ]")
        builder.appendLine("}")
        return builder.toString()
    }

    private fun appendJsonObject(builder: StringBuilder, values: Map<String, Any?>) {
        builder.append("{")
        val entries = values.entries.sortedBy { it.key }
        entries.forEachIndexed { index, entry ->
            builder.append("\"${escape(entry.key)}\": ${entry.value.toJsonValue()}")
            if (index != entries.lastIndex) {
                builder.append(", ")
            }
        }
        builder.append("}")
    }

    private fun Any?.toJsonValue(): String = when (this) {
        null -> "null"
        is String -> "\"${escape(this)}\""
        is Number, is Boolean -> toString()
        is Iterable<*> -> this.joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
        is Array<*> -> this.joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
        else -> "\"${escape(toString())}\""
    }

    private fun escape(input: String): String = buildString(input.length) {
        input.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private sealed interface ClassArtifact {
        val logicalPath: String
        fun openStream(): InputStream
    }

    private class DirectoryClassFile(
        private val file: File,
        override val logicalPath: String
    ) : ClassArtifact {
        override fun openStream(): InputStream = file.inputStream()
    }

    private class JarClassEntry(
        private val bytes: ByteArray,
        override val logicalPath: String
    ) : ClassArtifact {
        override fun openStream(): InputStream = bytes.inputStream()
    }

    private data class PreviewDefinition(
        val fqcn: String,
        val simpleClassName: String,
        val packageName: String,
        val methodName: String,
        val previewIndex: Int,
        val isComposable: Boolean,
        val origin: String,
        val annotationValues: Map<String, Any?>
    ) {
        val id: String = "$fqcn#$methodName[$previewIndex]"
    }

    private class PreviewClassVisitor(
        private val origin: String,
        private val onPreview: (PreviewDefinition) -> Unit
    ) : ClassVisitor(Opcodes.ASM9) {
        private var fqcn: String = ""

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            fqcn = name?.replace('/', '.') ?: ""
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            val methodName = name ?: return super.visitMethod(access, name, descriptor, signature, exceptions)
            if ((access and Opcodes.ACC_SYNTHETIC) != 0) {
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
            return PreviewMethodVisitor(fqcn, methodName, origin, onPreview)
        }
    }

    private class PreviewMethodVisitor(
        private val fqcn: String,
        private val methodName: String,
        private val origin: String,
        private val onPreview: (PreviewDefinition) -> Unit
    ) : MethodVisitor(Opcodes.ASM9) {
        private var isComposable: Boolean = false
        private val previews = mutableListOf<Map<String, Any?>>()

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return when (descriptor) {
                PREVIEW_DESCRIPTOR -> PreviewAnnotationCollector { values ->
                    previews += values
                }
                PREVIEW_CONTAINER_DESCRIPTOR -> PreviewContainerCollector { values ->
                    previews += values
                }
                COMPOSABLE_DESCRIPTOR -> {
                    isComposable = true
                    super.visitAnnotation(descriptor, visible)
                }
                else -> super.visitAnnotation(descriptor, visible)
            }
        }

        override fun visitEnd() {
            if (isComposable && previews.isNotEmpty() && fqcn.isNotBlank()) {
                val packageName = fqcn.substringBeforeLast('.', "")
                val simpleClass = fqcn.substringAfterLast('.')
                previews.forEachIndexed { index, values ->
                    onPreview(
                        PreviewDefinition(
                            fqcn = fqcn,
                            simpleClassName = simpleClass,
                            packageName = packageName,
                            methodName = methodName,
                            previewIndex = index + 1,
                            isComposable = true,
                            origin = origin,
                            annotationValues = values
                        )
                    )
                }
            }
            super.visitEnd()
        }
    }

    private class PreviewAnnotationCollector(
        private val onComplete: (Map<String, Any?>) -> Unit
    ) : AnnotationVisitor(Opcodes.ASM9) {
        private val values = linkedMapOf<String, Any?>()
        private val pendingArrays = mutableMapOf<String, MutableList<Any?>>()

        override fun visit(name: String?, value: Any?) {
            if (name != null) {
                values[name] = value
            }
            super.visit(name, value)
        }

        override fun visitEnum(name: String?, descriptor: String?, value: String?) {
            if (name != null && value != null) {
                values[name] = value
            }
            super.visitEnum(name, descriptor, value)
        }

        override fun visitArray(name: String?): AnnotationVisitor {
            if (name == null) return super.visitArray(name)
            val arrayValues = mutableListOf<Any?>()
            pendingArrays[name] = arrayValues
            values[name] = arrayValues
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String?, value: Any?) {
                    arrayValues += value
                    super.visit(name, value)
                }
            }
        }

        override fun visitEnd() {
            pendingArrays.forEach { (key, list) ->
                values[key] = list.toList()
            }
            onComplete(values.toMap())
            super.visitEnd()
        }
    }

    private class PreviewContainerCollector(
        private val onPreview: (Map<String, Any?>) -> Unit
    ) : AnnotationVisitor(Opcodes.ASM9) {
        override fun visitArray(name: String?): AnnotationVisitor {
            if (name != "value") {
                return super.visitArray(name)
            }
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor {
                    return if (descriptor == PREVIEW_DESCRIPTOR) {
                        PreviewAnnotationCollector(onPreview)
                    } else {
                        super.visitAnnotation(name, descriptor)
                    }
                }
            }
        }
    }

    private companion object {
        private const val PREVIEW_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/Preview;"
        private const val PREVIEW_CONTAINER_DESCRIPTOR =
            "Landroidx/compose/ui/tooling/preview/Preview\$Container;"
        private const val COMPOSABLE_DESCRIPTOR = "Landroidx/compose/runtime/Composable;"
    }
}
