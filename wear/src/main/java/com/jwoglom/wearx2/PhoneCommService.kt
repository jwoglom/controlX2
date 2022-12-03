package com.jwoglom.wearx2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "WearableCommService"
private const val SEND_TO_WEAR_PATH = "/send-to-wear"
private const val SEND_TO_PHONE_PATH = "/send-to-phone"

class PhoneCommService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/to-wear/start-activity" -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
//            "/wear/send-data" -> {
//                val intent = Intent("com.jwoglom.wearx2.PhoneCommService");
//                intent.action = Intent.ACTION_SEND
//                intent.putExtra("data", messageEvent.data)
//                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
//            }
        }
    }
}