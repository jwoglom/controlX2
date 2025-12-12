package com.jwoglom.controlx2.sync.nightscout.models

import org.junit.Assert.*
import org.junit.Test
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
        assertEquals(timestamp.toString(), entry.dateString)

        val expectedEpochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedEpochMilli, entry.date)
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
        assertEquals(timestamp.toString(), treatment.createdAt)

        val expectedEpochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedEpochMilli, treatment.timestamp)
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
        assertEquals(timestamp.toString(), status.createdAt)
        assertEquals(90, status.uploaderBattery)

        assertNotNull(status.pump)
        assertEquals(75, status.pump?.battery?.percent)
        assertNotNull(status.pump?.reservoir)
        assertEquals(150.5, status.pump?.reservoir!!, 0.001)
        assertNotNull(status.pump?.iob?.iob)
        assertEquals(3.2, status.pump?.iob?.iob!!, 0.001)
        assertEquals("normal", status.pump?.status?.status)
        assertEquals(timestamp.toString(), status.pump?.clock)
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
}
