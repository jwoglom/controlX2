package com.jwoglom.wearx2

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jwoglom.wearx2.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private val WEAR_MESSAGE_PREFIX = "/to-wear"

    private lateinit var binding: ActivityMainBinding

    private lateinit var mApiClient: GoogleApiClient

    private lateinit var text: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        text = requireViewById<TextView>(R.id.text)
        button = requireViewById<Button>(R.id.button)
        button.setOnClickListener { sendMessage("/to-phone/message", "from_wear") }

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
        sendMessage("/to-phone/connected", "wear_launched")
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

    private fun sendMessage(path: String, message: String) {
//        Thread {
//            Timber.i("wear sendMessage: $path $message")
//            val nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await().nodes
//            Timber.i("wear sendMessage nodes: $nodes")
//            nodes.forEach { node ->
//                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message.toByteArray()).await()
//            }
//        }.start()
        Timber.i("wear sendMessage: $path $message")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("wear sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message.toByteArray())
                    .setResultCallback { result ->
                        Timber.d("wear sendMessage callback: ${result}")
                        if (result.status.isSuccess) {
                            Timber.i("Wear message sent: ${path} ${message}")
                        }
                    }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        runOnUiThread {
            Timber.i("wear onMessageReceived: $messageEvent: ${messageEvent.path} ${messageEvent.data}")
            if (messageEvent.path.startsWith(WEAR_MESSAGE_PREFIX)) {
                text.text = String(messageEvent.data)
            }
        }
    }
}