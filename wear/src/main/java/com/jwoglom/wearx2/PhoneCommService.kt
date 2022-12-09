package com.jwoglom.wearx2

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.shared.util.setupTimber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


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
            "/to-wear/start-activity" -> {
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
        }
    }

    override fun onConnectedNodes(nodes: MutableList<Node>) {
        Timber.i("onConnectedNodes: ${nodes.size}: $nodes")
        super.onConnectedNodes(nodes)
        if (nodes.size == 0) {
            currentlyConnected = false
            disconnectedNotification("phone disconnected")
        }
    }

    override fun onPeerDisconnected(node: Node) {
        Timber.i("onPeerDisconnected $node")
        super.onPeerDisconnected(node)
        currentlyConnected = false
        disconnectedNotification("phone disconnected")
    }

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
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("wear sendMessage nodes: ${nodes.nodes}")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                    .setResultCallback { result ->
                        if (result.status.isSuccess) {
                            Timber.i("Wear message sent: $path ${String(message)}")
                        } else {
                            Timber.w("wear sendMessage callback: ${result.status}")
                        }
                    }
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