package com.jwoglom.controlx2.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.jwoglom.controlx2.CommService
import com.jwoglom.controlx2.MobileClientService
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.pump.util.removeBondCompat
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import timber.log.Timber

/**
 * Switches the phone's DeviceRole at runtime.
 *
 * A role switch requires also clearing the OS-level Bluetooth bond on the
 * Mobi pump (the Mobi has no on-pump unpair UI; the bond is only released by
 * the host removing it). Without this, the pump won't re-advertise pairing
 * mode and the new host can't see it. The pumpFinderPumpMac saved in prefs
 * tells us which device to remove.
 *
 * If [notifyPeer] is true, also fire a best-effort peer-rescue message so the
 * other device can flip itself to CLIENT and clear its own pump-host state.
 * This makes the rescue button on either device be enough to recover from a
 * wedged half-flipped state.
 */
fun switchDeviceRole(
    activity: Activity,
    newRole: DeviceRole,
    notifyPeer: Boolean = true,
) {
    val context = activity.applicationContext
    val prefs = Prefs(context)
    val currentRole = prefs.deviceRole()
    if (currentRole == newRole) {
        Timber.i("switchDeviceRole: role unchanged ($newRole), skipping")
        return
    }
    Timber.i("switchDeviceRole: $currentRole -> $newRole (notifyPeer=$notifyPeer)")

    // Clear the OS-level bond so the pump re-advertises pairing on the
    // charging pad. Mobi has no on-pump unpair, so this is the only way.
    val savedMac = prefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("switchDeviceRole: removeBondCompat($savedMac) -> $ok")
    }

    if (notifyPeer) {
        sendPeerRescueBestEffort(context, currentRole)
    }

    prefs.setDeviceRole(newRole)
    // Clear all pump-host state — the new pump-host is starting fresh, the
    // outgoing pump-host has nothing left to manage.
    prefs.setPumpFinderPumpMac("")
    prefs.setPumpFinderPairingCodeType("")
    prefs.setCurrentPumpSid(-1)
    prefs.setPumpSetupComplete(false)
    prefs.setPumpFinderServiceEnabled(newRole == DeviceRole.PUMP_HOST)

    context.stopService(Intent(context, CommService::class.java))
    context.stopService(Intent(context, MobileClientService::class.java))

    activity.recreate()
}

/**
 * Rescue path for a wedged device. One tap:
 *  - clears the local pump bond (if any),
 *  - sets THIS device to PUMP_HOST and resets pump-finder state,
 *  - notifies the peer to flip itself to CLIENT,
 *  - recreates the activity so the fresh PumpFinder flow starts.
 *
 * Use when the user is in a half-flipped / pairing-popup-loop state.
 */
fun rescueResetThisDevice(activity: Activity) {
    val context = activity.applicationContext
    val prefs = Prefs(context)
    Timber.i("rescueResetThisDevice: forcing PUMP_HOST on phone")

    val savedMac = prefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("rescueResetThisDevice: removeBondCompat($savedMac) -> $ok")
    }

    sendPeerRescueBestEffort(context, prefs.deviceRole())

    prefs.setDeviceRole(DeviceRole.PUMP_HOST)
    prefs.setPumpFinderPumpMac("")
    prefs.setPumpFinderPairingCodeType("")
    prefs.setCurrentPumpSid(-1)
    prefs.setPumpSetupComplete(false)
    prefs.setPumpFinderServiceEnabled(true)

    context.stopService(Intent(context, CommService::class.java))
    context.stopService(Intent(context, MobileClientService::class.java))

    activity.recreate()
}

/**
 * Fire-and-forget peer notification. The receiving peer flips itself to CLIENT
 * and clears its pump-host state.
 *
 * Path choice is keyed on the SENDER's current role, because
 * [com.jwoglom.controlx2.messaging.HybridMessageBus] only forwards
 * to-client paths over Wear DL when sender is PUMP_HOST, and to-server
 * paths when sender is CLIENT.
 */
/**
 * Apply a peer-rescue notification received over the wear data layer. Either
 * flips us to CLIENT (if the peer is taking over pump-host), or just clears
 * any lingering pump-host state.
 *
 * Called from CommService and MobileClientService — services can't call
 * [activity.recreate], so we trigger a process reload via [triggerAppReload].
 */
fun applyPeerRescueFromService(context: Context, becomeClient: Boolean) {
    val prefs = Prefs(context)
    Timber.i("applyPeerRescueFromService: becomeClient=$becomeClient (currentRole=${prefs.deviceRole()})")

    val savedMac = prefs.pumpFinderPumpMac().orEmpty()
    if (savedMac.isNotBlank()) {
        val ok = removeBondCompat(context, savedMac)
        Timber.i("applyPeerRescueFromService: removeBondCompat($savedMac) -> $ok")
    }

    prefs.setPumpFinderPumpMac("")
    prefs.setPumpFinderPairingCodeType("")
    prefs.setCurrentPumpSid(-1)
    prefs.setPumpSetupComplete(false)
    if (becomeClient) {
        prefs.setDeviceRole(DeviceRole.CLIENT)
        prefs.setPumpFinderServiceEnabled(false)
    }

    com.jwoglom.controlx2.shared.util.triggerAppReload(context)
}

private fun sendPeerRescueBestEffort(context: Context, currentRole: DeviceRole) {
    try {
        val bus = MessageBusFactory.createMessageBus(context)
        val path = when (currentRole) {
            DeviceRole.PUMP_HOST -> MessagePaths.TO_CLIENT_WIZARD_PEER_RESCUED
            DeviceRole.CLIENT -> MessagePaths.TO_SERVER_WIZARD_PEER_RESCUED
        }
        bus.sendMessage(path, byteArrayOf(), MessageBusSender.MOBILE_UI)
        Timber.i("sendPeerRescueBestEffort: sent $path")
    } catch (t: Throwable) {
        Timber.w(t, "sendPeerRescueBestEffort: failed (peer notification is best-effort)")
    }
}
