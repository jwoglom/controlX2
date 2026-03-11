package com.jwoglom.controlx2.presentation.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SupportBundleSummary(
    val debugFileCount: Int,
    val totalLogLines: Long,
    val rangeStart: String,
    val rangeEnd: String
)

val supportEmail = "controlx2" + "@" + "wogloms.net"

fun formatLogLineCount(totalLogLines: Long): String {
    return String.format(Locale.US, "%,d", totalLogLines)
}

private val bundleTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

private fun formatBundleFilenameTimestamp(timestamp: Instant): String {
    return bundleTimestampFormatter.format(timestamp)
}

private fun listDebugLogFiles(context: Context): List<File> {
    return context.filesDir.listFiles { file ->
        file.isFile && file.name.startsWith("debugLog-") && file.name.endsWith(".txt")
    }?.sortedBy { it.name }.orEmpty()
}

private data class SupportBundleData(
    val summary: SupportBundleSummary,
    val debugLogs: List<File>
)

private fun countFileLines(file: File): Long {
    return file.bufferedReader().useLines { lines -> lines.count().toLong() }
}

private fun getSupportBundleData(context: Context): SupportBundleData? {
    val debugLogs = listDebugLogFiles(context)
    if (debugLogs.isEmpty()) {
        return null
    }

    val totalLogLines = debugLogs.sumOf { countFileLines(it) }
    val minModified = debugLogs.minOf { it.lastModified() }
    val maxModified = debugLogs.maxOf { it.lastModified() }
    val summary = SupportBundleSummary(
        debugFileCount = debugLogs.size,
        totalLogLines = totalLogLines,
        rangeStart = Instant.ofEpochMilli(minModified).toString(),
        rangeEnd = Instant.ofEpochMilli(maxModified).toString()
    )
    return SupportBundleData(summary, debugLogs)
}

fun getSupportBundleSummary(context: Context): SupportBundleSummary? {
    return getSupportBundleData(context)?.summary
}

private fun createSupportBundleZip(context: Context, timestamp: String, debugLogs: List<File>): File {
    val bundleFile = File(context.filesDir, "controlx2-support-bundle-$timestamp.zip")
    ZipOutputStream(FileOutputStream(bundleFile, false)).use { zipOutputStream ->
        debugLogs.forEach { logFile ->
            zipOutputStream.putNextEntry(ZipEntry(logFile.name))
            logFile.inputStream().use { input ->
                input.copyTo(zipOutputStream)
            }
            zipOutputStream.closeEntry()
        }
    }
    return bundleFile
}

fun shareSupportBundle(context: Context) {
    val supportBundleData = getSupportBundleData(context)
    if (supportBundleData == null) {
        Toast.makeText(context, "No debug logs are available to share.", Toast.LENGTH_SHORT).show()
        return
    }

    val now = Instant.now()
    val filenameTimestamp = formatBundleFilenameTimestamp(now)
    val subjectTimestamp = now.toString()
    val bundleFile = createSupportBundleZip(context, filenameTimestamp, supportBundleData.debugLogs)
    Toast.makeText(
        context,
        "${supportBundleData.summary.debugFileCount} debug logs found",
        Toast.LENGTH_SHORT
    ).show()

    val uri = FileProvider.getUriForFile(context, context.packageName, bundleFile)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "PumpX2 Support Bundle - $subjectTimestamp")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    context.startActivity(Intent.createChooser(intent, "Send PumpX2 Support Bundle"))
}

fun sendSupportBundleEmail(context: Context) {
    val supportBundleData = getSupportBundleData(context)
    if (supportBundleData == null) {
        Toast.makeText(context, "No debug logs are available to share.", Toast.LENGTH_SHORT).show()
        return
    }

    val now = Instant.now()
    val filenameTimestamp = formatBundleFilenameTimestamp(now)
    val subjectTimestamp = now.toString()
    val bundleFile = createSupportBundleZip(context, filenameTimestamp, supportBundleData.debugLogs)
    Toast.makeText(
        context,
        "${supportBundleData.summary.debugFileCount} debug logs found",
        Toast.LENGTH_SHORT
    ).show()

    val uri = FileProvider.getUriForFile(context, context.packageName, bundleFile)
    val summary = supportBundleData.summary
    val totalLogLines = formatLogLineCount(summary.totalLogLines)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
        .putExtra(Intent.EXTRA_SUBJECT, "PumpX2 Support Bundle - $subjectTimestamp")
        .putExtra(Intent.EXTRA_TEXT, "Contains ${summary.debugFileCount} debug files with $totalLogLines total logs from ${summary.rangeStart} - ${summary.rangeEnd}")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    context.startActivity(Intent.createChooser(intent, "Send PumpX2 Support Bundle Email"))
}
