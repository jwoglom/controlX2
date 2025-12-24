package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process Control-IQ user mode changes for Nightscout upload
 *
 * Converts mode change records (Sleep, Exercise, etc.) to Nightscout treatments
 */
class ProcessUserMode(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.USER_MODE

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[ControlIQUserModeChangeHistoryLog::class.java]
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
                modeChangeToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert mode change seqId=${item.seqId}")
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

    private fun modeChangeToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is ControlIQUserModeChangeHistoryLog -> {
                val modeName = when (parsed.currentUserMode) {
                    0 -> "Standard"
                    1 -> "Sleep"
                    2 -> "Exercise"
                    else -> "Mode ${parsed.currentUserMode}"
                }

                val previousModeName = when (parsed.previousUserMode) {
                    0 -> "Standard"
                    1 -> "Sleep"
                    2 -> "Exercise"
                    else -> "Mode ${parsed.previousUserMode}"
                }

                val eventType = if (parsed.currentUserMode == 2) "Exercise" else "Note"

                NightscoutTreatment.fromTimestamp(
                    eventType = eventType,
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    duration = null,
                    reason = "Control-IQ mode changed to $modeName",
                    notes = "Control-IQ mode changed from $previousModeName to $modeName"
                )
            }
            else -> {
                Timber.w("Unexpected mode type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
