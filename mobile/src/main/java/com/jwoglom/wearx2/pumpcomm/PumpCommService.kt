package com.jwoglom.wearx2.pumpcomm

/*
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.response.authentication.CentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.wearx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import timber.log.Timber


class PumpCommService: Service() {
    private var clientCommBridge = ClientCommBridge()
    private inner class ClientCommBridge : GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
        private lateinit var mApiClient: GoogleApiClient

        fun initApiClient() {
            if (!this@ClientCommBridge::mApiClient.isInitialized) {
                mApiClient = GoogleApiClient.Builder(applicationContext)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this@ClientCommBridge)
                    .addOnConnectionFailedListener(this@ClientCommBridge)
                    .build()
            }

            mApiClient.connect()
        }

        fun sendMessage(path: String, message: ByteArray) {
            // to phone
            val localNode = Wearable.NodeApi.getLocalNode(mApiClient).await()
            Wearable.MessageApi.sendMessage(mApiClient, localNode.node.id, path, message)

            // to wear
            val connNodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await()
            connNodes.nodes.forEach {
                Wearable.MessageApi.sendMessage(mApiClient, it.id, path, message)
            }
        }

        override fun onConnected(p0: Bundle?) {
            Wearable.MessageApi.addListener(mApiClient) {}
        }

        override fun onConnectionSuspended(p0: Int) {
            mApiClient.reconnect()
        }

        override fun onConnectionFailed(p0: ConnectionResult) {
            mApiClient.reconnect()
        }

    }

    private lateinit var tandemBTHandler: TandemBluetoothHandler
    private lateinit var pump: Pump
    private inner class Pump(): TandemPump(applicationContext) {
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
            message?.let {
                // lastResponseMessage.put(Pair(it.characteristic, it.opCode()), it)
            }
            clientCommBridge.sendMessage("/from-pump/receive-message", PumpMessageSerializer.toBytes(message))

            // Callbacks handled by this service itself
            when (message) {
                // is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
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
            clientCommBridge.sendMessage("/from-pump/receive-qualifying-event", PumpQualifyingEventsSerializer.toBytes(events))
        }

        override fun onWaitingForPairingCode(
            peripheral: BluetoothPeripheral?,
            centralChallengeResponse: CentralChallengeResponse?
        ) {
            PumpState.getPairingCode(context)?.let {
                Timber.i("Pairing with saved code: $it centralChallenge: $centralChallengeResponse")
                pair(peripheral, centralChallengeResponse, it)
                clientCommBridge.sendMessage(
                    "/from-pump/entered-pairing-code",
                    PumpMessageSerializer.toBytes(centralChallengeResponse))
            } ?: run {
                clientCommBridge.sendMessage(
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
            lastPeripheral = peripheral
            var numResponses = -99999
            while (PumpState.processedResponseMessages != numResponses) {
                numResponses = PumpState.processedResponseMessages
                val wait = (250..500).random()
                Timber.i("service onPumpConnected -- waiting for ${wait}ms to avoid race conditions: (processedResponseMessages: ${PumpState.processedResponseMessages})")
                Thread.sleep(wait.toLong())
            }
            Timber.i("service onPumpConnected -- running super (processedResponseMessages: ${PumpState.processedResponseMessages})")
            super.onPumpConnected(peripheral)
            Thread.sleep(250)
            isConnected = true
            Timber.i("service onPumpConnected: $this")
            clientCommBridge.sendMessage("/from-pump/pump-connected",
                peripheral?.name!!.toByteArray()
            )
        }

        override fun onPumpModel(peripheral: BluetoothPeripheral?, modelNumber: String?) {
            super.onPumpModel(peripheral, modelNumber)
            Timber.i("service onPumpModel")
            clientCommBridge.sendMessage("/from-pump/pump-model",
                modelNumber!!.toByteArray()
            )
        }

        override fun onPumpDisconnected(
            peripheral: BluetoothPeripheral?,
            status: HciStatus?
        ): Boolean {
            Timber.i("service onPumpDisconnected: isConnected=false")
            lastPeripheral = null
            isConnected = false
            clientCommBridge.sendMessage("/from-pump/pump-disconnected",
                peripheral?.name!!.toByteArray()
            )
            return super.onPumpDisconnected(peripheral, status)
        }

        override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
            super.onPumpCriticalError(peripheral, reason)
            Timber.w("onPumpCriticalError $reason")
            clientCommBridge.sendMessage("/from-pump/pump-critical-error",
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

    private var lastResponseMessage: MutableMap<Pair<Characteristic, Byte>, com.jwoglom.pumpx2.pump.messages.Message> = mutableMapOf()
    private var handlerThreadCallback = HandlerThreadCallback()
    inner class HandlerThreadCallback: Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                MsgCode.INIT_PUMP_COMM.ordinal -> {
                    Timber.i("wearCommHandler: init pump class")
                    pump = Pump()
                    tandemBTHandler = TandemBluetoothHandler.getInstance(applicationContext, pump, null)
                    while (true) {
                        try {
                            Timber.i("wearCommHandler: Starting scan...")
                            tandemBTHandler.startScan()
                            break
                        } catch (e: SecurityException) {
                            Timber.e("wearCommHandler: Waiting for BT permissions $e")
                            Thread.sleep(500)
                        }
                    }
                }
                MsgCode.SEND_PUMP_COMMAND.ordinal -> {
                    Timber.i("wearCommHandler send command raw: ${String(msg.obj as ByteArray)}")
                    val pumpMsg = PumpMessageSerializer.fromBytes(msg.obj as ByteArray)
                    if (this@PumpCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(pumpMsg)) {
                        Timber.i("wearCommHandler send command: $pumpMsg")
                        pump.command(pumpMsg)
                    } else {
                        Timber.w("wearCommHandler not sending command due to pump state: $pump $pumpMsg")
                    }
                }
                MsgCode.SEND_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("wearCommHandler send commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (this@PumpCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler send command: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending command due to pump state: $pump $it")
                        }
                    }
                }
                MsgCode.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal -> {
                    Timber.i("wearCommHandler send commands bust cache raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler busted cache: $it")
                            lastResponseMessage.remove(Pair(it.characteristic, it.responseOpCode))
                        }
                        if (this@PumpCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler send command bust cache: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending command due to pump state: $pump $it")
                        }
                    }
                }
                MsgCode.CACHED_PUMP_COMMANDS_BULK.ordinal -> {
                    Timber.i("wearCommHandler cached pump commands raw: ${String(msg.obj as ByteArray)}")
                    PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                        if (lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                            val response = lastResponseMessage.get(Pair(it.characteristic, it.responseOpCode))
                            Timber.i("wearCommHandler cached hit: $response")
                            sendMessage("/from-pump/receive-cached-message", PumpMessageSerializer.toBytes(response))
                        } else if (this@PumpCommService::pump.isInitialized && pump.isConnected && !isBolusCommand(it)) {
                            Timber.i("wearCommHandler cached miss: $it")
                            pump.command(it)
                        } else {
                            Timber.w("wearCommHandler not sending cached send command due to pump state: $pump $it")
                        }
                    }
                }
                MsgCode.SEND_PUMP_COMMAND_BOLUS.ordinal -> {
                    Timber.i("wearCommHandler send bolus raw: ${String(msg.obj as ByteArray)}")
                    val secretKey = prefs(applicationContext)?.getString("initiateBolusSecret", "") ?: ""
                    val confirmedBolus =
                        InitiateConfirmedBolusSerializer.fromBytes(secretKey, msg.obj as ByteArray)

                    val messageOk = confirmedBolus.left
                    val pumpMsg = confirmedBolus.right
                    if (!messageOk) {
                        Timber.w("wearCommHandler bolus invalid signature")
                        sendMessage("/to-wear/bolus-blocked-signature", "WearCommHandler".toByteArray())
                    } else if (this@PumpCommService::pump.isInitialized && pump.isConnected && isBolusCommand(pumpMsg)) {
                        Timber.i("wearCommHandler send bolus command with valid signature: $pumpMsg")
                        if (!isInsulinDeliveryEnabled()) {
                            Timber.e("No insulin delivery messages enabled -- blocking bolus command $pumpMsg")
                            sendMessage("/to-wear/bolus-not-enabled", "from_self".toByteArray())
                            return true
                        }
                        try {
                            val bolusReq = pumpMsg as InitiateBolusRequest
                            if (bolusReq.bolusCarbs > 0) {
                                pump.command(RemoteCarbEntryRequest(
                                    bolusReq.bolusCarbs,
                                    0L,
                                    bolusReq.bolusID
                                ))
                            }
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
            return true;
        }

        private fun isBolusCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
            return (message is InitiateBolusRequest) || message.opCode() == InitiateBolusRequest().opCode()
        }

        private fun sendMessage(path: String, message: ByteArray) {
            clientCommBridge.sendMessage(path, message)
        }
    }

    private val handlerThread = HandlerThread("PCSHandlerThread")
    private var handler: Handler
    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper, handlerThreadCallback)
    }

    override fun onBind(intent: Intent?) = PumpCommBinder()
    inner class PumpCommBinder: Binder() {
        fun getPumpCommService() = this@PumpCommService
    }

    override fun onCreate() {
        super.onCreate()

        // Listen to BLE state changes
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
        registerReceiver(this.bleChangeReceiver, intentFilter)

        clientCommBridge.initApiClient()
    }

    private var bleChangeReceiver = BleChangeReceiver()
    inner class BleChangeReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            TODO("Not yet implemented")
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(this.bleChangeReceiver)
        sendToLooper(MsgCode.DISCONNECT)
    }

    private fun sendToLooper(code: MsgCode) {
        handler.sendEmptyMessage(code.ordinal)
    }



    private fun prefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences("WearX2", MODE_PRIVATE)
    }

    private fun isInsulinDeliveryEnabled(): Boolean {
        return prefs(applicationContext)?.getBoolean("insulinDeliveryEnabled", false) ?: false
    }
}
*/