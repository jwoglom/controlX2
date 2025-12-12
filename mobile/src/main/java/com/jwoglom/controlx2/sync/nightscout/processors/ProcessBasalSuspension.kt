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
 * Process basal suspension events for Nightscout upload
 *
 * Converts basal suspension records to Nightscout "Temp Basal" treatments with rate=0
 */
class ProcessBasalSuspension(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BASAL_SUSPENSION

    override fun supportedTypeIds(): Set<Int> {
        // Try to find basal suspension-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalSuspendedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinSuspendedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalSuspensionHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.SuspendPumpHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinDeliverySuspendedHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Basal suspension HistoryLog class not found: $className")
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
                suspensionToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert basal suspension seqId=${item.seqId}")
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

    private fun suspensionToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val suspensionClass = parsed.javaClass

        // Extract suspension data using reflection
        val suspensionData = extractSuspensionData(suspensionClass, parsed)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Temp Basal",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            rate = 0.0, // Suspension means 0 basal rate
            absolute = 0.0,
            duration = suspensionData.duration,
            reason = suspensionData.reason,
            notes = suspensionData.notes
        )
    }

    private data class SuspensionData(
        val duration: Int?,
        val reason: String?,
        val notes: String?
    )

    private fun extractSuspensionData(clazz: Class<*>, obj: Any): SuspensionData {
        try {
            // Try to get duration (common field names)
            val durationSeconds = tryGetField<Int>(clazz, obj, "duration")
                ?: tryGetField<Long>(clazz, obj, "duration")?.toInt()
                ?: tryGetField<Int>(clazz, obj, "durationSeconds")
                ?: tryGetField<Long>(clazz, obj, "durationSeconds")?.toInt()

            val duration = durationSeconds?.let { it / 60 } // Convert seconds to minutes

            // Try to get suspension reason
            val reasonCode = tryGetField<Int>(clazz, obj, "suspendReason")
                ?: tryGetField<Int>(clazz, obj, "reason")
                ?: tryGetField<String>(clazz, obj, "suspendReason")
                ?: tryGetField<String>(clazz, obj, "reason")

            val reason = when (reasonCode) {
                is Int -> "Suspension reason code: $reasonCode"
                is String -> reasonCode
                else -> "Basal suspended"
            }

            // Build notes with available info
            val notes = buildString {
                append("Basal suspension")

                val suspendType = tryGetField<Int>(clazz, obj, "suspendType")
                suspendType?.let {
                    append(", type: $it")
                }

                val autoSuspend = tryGetField<Boolean>(clazz, obj, "autoSuspend")
                if (autoSuspend == true) {
                    append(" (auto)")
                }
            }

            return SuspensionData(
                duration = duration,
                reason = reason,
                notes = notes.takeIf { it.length > "Basal suspension".length }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting suspension data")
            return SuspensionData(null, "Basal suspended", "Basal suspension")
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
