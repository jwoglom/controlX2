package com.jwoglom.controlx2.sync.nightscout

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuration for Nightscout sync
 *
 * Stored in SharedPreferences and loaded/saved via companion object methods
 */
data class NightscoutSyncConfig(
    val enabled: Boolean = false,
    val nightscoutUrl: String = "",
    val apiSecret: String = "",

    // Enabled processors (stored as serialized string set in SharedPreferences)
    val enabledProcessors: Set<ProcessorType> = ProcessorType.all(),

    // Sync settings
    val syncIntervalMinutes: Int = 15,

    // Initial lookback when first enabled (hours)
    val initialLookbackHours: Int = 24
) {
    companion object {
        private const val PREF_ENABLED = "nightscout_enabled"
        private const val PREF_URL = "nightscout_url"
        private const val PREF_API_SECRET = "nightscout_api_secret"
        private const val PREF_ENABLED_PROCESSORS = "nightscout_enabled_processors"
        private const val PREF_SYNC_INTERVAL = "nightscout_sync_interval"
        private const val PREF_INITIAL_LOOKBACK = "nightscout_initial_lookback_hours"

        /**
         * Load configuration from SharedPreferences
         */
        fun load(prefs: SharedPreferences): NightscoutSyncConfig {
            val enabledProcessorsStr = prefs.getStringSet(
                PREF_ENABLED_PROCESSORS,
                ProcessorType.all().map { it.name }.toSet()
            ) ?: emptySet()

            val enabledProcessors = enabledProcessorsStr
                .mapNotNull { ProcessorType.fromName(it) }
                .toSet()

            return NightscoutSyncConfig(
                enabled = prefs.getBoolean(PREF_ENABLED, false),
                nightscoutUrl = prefs.getString(PREF_URL, "") ?: "",
                apiSecret = prefs.getString(PREF_API_SECRET, "") ?: "",
                enabledProcessors = enabledProcessors,
                syncIntervalMinutes = prefs.getInt(PREF_SYNC_INTERVAL, 15),
                initialLookbackHours = prefs.getInt(PREF_INITIAL_LOOKBACK, 24)
            )
        }

        /**
         * Save configuration to SharedPreferences
         */
        fun save(prefs: SharedPreferences, config: NightscoutSyncConfig) {
            prefs.edit {
                putBoolean(PREF_ENABLED, config.enabled)
                putString(PREF_URL, config.nightscoutUrl)
                putString(PREF_API_SECRET, config.apiSecret)
                putStringSet(
                    PREF_ENABLED_PROCESSORS,
                    config.enabledProcessors.map { it.name }.toSet()
                )
                putInt(PREF_SYNC_INTERVAL, config.syncIntervalMinutes)
                putInt(PREF_INITIAL_LOOKBACK, config.initialLookbackHours)
            }
        }
    }

    /**
     * Validate configuration
     */
    fun isValid(): Boolean {
        return nightscoutUrl.isNotBlank() && apiSecret.isNotBlank()
    }

    /**
     * Get sanitized Nightscout URL (remove trailing slash)
     */
    fun getSanitizedUrl(): String {
        return nightscoutUrl.trimEnd('/')
    }
}
