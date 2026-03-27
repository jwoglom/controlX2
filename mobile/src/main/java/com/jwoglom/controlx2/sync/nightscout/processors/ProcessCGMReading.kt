package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.TrendArrowCalculator
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Process CGM readings (glucose values) for Nightscout upload
 *
 * Handles both Dexcom G6 and G7 CGM readings, and calculates trend
 * direction arrows from recent readings since the pump history logs
 * don't include trend data.
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

        // Parse all readings first for trend calculation
        val parsedReadings = logs.mapNotNull { item ->
            try {
                val (sgv, _) = extractGlucose(item) ?: return@mapNotNull null
                if (sgv <= 0) return@mapNotNull null
                Triple(item, item.pumpTime, sgv)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse CGM reading seqId=${item.seqId}")
                null
            }
        }.sortedBy { it.second }

        if (parsedReadings.isEmpty()) {
            Timber.d("${processorName()}: No entries to upload")
            return 0
        }

        // Build entries with trend directions calculated from sliding window
        val entries = parsedReadings.mapIndexed { index, (item, timestamp, sgv) ->
            // Use up to the last 3 readings ending at this one for trend calculation
            val windowStart = maxOf(0, index - 2)
            val window = parsedReadings.subList(windowStart, index + 1)
                .map { it.second to it.third }

            val direction = TrendArrowCalculator.calculateDirection(window)

            NightscoutEntry.fromTimestamp(
                timestamp = timestamp,
                sgv = sgv,
                direction = direction,
                seqId = item.seqId
            )
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadEntries(entries)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun extractGlucose(item: HistoryLogItem): Pair<Int, Nothing?>? {
        return when (val parsed = item.parse()) {
            is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue to null
            is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue to null
            else -> {
                Timber.w("Unexpected CGM type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
