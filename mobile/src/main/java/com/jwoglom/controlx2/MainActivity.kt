package com.jwoglom.controlx2

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.compositionLocalOf
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.MobileApp
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.screens.sections.messagePairToJson
import com.jwoglom.controlx2.presentation.screens.sections.verbosePumpMessage
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.controlx2.util.extractPumpSid
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GetSavedG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Objects
import java.util.function.Supplier
import kotlin.system.exitProcess


var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : ComponentActivity() {
    private lateinit var messageBus: MessageBus

    private val applicationScope = CoroutineScope(SupervisorJob())
    private val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    private val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }
    // hack: the ViewModel references the current pumpSid, so that streaming works in the UI, which we store lazily in the app preferences
    private val historyLogViewModel: HistoryLogViewModel by viewModels {
        HistoryLogViewModelFactory(historyLogRepo, Prefs(applicationContext).currentPumpSid())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("mobile UIActivity onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        setupTimber(
            "MUA",
            context = this,
            logToFile = true,
            shouldLog = ShouldLogToFile(this),
            writeCharacteristicFailedCallback = writeCharacteristicFailedCallback
        )
        val startDestination = determineStartDestination()
        Timber.d("startDestination=%s", startDestination)

        setContent {
            MobileApp(
                startDestination = startDestination,
                sendMessage = { path, message -> sendMessage(path, message) },
                sendPumpCommands = { type, messages -> sendPumpCommands(type, messages) },
                sendServiceBolusRequest = { bolusId, bolusParameters, unitBreakdown, dataSnapshot, timeSinceReset ->
                    sendServiceBolusRequest(
                        bolusId,
                        bolusParameters,
                        unitBreakdown,
                        dataSnapshot,
                        timeSinceReset
                    )
                },
                sendServiceBolusCancel = {
                    sendMessage(
                        "/to-phone/bolus-cancel",
                        "".toByteArray()
                    )
                },
                historyLogViewModel = historyLogViewModel
            )
        }

        // Initialize MessageBus and register as listener
        if (checkPlayServicesAndInitialize()) {
            messageBus = MessageBusFactory.createMessageBus(this)
            messageBus.addMessageListener(object : MessageListener {
                override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                    handleMessageReceived(path, data, sourceNodeId)
                }
            })
        }
        checkNotificationPermissions()


        startCommServiceWithPreconditions()
    }

    private fun checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {

                val REQUEST_POST_NOTIFICATIONS = 10023;
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }
    }

    private val writeCharacteristicFailedCallback: (String) -> Unit = { uuid ->
        sendMessage("/to-phone/write-characteristic-failed-callback", uuid.toByteArray())
    }

    override fun onResume() {
        Timber.i("activity onResume")
//        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
//            mApiClient.connect()
//        }

        if (!Prefs(applicationContext).tosAccepted()) {
            Timber.i("BTPermissionsCheck not started because TOS not accepted")
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.i("BTPermissionsCheck not started because service not enabled")
        } else {
            startBTPermissionsCheck()
        }

        // Request service status when resuming, in case app was started in background
        if (dataStore.pumpSetupStage.value == PumpSetupStage.WAITING_PUMP_FINDER_INIT &&
            Prefs(applicationContext).serviceEnabled()) {
            Timber.i("onResume: requesting service status (currently in WAITING_PUMP_FINDER_INIT)")
            sendMessage("/to-phone/request-service-status", "".toByteArray())
        }

        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        if (::messageBus.isInitialized) {
            messageBus.close()
        }
        super.onDestroy()
    }

    private fun startCommServiceWithPreconditions() {
        if (!Prefs(applicationContext).tosAccepted()) {
            Timber.i("commService not started because first TOS not accepted")
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.i("commService not started because service not enabled")
        } else {
            startCommService()
        }
    }
    private fun startCommService() {
        Timber.i("starting CommService")
        // Start CommService
        val intent = Intent(applicationContext, CommService::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        applicationContext.bindService(intent, commServiceConnection, BIND_AUTO_CREATE)
    }

    private val commServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            //retrieve an instance of the service here from the IBinder returned
            //from the onBind method to communicate with
            Timber.i("CommService onServiceConnected")
            // Request service status now that we're ready to receive it
            sendMessage("/to-phone/request-service-status", "".toByteArray())
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("CommService onServiceDisconnected")
        }
    }


    private fun checkPlayServicesAndInitialize(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode == ConnectionResult.SUCCESS) {
            return true
        } else {
            when (resultCode) {
                ConnectionResult.API_UNAVAILABLE -> {
                    AlertDialog.Builder(this)
                        .setMessage(
                            """
                        The 'Wear OS' application is not installed on this device.
                        This is required due to the current implementation.
                        To resolve, install the 'Wear OS' app from Google Play.
                        """.trimIndent()
                        )
                        .setPositiveButton("Install") { _, _ ->
                            openPlayStore("com.google.android.wearable.app")
                        }
                        .show()
                }
                else -> {
                    if (availability.isUserResolvableError(resultCode)) {
                        availability.getErrorDialog(this, resultCode, 1000) {
                            exitProcess(0)
                        }?.show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Error connecting to Google Play Services")
                            .setMessage("Error code: $resultCode")
                            .setPositiveButton("OK") { d, _ -> d.cancel() }
                            .show()
                    }
                }
            }
            return false
        }
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("mobile sendMessage: $path ${String(message)}")
        messageBus.sendMessage(path, message, MessageBusSender.MOBILE_UI)
    }

    private fun sendPumpCommands(type: SendType, msgs: List<Message>) {
        if (msgs.isEmpty()) return
        if (type == SendType.DEBUG_PROMPT) {
            synchronized (dataStore.debugPromptAwaitingResponses) {
                val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
                awaiting.addAll(msgs.map { it.responseClass.name })
                dataStore.debugPromptAwaitingResponses.value = awaiting
                Timber.d("added %s to debugPromptAwaitingResponses = %s", msgs, dataStore.debugPromptAwaitingResponses.value)
            }
        }
        sendMessage("/to-pump/${type.slug}", PumpMessageSerializer.toBulkBytes(msgs))
    }

    private fun sendServiceBolusRequest(bolusId: Int, params: BolusParameters, unitBreakdown: BolusCalcUnits, dataSnapshot: BolusCalcDataSnapshotResponse, timeSinceReset: TimeSinceResetResponse) {
        val numUnits = InsulinUnit.from1To1000(params.units)
        val numCarbs = params.carbsGrams
        val bgValue = params.glucoseMgdl

        val bolusTypes = mutableListOf(BolusDeliveryHistoryLog.BolusType.FOOD2)

        val foodVolume = InsulinUnit.from1To1000(unitBreakdown.fromCarbs)
        if (foodVolume > 0) {
            bolusTypes.add(BolusDeliveryHistoryLog.BolusType.FOOD1)
        }

        var corrVolume = InsulinUnit.from1To1000(unitBreakdown.fromBG) + InsulinUnit.from1To1000(unitBreakdown.fromIOB)
        if (corrVolume > 0) {
            bolusTypes.add(BolusDeliveryHistoryLog.BolusType.CORRECTION)
        } else {
            corrVolume = 0 // negative correction volume is not passed through
        }

        val preCommands: MutableList<Message> = mutableListOf()
        if (bgValue > 0 && timeSinceReset.pumpTimeSecondsSinceReset > 0) {
            val autopopBg = when {
                !dataSnapshot.isAutopopAllowed -> false
                dataSnapshot.correctionFactor == 0 -> false
                bgValue != dataSnapshot.correctionFactor -> false
                else -> true
            }
            val remoteBgRequest = RemoteBgEntryRequest(
                bgValue,
                false, // useForCgmCalibration
                autopopBg,
                timeSinceReset.pumpTimeSecondsSinceReset,
                bolusId
            )
            Timber.i("sendServiceBolusRequest: sending remoteBgRequest=$remoteBgRequest")
            preCommands.add(remoteBgRequest)
        }

        if (numCarbs > 0 && timeSinceReset.pumpTimeSecondsSinceReset > 0) {
            val remoteCarbRequest = RemoteCarbEntryRequest(
                numCarbs,
                timeSinceReset.pumpTimeSecondsSinceReset,
                bolusId
            )
            Timber.i("sendServiceBolusRequest: sending remoteCarbRequest=$remoteCarbRequest")
            preCommands.add(remoteCarbRequest)
        }

        if (preCommands.isNotEmpty()) {
            this.sendPumpCommands(SendType.STANDARD, preCommands)
        }

        val iobUnits = dataSnapshot.iob
        val bolusRequest = InitiateBolusRequest(
            numUnits,
            bolusId,
            BolusDeliveryHistoryLog.BolusType.toBitmask(*bolusTypes.toTypedArray()),
            foodVolume,
            corrVolume,
            numCarbs,
            bgValue,
            iobUnits
        )

        Timber.i("sendServiceBolusRequest: numUnits=$numUnits numCarbs=$numCarbs bgValue=$bgValue foodVolume=$foodVolume corrVolume=$corrVolume iobUnits=$iobUnits: bolusRequest=$bolusRequest preCommands=$preCommands")
        this.sendMessage("/to-phone/bolus-request-phone", PumpMessageSerializer.toBytes(bolusRequest))
    }


    // Message received from Wear or CommService via MessageBus
    private fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        Timber.i("phone messageReceived: $path: ${String(data)} from $sourceNodeId")
        when (path) {
            "/to-phone/start-comm" -> {
                when (String(data)) {
                    "skip_notif_permission" -> {
                        startBTPermissionsCheck()
                        startCommServiceWithPreconditions()
                        dataStore.pumpSetupStage.value =
                            dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                    }
                    else -> {
                        requestNotificationCallback = { isGranted ->
                            if (isGranted) {
                                startBTPermissionsCheck()
                                startCommServiceWithPreconditions()
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                            } else {
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            "/to-phone/comm-started" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP)
                // Acknowledge receipt to stop periodic sender
                sendMessage("/to-phone/service-status-acknowledged", "".toByteArray())
            }

            "/to-phone/start-pump-finder" -> {
                when (String(data)) {
                    "skip_notif_permission" -> {
                        startBTPermissionsCheck()
                        startCommServiceWithPreconditions()
                        dataStore.pumpSetupStage.value =
                            dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
                    }
                    else -> {
                        requestNotificationCallback = { isGranted ->
                            if (isGranted) {
                                startBTPermissionsCheck()
                                startCommServiceWithPreconditions()
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
                            } else {
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            "/to-phone/pump-finder-started" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS)
                // Acknowledge receipt to stop periodic sender
                sendMessage("/to-phone/service-status-acknowledged", "".toByteArray())
            }

            "/from-pump/pump-finder-found-pumps" -> {
                dataStore.pumpFinderPumps.value = String(data).split(";").map {
                    Pair(it.substringBefore("="), it.substringAfter("="))
                }
                if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS) {
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_SELECT_PUMP)
                }
            }

            "/to-phone/set-pairing-code" -> {
                val pairingCodeText = String(data)
                PumpState.setPairingCode(applicationContext, pairingCodeText)
                Toast.makeText(applicationContext, "Set pairing code: $pairingCodeText", Toast.LENGTH_SHORT).show()

                if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMP_FINDER_ENTER_PAIRING_CODE ||
                    dataStore.pumpSetupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE ||
                    dataStore.pumpSetupStage.value == PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP)
                {
                    Prefs(applicationContext).setPumpFinderServiceEnabled(false)
                    sendMessage("/to-phone/stop-pump-finder", "init_comm".toByteArray())
                } else if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE) {
                    sendMessage("/to-pump/pair", "".toByteArray())
                }
            }

            "/to-phone/app-reload" -> {
                triggerAppReload(applicationContext)
            }

            "/to-phone/connected" -> {
                dataStore.watchConnected.value = true
            }

            "/from-pump/pump-discovered" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCOVERED)
                dataStore.setupDeviceName.value = String(data)
                extractPumpSid(String(data))?.let {
                    dataStore.pumpSid.value = it
                }
            }

            "/from-pump/pump-model" -> {
                dataStore.setupDeviceModel.value = String(data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA)
            }

            "/from-pump/initial-pump-connection" -> {
                dataStore.setupDeviceName.value = String(data)
                extractPumpSid(String(data))?.let {
                    dataStore.pumpSid.value = it
                }
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION)
            }

            "/from-pump/entered-pairing-code" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SENDING_PAIRING_CODE)
            }

            "/from-pump/missing-pairing-code" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE)
            }

            "/from-pump/invalid-pairing-code" -> {
                Timber.w("invalid-pairing-code with code: ${PumpState.getPairingCode(applicationContext)}")
                PumpState.setPairingCode(applicationContext, "")
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE)
                sendMessage("/to-phone/stop-comm", "invalid_pairing_code".toByteArray())
            }

            "/from-pump/pump-critical-error" -> {
                Timber.w("pump-critical-error: ${String(data)}")
                dataStore.pumpCriticalError.value = Pair(String(data), Instant.now())
            }

            "/from-pump/pump-connected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_CONNECTED)
                dataStore.setupDeviceName.value = String(data)
                extractPumpSid(String(data))?.let {
                    dataStore.pumpSid.value = it
                }
                dataStore.pumpConnected.value = true
                dataStore.pumpLastConnectionTimestamp.value = Instant.now()
            }

            // on explicit disconnection
            "/from-pump/pump-disconnected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                dataStore.setupDeviceModel.value = String(data)
                dataStore.pumpConnected.value = false
            }

            // on implicit disconnection (i.e. we didn't get the explicit disconnect)
            "/from-pump/pump-not-connected" -> {
                if (dataStore.pumpConnected.value == true) {
                    Timber.i("tracked implicit disconnection (before pump-disconnected)")
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                    dataStore.pumpConnected.value = false
                }
            }

            "/from-pump/receive-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(data)
                onPumpMessageReceived(pumpMessage, false)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }
            "/from-pump/receive-cached-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(data)
                onPumpMessageReceived(pumpMessage, true)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }

            "/from-pump/debug-message-cache" -> {
                val processed = PumpMessageSerializer.fromDebugMessageCacheBytes(data)
                dataStore.debugMessageCache.value = processed
            }

            "/from-pump/debug-historylog-cache" -> {
                val processed = PumpMessageSerializer.fromDebugHistoryLogCacheBytes(data)
                var mp = dataStore.historyLogCache.value
                if (mp == null) {
                     mp = mutableMapOf()
                }
                mp.putAll(processed)
                dataStore.historyLogCache.value = mp
            }
        }
    }

    private fun onPumpMessageReceived(message: Message, cached: Boolean) {
        if (dataStore.debugPromptAwaitingResponses.value?.contains(message.javaClass.name) == true) {
            synchronized (dataStore.debugPromptAwaitingResponses) {
                val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
                awaiting.remove(message.javaClass.name)
                dataStore.debugPromptAwaitingResponses.value = awaiting
                Timber.d("removed %s from debugPromptAwaitingResponses = %s", message.javaClass.name, dataStore.debugPromptAwaitingResponses.value)
            }
            fun setClipboard(str: String) {
                val clipboard: ClipboardManager =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(str, str)
                clipboard.setPrimaryClip(clip)
            }
            val verboseStr = verbosePumpMessage(message)
            AlertDialog.Builder(this)
                .setMessage(verboseStr)
                .setNeutralButton("Copy JSON") { dialog, which ->
                    setClipboard(messagePairToJson(Pair(message, Instant.now())))
                }
                .setNegativeButton("Copy") { dialog, which ->
                    setClipboard(verboseStr)
                }
                .setPositiveButton("OK") { dialog, which -> dialog.cancel() }
                .show()
        }

        // Always process error handlers first
        when (message) {
            is StatusMessage -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                    return
                }
            }
        }

        if (NotificationBundle.isNotificationResponse(message)) {
            synchronized(dataStore.notificationBundle) {
                val updated = (dataStore.notificationBundle.value ?: NotificationBundle()).add(message)
                // re-create to trigger state update via object change
                dataStore.notificationBundle.value = NotificationBundle(updated)
            }
        }

        if (IDPManager.isIDPManagerResponse(message)) {
            synchronized(dataStore.idpManager) {
                val updated = (dataStore.idpManager.value ?: IDPManager()).processMessage(message)
                // re-create to trigger state update via object change
                dataStore.idpManager.value = IDPManager(updated)
            }
        }
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                dataStore.batteryPercent.value = message.batteryPercent
            }
            is ControlIQIOBResponse -> {
                dataStore.iobUnits.value = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is ControlIQInfoAbstractResponse -> {
                dataStore.controlIQMode.value = when (message.currentUserModeType) {
                    ControlIQInfoAbstractResponse.UserModeType.STANDARD -> UserMode.NONE
                    ControlIQInfoAbstractResponse.UserModeType.SLEEP -> UserMode.SLEEP
                    ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> UserMode.EXERCISE
                    else -> UserMode.UNKNOWN
                }
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
            }
            is LastBolusStatusAbstractResponse -> {

                dataStore.lastBolusStatus.value = if (message.timestamp > 0)
                    "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(pumpTimeToLocalTz(message.timestampInstant))}"
                else
                    null
                dataStore.lastBolusStatusResponse.value = message
            }
            is HomeScreenMirrorResponse -> {
                dataStore.controlIQStatus.value = when (message.apControlStateIcon) {
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY -> "On"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_RED_BIQ_CIQ_BASAL_SUSPENDED -> "Suspended"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_BLUE_CIQ_INCREASE_BASAL -> "Increase"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_ORANGE_CIQ_ATTENUATION_BASAL -> "Reduced"
                    else -> "Off"
                }
                dataStore.cgmStatusText.value = when (message.cgmAlertIcon) {
                    HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_1, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_2, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_3, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_4 -> "Starting up"
                    HomeScreenMirrorResponse.CGMAlertIcon.CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.CHECKMARK_BLOOD_DROP -> "Calibration Needed"
                    HomeScreenMirrorResponse.CGMAlertIcon.ERROR_HIGH_WEDGE, HomeScreenMirrorResponse.CGMAlertIcon.ERROR_LOW_WEDGE -> "Error"
                    HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_SENSOR -> "Replace Sensor"
                    HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_TRANSMITTER -> "Replace Transmitter"
                    HomeScreenMirrorResponse.CGMAlertIcon.OUT_OF_RANGE -> "Out Of Range"
                    HomeScreenMirrorResponse.CGMAlertIcon.FAILED_SENSOR -> "Sensor Failed"
                    HomeScreenMirrorResponse.CGMAlertIcon.TRIPLE_DASHES -> "---"
                    else -> ""
                }
                dataStore.cgmHighLowState.value = when (message.cgmAlertIcon) {
                    HomeScreenMirrorResponse.CGMAlertIcon.LOW -> "LOW"
                    HomeScreenMirrorResponse.CGMAlertIcon.HIGH -> "HIGH"
                    else -> "IN_RANGE"
                }
                dataStore.cgmDeltaArrow.value = message.cgmTrendIcon.arrow()
                dataStore.basalStatus.value = when (message.basalStatusIcon) {
                    HomeScreenMirrorResponse.BasalStatusIcon.BASAL -> BasalStatus.ON
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_BASAL -> BasalStatus.ZERO
                    HomeScreenMirrorResponse.BasalStatusIcon.TEMP_RATE -> BasalStatus.TEMP_RATE
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_TEMP_RATE -> BasalStatus.ZERO_TEMP_RATE
                    HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND -> BasalStatus.PUMP_SUSPENDED
                    HomeScreenMirrorResponse.BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> BasalStatus.BASALIQ_SUSPENDED
                    HomeScreenMirrorResponse.BasalStatusIcon.INCREASE_BASAL -> BasalStatus.CONTROLIQ_INCREASED
                    HomeScreenMirrorResponse.BasalStatusIcon.ATTENUATED_BASAL -> BasalStatus.CONTROLIQ_REDUCED
                    else -> BasalStatus.UNKNOWN
                }
                dataStore.cartridgeRemainingEstimate.value = message.remainingInsulinPlusIcon
            }
            is CurrentBasalStatusResponse -> {
                dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
            }
            is TempRateResponse -> {
                dataStore.tempRateActive.value = message.active
                dataStore.tempRateDetails.value = message
            }
            is CGMStatusResponse -> {
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> CGMSessionState.ACTIVE
                    CGMStatusResponse.SessionState.SESSION_STOPPED -> CGMSessionState.STOPPED
                    CGMStatusResponse.SessionState.SESSION_START_PENDING -> CGMSessionState.STARTING
                    CGMStatusResponse.SessionState.SESSION_STOP_PENDING -> CGMSessionState.STOPPING
                    else -> null
                }
                dataStore.cgmSessionExpireRelative.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTimeAgo(
                            pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                                .plus(10, ChronoUnit.DAYS),
                        suffix = "left")
                    else -> ""
                }
                dataStore.cgmSessionExpireExact.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTime(
                        pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                            .plus(10, ChronoUnit.DAYS))
                    else -> ""
                }
                dataStore.cgmTransmitterStatus.value = when (message.transmitterBatteryStatus) {
                    CGMStatusResponse.TransmitterBatteryStatus.ERROR -> "Error"
                    CGMStatusResponse.TransmitterBatteryStatus.EXPIRED -> "Expired"
                    CGMStatusResponse.TransmitterBatteryStatus.OK -> "OK"
                    CGMStatusResponse.TransmitterBatteryStatus.OUT_OF_RANGE -> "OOR"
                    else -> "Unknown"
                }
            }
            is CurrentEGVGuiDataResponse -> {
                dataStore.cgmReading.value = message.cgmReading
                dataStore.cgmDelta.value = message.trendRate
            }
            is GetSavedG7PairingCodeResponse -> {
                dataStore.savedG7PairingCode.value = message.pairingCode
            }
            is BolusCalcDataSnapshotResponse -> {
                if (!cached) {
                    dataStore.bolusCalcDataSnapshot.value = message
                }
            }
            is LastBGResponse -> {
                dataStore.bolusCalcLastBG.value = message
            }
            is PumpGlobalsResponse -> {
                dataStore.pumpGlobalsResponse.value = message
            }
            is GlobalMaxBolusSettingsResponse -> {
                dataStore.maxBolusAmount.value = message.maxBolus
            }
            is HistoryLogStatusResponse -> {
                dataStore.historyLogStatus.value = message
            }
            is BolusPermissionResponse -> {
                dataStore.bolusPermissionResponse.value = message
                PumpStateSupplier.inProgressBolusId = Supplier { message.bolusId }
            }
            is RemoteCarbEntryResponse -> {
                dataStore.bolusCarbEntryResponse.value = message
            }
            is InitiateBolusResponse -> {
                dataStore.bolusInitiateResponse.value = message
            }
            is CancelBolusResponse -> {
                if (dataStore.bolusCancelResponse.value == null || message.wasCancelled()) {
                    dataStore.bolusCancelResponse.value = message
                    PumpStateSupplier.inProgressBolusId = Supplier { null }
                } else {
                    Timber.w("skipping population of bolusCancelResponse: $message because a successful cancellation already existed in the state: ${dataStore.bolusCancelResponse.value}");
                }
            }
            is CurrentBolusStatusResponse -> {
                dataStore.bolusCurrentResponse.value = message
            }
            is TimeSinceResetResponse -> {
                dataStore.timeSinceResetResponse.value = message
            }

            is EnterChangeCartridgeModeStateStreamResponse -> {
                dataStore.enterChangeCartridgeState.value = message
            }
            is DetectingCartridgeStateStreamResponse -> {
                dataStore.detectingCartridgeState.value = message
            }
            is FillTubingStateStreamResponse -> {
                dataStore.fillTubingState.value = message
            }
            is ExitFillTubingModeStateStreamResponse -> {
                dataStore.exitFillTubingState.value = message
            }
            is FillCannulaStateStreamResponse -> {
                dataStore.fillCannulaState.value = message
            }
            is PumpingStateStreamResponse -> {
                dataStore.pumpingState.value = message
            }
            is EnterChangeCartridgeModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = true
                }
            }
            is ExitChangeCartridgeModeResponse -> {
                if (message.status != 0) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = false
                }
            }
            is EnterFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = true
                }
            }
            is ExitFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = false
                }
            }

        }
    }

    private fun unsuccessfulAlert(req: String) {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Failed Pump Request")
            .setMessage("$req was not successful. The pump returned an error fulfilling the request.")
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * BT permissions
     */

    private fun startBTPermissionsCheck() {
        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                val REQUEST_ENABLE_BT = 1
                startActivityForResult(
                    enableBtIntent,
                    REQUEST_ENABLE_BT
                )
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private fun getBluetoothManager(): BluetoothManager {
        return Objects.requireNonNull(
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager,
            "cannot get BluetoothManager"
        )
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = getBluetoothManager().adapter ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = getMissingPermissions(getRequiredPermissions())
            if (missingPermissions.size > 0) {
                val ACCESS_LOCATION_REQUEST = 2
                requestPermissions(
                    missingPermissions,
                    ACCESS_LOCATION_REQUEST
                )
            } else {
                permissionsGranted()
            }
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun getRequiredPermissions(): Array<String> {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        val targetSdkVersion = applicationInfo.targetSdkVersion
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                // getBluetoothHandler().startScan()
            }
        } else {
            // getBluetoothHandler().startScan()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        if (locationManager == null) {
            Timber.e("could not get location manager")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton(
                    "Enable"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, which -> // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton(
                    "Retry"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }

    /**
     * Notification permission
     */
    private var requestNotificationCallback: (Boolean) -> Unit = {}
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        requestNotificationCallback(isGranted)
    }


    private fun triggerAppReload(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun openPlayStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun determineStartDestination(): String {
        return when {
            !Prefs(applicationContext).tosAccepted() -> Screen.FirstLaunch.route
            !Prefs(applicationContext).pumpSetupComplete() -> Screen.PumpSetup.route
            !Prefs(applicationContext).appSetupComplete() -> Screen.AppSetup.route
            else -> Screen.Landing.route
        }
    }
}
