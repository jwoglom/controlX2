package com.jwoglom.controlx2.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cross-process implementation of MessageBus using Android BroadcastReceiver.
 * Enables communication between different processes on the same device
 * (e.g., foreground app and background service).
 */
class BroadcastMessageBus(private val context: Context) : MessageBus {
    private val listeners = CopyOnWriteArrayList<MessageListener>()
    private val localNode = MessageNode(
        id = "local-phone-broadcast",
        displayName = "Phone (Broadcast)",
        isLocal = true
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(listOf(localNode))
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_MESSAGE) return

            val path = intent.getStringExtra(EXTRA_PATH) ?: return
            val data = intent.getByteArrayExtra(EXTRA_DATA) ?: ByteArray(0)
            val sourceNodeId = intent.getStringExtra(EXTRA_SOURCE_NODE_ID) ?: localNode.id

            Timber.i("BroadcastMessageBus.onReceive: $path from $sourceNodeId")

            // Deliver to all listeners
            listeners.forEach { listener ->
                try {
                    listener.onMessageReceived(path, data, sourceNodeId)
                } catch (e: Exception) {
                    Timber.e(e, "Error delivering broadcast message to listener: $path")
                }
            }
        }
    }

    init {
        Timber.d("BroadcastMessageBus initialized for cross-process communication")
        // Register receiver for messages from other processes
        val filter = IntentFilter(ACTION_MESSAGE)
        try {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            Timber.d("BroadcastMessageBus receiver registered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register BroadcastMessageBus receiver")
        }
    }

    override fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender) {
        Timber.i("BroadcastMessageBus.sendMessage: $path (sender: $sender)")

        try {
            val intent = Intent(ACTION_MESSAGE).apply {
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_SOURCE_NODE_ID, localNode.id)
                putExtra(EXTRA_SENDER, sender.name)
                // Make the broadcast explicit to the same app package
                setPackage(context.packageName)
            }

            context.applicationContext.sendBroadcast(intent)
            Timber.d("BroadcastMessageBus message broadcasted: $path")
        } catch (e: Exception) {
            Timber.e(e, "Failed to broadcast message: $path")
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        Timber.d("BroadcastMessageBus.addMessageListener: $listener")
        listeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        Timber.d("BroadcastMessageBus.removeMessageListener: $listener")
        listeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> {
        // In cross-process mode, we always have the local node
        return listOf(localNode)
    }

    override fun observeConnectionState(): Flow<ConnectionState> {
        return _connectionState.asStateFlow()
    }

    override fun close() {
        Timber.d("BroadcastMessageBus.close()")
        try {
            context.applicationContext.unregisterReceiver(receiver)
            Timber.d("BroadcastMessageBus receiver unregistered")
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering BroadcastMessageBus receiver")
        }
        listeners.clear()
    }

    companion object {
        private const val ACTION_MESSAGE = "com.jwoglom.controlx2.MESSAGE"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_SOURCE_NODE_ID = "sourceNodeId"
        private const val EXTRA_SENDER = "sender"
    }
}
