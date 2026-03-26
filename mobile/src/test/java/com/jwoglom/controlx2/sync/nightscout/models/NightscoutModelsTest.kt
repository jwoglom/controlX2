package com.jwoglom.controlx2.sync.nightscout.models

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit tests for Nightscout model classes
 */
class NightscoutModelsTest {

    @Test
    fun testNightscoutEntryFromTimestamp() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val entry = NightscoutEntry.fromTimestamp(
            timestamp = timestamp,
            sgv = 120,
            direction = "Flat",
            seqId = 12345L
        )

        assertEquals(120, entry.sgv)
        assertEquals("Flat", entry.direction)
        assertEquals("sgv", entry.type)
        assertEquals("ControlX2", entry.device)
        assertEquals("12345", entry.identifier)
        assertTrue(entry.dateString.endsWith("Z"))

        val expectedEpochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedEpochMilli, entry.date)
        assertEquals(expectedEpochMilli, Instant.parse(entry.dateString).toEpochMilli())
    }

    @Test
    fun testNightscoutEntryWithoutDirection() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val entry = NightscoutEntry.fromTimestamp(
            timestamp = timestamp,
            sgv = 150,
            direction = null,
            seqId = 99999L
        )

        assertEquals(150, entry.sgv)
        assertNull(entry.direction)
        assertEquals("99999", entry.identifier)
    }

    @Test
    fun testNightscoutTreatmentFromTimestamp() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Bolus",
            timestamp = timestamp,
            seqId = 54321L,
            insulin = 5.5,
            carbs = 45.0,
            duration = null,
            rate = null,
            absolute = null,
            reason = "Meal",
            notes = "Lunch bolus"
        )

        assertEquals("Bolus", treatment.eventType)
        assertNotNull(treatment.insulin)
        assertEquals(5.5, treatment.insulin!!, 0.001)
        assertNotNull(treatment.carbs)
        assertEquals(45.0, treatment.carbs!!, 0.001)
        assertNull(treatment.duration)
        assertNull(treatment.rate)
        assertEquals("Meal", treatment.reason)
        assertEquals("Lunch bolus", treatment.notes)
        assertEquals("ControlX2", treatment.enteredBy)
        assertEquals("ControlX2", treatment.device)
        assertEquals("54321", treatment.pumpId)
        assertTrue(treatment.createdAt.endsWith("Z"))

        val expectedEpochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedEpochMilli, treatment.timestamp)
        assertEquals(expectedEpochMilli, Instant.parse(treatment.createdAt).toEpochMilli())
        assertEquals(timestamp.atZone(ZoneId.systemDefault()).offset.totalSeconds / 60, treatment.utcOffset)
    }

    @Test
    fun testNightscoutTreatmentBasal() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Temp Basal",
            timestamp = timestamp,
            seqId = 11111L,
            insulin = null,
            carbs = null,
            duration = 30,
            rate = 1.5,
            absolute = 1.5,
            reason = "High BG",
            notes = null
        )

        assertEquals("Temp Basal", treatment.eventType)
        assertNull(treatment.insulin)
        assertNull(treatment.carbs)
        assertEquals(30, treatment.duration)
        assertNotNull(treatment.rate)
        assertEquals(1.5, treatment.rate!!, 0.001)
        assertNotNull(treatment.absolute)
        assertEquals(1.5, treatment.absolute!!, 0.001)
        assertEquals("High BG", treatment.reason)
        assertEquals("11111", treatment.pumpId)
    }

    @Test
    fun testCreateDeviceStatus() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val status = createDeviceStatus(
            timestamp = timestamp,
            batteryPercent = 75,
            reservoirUnits = 150.5,
            iob = 3.2,
            pumpStatus = "normal",
            uploaderBattery = 90
        )

        assertEquals("ControlX2", status.device)
        assertTrue(status.createdAt.endsWith("Z"))
        assertEquals(90, status.uploaderBattery)
        assertEquals(timestamp.atZone(ZoneId.systemDefault()).offset.totalSeconds / 60, status.utcOffset)

        assertNotNull(status.pump)
        assertEquals(75, status.pump?.battery?.percent)
        assertNotNull(status.pump?.reservoir)
        assertEquals(150.5, status.pump?.reservoir!!, 0.001)
        assertNotNull(status.pump?.iob?.iob)
        assertEquals(3.2, status.pump?.iob?.iob!!, 0.001)
        assertEquals("normal", status.pump?.status?.status)
        assertEquals(status.createdAt, status.pump?.clock)
        assertEquals(status.createdAt, status.pump?.iob?.timestamp)
        assertEquals(status.createdAt, status.pump?.status?.timestamp)
    }

    @Test
    fun testCreateDeviceStatusPartial() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val status = createDeviceStatus(
            timestamp = timestamp,
            batteryPercent = 50,
            reservoirUnits = null,
            iob = null,
            pumpStatus = null,
            uploaderBattery = null
        )

        assertNotNull(status.pump)
        assertEquals(50, status.pump?.battery?.percent)
        assertNull(status.pump?.reservoir)
        assertNull(status.pump?.iob)
        assertNull(status.pump?.status)
        assertNull(status.uploaderBattery)
    }

    @Test
    fun testIOBStructure() {
        val iob = IOB(
            bolusIob = 2.5,
            iob = 3.0,
            timestamp = "2024-12-12T10:30:00"
        )

        assertNotNull(iob.bolusIob)
        assertEquals(2.5, iob.bolusIob!!, 0.001)
        assertNotNull(iob.iob)
        assertEquals(3.0, iob.iob!!, 0.001)
        assertEquals("2024-12-12T10:30:00", iob.timestamp)
    }

    @Test
    fun testBatteryStructure() {
        val battery = Battery(percent = 85)
        assertEquals(85, battery.percent)
    }

    @Test
    fun testPumpStatusInfoStructure() {
        val statusInfo = PumpStatusInfo(
            status = "suspended",
            bolusing = false,
            suspended = true,
            timestamp = "2024-12-12T10:30:00"
        )

        assertEquals("suspended", statusInfo.status)
        assertEquals(false, statusInfo.bolusing)
        assertEquals(true, statusInfo.suspended)
        assertEquals("2024-12-12T10:30:00", statusInfo.timestamp)
    }

    @Test
    fun testCreateDeviceStatusWithBooleanPumpStatus() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val status = createDeviceStatus(
            timestamp = timestamp,
            batteryPercent = 80,
            iob = 2.0,
            suspended = true,
            bolusing = false,
            uploaderBattery = 65,
            device = "t:slim X2"
        )

        assertEquals("t:slim X2", status.device)
        assertEquals(65, status.uploaderBattery)
        assertNotNull(status.pump?.status)
        assertEquals(true, status.pump?.status?.suspended)
        assertEquals(false, status.pump?.status?.bolusing)
        assertEquals("suspended", status.pump?.status?.status)
    }

    @Test
    fun testCreateDeviceStatusNormalStatus() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val status = createDeviceStatus(
            timestamp = timestamp,
            batteryPercent = 80,
            suspended = false
        )

        assertNotNull(status.pump?.status)
        assertEquals(false, status.pump?.status?.suspended)
        assertEquals("normal", status.pump?.status?.status)
    }

    @Test
    fun testCreateDeviceStatusNoStatusFlags() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val status = createDeviceStatus(
            timestamp = timestamp,
            batteryPercent = 80
        )

        // When no status flags are provided, status should be null
        assertNull(status.pump?.status)
    }

    @Test
    fun testCarbCorrectionTreatment() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Carb Correction",
            timestamp = timestamp,
            seqId = 77777L,
            carbs = 30.0
        )

        assertEquals("Carb Correction", treatment.eventType)
        assertEquals(30.0, treatment.carbs!!, 0.001)
        assertNull(treatment.insulin)
        assertEquals("77777", treatment.pumpId)
        assertEquals("ControlX2", treatment.enteredBy)
    }

    @Test
    fun testComboBolusTreatment() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        // 10U total: 5U now (50%) + 5U over 120min (50%)
        // relative rate = 5U / 120min * 60 = 2.5 U/hr
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Combo Bolus",
            timestamp = timestamp,
            seqId = 88888L,
            insulin = 5.0,           // immediate portion only
            duration = 120,           // extended duration
            enteredInsulin = 10.0,   // total
            splitNow = 50,
            splitExt = 50,
            relative = 2.5           // U/hr
        )

        assertEquals("Combo Bolus", treatment.eventType)
        assertEquals(5.0, treatment.insulin!!, 0.001)
        assertEquals(10.0, treatment.enteredInsulin!!, 0.001)
        assertEquals(50, treatment.splitNow)
        assertEquals(50, treatment.splitExt)
        assertEquals(120, treatment.duration)
        assertEquals(2.5, treatment.relative!!, 0.001)
        assertEquals("88888", treatment.pumpId)
    }

    @Test
    fun testComboBolusSplitsSumTo100() {
        val timestamp = LocalDateTime.of(2024, 12, 12, 10, 30, 0)
        // 70/30 split: 7U now, 3U extended
        val treatment = NightscoutTreatment.fromTimestamp(
            eventType = "Combo Bolus",
            timestamp = timestamp,
            seqId = 99999L,
            insulin = 7.0,
            duration = 60,
            enteredInsulin = 10.0,
            splitNow = 70,
            splitExt = 30,
            relative = 3.0  // 3U / 60min * 60 = 3.0 U/hr
        )

        assertEquals(70, treatment.splitNow)
        assertEquals(30, treatment.splitExt)
        assertEquals(100, treatment.splitNow!! + treatment.splitExt!!)
    }
}
