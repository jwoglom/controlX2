package com.jwoglom.wearx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.response.authentication.CentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.shared.Hex
import com.jwoglom.wearx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.wearx2.shared.WearCommServiceCodes
import com.jwoglom.wearx2.shared.util.DebugTree
import com.jwoglom.wearx2.shared.util.setupTimber
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Instant


class WearCommService : WearableListenerService(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var wearCommHandler: WearCommHandler? = null

    private lateinit var mApiClient: GoogleApiClient
    private lateinit var pump: Pump
    private lateinit var tandemBTHandler: TandemBluetoothHandler

    private var lastResponseMessage: MutableMap<Pair<Characteristic, Byte>, com.jwoglom.pumpx2.pump.messages.Message> = mutableMapOf()

    private inner class Pump() : TandemPump(applicationContext) {
        var lastPeripheral: BluetoothPeripheral? = null
        var isConnected = false

        init {
            enableTconnectAppConnectionSharing()
            enableSendSharedConnectionResponseMessages()
            // before adding relyOnConnectionSharingForAuthentication(), callback issues need to be resolved

            if (isInsulinDeliveryEnabled()) {
                Timber.i("ACTIONS AFFECTING INSULIN DELIVERY ENABLED")
                enableActionsAffectingInsulinDelivery()
            } else {
                Timber.i("Actions affecting insulin delivery are disabled")
            }
            Timber.i("Pump init")
        }

        override fun onReceiveMessage(
            peripheral: BluetoothPeripheral?,
            message: com.jwoglom.pumpx2.pump.messages.Message?
        ) {
            message?.let { lastResponseMessage.put(Pair(it.characteristic, it.opCode()), it) }
            wearCommHandler?.sendMessage("/from-pump/receive-message", PumpMessageSerializer.toBytes(message))

            // Callbacks handled by this service itself
            when (message) {
                is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
            }
        }

        override fun onReceiveQualifyingEvent(
            peripheral: BluetoothPeripheral?,
            events: MutableSet<QualifyingEvent>?
        ) {
            Timber.i("onReceiveQualifyingEvent: $events")
            events?.forEach { event ->
                event.suggestedHandlers.forEach {
                    Timber.i("onReceiveQualifyingEvent: running handler for $event message: ${it.get()}")
                    command(it.get())
                }
            }
            wearCommHandler?.sendMessage("/from-pump/receive-qualifying-event", PumpQualifyingEventsSerializer.toBytes(events))
        }

        override fun onWaitingForPairingCode(
            peripheral: BluetoothPeripheral?,
            centralChallengeResponse: CentralChallengeResponse?
        ) {
            PumpState.getPairingCode(context)?.let {
                Timber.i("Pairing with saved code: $it centralChallenge: $centralChallengeResponse")
                pair(peripheral, centralChallengeResponse, it)
                wearCommHandler?.sendMessage(
                    "/from-pump/entered-pairing-code",
                    PumpMessageSerializer.toBytes(centralChallengeResponse))
            } ?: run {
                wearCommHandler?.sendMessage(
                    "/from-pump/missing-pairing-code",
                    PumpMessageSerializer.toBytes(centralChallengeResponse))
            }
        }

        override fun onInitialPumpConnection(peripheral: BluetoothPeripheral?) {
            lastPeripheral = peripheral
            val wait = (500..1000).random()
            Timber.i("Waiting to pair onInitialPumpConnection to avoid race condition with tconnect app for ${wait}ms")
            Thread.sleep(wait.toLong())
            if (Packetize.txId.get() > 0) {
                Timber.w("Not pairing in onInitialPumpConnection because it looks like the tconnect app has already paired with txId=${Packetize.txId.get()}")
                return
            }
            super.onInitialPumpConnection(peripheral)
        }

        override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
            super.onPumpConnected(peripheral)
            lastPeripheral = peripheral
            val wait = (100..250).random()
            Timber.i("service onPumpConnected -- waiting for ${wait}ms to avoid race conditions")
            Thread.sleep(wait.toLong())
            isConnected = true
            wearCommHandler?.sendMessage("/from-pump/pump-connected",
                peripheral?.name!!.toByteArray()
            )
        }

        override fun onPumpModel(peripheral: BluetoothPeripheral?, modelNumber: String?) {
            super.onPumpModel(peripheral, modelNumber)
            Timber.i("service onPumpModel")
            wearCommHandler?.sendMessage("/from-pump/pump-model",
                modelNumber!!.toByteArray()
            )
        }

        override fun onPumpDisconnected(
            peripheral: BluetoothPeripheral?,
            status: HciStatus?
        ): Boolean {
            Timber.i("service onPumpDisconnected")
            lastPeripheral = null
            isConnected = false
            wearCommHandler?.sendMessage("/from-pump/pump-disconnected",
                peripheral?.name!!.toByteArray()
            )
            return super.onPumpDisconnected(peripheral, status)
        }

        override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
            super.onPumpCriticalError(peripheral, reason)
            Timber.w("onPumpCriticalError $reason")
            wearCommHandler?.sendMessage("/from-pump/pump-critical-error",
                reason?.message!!.toByteArray()
            );
        }

        @Synchronized
        fun command(message: com.jwoglom.pumpx2.pump.messages.Message?) {
            if (lastPeripheral == null) {
                Timber.w("Not sending message because no saved peripheral yet: $message")
                return
            }

            if (!isConnected) {
                Timber.w("Not sending message because no onConnected event yet: $message")
                return
            }

            Timber.i("Pump send command: $message")
            sendCommand(lastPeripheral, message)
            Thread.sleep(20) // artificially limit the rate at which we send requests to the pump (50rps)
        }

        override fun toString(): String {
            return "Pump(isConnected=$isConnected, lastPeripheral=$lastPeripheral)"
        }

    }

    // Handler that receives messages from the thread
    private inner class WearCommHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                WearCommServiceCodes.INIT_PUMP_COMM.ordinal -> {
                    Timber.i("wearCommHandler init pump class")
                    pump = Pump()
                    tandemBTHandler = TandemBluetoothHandler.getInstance(applicationContext, pump, null)
                    while (true) {
                        try {
                            Timber.i("Starting scan...")
                            tandemBTHandler.startScan()
                        } catch (e: SecurityException) {
                            Timber.e("Waiting for BT permissions $e")
                            Thread.sleep(500)
                            continue
                        }
                        break
                    }
                }
                WearCommServiceCodes.SEND_PUMP_COMMAND.ordinal -> {
                    Timber.i("wearCommHandler send command raw: ${String(msg.obj as ByteArray)}")
                    val pumpMsg = PumpMessageSerializer.fromBytes(msg.obj as ByteArray)
                    if (this@WearCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(pumpMsg)) {
                        Timber.i("wearCommHandler send command: $pumpMsg")
                        pump.command(pumpMsg)
                    } else {
                        Timber.w("wearCommHandler not sending command due to pump state: $pump $pumpMsg")
                    }
                }
                WearCommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("wearCommHandler send commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (this@WearCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler send command: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending command due to pump state: $pump $it")
                        }
                    }
                }
                WearCommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal -> {
                    Timber.i("wearCommHandler send commands bust cache raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler busted cache: $it")
                            lastResponseMessage.remove(Pair(it.characteristic, it.responseOpCode))
                        }
                        if (this@WearCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler send command bust cache: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending command due to pump state: $pump $it")
                        }
                    }
                }
                WearCommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("wearCommHandler cached pump commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            val response = lastResponseMessage.get(Pair(it.characteristic, it.responseOpCode))
                            Timber.i("wearCommHandler cached hit: $response")
                            wearCommHandler?.sendMessage("/from-pump/receive-cached-message", PumpMessageSerializer.toBytes(response))
                        } else if (this@WearCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler cached miss: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending cached send command due to pump state: $pump $it")
                        }
                    }
                }
                WearCommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal -> {
                    Timber.i("wearCommHandler send bolus raw: ${String(msg.obj as ByteArray)}")
                    val secretKey = prefs(applicationContext)?.getString("initiateBolusSecret", "") ?: ""
                    val confirmedBolus =
                        InitiateConfirmedBolusSerializer.fromBytes(secretKey, msg.obj as ByteArray)

                    val messageOk = confirmedBolus.left
                    val pumpMsg = confirmedBolus.right
                    if (!messageOk) {
                        Timber.w("wearCommHandler bolus invalid signature")
                        sendMessage("/to-wear/bolus-blocked-signature", "WearCommHandler".toByteArray())
                    } else if (this@WearCommService::pump.isInitialized && pump.isConnected && isBolusCommand(pumpMsg)) {
                        Timber.i("wearCommHandler send bolus command with valid signature: $pumpMsg")
                        if (!isInsulinDeliveryEnabled()) {
                            Timber.e("No insulin delivery messages enabled -- blocking bolus command $pumpMsg")
                            sendMessage("/to-wear/bolus-not-enabled", "from_self".toByteArray())
                            return
                        }
                        try {
                            pump.command(pumpMsg)
                        } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                            Timber.e(e)
                            sendMessage("/to-wear/bolus-not-enabled", "from_pumpx2_lib".toByteArray())
                        }
                    } else {
                        Timber.w("wearCommHandler not sending command due to pump state: $pump")
                    }
                }
            }
        }

        fun sendMessage(path: String, message: ByteArray) {
            Timber.i("service sendMessage: $path ${String(message)}")
            Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
                Timber.i("service sendMessage nodes: ${nodes.nodes}")
                nodes.nodes.forEach { node ->
                    Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                        .setResultCallback { result ->
                            if (result.status.isSuccess) {
                                Timber.i("service message sent: $path ${String(message)} to: $node")
                            } else {
                                Timber.w("service sendMessage callback: ${result.status} for: $path ${String(message)}")
                            }
                        }
                }
            }
        }

        private fun isBolusCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
            return (message is InitiateBolusRequest) || message.opCode() == InitiateBolusRequest().opCode()
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupTimber("MWC")
        Timber.d("service onCreate")

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
//        HandlerThread("PumpCommServiceThread", Process.THREAD_PRIORITY_FOREGROUND).apply {
//            start()
//            Timber.d("service thread start")

            mApiClient = GoogleApiClient.Builder(applicationContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this@WearCommService)
                .addOnConnectionFailedListener(this@WearCommService)
                .build()

            mApiClient.connect()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            wearCommHandler = WearCommHandler(looper)

            var notification = createNotification()
            startForeground(1, notification)
        // }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("service messageReceived: ${messageEvent.path} ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-phone/open-activity" -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            "/to-phone/force-reload" -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            "/to-phone/is-pump-connected" -> {
                if (this::pump.isInitialized && pump.isConnected && pump.lastPeripheral != null) {
                    wearCommHandler?.sendMessage("/from-pump/pump-connected",
                        pump.lastPeripheral?.name!!.toByteArray()
                    )
                } else {
                    Timber.e("pump not initialized")
                }
            }
            "/to-phone/bolus-request" -> {
                if (this::pump.isInitialized && pump.isConnected && pump.lastPeripheral != null) {
                    confirmBolusRequest(PumpMessageSerializer.fromBytes(messageEvent.data) as InitiateBolusRequest)
                } else {
                    Timber.e("pump not initialized")
                }
            }
            "/to-phone/bolus-cancel" -> {
                Timber.i("bolus state cancelled")
                resetBolusPrefs(this)
            }
            "/to-phone/initiate-confirmed-bolus" -> {
                if (this::pump.isInitialized && pump.isConnected && pump.lastPeripheral != null) {
                    val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                    val confirmedBolus =
                        InitiateConfirmedBolusSerializer.fromBytes(secretKey, messageEvent.data)

                    val messageOk = confirmedBolus.left
                    val initiateMessage = confirmedBolus.right
                    if (!messageOk) {
                        Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                        wearCommHandler?.sendMessage("/to-wear/blocked-bolus-signature",
                            "WearCommService".toByteArray()
                        )
                        return
                    }

                    Timber.i("sending confirmed bolus request: $initiateMessage")
                    sendPumpCommBolusMessage(messageEvent.data)

                } else {
                    Timber.e("pump not initialized")
                }
            }
            "/to-pump/command" -> {
                sendPumpCommMessage(messageEvent.data)
            }
            "/to-pump/commands" -> {
                sendPumpCommMessages(messageEvent.data)
            }
            "/to-pump/commands-bust-cache" -> {
                sendPumpCommMessagesBustCache(messageEvent.data)
            }
            "/to-pump/cached-commands" -> {
                handleCachedCommandsRequest(messageEvent.data)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("WearCommService onStartCommand $intent $flags $startId")
        Toast.makeText(this, "WearX2 service starting", Toast.LENGTH_SHORT).show()

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.INIT_PUMP_COMM.ordinal
            wearCommHandler?.sendMessage(msg)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.w("WearCommService onTaskRemoved")
        val restartServiceIntent = Intent(
            applicationContext, this.javaClass
        )
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        Toast.makeText(this, "WearX2 service removed", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun sendPumpCommMessage(pumpMsgBytes: ByteArray) {
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.SEND_PUMP_COMMAND.ordinal
            msg.obj = pumpMsgBytes
            wearCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessages(pumpMsgBytes: ByteArray) {
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal
            msg.obj = pumpMsgBytes
            wearCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessagesBustCache(pumpMsgBytes: ByteArray) {
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal
            msg.obj = pumpMsgBytes
            wearCommHandler?.sendMessage(msg)
        }
    }
    private fun handleCachedCommandsRequest(rawBytes: ByteArray) {
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal
            msg.obj = rawBytes
            wearCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommBolusMessage(initiateConfirmedBolusBytes: ByteArray) {
        wearCommHandler?.obtainMessage()?.also { msg ->
            msg.what = WearCommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal
            msg.obj = initiateConfirmedBolusBytes
            wearCommHandler?.sendMessage(msg)
        }
    }

    private var bolusNotificationId: Int = 1000


    private fun confirmBolusRequest(request: InitiateBolusRequest) {
        val units = twoDecimalPlaces(InsulinUnit.from1000To1(request.totalVolume))
        Timber.i("confirmBolusRequest $units: $request")
        bolusNotificationId++
        prefs(this)?.edit()
            ?.putString("initiateBolusRequest", Hex.encodeHexString(PumpMessageSerializer.toBytes(request)))
            ?.putString("initiateBolusSecret", Hex.encodeHexString(Bytes.getSecureRandom10Bytes()))
            ?.putLong("initiateBolusTime", Instant.now().toEpochMilli())
            ?.putInt("initiateBolusNotificationId", bolusNotificationId)
            ?.apply()

        val builder = confirmBolusRequestBaseNotification(this, "Bolus Request", "$units units. Press Confirm to deliver.")
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L))

        val rejectIntent = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
            putExtra("action", "REJECT")
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(this, 2000, rejectIntent, FLAG_IMMUTABLE or FLAG_ONE_SHOT)
        builder.addAction(R.drawable.decline, "Reject", rejectPendingIntent)

        val confirmIntent = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
            putExtra("action", "INITIATE")
            putExtra("request", PumpMessageSerializer.toBytes(request))
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(this, 2001, confirmIntent, FLAG_IMMUTABLE or FLAG_ONE_SHOT)
        builder.addAction(R.drawable.bolus_icon, "Confirm ${units}u", confirmPendingIntent)

        val notif = builder.build()
        Timber.i("bolus notification $bolusNotificationId $builder $notif")
        makeNotif(bolusNotificationId, notif)
    }

    private fun onReceiveInitiateBolusResponse(response: InitiateBolusResponse?) {
        val intent: Intent? = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
            putExtra("action", "INITIATE_RESPONSE")
            putExtra("response", PumpMessageSerializer.toBytes(response))
        }
        applicationContext.startService(intent)
    }

    private fun makeNotif(id: Int, notif: Notification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        notificationManager.notify(id, notif)
    }

    private fun cancelNotif(id: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        notificationManager.cancel(id)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Toast.makeText(this, "WearX2 service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onConnected(bundle: Bundle?) {
        Timber.i("service onConnected $bundle")
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("service onConnectionSuspended: $id")
        mApiClient.reconnect()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("service onConnectionFailed: $result")
        mApiClient.reconnect()
    }


    private fun createNotification(): Notification {
        val notificationChannelId = "WearX2 Background Notification"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        val channel = NotificationChannel(
            notificationChannelId,
            "Endless Service notifications channel",
            NotificationManager.IMPORTANCE_LOW
        ).let {
            it.description = "Endless Service channel"
            it
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE)

        val builder: Notification.Builder = Notification.Builder(
            this,
            notificationChannelId
        )

        return builder
            .setContentTitle("WearX2")
            .setContentText("WearX2 is running")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.pump)
            .setTicker("WearX2 is running")
            .setPriority(Notification.PRIORITY_MIN) // for under android 26 compatibility
            .build()
    }

    private fun prefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences("WearX2", MODE_PRIVATE)
    }

    private fun resetBolusPrefs(context: Context) {
        prefs(context)?.edit()
            ?.remove("initiateBolusRequest")
            ?.remove("initiateBolusTime")
            ?.apply()
    }

    private fun isInsulinDeliveryEnabled(): Boolean {
        return prefs(applicationContext)?.getBoolean("insulinDeliveryEnabled", false) ?: false
    }

    private fun triggerAppReload(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}



fun confirmBolusRequestBaseNotification(context: Context?, title: String, text: String): NotificationCompat.Builder {
    val notificationChannelId = "Confirm Bolus"

    val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
    val channel = NotificationChannel(
        notificationChannelId,
        "Confirm Bolus",
        NotificationManager.IMPORTANCE_HIGH
    ).let {
        it.description = "Confirm Bolus"
        it
    }
    notificationManager.createNotificationChannel(channel)

    //val intent = Intent(this, MainActivity::class.java)
    //val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE)

    return NotificationCompat.Builder(
        context,
        notificationChannelId
    )
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(R.drawable.bolus_icon)
        .setTicker(title)
        .setPriority(Notification.PRIORITY_MAX) // for under android 26 compatibility
        .setAutoCancel(true)

}