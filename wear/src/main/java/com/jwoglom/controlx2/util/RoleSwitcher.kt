package com.jwoglom.controlx2.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.jwoglom.controlx2.PhoneCommService
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.WearPumpCommService
import com.jwoglom.controlx2.messaging.WearHybridMessageBus
import com.jwoglom.controlx2.pump.util.removeBondCompat
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import timber.log.Timber

/**
 * Switches the watch's DeviceRole at runtime.
 *
 * Like the phone counterpart, a role switch must also clear the Mobi's OS-level
 * bond — the pump only re-advertises pairing mode after the previous bond is
 * dropped, and Mobi has no on-pump unpair UI. We send a best-effort peer-rescue
 * message so the phone flips to the opposite role automatically.
 */
fun switchDeviceRole(
    activity: Activity,
    newRole: DeviceRole,
    notifyPeer: Boolean = true,
) {
    val context = activity.applicationContext
    val currentRole = StatePrefs(context).deviceRole()
    if (currentRole == newRole) {
        Timber.i("switchDeviceRole: role unchanged ($newRole), skipping")
        return
    }
    Timber.i("switchDeviceRole: $currentRole -> $newRole (notifyPeer=$notifyPeer)")

    val wearPrefs = WearPrefs(context)

    val savedMac = wearPrefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("switchDeviceRole: removeBondCompat($savedMac) -> $ok")
    }

    if (notifyPeer) {
        sendPeerRescueBestEffort(context, currentRole)
    }

    StatePrefs(context).setDeviceRole(newRole)
    wearPrefs.setPumpFinderPumpMac("")
    wearPrefs.setPumpFinderPairingCodeType("")
    wearPrefs.setCurrentPumpSid(-1)
    wearPrefs.setPumpSetupComplete(false)
    wearPrefs.setPumpFinderServiceEnabled(newRole == DeviceRole.PUMP_HOST)

    context.stopService(Intent(context, PhoneCommService::class.java))
    context.stopService(Intent(context, WearPumpCommService::class.java))

    activity.recreate()
}

/**
 * One-tap rescue from a wedged half-flipped state. Forces this watch into
 * PUMP_HOST mode, drops the local bond, and tells the phone to flip itself to
 * CLIENT.
 */
fun rescueResetThisDevice(activity: Activity) {
    val context = activity.applicationContext
    val statePrefs = StatePrefs(context)
    val wearPrefs = WearPrefs(context)
    val currentRole = statePrefs.deviceRole()
    Timber.i("rescueResetThisDevice: forcing PUMP_HOST on watch (was $currentRole)")

    val savedMac = wearPrefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("rescueResetThisDevice: removeBondCompat($savedMac) -> $ok")
    }

    sendPeerRescueBestEffort(context, currentRole)

    statePrefs.setDeviceRole(DeviceRole.PUMP_HOST)
    wearPrefs.setPumpFinderPumpMac("")
    wearPrefs.setPumpFinderPairingCodeType("")
    wearPrefs.setCurrentPumpSid(-1)
    wearPrefs.setPumpSetupComplete(false)
    wearPrefs.setPumpFinderServiceEnabled(true)

    context.stopService(Intent(context, PhoneCommService::class.java))
    context.stopService(Intent(context, WearPumpCommService::class.java))

    activity.recreate()
}

/**
 * Apply a peer-rescue notification received over the wear data layer. Either
 * flips us to CLIENT (if the peer is taking over pump-host), or just clears
 * any lingering pump-host state. Called from comm services (no Activity).
 */
fun applyPeerRescueFromService(context: Context, becomeClient: Boolean) {
    val statePrefs = StatePrefs(context)
    val wearPrefs = WearPrefs(context)
    Timber.i("applyPeerRescueFromService: becomeClient=$becomeClient (currentRole=${statePrefs.deviceRole()})")

    val savedMac = wearPrefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("applyPeerRescueFromService: removeBondCompat($savedMac) -> $ok")
    }

    wearPrefs.setPumpFinderPumpMac("")
    wearPrefs.setPumpFinderPairingCodeType("")
    wearPrefs.setCurrentPumpSid(-1)
    wearPrefs.setPumpSetupComplete(false)
    if (becomeClient) {
        statePrefs.setDeviceRole(DeviceRole.CLIENT)
        wearPrefs.setPumpFinderServiceEnabled(false)
    }

    com.jwoglom.controlx2.shared.util.triggerAppReload(context)
}

private fun sendPeerRescueBestEffort(context: Context, currentRole: DeviceRole) {
    try {
        // Path keyed on sender's current role: WearHybridMessageBus only
        // forwards /to-client/* over Wear DL when sender is PUMP_HOST, and
        // /to-server/* when sender is CLIENT.
        val bus = WearHybridMessageBus(
            context = context,
            deviceRole = currentRole,
            identity = MessageBusSender.MOBILE_UI,
        )
        val path = when (currentRole) {
            DeviceRole.PUMP_HOST -> MessagePaths.TO_CLIENT_WIZARD_PEER_RESCUED
            DeviceRole.CLIENT -> MessagePaths.TO_SERVER_WIZARD_PEER_RESCUED
        }
        bus.sendMessage(path, byteArrayOf(), MessageBusSender.MOBILE_UI)
        Timber.i("sendPeerRescueBestEffort: sent $path")
    } catch (t: Throwable) {
        Timber.w(t, "sendPeerRescueBestEffort: failed (best-effort)")
    }
}
