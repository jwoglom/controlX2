package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerResumeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
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
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[PumpingResumedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[HypoMinimizerResumeHistoryLog::class.java]
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

        return when (parsed) {
            is PumpingResumedHistoryLog -> {
                val insulinAmount = parsed.insulinAmount / 1000.0
                NightscoutTreatment.fromTimestamp(
                    eventType = "Note",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = "Pumping resumed",
                    notes = "Pumping resumed, IOB: ${insulinAmount}U"
                )
            }
            is HypoMinimizerResumeHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Note",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = "Hypo Minimizer resume",
                    notes = "Hypo Minimizer resume, reason code: ${parsed.reason}"
                )
            }
            else -> {
                Timber.w("Unexpected resume type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
