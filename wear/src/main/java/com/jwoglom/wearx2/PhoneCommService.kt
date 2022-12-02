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
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

//    override fun onDataChanged(dataEvents: DataEventBuffer) {
//        super.onDataChanged(dataEvents)
//        Timber.d("wear onDataChanged: " + dataEvents)
//
//        // Loop through the events and send a message
//        // to the node that created the data item.
//        dataEvents.map { it.dataItem.uri }
//            .forEach { uri ->
//                scope.launch {
//
//                    // Get the node id from the host value of the URI
//                    val nodeId: String = uri.host!!
//                    // Set the data of the message to be the bytes of the URI
//                    val payload: ByteArray = uri.toString().toByteArray()
//
//                    // Send the RPC
//                    withContext(scope.coroutineContext) {
//                        messageClient
//                            .sendMessage(nodeId, SEND_TO_PHONE_PATH, payload)
//                        Timber.d("wear onDataChanged message sent")
//                    }
//                }
//            }
//    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/wear/start-activity" -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            "/wear/send-data" -> {
                val intent = Intent("com.jwoglom.wearx2.PhoneCommService");
                intent.action = Intent.ACTION_SEND
                intent.putExtra("data", messageEvent.data)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}