package com.jwoglom.wearx2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse.SessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse.TransmitterBatteryStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse.ApControlStateIcon
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse.CGMAlertIcon
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.wearx2.databinding.ActivityMainBinding
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.WearApp
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
import com.jwoglom.wearx2.shared.util.DebugTree
import com.jwoglom.wearx2.util.shortTime
import com.jwoglom.wearx2.util.shortTimeAgo
import com.jwoglom.wearx2.util.twoDecimalPlaces1000Unit
import timber.log.Timber

var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : ComponentActivity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    internal lateinit var navController: NavHostController
    private lateinit var binding: ActivityMainBinding
    private lateinit var mApiClient: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())


        setContent {
            navController = rememberSwipeDismissableNavController()

            val sendPumpCommandLocal: (Message) -> Unit = { msg ->
                this.sendPumpCommand(msg)
            }

            WearApp(
                swipeDismissableNavController = navController,
                sendPumpCommand = sendPumpCommandLocal,
            )
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        Timber.d("create: mApiClient: $mApiClient")
        mApiClient.connect()


        // Start PhoneCommService
        Intent(this, PhoneCommService::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("resume: mApiClient: $mApiClient")
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }

        sendMessage("/to-phone/is-pump-connected", "".toByteArray())
    }

    override fun onConnected(bundle: Bundle?) {
        Timber.i("wear onConnected: $bundle")
        sendMessage("/to-phone/connected", "wear_launched".toByteArray())
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.w("wear connectionSuspended: $id")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.w("wear connectionFailed $result")
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

    private fun sendPumpCommand(msg: Message) {
        sendMessage("/to-pump/command", PumpMessageSerializer.toBytes(msg))
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("wear sendMessage: $path ${String(message)}")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("wear sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                    .setResultCallback { result ->
                        if (result.status.isSuccess) {
                            Timber.i("Wear message sent: ${path} ${String(message)}")
                        } else {
                            Timber.w("wear sendMessage callback: ${result.status}")
                        }
                    }
            }
        }
    }

    private fun inWaitingState(): Boolean {
        return when (navController.currentDestination?.route) {
            Screen.WaitingForPhone.route,
            Screen.WaitingToFindPump.route,
            Screen.ConnectingToPump.route,
            Screen.PumpDisconnectedReconnecting.route -> true
            else -> false
        }
    }

    private fun onPumpMessageReceived(message: Message) {
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                dataStore.batteryPercent.value = message.batteryPercent
            }
            is ControlIQIOBResponse -> {
                dataStore.iobUnits.value = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
            }
            is LastBolusStatusAbstractResponse -> {
                dataStore.lastBolusStatus.value = "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(message.timestampInstant)}"
            }
            is HomeScreenMirrorResponse -> {
                dataStore.controlIQStatus.value = when (message.apControlStateIcon) {
                    ApControlStateIcon.STATE_GRAY -> "On"
                    ApControlStateIcon.STATE_GRAY_RED_BIQ_CIQ_BASAL_SUSPENDED -> "Suspended"
                    ApControlStateIcon.STATE_GRAY_BLUE_CIQ_INCREASE_BASAL -> "Increase"
                    ApControlStateIcon.STATE_GRAY_ORANGE_CIQ_ATTENUATION_BASAL -> "Reduced"
                    else -> "Off"
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
                    else -> ""
                }
                dataStore.cgmDeltaArrow.value = message.cgmTrendIcon.arrow()
            }
            is CurrentBasalStatusResponse -> {
                dataStore.basalStatus.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
            }
            is CGMStatusResponse -> {
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    SessionState.SESSION_STOPPED -> "Stopped"
                    SessionState.SESSION_START_PENDING -> "Starting"
                    SessionState.SESSION_ACTIVE -> "Active"
                    SessionState.SESSION_STOP_PENDING -> "Stopping"
                    else -> "Unknown"
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
            }
            is BolusCalcDataSnapshotResponse -> {
                dataStore.bolusCalcDataSnapshot.value = message
            }
            is LastBGResponse -> {
                dataStore.bolusCalcLastBG.value = message
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("wear onMessageReceived: ${messageEvent.path} ${String(messageEvent.data)}")
        var text = ""
        when (messageEvent.path) {
            "/to-wear/connected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigate(Screen.WaitingToFindPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "".toByteArray())
                }
            }
            "/from-pump/pump-model" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigate(Screen.ConnectingToPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "".toByteArray())
                }
                text = "Found model ${String(messageEvent.data)}"
            }
            "/from-pump/waiting-for-pairing-code" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigate(Screen.ConnectingToPump.route)
                    }
                    sendMessage("/to-phone/is-pump-connected", "".toByteArray())
                }
                text = "Waiting for Pairing Code"
            }
            "/from-pump/pump-connected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigate(Screen.Landing.route)
                    }
                }
                text = "Connected to ${String(messageEvent.data)}"
            }
            "/from-pump/pump-disconnected" -> {
                if (inWaitingState()) {
                    runOnUiThread {
                        navController.navigate(Screen.PumpDisconnectedReconnecting.route)
                    }
                }
                text = "Disconnected from ${String(messageEvent.data)}"
            }
            "/from-pump/pump-critical-error" -> {
                text = "Error: ${String(messageEvent.data)}"
            }
            "/from-pump/receive-qualifying-event" -> {
                text = "Event: ${PumpQualifyingEventsSerializer.fromBytes(messageEvent.data)}"
            }
            "/from-pump/receive-message" -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
                text = "${pumpMessage}"
                onPumpMessageReceived(pumpMessage)
            }
            else -> text = "? ${String(messageEvent.data)}"
        }
        Timber.i("wear text: $text")
    }
}