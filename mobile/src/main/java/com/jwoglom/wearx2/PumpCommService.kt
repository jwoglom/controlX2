package com.jwoglom.wearx2

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jwoglom.wearx2.shared.WearToPhoneCommMessages

class PumpCommService : Service() {

    private var wearCommHandler: Handler ?= null;
    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null
    private var tandemPump: WearX2TandemPump? = null

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                WearToPhoneCommMessages.INIT_PUMP_COMM.ordinal -> {
                    tandemPump = WearX2TandemPump(applicationContext)
                }
            }
        }
    }

    override fun onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread("PumpCommServiceThread", THREAD_PRIORITY_BACKGROUND).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)

            //var nodes = Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes)
//            nodes.forEach { node ->
//                Wearable.getMessageClient(MainActivity).sendMessage(node.id, "/comm", "".encodeToByteArray()) }
        }

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        serviceHandler?.obtainMessage()?.also { msg ->
            msg.what = WearToPhoneCommMessages.INIT_PUMP_COMM.ordinal
            msg.arg1 = startId
            serviceHandler?.sendMessage(msg)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
    }
}