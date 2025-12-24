package com.jwoglom.controlx2

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.compositionLocalOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.WearApp
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.ui.resetBolusDataStoreState
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.controlx2.util.UpdateComplication
import com.jwoglom.controlx2.util.WearX2Complication
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse.SessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse.TransmitterBatteryStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse.UserModeType
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse.ApControlStateIcon
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse.BasalStatusIcon
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse.CGMAlertIcon
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    internal lateinit var navController: NavHostController
    private lateinit var messageClient: MessageClient

    private lateinit var initialRoute: String

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(R.style.MainTheme) // clean up from splash screen icon
        setupTimber("WA", context = this)

        if (intent != null) {
            initialRoute = intent.getStringExtra("route") ?: Screen.Landing.route
        }

        Timber.i("activity onCreate initialRoute=$initialRoute savedInstanceState=$savedInstanceState")

        setContent {
            navController = rememberSwipeDismissableNavController()

            val sendPumpCommands: (SendType, List<Message>) -> Unit = { type, msg ->
                this.sendPumpCommands(type, msg)
            }

            val sendPhoneConnectionCheck: () -> Unit = {
                this.sendMessage("/to-phone/is-pump-connected", "phone_connection_check".toByteArray())
            }

            val sendPhoneBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse) -> Unit = { bolusId, params, unitBreakdown, dataSnapshot, timeSinceReset ->
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
                    Timber.i("sendPhoneBolusRequest: sending remoteBgRequest=$remoteBgRequest")
                    preCommands.add(remoteBgRequest)
                }

                if (numCarbs > 0 && timeSinceReset.pumpTimeSecondsSinceReset > 0) {
                    val remoteCarbRequest = RemoteCarbEntryRequest(
                        numCarbs,
                        timeSinceReset.pumpTimeSecondsSinceReset,
                        bolusId
                    )
                    Timber.i("sendPhoneBolusRequest: sending remoteCarbRequest=$remoteCarbRequest")
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

                Timber.i("sendPhoneBolusRequest: numUnits=$numUnits numCarbs=$numCarbs bgValue=$bgValue foodVolume=$foodVolume corrVolume=$corrVolume iobUnits=$iobUnits: bolusRequest=$bolusRequest preCommands=$preCommands")
                this.sendMessage("/to-phone/bolus-request-wear", PumpMessageSerializer.toBytes(bolusRequest))
            }

            val sendPhoneBolusCancel: () -> Unit = {
                this.sendMessage("/to-phone/bolus-cancel", "".toByteArray())
            }

            val sendPhoneOpenActivity: () -> Unit = {
                val nodeClient = Wearable.getNodeClient(this)
                val phoneIntent = Intent(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setData(Uri.parse("com_jwoglom_controlx2://controlx2/"))
                nodeClient.connectedNodes
                    .addOnSuccessListener { nodes ->
                        Timber.d("wear openActivity nodes: ${nodes}")
                        nodes.forEach { node ->
                            RemoteActivityHelper(this)
                                .startRemoteActivity(
                                    targetIntent = phoneIntent,
                                    targetNodeId = node.id
                                )
                        }
                    }
            }

            val sendPhoneOpenTconnect: () -> Unit = {
                val helper = RemoteActivityHelper(application, Dispatchers.IO.asExecutor())
                helper.startRemoteActivity(Intent("com.tandemdiabetes.tconnect"))
            }

            val sendPhoneCommand: (String) -> Unit = {cmd ->
                this.sendMessage("/to-phone/$cmd", "".toByteArray())
            }

            WearApp(
                navController = navController,
                sendPumpCommands = sendPumpCommands,
                sendPhoneConnectionCheck = sendPhoneConnectionCheck,
                sendPhoneBolusRequest = sendPhoneBolusRequest,
                sendPhoneBolusCancel = sendPhoneBolusCancel,
                sendPhoneCommand = sendPhoneCommand,
                sendPhoneOpenActivity = sendPhoneOpenActivity,
            )
        }

        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        startPhoneCommService()
        onConnected(savedInstanceState)
    }

    private fun startPhoneCommService() {
        Timber.i("starting PhoneCommService")
        // Start CommService
        val intent = Intent(applicationContext, PhoneCommService::class.java)

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
            Timber.i("PhoneCommService onServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("PhoneCommService onServiceDisconnected")
        }
    }

    override fun onResume() {
        super.onResume()
        messageClient.addListener(this)

        sendMessage("/to-phone/is-pump-connected", "onResume".toByteArray())
    }

    override fun onNewIntent(intent: Intent) {
        var newRoute = initialRoute
        if (intent != null) {
            newRoute = intent.getStringExtra("route") ?: Screen.Landing.route
        }

        Timber.i("activity onNewIntent newRoute: $newRoute initialRoute: $initialRoute")
        if (newRoute != initialRoute) {
            initialRoute = newRoute
            if (!inWaitingState()) {
                runOnUiThread {
                    navController.navigateClearBackStack(newRoute)
                }
            }
        }
        super.onNewIntent(intent)
    }

    fun onConnected(bundle: Bundle?) {
        Timber.i("wear onConnected: $bundle")
        sendMessage("/to-phone/connected", "wear_launched".toByteArray())
        sendMessage("/to-phone/is-pump-connected", "onConnected".toByteArray())
        messageClient.addListener(this)
    }

    override fun onStop() {
        super.onStop()
        messageClient.removeListener(this)
    }

    override fun onDestroy() {
        messageClient.removeListener(this)
        super.onDestroy()
    }

    private fun sendPumpCommand(msg: Message) {
        sendMessage("/to-pump/command", PumpMessageSerializer.toBytes(msg))
    }

    private fun sendPumpCommands(type: SendType, msgs: List<Message>) {
        sendMessage("/to-pump/${type.slug}", PumpMessageSerializer.toBulkBytes(msgs))
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("wear sendMessage: $path ${String(message)}")
        val nodeClient = Wearable.getNodeClient(this)


        fun inner(node: Node) {
            messageClient.sendMessage(node.id, path, message)
                .addOnSuccessListener {
                    Timber.i("Wear message sent: ${path} ${String(message)} to ${node.displayName}")
                }
                .addOnFailureListener {
                    Timber.w("wear sendMessage callback: ${it}")
                }
        }
        if (path.startsWith("/to-wear")) {
            nodeClient.localNode
                .addOnSuccessListener { localNode ->
                    Timber.d("wear sendMessage local: ${localNode}")
                    inner(localNode)
                }
        }
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                Timber.d("wear sendMessage nodes: ${nodes}")
                nodes.forEach { node ->
                    inner(node)
                }
            }
    }

    private fun inWaitingState(): Boolean {
        return when (navController.currentDestination?.route) {
            Screen.WaitingForPhone.route,
            Screen.WaitingToFindPump.route,
            Screen.ConnectingToPump.route,
            Screen.PairingToPump.route,
            Screen.MissingPairingCode.route,
            Screen.PumpDisconnectedReconnecting.route -> true
            else -> false
        }
    }

    private fun onPumpMessageReceived(message: Message, cached: Boolean) {
        if (IDPManager.isIDPManagerResponse(message)) {
            synchronized(dataStore.idpManager) {
                // processMessage returns an instance of itself: ensures that watchers get the updated values
                val isComplete = dataStore.idpManager.value?.isComplete == true
                dataStore.idpManager.value = (dataStore.idpManager.value ?: IDPManager()).processMessage(message)
                // re-create self when complete to trigger state update via object change
                if (isComplete != dataStore.idpManager.value?.isComplete) {
                    dataStore.idpManager.value = IDPManager(dataStore.idpManager.value)
                }
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
                    UserModeType.STANDARD -> UserMode.NONE
                    UserModeType.SLEEP -> UserMode.SLEEP
                    UserModeType.EXERCISE -> UserMode.EXERCISE
                    else -> UserMode.UNKNOWN
                }
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
            }
            is LastBolusStatusAbstractResponse -> {
                dataStore.lastBolusStatus.value = "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(pumpTimeToLocalTz(message.timestampInstant))}"
                dataStore.lastBolusStatusResponse.value = message
            }
            is HomeScreenMirrorResponse -> {
                dataStore.controlIQStatus.value = when (message.apControlStateIcon) {
                    ApControlStateIcon.STATE_GRAY -> "On"
                    ApControlStateIcon.STATE_GRAY_RED_BIQ_CIQ_BASAL_SUSPENDED -> "Suspended"
                    ApControlStateIcon.STATE_GRAY_BLUE_CIQ_INCREASE_BASAL -> "Increase"
                    ApControlStateIcon.STATE_GRAY_ORANGE_CIQ_ATTENUATION_BASAL -> "Reduced"
                    else -> "CIQ Off"
                }
                dataStore.cgmStatusText.value = when (message.cgmAlertIcon) {
                    CGMAlertIcon.STARTUP_1, CGMAlertIcon.STARTUP_2, CGMAlertIcon.STARTUP_3, CGMAlertIcon.STARTUP_4 -> "Starting up"
                    CGMAlertIcon.CALIBRATE, CGMAlertIcon.STARTUP_CALIBRATE, CGMAlertIcon.CHECKMARK_BLOOD_DROP -> "Calibration Needed"
                    CGMAlertIcon.ERROR_HIGH_WEDGE, CGMAlertIcon.ERROR_LOW_WEDGE -> "Error"
                    CGMAlertIcon.REPLACE_SENSOR -> "Replace Sensor"
                    CGMAlertIcon.REPLACE_TRANSMITTER -> "Replace Transmitter"
                    CGMAlertIcon.OUT_OF_RANGE -> "Out Of Range"
                    CGMAlertIcon.FAILED_SENSOR -> "Sensor Failed"
                    CGMAlertIcon.TRIPLE_DASHES -> "---"
                    else -> ""
                }
                dataStore.cgmHighLowState.value = when (message.cgmAlertIcon) {
                    CGMAlertIcon.LOW -> "LOW"
                    CGMAlertIcon.HIGH -> "HIGH"
                    else -> "IN_RANGE"
                }
                dataStore.cgmDeltaArrow.value = message.cgmTrendIcon.arrow()
                dataStore.basalStatus.value = when (message.basalStatusIcon) {
                    BasalStatusIcon.BASAL -> BasalStatus.ON
                    BasalStatusIcon.ZERO_BASAL -> BasalStatus.ZERO
                    BasalStatusIcon.TEMP_RATE -> BasalStatus.TEMP_RATE
                    BasalStatusIcon.ZERO_TEMP_RATE -> BasalStatus.ZERO_TEMP_RATE
                    BasalStatusIcon.SUSPEND -> BasalStatus.PUMP_SUSPENDED
                    BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> BasalStatus.BASALIQ_SUSPENDED
                    BasalStatusIcon.INCREASE_BASAL -> BasalStatus.CONTROLIQ_INCREASED
                    BasalStatusIcon.ATTENUATED_BASAL -> BasalStatus.CONTROLIQ_REDUCED
                    else -> BasalStatus.UNKNOWN
                }
                dataStore.cartridgeRemainingEstimate.value = message.remainingInsulinPlusIcon
            }
            is CurrentBasalStatusResponse -> {
                dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
            }
            is CGMStatusResponse -> {
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    SessionState.SESSION_ACTIVE -> "Active"
                    SessionState.SESSION_STOPPED -> "Stopped"
                    SessionState.SESSION_START_PENDING -> "Starting"
                    SessionState.SESSION_STOP_PENDING -> "Stopping"
                    else -> "Unknown"
                }
                dataStore.cgmSessionExpireRelative.value = when (message.sessionState) {
                    SessionState.SESSION_ACTIVE -> shortTimeAgo(
                        pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                            .plus(10, ChronoUnit.DAYS),
                        suffix = "left")
                    else -> ""
                }
                dataStore.cgmSessionExpireExact.value = when (message.sessionState) {
                    SessionState.SESSION_ACTIVE -> shortTime(
                        pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                            .plus(10, ChronoUnit.DAYS))
                    else -> ""
                }
                dataStore.cgmTransmitterStatus.value = when (message.transmitterBatteryStatus) {
                    TransmitterBatteryStatus.ERROR -> "Error"
                    TransmitterBatteryStatus.EXPIRED -> "Expired"
                    TransmitterBatteryStatus.OK -> "OK"
                    TransmitterBatteryStatus.OUT_OF_RANGE -> "OOR"
                    else -> "Unknown"
                }
            }
            is CurrentEGVGuiDataResponse -> {
                dataStore.cgmReading.value = message.cgmReading
                dataStore.cgmDelta.value = message.trendRate
                UpdateComplication(applicationContext, WearX2Complication.CGM_READING)
            }
            is BolusCalcDataSnapshotResponse -> {
                if (!cached) {
                    dataStore.bolusCalcDataSnapshot.value = message
                }
                dataStore.maxCarbAmount.value = (InsulinUnit.from1000To1(message.maxBolusAmount.toLong()) * InsulinUnit.from1000To1(message.carbRatio)).roundToInt()
            }
            is LastBGResponse -> {
                dataStore.bolusCalcLastBG.value = message
            }
            is GlobalMaxBolusSettingsResponse -> {
                dataStore.maxBolusAmount.value = InsulinUnit.from1000To1(message.maxBolus.toLong())
            }
            is BolusPermissionResponse -> {
                dataStore.bolusPermissionResponse.value = message
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
        }
    }

    private fun onPumpQualifyingEventReceived(events: Set<QualifyingEvent>) {
        events.forEach { event ->
            when (event) {
                else -> {}
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("wear onMessageReceived: ${messageEvent.path} ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-wear/connected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(Screen.WaitingToFindPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "on-phone-connected".toByteArray())
                }
                dataStore.connectionStatus.value = "Waiting to find pump"
            }
            "/to-wear/bolus-min-notify-threshold" -> {
                dataStore.bolusMinNotifyThreshold.value = String(messageEvent.data).toDoubleOrNull()
            }
            "/to-wear/initiate-confirmed-bolus" -> {
                if (inWaitingState()) {
                    Timber.e("in invalid state for initiate-confirmed-bolus")
                    runOnUiThread {
                        navController.navigate(Screen.BolusBlocked.route)
                    }
                    return
                }

                if (navController.currentDestination?.route != Screen.Bolus.route) {
                    Timber.e("in non-bolus state for initiate-confirmed-bolus")
                    runOnUiThread {
                        navController.navigate(Screen.BolusBlocked.route)
                    }
                    return
                }

                val dataStoreUnits = dataStore.bolusFinalParameters.value?.units
                val confirmedBolus = InitiateConfirmedBolusSerializer.fromBytes("IGNORED_BY_WEAR", messageEvent.data)
                val initiateBolusRequest = confirmedBolus.right as InitiateBolusRequest
                if (initiateBolusRequest.totalVolume != InsulinUnit.from1To1000(dataStoreUnits)) {
                    Timber.e("blocked bolus with different volume amount $initiateBolusRequest vs $dataStoreUnits")
                    runOnUiThread {
                        navController.navigate(Screen.BolusBlocked.route)
                    }
                    return
                }

                Timber.i("sending initiate-confirmed-bolus from wearable to phone")
                sendMessage("/to-phone/initiate-confirmed-bolus", messageEvent.data)
            }
            "/to-wear/blocked-bolus-signature" -> {
                Timber.w("blocked bolus signature")
                runOnUiThread {
                    navController.navigate(Screen.BolusBlocked.route)
                }
            }
            "/to-wear/bolus-not-enabled" -> {
                Timber.w("bolus not enabled")
                runOnUiThread {
                    navController.navigate(Screen.BolusNotEnabled.route)
                }
            }
            "/to-wear/bolus-rejected" -> {
                Timber.w("bolus rejected")
                runOnUiThread {
                    resetBolusDataStoreState(dataStore)
                    navController.navigate(Screen.BolusRejectedOnPhone.route)
                }
            }
            "/from-pump/pump-model" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(Screen.ConnectingToPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "on-pump-model".toByteArray())
                }
                dataStore.connectionStatus.value = "Connecting to pump"
            }
            "/from-pump/entered-pairing-code" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(Screen.PairingToPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "on-entered-pairing-code".toByteArray())
                }
                dataStore.connectionStatus.value = "Pairing to pump"
            }
            "/from-pump/missing-pairing-code" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(Screen.MissingPairingCode.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "on-missing-pairing-code".toByteArray())
                }
                dataStore.connectionStatus.value = "Missing pairing code"
            }
            "/from-pump/pump-connected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        setTurnScreenOn(true)
                        navController.navigateClearBackStack(initialRoute)
                    }
                }
                dataStore.connectionStatus.value = ""
            }
            "/from-pump/pump-disconnected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(Screen.PumpDisconnectedReconnecting.route)
                    }
                } else {
                    if (dataStore.connectionStatus.value == "") {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Disconnected", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
                dataStore.connectionStatus.value = "Reconnecting"
            }
            "/from-pump/pump-critical-error" -> {
                dataStore.connectionStatus.value = "Error: ${String(messageEvent.data)}"
            }
            "/from-pump/receive-qualifying-event" -> {
                val pumpEvents = PumpQualifyingEventsSerializer.fromBytes(messageEvent.data)
                onPumpQualifyingEventReceived(pumpEvents)
            }
            "/from-pump/receive-message" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(initialRoute)
                    }
                }
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, false)
            }
            "/from-pump/receive-cached-message" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigateClearBackStack(initialRoute)
                    }
                }
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                onPumpMessageReceived(pumpMessage, true)
            }
            else -> {
                Timber.w("wear activity unhandled receive: ${messageEvent.path} ${String(messageEvent.data)}")
            }
        }
    }
}

private fun NavController.navigateClearBackStack(route: String) {
    navigate(route)
    currentBackStackEntry?.id?.let { clearBackStack(it) }
}
