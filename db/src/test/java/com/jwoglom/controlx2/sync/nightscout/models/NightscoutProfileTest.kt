package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NightscoutProfile model.
 * Validates round-trip serialization matches Nightscout's /api/v1/profile schema.
 */
class NightscoutProfileTest {

    private val gson = Gson()

    @Test
    fun testProfileRoundTripsWithCorrectFieldNames() {
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

        // Serialize to JSON and parse as raw map to verify Nightscout field names
        val json = gson.toJson(profile)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val raw: Map<String, Any> = gson.fromJson(json, type)

        // Top-level fields use correct Nightscout names
        assertEquals("Default", raw["defaultProfile"])
        assertEquals("2024-12-12T00:00:00.000Z", raw["startDate"])

        @Suppress("UNCHECKED_CAST")
        val store = raw["store"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val defaultStore = store["Default"] as Map<String, Any>

        assertEquals(5.0, defaultStore["dia"])
        assertEquals("America/New_York", defaultStore["timezone"])
        assertEquals("mg/dl", defaultStore["units"])

        // Verify Nightscout-specific field names (not Kotlin property names)
        assertNotNull(defaultStore["carbratio"])  // not "carbRatio"
        assertNotNull(defaultStore["sens"])        // not "sensitivity"
        assertNotNull(defaultStore["target_low"])  // not "targetLow"
        assertNotNull(defaultStore["target_high"]) // not "targetHigh"
        assertNull(defaultStore["carbRatio"])       // Kotlin name must NOT appear
        assertNull(defaultStore["sensitivity"])
        assertNull(defaultStore["targetLow"])
        assertNull(defaultStore["targetHigh"])

        // Verify basal array values
        @Suppress("UNCHECKED_CAST")
        val basal = defaultStore["basal"] as List<Map<String, Any>>
        assertEquals(3, basal.size)
        assertEquals("00:00", basal[0]["time"])
        assertEquals(0.8, basal[0]["value"])
        assertEquals(0.0, basal[0]["timeAsSeconds"])  // Gson deserializes int as double
        assertEquals("06:00", basal[1]["time"])
        assertEquals(1.0, basal[1]["value"])
        assertEquals(21600.0, basal[1]["timeAsSeconds"])
        assertEquals("22:00", basal[2]["time"])
        assertEquals(0.7, basal[2]["value"])

        // Verify carbratio array values
        @Suppress("UNCHECKED_CAST")
        val carbratio = defaultStore["carbratio"] as List<Map<String, Any>>
        assertEquals(2, carbratio.size)
        assertEquals("00:00", carbratio[0]["time"])
        assertEquals(15.0, carbratio[0]["value"])
        assertEquals("12:00", carbratio[1]["time"])
        assertEquals(12.0, carbratio[1]["value"])
        assertEquals(43200.0, carbratio[1]["timeAsSeconds"])

        // Verify sensitivity
        @Suppress("UNCHECKED_CAST")
        val sens = defaultStore["sens"] as List<Map<String, Any>>
        assertEquals(1, sens.size)
        assertEquals(50.0, sens[0]["value"])
    }

    @Test
    fun testTimeValueSerializesWithCorrectFieldNames() {
        val tv = TimeValue("08:30", 1.2, 30600)

        val json = gson.toJson(tv)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val raw: Map<String, Any> = gson.fromJson(json, type)

        assertEquals("08:30", raw["time"])
        assertEquals(1.2, raw["value"])
        assertEquals(30600.0, raw["timeAsSeconds"])  // Gson int→double
        assertEquals(3, raw.size)  // No extra fields
    }
}
