package com.jwoglom.controlx2

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit

/**
 * SharedPreferences wrapper for watch pump-host mode.
 * Mirrors mobile/Prefs.kt with the same preference keys so behavior
 * is consistent regardless of which device is the pump-host.
 */
class WearPrefs(val context: Context) {

    fun serviceEnabled(): Boolean {
        return prefs().getBoolean("service-enabled", false)
    }

    fun setServiceEnabled(b: Boolean) {
        prefs().edit().putBoolean("service-enabled", b).commit()
    }

    fun pumpFinderServiceEnabled(): Boolean {
        return prefs().getBoolean("pumpfinder-service-enabled", false)
    }

    fun setPumpFinderServiceEnabled(b: Boolean) {
        prefs().edit().putBoolean("pumpfinder-service-enabled", b).commit()
    }

    fun pumpFinderPumpMac(): String? {
        return prefs().getString("pumpfinder-pump-mac", null)
    }

    fun setPumpFinderPumpMac(s: String) {
        prefs().edit().putString("pumpfinder-pump-mac", s).commit()
    }

    fun pumpFinderPairingCodeType(): String? {
        return prefs().getString("pumpfinder-pairing-code-type", null)
    }

    fun setPumpFinderPairingCodeType(s: String) {
        prefs().edit().putString("pumpfinder-pairing-code-type", s).commit()
    }

    fun unbondOnNextCommInitMac(): String? {
        return prefs().getString("unbond-on-next-comm-init-mac", null)
    }

    fun setUnbondOnNextCommInitMac(mac: String?) {
        val normalized = mac?.trim()?.uppercase()
        prefs().edit().putString("unbond-on-next-comm-init-mac", normalized).commit()
    }

    fun connectionSharingEnabled(): Boolean {
        return prefs().getBoolean("connection-sharing-enabled", false)
    }

    fun onlySnoopBluetoothEnabled(): Boolean {
        return prefs().getBoolean("only-snoop-bluetooth-enabled", false)
    }

    fun pumpSetupComplete(): Boolean {
        return prefs().getBoolean("pump-setup-complete", false)
    }

    fun setPumpSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("pump-setup-complete", b).commit()
    }

    fun appSetupComplete(): Boolean {
        return prefs().getBoolean("app-setup-complete", false)
    }

    fun setAppSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("app-setup-complete", b).commit()
    }

    fun insulinDeliveryActions(): Boolean {
        return prefs().getBoolean("insulin-delivery-actions", false)
    }

    fun setInsulinDeliveryActions(b: Boolean) {
        prefs().edit().putBoolean("insulin-delivery-actions", b).commit()
    }

    fun autoFetchHistoryLogs(): Boolean {
        return prefs().getBoolean("auto-fetch-history-logs", true)
    }

    fun glucoseUnit(): GlucoseUnit? {
        val unitStr = prefs().getString("glucose-unit", null)
        return GlucoseUnit.fromName(unitStr)
    }

    fun setGlucoseUnit(unit: GlucoseUnit?) {
        prefs().edit().putString("glucose-unit", unit?.name).commit()
    }

    fun qualifyingEventToastsEnabled(): Boolean {
        return prefs().getBoolean("qualifying-event-toasts-enabled", false)
    }

    fun currentPumpSid(): Int {
        return prefs().getInt("current-pump-sid", -1)
    }

    fun setCurrentPumpSid(v: Int) {
        if (currentPumpSid() == v) return
        prefs().edit().putInt("current-pump-sid", v).commit()
    }

    fun pumpModelName(): String? {
        return prefs().getString("pump-model-name", null)
    }

    fun setPumpModelName(name: String) {
        prefs().edit().putString("pump-model-name", name).commit()
    }

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

    fun tosAccepted(): Boolean {
        return prefs().getBoolean("tos-accepted", false)
    }

    fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
    }
}
