package com.jwoglom.controlx2.pump

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.TandemError
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * Interface defining callbacks that extracted pump components use to communicate
 * with CommService. Replaces the `this@CommService` inner-class back-reference pattern.
 *
 * All mobile-app-specific types (Prefs, HttpDebugApiService, HistoryLogRepo, DataClientState,
 * NightscoutSyncWorker, XdripMessageDispatcher) are abstracted behind these methods so that
 * PumpCommHandler has no imports from the mobile app outside of pump/ and shared/.
 */
interface CommServiceCallbacks {
    fun getApplicationContext(): Context
    val supervisorJob: Job
    val pumpCommState: PumpCommState
    fun sendWearCommMessage(path: String, message: ByteArray)
    fun updateNotification(statusText: String? = null)
    fun updateNotificationWithPumpData(message: com.jwoglom.pumpx2.pump.messages.Message)
    fun markConnectionTime()
    fun showToast(text: String, duration: Int)
    fun getWearPrefs(): SharedPreferences?
    fun sendPumpCommMessages(pumpMsgBytes: ByteArray)

    // --- Preference accessors (replaces Prefs(context).xxx()) ---
    fun prefAutoFetchHistoryLogs(): Boolean
    fun prefConnectionSharingEnabled(): Boolean
    fun prefOnlySnoopBluetoothEnabled(): Boolean
    fun prefInsulinDeliveryActions(): Boolean
    fun prefQualifyingEventToastsEnabled(): Boolean
    fun prefGlucoseUnit(): GlucoseUnit?
    fun prefSetPumpModelName(name: String)
    fun prefSetCurrentPumpSid(sid: Int)
    fun prefPumpFinderPumpMac(): String?
    fun prefUnbondOnNextCommInitMac(): String?
    fun prefSetUnbondOnNextCommInitMac(mac: String?)

    // --- Sync/dispatch callbacks (replaces NightscoutSyncWorker, XdripMessageDispatcher, DataClientState) ---
    fun onPumpConnectedSync(pumpSid: Int)
    fun dispatchExternalMessage(message: com.jwoglom.pumpx2.pump.messages.Message)
    fun updateComplicationData(key: String, value: String, timestamp: Instant)

    // --- Debug API callbacks (replaces HttpDebugApiService direct access) ---
    fun onPumpMessageReceived(message: com.jwoglom.pumpx2.pump.messages.Message, source: SendType)
    fun onPumpCriticalError(error: TandemError, source: SendType)

    // --- History log factories (replaces direct HistoryLogFetcher/HistoryLogSyncWorker/HistoryLogRepo) ---
    fun createHistoryLogFetcher(
        pumpSid: Int,
        pumpSession: PumpSession,
        autoFetchEnabled: () -> Boolean,
    ): PumpHistoryLogFetcher

    fun createHistoryLogSyncWorker(requestSync: () -> Unit): PumpHistoryLogSyncWorker
}
