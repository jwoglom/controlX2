package com.jwoglom.controlx2.sync.nightscout.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class NightscoutTimestampModelsTest {
    @Test
    fun treatmentAndEntry_useTimezoneAwareIsoAndMatchingEpoch_inUtcAndTokyo() {
        assertModelTimestampsForZone("UTC")
        assertModelTimestampsForZone("Asia/Tokyo")
    }

    @Test
    fun timestamps_areStableAcrossDstBoundaryInLosAngeles() {
        withDefaultTimeZone("America/Los_Angeles") {
            val preDst = LocalDateTime.of(2024, 3, 10, 1, 30, 0, 0)
            val postDst = LocalDateTime.of(2024, 3, 10, 3, 30, 0, 0)

            val preTreatment = NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = preDst,
                seqId = 10L
            )
            val postTreatment = NightscoutTreatment.fromTimestamp(
                eventType = "Temp Basal",
                timestamp = postDst,
                seqId = 11L
            )

            assertIsoTimezoneAware(preTreatment.createdAt)
            assertIsoTimezoneAware(postTreatment.createdAt)
            assertEquals(-480, preTreatment.utcOffset)
            assertEquals(-420, postTreatment.utcOffset)

            val preInstant = Instant.parse(preTreatment.createdAt)
            val postInstant = Instant.parse(postTreatment.createdAt)
            assertEquals(preInstant.toEpochMilli(), preTreatment.timestamp)
            assertEquals(postInstant.toEpochMilli(), postTreatment.timestamp)

            val expectedPreEpoch = preDst.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val expectedPostEpoch = postDst.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(expectedPreEpoch, preTreatment.timestamp)
            assertEquals(expectedPostEpoch, postTreatment.timestamp)
        }
    }

    @Test
    fun regression_oldNaiveLocalDateTimeStringIsZoneLess_butNewOutputIsParsableInstant() {
        withDefaultTimeZone("America/New_York") {
            val timestamp = LocalDateTime.of(2026, 3, 15, 12, 34, 56, 789_000_000)

            val oldNaiveString = timestamp.toString()
            assertFalse(oldNaiveString.endsWith("Z"))
            assertFalse(oldNaiveString.contains("+"))

            val treatment = NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = timestamp,
                seqId = 99L,
                insulin = 1.0
            )

            assertIsoTimezoneAware(treatment.createdAt)
            val parsed = Instant.parse(treatment.createdAt)
            assertEquals(parsed.toEpochMilli(), treatment.timestamp)
        }
    }

    private fun assertModelTimestampsForZone(zoneId: String) {
        withDefaultTimeZone(zoneId) {
            val timestamp = LocalDateTime.of(2026, 3, 15, 12, 34, 56, 789_000_000)

            val treatment = NightscoutTreatment.fromTimestamp(
                eventType = "Bolus",
                timestamp = timestamp,
                seqId = 1L,
                insulin = 1.25
            )
            val entry = NightscoutEntry.fromTimestamp(
                timestamp = timestamp,
                sgv = 123,
                direction = "Flat",
                seqId = 2L
            )
            val status = createDeviceStatus(
                timestamp = timestamp,
                batteryPercent = 80,
                iob = 0.55
            )

            assertIsoTimezoneAware(treatment.createdAt)
            assertIsoTimezoneAware(entry.dateString)
            assertIsoTimezoneAware(status.createdAt)
            assertIsoTimezoneAware(status.pump?.clock ?: "")
            assertIsoTimezoneAware(status.pump?.iob?.timestamp ?: "")

            val treatmentInstant = Instant.parse(treatment.createdAt)
            val entryInstant = Instant.parse(entry.dateString)
            val statusInstant = Instant.parse(status.createdAt)

            assertEquals(treatmentInstant.toEpochMilli(), treatment.timestamp)
            assertEquals(entryInstant.toEpochMilli(), entry.date)

            val expectedOffsetMinutes = timestamp.atZone(ZoneId.systemDefault()).offset.totalSeconds / 60
            assertEquals(expectedOffsetMinutes, treatment.utcOffset)
            assertEquals(expectedOffsetMinutes, status.utcOffset)

            val expectedEpoch = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(expectedEpoch, treatment.timestamp)
            assertEquals(expectedEpoch, entry.date)
            assertEquals(expectedEpoch, statusInstant.toEpochMilli())
        }
    }

    private fun assertIsoTimezoneAware(value: String) {
        val hasZoneSuffix = value.endsWith("Z") || value.contains("+") || value.matches(Regex(".*-[0-9]{2}:[0-9]{2}$"))
        assertTrue("Expected timezone-aware ISO timestamp but got: $value", hasZoneSuffix)
    }

    private fun withDefaultTimeZone(zoneId: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
