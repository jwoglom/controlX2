package com.jwoglom.wearx2

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.util.setupTimber
import com.jwoglom.wearx2.util.StatePrefs
import timber.log.Timber
import java.time.Instant


class PhoneCommService : WearableListenerService() {
    private var currentlyConnected = false
    private var notificationId = 0

    private val notificationManagerCompat: NotificationManagerCompat by lazy { NotificationManagerCompat.from(this) }

    override fun onCreate() {
        super.onCreate()
        setupTimber("WPC")
        Timber.d("wear service onCreate")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("wear service onMessageReceived ${messageEvent.path}: ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-wear/open-activity" -> {
                startActivity(
                    Intent(applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
//            "/from-pump/pump-connected" -> {
//                currentlyConnected = true
//            }
//            "/from-pump/pump-disconnected" -> {
//                currentlyConnected = false
//                disconnectedNotification("pump disconnected")
//            }
            "/to-wear/blocked-bolus-signature" -> {
                Timber.w("PhoneCommService: blocked bolus signature")
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("route", Screen.BolusBlocked.route)
            }
            "/to-wear/bolus-not-enabled" -> {
                Timber.w("PhoneCommService: bolus not enabled")
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("route", Screen.BolusNotEnabled.route)
            }
            "/from-pump/receive-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, false)
            }
        }
    }

    private fun onPumpMessageReceived(message: Message, cached: Boolean) {
        Timber.i("phoneComm onPumpMessageReceived($message)")
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                StatePrefs(this).pumpBattery = Pair("${message.batteryPercent}", Instant.now())
            }
        }
    }

    override fun onConnectedNodes(nodes: MutableList<Node>) {
        Timber.i("onConnectedNodes: ${nodes.size}: $nodes")
        super.onConnectedNodes(nodes)
        if (nodes.size == 0) {
            currentlyConnected = false
            // disconnectedNotification("phone disconnected")
        }
    }

//    override fun onPeerDisconnected(node: Node) {
//        Timber.i("onPeerDisconnected $node")
//        super.onPeerDisconnected(node)
//        currentlyConnected = false
//        disconnectedNotification("phone disconnected")
//    }

    private fun disconnectedNotification(reason: String) {
        Thread {
            Thread.sleep(30 * 1000)
            if (currentlyConnected) {
                return@Thread
            }

            notificationManagerCompat.createNotificationChannel(
                NotificationChannel(
                    "disconnectedChannel",
                    "Disconnected",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            val notif = NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.comm_error)
                .setContentTitle("WearX2 disconnected")
                .setContentText(reason)
                .setChannelId("disconnectedChannel")
                .build()

            notificationManagerCompat.notify(notificationId, notif)
        }.start()
    }

    private fun sendMessage(path: String, message: ByteArray) {
        val mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build()
        mApiClient.connect()
        while (!mApiClient.isConnected) {
            Timber.d("wear service sendMessage waiting for connect $path ${String(message)}")
            Thread.sleep(100)
        }
        Timber.i("wear sendMessage: $path ${String(message)}")
        fun inner(node: Node) {
            Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                .setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Timber.i("Wear message sent: $path ${String(message)} to ${node.displayName}")
                    } else {
                        Timber.w("wear sendMessage callback: ${result.status} to ${node.displayName}")
                    }
                }
        }
        if (path.startsWith("/to-wear")) {
            Wearable.NodeApi.getLocalNode(mApiClient).setResultCallback { nodes ->
                Timber.d("wear sendMessage local: ${nodes.node}")
                inner(nodes.node)
            }
        }
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.d("wear sendMessage nodes: ${nodes.nodes}")
            nodes.nodes.forEach { node ->
                inner(node)
            }
        }
    }

    private fun isForegrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        val isForeground = appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
        Timber.i("isForegrounded: $isForeground")
        return isForeground
    }
}