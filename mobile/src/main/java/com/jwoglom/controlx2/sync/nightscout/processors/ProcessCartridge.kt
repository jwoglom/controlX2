package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import timber.log.Timber

/**
 * Process cartridge changes for Nightscout upload
 *
 * Converts cartridge change records to Nightscout "Insulin Change" or "Site Change" treatments
 */
class ProcessCartridge(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARTRIDGE

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CartridgeFilledHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[TubingFilledHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CannulaFilledHistoryLog::class.java]
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
                cartridgeToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert cartridge change seqId=${item.seqId}")
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

    private fun cartridgeToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is CartridgeFilledHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Insulin Change",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    notes = "Cartridge filled with ${parsed.insulinDisplay}U (actual: ${parsed.insulinActual}U)"
                )
            }
            is TubingFilledHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Site Change",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    notes = "Tubing filled/primed with ${parsed.primeSize}U"
                )
            }
            is CannulaFilledHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Site Change",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    notes = "Cannula filled/primed with ${parsed.primeSize}U"
                )
            }
            else -> {
                Timber.w("Unexpected cartridge type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
