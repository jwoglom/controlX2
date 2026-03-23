package com.jwoglom.controlx2

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
import com.jwoglom.controlx2.sync.xdrip.XdripMessageDispatcher
import com.jwoglom.controlx2.util.AppVersionCheck
import com.jwoglom.controlx2.util.DataClientState
import com.jwoglom.controlx2.util.HistoryLogFetcher
import com.jwoglom.controlx2.util.HistoryLogSyncWorker
import com.jwoglom.controlx2.util.extractPumpSid
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.bluetooth.ServiceUUID
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
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
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
import com.welie.blessed.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import timber.log.Timber
import java.security.Security
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID
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
    private var debugPromptResponseCounts: MutableMap<Pair<Characteristic, Byte>, Int> = Collections.synchronizedMap(mutableMapOf())
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
        var currentSession: PumpSession? = null
            private set

        fun getPumpSid(): Int? {
            return if (this::pump.isInitialized) pump.pumpSid else null
        }

        fun isPumpReadyForHistoryFetch(): Boolean {
            return currentSession?.isActive == true
        }

        private fun ensurePumpUnbondedForFreshInit(filterToBluetoothMac: String?): Boolean {
            val targetMac = (filterToBluetoothMac ?: Prefs(applicationContext).pumpFinderPumpMac().orEmpty())
                .trim()
                .uppercase()
            val prefs = Prefs(applicationContext)
            val scheduledUnbondMac = prefs.unbondOnNextCommInitMac().orEmpty().trim().uppercase()

            if (scheduledUnbondMac.isEmpty()) {
                Timber.i("init_pump_comm: skipping unbond (not scheduled for this init)")
                return true
            }

            if (targetMac.isEmpty()) {
                Timber.i("init_pump_comm: skipping unbond (no target MAC)")
                return true
            }

            if (scheduledUnbondMac != targetMac) {
                Timber.i("init_pump_comm: skipping unbond for $targetMac (scheduled for $scheduledUnbondMac)")
                return true
            }

            // Consume schedule now so this runs only once per re-pair handoff.
            prefs.setUnbondOnNextCommInitMac(null)

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                Timber.w("init_pump_comm: cannot unbond $targetMac because BluetoothAdapter is null")
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Timber.w("init_pump_comm: cannot unbond $targetMac without BLUETOOTH_CONNECT permission")
                return true
            }

            val bondedDevice = adapter.bondedDevices
                ?.firstOrNull { it.address.equals(targetMac, ignoreCase = true) }
            if (bondedDevice == null) {
                Timber.i("init_pump_comm: target $targetMac not currently bonded")
                return true
            }

            try {
                val removeBondMethod = bondedDevice.javaClass.getMethod("removeBond")
                val didRequestUnbond = removeBondMethod.invoke(bondedDevice) as? Boolean ?: false
                Timber.i("init_pump_comm: requested unbond for ${bondedDevice.name} ($targetMac), result=$didRequestUnbond")
            } catch (e: SecurityException) {
                Timber.w(e, "init_pump_comm: missing permission while requesting unbond for $targetMac")
            } catch (e: Exception) {
                Timber.w(e, "init_pump_comm: failed unbond request for $targetMac")
            }

            Thread.sleep(500)
            val stillBonded = adapter.bondedDevices
                ?.any { it.address.equals(targetMac, ignoreCase = true) } == true
            if (!stillBonded) {
                Timber.i("init_pump_comm: target $targetMac unbonded successfully")
                return true
            }

            val details = "${bondedDevice.name ?: "Unknown Tandem device"}=$targetMac"
            Timber.w("init_pump_comm: target is still bonded after unbond attempt: $details")
            sendWearCommMessage("/from-pump/pump-bonded-needs-manual-unbond", details.toByteArray())
            Toast.makeText(
                this@CommService,
                "Pump is still bonded in Android Bluetooth settings. Unpair it, then retry.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        private inner class Pump(var tandemConfig: TandemConfig) : TandemPump(applicationContext, tandemConfig) {
            private val scope = CoroutineScope(SupervisorJob(parent = supervisorJob) + Dispatchers.IO)
            private val xdripMessageDispatcher = XdripMessageDispatcher(this@CommService)
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
                message?.let {
                    lastResponseMessage.put(Pair(it.characteristic, it.opCode()), Pair(it, Instant.now()))
                    val source = if (consumeDebugPromptResponse(it.characteristic, it.opCode())) {
                        SendType.DEBUG_PROMPT
                    } else {
                        SendType.STANDARD
                    }
                    // Propagate to HttpDebugApiService first so API callbacks/streams are not
                    // blocked by downstream UI-side handling (e.g. debug popup flow).
                    httpDebugApiService?.onPumpMessageReceived(it, source = source)
                }
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

                message?.let { xdripMessageDispatcher.onReceiveMessage(it) }
                message?.let { updateNotificationWithPumpData(it) }
            }



            override fun onReceiveQualifyingEvent(
                peripheral: BluetoothPeripheral?,
                events: MutableSet<QualifyingEvent>?
            ) {
                Timber.i("onReceiveQualifyingEvent: $events")
                Toast.makeText(this@CommService, "Events: $events", Toast.LENGTH_SHORT).show()
                if (events != null && QualifyingEvent.PUMP_COMMUNICATIONS_SUSPENDED in events) {
                    Timber.w("onReceiveQualifyingEvent: PUMP_COMMUNICATIONS_SUSPENDED — pausing sends")
                    currentSession?.pauseSends(currentSession?.rateLimitConfig?.commSuspendedPauseMs ?: 5_000)
                }
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
                val hasSavedPairingCode = !PumpState.getPairingCode(context).isNullOrBlank()
                if (!hasSavedPairingCode) {
                    val challengeBytes = if (centralChallengeResponse != null) {
                        PumpMessageSerializer.toBytes(centralChallengeResponse)
                    } else {
                        byteArrayOf()
                    }
                    Timber.i("onWaitingForPairingCode: no saved pairing code, waiting for user input")
                    sendWearCommMessage("/from-pump/missing-pairing-code", challengeBytes)
                    return
                }
                Timber.i("onWaitingForPairingCode: saved pairing code found, auto-pairing")
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
                scanResult: ScanResult?,
                readyState: PumpReadyState
            ): Boolean {
                sendWearCommMessage(
                    "/from-pump/pump-discovered",
                    "${peripheral?.name.orEmpty()};;${readyState.name}".toByteArray()
                )
                return super.onPumpDiscovered(peripheral, scanResult, readyState)
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

                val session = PumpSession.open(this, peripheral!!)
                currentSession = session
                historyLogFetcher = HistoryLogFetcher(
                    historyLogRepo = this@CommService.historyLogRepo,
                    pumpSid = pumpSid!!,
                    pumpSession = session,
                    autoFetchEnabled = { Prefs(applicationContext).autoFetchHistoryLogs() },
                    broadcastCallback = { item -> this@CommService.broadcastHistoryLogItem(item) }
                )
                Timber.i("HistoryLogFetcher initialized")

                historyLogSyncWorker = HistoryLogSyncWorker(requestSync = { requestHistoryLogStatusUpdate() })
                refreshHistoryLogSyncWorker(triggerImmediateSync = true)
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
                // Sync glucose unit preference to wear app
                val glucoseUnit = Prefs(applicationContext).glucoseUnit()
                if (glucoseUnit != null) {
                    sendWearCommMessage("/to-wear/glucose-unit", glucoseUnit.name.toByteArray())
                }
                currentPumpData.connectionTime = Instant.now()
                updateNotification("Connected to pump")
            }

            override fun onPumpModel(peripheral: BluetoothPeripheral?, model: KnownDeviceModel?) {
                super.onPumpModel(peripheral, model)
                Timber.i("service onPumpModel: ${model?.name}")
                Prefs(applicationContext).setPumpModel(model!!.name)
                sendWearCommMessage("/from-pump/pump-model",
                    model.name.toByteArray()
                )
            }

            override fun onPumpDisconnected(
                peripheral: BluetoothPeripheral?,
                status: HciStatus?
            ): Boolean {
                Timber.i("service onPumpDisconnected: isConnected=false")
                currentSession?.close()
                currentSession = null
                lastPeripheral = null
                lastResponseMessage.clear()
                historyLogFetcher?.cancel()
                historyLogFetcher = null
                historyLogSyncWorker?.stop()
                historyLogSyncWorker = null
                lastTimeSinceReset = null
                debugPromptResponseCounts.clear()
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
                // Complete HttpDebugApiService request callbacks before UI notification side effects.
                val source = reason?.initiatingMessage?.let {
                    if (consumeDebugPromptResponse(it.characteristic, it.responseOpCode)) {
                        SendType.DEBUG_PROMPT
                    } else {
                        SendType.STANDARD
                    }
                } ?: SendType.STANDARD
                reason?.let { httpDebugApiService?.onPumpCriticalError(it, source = source) }
                Toast.makeText(this@CommService, "${reason?.name}: ${reason?.message}", Toast.LENGTH_LONG).show()
                sendWearCommMessage("/from-pump/pump-critical-error",
                    reason?.message!!.toByteArray()
                );
            }

            @Synchronized
            fun command(message: com.jwoglom.pumpx2.pump.messages.Message?) {
                if (message == null) {
                    Timber.w("Not sending null message")
                    return
                }

                if (lastPeripheral == null) {
                    Timber.w("Not sending message because no saved peripheral yet: $message")
                    return
                }

                if (!isConnected) {
                    Timber.w("Not sending message because no onConnected event yet: $message")
                    return
                }

                val session = currentSession
                if (session == null) {
                    Timber.w("Not sending message because no active session: $message")
                    return
                }

                Timber.i("Pump send command: $message")
                runBlocking { session.sendCommand(message) }
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

                        if (!ensurePumpUnbondedForFreshInit(filterToBluetoothMac)) {
                            return
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
                            val ageSeconds = Duration.between(response?.second, Instant.now()).seconds
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
                CommServiceCodes.DEBUG_WRITE_BT_CHARACTERISTIC.ordinal -> {
                    Timber.i("pumpCommHandler debug_write_bt_characteristic: ${String(msg.obj as ByteArray)}")
                    try {
                        val contents = JSONObject(String(msg.obj as ByteArray));
                        val uuidStr = contents.getString("characteristicUuid")
                        val uuid = UUID.fromString(uuidStr)

                        val valuesStr = contents.getJSONArray("valuesHex")
                        var valuesHex = ArrayList<ByteArray>()
                        for (i in 0..valuesStr.length()) {
                            valuesHex.add(Hex.decodeHex(valuesStr.getString(i)))
                        }

                        if (pumpConnectedPrecondition()) {
                            if (!Prefs(applicationContext).insulinDeliveryActions()) {
                                return
                            }
                            valuesHex.forEach {
                                Packetize.txId.increment()
                                pump.lastPeripheral?.writeCharacteristic(
                                    ServiceUUID.PUMP_SERVICE_UUID,
                                    uuid,
                                    it,
                                    WriteType.WITH_RESPONSE
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
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
            return (message is InitiateBolusRequest) || (message.opCode() == InitiateBolusRequest().opCode() && message.characteristic == Characteristic.CONTROL)
        }
    }

    private inner class PumpFinderCommHandler(looper: Looper) : Handler(looper) {

        private lateinit var pumpFinder: PumpFinder
        private var pumpFinderActive = false
        private var foundPumps = mutableListOf<String>()

        private inner class PumpFinder() : TandemPumpFinder(applicationContext, null) {
            init {
                Timber.i("PumpFinder init")
            }

            override fun toString(): String {
                return "PumpFinder(pumpFinderActive=${pumpFinderActive},foundPumps=${foundPumps.joinToString(";")})"
            }

            override fun onDiscoveredPump(
                peripheral: BluetoothPeripheral?,
                scanResult: ScanResult?,
                readyState: PumpReadyState
            ) {
                val name = when {
                    peripheral?.name.isNullOrEmpty() -> "NO NAME"
                    else -> peripheral?.name
                }
                val key = "${name}=${peripheral?.address}"
                sendWearCommMessage(
                    "/from-pump/pump-finder-pump-discovered",
                    "${key};;${readyState.name}".toByteArray()
                )
                // Keep emitting ready-state transitions, but only add each pump once to selection list.
                if (!foundPumps.contains(key)) {
                    foundPumps.add(key)
                    sendWearCommMessage("/from-pump/pump-finder-found-pumps",
                        foundPumps.joinToString(";").toByteArray()
                    )
                }
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

    fun isPumpReadyForHistoryFetch(): Boolean {
        return pumpCommHandler?.isPumpReadyForHistoryFetch() == true
    }

    fun getPumpSession(): PumpSession? {
        return pumpCommHandler?.currentSession
    }

    private fun refreshHistoryLogSyncWorker(triggerImmediateSync: Boolean = false) {
        val worker = historyLogSyncWorker
        if (worker == null) {
            Timber.i("refreshHistoryLogSyncWorker skipped: worker not initialized")
            return
        }

        if (!Prefs(applicationContext).autoFetchHistoryLogs()) {
            Timber.i("refreshHistoryLogSyncWorker stopping worker")
            worker.stop()
            return
        }

        Timber.i("refreshHistoryLogSyncWorker starting worker")
        worker.start()
        if (triggerImmediateSync) {
            worker.triggerImmediateSync()
        }
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
        httpDebugApiService?.getCurrentPumpSidCallback = { pumpCommHandler?.getPumpSid() ?: Prefs(applicationContext).currentPumpSid() }
        httpDebugApiService?.sendPumpMessagesCallback = { data -> sendPumpCommMessages(data) }
        httpDebugApiService?.sendMessagingCallback = { path, data -> sendWearCommMessage(path, data) }
        httpDebugApiService?.onHistoryLogInsertedCallback = { item -> httpDebugApiService?.onHistoryLogInserted(item) }
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
        // Ignore noisy loopback logs for pump-originated broadcasts.
        if (!path.startsWith("/from-pump/")) {
            Timber.d("service messageReceived: $path ${String(data)} from $sourceNodeId")
        }
        httpDebugApiService?.onMessagingReceived(path, data, sourceNodeId)
        when (path) {
            "/to-phone/force-reload" -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            "/to-phone/set-pairing-code" -> {
                Timber.i("set-pairing-code received in service")
            }
            "/to-phone/stop-pump-finder" -> {
                Timber.i("stop-pump-finder")
                sendStopPumpFinderComm()
                if (String(data) == "init_comm") {
                    pumpFinderCommHandler = null
                    pumpCommHandler = PumpCommHandler(serviceLooper!!)
                    val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
                    val pairingCodeType = Prefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
                    val pairingCodeTypeEnum = if (pairingCodeType.isNotEmpty())
                        PairingCodeType.fromLabel(pairingCodeType)
                    else
                        PairingCodeType.SHORT_6CHAR
                    Prefs(applicationContext).setPumpFinderServiceEnabled(false)
                    Prefs(applicationContext).setUnbondOnNextCommInitMac(filterToMac)
                    Timber.i("stop-pump-finder-next: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")

                    pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
                    pumpCommHandler?.postDelayed(checkForUpdatesTask, checkForUpdatesDelayMs)
                    sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
                    sendWearCommMessage("/to-phone/comm-started", "".toByteArray())
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
            "/to-phone/refresh-history-log-sync" -> {
                Timber.i("refresh-history-log-sync received")
                refreshHistoryLogSyncWorker(triggerImmediateSync = true)
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
            "/to-pump/debug-commands" -> {
                sendPumpCommDebugMessages(data)
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
            "/to-pump/debug-write-bt-characteristic" -> {
                sendDebugWriteBtCharacteristic(data)
            }
        }
    }

    fun broadcastHistoryLogItem(item: HistoryLogItem) {
        httpDebugApiService?.onHistoryLogInsertedCallback?.invoke(item)
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
            val pairingCodeTypeEnum = if (!pairingCodeType.isEmpty())
                PairingCodeType.fromLabel(pairingCodeType)
            else
                PairingCodeType.SHORT_6CHAR
            val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
            Timber.i("Starting CommService in standard mode: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")

            sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    private fun startForegroundWrapped(id: Int, notification: Notification): Boolean {
        return try {
            startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Unable to start foreground service: missing connectedDevice permissions")
            Toast.makeText(
                this,
                "ControlX2 needs Bluetooth permissions before starting the background service",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            false
        }
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
            pumpFinderCommHandler?.sendMessage(msg)
        }
    }

    private fun sendCheckPumpFinderFoundPumps() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal
            pumpFinderCommHandler?.sendMessage(msg)
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
    private fun sendPumpCommDebugMessages(pumpMsgBytes: ByteArray) {
        try {
            val messages = PumpMessageSerializer.fromBulkBytes(pumpMsgBytes)
            synchronized(debugPromptResponseCounts) {
                messages.forEach {
                    val key = Pair(it.characteristic, it.responseOpCode)
                    debugPromptResponseCounts[key] = (debugPromptResponseCounts[key] ?: 0) + 1
                }
            }
            Timber.i("Registered ${messages.size} debug-prompt request(s) for stream tagging")
        } catch (e: Exception) {
            Timber.w(e, "Failed to register debug-prompt requests")
        }
        sendPumpCommMessages(pumpMsgBytes)
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

    private fun consumeDebugPromptResponse(characteristic: Characteristic, opCode: Byte): Boolean {
        val key = Pair(characteristic, opCode)
        synchronized(debugPromptResponseCounts) {
            val current = debugPromptResponseCounts[key] ?: return false
            if (current <= 1) {
                debugPromptResponseCounts.remove(key)
            } else {
                debugPromptResponseCounts[key] = current - 1
            }
            return true
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

    private fun sendDebugWriteBtCharacteristic(jsonBlobBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_WRITE_BT_CHARACTERISTIC.ordinal
            msg.obj = jsonBlobBytes
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
        val autoApproveTimeout = if (source == BolusRequestSource.WEAR)
            Prefs(this).wearBolusAutoApproveTimeoutSeconds() else 0
        sendWearCommMessage("/to-wear/bolus-min-notify-threshold", "$minNotifyThreshold".toByteArray())
        sendWearCommMessage("/to-wear/wear-auto-approve-timeout", "$autoApproveTimeout".toByteArray())

        if (InsulinUnit.from1000To1(request.totalVolume) >= minNotifyThreshold || minNotifyThreshold == 0.0) {
            Timber.i("Requesting permission for bolus because $units >= minNotifyThreshold=$minNotifyThreshold")

            val builder = confirmBolusRequestBaseNotification(
                this,
                "Bolus Request",
                if (autoApproveTimeout > 0) "$units units. Auto-approving in ${autoApproveTimeout}s unless canceled."
                else "$units units. Press Confirm to deliver."
            )
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L, 500L))

            builder.addAction(R.drawable.decline, "Reject", getRejectIntent())

            builder.addAction(R.drawable.bolus_icon, "Confirm ${units}u", getConfirmIntent())

            val notif = builder.build()
            Timber.i("bolus notification $bolusNotificationId $builder $notif")
            makeNotif(bolusNotificationId, notif)

            // Broadcast to phone UI for in-app dialog
            sendWearCommMessage("/to-phone/bolus-confirm-dialog",
                "${Hex.encodeHexString(PumpMessageSerializer.toBytes(request))}|${source.id}|$autoApproveTimeout".toByteArray())

            // Schedule auto-approve if timeout is set
            if (autoApproveTimeout > 0) {
                scheduleAutoApprove(autoApproveTimeout, getConfirmIntent())
            }
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

    private fun scheduleAutoApprove(timeoutSeconds: Int, confirmIntent: PendingIntent) {
        cancelAutoApproveStatic(this)
        val handler = Handler(Looper.getMainLooper())
        _autoApproveHandler = handler
        val runnable = Runnable {
            Timber.i("Auto-approving bolus after ${timeoutSeconds}s timeout")
            try {
                confirmIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Timber.w("Auto-approve PendingIntent already cancelled (bolus was manually handled)")
            }
            _autoApproveRunnable = null
            _autoApproveHandler = null
        }
        _autoApproveRunnable = runnable
        handler.postDelayed(runnable, timeoutSeconds * 1000L)
    }

    companion object {
        @Volatile
        private var _autoApproveHandler: Handler? = null
        @Volatile
        private var _autoApproveRunnable: Runnable? = null

        fun cancelAutoApproveStatic(context: Context?) {
            _autoApproveRunnable?.let { runnable ->
                _autoApproveHandler?.removeCallbacks(runnable)
                Timber.i("Auto-approve timer cancelled")
            }
            _autoApproveRunnable = null
            _autoApproveHandler = null
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
            .setFullScreenIntent(pendingIntent, false)
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
