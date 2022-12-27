package com.jwoglom.wearx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process.THREAD_PRIORITY_FOREGROUND
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
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
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.authentication.CentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.authentication.PumpChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.shared.Hex
import com.jwoglom.wearx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.wearx2.shared.CommServiceCodes
import com.jwoglom.wearx2.shared.util.setupTimber
import com.jwoglom.wearx2.shared.util.shortTime
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Duration
import java.time.Instant


const val CacheSeconds = 30

class CommService : WearableListenerService(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var pumpCommHandler: PumpCommHandler? = null

    private lateinit var mApiClient: GoogleApiClient

    private var lastResponseMessage: MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>> = mutableMapOf()
    private var lastTimeSinceReset: TimeSinceResetResponse? = null

    // Handler that receives messages from the thread
    private inner class PumpCommHandler(looper: Looper) : Handler(looper) {
        private lateinit var pump: Pump
        private lateinit var tandemBTHandler: TandemBluetoothHandler

        private inner class Pump() : TandemPump(applicationContext) {
            var lastPeripheral: BluetoothPeripheral? = null
            var isConnected = false

            init {
                enableTconnectAppConnectionSharing()
                enableSendSharedConnectionResponseMessages()
                // before adding relyOnConnectionSharingForAuthentication(), callback issues need to be resolved

                if (Prefs(applicationContext).insulinDeliveryActions()) {
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
                message?.let { lastResponseMessage.put(Pair(it.characteristic, it.opCode()), Pair(it, Instant.now())) }
                sendWearCommMessage("/from-pump/receive-message", PumpMessageSerializer.toBytes(message))

                // Callbacks handled by this service itself
                when (message) {
                    is TimeSinceResetResponse -> onReceiveTimeSinceResetResponse(message)
                    is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
                }
                message?.let { updateNotificationWithPumpData(it) }
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
                sendWearCommMessage("/from-pump/receive-qualifying-event", PumpQualifyingEventsSerializer.toBytes(events))
            }

            var pairingCodeCentralChallenge: CentralChallengeResponse? = null
            override fun onWaitingForPairingCode(
                peripheral: BluetoothPeripheral?,
                centralChallengeResponse: CentralChallengeResponse?
            ) {
                pairingCodeCentralChallenge = centralChallengeResponse
                performPairing(peripheral, centralChallengeResponse, false)
            }

            override fun onInvalidPairingCode(
                peripheral: BluetoothPeripheral?,
                resp: PumpChallengeResponse?
            ) {
                sendWearCommMessage("/from-pump/invalid-pairing-code", "".toByteArray())
                super.onInvalidPairingCode(peripheral, resp)
            }

            fun performPairing(
                peripheral: BluetoothPeripheral?,
                centralChallengeResponse: CentralChallengeResponse?,
                manuallyTriggered: Boolean
            ) {
                Timber.i("performPairing manuallyTriggered=$manuallyTriggered")
                PumpState.getPairingCode(context)?.let {
                    Timber.i("Pairing with saved code: $it centralChallenge: $centralChallengeResponse")
                    pair(peripheral, centralChallengeResponse, it)
                    sendWearCommMessage(
                        "/from-pump/entered-pairing-code",
                        PumpMessageSerializer.toBytes(centralChallengeResponse))
                } ?: run {
                    sendWearCommMessage(
                        "/from-pump/missing-pairing-code",
                        PumpMessageSerializer.toBytes(centralChallengeResponse))
                }
            }

            /**
             * Callback is not run when the pump is already bonded
             */
            override fun onPumpDiscovered(
                peripheral: BluetoothPeripheral?,
                scanResult: ScanResult?
            ): Boolean {
                sendWearCommMessage("/from-pump/pump-discovered", "${peripheral?.name}".toByteArray())
                return super.onPumpDiscovered(peripheral, scanResult)
            }

            override fun onInitialPumpConnection(peripheral: BluetoothPeripheral?) {
                lastPeripheral = peripheral
                val wait = (500..1000).random()
                Timber.i("Waiting to pair onInitialPumpConnection to avoid race condition with tconnect app for ${wait}ms")
                Thread.sleep(wait.toLong())

                sendWearCommMessage("/from-pump/initial-pump-connection", "${peripheral?.name}".toByteArray())

                if (Packetize.txId.get() > 0) {
                    Timber.w("Not pairing in onInitialPumpConnection because it looks like the tconnect app has already paired with txId=${Packetize.txId.get()}")
                    return
                }
                super.onInitialPumpConnection(peripheral)
            }

            override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
                lastPeripheral = peripheral
                var numResponses = -99999
                while (PumpState.processedResponseMessages != numResponses) {
                    numResponses = PumpState.processedResponseMessages
                    val wait = (250..500).random()
                    Timber.i("service onPumpConnected -- waiting for ${wait}ms to avoid race conditions: (processedResponseMessages: ${PumpState.processedResponseMessages})")
                    Thread.sleep(wait.toLong())
                }
                Timber.i("service onPumpConnected -- checking for base messages (processedResponseMessages: ${PumpState.processedResponseMessages})")
                if (!lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, ApiVersionRequest().opCode()))) {
                    Timber.i("service onPumpConnected -- sending ApiVersionRequest")
                    this.sendCommand(peripheral, ApiVersionRequest())
                }

                if (!lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, TimeSinceResetResponse().opCode()))) {
                    Timber.i("service onPumpConnected -- sending TimeSinceResetResponse")
                    this.sendCommand(peripheral, TimeSinceResetRequest())
                }
                Thread.sleep(250)
                isConnected = true
                Timber.i("service onPumpConnected: $this")
                sendWearCommMessage("/from-pump/pump-connected",
                    peripheral?.name!!.toByteArray()
                )
                updateNotification("Connected to pump at ${shortTime(Instant.now())}")
            }

            override fun onPumpModel(peripheral: BluetoothPeripheral?, modelNumber: String?) {
                super.onPumpModel(peripheral, modelNumber)
                Timber.i("service onPumpModel")
                sendWearCommMessage("/from-pump/pump-model",
                    modelNumber!!.toByteArray()
                )
            }

            override fun onPumpDisconnected(
                peripheral: BluetoothPeripheral?,
                status: HciStatus?
            ): Boolean {
                Timber.i("service onPumpDisconnected: isConnected=false")
                lastPeripheral = null
                lastResponseMessage.clear()
                lastTimeSinceReset = null
                isConnected = false
                sendWearCommMessage("/from-pump/pump-disconnected",
                    peripheral?.name!!.toByteArray()
                )
                updateNotification("Disconnected from pump at ${shortTime(Instant.now())}")
                return super.onPumpDisconnected(peripheral, status)
            }

            override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
                super.onPumpCriticalError(peripheral, reason)
                Timber.w("onPumpCriticalError $reason")
                sendWearCommMessage("/from-pump/pump-critical-error",
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
            }

            override fun toString(): String {
                return "Pump(isConnected=$isConnected, lastPeripheral=$lastPeripheral)"
            }

        }

        
        private fun onReceiveInitiateBolusResponse(response: InitiateBolusResponse?) {
            val intent: Intent? = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                putExtra("action", "INITIATE_RESPONSE")
                putExtra("response", PumpMessageSerializer.toBytes(response))
            }
            applicationContext.startService(intent)
        }

        private fun onReceiveTimeSinceResetResponse(response: TimeSinceResetResponse?) {
            Timber.i("lastTimeSinceReset = $response")
            lastTimeSinceReset = response
        }

        private fun pumpConnectedPrecondition(): Boolean {
            if (!this::pump.isInitialized) {
                Timber.e("pumpConnectedPrecondition: pump not initialized")
                sendWearCommMessage("/from-pump/pump-not-connected", "not_initialized".toByteArray())
            } else if (!pump.isConnected) {
                Timber.e("pumpConnectedPrecondition: pump not connected")
                sendWearCommMessage("/from-pump/pump-not-connected", "not_connected".toByteArray())
            } else if (pump.lastPeripheral == null) {
                Timber.e("pumpConnectedPrecondition: pump not saved peripheral")
                sendWearCommMessage("/from-pump/pump-not-connected", "null_peripheral".toByteArray())
            } else {
                return true
            }
            return false
        }
        
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CommServiceCodes.INIT_PUMP_COMM.ordinal -> {
                    if (pumpConnectedPrecondition()) {
                        Timber.w("pumpCommHandler: init_pump_comm already run, ignoring")
                        return
                    }
                    Timber.i("pumpCommHandler: init_pump_comm")
                    pump = Pump()
                    tandemBTHandler = TandemBluetoothHandler.getInstance(applicationContext, pump, null)
                    while (true) {
                        try {
                            Timber.i("pumpCommHandler: Starting scan...")
                            tandemBTHandler.startScan()
                            break
                        } catch (e: SecurityException) {
                            Timber.e("pumpCommHandler: Waiting for BT permissions $e")
                            Thread.sleep(500)
                        }
                    }
                }
                CommServiceCodes.CHECK_PUMP_CONNECTED.ordinal -> {
                    if (pumpConnectedPrecondition()) {
                        sendWearCommMessage("/from-pump/pump-connected",
                            pump.lastPeripheral?.name!!.toByteArray()
                        )
                    }
                }
                CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal -> {
                    if (pumpConnectedPrecondition()) {
                        if (pump.lastPeripheral != null && pump.pairingCodeCentralChallenge != null) {
                            Timber.i("sendPumpPairingMessage: running performPairing")
                            pump.performPairing(
                                pump.lastPeripheral,
                                pump.pairingCodeCentralChallenge,
                                true
                            )
                        } else {
                            Timber.w("sendPumpPairingMessage: cannot send pump pairing message: lastPeripheral=${pump.lastPeripheral} pairingCodeCentralChallenge=${pump.pairingCodeCentralChallenge}")
                        }
                    }
                }
                CommServiceCodes.SEND_PUMP_COMMAND.ordinal -> {
                    Timber.i("pumpCommHandler send command raw: ${String(msg.obj as ByteArray)}")
                    val pumpMsg = PumpMessageSerializer.fromBytes(msg.obj as ByteArray)
                    if (isBolusCommand(pumpMsg)) {
                        Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                    } else if (pumpConnectedPrecondition()) {
                        Timber.i("pumpCommHandler send command: $pumpMsg")
                        pump.command(pumpMsg)
                    }
                }
                CommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("pumpCommHandler send commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (isBolusCommand(it)) {
                            Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                        } else if (pumpConnectedPrecondition()) {
                            Timber.i("pumpCommHandler send command: $it")
                            pump.command(it)
                        }
                    }
                }
                CommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal -> {
                    Timber.i("pumpCommHandler send commands bust cache raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            Timber.i("pumpCommHandler busted cache: $it")
                            lastResponseMessage.remove(Pair(it.characteristic, it.responseOpCode))
                        }
                        if (isBolusCommand(it)) {
                            Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                        } else if (pumpConnectedPrecondition()) {
                            Timber.i("pumpCommHandler send command bust cache: $it")
                            pump.command(it)
                        }
                    }
                }
                CommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("pumpCommHandler cached pump commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            val response = lastResponseMessage.get(Pair(it.characteristic, it.responseOpCode))
                            val ageSeconds = Duration.between(Instant.now(), response?.second).seconds
                            if (ageSeconds <= CacheSeconds) {
                                Timber.i("pumpCommHandler cached hit: $response")
                                sendWearCommMessage(
                                    "/from-pump/receive-cached-message",
                                    PumpMessageSerializer.toBytes(response?.first)
                                )
                            } else {
                                Timber.i("pumpCommHandler expired cache hit $ageSeconds sec: $response")
                                if (!isBolusCommand(it) && pumpConnectedPrecondition()) {
                                    pump.command(it)
                                }
                            }
                        } else if (isBolusCommand(it)) {
                            Timber.e("CACHED_PUMP_COMMANDS_BULK blocked bolus command")
                        } else if (pumpConnectedPrecondition()) {
                            Timber.i("pumpCommHandler cached miss: $it")
                            pump.command(it)
                        }
                    }
                }
                CommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal -> {
                    Timber.i("pumpCommHandler send bolus raw: ${String(msg.obj as ByteArray)}")
                    val secretKey = prefs(applicationContext)?.getString("initiateBolusSecret", "") ?: ""
                    val confirmedBolus =
                        InitiateConfirmedBolusSerializer.fromBytes(secretKey, msg.obj as ByteArray)

                    val messageOk = confirmedBolus.left
                    val pumpMsg = confirmedBolus.right
                    if (!messageOk) {
                        Timber.w("pumpCommHandler bolus invalid signature")
                        sendWearCommMessage("/to-wear/bolus-blocked-signature", "WearCommHandler".toByteArray())
                    } else if (!isBolusCommand(pumpMsg)) {
                        Timber.e("SEND_PUMP_COMMAND_BOLUS not a bolus command: $pumpMsg")
                    } else if (pumpConnectedPrecondition()) {
                        Timber.i("pumpCommHandler send bolus command with valid signature: $pumpMsg")
                        if (!Prefs(applicationContext).insulinDeliveryActions()) {
                            Timber.e("No insulin delivery messages enabled -- blocking bolus command $pumpMsg")
                            sendWearCommMessage("/to-wear/bolus-not-enabled", "from_self".toByteArray())
                            return
                        }
                        try {
                            val bolusReq = pumpMsg as InitiateBolusRequest
                            if (bolusReq.bolusCarbs > 0 && lastTimeSinceReset != null) {
                                pump.command(
                                    RemoteCarbEntryRequest(
                                        bolusReq.bolusCarbs,
                                        lastTimeSinceReset!!.currentTime,
                                        bolusReq.bolusID
                                    )
                                )
                            }

                            pump.command(pumpMsg)
                        } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                            Timber.e(e)
                            sendWearCommMessage("/to-wear/bolus-not-enabled", "from_pumpx2_lib".toByteArray())
                        }
                    }
                }
                CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal -> {
                    if (pumpConnectedPrecondition()) {
                        Timber.e("writeCharacteristicFailedCallback: calling onPumpConnected pump=$pump")
                        pump.onPumpConnected(pump.lastPeripheral)
                    }
                }
                CommServiceCodes.DEBUG_GET_MESSAGE_CACHE.ordinal -> {
                    Timber.i("pumpCommHandler debug get message cache: $lastResponseMessage")
                    sendWearCommMessage("/from-pump/debug-message-cache", PumpMessageSerializer.toDebugMessageCacheBytes(lastResponseMessage.values))
                }
            }
        }

        fun sendWearCommMessage(path: String, message: ByteArray) {
            this@CommService.sendWearCommMessage(path, message)
        }

        private fun isBolusCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
            return (message is InitiateBolusRequest) || message.opCode() == InitiateBolusRequest().opCode()
        }
    }

    fun sendWearCommMessage(path: String, message: ByteArray) {
        Timber.i("service sendMessage: $path ${String(message)}")
        fun inner(node: Node) {
            Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                .setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Timber.d("service message sent: $path ${String(message)} to: $node")
                    } else {
                        Timber.w("service sendMessage callback: ${result.status} for: $path ${String(message)}")
                    }
                }
        }
        if (!path.startsWith("/to-wear")) {
            Wearable.NodeApi.getLocalNode(mApiClient).setResultCallback { nodes ->
                Timber.d("service sendMessage to local node: ${nodes.node}")
                inner(nodes.node)
            }
        }
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.d("service sendMessage nodes: ${nodes.nodes}")
            nodes.nodes.forEach { node ->
                inner(node)
            }
        }
    }

    enum class BondState(val id: Int) {
        NOT_BONDED(10),
        BONDING(11),
        BONDED(12),
        ;
        companion object {
            private val map = BondState.values().associateBy(BondState::id)
            fun fromId(type: Int) = map[type]
        }
    }
    private var bleChangeReceiver = BleChangeReceiver()
    inner class BleChangeReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "android.bluetooth.device.action.BOND_STATE_CHANGED" -> {
                    val bondState = BondState.fromId(intent.getIntExtra(
                        "android.bluetooth.device.extra.BOND_STATE",
                        Int.MIN_VALUE
                    ))
                    Timber.i("BleChangeReceiver BOND_STATE_CHANGED: $bondState")
                }
                "android.bluetooth.adapter.action.STATE_CHANGED" -> {
                    when (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Int.MIN_VALUE)) {
                        10, 13 -> {
                            // Turned off
                            Timber.i("BleChangeReceiver STATE_CHANGED: off")
                        }
                        12 -> {
                            // Turned on
                            Timber.i("BleChangeReceiver STATE_CHANGED: on")
                        }
                    }
                }
            }
        }

    }

    override fun onCreate() {
        super.onCreate()

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread("PumpCommServiceThread", THREAD_PRIORITY_FOREGROUND).apply {
            start()
            Timber.d("service thread start")

            setupTimber("MWC", writeCharacteristicFailedCallback = { writeCharacteristicFailedCallback() })
            Timber.d("service onCreate")

            // Listen to BLE state changes
            val intentFilter = IntentFilter()
            intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
            registerReceiver(bleChangeReceiver, intentFilter)


            mApiClient = GoogleApiClient.Builder(applicationContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this@CommService)
                .addOnConnectionFailedListener(this@CommService)
                .build()

            mApiClient.connect()
            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            pumpCommHandler = PumpCommHandler(looper)


            Thread {
                while (!mApiClient.isConnected) {
                    Timber.d("waiting for mApiClient in CommService")
                    Thread.sleep(100)
                }

                sendWearCommMessage("/to-phone/comm-started", "".toByteArray())
            }.start()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("service messageReceived: ${messageEvent.path} ${String(messageEvent.data)}")
        when (messageEvent.path) {

            "/to-phone/force-reload" -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            "/to-phone/is-pump-connected" -> {
                sendCheckPumpConnected()
            }
            "/to-phone/pair" -> {
                sendPumpPairingMessage()
            }
            "/to-phone/bolus-request" -> {
                // removed: initialized check
                confirmBolusRequest(PumpMessageSerializer.fromBytes(messageEvent.data) as InitiateBolusRequest)
            }
            "/to-phone/bolus-cancel" -> {
                Timber.i("bolus state cancelled")
                resetBolusPrefs(this)
            }
            "/to-phone/initiate-confirmed-bolus" -> {
                // removed: initialized check
                val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, messageEvent.data)

                val messageOk = confirmedBolus.left
                val initiateMessage = confirmedBolus.right
                if (!messageOk) {
                    Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                    sendWearCommMessage("/to-wear/blocked-bolus-signature",
                        "CommService".toByteArray()
                    )
                    return
                }

                Timber.i("sending confirmed bolus request: $initiateMessage")
                sendPumpCommBolusMessage(messageEvent.data)
            }
            "/to-phone/write-characteristic-failed-callback" -> {
                Timber.i("writeCharacteristicFailedCallback from message")
                writeCharacteristicFailedCallback()
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

    private var started = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("CommService onStartCommand $intent $flags $startId")

        if (started) {
            Timber.w("CommService onStartCommand when already running, ignoring")
            return START_STICKY
        }

        started = true
        Toast.makeText(this, "WearX2 service starting", Toast.LENGTH_SHORT).show()

        updateNotification("Initializing...")

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_COMM.ordinal
            pumpCommHandler?.sendMessage(msg)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    fun updateNotification(statusText: String? = null) {
        if (statusText != null) {
            currentPumpData.statusText = statusText
        }
        var notification = createNotification()
        startForeground(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.w("CommService onTaskRemoved")
        triggerAppReload(applicationContext)
        Toast.makeText(this, "WearX2 service removed", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private data class DisplayablePumpData(
        var statusText: String = "Initializing...",
        var batteryPercent: Int? = null,
        var iobUnits: Double? = null,
        var cartridgeRemainingUnits: Int? = null,
    )

    private val currentPumpData: DisplayablePumpData = DisplayablePumpData()
    fun updateNotificationWithPumpData(message: com.jwoglom.pumpx2.pump.messages.Message) {
        var changed = false
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                changed = currentPumpData.batteryPercent != message.batteryPercent
                currentPumpData.batteryPercent = message.batteryPercent
            }
            is ControlIQIOBResponse -> {
                changed = currentPumpData.iobUnits != InsulinUnit.from1000To1(message.pumpDisplayedIOB)
                currentPumpData.iobUnits = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is InsulinStatusResponse -> {
                changed = currentPumpData.cartridgeRemainingUnits != message.currentInsulinAmount
                currentPumpData.cartridgeRemainingUnits = message.currentInsulinAmount
            }
        }

        if (changed) {
            updateNotification()
        }
    }
    
    private fun sendCheckPumpConnected() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CHECK_PUMP_CONNECTED.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendPumpPairingMessage() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessage(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMAND.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessages(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessagesBustCache(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun handleCachedCommandsRequest(rawBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal
            msg.obj = rawBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommBolusMessage(initiateConfirmedBolusBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal
            msg.obj = initiateConfirmedBolusBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun writeCharacteristicFailedCallback() {
        Timber.i("writeCharacteristicFailedCallback")
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal
            pumpCommHandler?.sendMessage(msg)
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

        val builder: Notification.Builder = Notification.Builder(
            this,
            notificationChannelId
        )

        var contentText = ""
        if (currentPumpData.batteryPercent != null) {
            contentText += "Battery: ${currentPumpData.batteryPercent}%\u00A0\u00A0\u00A0"
        }
        if (currentPumpData.iobUnits != null) {
            contentText += "IOB: ${currentPumpData.iobUnits}u\u00A0\u00A0\u00A0"
        }
        if (currentPumpData.cartridgeRemainingUnits != null) {
            contentText += "Cartridge: ${currentPumpData.cartridgeRemainingUnits}u"
        }

        return builder
            .setContentTitle("WearX2: ${currentPumpData.statusText}")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.pump_notif_1d))
            .setTicker(currentPumpData.statusText)
            .setPriority(Notification.PRIORITY_MAX) // for under android 26 compatibility
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