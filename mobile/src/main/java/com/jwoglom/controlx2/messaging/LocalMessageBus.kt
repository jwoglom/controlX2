package com.jwoglom.controlx2.messaging

import android.content.Context
import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local in-process implementation of MessageBus for phone-only mode.
 * Uses an internal event bus to deliver messages between components
 * within the same application without requiring Wear OS libraries.
 */
class LocalMessageBus(private val context: Context) : MessageBus {
    private val listeners = CopyOnWriteArrayList<MessageListener>()
    private val localNode = MessageNode(
        id = "local-phone",
        displayName = "Phone",
        isLocal = true
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(listOf(localNode))
    )

    init {
        Timber.d("LocalMessageBus initialized for phone-only mode")
    }

    override fun sendMessage(path: String, data: ByteArray) {
        Timber.i("LocalMessageBus.sendMessage: $path ${String(data)}")

        // Deliver message to all listeners immediately (in-process)
        listeners.forEach { listener ->
            try {
                listener.onMessageReceived(path, data, localNode.id)
            } catch (e: Exception) {
                Timber.e(e, "Error delivering message to listener: $path")
            }
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        Timber.d("LocalMessageBus.addMessageListener: $listener")
        listeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        Timber.d("LocalMessageBus.removeMessageListener: $listener")
        listeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> {
        // In phone-only mode, we only have the local node
        return listOf(localNode)
    }

    override fun observeConnectionState(): Flow<ConnectionState> {
        return _connectionState.asStateFlow()
    }

    override fun close() {
        Timber.d("LocalMessageBus.close()")
        listeners.clear()
    }
}
