package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process carb entries for Nightscout upload
 *
 * Converts CarbEnteredHistoryLog records to Nightscout "Carb Correction" treatments.
 * Per Nightscout spec, "Carb Correction" is the standard eventType for carb-only entries.
 */
class ProcessCarb(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARB

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CarbEnteredHistoryLog::class.java]
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
                carbToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert carb entry seqId=${item.seqId}")
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

    private fun carbToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        if (parsed !is CarbEnteredHistoryLog) {
            Timber.w("Unexpected carb type: ${parsed.javaClass.simpleName}")
            return null
        }

        val carbs = parsed.carbs.toInt()
        if (carbs <= 0) {
            Timber.d("Skipping zero/negative carb entry: $carbs")
            return null
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = "Carb Correction",
            timestamp = item.pumpTime,
            seqId = item.seqId,
            carbs = carbs.toDouble(),
            notes = "Carb entry: ${carbs}g"
        )
    }
}
