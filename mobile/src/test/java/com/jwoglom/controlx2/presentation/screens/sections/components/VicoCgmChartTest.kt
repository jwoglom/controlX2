package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

class VicoCgmChartTest {
    @Test
    fun toCgmDataPoint_appliesPumpLocalTimezoneCorrection() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val pumpWallClock = LocalDateTime.of(2026, 3, 11, 23, 54)
            val rawPumpInstant = pumpWallClock.toInstant(ZoneOffset.UTC)
            val pumpEpochStart = Instant.parse("2008-01-01T00:00:00Z")
            val pumpTimeSec = Duration.between(pumpEpochStart, rawPumpInstant).seconds

            val message = DexcomG6CGMHistoryLog(
                pumpTimeSec,
                42L,
                0,
                1,
                -2,
                6,
                -89,
                149,
                pumpTimeSec,
                481,
                0
            )
            val item = HistoryLogItem(message)

            val point = item.toCgmDataPoint()
            val formatter = SimpleDateFormat("h:mm a", Locale.US)

            assertEquals(149f, point?.value)
            assertEquals("11:54 PM", formatter.format(point!!.timestamp * 1000L))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun formatChartTimestamp_formatsUnixEpochSecondsDirectly() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        assertEquals("10:13 PM", formatChartTimestamp(1_700_000_000L, formatter))
    }
}
