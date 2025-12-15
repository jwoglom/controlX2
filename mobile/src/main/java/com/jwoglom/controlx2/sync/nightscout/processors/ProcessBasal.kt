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
 * Process basal rate deliveries for Nightscout upload
 *
 * Converts basal delivery records to Nightscout "Temp Basal" treatments
 */
class ProcessBasal(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BASAL

    override fun supportedTypeIds(): Set<Int> {
        // Try to find basal-related HistoryLog types
        // Common naming patterns in pumpx2 library
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateStartedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalActivatedHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Basal HistoryLog class not found: $className")
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
                basalToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert basal seqId=${item.seqId}")
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

    private fun basalToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val basalClass = parsed.javaClass

        // Special handling for TempRateCompletedHistoryLog - indicates end of temp basal
        if (basalClass.simpleName.contains("TempRateCompleted", ignoreCase = true)) {
            return NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = item.pumpTime,
                seqId = item.seqId,
                rate = 0.0, // Should reset to scheduled? 0 usually means cancel/none active? 
                            // Or does it mean "back to pattern"?
                            // Nightscout: duration=0 cancels temp basal.
                absolute = null, // don't set absolute if we are cancelling
                duration = 0.0, // Duration 0 means cancel/end
                notes = "Temp rate completed"
            )
        }

        // Extract basal data using reflection
        val basalData = extractBasalData(basalClass, parsed)

        // Skip if we couldn't extract any meaningful data
        if (basalData.rate == null && basalData.duration == null) {
            Timber.d("Skipping basal with no rate or duration data")
            return null
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = "Temp Basal",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            rate = basalData.rate,
            absolute = basalData.rate, // Nightscout uses absolute for the actual rate
            duration = basalData.duration?.toDouble(),
            notes = basalData.notes
        )
    }

    private data class BasalData(
        val rate: Double?,
        val duration: Int?,
        val notes: String?
    )

    private fun extractBasalData(clazz: Class<*>, obj: Any): BasalData {
        try {
            // Try to get basal rate (common field names)
            val rate = tryGetField<Int>(clazz, obj, "basalRate")
                ?.let { it / 1000.0 } // Convert from milli-units/hr to units/hr
                ?: tryGetField<Int>(clazz, obj, "rate")
                    ?.let { it / 1000.0 }
                ?: tryGetField<Double>(clazz, obj, "basalRate")
                ?: tryGetField<Double>(clazz, obj, "rate")

            // Try to get duration (common field names)
            val durationSeconds = tryGetField<Int>(clazz, obj, "duration")
                ?: tryGetField<Long>(clazz, obj, "duration")?.toInt()
                ?: tryGetField<Int>(clazz, obj, "durationSeconds")
                ?: tryGetField<Long>(clazz, obj, "durationSeconds")?.toInt()

            val duration = durationSeconds?.let { it / 60 } // Convert seconds to minutes

            // Build notes with available info
            val notes = buildString {
                append("Basal delivery")

                val basalType = tryGetField<Int>(clazz, obj, "basalType")
                    ?: tryGetField<String>(clazz, obj, "basalType")

                basalType?.let {
                    append(", type: $it")
                }

                val percentageAdjustment = tryGetField<Int>(clazz, obj, "percentageAdjustment")
                percentageAdjustment?.let {
                    append(", adjustment: $it%")
                }
            }

            return BasalData(
                rate = rate,
                duration = duration,
                notes = notes.takeIf { it.length > "Basal delivery".length }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting basal data")
            return BasalData(null, null, "Basal delivery (details unavailable)")
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
