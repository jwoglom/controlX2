package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.messaging.WearMessageBus
import com.jwoglom.controlx2.pump.BleChangeReceiver
import com.jwoglom.controlx2.pump.WearBolusManager
import com.jwoglom.controlx2.pump.CommServiceCallbacks
import com.jwoglom.controlx2.pump.PumpCommHandler
import com.jwoglom.controlx2.pump.PumpCommState
import com.jwoglom.controlx2.pump.PumpFinderCommHandler
import com.jwoglom.controlx2.pump.PumpHistoryLogFetcher
import com.jwoglom.controlx2.pump.PumpHistoryLogSyncWorker
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.util.HistoryLogFetcher
import com.jwoglom.controlx2.util.HistoryLogSyncWorker
import com.jwoglom.controlx2.util.UpdateComplication
import com.jwoglom.controlx2.util.WearX2Complication
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Instant

/**
 * Watch-side pump-host service. Manages the BT connection to the Tandem pump
 * when the watch is configured as the primary device (DeviceRole.PUMP_HOST).
 *
 * Modeled after mobile/CommService.kt, using the same :pumpcomm components
 * (PumpCommHandler, PumpFinderCommHandler, BolusManager) via CommServiceCallbacks.
 */
class WearPumpCommService : Service(), CommServiceCallbacks {
    override val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var pumpCommHandler: PumpCommHandler? = null
    private var pumpFinderCommHandler: PumpFinderCommHandler? = null

    private lateinit var messageBus: MessageBus
    private lateinit var bolusManager: WearBolusManager

    override val pumpCommState = PumpCommState()

    val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }

    private var bleChangeReceiver = BleChangeReceiver()

    private var serviceStatusAcknowledged = false
    private val serviceStatusTask = object : Runnable {
        override fun run() {
            if (!serviceStatusAcknowledged) {
                Timber.i("Periodically sending service status (not yet acknowledged)")
                if (WearPrefs(applicationContext).pumpFinderServiceEnabled()) {
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

    private val periodicUpdateIntervalMs: Long = 1000 * 60 * 5
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

    override fun sendWearCommMessage(path: String, message: ByteArray) {
        Timber.i("WearPumpCommService sendMessage: $path ${String(message)}")
        messageBus.sendMessage(path, message, MessageBusSender.COMM_SERVICE)
    }

    override fun onCreate() {
        super.onCreate()
        setupTimber("WPC", context = this)
        Timber.i("WearPumpCommService onCreate")

        val intentFilter = IntentFilter()
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
        registerReceiver(bleChangeReceiver, intentFilter)

        if (!WearPrefs(applicationContext).tosAccepted()) {
            Timber.w("WearPumpCommService short-circuiting: TOS not accepted")
            return
        } else if (!WearPrefs(applicationContext).serviceEnabled()) {
            Timber.w("WearPumpCommService short-circuiting: service not enabled")
            return
        }

        val handlerThread = HandlerThread("WearPumpCommServiceThread", Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.start()
        serviceLooper = handlerThread.looper

        messageBus = WearMessageBus(this)
        messageBus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                handleMessageReceived(path, data, sourceNodeId)
            }
        })

        bolusManager = WearBolusManager(applicationContext, ::sendWearCommMessage)

        if (WearPrefs(applicationContext).pumpFinderServiceEnabled()) {
            pumpFinderCommHandler = PumpFinderCommHandler(serviceLooper!!, this)
        } else {
            pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
        }

        serviceLooper?.let { looper ->
            Handler(looper).postDelayed(serviceStatusTask, 500)
        }
    }

    override fun onBind(intent: Intent?) = null

    private fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        if (!path.startsWith(MessagePaths.PREFIX_FROM_PUMP)) {
            Timber.d("WearPumpCommService messageReceived: $path ${String(data)} from $sourceNodeId")
        }
        when (path) {
            MessagePaths.TO_SERVER_FORCE_RELOAD -> {
                Timber.i("force-reload")
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
                    val filterToMac = WearPrefs(applicationContext).pumpFinderPumpMac().orEmpty()
                    val pairingCodeType = WearPrefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
                    val pairingCodeTypeEnum = if (pairingCodeType.isNotEmpty())
                        PairingCodeType.fromLabel(pairingCodeType)
                    else
                        PairingCodeType.SHORT_6CHAR
                    WearPrefs(applicationContext).setPumpFinderServiceEnabled(false)
                    WearPrefs(applicationContext).setUnbondOnNextCommInitMac(filterToMac)
                    pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
                    sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
            }
            MessagePaths.TO_SERVER_RESTART_PUMP_FINDER -> {
                Timber.i("restart-pump-finder")
                sendStopPumpFinderComm()
                pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
                WearPrefs(applicationContext).setPumpFinderServiceEnabled(true)
            }
            MessagePaths.TO_SERVER_CHECK_PUMP_FINDER_FOUND_PUMPS -> {
                sendCheckPumpFinderFoundPumps()
            }
            MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS -> {
                if (WearPrefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED, "".toByteArray())
                } else {
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
            }
            MessagePaths.TO_SERVER_REFRESH_HISTORY_LOG_SYNC -> {
                pumpCommHandler?.refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            }
            MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED -> {
                serviceStatusAcknowledged = true
            }
            MessagePaths.TO_SERVER_STOP_COMM -> {
                sendStopPumpComm()
            }
            MessagePaths.TO_SERVER_IS_PUMP_CONNECTED -> {
                sendCheckPumpConnected()
            }
            MessagePaths.TO_PUMP_PAIR -> {
                sendPumpPairingMessage()
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_WEAR -> {
                bolusManager.confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, WearBolusManager.BolusRequestSource.WEAR)
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE -> {
                bolusManager.confirmBolusRequest(PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest, WearBolusManager.BolusRequestSource.PHONE)
            }
            MessagePaths.TO_SERVER_BOLUS_CANCEL -> {
                bolusManager.resetBolusPrefs()
            }
            MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS -> {
                val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus = InitiateConfirmedBolusSerializer.fromBytes(secretKey, data)
                val messageOk = confirmedBolus.left
                val initiateMessage = confirmedBolus.right
                if (!messageOk) {
                    Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                    sendWearCommMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE, "WearPumpCommService".toByteArray())
                    return
                }
                Timber.i("sending confirmed bolus request: $initiateMessage")
                sendPumpCommBolusMessage(data)
            }
            MessagePaths.TO_SERVER_WRITE_CHARACTERISTIC_FAILED_CALLBACK -> {
                handleWriteCharacteristicFailedCallback(String(data))
            }
            MessagePaths.TO_PUMP_COMMAND -> {
                sendPumpCommMessage(data)
            }
            MessagePaths.TO_PUMP_COMMANDS -> {
                sendPumpCommMessages(data)
            }
            MessagePaths.TO_PUMP_DEBUG_COMMANDS -> {
                sendPumpCommMessages(data)
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

    // --- Preference accessors ---
    override fun prefAutoFetchHistoryLogs() = WearPrefs(applicationContext).autoFetchHistoryLogs()
    override fun prefConnectionSharingEnabled() = WearPrefs(applicationContext).connectionSharingEnabled()
    override fun prefOnlySnoopBluetoothEnabled() = WearPrefs(applicationContext).onlySnoopBluetoothEnabled()
    override fun prefInsulinDeliveryActions() = WearPrefs(applicationContext).insulinDeliveryActions()
    override fun prefQualifyingEventToastsEnabled() = WearPrefs(applicationContext).qualifyingEventToastsEnabled()
    override fun prefGlucoseUnit(): GlucoseUnit? = WearPrefs(applicationContext).glucoseUnit()
    override fun prefSetPumpModelName(name: String) { WearPrefs(applicationContext).setPumpModelName(name) }
    override fun prefSetCurrentPumpSid(sid: Int) { WearPrefs(applicationContext).setCurrentPumpSid(sid) }
    override fun prefPumpFinderPumpMac(): String? = WearPrefs(applicationContext).pumpFinderPumpMac()
    override fun prefUnbondOnNextCommInitMac(): String? = WearPrefs(applicationContext).unbondOnNextCommInitMac()
    override fun prefSetUnbondOnNextCommInitMac(mac: String?) { WearPrefs(applicationContext).setUnbondOnNextCommInitMac(mac) }

    // --- Sync/dispatch callbacks ---
    override fun onPumpConnectedSync(pumpSid: Int) {
        // Forward pump connection event to phone client for Nightscout/xDrip+ sync
        Timber.i("WearPumpCommService: pump connected, pumpSid=$pumpSid")
    }

    override fun dispatchExternalMessage(message: com.jwoglom.pumpx2.pump.messages.Message) {
        // Forward pump messages to phone client for xDrip+ broadcast relay
        Timber.d("WearPumpCommService: dispatchExternalMessage: $message")
    }

    override fun updateComplicationData(key: String, value: String, timestamp: Instant) {
        when (key) {
            "pumpBattery" -> UpdateComplication(this, WearX2Complication.PUMP_BATTERY)
            "pumpIOB" -> UpdateComplication(this, WearX2Complication.PUMP_IOB)
            "cgmReading" -> UpdateComplication(this, WearX2Complication.CGM_READING)
        }
    }

    // --- Debug API callbacks (no-op on watch) ---
    override fun onPumpMessageReceived(message: com.jwoglom.pumpx2.pump.messages.Message, source: SendType) {
        Timber.d("WearPumpCommService: onPumpMessageReceived: $message source=$source")
    }

    override fun onPumpCriticalError(error: TandemError, source: SendType) {
        Timber.e("WearPumpCommService: onPumpCriticalError: $error source=$source")
    }

    // --- Bolus response broadcasting ---
    override fun broadcastBolusInitiateResponse(responseBytes: ByteArray) {
        // Forward to phone client via message bus
        sendWearCommMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE, responseBytes)
    }

    override fun broadcastBolusStatusUpdate(statusBytes: ByteArray) {
        sendWearCommMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE, statusBytes)
    }

    // --- History log factories ---
    override fun createHistoryLogFetcher(
        pumpSid: Int,
        pumpSession: PumpSession,
        autoFetchEnabled: () -> Boolean,
    ): PumpHistoryLogFetcher {
        return HistoryLogFetcher(
            historyLogRepo = historyLogRepo,
            pumpSid = pumpSid,
            pumpSession = pumpSession,
            autoFetchEnabled = autoFetchEnabled,
            broadcastCallback = null
        )
    }

    override fun createHistoryLogSyncWorker(requestSync: () -> Unit): PumpHistoryLogSyncWorker {
        return HistoryLogSyncWorker(requestSync = requestSync)
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

    // --- Notification ---
    private data class DisplayablePumpData(
        var statusText: String = "Initializing...",
        var connectionTime: Instant? = null,
        var lastMessageTime: Instant? = null,
        var batteryPercent: Int? = null,
        var iobUnits: Double? = null,
    )

    private val currentPumpData = DisplayablePumpData()

    override fun updateNotification(statusText: String?) {
        if (statusText != null) {
            currentPumpData.statusText = statusText
        }
        val notification = createNotification()
        try {
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: SecurityException) {
            Timber.e(e, "Unable to start foreground service")
            stopSelf()
        }
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
        }
        if (changed) {
            currentPumpData.lastMessageTime = Instant.now()
            updateNotification()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "ControlX2 Pump Host"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Pump Host Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "ControlX2 pump host service"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        var title = currentPumpData.statusText
        currentPumpData.lastMessageTime?.let { title += " at ${shortTime(it)}" }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setSmallIcon(IconCompat.createWithResource(this, R.drawable.pump))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    // --- Pump command dispatch ---
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
            msg.obj = if (filterToBluetoothMac.isNotEmpty()) {
                "${pairingCodeType.label} $filterToBluetoothMac"
            } else {
                pairingCodeType.label
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

    private var started = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("WearPumpCommService onStartCommand $intent $flags $startId")

        if (started) {
            Timber.w("WearPumpCommService onStartCommand when already running, ignoring")
            return START_STICKY
        }

        started = true
        Toast.makeText(this, "ControlX2 pump host starting", Toast.LENGTH_SHORT).show()
        updateNotification("Initializing...")

        if (WearPrefs(applicationContext).pumpFinderServiceEnabled()) {
            Timber.i("Starting WearPumpCommService in PumpFinder mode")
            sendInitPumpFinderComm()
        } else {
            val pairingCodeType = WearPrefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
            val pairingCodeTypeEnum = if (pairingCodeType.isNotEmpty())
                PairingCodeType.fromLabel(pairingCodeType)
            else
                PairingCodeType.SHORT_6CHAR
            val filterToMac = WearPrefs(applicationContext).pumpFinderPumpMac().orEmpty()
            Timber.i("Starting WearPumpCommService: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")
            sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pumpCommHandler?.stopHistoryLogSyncWorker()
        scope.cancel()
        messageBus.close()
        try {
            unregisterReceiver(bleChangeReceiver)
        } catch (_: Exception) {}
        Toast.makeText(this, "ControlX2 pump host stopped", Toast.LENGTH_SHORT).show()
    }

    private fun prefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences("WearX2", MODE_PRIVATE)
    }
}
