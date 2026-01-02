package com.jwoglom.controlx2

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit

class Prefs(val context: Context) {

    fun tosAccepted(): Boolean {
        return prefs().getBoolean("tos-accepted", false)
    }

    fun setTosAccepted(b: Boolean) {
        prefs().edit().putBoolean("tos-accepted", b).commit()
    }

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

    fun connectionSharingEnabled(): Boolean {
        return prefs().getBoolean("connection-sharing-enabled", false)
    }

    fun setConnectionSharingEnabled(b: Boolean) {
        prefs().edit().putBoolean("connection-sharing-enabled", b).commit()
    }

    fun onlySnoopBluetoothEnabled(): Boolean {
        return prefs().getBoolean("only-snoop-bluetooth-enabled", false)
    }

    fun setOnlySnoopBluetoothEnabled(b: Boolean) {
        prefs().edit().putBoolean("only-snoop-bluetooth-enabled", b).commit()
    }

    fun verboseFileLoggingEnabled(): Boolean {
        return prefs().getBoolean("verbose-file-logging-enabled", false)
    }

    fun setVerboseFileLoggingEnabled(b: Boolean) {
        prefs().edit().putBoolean("verbose-file-logging-enabled", b).commit()
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

    /**
     * Allows insulin delivery actions (remote boluses) from phone or wearable
     */
    fun insulinDeliveryActions(): Boolean {
        return prefs().getBoolean("insulin-delivery-actions", false)
    }

    fun setInsulinDeliveryActions(b: Boolean) {
        prefs().edit().putBoolean("insulin-delivery-actions", b).commit()
    }

    /**
     * Boluses above this threshold (in milliunits) will require phone confirmation in a notification.
     */
    fun bolusConfirmationInsulinThreshold(): Double {
        return InsulinUnit.from1000To1(prefs().getLong("bolus-confirmation-insulin-threshold", 0))
    }

    fun setBolusConfirmationInsulinThreshold(d: Double) {
        prefs().edit().putLong("bolus-confirmation-insulin-threshold", InsulinUnit.from1To1000(d)).commit()
    }

    /**
     * Sends basic application version telemetry to the server and enables automatic update checks.
     */
    fun checkForUpdates(): Boolean {
        return prefs().getBoolean("check-for-updates", true)
    }

    fun setCheckForUpdates(b: Boolean) {
        prefs().edit().putBoolean("check-for-updates", b).commit()
    }

    /**
     * The current pumpSid for use when initializing the database ViewModel
     */
    fun currentPumpSid(): Int {
        return prefs().getInt("current-pump-sid", -1)
    }

    fun setCurrentPumpSid(v: Int) {
        if (currentPumpSid() == v) return
        prefs().edit().putInt("current-pump-sid", v).commit()
    }

    /**
     * Whether to run history log fetching in background
     */
    fun autoFetchHistoryLogs(): Boolean {
        return prefs().getBoolean("auto-fetch-history-logs", true)
    }

    fun setAutoFetchHistoryLogs(b: Boolean) {
        prefs().edit().putBoolean("auto-fetch-history-logs", b).commit()
    }

    /**
     * User's preferred glucose unit (mg/dL or mmol/L)
     */
    fun glucoseUnit(): GlucoseUnit? {
        val unitStr = prefs().getString("glucose-unit", null)
        return GlucoseUnit.fromName(unitStr)
    }

    fun setGlucoseUnit(unit: GlucoseUnit?) {
        prefs().edit().putString("glucose-unit", unit?.name).commit()
    }

    /**
     * Whether the HTTP Debug API is enabled
     */
    fun httpDebugApiEnabled(): Boolean {
        return prefs().getBoolean("http-debug-api-enabled", false)
    }

    fun setHttpDebugApiEnabled(b: Boolean) {
        prefs().edit().putBoolean("http-debug-api-enabled", b).commit()
    }

    /**
     * HTTP Debug API username for Basic Authentication
     */
    fun httpDebugApiUsername(): String {
        return prefs().getString("http-debug-api-username", "admin") ?: "admin"
    }

    fun setHttpDebugApiUsername(s: String) {
        prefs().edit().putString("http-debug-api-username", s).commit()
    }

    /**
     * HTTP Debug API password for Basic Authentication
     */
    fun httpDebugApiPassword(): String {
        return prefs().getString("http-debug-api-password", "") ?: ""
    }

    fun setHttpDebugApiPassword(s: String) {
        prefs().edit().putString("http-debug-api-password", s).commit()
    }

    fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", WearableListenerService.MODE_PRIVATE)
    }
}