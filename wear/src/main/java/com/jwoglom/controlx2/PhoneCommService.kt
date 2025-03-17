package com.jwoglom.controlx2

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.util.ConnectedState
import com.jwoglom.controlx2.util.StatePrefs
import com.jwoglom.controlx2.util.UpdateComplication
import com.jwoglom.controlx2.util.WearX2Complication
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import timber.log.Timber
import java.time.Instant


class PhoneCommService : WearableListenerService(), MessageClient.OnMessageReceivedListener  {
    private var connected: ConnectedState = ConnectedState.UNKNOWN
    private var notificationId = 0

    private lateinit var messageClient: MessageClient
    private val notificationManagerCompat: NotificationManagerCompat by lazy { NotificationManagerCompat.from(this) }

    override fun onCreate() {
        super.onCreate()
        setupTimber("WPC", context = this)
        Timber.d("wear service onCreate")


        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        Timber.d("create: messageClient: $messageClient")

        updateNotification()
    }

    private fun startForegroundWrapped(id: Int, notification: Notification) {
        startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun updateNotification() {
        var notification = createNotification()
        startForegroundWrapped(1, notification)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ControlX2 Background Notification"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        val channel = NotificationChannel(
            notificationChannelId,
            "Endless Service notifications channel",
            NotificationManager.IMPORTANCE_NONE
        ).let {
            it.description = "Endless Service channel"
            it.setShowBadge(false)
            it.lockscreenVisibility = 0

            it
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            this,
            notificationChannelId
        )

        val title = "ControlX2 is running: $connected"
        return builder
            .setContentTitle(title)
            .setContentText("This notification can be hidden: open Settings > Apps > Notifications > All > ControlX2 and turn Endless Service Notifications off")
            .setContentIntent(pendingIntent)
            .setSmallIcon(IconCompat.createWithResource(this, R.drawable.pump))
            .setTicker(title)
            .setPriority(NotificationCompat.PRIORITY_MAX) // for under android 26 compatibility
            .setOngoing(true)
            .build()
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
            "/from-pump/pump-connected" -> {
                connected = ConnectedState.PHONE_CONNECTED_PUMP_CONNECTED
                StatePrefs(this).connected = Pair(connected.name, Instant.now())
                updateNotification()
            }
            "/from-pump/pump-disconnected" -> {
                connected = ConnectedState.PHONE_CONNECTED_PUMP_DISCONNECTED
                StatePrefs(this).connected = Pair(connected.name, Instant.now())
                updateNotification()
            }
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
            "/to-wear/service-receive-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, false)
            }
        }
    }

    // keep in sync with CommService.PumpCommHandler#onReceiveMessage to ensure messages are sent
    private fun onPumpMessageReceived(message: Message, cached: Boolean) {
        Timber.i("phoneComm onPumpMessageReceived($message)")
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                StatePrefs(applicationContext).pumpBattery = Pair("${message.batteryPercent}", Instant.now())
                UpdateComplication(this, WearX2Complication.PUMP_BATTERY)
            }
            is ControlIQIOBResponse -> {
                StatePrefs(applicationContext).pumpIOB = Pair("${InsulinUnit.from1000To1(message.pumpDisplayedIOB)}", Instant.now())
                UpdateComplication(this, WearX2Complication.PUMP_IOB)
            }
            is CurrentEGVGuiDataResponse -> {
                StatePrefs(applicationContext).cgmReading = Pair("${message.cgmReading}", Dates.fromJan12008EpochSecondsToDate(message.bgReadingTimestampSeconds))
                UpdateComplication(this, WearX2Complication.CGM_READING)
            }
        }
    }

    override fun onConnectedNodes(nodes: MutableList<Node>) {
        Timber.i("onConnectedNodes: ${nodes.size}: $nodes")
        super.onConnectedNodes(nodes)
    }

    override fun onPeerDisconnected(node: Node) {
        Timber.i("onPeerDisconnected $node")
        super.onPeerDisconnected(node)
        connected = ConnectedState.PHONE_DISCONNECTED
        StatePrefs(this).connected = Pair(connected.name, Instant.now())
        // disconnectedNotification("phone disconnected")
        updateNotification()
    }

    private fun disconnectedNotification(reason: String) {
        Thread {
            Thread.sleep(30 * 1000)
            if (connected == ConnectedState.PHONE_CONNECTED_PUMP_CONNECTED) {
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
                .setContentTitle("ControlX2 disconnected")
                .setContentText(reason)
                .setChannelId("disconnectedChannel")
                .build()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManagerCompat.notify(notificationId, notif)
            }
        }.start()
    }

    private fun sendMessage(path: String, message: ByteArray) {
        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        Timber.i("wear sendMessage: $path ${String(message)}")
        fun inner(node: Node) {
            messageClient.sendMessage(node.id, path, message)
                .addOnSuccessListener {
                    Timber.i("Wear message sent: $path ${String(message)} to ${node.displayName}")
                }
                .addOnFailureListener {
                    Timber.w("wear sendMessage callback: ${it} to ${node.displayName}")
                }
        }
        if (path.startsWith("/to-wear")) {
            nodeClient.localNode
                .addOnSuccessListener { localNode ->
                    inner(localNode)
            }
        }
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                Timber.d("wear sendMessage nodes: ${nodes}")
                nodes.forEach { node ->
                    inner(node)
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