package com.jwoglom.wearx2

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.wearx2.databinding.ActivityMainBinding
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.PumpQualifyingEventsSerializer
import timber.log.Timber

class MainActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private val WEAR_MESSAGE_PREFIX = "/to-wear"

    private lateinit var binding: ActivityMainBinding

    private lateinit var mApiClient: GoogleApiClient

    private lateinit var text: TextView
    private lateinit var getIOBButton: Button
    private lateinit var bolusButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        text = requireViewById<TextView>(R.id.text)
        getIOBButton = requireViewById<Button>(R.id.getIOBButton)
        getIOBButton.setOnClickListener {
            sendPumpCommand(ControlIQIOBRequest())
        }
        bolusButton = requireViewById<Button>(R.id.bolusButton)
        bolusButton.setOnClickListener {
            startActivity(Intent(this, BolusActivity::class.java))
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        Timber.d("create: mApiClient: $mApiClient")
        mApiClient.connect()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("resume: mApiClient: $mApiClient")
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }
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

    private inner class PhoneCommBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent!!.getByteArrayExtra("data")
            text.text = data.toString()
        }
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
                        Timber.d("wear sendMessage callback: ${result}")
                        if (result.status.isSuccess) {
                            Timber.i("Wear message sent: ${path} ${String(message)}")
                        }
                    }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        runOnUiThread {
            Timber.i("wear onMessageReceived: ${messageEvent.path} ${String(messageEvent.data)}")
            if (messageEvent.path.startsWith(WEAR_MESSAGE_PREFIX)) {
                text.text = String(messageEvent.data)
            } else if (messageEvent.path.startsWith("/from-pump")) {
                when (messageEvent.path) {
                    "/from-pump/pump-model" -> {
                        text.text = "Found model ${String(messageEvent.data)}"
                    }
                    "/from-pump/waiting-for-pairing-code" -> {
                        text.text = "Waiting for Pairing Code"
                    }
                    "/from-pump/pump-connected" -> {
                        text.text = "Connected to ${String(messageEvent.data)}"
                    }
                    "/from-pump/pump-disconnected" -> {
                        text.text = "Disconnected from ${String(messageEvent.data)}"
                    }
                    "/from-pump/pump-critical-error" -> {
                        text.text = "Error: ${String(messageEvent.data)}"
                    }
                    "/from-pump/receive-qualifying-event" -> {
                        text.text = "Event: ${PumpQualifyingEventsSerializer.fromBytes(messageEvent.data)}"
                    }
                    "/from-pump/receive-message" -> {
                        text.text = "${PumpMessageSerializer.fromBytes(messageEvent.data)}"
                    }
                    else -> text.text = "? ${String(messageEvent.data)}"
                }
            }
        }
    }
}