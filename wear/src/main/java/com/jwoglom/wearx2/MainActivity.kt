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
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.util.timber.DebugTree
import com.jwoglom.wearx2.databinding.ActivityMainBinding
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.WearApp
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
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
                dataStore.iobUnits.value = InsulinUnit.from1000To1(message.mudaliarIOB)
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
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