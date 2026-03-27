package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.*
import org.junit.Test

class NightscoutProfileConverterTest {

    @Test
    fun testFormatMinutesAsTime_midnight() {
        assertEquals("00:00", NightscoutProfileConverter.formatMinutesAsTime(0))
    }

    @Test
    fun testFormatMinutesAsTime_sixAm() {
        assertEquals("06:00", NightscoutProfileConverter.formatMinutesAsTime(360))
    }

    @Test
    fun testFormatMinutesAsTime_withMinutes() {
        assertEquals("08:30", NightscoutProfileConverter.formatMinutesAsTime(510))
    }

    @Test
    fun testFormatMinutesAsTime_endOfDay() {
        assertEquals("23:59", NightscoutProfileConverter.formatMinutesAsTime(1439))
    }

    @Test
    fun testFormatMinutesAsTime_noon() {
        assertEquals("12:00", NightscoutProfileConverter.formatMinutesAsTime(720))
    }

    @Test
    fun testConvertBasalRate_standardRate() {
        // 800 milliunits = 0.8 U/hr
        assertEquals(0.8, NightscoutProfileConverter.convertBasalRate(800), 0.001)
    }

    @Test
    fun testConvertBasalRate_zeroRate() {
        assertEquals(0.0, NightscoutProfileConverter.convertBasalRate(0), 0.001)
    }

    @Test
    fun testConvertBasalRate_highRate() {
        // 2500 milliunits = 2.5 U/hr
        assertEquals(2.5, NightscoutProfileConverter.convertBasalRate(2500), 0.001)
    }

    @Test
    fun testConvertBasalRate_preciseRate() {
        // 1250 milliunits = 1.25 U/hr
        assertEquals(1.25, NightscoutProfileConverter.convertBasalRate(1250), 0.001)
    }

    @Test
    fun testConvert_returnsNull_forIncompleteManager() {
        // A fresh IDPManager is not complete
        val idpManager = com.jwoglom.pumpx2.pump.messages.builders.IDPManager()
        assertNull(NightscoutProfileConverter.convert(idpManager))
    }
}
