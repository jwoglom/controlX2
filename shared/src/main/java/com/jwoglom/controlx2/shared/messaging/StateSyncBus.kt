package com.jwoglom.controlx2.shared.messaging

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Abstraction for synchronizing persistent state across devices.
 * Implementations include:
 * - LocalStateSyncBus: SharedPreferences-based storage for phone-only mode
 * - WearStateSyncBus: Wear OS Data Client for phone-watch synchronization
 */
interface StateSyncBus {
    /**
     * Store a state value with timestamp
     * @param key The state key (e.g., "pumpBattery", "cgmReading")
     * @param value The value string and timestamp when it was recorded
     */
    suspend fun putState(key: String, value: Pair<String, Instant>?)

    /**
     * Retrieve a state value
     * @param key The state key
     * @return The value and timestamp, or null if not found
     */
    suspend fun getState(key: String): Pair<String, Instant>?

    /**
     * Retrieve all state values
     * @return Map of all keys to their values and timestamps
     */
    suspend fun getAllStates(): Map<String, Pair<String, Instant>>

    /**
     * Observe changes to a specific state key
     * @param key The state key to observe
     * @return Flow of state changes
     */
    fun observeState(key: String): Flow<Pair<String, Instant>?>

    /**
     * Clear all state data
     */
    suspend fun clearAllStates()

    /**
     * Close and release resources
     */
    fun close()
}

/**
 * Standard state keys used across the application
 */
object StateKeys {
    const val CONNECTED = "connected"
    const val PUMP_BATTERY = "pumpBattery"
    const val PUMP_IOB = "pumpIOB"
    const val PUMP_CARTRIDGE_UNITS = "pumpCartridgeUnits"
    const val PUMP_CURRENT_BASAL = "pumpCurrentBasal"
    const val CGM_READING = "cgmReading"
}
