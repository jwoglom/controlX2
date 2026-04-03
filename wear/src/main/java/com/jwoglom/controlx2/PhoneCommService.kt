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
import android.app.Service
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.clientcomm.ClientConnectionState
import com.jwoglom.controlx2.clientcomm.ClientMessageHandler
import com.jwoglom.controlx2.clientcomm.ClientSideEffects
import com.jwoglom.controlx2.messaging.WearMessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.util.StatePrefs
import com.jwoglom.controlx2.util.UpdateComplication
import com.jwoglom.controlx2.util.WearX2Complication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber


class PhoneCommService : Service() {
    private var connectionState: ClientConnectionState = ClientConnectionState.UNKNOWN
    private var notificationId = 0

    private lateinit var messageBus: MessageBus
    private lateinit var clientMessageHandler: ClientMessageHandler
    private val notificationManagerCompat: NotificationManagerCompat by lazy { NotificationManagerCompat.from(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        setupTimber("WPC", context = this)
        Timber.d("wear service onCreate")

        messageBus = WearMessageBus(this)

        clientMessageHandler = ClientMessageHandler(
            stateStore = StatePrefs(this),
            sideEffects = object : ClientSideEffects {
                override fun onConnectionStateChanged(state: ClientConnectionState) {
                    connectionState = state
                    updateNotification()
                }
                override fun onPumpDataUpdated(key: String) {
                    when (key) {
                        "pumpBattery" -> UpdateComplication(this@PhoneCommService, WearX2Complication.PUMP_BATTERY)
                        "pumpIOB" -> UpdateComplication(this@PhoneCommService, WearX2Complication.PUMP_IOB)
                        "cgmReading" -> UpdateComplication(this@PhoneCommService, WearX2Complication.CGM_READING)
                    }
                }
                override fun onGlucoseUnitUpdated() {
                    UpdateComplication(this@PhoneCommService, WearX2Complication.CGM_READING)
                }
                override fun onOpenActivityRequested() {
                    startActivity(
                        Intent(applicationContext, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }
                override fun onBolusBlockedSignature() {
                    Timber.w("PhoneCommService: blocked bolus signature")
                }
                override fun onBolusNotEnabled() {
                    Timber.w("PhoneCommService: bolus not enabled")
                }
            },
            scope = serviceScope,
        )

        messageBus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                clientMessageHandler.handleMessage(path, data)
            }
        })

        Timber.d("create: messageBus: $messageBus")

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

        val title = "ControlX2 is running: $connectionState"
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun disconnectedNotification(reason: String) {
        Thread {
            Thread.sleep(30 * 1000)
            if (connectionState == ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED) {
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
        Timber.i("wear sendMessage: $path ${String(message)}")
        messageBus.sendMessage(path, message)
    }

    private fun isForegrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        val isForeground = appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
        Timber.i("isForegrounded: $isForeground")
        return isForeground
    }
}
