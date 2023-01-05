package com.jwoglom.wearx2

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit

class Prefs(val context: Context) {

    fun tosAccepted(): Boolean {
        return prefs().getBoolean("tos-accepted", false)
    }

    fun setTosAccepted(b: Boolean) {
        prefs().edit().putBoolean("tos-accepted", b).apply()
    }

    fun serviceEnabled(): Boolean {
        return prefs().getBoolean("service-enabled", false)
    }

    fun setServiceEnabled(b: Boolean) {
        prefs().edit().putBoolean("service-enabled", b).apply()
    }

    fun connectionSharingEnabled(): Boolean {
        return prefs().getBoolean("connection-sharing-enabled", false)
    }

    fun setConnectionSharingEnabled(b: Boolean) {
        prefs().edit().putBoolean("connection-sharing-enabled", b).apply()
    }

    fun pumpSetupComplete(): Boolean {
        return prefs().getBoolean("pump-setup-complete", false)
    }

    fun setPumpSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("pump-setup-complete", b).apply()
    }

    fun appSetupComplete(): Boolean {
        return prefs().getBoolean("app-setup-complete", false)
    }

    fun setAppSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("app-setup-complete", b).apply()
    }

    /**
     * Allows insulin delivery actions (remote boluses) from phone or wearable
     */
    fun insulinDeliveryActions(): Boolean {
        return prefs().getBoolean("insulin-delivery-actions", false)
    }

    fun setInsulinDeliveryActions(b: Boolean) {
        prefs().edit().putBoolean("insulin-delivery-actions", b).apply()
    }

    /**
     * Boluses above this threshold (in milliunits) will require phone confirmation in a notification.
     */
    fun bolusConfirmationInsulinThreshold(): Double {
        return InsulinUnit.from1000To1(prefs().getLong("bolus-confirmation-insulin-threshold", 0))
    }

    fun setBolusConfirmationInsulinThreshold(d: Double) {
        prefs().edit().putLong("bolus-confirmation-insulin-threshold", InsulinUnit.from1To1000(d)).apply()
    }

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", WearableListenerService.MODE_PRIVATE)
    }
}