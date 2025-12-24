package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process bolus deliveries for Nightscout upload
 *
 * Converts insulin bolus records to Nightscout treatments
 */
class ProcessBolus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BOLUS

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BolusDeliveryHistoryLog::class.java]
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

        if (parsed !is BolusDeliveryHistoryLog) {
            Timber.w("Unexpected bolus type: ${parsed.javaClass.simpleName}")
            return null
        }

        // Extract bolus data
        val bolusData = extractBolusData(parsed)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Bolus",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            insulin = bolusData.insulin,
            carbs = null, // Carbs are not stored in BolusDeliveryHistoryLog
            notes = bolusData.notes
        )
    }

    private data class BolusData(
        val insulin: Double?,
        val notes: String?
    )

    private fun extractBolusData(bolus: BolusDeliveryHistoryLog): BolusData {
        try {
            // Get insulin delivered (in milliunits, convert to units)
            val insulin = bolus.deliveredTotal / 1000.0

            // Build notes with bolus details
            val bolusTypes = bolus.bolusTypes
            val bolusSource = bolus.bolusSource

            val notes = buildString {
                append("Bolus delivery")

                if (bolusTypes.isNotEmpty()) {
                    append(", types: ${bolusTypes.joinToString(", ") { it.name }}")
                }

                bolusSource?.let {
                    append(", source: ${it.name}")
                }

                append(", ID: ${bolus.bolusID}")

                if (bolus.requestedNow > 0) {
                    append(", requested: ${bolus.requestedNow / 1000.0}U")
                }

                if (bolus.correction > 0) {
                    append(", correction: ${bolus.correction / 1000.0}U")
                }
            }

            return BolusData(
                insulin = insulin,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting bolus data, using defaults")
            return BolusData(null, "Bolus delivery (details unavailable)")
        }
    }
}
