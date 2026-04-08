package com.jwoglom.controlx2.sync.nightscout

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for NightscoutSyncConfig SharedPreferences operations
 */
@RunWith(AndroidJUnit4::class)
class NightscoutSyncConfigTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_nightscout_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun cleanup() {
        prefs.edit().clear().commit()
    }

    @Test
    fun testDefaultConfig() {
        val config = NightscoutSyncConfig.load(prefs)

        assertFalse(config.enabled)
        assertEquals("", config.nightscoutUrl)
        assertEquals("", config.apiSecret)
        assertEquals(ProcessorType.all(), config.enabledProcessors)
        assertEquals(15, config.syncIntervalMinutes)
        assertEquals(24, config.initialLookbackHours)
    }

    @Test
    fun testSaveAndLoad() {
        val config = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test.nightscout.com",
            apiSecret = "test-secret-123",
            enabledProcessors = setOf(ProcessorType.CGM_READING, ProcessorType.BOLUS),
            syncIntervalMinutes = 30,
            initialLookbackHours = 48
        )

        NightscoutSyncConfig.save(prefs, config)
        val loaded = NightscoutSyncConfig.load(prefs)

        assertTrue(loaded.enabled)
        assertEquals("https://test.nightscout.com", loaded.nightscoutUrl)
        assertEquals("test-secret-123", loaded.apiSecret)
        assertEquals(setOf(ProcessorType.CGM_READING, ProcessorType.BOLUS), loaded.enabledProcessors)
        assertEquals(30, loaded.syncIntervalMinutes)
        assertEquals(48, loaded.initialLookbackHours)
    }

    @Test
    fun testSaveEmptyProcessors() {
        val config = NightscoutSyncConfig(
            enabled = true,
            enabledProcessors = emptySet()
        )

        NightscoutSyncConfig.save(prefs, config)
        val loaded = NightscoutSyncConfig.load(prefs)

        assertTrue(loaded.enabledProcessors.isEmpty())
    }

    @Test
    fun testSaveAllProcessors() {
        val config = NightscoutSyncConfig(
            enabled = true,
            enabledProcessors = ProcessorType.all()
        )

        NightscoutSyncConfig.save(prefs, config)
        val loaded = NightscoutSyncConfig.load(prefs)

        assertEquals(ProcessorType.all().size, loaded.enabledProcessors.size)
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.CGM_READING))
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.BOLUS))
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.BASAL))
    }

    @Test
    fun testIsValid() {
        val validConfig = NightscoutSyncConfig(
            nightscoutUrl = "https://test.com",
            apiSecret = "secret"
        )
        assertTrue(validConfig.isValid())

        val invalidUrlConfig = NightscoutSyncConfig(
            nightscoutUrl = "",
            apiSecret = "secret"
        )
        assertFalse(invalidUrlConfig.isValid())

        val invalidSecretConfig = NightscoutSyncConfig(
            nightscoutUrl = "https://test.com",
            apiSecret = ""
        )
        assertFalse(invalidSecretConfig.isValid())

        val invalidBothConfig = NightscoutSyncConfig(
            nightscoutUrl = "",
            apiSecret = ""
        )
        assertFalse(invalidBothConfig.isValid())
    }

    @Test
    fun testGetSanitizedUrl() {
        val config1 = NightscoutSyncConfig(nightscoutUrl = "https://test.com/")
        assertEquals("https://test.com", config1.getSanitizedUrl())

        val config2 = NightscoutSyncConfig(nightscoutUrl = "https://test.com")
        assertEquals("https://test.com", config2.getSanitizedUrl())

        val config3 = NightscoutSyncConfig(nightscoutUrl = "https://test.com///")
        assertEquals("https://test.com", config3.getSanitizedUrl())
    }

    @Test
    fun testUpdateConfig() {
        val config1 = NightscoutSyncConfig(
            enabled = true,
            nightscoutUrl = "https://test1.com",
            syncIntervalMinutes = 15
        )
        NightscoutSyncConfig.save(prefs, config1)

        val config2 = config1.copy(
            nightscoutUrl = "https://test2.com",
            syncIntervalMinutes = 30
        )
        NightscoutSyncConfig.save(prefs, config2)

        val loaded = NightscoutSyncConfig.load(prefs)
        assertEquals("https://test2.com", loaded.nightscoutUrl)
        assertEquals(30, loaded.syncIntervalMinutes)
        assertTrue(loaded.enabled) // Should remain true
    }

    @Test
    fun testProcessorNamePersistence() {
        val config = NightscoutSyncConfig(
            enabledProcessors = setOf(
                ProcessorType.CGM_READING,
                ProcessorType.BOLUS,
                ProcessorType.BASAL
            )
        )

        NightscoutSyncConfig.save(prefs, config)
        val loaded = NightscoutSyncConfig.load(prefs)

        // Verify exact processor names are preserved
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.CGM_READING))
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.BOLUS))
        assertTrue(loaded.enabledProcessors.contains(ProcessorType.BASAL))
        assertFalse(loaded.enabledProcessors.contains(ProcessorType.ALARM))
    }
}
