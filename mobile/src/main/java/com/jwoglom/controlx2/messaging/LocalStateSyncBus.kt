package com.jwoglom.controlx2.messaging

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.shared.messaging.StateSyncBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant

/**
 * Local SharedPreferences-based implementation of StateSyncBus for phone-only mode.
 * Stores state data in SharedPreferences with no cross-device synchronization.
 */
class LocalStateSyncBus(context: Context) : StateSyncBus {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "LocalStateSyncBus",
        Context.MODE_PRIVATE
    )

    // Flow for state change notifications
    private val stateChanges = MutableSharedFlow<Pair<String, Pair<String, Instant>?>>()

    init {
        Timber.d("LocalStateSyncBus initialized with SharedPreferences")
    }

    override suspend fun putState(key: String, value: Pair<String, Instant>?) {
        Timber.d("LocalStateSyncBus.putState: $key = $value")

        if (value == null) {
            prefs.edit()
                .remove("$key.value")
                .remove("$key.timestamp")
                .apply()
        } else {
            prefs.edit()
                .putString("$key.value", value.first)
                .putLong("$key.timestamp", value.second.toEpochMilli())
                .apply()
        }

        // Notify observers
        stateChanges.emit(Pair(key, value))
    }

    override suspend fun getState(key: String): Pair<String, Instant>? {
        val value = prefs.getString("$key.value", null)
        val timestamp = prefs.getLong("$key.timestamp", -1)

        return if (value != null && timestamp != -1L) {
            Pair(value, Instant.ofEpochMilli(timestamp))
        } else {
            null
        }
    }

    override suspend fun getAllStates(): Map<String, Pair<String, Instant>> {
        val result = mutableMapOf<String, Pair<String, Instant>>()

        prefs.all.keys
            .filter { it.endsWith(".value") }
            .forEach { key ->
                val stateKey = key.removeSuffix(".value")
                getState(stateKey)?.let { value ->
                    result[stateKey] = value
                }
            }

        Timber.d("LocalStateSyncBus.getAllStates: ${result.size} states")
        return result
    }

    override fun observeState(key: String): Flow<Pair<String, Instant>?> {
        return stateChanges
            .filter { it.first == key }
            .map { it.second }
    }

    override suspend fun clearAllStates() {
        Timber.d("LocalStateSyncBus.clearAllStates")
        prefs.edit().clear().apply()
        stateChanges.emit(Pair("*", null))
    }

    override fun close() {
        Timber.d("LocalStateSyncBus.close()")
        // SharedPreferences doesn't need explicit cleanup
    }
}
