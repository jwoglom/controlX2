package com.jwoglom.controlx2.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.jwoglom.controlx2.CommService
import com.jwoglom.controlx2.MobileClientService
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.shared.enums.DeviceRole
import timber.log.Timber

/**
 * Switches the phone's DeviceRole at runtime: writes the new pref, stops both possible
 * comm services, and recreates the hosting Activity so its onCreate re-reads the pref
 * and starts the correct service for the new role.
 *
 * The pump only supports one BT bond at a time, so the user must also flip the other
 * device (watch) to the opposite role and re-pair the pump. The UI calling this should
 * surface that requirement.
 */
fun switchDeviceRole(activity: Activity, newRole: DeviceRole) {
    val context = activity.applicationContext
    val currentRole = Prefs(context).deviceRole()
    if (currentRole == newRole) {
        Timber.i("switchDeviceRole: role unchanged ($newRole), skipping")
        return
    }
    Timber.i("switchDeviceRole: $currentRole -> $newRole")

    Prefs(context).setDeviceRole(newRole)

    context.stopService(Intent(context, CommService::class.java))
    context.stopService(Intent(context, MobileClientService::class.java))

    activity.recreate()
}
