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
 * Process pump alarms for Nightscout upload
 *
 * Converts alarm records to Nightscout "Announcement" treatments
 */
class ProcessAlarm(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.ALARM

    override fun supportedTypeIds(): Set<Int> {
        // Try to find alarm-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmActivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmClearedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpAlarmHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertClearedHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Alarm HistoryLog class not found: $className")
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
                alarmToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert alarm seqId=${item.seqId}")
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

    private fun alarmToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val alarmClass = parsed.javaClass

        // Extract alarm data using reflection
        val alarmData = extractAlarmData(alarmClass, parsed)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Announcement",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            reason = alarmData.reason,
            notes = alarmData.notes
        )
    }

    private data class AlarmData(
        val reason: String,
        val notes: String
    )

    private fun extractAlarmData(clazz: Class<*>, obj: Any): AlarmData {
        try {
            // Try to get alarm ID/type
            val alarmId = tryGetField<Int>(clazz, obj, "alarmId")
                ?: tryGetField<Int>(clazz, obj, "alertId")
                ?: tryGetField<Int>(clazz, obj, "id")

            val alarmType = tryGetField<Int>(clazz, obj, "alarmType")
                ?: tryGetField<Int>(clazz, obj, "alertType")
                ?: tryGetField<String>(clazz, obj, "alarmType")
                ?: tryGetField<String>(clazz, obj, "alertType")

            // Try to get alarm description
            val description = tryGetField<String>(clazz, obj, "description")
                ?: tryGetField<String>(clazz, obj, "alarmDescription")
                ?: tryGetField<String>(clazz, obj, "message")

            // Try to get alarm severity
            val severity = tryGetField<Int>(clazz, obj, "severity")
                ?: tryGetField<String>(clazz, obj, "severity")

            // Try to get cleared/activated status
            val isCleared = tryGetField<Boolean>(clazz, obj, "cleared")
                ?: tryGetField<Boolean>(clazz, obj, "isCleared")
                ?: false

            // Build reason
            val reason = buildString {
                if (isCleared) {
                    append("Alarm cleared")
                } else {
                    append("Alarm activated")
                }

                alarmId?.let {
                    append(": ID $it")
                }

                description?.let {
                    append(" - $it")
                }
            }

            // Build notes with all available info
            val notes = buildString {
                append(if (isCleared) "Alarm cleared" else "Pump alarm")

                alarmType?.let {
                    append(", type: $it")
                }

                alarmId?.let {
                    append(", ID: $it")
                }

                severity?.let {
                    append(", severity: $it")
                }

                description?.let {
                    append(", description: $it")
                }
            }

            return AlarmData(
                reason = reason,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting alarm data")
            return AlarmData("Pump alarm", "Pump alarm (details unavailable)")
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
