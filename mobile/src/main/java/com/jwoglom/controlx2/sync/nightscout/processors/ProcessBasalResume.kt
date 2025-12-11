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
 * Process basal resume events for Nightscout upload
 *
 * Converts basal resume records to Nightscout "Note" treatments to mark end of suspension
 */
class ProcessBasalResume(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BASAL_RESUME

    override fun supportedTypeIds(): Set<Int> {
        // Try to find basal resume-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalResumedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinResumedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalResumptionHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ResumePumpHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinDeliveryResumedHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Basal resume HistoryLog class not found: $className")
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
                resumeToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert basal resume seqId=${item.seqId}")
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

    private fun resumeToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val resumeClass = parsed.javaClass

        // Extract resume data using reflection
        val resumeData = extractResumeData(resumeClass, parsed)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Note",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            reason = resumeData.reason,
            notes = resumeData.notes
        )
    }

    private data class ResumeData(
        val reason: String?,
        val notes: String?
    )

    private fun extractResumeData(clazz: Class<*>, obj: Any): ResumeData {
        try {
            // Try to get resume reason
            val reasonCode = tryGetField<Int>(clazz, obj, "resumeReason")
                ?: tryGetField<Int>(clazz, obj, "reason")
                ?: tryGetField<String>(clazz, obj, "resumeReason")
                ?: tryGetField<String>(clazz, obj, "reason")

            val reason = when (reasonCode) {
                is Int -> "Resume reason code: $reasonCode"
                is String -> reasonCode
                else -> "Basal resumed"
            }

            // Build notes with available info
            val notes = buildString {
                append("Basal resumed")

                val resumeType = tryGetField<Int>(clazz, obj, "resumeType")
                resumeType?.let {
                    append(", type: $it")
                }

                val autoResume = tryGetField<Boolean>(clazz, obj, "autoResume")
                if (autoResume == true) {
                    append(" (auto)")
                }

                val newBasalRate = tryGetField<Int>(clazz, obj, "basalRate")
                    ?.let { it / 1000.0 }
                newBasalRate?.let {
                    append(", new rate: ${it}U/hr")
                }
            }

            return ResumeData(
                reason = reason,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting resume data")
            return ResumeData("Basal resumed", "Basal resumed")
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
