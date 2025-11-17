package com.jwoglom.controlx2.messaging

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wear OS implementation of MessageBus using Google Wearable Data Layer API.
 * Wraps MessageClient for communication between phone and watch.
 */
class WearMessageBus(private val context: Context) : MessageBus, MessageClient.OnMessageReceivedListener {
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val listeners = CopyOnWriteArrayList<MessageListener>()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    init {
        Timber.d("WearMessageBus initialized with Wearable Data Layer")
        messageClient.addListener(this)
        updateConnectionState()
    }

    override fun sendMessage(path: String, data: ByteArray) {
        Timber.i("WearMessageBus.sendMessage: $path ${String(data)}")

        fun sendToNode(node: Node) {
            messageClient.sendMessage(node.id, path, data)
                .addOnSuccessListener {
                    Timber.d("WearMessageBus message sent: $path to ${node.displayName}")
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "WearMessageBus sendMessage failed: $path to ${node.displayName}")
                }
        }

        // Send to local node if path doesn't start with /to-wear
        if (!path.startsWith("/to-wear")) {
            nodeClient.localNode.addOnSuccessListener { localNode ->
                sendToNode(localNode)
            }
        }

        // Send to all connected nodes
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                sendToNode(node)
            }
            updateConnectionState()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("WearMessageBus.onMessageReceived: ${messageEvent.path} from ${messageEvent.sourceNodeId}")

        listeners.forEach { listener ->
            try {
                listener.onMessageReceived(
                    messageEvent.path,
                    messageEvent.data,
                    messageEvent.sourceNodeId
                )
            } catch (e: Exception) {
                Timber.e(e, "Error delivering message to listener: ${messageEvent.path}")
            }
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        Timber.d("WearMessageBus.addMessageListener: $listener")
        listeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        Timber.d("WearMessageBus.removeMessageListener: $listener")
        listeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val localNode = nodeClient.localNode.await()

            val result = mutableListOf<MessageNode>()

            // Add local node
            result.add(MessageNode(
                id = localNode.id,
                displayName = localNode.displayName,
                isLocal = true
            ))

            // Add connected nodes
            nodes.forEach { node ->
                result.add(MessageNode(
                    id = node.id,
                    displayName = node.displayName,
                    isLocal = false
                ))
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error getting connected nodes")
            emptyList()
        }
    }

    override fun observeConnectionState(): Flow<ConnectionState> {
        return _connectionState.asStateFlow()
    }

    private fun updateConnectionState() {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val messageNodes = nodes.map { node ->
                MessageNode(
                    id = node.id,
                    displayName = node.displayName,
                    isLocal = false
                )
            }
            _connectionState.value = if (messageNodes.isEmpty()) {
                ConnectionState.Disconnected
            } else {
                ConnectionState.Connected(messageNodes)
            }
        }
    }

    override fun close() {
        Timber.d("WearMessageBus.close()")
        messageClient.removeListener(this)
        listeners.clear()
    }
}
