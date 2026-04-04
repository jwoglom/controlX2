package com.jwoglom.controlx2

import android.content.Context
import com.jwoglom.controlx2.clientcomm.ClientConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileClientServiceTest {

    private lateinit var stateStore: MobileClientStateStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        stateStore = MobileClientStateStore(context)
    }

    // --- MobileClientStateStore round-trip tests ---

    @Test
    fun `connectionState defaults to UNKNOWN`() {
        assertEquals(ClientConnectionState.UNKNOWN, stateStore.connectionState)
    }

    @Test
    fun `connectionState round-trips`() {
        stateStore.connectionState = ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED
        assertEquals(ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED, stateStore.connectionState)

        stateStore.connectionState = ClientConnectionState.HOST_DISCONNECTED
        assertEquals(ClientConnectionState.HOST_DISCONNECTED, stateStore.connectionState)
    }

    @Test
    fun `pumpBattery round-trips`() {
        assertNull(stateStore.pumpBattery)

        val ts = Instant.ofEpochMilli(1700000000000L)
        stateStore.updatePumpBattery("85", ts)
        val result = stateStore.pumpBattery
        assertEquals("85", result?.first)
        assertEquals(ts, result?.second)
    }

    @Test
    fun `pumpIOB round-trips`() {
        assertNull(stateStore.pumpIOB)

        val ts = Instant.ofEpochMilli(1700000000000L)
        stateStore.updatePumpIOB("2.5", ts)
        val result = stateStore.pumpIOB
        assertEquals("2.5", result?.first)
        assertEquals(ts, result?.second)
    }

    @Test
    fun `cgmReading round-trips`() {
        assertNull(stateStore.cgmReading)

        val ts = Instant.ofEpochMilli(1700000000000L)
        stateStore.updateCgmReading("120", ts)
        val result = stateStore.cgmReading
        assertEquals("120", result?.first)
        assertEquals(ts, result?.second)
    }

    @Test
    fun `glucoseUnit round-trips via SharedPreferences`() {
        // updateGlucoseUnit stores in prefs
        stateStore.updateGlucoseUnit(com.jwoglom.controlx2.shared.enums.GlucoseUnit.MMOL)
        // Verify by reading prefs directly through a fresh store
        val context = RuntimeEnvironment.getApplication()
        val freshStore = MobileClientStateStore(context)
        // The store doesn't expose glucoseUnit getter, but we can verify prefs
        val prefs = context.getSharedPreferences("MobileClientState", Context.MODE_PRIVATE)
        assertEquals("MMOL", prefs.getString("glucoseUnit", null))
    }
}
