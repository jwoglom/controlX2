package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ExtendedBolusHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process bolus deliveries for Nightscout upload
 *
 * Converts insulin bolus records to Nightscout treatments
 * Handles standard and extended boluses.
 */
class ProcessBolus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BOLUS

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BolusDeliveryHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[ExtendedBolusHistoryLog::class.java]
        )
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
                bolusToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert bolus seqId=${item.seqId}")
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

    private fun bolusToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val pumpTime = item.pumpTime

        return when (parsed) {
            is BolusDeliveryHistoryLog -> {
                val bolusData = extractBolusData(parsed)
                NightscoutTreatment.fromTimestamp(
                    eventType = "Bolus",
                    timestamp = pumpTime,
                    seqId = item.seqId,
                    insulin = bolusData.insulin,
                    carbs = bolusData.carbs,
                    notes = bolusData.notes
                )
            }
            is ExtendedBolusHistoryLog -> {
                // Extended bolus (Combo Bolus part 2)
                // "extendedAmount" is the amount delivered over time
                // "duration" is in minutes
                val extendedAmount = tryGetField<Int>(parsed.javaClass, parsed, "extendedAmount")?.div(1000.0)
                    ?: tryGetField<Double>(parsed.javaClass, parsed, "extendedAmount")
                    ?: 0.0
                
                val duration = tryGetField<Int>(parsed.javaClass, parsed, "duration") // minutes
                    ?: 0

                val requestId = tryGetField<Long>(parsed.javaClass, parsed, "bolusRequestId")

                NightscoutTreatment.fromTimestamp(
                    eventType = "Bolus", // Or "Combo Bolus"? Nightscout usually uses "Bolus" with extended fields
                    timestamp = pumpTime,
                    seqId = item.seqId,
                    enteredInsulin = extendedAmount, // Total valid for extended part?
                    relative = extendedAmount,       // The extended portion
                    duration = duration.toDouble(),
                    notes = "Extended Bolus (ID: $requestId)"
                )
            }
            else -> {
                Timber.w("Unexpected bolus type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }

    private data class BolusData(
        val insulin: Double?,
        val carbs: Double?,
        val notes: String?
    )

    private fun extractBolusData(bolus: BolusDeliveryHistoryLog): BolusData {
        try {
            // Try to access common bolus fields using reflection
            val bolusClass = bolus.javaClass

            // Try to get insulin delivered (common field names)
            val insulin = tryGetField<Int>(bolusClass, bolus, "insulinDelivered")
                ?.let { it / 1000.0 } // Convert from milli-units to units
                ?: tryGetField<Int>(bolusClass, bolus, "totalVolumeDelivered")
                    ?.let { it / 1000.0 }
                ?: tryGetField<Double>(bolusClass, bolus, "insulinAmount")

            // Try to get carbs (common field names)
            val carbs = tryGetField<Int>(bolusClass, bolus, "carbSize")?.toDouble()
                ?: tryGetField<Int>(bolusClass, bolus, "carbs")?.toDouble()
                ?: tryGetField<Double>(bolusClass, bolus, "carbAmount")

            // Build notes with available info
            val notes = buildString {
                append("Bolus delivery")
                tryGetField<Long>(bolusClass, bolus, "bolusRequestId")?.let {
                    append(", ID: $it")
                }
                tryGetField<Int>(bolusClass, bolus, "bolusTypeBitmask")?.let {
                    append(", type: $it")
                }
            }

            return BolusData(
                insulin = insulin,
                carbs = carbs,
                notes = notes.takeIf { it.length > "Bolus delivery".length }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting bolus data, using defaults")
            return BolusData(null, null, "Bolus delivery (details unavailable)")
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
