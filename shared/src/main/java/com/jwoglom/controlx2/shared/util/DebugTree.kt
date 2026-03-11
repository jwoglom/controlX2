package com.jwoglom.controlx2.shared.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant

const val failedForCharacteristicMsg = "writeCharacteristic failed for characteristic: "
class DebugTree(
    val prefix: String,
    val context: Context,
    val logToFile: Boolean,
    val shouldLog: (Int, String) -> Boolean,
    val writeCharacteristicFailedCallback: (String) -> Unit = {},
) : timber.log.Timber.DebugTree() {
    private val maxDebugLogFileBytes = 25L * 1024L * 1024L
    private val trimReserveBytes = 256L * 1024L
    private val fileLock = Any()
    private val fileSizeCache = mutableMapOf<String, Long>()
    private var logFile: File? = null
    private var pumpx2LogFile: File? = null

    /**
     * To view these logs in `adb shell`:
     * $ run-as com.jwoglom.controlx2
     * $ tail -f /data/user/0/com.jwoglom.controlx2/files/debugLog-MUA.txt
     */
    init {
        if (logToFile) {
            logFile = File("${context.filesDir}/debugLog-$prefix.txt")
            logFile?.createNewFile()
            pumpx2LogFile = File("${context.filesDir}/debugLog-$prefix-pumpx2L.txt")
            pumpx2LogFile?.createNewFile()
            appendLogLine("${Instant.now()},$prefix,DebugTree,${
                Log.INFO
            },Debug log initialized (prefix=$prefix),null\n")
            appendLogLine("${Instant.now()},$prefix,DebugTree,${
                Log.INFO
            },PumpX2 L log initialized (prefix=$prefix),null\n", pumpx2LogFile)
            log(Log.INFO, "DebugTree", "Writing to debugLog: ${logFile?.absolutePath}", null)

        }
    }
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // HACK: a bug in timber / Android 13 does not properly notify for characteristic write failures
        if (tag == "BluetoothPeripheral") {
            if (message.startsWith(failedForCharacteristicMsg)) {
                writeCharacteristicFailedCallback(message.removePrefix(failedForCharacteristicMsg).trim())
            }
        }
        super.log(priority, "CX2:${prefix}:$tag", message, t)
        tag?.let {
            val isPumpx2Tag = it.startsWith("L:")
            if (shouldLog(priority, it) || isPumpx2Tag) {
                appendLogLine("${Instant.now()},$prefix,$tag,$priority,$message,$t\n")
            }
            if (isPumpx2Tag) {
                appendLogLine("${Instant.now()},$prefix,$tag,$priority,$message,$t\n", pumpx2LogFile)
            }
        }
    }

    private fun appendLogLine(line: String, targetFile: File? = logFile) {
        val file = targetFile ?: return
        val lineBytes = line.toByteArray(Charsets.UTF_8)

        try {
            synchronized(fileLock) {
                if (!file.exists()) {
                    file.createNewFile()
                    setCachedFileSize(file, 0L)
                }

                var currentSize = getCachedFileSize(file)
                if (currentSize + lineBytes.size > maxDebugLogFileBytes) {
                    if (lineBytes.size >= maxDebugLogFileBytes.toInt()) {
                        FileOutputStream(file, false).use { output ->
                            output.write(
                                lineBytes.copyOfRange(
                                    lineBytes.size - maxDebugLogFileBytes.toInt(),
                                    lineBytes.size
                                )
                            )
                        }
                        setCachedFileSize(file, maxDebugLogFileBytes)
                        return
                    }

                    // Trim extra old data so we don't need to re-trim on nearly every append.
                    val bytesToKeep = (maxDebugLogFileBytes - lineBytes.size - trimReserveBytes)
                        .coerceAtLeast(0L)
                    trimFileToLastBytes(file, bytesToKeep)
                    currentSize = file.length()
                    setCachedFileSize(file, currentSize)
                }

                FileOutputStream(file, true).use { output ->
                    output.write(lineBytes)
                }
                setCachedFileSize(file, currentSize + lineBytes.size)
            }
        } catch (_: Exception) {
            // Avoid crashing the app if log file writes fail.
        }
    }

    private fun getCachedFileSize(file: File): Long {
        val key = file.absolutePath
        return fileSizeCache[key] ?: file.length().also { fileSizeCache[key] = it }
    }

    private fun setCachedFileSize(file: File, size: Long) {
        fileSizeCache[file.absolutePath] = size
    }

    private fun trimFileToLastBytes(file: File, bytesToKeep: Long) {
        val originalLength = file.length()
        if (originalLength <= bytesToKeep) {
            return
        }

        val tempFile = File(file.parentFile, "${file.name}.tmp")
        RandomAccessFile(file, "r").use { input ->
            input.seek(originalLength - bytesToKeep)
            FileOutputStream(tempFile, false).use { output ->
                val buffer = ByteArray(8192)
                var remaining = bytesToKeep
                while (remaining > 0) {
                    val bytesRead = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (bytesRead <= 0) {
                        break
                    }
                    output.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                }
            }
        }

        val replaced = file.delete() && tempFile.renameTo(file)
        if (!replaced) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }
}
