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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jwoglom.wearx2.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var phoneCommBroadcastReceiver = PhoneCommBroadcastReceiver()

    private lateinit var text: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        text = requireViewById<TextView>(R.id.text)
        button = requireViewById<Button>(R.id.button)
        button.setOnClickListener { SendMessage("/phone/send-data", "abcd").start() }

        LocalBroadcastManager.getInstance(this).registerReceiver(phoneCommBroadcastReceiver, IntentFilter("com.jwoglom.wearx2.PhoneCommService"))
    }

    private inner class PhoneCommBroadcastReceiver : BroadcastReceiver() {
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
}