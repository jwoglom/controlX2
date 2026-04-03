package com.jwoglom.controlx2.clientcomm

import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant

/**
 * Persistent state storage for a client device receiving pump data from a host.
 * Implementations back this with SharedPreferences, DataStore, etc.
 */
interface ClientStateStore {
    var connectionState: ClientConnectionState
    fun updatePumpBattery(value: String, timestamp: Instant)
    fun updatePumpIOB(value: String, timestamp: Instant)
    fun updateCgmReading(value: String, timestamp: Instant)
    fun updateGlucoseUnit(unit: GlucoseUnit)
}
