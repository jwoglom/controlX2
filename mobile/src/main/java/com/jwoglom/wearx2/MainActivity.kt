package com.jwoglom.wearx2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber


private const val SEND_TO_WEAR_PATH = "/send-to-wear"
private const val SEND_TO_PHONE_PATH = "/send-to-phone"

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private lateinit var mApiClient: GoogleApiClient

    private lateinit var text: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)

        text = requireViewById<TextView>(R.id.text)
        button = requireViewById<Button>(R.id.button);
        button.setOnClickListener {
            sendMessage("/to-wear/send-data", "fromphone")
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        mApiClient.connect()

        // Start PumpCommService
//        Intent(this, PumpCommService::class.java).also { intent ->
//            startService(intent)
//        }
    }

    override fun onResume() {
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }
        super.onResume()
    }

    private fun sendMessage(path: String, message: String) {
        Timber.i("mobile sendMessage: $path $message")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("mobile sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message.toByteArray())
                    .setResultCallback { result ->
                        Timber.d("sendMessage callback: ${result}")
                        if (result.status.isSuccess) {
                            Timber.i("Message sent: ${path} ${message}")
                        }
                    }
            }
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Timber.i("mobile onConnected $bundle")
        sendMessage("/to-wear/connected", "phone_launched")
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("mobile onConnectionSuspended: $id")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("mobile onConnectionFailed: $result")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("phone messageReceived: ${messageEvent}: ${messageEvent.path}: ${messageEvent.data}")
        runOnUiThread {
            if (messageEvent.path.startsWith("/to-phone")) {
                text.text = String(messageEvent.data)
            }
        }
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
}