package com.jwoglom.controlx2.sync.nightscout

import java.time.Duration
import java.time.LocalDateTime

/**
 * Calculates Nightscout-compatible trend direction arrows from CGM readings.
 *
 * Uses a simple slope calculation over recent readings, mapping the rate of change
 * (mg/dL per minute) to standard Dexcom trend arrow strings.
 *
 * Thresholds follow the standard Dexcom CGM trend arrow ranges.
 */
object TrendArrowCalculator {

    /**
     * Calculate trend direction from a list of (timestamp, sgv) pairs.
     * Requires at least 2 readings within the last 15 minutes.
     *
     * @return Nightscout direction string, or null if insufficient data
     */
    fun calculateDirection(readings: List<Pair<LocalDateTime, Int>>): String? {
        if (readings.size < 2) return null

        val sorted = readings.sortedBy { it.first }
        val newest = sorted.last()
        val oldest = sorted.first()

        val durationMinutes = Duration.between(oldest.first, newest.first).toMinutes()
        if (durationMinutes < 5 || durationMinutes > 25) return null

        val slopePerMinute = (newest.second - oldest.second).toDouble() / durationMinutes
        return directionFromRate(slopePerMinute)
    }

    /**
     * Map a rate of change (mg/dL per minute) to a Nightscout direction string.
     */
    fun directionFromRate(ratePerMinute: Double): String {
        return when {
            ratePerMinute <= -3.0 -> "DoubleDown"
            ratePerMinute <= -2.0 -> "SingleDown"
            ratePerMinute <= -1.0 -> "FortyFiveDown"
            ratePerMinute < 1.0 -> "Flat"
            ratePerMinute < 2.0 -> "FortyFiveUp"
            ratePerMinute < 3.0 -> "SingleUp"
            else -> "DoubleUp"
        }
    }
}
