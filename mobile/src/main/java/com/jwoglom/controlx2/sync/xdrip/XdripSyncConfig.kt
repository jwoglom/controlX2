package com.jwoglom.controlx2.sync.xdrip

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuration for xDrip sync.
 *
 * Stored in SharedPreferences and loaded/saved via companion object methods.
 */
data class XdripSyncConfig(
    val enabled: Boolean = false,
    val sendCgmSgv: Boolean = true,
    val sendPumpDeviceStatus: Boolean = true,
    val sendTreatments: Boolean = true,
    val sendStatusLine: Boolean = true,

    // Optional rate limits (seconds)
    val cgmSgvMinimumIntervalSeconds: Int? = null,
    val pumpDeviceStatusMinimumIntervalSeconds: Int? = null,
    val treatmentsMinimumIntervalSeconds: Int? = null,
    val statusLineMinimumIntervalSeconds: Int? = null
) {
    companion object {
        private const val PREF_ENABLED = "xdrip_enabled"
        private const val PREF_SEND_CGM_SGV = "xdrip_send_cgm_sgv"
        private const val PREF_SEND_PUMP_DEVICE_STATUS = "xdrip_send_pump_device_status"
        private const val PREF_SEND_TREATMENTS = "xdrip_send_treatments"
        private const val PREF_SEND_STATUS_LINE = "xdrip_send_status_line"

        private const val PREF_CGM_SGV_MIN_INTERVAL_SECONDS = "xdrip_cgm_sgv_min_interval_seconds"
        private const val PREF_PUMP_DEVICE_STATUS_MIN_INTERVAL_SECONDS =
            "xdrip_pump_device_status_min_interval_seconds"
        private const val PREF_TREATMENTS_MIN_INTERVAL_SECONDS =
            "xdrip_treatments_min_interval_seconds"
        private const val PREF_STATUS_LINE_MIN_INTERVAL_SECONDS =
            "xdrip_status_line_min_interval_seconds"

        fun load(prefs: SharedPreferences): XdripSyncConfig {
            return XdripSyncConfig(
                enabled = prefs.getBoolean(PREF_ENABLED, false),
                sendCgmSgv = prefs.getBoolean(PREF_SEND_CGM_SGV, true),
                sendPumpDeviceStatus = prefs.getBoolean(PREF_SEND_PUMP_DEVICE_STATUS, true),
                sendTreatments = prefs.getBoolean(PREF_SEND_TREATMENTS, true),
                sendStatusLine = prefs.getBoolean(PREF_SEND_STATUS_LINE, true),
                cgmSgvMinimumIntervalSeconds = prefs.getNullableInt(PREF_CGM_SGV_MIN_INTERVAL_SECONDS),
                pumpDeviceStatusMinimumIntervalSeconds =
                    prefs.getNullableInt(PREF_PUMP_DEVICE_STATUS_MIN_INTERVAL_SECONDS),
                treatmentsMinimumIntervalSeconds =
                    prefs.getNullableInt(PREF_TREATMENTS_MIN_INTERVAL_SECONDS),
                statusLineMinimumIntervalSeconds =
                    prefs.getNullableInt(PREF_STATUS_LINE_MIN_INTERVAL_SECONDS)
            )
        }

        fun save(prefs: SharedPreferences, config: XdripSyncConfig) {
            prefs.edit {
                putBoolean(PREF_ENABLED, config.enabled)
                putBoolean(PREF_SEND_CGM_SGV, config.sendCgmSgv)
                putBoolean(PREF_SEND_PUMP_DEVICE_STATUS, config.sendPumpDeviceStatus)
                putBoolean(PREF_SEND_TREATMENTS, config.sendTreatments)
                putBoolean(PREF_SEND_STATUS_LINE, config.sendStatusLine)

                putNullableInt(PREF_CGM_SGV_MIN_INTERVAL_SECONDS, config.cgmSgvMinimumIntervalSeconds)
                putNullableInt(
                    PREF_PUMP_DEVICE_STATUS_MIN_INTERVAL_SECONDS,
                    config.pumpDeviceStatusMinimumIntervalSeconds
                )
                putNullableInt(PREF_TREATMENTS_MIN_INTERVAL_SECONDS, config.treatmentsMinimumIntervalSeconds)
                putNullableInt(PREF_STATUS_LINE_MIN_INTERVAL_SECONDS, config.statusLineMinimumIntervalSeconds)
            }
        }

        private fun SharedPreferences.getNullableInt(key: String): Int? {
            return if (contains(key)) getInt(key, 0) else null
        }

        private fun SharedPreferences.Editor.putNullableInt(key: String, value: Int?) {
            if (value == null) {
                remove(key)
            } else {
                putInt(key, value)
            }
        }
    }

    fun isPayloadEnabled(payload: XdripPayloadGroup): Boolean {
        return when (payload) {
            XdripPayloadGroup.CGM -> sendCgmSgv
            XdripPayloadGroup.PUMP_DEVICE_STATUS -> sendPumpDeviceStatus
            XdripPayloadGroup.TREATMENTS -> sendTreatments
            XdripPayloadGroup.STATUS_LINE -> sendStatusLine
        }
    }

    fun withPayloadEnabled(payload: XdripPayloadGroup, enabled: Boolean): XdripSyncConfig {
        return when (payload) {
            XdripPayloadGroup.CGM -> copy(sendCgmSgv = enabled)
            XdripPayloadGroup.PUMP_DEVICE_STATUS -> copy(sendPumpDeviceStatus = enabled)
            XdripPayloadGroup.TREATMENTS -> copy(sendTreatments = enabled)
            XdripPayloadGroup.STATUS_LINE -> copy(sendStatusLine = enabled)
        }
    }

    fun enabledPayloads(): Set<XdripPayloadGroup> {
        return XdripPayloadGroup.all().filterTo(mutableSetOf()) { isPayloadEnabled(it) }
    }
}
