package com.jwoglom.controlx2.util

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.WearableListenerService
import timber.log.Timber
import java.time.Instant

class StatePrefs(val context: Context) {
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

    var cgmReading: Pair<String, Instant>?
        get() = get("cgmReading")
        set(value) {
            set("cgmReading", value)
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

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", WearableListenerService.MODE_PRIVATE)
    }
}