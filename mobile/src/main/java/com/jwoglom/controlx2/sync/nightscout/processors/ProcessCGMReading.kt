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
 * Handles both Dexcom G6 and G7 CGM readings.
 * Calculates trend direction from slope of recent readings.
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

        // Enrich with trend arrows calculated from slope
        val enrichedEntries = addTrendDirections(entries)

        // Upload to Nightscout
        val result = nightscoutApi.uploadEntries(enrichedEntries)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun cgmToNightscoutEntry(item: HistoryLogItem): NightscoutEntry? {
        val parsed = item.parse()

        val glucoseValue = when (parsed) {
            is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue
            is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue
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
            direction = null,  // Populated later by addTrendDirections
            seqId = item.seqId
        )
    }

    /**
     * Calculate trend direction for each entry based on slope of surrounding readings.
     * Uses a 15-minute window (3 readings at 5-min intervals) to compute mg/dL per minute.
     */
    internal fun addTrendDirections(entries: List<NightscoutEntry>): List<NightscoutEntry> {
        if (entries.size < 3) return entries

        val sorted = entries.sortedBy { it.date }
        return sorted.mapIndexed { index, entry ->
            val direction = calculateTrend(sorted, index)
            if (direction != null) entry.copy(direction = direction) else entry
        }
    }

    internal fun calculateTrend(entries: List<NightscoutEntry>, currentIndex: Int): String? {
        // Need at least 2 prior readings
        if (currentIndex < 2) return null

        val current = entries[currentIndex]
        val prev2 = entries[currentIndex - 2]

        // Verify readings are close in time (within ~20 min to allow for gaps)
        val timeDiffMs = current.date - prev2.date
        if (timeDiffMs > 20 * 60 * 1000 || timeDiffMs <= 0) return null

        // Slope in mg/dL per minute
        val slope = (current.sgv - prev2.sgv).toDouble() / (timeDiffMs / 60000.0)

        return slopeToDirection(slope)
    }

    companion object {
        /**
         * Map slope (mg/dL per minute) to Nightscout trend direction string.
         * Thresholds based on Dexcom trend arrow definitions.
         */
        fun slopeToDirection(slope: Double): String {
            return when {
                slope > 3.0 -> "DoubleUp"
                slope > 2.0 -> "SingleUp"
                slope > 1.0 -> "FortyFiveUp"
                slope > -1.0 -> "Flat"
                slope > -2.0 -> "FortyFiveDown"
                slope > -3.0 -> "SingleDown"
                else -> "DoubleDown"
            }
        }
    }
}
