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
 * Handles standard boluses as "Bolus" events and extended/combo boluses
 * as "Combo Bolus" events per the Nightscout treatment schema.
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

        val totalInsulin = parsed.deliveredTotal / 1000.0
        val immediateInsulin = parsed.requestedNow / 1000.0
        val extendedInsulin = parsed.requestedLater / 1000.0
        val extendedDurationMinutes = parsed.extendedDurationRequested

        val isExtended = extendedInsulin > 0 && extendedDurationMinutes > 0

        return if (isExtended) {
            buildComboBolus(item, parsed, totalInsulin, immediateInsulin, extendedInsulin, extendedDurationMinutes)
        } else {
            buildStandardBolus(item, parsed, totalInsulin)
        }
    }

    private fun buildStandardBolus(
        item: HistoryLogItem,
        bolus: BolusDeliveryHistoryLog,
        totalInsulin: Double
    ): NightscoutTreatment {
        val notes = buildBolusNotes(bolus)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Bolus",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            insulin = totalInsulin,
            notes = notes
        )
    }

    private fun buildComboBolus(
        item: HistoryLogItem,
        bolus: BolusDeliveryHistoryLog,
        totalInsulin: Double,
        immediateInsulin: Double,
        extendedInsulin: Double,
        extendedDurationMinutes: Int
    ): NightscoutTreatment {
        // Per Nightscout spec:
        // insulin = immediate portion only
        // enteredinsulin = total (immediate + extended)
        // splitNow/splitExt = percentages (must sum to 100)
        // relative = extended rate in U/hr
        // duration = extended duration in minutes
        val splitNow = if (totalInsulin > 0) {
            ((immediateInsulin / totalInsulin) * 100).toInt()
        } else 0
        val splitExt = 100 - splitNow

        val relativeRate = if (extendedDurationMinutes > 0) {
            extendedInsulin / extendedDurationMinutes * 60
        } else 0.0

        val notes = buildString {
            append(buildBolusNotes(bolus))
            append(", combo: ${immediateInsulin}U now + ${extendedInsulin}U over ${extendedDurationMinutes}min")
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = "Combo Bolus",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            insulin = immediateInsulin,
            duration = extendedDurationMinutes,
            notes = notes,
            enteredInsulin = totalInsulin,
            splitNow = splitNow,
            splitExt = splitExt,
            relative = relativeRate
        )
    }

    private fun buildBolusNotes(bolus: BolusDeliveryHistoryLog): String {
        return buildString {
            append("Bolus delivery")

            try {
                val bolusTypes = bolus.bolusTypes
                if (bolusTypes.isNotEmpty()) {
                    append(", types: ${bolusTypes.joinToString(", ") { it.name }}")
                }

                bolus.bolusSource?.let {
                    append(", source: ${it.name}")
                }

                append(", ID: ${bolus.bolusID}")

                if (bolus.requestedNow > 0) {
                    append(", requested: ${bolus.requestedNow / 1000.0}U")
                }

                if (bolus.correction > 0) {
                    append(", correction: ${bolus.correction / 1000.0}U")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error building bolus notes")
            }
        }
    }
}
