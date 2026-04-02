package com.jwoglom.controlx2.pump

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.extractPumpSid
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.bluetooth.ServiceUUID
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
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
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.shared.Hex
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import com.welie.blessed.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import timber.log.Timber
import java.security.Security
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.function.Supplier

const val CacheSeconds = 30

/**
 * Handler for pump communication. Manages the TandemPump connection lifecycle,
 * message routing, response caching, and bolus command handling.
 *
 * Extracted from CommService's inner class to enable independent testing.
 */
class PumpCommHandler(
    looper: Looper,
    private val callbacks: CommServiceCallbacks
) : Handler(looper) {
    private lateinit var pump: Pump
    private lateinit var tandemBTHandler: TandemBluetoothHandler
    var currentSession: PumpSession? = null
        internal set

    private var historyLogFetcher: PumpHistoryLogFetcher? = null
    private var historyLogSyncWorker: PumpHistoryLogSyncWorker? = null

    fun getPumpSid(): Int? {
        return if (this::pump.isInitialized) pump.pumpSid else null
    }

    fun isPumpReadyForHistoryFetch(): Boolean {
        return currentSession?.isActive == true
    }

    @androidx.annotation.VisibleForTesting
    fun simulateConnectedPump(peripheral: BluetoothPeripheral) {
        pump.initializeConnection(peripheral)
    }

    fun stopHistoryLogSyncWorker() {
        historyLogSyncWorker?.stop()
        historyLogSyncWorker = null
    }

    fun refreshHistoryLogSyncWorker(triggerImmediateSync: Boolean = false) {
        val worker = historyLogSyncWorker
        if (worker == null) {
            Timber.i("refreshHistoryLogSyncWorker skipped: worker not initialized")
            return
        }

        if (!callbacks.prefAutoFetchHistoryLogs()) {
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

    private fun requestHistoryLogStatusUpdate() {
        if (!callbacks.prefAutoFetchHistoryLogs()) {
            return
        }
        callbacks.sendPumpCommMessages(
            PumpMessageSerializer.toBulkBytes(
                listOf(com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest())
            )
        )
    }

    private fun ensurePumpUnbondedForFreshInit(filterToBluetoothMac: String?): Boolean {
        val ctx = callbacks.getApplicationContext()
        val targetMac = (filterToBluetoothMac ?: callbacks.prefPumpFinderPumpMac().orEmpty())
            .trim()
            .uppercase()
        val scheduledUnbondMac = callbacks.prefUnbondOnNextCommInitMac().orEmpty().trim().uppercase()

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

        callbacks.prefSetUnbondOnNextCommInitMac(null)

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Timber.w("init_pump_comm: cannot unbond $targetMac because BluetoothAdapter is null")
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
        callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_BONDED_NEEDS_MANUAL_UNBOND, details.toByteArray())
        callbacks.showToast(
            "Pump is still bonded in Android Bluetooth settings. Unpair it, then retry.",
            Toast.LENGTH_LONG
        )
        return false
    }

    private inner class Pump(var tandemConfig: TandemConfig) : TandemPump(callbacks.getApplicationContext(), tandemConfig) {
        private val scope = CoroutineScope(SupervisorJob(parent = callbacks.supervisorJob) + Dispatchers.IO)
        var lastPeripheral: BluetoothPeripheral? = null
        var isConnected = false
        var pumpSid: Int? = null

        init {
            if (callbacks.prefConnectionSharingEnabled()) {
                enableTconnectAppConnectionSharing()
                enableSendSharedConnectionResponseMessages()
            }
            if (callbacks.prefOnlySnoopBluetoothEnabled()) {
                Timber.i("ONLY SNOOP BLUETOOTH ENABLED")
                onlySnoopBluetoothAndBlockAllPumpX2Functionality()
            }

            if (callbacks.prefInsulinDeliveryActions()) {
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
                callbacks.pumpCommState.lastResponseMessage.put(Pair(it.characteristic, it.opCode()), Pair(it, Instant.now()))
                val source = if (callbacks.pumpCommState.consumeDebugPromptResponse(it.characteristic, it.opCode())) {
                    com.jwoglom.controlx2.shared.util.SendType.DEBUG_PROMPT
                } else {
                    com.jwoglom.controlx2.shared.util.SendType.STANDARD
                }
                callbacks.onPumpMessageReceived(it, source = source)
            }
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_RECEIVE_MESSAGE, PumpMessageSerializer.toBytes(message))

            message?.let {
                if (it is CurrentBatteryAbstractResponse ||
                    it is ControlIQIOBResponse ||
                    it is CurrentEGVGuiDataResponse
                ) {
                    callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE, PumpMessageSerializer.toBytes(message))
                }
            }

            when (message) {
                is TimeSinceResetResponse -> onReceiveTimeSinceResetResponse(message)
                is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
                is CurrentBolusStatusResponse -> onReceiveCurrentBolusStatusResponse(message)
                is BolusPermissionResponse -> {
                    PumpStateSupplier.inProgressBolusId = Supplier { message.bolusId }
                }
                is CurrentBatteryAbstractResponse -> callbacks.updateComplicationData("pumpBattery", "${message.batteryPercent}", Instant.now())
                is ControlIQIOBResponse -> callbacks.updateComplicationData("pumpIOB", "${InsulinUnit.from1000To1(message.pumpDisplayedIOB)}", Instant.now())
                is InsulinStatusResponse -> callbacks.updateComplicationData("pumpCartridgeUnits", "${message.currentInsulinAmount}", Instant.now())
                is CurrentBasalStatusResponse -> callbacks.updateComplicationData("pumpCurrentBasal", "${InsulinUnit.from1000To1(message.currentBasalRate)}", Instant.now())
                is CurrentEGVGuiDataResponse -> callbacks.updateComplicationData("cgmReading", "${message.cgmReading}", Instant.now())
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
                        callbacks.pumpCommState.historyLogCache[it.sequenceNum] = it
                        scope.launch {
                            historyLogFetcher?.onStreamResponse(it)
                        }
                    }
                }
            }

            message?.let { callbacks.dispatchExternalMessage(it) }
            message?.let { callbacks.updateNotificationWithPumpData(it) }
        }

        override fun onReceiveQualifyingEvent(
            peripheral: BluetoothPeripheral?,
            events: MutableSet<QualifyingEvent>?
        ) {
            Timber.i("onReceiveQualifyingEvent: $events")
            if (callbacks.prefQualifyingEventToastsEnabled()) {
                callbacks.showToast("Events: $events", Toast.LENGTH_SHORT)
            }
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
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_RECEIVE_QUALIFYING_EVENT, com.jwoglom.controlx2.shared.PumpQualifyingEventsSerializer.toBytes(events))
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
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_MISSING_PAIRING_CODE, challengeBytes)
                return
            }
            Timber.i("onWaitingForPairingCode: saved pairing code found, auto-pairing")
            performPairing(peripheral, centralChallengeResponse, false)
        }

        override fun onInvalidPairingCode(
            peripheral: BluetoothPeripheral?,
            resp: AbstractPumpChallengeResponse?
        ) {
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_INVALID_PAIRING_CODE, "".toByteArray())
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
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_ENTERED_PAIRING_CODE, challengeBytes)
            } ?: run {
                Timber.i("Pairing without saved code: centralChallenge: $centralChallengeResponse")
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_MISSING_PAIRING_CODE, challengeBytes)
            }
        }

        override fun onPumpDiscovered(
            peripheral: BluetoothPeripheral?,
            scanResult: ScanResult?,
            readyState: PumpReadyState
        ): Boolean {
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_DISCOVERED,
                "${peripheral?.name.orEmpty()};;${readyState.name}".toByteArray()
            )
            return super.onPumpDiscovered(peripheral, scanResult, readyState)
        }

        override fun onInitialPumpConnection(peripheral: BluetoothPeripheral?) {
            lastPeripheral = peripheral
            val wait = (500..1000).random()
            Timber.i("Waiting to pair onInitialPumpConnection to avoid race condition with tconnect app for ${wait}ms")
            Thread.sleep(wait.toLong())

            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_INITIAL_PUMP_CONNECTION, "${peripheral?.name}".toByteArray())

            if (Packetize.txId.get() > 0) {
                Timber.w("Not pairing in onInitialPumpConnection because it looks like the tconnect app has already paired with txId=${Packetize.txId.get()}")
                return
            }
            super.onInitialPumpConnection(peripheral)
        }

        @androidx.annotation.VisibleForTesting
        fun initializeConnection(peripheral: BluetoothPeripheral) {
            lastPeripheral = peripheral

            extractPumpSid(peripheral.name ?: "")?.let {
                pumpSid = it
                callbacks.prefSetCurrentPumpSid(it)
            }

            val session = PumpSession.open(this, peripheral)
            this@PumpCommHandler.currentSession = session

            isConnected = true
            Timber.i("service initializeConnection: $this")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED,
                peripheral.name!!.toByteArray()
            )
            val glucoseUnit = callbacks.prefGlucoseUnit()
            if (glucoseUnit != null) {
                callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_GLUCOSE_UNIT, glucoseUnit.name.toByteArray())
            }
            callbacks.markConnectionTime()
        }

        private fun initializeHistoryAndSync() {
            historyLogFetcher = callbacks.createHistoryLogFetcher(
                pumpSid = pumpSid!!,
                pumpSession = this@PumpCommHandler.currentSession!!,
                autoFetchEnabled = { callbacks.prefAutoFetchHistoryLogs() },
            )
            Timber.i("HistoryLogFetcher initialized")

            historyLogSyncWorker = callbacks.createHistoryLogSyncWorker(requestSync = { requestHistoryLogStatusUpdate() })
            refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            callbacks.onPumpConnectedSync(pumpSid!!)
        }

        private fun waitForResponseStabilization() {
            var numResponses = -99999
            while (PumpState.processedResponseMessages != numResponses) {
                numResponses = PumpState.processedResponseMessages
                val wait = (250..500).random()
                Timber.i("service onPumpConnected -- waiting for ${wait}ms to avoid race conditions: (processedResponseMessages: ${PumpState.processedResponseMessages})")
                Thread.sleep(wait.toLong())
            }
        }

        private fun sendBaseCommandsIfNeeded(peripheral: BluetoothPeripheral) {
            Timber.i("service onPumpConnected -- checking for base messages (processedResponseMessages: ${PumpState.processedResponseMessages})")
            if (!callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, ApiVersionRequest().opCode()))) {
                Timber.i("service onPumpConnected -- sending ApiVersionRequest")
                this.sendCommand(peripheral, ApiVersionRequest())
            }

            if (!callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, TimeSinceResetResponse().opCode()))) {
                Timber.i("service onPumpConnected -- sending TimeSinceResetResponse")
                this.sendCommand(peripheral, TimeSinceResetRequest())
            }
            Thread.sleep(250)
        }

        override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
            initializeConnection(peripheral!!)
            initializeHistoryAndSync()
            waitForResponseStabilization()
            sendBaseCommandsIfNeeded(peripheral)
            callbacks.updateNotification("Connected to pump")
        }

        override fun onPumpModel(peripheral: BluetoothPeripheral?, model: KnownDeviceModel?) {
            super.onPumpModel(peripheral, model)
            Timber.i("service onPumpModel")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_MODEL,
                model!!.name.toByteArray()
            )

            val modelName = when (model) {
                KnownDeviceModel.TSLIM_X2 -> "t:slim X2"
                KnownDeviceModel.MOBI -> "Tandem Mobi"
                else -> "Tandem Pump"
            }
            callbacks.prefSetPumpModelName(modelName)
        }

        override fun onPumpDisconnected(
            peripheral: BluetoothPeripheral?,
            status: HciStatus?
        ): Boolean {
            Timber.i("service onPumpDisconnected: isConnected=false")
            currentSession?.close()
            currentSession = null
            lastPeripheral = null
            callbacks.pumpCommState.lastResponseMessage.clear()
            historyLogFetcher?.cancel()
            historyLogFetcher = null
            historyLogSyncWorker?.stop()
            historyLogSyncWorker = null
            callbacks.pumpCommState.lastTimeSinceReset = null
            callbacks.pumpCommState.debugPromptResponseCounts.clear()
            isConnected = false
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_DISCONNECTED,
                peripheral?.name!!.toByteArray()
            )
            callbacks.markConnectionTime()
            callbacks.updateNotification("Disconnected from pump")
            callbacks.showToast("Pump disconnected: $status", Toast.LENGTH_SHORT)
            return super.onPumpDisconnected(peripheral, status)
        }

        override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
            super.onPumpCriticalError(peripheral, reason)
            Timber.w("onPumpCriticalError $reason")
            val source = reason?.initiatingMessage?.let {
                if (callbacks.pumpCommState.consumeDebugPromptResponse(it.characteristic, it.responseOpCode)) {
                    com.jwoglom.controlx2.shared.util.SendType.DEBUG_PROMPT
                } else {
                    com.jwoglom.controlx2.shared.util.SendType.STANDARD
                }
            } ?: com.jwoglom.controlx2.shared.util.SendType.STANDARD
            reason?.let { callbacks.onPumpCriticalError(it, source = source) }
            callbacks.showToast("${reason?.name}: ${reason?.message}", Toast.LENGTH_LONG)
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CRITICAL_ERROR,
                reason?.message!!.toByteArray()
            )
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
        callbacks.broadcastBolusInitiateResponse(PumpMessageSerializer.toBytes(response))
    }

    private var lastBolusStatusId: Int? = null

    private fun onReceiveCurrentBolusStatusResponse(response: CurrentBolusStatusResponse?) {
        if (response != null) {
            val shouldBroadcast = response.bolusId != 0 ||
                (response.bolusId == 0 && lastBolusStatusId != null && lastBolusStatusId != 0)

            if (shouldBroadcast) {
                callbacks.broadcastBolusStatusUpdate(PumpMessageSerializer.toBytes(response))
            }

            lastBolusStatusId = response.bolusId

            if (response.bolusId == 0) {
                this.postDelayed({
                    if (lastBolusStatusId == 0) {
                        lastBolusStatusId = null
                    }
                }, 2000)
            }
        }
    }

    private fun onReceiveTimeSinceResetResponse(response: TimeSinceResetResponse?) {
        Timber.i("pumpCommState.lastTimeSinceReset = $response")
        callbacks.pumpCommState.lastTimeSinceReset = response
    }

    private fun pumpConnectedPrecondition(checkConnected: Boolean = true): Boolean {
        if (!this::pump.isInitialized) {
            Timber.e("pumpConnectedPrecondition: pump not initialized")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED, "not_initialized".toByteArray())
        } else if (pump.lastPeripheral == null) {
            Timber.e("pumpConnectedPrecondition: pump not saved peripheral")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED, "null_peripheral".toByteArray())
        } else if (checkConnected && !pump.isConnected) {
            Timber.e("pumpConnectedPrecondition: pump not connected")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED, "not_connected".toByteArray())
        } else {
            return true
        }
        return false
    }

    private fun isBolusCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
        return (message is InitiateBolusRequest) || (message.opCode() == InitiateBolusRequest().opCode() && message.characteristic == Characteristic.CONTROL)
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
                        TandemBluetoothHandler.getInstance(callbacks.getApplicationContext(), pump, null)
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
                    callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED,
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
                    if (callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                        Timber.i("pumpCommHandler busted cache: $it")
                        callbacks.pumpCommState.lastResponseMessage.remove(Pair(it.characteristic, it.responseOpCode))
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
                    if (callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                        val response = callbacks.pumpCommState.lastResponseMessage.get(Pair(it.characteristic, it.responseOpCode))
                        val ageSeconds = Duration.between(response?.second, Instant.now()).seconds
                        if (ageSeconds <= CacheSeconds) {
                            Timber.i("pumpCommHandler cached hit: $response")
                            callbacks.sendWearCommMessage(
                                MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE,
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
                val secretKey = callbacks.getWearPrefs()?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, msg.obj as ByteArray)

                val messageOk = confirmedBolus.left
                val pumpMsg = confirmedBolus.right
                if (!messageOk) {
                    Timber.w("pumpCommHandler bolus invalid signature")
                    callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_BLOCKED_SIGNATURE, "WearCommHandler".toByteArray())
                } else if (!isBolusCommand(pumpMsg)) {
                    Timber.e("SEND_PUMP_COMMAND_BOLUS not a bolus command: $pumpMsg")
                } else if (pumpConnectedPrecondition()) {
                    Timber.i("pumpCommHandler send bolus command with valid signature: $pumpMsg")
                    if (!callbacks.prefInsulinDeliveryActions()) {
                        Timber.e("No insulin delivery messages enabled -- blocking bolus command $pumpMsg")
                        callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED, "from_self".toByteArray())
                        return
                    }
                    try {
                        pump.command(pumpMsg as InitiateBolusRequest)
                    } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                        Timber.e(e)
                        callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED, "from_pumpx2_lib".toByteArray())
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
                        if (!callbacks.prefInsulinDeliveryActions()) {
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
                Timber.i("pumpCommHandler debug get message cache: ${callbacks.pumpCommState}.lastResponseMessage")
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_DEBUG_MESSAGE_CACHE, PumpMessageSerializer.toDebugMessageCacheBytes(callbacks.pumpCommState.lastResponseMessage.values))
            }
            CommServiceCodes.DEBUG_GET_HISTORYLOG_CACHE.ordinal -> {
                Timber.i("pumpCommHandler debug get historylog cache: ${callbacks.pumpCommState}.historyLogCache")
                if (callbacks.pumpCommState.historyLogCache.size <= 100) {
                    callbacks.sendWearCommMessage(
                        MessagePaths.FROM_PUMP_DEBUG_HISTORYLOG_CACHE,
                        PumpMessageSerializer.toDebugHistoryLogCacheBytes(callbacks.pumpCommState.historyLogCache)
                    )
                } else {
                    callbacks.pumpCommState.historyLogCache.entries.toList().chunked(100).forEach {
                        callbacks.sendWearCommMessage(
                            MessagePaths.FROM_PUMP_DEBUG_HISTORYLOG_CACHE,
                            PumpMessageSerializer.toDebugHistoryLogCacheBytes(it.associate {
                                Pair(it.key, it.value)
                            })
                        )
                    }
                }
            }
        }
    }
}
