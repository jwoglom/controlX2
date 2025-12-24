package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerSuspendHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
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
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[PumpingSuspendedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[HypoMinimizerSuspendHistoryLog::class.java]
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

        return when (parsed) {
            is PumpingSuspendedHistoryLog -> {
                val insulinAmount = parsed.insulinAmount / 1000.0
                NightscoutTreatment.fromTimestamp(
                    eventType = "Temp Basal",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    rate = 0.0,
                    absolute = 0.0,
                    duration = null,
                    reason = "Pumping suspended (reason: ${parsed.reasonId})",
                    notes = "Pumping suspended, IOB: ${insulinAmount}U, reason code: ${parsed.reasonId}"
                )
            }
            is HypoMinimizerSuspendHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Temp Basal",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    rate = 0.0,
                    absolute = 0.0,
                    duration = null,
                    reason = "Hypo Minimizer suspend",
                    notes = "Hypo Minimizer suspend"
                )
            }
            else -> {
                Timber.w("Unexpected suspension type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
