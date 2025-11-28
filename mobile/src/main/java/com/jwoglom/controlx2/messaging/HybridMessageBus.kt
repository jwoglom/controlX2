package com.jwoglom.controlx2.messaging

import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hybrid MessageBus that combines multiple transport mechanisms:
 * - BroadcastMessageBus for cross-process communication on the same phone
 * - WearMessageBus for phone-to-watch communication
 *
 * This allows seamless communication between:
 * 1. Foreground app ↔ Background service (via Broadcast)
 * 2. Phone ↔ Watch (via Wear OS)
 *
 * All operations are transparently delegated to both transports.
 */
class HybridMessageBus(
    private val broadcastBus: MessageBus,
    private val wearBus: MessageBus
) : MessageBus {
    private val listeners = CopyOnWriteArrayList<MessageListener>()

    // Proxy listeners that forward messages from each transport to our listeners
    private val broadcastProxyListener = object : MessageListener {
        override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
            Timber.d("HybridMessageBus: Message from Broadcast transport: $path")
            notifyListeners(path, data, sourceNodeId)
        }
    }

    private val wearProxyListener = object : MessageListener {
        override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
            Timber.d("HybridMessageBus: Message from Wear transport: $path")

            // Special handling: /to-phone/* from Wear needs to reach BOTH CommService and Mobile UI
            // Re-broadcast locally so both components receive it
            if (path.startsWith("/to-phone/")) {
                Timber.d("HybridMessageBus: Re-broadcasting /to-phone/* from Wear locally")
                broadcastBus.sendMessage(path, data, MessageBusSender.WEAR_UI)
            }

            notifyListeners(path, data, sourceNodeId)
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    init {
        Timber.d("HybridMessageBus initialized with Broadcast + Wear transports")

        // Register proxy listeners with both transports
        broadcastBus.addMessageListener(broadcastProxyListener)
        wearBus.addMessageListener(wearProxyListener)

        // Observe combined connection state (simplified - shows Wear state)
        // In production, you might want to merge both states more intelligently
        updateConnectionState()
    }

    override fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender) {
        Timber.i("HybridMessageBus.sendMessage: $path (sender: $sender)")

        when {
            // Messages TO CommService from Mobile UI
            path.startsWith("/to-phone/") && sender == MessageBusSender.MOBILE_UI -> {
                Timber.d("Routing /to-phone/* from Mobile UI → Broadcast only")
                broadcastBus.sendMessage(path, data, sender)
            }

            // Messages TO CommService+Mobile UI from Wear UI
            path.startsWith("/to-phone/") && sender == MessageBusSender.WEAR_UI -> {
                Timber.d("Routing /to-phone/* from Wear UI → Wear only (will re-broadcast on phone)")
                wearBus.sendMessage(path, data, sender)
            }

            // Messages TO Wear from Mobile UI or CommService
            path.startsWith("/to-wear/") -> {
                Timber.d("Routing /to-wear/* → Wear only")
                wearBus.sendMessage(path, data, sender)
            }

            // Messages TO Pump from Mobile UI
            path.startsWith("/to-pump/") && sender == MessageBusSender.MOBILE_UI -> {
                Timber.d("Routing /to-pump/* from Mobile UI → Broadcast only")
                broadcastBus.sendMessage(path, data, sender)
            }

            // Messages TO Pump from Wear UI
            path.startsWith("/to-pump/") && sender == MessageBusSender.WEAR_UI -> {
                Timber.d("Routing /to-pump/* from Wear UI → Wear only")
                wearBus.sendMessage(path, data, sender)
            }

            // Messages FROM Pump (broadcast to all UIs)
            path.startsWith("/from-pump/") -> {
                Timber.d("Routing /from-pump/* → Both transports (broadcast to all)")
                broadcastBus.sendMessage(path, data, sender)
                wearBus.sendMessage(path, data, sender)
            }

            else -> {
                // Unknown pattern - send via both for safety
                Timber.w("Unknown message pattern: $path (sender: $sender) - sending via both transports")
                broadcastBus.sendMessage(path, data, sender)
                wearBus.sendMessage(path, data, sender)
            }
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        Timber.d("HybridMessageBus.addMessageListener: $listener")
        listeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        Timber.d("HybridMessageBus.removeMessageListener: $listener")
        listeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> {
        // Combine nodes from both transports
        val broadcastNodes = try {
            broadcastBus.getConnectedNodes()
        } catch (e: Exception) {
            Timber.w(e, "Error getting broadcast nodes")
            emptyList()
        }

        val wearNodes = try {
            wearBus.getConnectedNodes()
        } catch (e: Exception) {
            Timber.w(e, "Error getting wear nodes")
            emptyList()
        }

        // Combine and deduplicate by node ID
        val allNodes = (broadcastNodes + wearNodes)
            .distinctBy { it.id }

        Timber.d("HybridMessageBus.getConnectedNodes: ${allNodes.size} nodes total")
        return allNodes
    }

    override fun observeConnectionState(): Flow<ConnectionState> {
        return _connectionState.asStateFlow()
    }

    override fun close() {
        Timber.d("HybridMessageBus.close()")

        // Remove proxy listeners
        broadcastBus.removeMessageListener(broadcastProxyListener)
        wearBus.removeMessageListener(wearProxyListener)

        // Close both transports
        try {
            broadcastBus.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing broadcast bus")
        }

        try {
            wearBus.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing wear bus")
        }

        listeners.clear()
    }

    /**
     * Notify all registered listeners about a received message
     */
    private fun notifyListeners(path: String, data: ByteArray, sourceNodeId: String) {
        listeners.forEach { listener ->
            try {
                listener.onMessageReceived(path, data, sourceNodeId)
            } catch (e: Exception) {
                Timber.e(e, "Error delivering message to listener: $path")
            }
        }
    }

    /**
     * Update the combined connection state based on both transports
     * For now, we primarily show the Wear connection state since that's
     * more interesting (local broadcast is always "connected")
     */
    private fun updateConnectionState() {
        // In a more sophisticated implementation, you could combine flows:
        // combine(broadcastBus.observeConnectionState(), wearBus.observeConnectionState()) { ... }
        // For now, just mirror the Wear state
        // This could be enhanced later if needed
        _connectionState.value = ConnectionState.Connecting
    }
}
