package com.jwoglom.controlx2.util

import android.content.Context
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.messaging.StateSyncBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.Instant

/**
 * State management for pump and CGM data.
 * Now uses StateSyncBus abstraction instead of direct DataClient access.
 */
class DataClientState(private val context: Context) {
    private val stateSyncBus: StateSyncBus = MessageBusFactory.createStateSyncBus(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var connected: Pair<String, Instant>?
        get() = get("connected")
        set(value) {
            set("connected", value)
        }

    var pumpBattery: Pair<String, Instant>?
        get() = get("pumpBattery")
        set(value) {
            set("pumpBattery", value)
        }

    var pumpIOB: Pair<String, Instant>?
        get() = get("pumpIOB")
        set(value) {
            set("pumpIOB", value)
        }

    var pumpCartridgeUnits: Pair<String, Instant>?
        get() = get("pumpCartridgeUnits")
        set(value) {
            set("pumpCartridgeUnits", value)
        }

    var pumpCurrentBasal: Pair<String, Instant>?
        get() = get("pumpCurrentBasal")
        set(value) {
            set("pumpCurrentBasal", value)
        }

    var cgmReading: Pair<String, Instant>?
        get() = get("cgmReading")
        set(value) {
            set("cgmReading", value)
        }

    private fun get(key: String): Pair<String, Instant>? {
        // Use runBlocking to maintain synchronous interface for backward compatibility
        return runBlocking {
            val value = stateSyncBus.getState(key)
            Timber.d("DataClientState get $key=$value")
            value
        }
    }

    private fun set(key: String, pair: Pair<String, Instant>?) {
        Timber.d("DataClientState set $key=$pair")
        // Launch coroutine to avoid blocking the caller
        scope.launch {
            stateSyncBus.putState(key, pair)
        }
    }

    /**
     * Get all stored states.
     * @return Map of all state keys to their values
     */
    fun getAllStates(): Map<String, Pair<String, Instant>> {
        return runBlocking {
            stateSyncBus.getAllStates()
        }
    }

    /**
     * Clear all state data.
     */
    fun clearAll() {
        scope.launch {
            stateSyncBus.clearAllStates()
        }
    }

    /**
     * Clean up resources.
     */
    fun close() {
        stateSyncBus.close()
    }
}
