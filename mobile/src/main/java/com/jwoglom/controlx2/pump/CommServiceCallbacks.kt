package com.jwoglom.controlx2.pump

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.HttpDebugApiService
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import kotlinx.coroutines.Job

/**
 * Interface defining callbacks that extracted pump components use to communicate
 * with CommService. Replaces the `this@CommService` inner-class back-reference pattern.
 */
interface CommServiceCallbacks {
    fun getApplicationContext(): Context
    val supervisorJob: Job
    val pumpCommState: PumpCommState
    val historyLogRepo: HistoryLogRepo
    val httpDebugApiService: HttpDebugApiService?
    fun sendWearCommMessage(path: String, message: ByteArray)
    fun updateNotification(statusText: String? = null)
    fun updateNotificationWithPumpData(message: com.jwoglom.pumpx2.pump.messages.Message)
    fun broadcastHistoryLogItem(item: HistoryLogItem)
    fun markConnectionTime()
    fun showToast(text: String, duration: Int)
    fun getWearPrefs(): SharedPreferences?
    fun sendPumpCommMessages(pumpMsgBytes: ByteArray)
}
