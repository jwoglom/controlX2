package com.jwoglom.controlx2.util

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.clientcomm.ClientConnectionState
import com.jwoglom.controlx2.clientcomm.ClientStateStore
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import timber.log.Timber
import java.time.Instant

class StatePrefs(val context: Context) : ClientStateStore {
    var connected: Pair<String, Instant>?
        get() = get("connected")
        set(value) {
            set("connected", value)
        }

    override var connectionState: ClientConnectionState
        get() {
            val name = connected?.first
            return try {
                if (name != null) ClientConnectionState.valueOf(name) else ClientConnectionState.UNKNOWN
            } catch (_: IllegalArgumentException) {
                ClientConnectionState.UNKNOWN
            }
        }
        set(value) {
            connected = Pair(value.name, Instant.now())
        }

    var pumpBattery: Pair<String, Instant>?
        get() = get("pumpBattery")
        set(value) {
            set("pumpBattery", value)
        }

    override fun updatePumpBattery(value: String, timestamp: Instant) {
        pumpBattery = Pair(value, timestamp)
    }

    var pumpIOB: Pair<String, Instant>?
        get() = get("pumpIOB")
        set(value) {
            set("pumpIOB", value)
        }

    override fun updatePumpIOB(value: String, timestamp: Instant) {
        pumpIOB = Pair(value, timestamp)
    }

    var cgmReading: Pair<String, Instant>?
        get() = get("cgmReading")
        set(value) {
            set("cgmReading", value)
        }

    override fun updateCgmReading(value: String, timestamp: Instant) {
        cgmReading = Pair(value, timestamp)
    }

    var glucoseUnit: GlucoseUnit
        get() {
            val name = prefs().getString("StatePrefs_glucoseUnit", null)
            return GlucoseUnit.fromName(name) ?: GlucoseUnit.MGDL
        }
        set(value) {
            Timber.d("StatePrefs set glucoseUnit=$value")
            prefs().edit().putString("StatePrefs_glucoseUnit", value.name).apply()
        }

    override fun updateGlucoseUnit(unit: GlucoseUnit) {
        glucoseUnit = unit
    }

    private fun get(key: String): Pair<String, Instant>? {
        val s = prefs().getString("StatePrefs_${key}", "")
        val parts = s?.split(";;", limit = 2)
        if (parts == null || parts.size != 2) {
            return null
        }
        Timber.d("StatePrefs get $key=$parts")
        return Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
    }

    private fun set(key: String, pair: Pair<String, Instant>?) {
        pair?.let {
            Timber.d("StatePrefs set $key=$pair")
            val ok = prefs().edit().putString("StatePrefs_${key}", "${pair.first};;${pair.second.toEpochMilli()}").commit()
            Timber.d("StatePrefs reply $key=$pair: $ok")
        }
    }

    /**
     * The role of this device: PUMP_HOST (manages BT pump connection) or CLIENT (thin client).
     * Default: CLIENT for watch (backward compatibility).
     */
    fun deviceRole(): DeviceRole {
        val name = prefs().getString("device-role", null)
        return try {
            if (name != null) DeviceRole.valueOf(name) else DeviceRole.CLIENT
        } catch (_: IllegalArgumentException) {
            DeviceRole.CLIENT
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        prefs().edit().putString("device-role", role.name).commit()
    }

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
    }
}
