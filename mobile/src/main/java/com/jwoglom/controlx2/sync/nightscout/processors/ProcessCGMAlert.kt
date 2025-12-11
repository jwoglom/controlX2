package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process CGM alerts (high/low glucose) for Nightscout upload
 *
 * Converts CGM alert records to Nightscout "Announcement" treatments
 */
class ProcessCGMAlert(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CGM_ALERT

    override fun supportedTypeIds(): Set<Int> {
        // Try to find CGM alert-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMAlertActivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMAlertClearedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.LowGlucoseAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.HighGlucoseAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMHighAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMLowAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.UrgentLowAlertHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.UrgentLowSoonAlertHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("CGM alert HistoryLog class not found: $className")
                null
            }
        }.toSet()
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                cgmAlertToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert CGM alert seqId=${item.seqId}")
                null
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun cgmAlertToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val alertClass = parsed.javaClass

        // Extract alert data using reflection
        val alertData = extractAlertData(alertClass, parsed)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Announcement",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            reason = alertData.reason,
            notes = alertData.notes
        )
    }

    private data class AlertData(
        val reason: String,
        val notes: String
    )

    private fun extractAlertData(clazz: Class<*>, obj: Any): AlertData {
        try {
            // Try to get alert type/ID
            val alertId = tryGetField<Int>(clazz, obj, "alertId")
                ?: tryGetField<Int>(clazz, obj, "id")

            val alertType = tryGetField<Int>(clazz, obj, "alertType")
                ?: tryGetField<String>(clazz, obj, "alertType")

            // Try to get alert name/description
            val alertName = tryGetField<String>(clazz, obj, "alertName")
                ?: tryGetField<String>(clazz, obj, "name")
                ?: tryGetField<String>(clazz, obj, "description")

            // Try to get glucose value at alert time
            val glucoseValue = tryGetField<Int>(clazz, obj, "glucoseValue")
                ?: tryGetField<Int>(clazz, obj, "bgValue")
                ?: tryGetField<Int>(clazz, obj, "currentGlucose")

            // Try to get threshold value
            val threshold = tryGetField<Int>(clazz, obj, "threshold")
                ?: tryGetField<Int>(clazz, obj, "alertThreshold")

            // Try to get cleared status
            val isCleared = tryGetField<Boolean>(clazz, obj, "cleared")
                ?: tryGetField<Boolean>(clazz, obj, "isCleared")
                ?: false

            // Determine alert category based on class name
            val className = clazz.simpleName
            val alertCategory = when {
                className.contains("Low", ignoreCase = true) -> "Low Glucose"
                className.contains("High", ignoreCase = true) -> "High Glucose"
                className.contains("UrgentLow", ignoreCase = true) -> "Urgent Low"
                else -> "CGM Alert"
            }

            // Build reason
            val reason = buildString {
                if (isCleared) {
                    append("$alertCategory cleared")
                } else {
                    append(alertCategory)
                }

                alertName?.let {
                    append(": $it")
                }

                glucoseValue?.let {
                    append(" (BG: ${it}mg/dL)")
                }
            }

            // Build notes with all available info
            val notes = buildString {
                append(if (isCleared) "$alertCategory cleared" else alertCategory)

                alertType?.let {
                    append(", type: $it")
                }

                alertId?.let {
                    append(", ID: $it")
                }

                alertName?.let {
                    append(", name: $it")
                }

                glucoseValue?.let {
                    append(", glucose: ${it}mg/dL")
                }

                threshold?.let {
                    append(", threshold: ${it}mg/dL")
                }
            }

            return AlertData(
                reason = reason,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting CGM alert data")
            return AlertData("CGM Alert", "CGM alert (details unavailable)")
        }
    }

    private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(obj)
            if (value is T) value else null
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to access field: $fieldName")
            null
        }
    }
}
