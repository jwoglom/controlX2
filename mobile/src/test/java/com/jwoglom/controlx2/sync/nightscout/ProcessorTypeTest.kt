package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ProcessorType enum
 */
class ProcessorTypeTest {

    @Test
    fun testFromName() {
        assertEquals(ProcessorType.CGM_READING, ProcessorType.fromName("CGM_READING"))
        assertEquals(ProcessorType.BOLUS, ProcessorType.fromName("BOLUS"))
        assertEquals(ProcessorType.BASAL, ProcessorType.fromName("BASAL"))
        assertNull(ProcessorType.fromName("INVALID"))
    }

    @Test
    fun testFromNameCaseInsensitive() {
        assertEquals(ProcessorType.CGM_READING, ProcessorType.fromName("cgm_reading"))
        assertEquals(ProcessorType.BOLUS, ProcessorType.fromName("Bolus"))
        assertEquals(ProcessorType.BASAL, ProcessorType.fromName("bAsAl"))
    }

    @Test
    fun testAll() {
        val all = ProcessorType.all()
        assertEquals(10, all.size)
        assertTrue(all.contains(ProcessorType.CGM_READING))
        assertTrue(all.contains(ProcessorType.BOLUS))
        assertTrue(all.contains(ProcessorType.BASAL))
        assertTrue(all.contains(ProcessorType.BASAL_SUSPENSION))
        assertTrue(all.contains(ProcessorType.BASAL_RESUME))
        assertTrue(all.contains(ProcessorType.ALARM))
        assertTrue(all.contains(ProcessorType.CGM_ALERT))
        assertTrue(all.contains(ProcessorType.USER_MODE))
        assertTrue(all.contains(ProcessorType.CARTRIDGE))
        assertTrue(all.contains(ProcessorType.DEVICE_STATUS))
    }

    @Test
    fun testDisplayNames() {
        assertEquals("CGM Readings", ProcessorType.CGM_READING.displayName)
        assertEquals("Bolus", ProcessorType.BOLUS.displayName)
        assertEquals("Basal", ProcessorType.BASAL.displayName)
        assertEquals("Basal Suspension", ProcessorType.BASAL_SUSPENSION.displayName)
        assertEquals("Basal Resume", ProcessorType.BASAL_RESUME.displayName)
        assertEquals("Alarms", ProcessorType.ALARM.displayName)
        assertEquals("CGM Alerts", ProcessorType.CGM_ALERT.displayName)
        assertEquals("User Mode", ProcessorType.USER_MODE.displayName)
        assertEquals("Cartridge", ProcessorType.CARTRIDGE.displayName)
        assertEquals("Device Status", ProcessorType.DEVICE_STATUS.displayName)
    }

    @Test
    fun testEnumNames() {
        assertEquals("CGM_READING", ProcessorType.CGM_READING.name)
        assertEquals("BOLUS", ProcessorType.BOLUS.name)
        assertEquals("BASAL", ProcessorType.BASAL.name)
    }

    @Test
    fun testAllDistinct() {
        val all = ProcessorType.all()
        val distinctNames = all.map { it.name }.toSet()
        assertEquals(all.size, distinctNames.size)
    }
}
