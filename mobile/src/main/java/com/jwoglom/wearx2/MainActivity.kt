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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber


private const val SEND_TO_WEAR_PATH = "/send-to-wear"
private const val SEND_TO_PHONE_PATH = "/send-to-phone"

class MainActivity : AppCompatActivity() {
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    //private val wearableCommService by lazy { WearableCommService()}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wearCommBroadcastReceiver = WearCommBroadcastReceiver()

    private lateinit var text: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)

        text = requireViewById<TextView>(R.id.text)
        button = requireViewById<Button>(R.id.button);
        button.setOnClickListener {
            // startWearableActivity()
            SendMessage("/wear/send-data", "fromphone").start()
        }

        // Start PumpCommService
        Intent(this, PumpCommService::class.java).also { intent ->
            startService(intent)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(wearCommBroadcastReceiver, IntentFilter("com.jwoglom.wearx2.PhoneCommService"))

        Timber.d("onCreate")
    }

    private inner class WearCommBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent!!.getByteArrayExtra("data")
            text.text = data.toString()
        }
    }

    private inner class SendMessage(val path: String, val data: String) : Thread() {
        override fun run() {
            Timber.d("sendMessage")
            val nodeList = Wearable.getNodeClient(applicationContext).connectedNodes
            Tasks.await(nodeList).forEach { node ->
                Timber.d("node: $node")
                val result = Tasks.await(
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, path, data.toByteArray())
                )

                Timber.d("sendMessage result: $result")
            }
        }
    }

    private fun startWearableActivity() {
        scope.launch {
            val capability = Tasks.await(
                capabilityClient.getCapability("wear", CapabilityClient.FILTER_REACHABLE))
            capability.nodes.map { node ->
                messageClient.sendMessage(
                    node.id,
                    "/start-wear-activity",
                    "datadatadata".toByteArray()
                )
            }
        }
    }
}