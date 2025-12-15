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
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Process CGM readings (glucose values) for Nightscout upload
 *
 * Handles both Dexcom G6 and G7 CGM readings
 * Calculates trend arrows based on recent history if not provided by the pump.
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

        // We need recent history to calculate trends.
        // If the batch is small, we might need to fetch prior logs.
        // If the batch is large, we can use the batch itself.
        
        // Strategy: 
        // 1. Process logs chronologically.
        // 2. Maintain a sliding window of recent readings (time, sgv).
        // 3. For each log, calculate trend based on window.

        // Initialize window with a few logs from DB before the first log in this batch
        val firstLog = logs.minByOrNull { it.seqId }!!
        val recentLogs = historyLogRepo.getLatestItemsForTypes(
            firstLog.pumpSid,
            supportedTypeIds().toList(),
            4 // Get last 4 readings (approx 20 mins)
        ).first().sortedBy { it.pumpTime }

        val window = ArrayDeque<ReadingPoint>()
        recentLogs.forEach { item ->
            val parsed = parseCgm(item)
            if (parsed != null && parsed.first > 0) {
                window.addLast(ReadingPoint(item.pumpTime, parsed.first))
            }
        }

        val entries = logs.sortedBy { it.seqId }.mapNotNull { item ->
            try {
                val parsed = parseCgm(item) ?: return@mapNotNull null
                val (sgv, _) = parsed
                
                if (sgv <= 0) return@mapNotNull null

                // Add current reading to window
                val currentPoint = ReadingPoint(item.pumpTime, sgv)
                window.addLast(currentPoint)
                
                // Prune window: keep points within last 15 mins of current point
                while (window.isNotEmpty() && 
                       ChronoUnit.MINUTES.between(window.first().time, currentPoint.time) > 16) {
                    window.removeFirst()
                }

                // Calculate direction if we have enough points (at least 2, preferably 3 spanning > 10 mins)
                val direction = calculateTrend(window)

                NightscoutEntry.fromTimestamp(
                    timestamp = item.pumpTime,
                    sgv = sgv,
                    direction = direction,
                    seqId = item.seqId
                )
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

    private data class ReadingPoint(val time: java.time.LocalDateTime, val sgv: Int)

    private fun parseCgm(item: HistoryLogItem): Pair<Int, String?>? {
        val parsed = item.parse()
        return when (parsed) {
            is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue to null
            is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue to null
            else -> null
        }
    }

    private fun calculateTrend(window: ArrayDeque<ReadingPoint>): String? {
        if (window.size < 2) return null

        val last = window.last()
        val first = window.first()
        
        // Ensure we have a span of at least 8 minutes to be significant
        val minutesSpan = ChronoUnit.MINUTES.between(first.time, last.time)
        if (minutesSpan < 8) return null

        // Simple linear regression slope or just start/end slope?
        // Nightscout usually expects mg/dL per minute.
        val deltaSgv = last.sgv - first.sgv
        val deltaMinutes = minutesSpan.toDouble()
        
        val slope = deltaSgv / deltaMinutes // mg/dL per minute

        return slopeToDirection(slope)
    }

    private fun slopeToDirection(slope: Double): String {
        // Thresholds based on typical CGM logic (e.g. Dexcom)
        // DoubleUp: > 3 mg/dL/min
        // SingleUp: > 2 mg/dL/min
        // FortyFiveUp: > 1 mg/dL/min
        // Flat: > -1 and < 1
        // FortyFiveDown: < -1
        // SingleDown: < -2
        // DoubleDown: < -3

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

    private fun cgmToNightscoutEntry(item: HistoryLogItem): NightscoutEntry? {
        // This method is now effectively inlined into process() to support stateful calculation
        return null 
    }
}
