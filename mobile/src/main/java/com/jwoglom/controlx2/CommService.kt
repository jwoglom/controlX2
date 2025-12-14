package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.Service
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.media.RingtoneManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.util.AppVersionCheck
import com.jwoglom.controlx2.util.DataClientState
import com.jwoglom.controlx2.util.HistoryLogFetcher
import com.jwoglom.controlx2.util.HistoryLogSyncWorker
import com.jwoglom.controlx2.util.extractPumpSid
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractCentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractPumpChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionReleaseResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.shared.Hex
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.function.Supplier


const val CacheSeconds = 30

class CommService : Service() {
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var pumpCommHandler: PumpCommHandler? = null
    private var pumpFinderCommHandler: PumpFinderCommHandler? = null

    private lateinit var messageBus: MessageBus
    private var httpDebugApiService: HttpDebugApiService? = null

    private var serviceStatusAcknowledged = false
    private val serviceStatusTask = object : Runnable {
        override fun run() {
            if (!serviceStatusAcknowledged) {
                Timber.i("Periodically sending service status (not yet acknowledged)")
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage("/to-phone/pump-finder-started", "".toByteArray())
                } else {
                    sendWearCommMessage("/to-phone/comm-started", "".toByteArray())
                }
                serviceLooper?.let { looper ->
                    Handler(looper).postDelayed(this, 2000)
                }
            }
        }
    }

    private var lastResponseMessage: MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>> = Collections.synchronizedMap(mutableMapOf())
    private var historyLogCache: MutableMap<Long, HistoryLog> = Collections.synchronizedMap(mutableMapOf())
    private var lastTimeSinceReset: TimeSinceResetResponse? = null

    val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }
    private var historyLogFetcher: HistoryLogFetcher? = null
    private var historyLogSyncWorker: HistoryLogSyncWorker? = null

    // Handler that receives messages from the thread
    private inner class PumpCommHandler(looper: Looper) : Handler(looper) {
        private lateinit var pump: Pump
        private lateinit var tandemBTHandler: TandemBluetoothHandler

        private inner class Pump(var tandemConfig: TandemConfig) : TandemPump(applicationContext, tandemConfig) {
            private val scope = CoroutineScope(SupervisorJob(parent = supervisorJob) + Dispatchers.IO)
            var lastPeripheral: BluetoothPeripheral? = null
            var isConnected = false
            var pumpSid: Int? = null

            init {
                if (Prefs(applicationContext).connectionSharingEnabled()) {
                    enableTconnectAppConnectionSharing()
                    enableSendSharedConnectionResponseMessages()
                    // before adding relyOnConnectionSharingForAuthentication(), callback issues need to be resolved
                }
                if (Prefs(applicationContext).onlySnoopBluetoothEnabled()) {
                    Timber.i("ONLY SNOOP BLUETOOTH ENABLED")
                    onlySnoopBluetoothAndBlockAllPumpX2Functionality()
                }

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

                message?.let {
                    if (it is CurrentBatteryAbstractResponse ||
                        it is ControlIQIOBResponse ||
                        it is CurrentEGVGuiDataResponse
                    ) {
                        sendWearCommMessage("/to-wear/service-receive-message", PumpMessageSerializer.toBytes(message))
                    }
                }

                // Callbacks handled by this service itself
                when (message) {
                    is TimeSinceResetResponse -> onReceiveTimeSinceResetResponse(message)
                    is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
                    is CurrentBolusStatusResponse -> onReceiveCurrentBolusStatusResponse(message)
                    is BolusPermissionResponse -> {
                        PumpStateSupplier.inProgressBolusId = Supplier { message.bolusId }
                    }
                    is CurrentBatteryAbstractResponse -> DataClientState(context).pumpBattery = Pair("${message.batteryPercent}", Instant.now())
                    is ControlIQIOBResponse -> DataClientState(context).pumpIOB = Pair("${InsulinUnit.from1000To1(message.pumpDisplayedIOB)}", Instant.now())
                    is InsulinStatusResponse -> DataClientState(context).pumpCartridgeUnits = Pair("${message.currentInsulinAmount}", Instant.now())
                    is CurrentBasalStatusResponse -> DataClientState(context).pumpCurrentBasal = Pair("${InsulinUnit.from1000To1(message.currentBasalRate)}", Instant.now())
                    is CurrentEGVGuiDataResponse -> DataClientState(context).cgmReading = Pair("${message.cgmReading}", Instant.now())
                    is HistoryLogStatusResponse -> {
                        Timber.i("HistoryLogStatusResponse: $message")
                        scope.launch {
                            Timber.i("HistoryLogStatusResponse: launching in scope $message")
                            historyLogFetcher?.onStatusResponse(message, scope)
                        }
                    }
                    is HistoryLogStreamResponse -> {
                        message.historyLogs.forEach {
                            Timber.i("HISTORY-LOG-MESSAGE(${it.sequenceNum}): $it")
                            historyLogCache[it.sequenceNum] = it
                            scope.launch {
                                historyLogFetcher?.onStreamResponse(it)
                            }
                        }
                    }
                }
                message?.let { updateNotificationWithPumpData(it) }
                message?.let { httpDebugApiService?.onPumpMessageReceived(it) }
            }

            override fun onReceiveQualifyingEvent(
                peripheral: BluetoothPeripheral?,
                events: MutableSet<QualifyingEvent>?
            ) {
                Timber.i("onReceiveQualifyingEvent: $events")
                Toast.makeText(this@CommService, "Events: $events", Toast.LENGTH_SHORT).show()
                events?.forEach { event ->
                    event.suggestedHandlers.forEach { handler ->
                        Timber.i("onReceiveQualifyingEvent: running handler for $event message: ${handler.get()}")
                        handler.get()?.let { command(it) }
                    }
                }
                sendWearCommMessage("/from-pump/receive-qualifying-event", PumpQualifyingEventsSerializer.toBytes(events))
            }

            var pairingCodeCentralChallenge: AbstractCentralChallengeResponse? = null
            override fun onWaitingForPairingCode(
                peripheral: BluetoothPeripheral?,
                centralChallengeResponse: AbstractCentralChallengeResponse?
            ) {
                pairingCodeCentralChallenge = centralChallengeResponse
                performPairing(peripheral, centralChallengeResponse, false)
            }

            override fun onInvalidPairingCode(
                peripheral: BluetoothPeripheral?,
                resp: AbstractPumpChallengeResponse?
            ) {
                sendWearCommMessage("/from-pump/invalid-pairing-code", "".toByteArray())
                super.onInvalidPairingCode(peripheral, resp)
            }

            fun performPairing(
                peripheral: BluetoothPeripheral?,
                centralChallengeResponse: AbstractCentralChallengeResponse?,
                manuallyTriggered: Boolean
            ) {

                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                }
                Security.addProvider(BouncyCastleProvider())

                Timber.i("performPairing manuallyTriggered=$manuallyTriggered")
                var challengeBytes = byteArrayOf()
                if (centralChallengeResponse != null) {
                    challengeBytes = PumpMessageSerializer.toBytes(centralChallengeResponse)
                }
                PumpState.getPairingCode(context)?.let {
                    Timber.i("Pairing with saved code: $it centralChallenge: $centralChallengeResponse")
                    pair(peripheral, centralChallengeResponse, it)
                    sendWearCommMessage(
                        "/from-pump/entered-pairing-code",
                        challengeBytes)
                } ?: run {
                    Timber.i("Pairing without saved code: centralChallenge: $centralChallengeResponse")
                    sendWearCommMessage(
                        "/from-pump/missing-pairing-code",
                        challengeBytes)
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

                extractPumpSid(peripheral?.name ?: "")?.let {
                    pumpSid = it
                    Prefs(applicationContext).setCurrentPumpSid(it)
                }

                historyLogFetcher = HistoryLogFetcher(this@CommService, pump, peripheral!!, pumpSid!!)
                Timber.i("HistoryLogFetcher initialized")

                historyLogSyncWorker = HistoryLogSyncWorker(requestSync = { requestHistoryLogStatusUpdate() })
                if (Prefs(applicationContext).autoFetchHistoryLogs()) {
                    historyLogSyncWorker?.start()
                    historyLogSyncWorker?.triggerImmediateSync()
                }

                // Start Nightscout sync worker if enabled
                NightscoutSyncWorker.startIfEnabled(
                    applicationContext,
                    applicationContext.getSharedPreferences("controlx2", Context.MODE_PRIVATE),
                    pumpSid!!
                )

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
                currentPumpData.connectionTime = Instant.now()
                updateNotification("Connected to pump")
            }

            override fun onPumpModel(peripheral: BluetoothPeripheral?, model: KnownDeviceModel?) {
                super.onPumpModel(peripheral, model)
                Timber.i("service onPumpModel")
                sendWearCommMessage("/from-pump/pump-model",
                    model!!.name.toByteArray()
                )
            }

            override fun onPumpDisconnected(
                peripheral: BluetoothPeripheral?,
                status: HciStatus?
            ): Boolean {
                Timber.i("service onPumpDisconnected: isConnected=false")
                lastPeripheral = null
                lastResponseMessage.clear()
                historyLogFetcher = null
                historyLogSyncWorker?.stop()
                historyLogSyncWorker = null
                lastTimeSinceReset = null
                isConnected = false
                sendWearCommMessage("/from-pump/pump-disconnected",
                    peripheral?.name!!.toByteArray()
                )
                currentPumpData.connectionTime = Instant.now()
                updateNotification("Disconnected from pump")
                Toast.makeText(this@CommService, "Pump disconnected: $status", Toast.LENGTH_SHORT).show();
                return super.onPumpDisconnected(peripheral, status)
            }

            override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
                super.onPumpCriticalError(peripheral, reason)
                Timber.w("onPumpCriticalError $reason")
                Toast.makeText(this@CommService, "${reason?.name}: ${reason?.message}", Toast.LENGTH_LONG).show()
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
            applicationContext.sendBroadcast(intent)
        }

        private var lastBolusStatusId: Int? = null
        
        private fun onReceiveCurrentBolusStatusResponse(response: CurrentBolusStatusResponse?) {
            if (response != null) {
                // Broadcast status updates for active boluses (bolusId != 0)
                // Also broadcast when bolus completes (bolusId becomes 0 after being non-zero)
                val shouldBroadcast = response.bolusId != 0 || 
                    (response.bolusId == 0 && lastBolusStatusId != null && lastBolusStatusId != 0)
                
                if (shouldBroadcast) {
                    val intent: Intent? = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                        putExtra("action", "STATUS_UPDATE")
                        putExtra("status", PumpMessageSerializer.toBytes(response))
                    }
                    applicationContext.sendBroadcast(intent)
                }
                
                // Track the last bolusId to detect completion (transition from non-zero to zero)
                lastBolusStatusId = response.bolusId
                
                // Clear tracking when bolusId has been 0 for a while (to avoid stale broadcasts)
                if (response.bolusId == 0) {
                    // Clear after a delay to allow the completion notification to be sent
                    pumpCommHandler?.postDelayed({
                        if (lastBolusStatusId == 0) {
                            lastBolusStatusId = null
                        }
                    }, 2000)
                }
            }
        }

        private fun onReceiveTimeSinceResetResponse(response: TimeSinceResetResponse?) {
            Timber.i("lastTimeSinceReset = $response")
            lastTimeSinceReset = response
        }

        private fun pumpConnectedPrecondition(checkConnected: Boolean = true): Boolean {
            if (!this::pump.isInitialized) {
                Timber.e("pumpConnectedPrecondition: pump not initialized")
                sendWearCommMessage("/from-pump/pump-not-connected", "not_initialized".toByteArray())
            } else if (pump.lastPeripheral == null) {
                Timber.e("pumpConnectedPrecondition: pump not saved peripheral")
                sendWearCommMessage("/from-pump/pump-not-connected", "null_peripheral".toByteArray())
            } else if (checkConnected && !pump.isConnected) {
                Timber.e("pumpConnectedPrecondition: pump not connected")
                sendWearCommMessage("/from-pump/pump-not-connected", "not_connected".toByteArray())
            } else {
                return true
            }
            return false
        }
        
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CommServiceCodes.INIT_PUMP_COMM.ordinal -> {
                    if (pumpConnectedPrecondition(checkConnected = false)) {
                        Timber.w("pumpCommHandler: init_pump_comm already run, ignoring")
                        return
                    }
                    try {
                        var pairingCodeType: PairingCodeType? = null
                        var filterToBluetoothMac: String? = null
                        if (msg.obj != null && msg.obj != "") {
                            var parts = (msg.obj as String).split(" ")
                            pairingCodeType = PairingCodeType.fromLabel(parts[0])
                            if (parts.size == 2) {
                                filterToBluetoothMac = parts[1]
                            }
                        }

                        Timber.i("pumpCommHandler: init_pump_comm: msg.obj=${msg.obj as String} pairingCodeType=$pairingCodeType filterToBluetoothMac=$filterToBluetoothMac")
                        val cfg = TandemConfig()
                            .withFilterToBluetoothMac(filterToBluetoothMac)
                            .withPairingCodeType(pairingCodeType)

                        pump = Pump(cfg)
                        tandemBTHandler =
                            TandemBluetoothHandler.getInstance(applicationContext, pump, null)
                    } catch (e: SecurityException) {
                        Timber.e("pumpCommHandler: SecurityException starting pump $e")
                    }
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
                CommServiceCodes.STOP_PUMP_COMM.ordinal -> {
                    tandemBTHandler.stop()
                }
                CommServiceCodes.CHECK_PUMP_CONNECTED.ordinal -> {
                    if (pumpConnectedPrecondition()) {
                        sendWearCommMessage("/from-pump/pump-connected",
                            pump.lastPeripheral?.name!!.toByteArray()
                        )
                    }
                }
                CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal -> {
                    if (pumpConnectedPrecondition(checkConnected = false)) {
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
                            pump.command(pumpMsg as InitiateBolusRequest)
                        } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                            Timber.e(e)
                            sendWearCommMessage("/to-wear/bolus-not-enabled", "from_pumpx2_lib".toByteArray())
                        }
                    }
                }
                CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal -> {
                    val uuidStr = msg.obj as String
                    if (pumpConnectedPrecondition(checkConnected = false)) {
                        if (uuidStr == "${CharacteristicUUID.AUTHORIZATION_CHARACTERISTICS}") {
                            Timber.w("writeCharacteristicFailedCallback: calling onPumpConnected pump=$pump from authorization error")
                            pump.onPumpConnected(pump.lastPeripheral)
                        } else {
                            Timber.w("writeCharacteristicFailedCallback: triggering disconnection from non-authorization error")
                            pump.lastPeripheral?.cancelConnection();
                            pump.lastPeripheral?.let { pump.onPumpDisconnected(it, null) }
                        }
                    }
                }
                CommServiceCodes.DEBUG_GET_MESSAGE_CACHE.ordinal -> {
                    Timber.i("pumpCommHandler debug get message cache: $lastResponseMessage")
                    sendWearCommMessage("/from-pump/debug-message-cache", PumpMessageSerializer.toDebugMessageCacheBytes(lastResponseMessage.values))
                }
                CommServiceCodes.DEBUG_GET_HISTORYLOG_CACHE.ordinal -> {
                    Timber.i("pumpCommHandler debug get historylog cache: $historyLogCache")
                    if (historyLogCache.size <= 100) {
                        sendWearCommMessage(
                            "/from-pump/debug-historylog-cache",
                            PumpMessageSerializer.toDebugHistoryLogCacheBytes(historyLogCache)
                        )
                    } else {
                        historyLogCache.entries.toList().chunked(100).forEach {
                            sendWearCommMessage(
                                "/from-pump/debug-historylog-cache",
                                PumpMessageSerializer.toDebugHistoryLogCacheBytes(it.associate {
                                    Pair(it.key, it.value)
                                })
                            )
                        }
                    }
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

    private inner class PumpFinderCommHandler(looper: Looper) : Handler(looper) {

        private lateinit var pumpFinder: PumpFinder
        private var pumpFinderActive = false
        private var foundPumps = mutableListOf<String>()

        private inner class PumpFinder() : TandemPumpFinder(applicationContext) {
            init {
                Timber.i("PumpFinder init")
            }

            override fun toString(): String {
                return "PumpFinder(pumpFinderActive=${pumpFinderActive},foundPumps=${foundPumps.joinToString(";")})"
            }

            override fun onDiscoveredPump(peripheral: BluetoothPeripheral?, scanResult: ScanResult?) {
                val name = when {
                    peripheral?.name.isNullOrEmpty() -> "NO NAME"
                    else -> peripheral?.name
                }
                val key = "${name}=${peripheral?.address}"
                // onDiscoveredPump is typically called quite extensively, each time it sees
                // a BT packet, which can be quite spammy
                if (foundPumps.contains(key)) {
                    return
                }
                sendWearCommMessage(
                    "/from-pump/pump-finder-pump-discovered",
                    key.toByteArray()
                )
                foundPumps.add(key)
                sendWearCommMessage("/from-pump/pump-finder-found-pumps",
                    foundPumps.joinToString(";").toByteArray()
                )
            }

            override fun onBluetoothState(bluetoothEnabled: Boolean) {
                sendWearCommMessage(
                    "/from-pump/pump-finder-bluetooth-state",
                    "${bluetoothEnabled}".toByteArray()
                )
            }
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CommServiceCodes.INIT_PUMP_FINDER_COMM.ordinal -> {
                    Timber.i("pumpFinderCommHandler: init_pump_comm")
                    try {
                        pumpFinder = PumpFinder()
                    } catch (e: SecurityException) {
                        Timber.e("pumpFinderCommHandler: SecurityException starting pump $e")
                    }
                    while (true) {
                        try {
                            Timber.i("pumpFinderCommHandler: Starting scan...")
                            pumpFinder.startScan()
                            pumpFinderActive = true
                            break
                        } catch (e: SecurityException) {
                            Timber.e("pumpFinderCommHandler: Waiting for BT permissions $e")
                            Thread.sleep(500)
                        }
                    }
                }
                CommServiceCodes.STOP_PUMP_FINDER_COMM.ordinal -> {
                    pumpFinder.stop()
                    pumpFinderActive = false
                    foundPumps.clear()
                }
                CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal -> {
                    if (pumpFinderActive) {
                        sendWearCommMessage("/from-pump/pump-finder-found-pumps",
                            foundPumps.joinToString(";").toByteArray()
                        )
                    }
                }
            }
        }

        fun sendWearCommMessage(path: String, message: ByteArray) {
            this@CommService.sendWearCommMessage(path, message)
        }
    }

    fun sendWearCommMessage(path: String, message: ByteArray) {
        Timber.i("service sendMessage: $path ${String(message)}")
        messageBus.sendMessage(path, message, MessageBusSender.COMM_SERVICE)
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

    private val periodicUpdateIntervalMs: Long = 1000 * 60 * 5 // 5 minutes
    private var periodicUpdateTask: Runnable = Runnable {}
    init {
        periodicUpdateTask = Runnable {
            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)

            fun apiVersion(): ApiVersion {
                var apiVersion = PumpState.getPumpAPIVersion()
                if (apiVersion == null) {
                    apiVersion = KnownApiVersion.API_V2_5.get()
                }
                return apiVersion
            }

            Timber.i("running periodicUpdateTask")
            sendPumpCommMessages(PumpMessageSerializer.toBulkBytes(listOf(
                CurrentBatteryRequestBuilder.create(apiVersion()),
                ControlIQIOBRequest(),
                InsulinStatusRequest(),
                HistoryLogStatusRequest()
            )))
        }
    }

    private fun requestHistoryLogStatusUpdate() {
        if (!Prefs(applicationContext).autoFetchHistoryLogs()) {
            return
        }
        sendPumpCommMessages(
            PumpMessageSerializer.toBulkBytes(
                listOf(HistoryLogStatusRequest())
            )
        )
    }

    private val checkForUpdatesDelayMs: Long = 1000 * 30 // 30 seconds
    private var checkForUpdatesTask: Runnable = Runnable {
        if (Prefs(applicationContext).checkForUpdates()) {
            AppVersionCheck(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Timber already set in up MUA, but for good measure:
        setupTimber("MWC",
            context = this,
            logToFile = true,
            shouldLog = ShouldLogToFile(this),
            writeCharacteristicFailedCallback = handleWriteCharacteristicFailedCallback)
        Timber.i("service onCreate")

        // Listen to BLE state changes
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
        registerReceiver(bleChangeReceiver, intentFilter)


        if (!Prefs(applicationContext).tosAccepted()) {
            Timber.w("commService is short-circuiting because first TOS not accepted")
            return
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.w("commService is short-circuiting because service not enabled")
            return
        }

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        val handlerThread = HandlerThread("PumpCommServiceThread", Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.start()
        Timber.d("service thread start")

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = handlerThread.looper

        messageBus = MessageBusFactory.createMessageBus(this)
        messageBus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                handleMessageReceived(path, data, sourceNodeId)
            }
        })

        // Initialize and start HTTP Debug API service
        httpDebugApiService = HttpDebugApiService(applicationContext)
        httpDebugApiService?.getCurrentPumpDataCallback = { getCurrentPumpDataJson() }
        httpDebugApiService?.sendPumpMessagesCallback = { data -> sendPumpCommMessages(data) }
        httpDebugApiService?.sendMessagingCallback = { path, data -> sendWearCommMessage(path, data) }
        httpDebugApiService?.start()

        if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
            pumpFinderCommHandler = PumpFinderCommHandler(serviceLooper!!)
        } else {
            pumpCommHandler = PumpCommHandler(serviceLooper!!)

            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
            pumpCommHandler?.postDelayed(checkForUpdatesTask, checkForUpdatesDelayMs)
        }


        // Start periodic status sender (will stop when acknowledged)
        serviceLooper?.let { looper ->
            Handler(looper).postDelayed(serviceStatusTask, 500)
        }
    }

    override fun onBind(intent: Intent?) = null

    private fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        Timber.i("service messageReceived: $path ${String(data)} from $sourceNodeId")
        httpDebugApiService?.onMessagingReceived(path, data, sourceNodeId)
        when (path) {
            "/to-phone/force-reload" -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            "/to-phone/stop-pump-finder" -> {
                Timber.i("stop-pump-finder")
                sendStopPumpFinderComm()
                if (String(data) == "init_comm") {
                    pumpCommHandler = PumpCommHandler(serviceLooper!!)
                    val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
                    Prefs(applicationContext).setPumpFinderServiceEnabled(false)
                    Timber.i("stop-pump-finder-next: filterToMac=$filterToMac")
                    triggerAppReload(applicationContext)
                    //sendInitPumpComm()
                }
            }
            "/to-phone/restart-pump-finder" -> {
                Timber.i("restart-pump-finder")
                sendStopPumpFinderComm()
                pumpCommHandler = PumpCommHandler(serviceLooper!!)
                Prefs(applicationContext).setPumpFinderServiceEnabled(true)
                Timber.i("restart-pump-finder")
                triggerAppReload(applicationContext)
                //sendInitPumpComm()
            }
            "/to-phone/check-pump-finder-found-pumps" -> {
                Timber.i("check-pump-finder-found-pumps")
                sendCheckPumpFinderFoundPumps()
            }
            "/to-phone/request-service-status" -> {
                Timber.i("request-service-status received, responding with current status")
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage("/to-phone/pump-finder-started", "".toByteArray())
                } else {
                    sendWearCommMessage("/to-phone/comm-started", "".toByteArray())
                }
            }
            "/to-phone/service-status-acknowledged" -> {
                Timber.i("service-status acknowledged, stopping periodic sender")
                serviceStatusAcknowledged = true
            }
            "/to-phone/stop-comm" -> {
                Timber.w("stop-comm")
                sendStopPumpComm()
            }
            "/to-phone/is-pump-connected" -> {
                sendCheckPumpConnected()
            }
            "/to-pump/pair" -> {
                sendPumpPairingMessage()
            }
            "/to-phone/bolus-request-wear" -> {
                // removed: initialized check
                confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, BolusRequestSource.WEAR)
            }
            "/to-phone/bolus-request-phone" -> {
                // removed: initialized check
                confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, BolusRequestSource.PHONE)
            }
            "/to-phone/bolus-cancel" -> {
                Timber.i("bolus state cancelled")
                resetBolusPrefs(this)
            }
            "/to-phone/initiate-confirmed-bolus" -> {
                // removed: initialized check
                val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, data)

                val messageOk = confirmedBolus.left
                val initiateMessage = confirmedBolus.right
                if (!messageOk) {
                    Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                    sendWearCommMessage("/to-wear/blocked-bolus-signature",
                        "CommService".toByteArray()
                    )
                    NotificationCompat.Builder(this)
                        .setContentTitle("Bolus Blocked")
                        .setContentText("Bolus message was blocked due to an invalid signature.")
                        .build()
                    return
                }

                Timber.i("sending confirmed bolus request: $initiateMessage")
                sendPumpCommBolusMessage(data)
            }
            "/to-phone/write-characteristic-failed-callback" -> {
                Timber.i("writeCharacteristicFailedCallback from message")
                handleWriteCharacteristicFailedCallback(String(data))
            }
            "/to-pump/command" -> {
                sendPumpCommMessage(data)
            }
            "/to-pump/commands" -> {
                sendPumpCommMessages(data)
            }
            "/to-pump/commands-bust-cache" -> {
                sendPumpCommMessagesBustCache(data)
            }
            "/to-pump/cached-commands" -> {
                handleCachedCommandsRequest(data)
            }
            "/to-pump/debug-message-cache" -> {
                handleDebugGetMessageCache()
            }
            "/to-pump/debug-historylog-cache" -> {
                handleDebugGetHistoryLogCache()
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
        Toast.makeText(this, "ControlX2 service starting", Toast.LENGTH_SHORT).show()

        updateNotification("Initializing...")

        Timber.i("CommService onStartCommand has pumpFinderCommHandler=${pumpFinderCommHandler} pumpCommHandler=${pumpCommHandler}")

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
            Timber.i("Starting CommService in PumpFinder mode")
            sendInitPumpFinderComm()
        } else {
            val pairingCodeType = Prefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
            val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
            Timber.i("Starting CommService in standard mode: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")

            sendInitPumpComm(PairingCodeType.fromLabel(pairingCodeType), filterToMac)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    private fun startForegroundWrapped(id: Int, notification: Notification) {
        startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }


    fun updateNotification(statusText: String? = null) {
        if (statusText != null) {
            currentPumpData.statusText = statusText
        }
        var notification = createNotification()
        startForegroundWrapped(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.w("CommService onTaskRemoved")
        triggerAppReload(applicationContext)
        Toast.makeText(this, "ControlX2 service removed", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private data class DisplayablePumpData(
        var statusText: String = "Initializing...",
        var connectionTime: Instant? = null,
        var lastMessageTime: Instant? = null,
        var batteryPercent: Int? = null,
        var iobUnits: Double? = null,
        var cartridgeRemainingUnits: Int? = null,
    )

    private val currentPumpData: DisplayablePumpData = DisplayablePumpData()

    private fun getCurrentPumpDataJson(): String {
        return """{"statusText":"${currentPumpData.statusText}","connectionTime":"${currentPumpData.connectionTime}","lastMessageTime":"${currentPumpData.lastMessageTime}","batteryPercent":${currentPumpData.batteryPercent},"iobUnits":${currentPumpData.iobUnits},"cartridgeRemainingUnits":${currentPumpData.cartridgeRemainingUnits}}"""
    }

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
            currentPumpData.lastMessageTime = Instant.now()
            updateNotification()
        }
    }

    private fun sendInitPumpFinderComm() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_FINDER_COMM.ordinal
            pumpFinderCommHandler?.sendMessage(msg)
        }
    }

    private fun sendStopPumpFinderComm() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.STOP_PUMP_FINDER_COMM.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendCheckPumpFinderFoundPumps() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendInitPumpComm(pairingCodeType: PairingCodeType, filterToBluetoothMac: String) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_COMM.ordinal
            if (filterToBluetoothMac.length > 0) {
                msg.obj = "${pairingCodeType.label} $filterToBluetoothMac"
            } else {
                msg.obj = "${pairingCodeType.label}"
            }
            pumpCommHandler?.sendMessage(msg)
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

    private val handleWriteCharacteristicFailedCallback: (String) -> Unit = { uuid ->
        Timber.i("handleWriteCharacteristicFailedCallback($uuid)")
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal
            msg.obj = uuid
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun handleDebugGetMessageCache() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_GET_MESSAGE_CACHE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun handleDebugGetHistoryLogCache() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_GET_HISTORYLOG_CACHE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendStopPumpComm() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.STOP_PUMP_COMM.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }


    private var bolusNotificationId: Int = 1000

    enum class BolusRequestSource(val id: String) {
        PHONE("phone"),
        WEAR("wear")
    }
    private fun confirmBolusRequest(request: InitiateBolusRequest, source: BolusRequestSource) {
        val units = twoDecimalPlaces(InsulinUnit.from1000To1(request.totalVolume))
        Timber.i("confirmBolusRequest $units: $request")
        bolusNotificationId++
        prefs(this)?.edit()
            ?.putString("initiateBolusRequest", Hex.encodeHexString(PumpMessageSerializer.toBytes(request)))
            ?.putString("initiateBolusSecret", Hex.encodeHexString(Bytes.getSecureRandom10Bytes()))
            ?.putString("initiateBolusSource", source.id)
            ?.putLong("initiateBolusTime", Instant.now().toEpochMilli())
            ?.putInt("initiateBolusNotificationId", bolusNotificationId)
            ?.commit().let {
                if (it != true) {
                    Timber.e("synchronous preference write failed in confirmBolusRequest")
                }
            }

        fun getRejectIntent(): PendingIntent {
            val rejectIntent =
                Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "REJECT")
                }
            return PendingIntent.getBroadcast(
                this,
                2000,
                rejectIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
        }

        fun getConfirmIntent(): PendingIntent {
            val confirmIntent =
                Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "INITIATE")
                    putExtra("request", PumpMessageSerializer.toBytes(request))
                }
            return PendingIntent.getBroadcast(
                this,
                2001,
                confirmIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
        }

        val minNotifyThreshold = Prefs(this).bolusConfirmationInsulinThreshold()
        sendWearCommMessage("/to-wear/bolus-min-notify-threshold", "$minNotifyThreshold".toByteArray())

        if (InsulinUnit.from1000To1(request.totalVolume) >= minNotifyThreshold || minNotifyThreshold == 0.0) {
            Timber.i("Requesting permission for bolus because $units >= minNotifyThreshold=$minNotifyThreshold")

            val builder = confirmBolusRequestBaseNotification(
                this,
                "Bolus Request",
                "$units units. Press Confirm to deliver."
            )
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L))

            builder.addAction(R.drawable.decline, "Reject", getRejectIntent())

            builder.addAction(R.drawable.bolus_icon, "Confirm ${units}u", getConfirmIntent())

            val notif = builder.build()
            Timber.i("bolus notification $bolusNotificationId $builder $notif")
            makeNotif(bolusNotificationId, notif)
        } else {
            Timber.i("Sending immediate bolus request because $units is less than minNotifyThreshold=$minNotifyThreshold")

            val confirmIntent =
                Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "INITIATE")
                    putExtra("request", PumpMessageSerializer.toBytes(request))
                }
            val confirmPendingIntent = PendingIntent.getBroadcast(
                this,
                2001,
                confirmIntent,
                FLAG_IMMUTABLE or FLAG_ONE_SHOT
            )
            // wait to avoid prefs not being saved
            Thread.sleep(250)
            confirmPendingIntent.send()
        }
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
        historyLogSyncWorker?.stop()
        historyLogSyncWorker = null
        scope.cancel()
        messageBus.close()
        httpDebugApiService?.stop()
        Toast.makeText(this, "ControlX2 service destroyed", Toast.LENGTH_SHORT).show()
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

        var title = "${currentPumpData.statusText}"
        var atTime = currentPumpData.lastMessageTime
        currentPumpData.connectionTime?.let {
            if (atTime == null || it.isAfter(atTime)) atTime = it
        }
        atTime?.let {
            title += " at ${shortTime(it)}"
        }

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
        currentPumpData.connectionTime?.let {
            contentText += "    \nConnection established at: ${shortTime(it)}"
        }

        return builder
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSmallIcon(IconCompat.createWithResource(this, R.drawable.pump_notif_1d))
            .setTicker(currentPumpData.statusText)
            .setPriority(NotificationCompat.PRIORITY_MAX) // for under android 26 compatibility
            .setOngoing(true)
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
        .setPriority(NotificationCompat.PRIORITY_MAX) // for under android 26 compatibility
        .setAutoCancel(true)

}
