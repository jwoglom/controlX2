package com.jwoglom.wearx2

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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.compositionLocalOf
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.MobileApp
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.PumpSetupStage
import com.jwoglom.wearx2.presentation.screens.sections.messagePairToJson
import com.jwoglom.wearx2.presentation.screens.sections.verbosePumpMessage
import com.jwoglom.wearx2.presentation.util.ShouldLogToFile
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.util.SendType
import com.jwoglom.wearx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.wearx2.shared.util.setupTimber
import com.jwoglom.wearx2.shared.util.shortTime
import com.jwoglom.wearx2.shared.util.shortTimeAgo
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces1000Unit
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.system.exitProcess


var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : ComponentActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private lateinit var mApiClient: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("mobile UIActivity onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        setupTimber("MUA",
            context = this,
            logToFile = true,
            shouldLog = ShouldLogToFile(this),
            writeCharacteristicFailedCallback = writeCharacteristicFailedCallback)
        val startDestination = determineStartDestination()
        Timber.d("startDestination=%s", startDestination)

        setContent {
            MobileApp(
                startDestination = startDestination,
                sendMessage = {path, message -> sendMessage(path, message) },
                sendPumpCommands = {type, messages -> sendPumpCommands(type, messages) },
            )
        }

        reinitializeGoogleApiClient()


        startCommServiceWithPreconditions()
    }

    private fun reinitializeGoogleApiClient() {
        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        mApiClient.connect()
    }

    private val writeCharacteristicFailedCallback: (String) -> Unit = { uuid ->
        sendMessage("/to-phone/write-characteristic-failed-callback", uuid.toByteArray())
    }

    override fun onResume() {
        Timber.i("activity onResume")
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }

        if (!Prefs(applicationContext).tosAccepted()) {
            Timber.i("BTPermissionsCheck not started because TOS not accepted")
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.i("BTPermissionsCheck not started because service not enabled")
        } else {
            startBTPermissionsCheck()
        }
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        Wearable.MessageApi.removeListener(mApiClient, this)
        if (mApiClient.isConnected) {
            mApiClient.disconnect()
        }
    }

    override fun onDestroy() {
        mApiClient.unregisterConnectionCallbacks(this)
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
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("CommService onServiceDisconnected")
        }
    }

    override fun onConnected(bundle: Bundle?) {
        connectionFailureCount = 0
        Timber.i("mobile onConnected $bundle")
        sendMessage("/to-wear/connected", "phone_launched".toByteArray())
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("mobile onConnectionSuspended: $id")
        mApiClient.reconnect()
    }

    private var connectionFailureCount = 0
    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("mobile onConnectionFailed: $result connectionFailureCount=$connectionFailureCount")
        if (!result.isSuccess) {
            val apiAvail = GoogleApiAvailability.getInstance()
            connectionFailureCount++
            when (result.errorCode) {
                ConnectionResult.API_UNAVAILABLE -> {
                    AlertDialog.Builder(this)
                        .setMessage(
                            """The 'Wear OS' application is not installed on this device.
                    This is required, even if not using a wearable, due to the current implementation of the app which uses these libraries.
                    To resolve this issue, install the 'Wear OS' app from Google Play. This dependency will be removed in a later version.""".trimMargin()
                        )
                        .setNegativeButton("Cancel") { dialog, which ->

                        }
                        .setPositiveButton("OK") { dialog, which ->
                            openPlayStore("com.google.android.wearable.app")
                        }
                        .show()
                    return
                }
                else -> {
                    if (connectionFailureCount > 3) {
                        if (apiAvail.isUserResolvableError(result.errorCode)) {
                            apiAvail.getErrorDialog(this, result.errorCode, 1000) {
                                exitProcess(0)
                            }?.show();
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Error connecting to Google Play Services")
                                .setMessage("$result")
                                .setPositiveButton("OK") { dialog, which -> dialog.cancel() }
                                .show()
                        }
                    } else {
                        reinitializeGoogleApiClient()
                        return
                    }
                }
            }
        }
        mApiClient.reconnect()
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("mobile sendMessage: $path ${String(message)}")
        fun inner(node: Node) {
            Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                .setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Timber.i("Message sent: ${path} ${String(message)}")
                    } else {
                        Timber.e("mobile sendMessage callback: ${result}")
                    }
                }
        }
        if (!path.startsWith("/to-wear")) {
            Wearable.NodeApi.getLocalNode(mApiClient).setResultCallback { nodes ->
                Timber.i("mobile sendMessage local: ${nodes.node}")
                inner(nodes.node)
            }
        }
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("mobile sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                inner(node)
            }
        }
    }

    private fun sendPumpCommands(type: SendType, msgs: List<Message>) {
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


    // Message received from Wear or CommService
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("phone messageReceived: ${messageEvent.path}: ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-phone/start-comm" -> {
                when (String(messageEvent.data)) {
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
            }

            "/to-phone/set-pairing-code" -> {
                val pairingCodeText = String(messageEvent.data)
                PumpState.setPairingCode(applicationContext, pairingCodeText)
                Toast.makeText(applicationContext, "Set pairing code: $pairingCodeText", Toast.LENGTH_SHORT).show()
                if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE) {
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
                dataStore.setupDeviceName.value = String(messageEvent.data)
            }

            "/from-pump/pump-model" -> {
                dataStore.setupDeviceModel.value = String(messageEvent.data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA)
            }

            "/from-pump/initial-pump-connection" -> {
                dataStore.setupDeviceName.value = String(messageEvent.data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION)
            }

            "/from-pump/missing-pairing-code" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE)
            }

            "/from-pump/invalid-pairing-code" -> {
                Timber.w("invalid-pairing-code with code: ${PumpState.getPairingCode(applicationContext)}")
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE)
            }

            "/from-pump/pump-critical-error" -> {
                Timber.w("pump-critical-error: ${String(messageEvent.data)}")
                dataStore.pumpCriticalError.value = Pair(String(messageEvent.data), Instant.now())
            }

            "/from-pump/pump-connected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_CONNECTED)
                dataStore.setupDeviceName.value = String(messageEvent.data)
                dataStore.pumpConnected.value = true
                dataStore.pumpLastConnectionTimestamp.value = Instant.now()
            }

            // on explicit disconnection
            "/from-pump/pump-disconnected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                dataStore.setupDeviceModel.value = String(messageEvent.data)
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
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, false)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }
            "/from-pump/receive-cached-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, true)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }

            "/from-pump/debug-message-cache" -> {
                val processed = PumpMessageSerializer.fromDebugMessageCacheBytes(messageEvent.data)
                dataStore.debugMessageCache.value = processed
            }

            "/from-pump/debug-historylog-cache" -> {
                val processed = PumpMessageSerializer.fromDebugHistoryLogCacheBytes(messageEvent.data)
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
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                dataStore.batteryPercent.value = message.batteryPercent
            }
            is ControlIQIOBResponse -> {
                dataStore.iobUnits.value = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is ControlIQInfoAbstractResponse -> {
                dataStore.controlIQMode.value = when (message.currentUserModeType) {
                    ControlIQInfoAbstractResponse.UserModeType.SLEEP -> "Sleep"
                    ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> "Exercise"
                    else -> ""
                }
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
            }
            is LastBolusStatusAbstractResponse -> {
                dataStore.lastBolusStatus.value = "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(pumpTimeToLocalTz(message.timestampInstant))}"
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
                    HomeScreenMirrorResponse.BasalStatusIcon.BASAL -> "On"
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_BASAL -> "Zero"
                    HomeScreenMirrorResponse.BasalStatusIcon.TEMP_RATE -> "Temp Rate"
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_TEMP_RATE -> "Zero Temp Rate"
                    HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND -> "Suspended"
                    HomeScreenMirrorResponse.BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> "Suspended from BG"
                    HomeScreenMirrorResponse.BasalStatusIcon.INCREASE_BASAL -> "Increased"
                    HomeScreenMirrorResponse.BasalStatusIcon.ATTENUATED_BASAL -> "Reduced"
                    else -> ""
                }
            }
            is CurrentBasalStatusResponse -> {
                dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
            }
            is CGMStatusResponse -> {
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> "Active"
                    CGMStatusResponse.SessionState.SESSION_STOPPED -> "Stopped"
                    CGMStatusResponse.SessionState.SESSION_START_PENDING -> "Starting"
                    CGMStatusResponse.SessionState.SESSION_STOP_PENDING -> "Stopping"
                    else -> "Unknown"
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
            is BolusCalcDataSnapshotResponse -> {
                if (!cached) {
                    dataStore.bolusCalcDataSnapshot.value = message
                }
            }
            is LastBGResponse -> {
                dataStore.bolusCalcLastBG.value = message
            }
            is GlobalMaxBolusSettingsResponse -> {
                dataStore.maxBolusAmount.value = message.maxBolus
            }
            is HistoryLogStatusResponse -> {
                dataStore.historyLogStatus.value = message
            }
            // TODO: bolus
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
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
