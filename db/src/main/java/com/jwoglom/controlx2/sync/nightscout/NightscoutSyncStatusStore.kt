package com.jwoglom.controlx2.sync.nightscout

import android.content.SharedPreferences
import androidx.core.content.edit

data class NightscoutSyncStatus(
    val lastSuccessfulSyncMillis: Long? = null,
    val lastError: String? = null,
    val lastErrorMillis: Long? = null
)

object NightscoutSyncStatusStore {
    private const val PREF_LAST_SUCCESS = "nightscout_last_successful_sync"
    private const val PREF_LAST_ERROR = "nightscout_last_error"
    private const val PREF_LAST_ERROR_TIME = "nightscout_last_error_time"

    fun load(prefs: SharedPreferences): NightscoutSyncStatus {
        val success = prefs.getLong(PREF_LAST_SUCCESS, -1L).takeIf { it > 0 }
        val error = prefs.getString(PREF_LAST_ERROR, null)
        val errorTime = prefs.getLong(PREF_LAST_ERROR_TIME, -1L).takeIf { it > 0 }
        return NightscoutSyncStatus(
            lastSuccessfulSyncMillis = success,
            lastError = error,
            lastErrorMillis = errorTime
        )
    }

    fun recordSuccess(prefs: SharedPreferences, timestampMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(PREF_LAST_SUCCESS, timestampMillis)
            remove(PREF_LAST_ERROR)
            remove(PREF_LAST_ERROR_TIME)
        }
    }

    fun recordFailure(prefs: SharedPreferences, error: String, timestampMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putString(PREF_LAST_ERROR, error)
            putLong(PREF_LAST_ERROR_TIME, timestampMillis)
        }
    }
}
