package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.media.RingtoneManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.pump.BleChangeReceiver
import com.jwoglom.controlx2.pump.CommServiceCallbacks
import com.jwoglom.controlx2.pump.PumpCommHandler
import com.jwoglom.controlx2.pump.PumpCommState
import com.jwoglom.controlx2.pump.PumpFinderCommHandler
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.util.AppVersionCheck
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.shared.Hex
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Instant


class CommService : Service(), CommServiceCallbacks {
    override val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var pumpCommHandler: PumpCommHandler? = null
    private var pumpFinderCommHandler: PumpFinderCommHandler? = null

    private lateinit var messageBus: MessageBus
    override var httpDebugApiService: HttpDebugApiService? = null
        private set

    @androidx.annotation.VisibleForTesting
    internal fun setMessageBusForTesting(bus: MessageBus) {
        messageBus = bus
    }

    @androidx.annotation.VisibleForTesting
    internal fun simulateConnectedPump(peripheral: BluetoothPeripheral) {
        pumpCommHandler!!.simulateConnectedPump(peripheral)
    }

    private var serviceStatusAcknowledged = false
    private val serviceStatusTask = object : Runnable {
        override fun run() {
            if (!serviceStatusAcknowledged) {
                Timber.i("Periodically sending service status (not yet acknowledged)")
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED, "".toByteArray())
                } else {
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
                serviceLooper?.let { looper ->
                    Handler(looper).postDelayed(this, 2000)
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    override val pumpCommState = PumpCommState()

    val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    override val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }


    // PumpCommHandler extracted to pump/PumpCommHandler.kt
    // PumpFinderCommHandler extracted to pump/PumpFinderCommHandler.kt

    override fun sendWearCommMessage(path: String, message: ByteArray) {
        Timber.i("service sendMessage: $path ${String(message)}")
        messageBus.sendMessage(path, message, MessageBusSender.COMM_SERVICE)
    }

    // BleChangeReceiver and BondState extracted to pump/BleChangeReceiver.kt
    private var bleChangeReceiver = BleChangeReceiver()

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

    fun isPumpReadyForHistoryFetch(): Boolean {
        return pumpCommHandler?.isPumpReadyForHistoryFetch() == true
    }

    fun getPumpSession(): PumpSession? {
        return pumpCommHandler?.currentSession
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
            pumpFinderCommHandler = PumpFinderCommHandler(serviceLooper!!, this)
        } else {
            pumpCommHandler = PumpCommHandler(serviceLooper!!, this)

            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
            pumpCommHandler?.postDelayed(checkForUpdatesTask, checkForUpdatesDelayMs)
        }


        // Start periodic status sender (will stop when acknowledged)
        serviceLooper?.let { looper ->
            Handler(looper).postDelayed(serviceStatusTask, 500)
        }
    }

    override fun onBind(intent: Intent?) = null

    @androidx.annotation.VisibleForTesting
    internal fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        // Ignore noisy loopback logs for pump-originated broadcasts.
        if (!path.startsWith(MessagePaths.PREFIX_FROM_PUMP)) {
            Timber.d("service messageReceived: $path ${String(data)} from $sourceNodeId")
        }
        httpDebugApiService?.onMessagingReceived(path, data, sourceNodeId)
        when (path) {
            MessagePaths.TO_SERVER_FORCE_RELOAD -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            MessagePaths.TO_SERVER_SET_PAIRING_CODE -> {
                Timber.i("set-pairing-code received in service")
            }
            MessagePaths.TO_SERVER_STOP_PUMP_FINDER -> {
                Timber.i("stop-pump-finder")
                sendStopPumpFinderComm()
                if (String(data) == "init_comm") {
                    pumpFinderCommHandler = null
                    pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
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
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
            }
            MessagePaths.TO_SERVER_RESTART_PUMP_FINDER -> {
                Timber.i("restart-pump-finder")
                sendStopPumpFinderComm()
                pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
                Prefs(applicationContext).setPumpFinderServiceEnabled(true)
                Timber.i("restart-pump-finder")
                triggerAppReload(applicationContext)
                //sendInitPumpComm()
            }
            MessagePaths.TO_SERVER_CHECK_PUMP_FINDER_FOUND_PUMPS -> {
                Timber.i("check-pump-finder-found-pumps")
                sendCheckPumpFinderFoundPumps()
            }
            MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS -> {
                Timber.i("request-service-status received, responding with current status")
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED, "".toByteArray())
                } else {
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
            }
            MessagePaths.TO_SERVER_REFRESH_HISTORY_LOG_SYNC -> {
                Timber.i("refresh-history-log-sync received")
                pumpCommHandler?.refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            }
            MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED -> {
                Timber.i("service-status acknowledged, stopping periodic sender")
                serviceStatusAcknowledged = true
            }
            MessagePaths.TO_SERVER_STOP_COMM -> {
                Timber.w("stop-comm")
                sendStopPumpComm()
            }
            MessagePaths.TO_SERVER_IS_PUMP_CONNECTED -> {
                sendCheckPumpConnected()
            }
            MessagePaths.TO_PUMP_PAIR -> {
                sendPumpPairingMessage()
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_WEAR -> {
                // removed: initialized check
                confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, BolusRequestSource.WEAR)
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE -> {
                // removed: initialized check
                confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, BolusRequestSource.PHONE)
            }
            MessagePaths.TO_SERVER_BOLUS_CANCEL -> {
                Timber.i("bolus state cancelled")
                resetBolusPrefs(this)
            }
            MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS -> {
                // removed: initialized check
                val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, data)

                val messageOk = confirmedBolus.left
                val initiateMessage = confirmedBolus.right
                if (!messageOk) {
                    Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                    sendWearCommMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE,
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
            MessagePaths.TO_SERVER_WRITE_CHARACTERISTIC_FAILED_CALLBACK -> {
                Timber.i("writeCharacteristicFailedCallback from message")
                handleWriteCharacteristicFailedCallback(String(data))
            }
            MessagePaths.TO_PUMP_COMMAND -> {
                sendPumpCommMessage(data)
            }
            MessagePaths.TO_PUMP_COMMANDS -> {
                sendPumpCommMessages(data)
            }
            MessagePaths.TO_PUMP_DEBUG_COMMANDS -> {
                sendPumpCommDebugMessages(data)
            }
            MessagePaths.TO_PUMP_COMMANDS_BUST_CACHE -> {
                sendPumpCommMessagesBustCache(data)
            }
            MessagePaths.TO_PUMP_CACHED_COMMANDS -> {
                handleCachedCommandsRequest(data)
            }
            MessagePaths.TO_PUMP_DEBUG_MESSAGE_CACHE -> {
                handleDebugGetMessageCache()
            }
            MessagePaths.TO_PUMP_DEBUG_HISTORYLOG_CACHE -> {
                handleDebugGetHistoryLogCache()
            }
            MessagePaths.TO_PUMP_DEBUG_WRITE_BT_CHARACTERISTIC -> {
                sendDebugWriteBtCharacteristic(data)
            }
        }
    }

    override fun broadcastHistoryLogItem(item: HistoryLogItem) {
        httpDebugApiService?.onHistoryLogInsertedCallback?.invoke(item)
    }

    override fun markConnectionTime() {
        currentPumpData.connectionTime = Instant.now()
    }

    override fun showToast(text: String, duration: Int) {
        Toast.makeText(this, text, duration).show()
    }

    override fun getWearPrefs(): SharedPreferences? {
        return getSharedPreferences("WearX2", MODE_PRIVATE)
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


    override fun updateNotification(statusText: String?) {
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

    override fun updateNotificationWithPumpData(message: com.jwoglom.pumpx2.pump.messages.Message) {
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
    override fun sendPumpCommMessages(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommDebugMessages(pumpMsgBytes: ByteArray) {
        try {
            val messages = PumpMessageSerializer.fromBulkBytes(pumpMsgBytes)
            synchronized(pumpCommState.debugPromptResponseCounts) {
                messages.forEach {
                    val key = Pair(it.characteristic, it.responseOpCode)
                    pumpCommState.debugPromptResponseCounts[key] = (pumpCommState.debugPromptResponseCounts[key] ?: 0) + 1
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
        return pumpCommState.consumeDebugPromptResponse(characteristic, opCode)
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
        sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_MIN_NOTIFY_THRESHOLD, "$minNotifyThreshold".toByteArray())
        sendWearCommMessage(MessagePaths.TO_CLIENT_WEAR_AUTO_APPROVE_TIMEOUT, "$autoApproveTimeout".toByteArray())

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
            sendWearCommMessage(MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG,
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
        pumpCommHandler?.stopHistoryLogSyncWorker()
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
