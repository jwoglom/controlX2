package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NightscoutProfile model.
 * Validates structure matches Nightscout's expected /api/v1/profile schema.
 */
class NightscoutProfileTest {

    private val gson = Gson()

    @Test
    fun testProfileSerializesToNightscoutFormat() {
        val profile = NightscoutProfile(
            defaultProfile = "Default",
            store = mapOf(
                "Default" to ProfileStore(
                    dia = 5.0,
                    timezone = "America/New_York",
                    units = "mg/dl",
                    carbRatio = listOf(
                        TimeValue("00:00", 15.0, 0),
                        TimeValue("12:00", 12.0, 43200)
                    ),
                    sensitivity = listOf(
                        TimeValue("00:00", 50.0, 0)
                    ),
                    basal = listOf(
                        TimeValue("00:00", 0.8, 0),
                        TimeValue("06:00", 1.0, 21600),
                        TimeValue("22:00", 0.7, 79200)
                    ),
                    targetLow = listOf(TimeValue("00:00", 100.0, 0)),
                    targetHigh = listOf(TimeValue("00:00", 120.0, 0))
                )
            ),
            startDate = "2024-12-12T00:00:00.000Z"
        )

        val json = gson.toJson(profile)

        // Verify key Nightscout field names are present
        assertTrue(json.contains("\"defaultProfile\""))
        assertTrue(json.contains("\"store\""))
        assertTrue(json.contains("\"dia\""))
        assertTrue(json.contains("\"timezone\""))
        assertTrue(json.contains("\"carbratio\""))
        assertTrue(json.contains("\"sens\""))
        assertTrue(json.contains("\"basal\""))
        assertTrue(json.contains("\"target_low\""))
        assertTrue(json.contains("\"target_high\""))
        assertTrue(json.contains("\"timeAsSeconds\""))
        assertTrue(json.contains("\"startDate\""))

        // Verify values
        assertTrue(json.contains("\"America/New_York\""))
        assertTrue(json.contains("\"mg/dl\""))

        // Verify time-indexed array format
        assertTrue(json.contains("\"time\":\"00:00\""))
        assertTrue(json.contains("\"time\":\"06:00\""))
    }

    @Test
    fun testTimeValueFormat() {
        val tv = TimeValue("08:30", 1.2, 30600)

        val json = gson.toJson(tv)
        assertTrue(json.contains("\"time\":\"08:30\""))
        assertTrue(json.contains("\"value\":1.2"))
        assertTrue(json.contains("\"timeAsSeconds\":30600"))
    }
}
