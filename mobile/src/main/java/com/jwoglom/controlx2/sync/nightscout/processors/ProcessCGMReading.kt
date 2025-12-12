package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process CGM readings (glucose values) for Nightscout upload
 *
 * Handles both Dexcom G6 and G7 CGM readings
 */
class ProcessCGMReading(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CGM_READING

    override fun supportedTypeIds(): Set<Int> {
        return setOf(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[DexcomG6CGMHistoryLog::class.java] ?: -1,
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[DexcomG7CGMHistoryLog::class.java] ?: -1
        ).filter { it != -1 }.toSet()
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout entries
        val entries = logs.mapNotNull { item ->
            try {
                cgmToNightscoutEntry(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert CGM reading seqId=${item.seqId}")
                null
            }
        }

        if (entries.isEmpty()) {
            Timber.d("${processorName()}: No entries to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadEntries(entries)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun cgmToNightscoutEntry(item: HistoryLogItem): NightscoutEntry? {
        val parsed = item.parse()

        val (glucoseValue, direction) = when (parsed) {
            is DexcomG6CGMHistoryLog -> {
                val value = parsed.currentGlucoseDisplayValue
                // G6 doesn't provide trend direction in history logs
                value to null
            }
            is DexcomG7CGMHistoryLog -> {
                val value = parsed.currentGlucoseDisplayValue
                // G7 doesn't provide trend direction in history logs
                value to null
            }
            else -> {
                Timber.w("Unexpected CGM type: ${parsed.javaClass.simpleName}")
                return null
            }
        }

        // Skip invalid readings
        if (glucoseValue <= 0) {
            Timber.d("Skipping invalid glucose value: $glucoseValue")
            return null
        }

        return NightscoutEntry.fromTimestamp(
            timestamp = item.pumpTime,
            sgv = glucoseValue,
            direction = direction,
            seqId = item.seqId
        )
    }
}
