package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.itemLruCache
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

class VicoCgmChartTest {
    @Before
    fun clearHistoryLogCache() {
        itemLruCache.evictAll()
    }

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
                9_042L,
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
    fun cgmMgDlFromCgmDataStyleCargo_readsSignedInt16AtOffset16() {
        val cargo = ByteArray(26) { 0 }
        cargo[16] = 0x95.toByte()
        cargo[17] = 0x00.toByte()
        assertEquals(149, cgmMgDlFromCgmDataStyleCargo(cargo))
    }

    @Test
    fun toCgmDataPoint_readsLibreFsl3FromGxCompatibleCargoLayout() {
        val pumpWallClock = LocalDateTime.of(2026, 3, 11, 12, 0)
        val rawPumpInstant = pumpWallClock.toInstant(ZoneOffset.UTC)
        val pumpEpochStart = Instant.parse("2008-01-01T00:00:00Z")
        val pumpTimeSec = Duration.between(pumpEpochStart, rawPumpInstant).seconds
        val cargo = CgmDataFsl3HistoryLog.buildCargo(pumpTimeSec, 99L).copyOf()
        cargo[16] = 0x8c.toByte()
        cargo[17] = 0x00.toByte()
        val item = HistoryLogItem(
            seqId = 99L,
            pumpSid = 0,
            typeId = CgmDataFsl3HistoryLog().typeId(),
            cargo = cargo,
            pumpTime = pumpWallClock
        )

        val point = item.toCgmDataPoint()

        assertEquals(140f, point?.value)
    }

    @Test
    fun formatChartTimestamp_formatsUnixEpochSecondsDirectly() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        assertEquals("10:13 PM", formatChartTimestamp(1_700_000_000L, formatter))
    }

    @Test
    fun snapTimestampToBucket_roundsToNearestBucket() {
        assertEquals(
            1_000_000_300L,
            snapTimestampToBucket(
                timestampSeconds = 1_000_000_389L,
                startTimeSeconds = 1_000_000_000L,
                bucketSeconds = 300L,
                bucketCount = 13
            )
        )
        assertEquals(
            1_000_000_600L,
            snapTimestampToBucket(
                timestampSeconds = 1_000_000_451L,
                startTimeSeconds = 1_000_000_000L,
                bucketSeconds = 300L,
                bucketCount = 13
            )
        )
    }

    @Test
    fun toBasalDataPoint_usesBaseRateWhenResumeLikeEventHasZeroCommandRate() {
        val event = BasalRateChangeHistoryLog(
            1_000_000L,
            42L,
            0f,
            0.85f,
            3.0f,
            1,
            8
        )

        val point = event.toBasalDataPoint()

        assertEquals(0.85f, point?.rate)
        assertFalse(point?.isTemp ?: true)
    }

    @Test
    fun activeBasalAtTimestamp_returnsMostRecentEarlierBasalRate() {
        val basalPoints = listOf(
            BasalDataPoint(timestamp = 1_000L, rate = 1.2f, isTemp = false, duration = null),
            BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null)
        )

        val point = activeBasalAtTimestamp(basalPoints, 1_100L)

        assertEquals(1.2f, point?.rate)
    }

    @Test
    fun activeBasalAtTimestamp_fallsBackToEarliestKnownBasalBeforeFirstPoint() {
        val basalPoints = listOf(
            BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
            BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
        )

        val point = activeBasalAtTimestamp(basalPoints, 1_000L)

        assertNull(point)
    }

    @Test
    fun buildBasalIntervals_doesNotBackfillWithoutPreWindowBasalPoint() {
        val intervals = buildBasalIntervals(
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            basalDataPoints = listOf(
                BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
            )
        )

        assertEquals(2, intervals.size)
        assertEquals(1_200L, intervals[0].startTimestamp)
        assertEquals(1_400L, intervals[0].endTimestamp)
        assertEquals(1.4f, intervals[0].rate)
        assertEquals(1_400L, intervals[1].startTimestamp)
        assertEquals(1_600L, intervals[1].endTimestamp)
        assertEquals(1.6f, intervals[1].rate)
    }

    @Test
    fun buildBasalIntervals_backfillsFromChartStartWhenLeadInBasalPointExists() {
        val intervals = buildBasalIntervals(
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            basalDataPoints = listOf(
                BasalDataPoint(timestamp = 900L, rate = 1.2f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
            )
        )

        assertEquals(3, intervals.size)
        assertEquals(1_000L, intervals[0].startTimestamp)
        assertEquals(1_200L, intervals[0].endTimestamp)
        assertEquals(1.2f, intervals[0].rate)
        assertEquals(1_200L, intervals[1].startTimestamp)
        assertEquals(1_400L, intervals[1].endTimestamp)
        assertEquals(1.4f, intervals[1].rate)
        assertEquals(1_400L, intervals[2].startTimestamp)
        assertEquals(1_600L, intervals[2].endTimestamp)
        assertEquals(1.6f, intervals[2].rate)
    }

    @Test
    fun hoverTimestampForPosition_snapsToSharedBucketGrid() {
        assertEquals(
            1_000_000_300L,
            hoverTimestampForPosition(
                x = 50f,
                width = 100f,
                startTimeSeconds = 1_000_000_000L,
                currentTimeSeconds = 1_000_000_600L,
                bucketSeconds = 300L,
                bucketCount = 3
            )
        )
    }
}
