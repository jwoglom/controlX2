package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.clientcomm.ClientConnectionState
import com.jwoglom.controlx2.clientcomm.ClientMessageHandler
import com.jwoglom.controlx2.clientcomm.ClientSideEffects
import com.jwoglom.controlx2.clientcomm.ClientStateStore
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.util.DataClientState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Instant

/**
 * Service that runs on the phone when the watch is the pump-host.
 * Acts as a client of the watch pump-host, receiving pump data and
 * dispatching to local sync systems (Nightscout, xDrip+, etc.).
 */
class MobileClientService : Service() {
    private var connectionState: ClientConnectionState = ClientConnectionState.UNKNOWN

    private lateinit var messageBus: MessageBus
    private lateinit var clientMessageHandler: ClientMessageHandler
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        setupTimber("MCS", context = this)
        Timber.d("MobileClientService onCreate")

        messageBus = MessageBusFactory.createMessageBus(this)

        val stateStore = MobileClientStateStore(this)

        clientMessageHandler = ClientMessageHandler(
            stateStore = stateStore,
            sideEffects = object : ClientSideEffects {
                override fun onConnectionStateChanged(state: ClientConnectionState) {
                    connectionState = state
                    updateNotification()
                }
                override fun onPumpDataUpdated(key: String) {
                    Timber.d("MobileClientService: pump data updated: $key")
                    // Update DataClientState for any watchers (widgets, etc.)
                    val dataState = DataClientState(applicationContext)
                    when (key) {
                        "pumpBattery" -> {
                            stateStore.pumpBattery?.let {
                                dataState.pumpBattery = it
                            }
                        }
                        "pumpIOB" -> {
                            stateStore.pumpIOB?.let {
                                dataState.pumpIOB = it
                            }
                        }
                        "cgmReading" -> {
                            stateStore.cgmReading?.let {
                                dataState.cgmReading = it
                            }
                        }
                    }
                }
                override fun onGlucoseUnitUpdated() {
                    Timber.d("MobileClientService: glucose unit updated")
                }
                override fun onOpenActivityRequested() {
                    startActivity(
                        Intent(applicationContext, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }
                override fun onBolusBlockedSignature() {
                    Timber.w("MobileClientService: blocked bolus signature")
                    Toast.makeText(applicationContext, "Bolus blocked: invalid signature", Toast.LENGTH_LONG).show()
                }
                override fun onBolusNotEnabled() {
                    Timber.w("MobileClientService: bolus not enabled")
                    Toast.makeText(applicationContext, "Bolus not enabled on pump-host", Toast.LENGTH_LONG).show()
                }
            },
            scope = serviceScope,
        )

        messageBus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                clientMessageHandler.handleMessage(path, data)
            }
        })

        Timber.d("MobileClientService: messageBus: $messageBus")

        updateNotification()
    }

    private fun updateNotification() {
        val notification = createNotification()
        startForeground(2, notification)
    }

    private fun createNotification(): Notification {
        val channelId = "ControlX2 Client Service"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Client Service notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ControlX2 client service channel"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val stateText = when (connectionState) {
            ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED -> "Watch connected to pump"
            ClientConnectionState.HOST_CONNECTED_PUMP_DISCONNECTED -> "Watch connected, pump disconnected"
            ClientConnectionState.HOST_DISCONNECTED -> "Watch disconnected"
            ClientConnectionState.UNKNOWN -> "Connecting to watch..."
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ControlX2 Client: $stateText")
            .setContentText("Watch is managing the pump connection")
            .setContentIntent(pendingIntent)
            .setSmallIcon(IconCompat.createWithResource(this, R.drawable.pump))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Timber.d("MobileClientService onDestroy")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

/**
 * ClientStateStore implementation for mobile, backed by SharedPreferences.
 * Mirrors the watch's StatePrefs pattern.
 */
class MobileClientStateStore(context: Context) : ClientStateStore {

    private val prefs = context.getSharedPreferences("MobileClientState", Context.MODE_PRIVATE)

    override var connectionState: ClientConnectionState
        get() {
            val name = prefs.getString("connectionState", null)
            return try {
                if (name != null) ClientConnectionState.valueOf(name) else ClientConnectionState.UNKNOWN
            } catch (_: IllegalArgumentException) {
                ClientConnectionState.UNKNOWN
            }
        }
        set(value) {
            prefs.edit().putString("connectionState", value.name).apply()
        }

    var pumpBattery: Pair<String, Instant>?
        get() = getPair("pumpBattery")
        set(value) { setPair("pumpBattery", value) }

    var pumpIOB: Pair<String, Instant>?
        get() = getPair("pumpIOB")
        set(value) { setPair("pumpIOB", value) }

    var cgmReading: Pair<String, Instant>?
        get() = getPair("cgmReading")
        set(value) { setPair("cgmReading", value) }

    override fun updatePumpBattery(value: String, timestamp: Instant) {
        pumpBattery = Pair(value, timestamp)
    }

    override fun updatePumpIOB(value: String, timestamp: Instant) {
        pumpIOB = Pair(value, timestamp)
    }

    override fun updateCgmReading(value: String, timestamp: Instant) {
        cgmReading = Pair(value, timestamp)
    }

    override fun updateGlucoseUnit(unit: GlucoseUnit) {
        prefs.edit().putString("glucoseUnit", unit.name).apply()
    }

    private fun getPair(key: String): Pair<String, Instant>? {
        val s = prefs.getString(key, null) ?: return null
        val parts = s.split(";;", limit = 2)
        if (parts.size != 2) return null
        return Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
    }

    private fun setPair(key: String, pair: Pair<String, Instant>?) {
        if (pair != null) {
            prefs.edit().putString(key, "${pair.first};;${pair.second.toEpochMilli()}").apply()
        }
    }
}
