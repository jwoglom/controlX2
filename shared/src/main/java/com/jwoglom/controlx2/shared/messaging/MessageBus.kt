package com.jwoglom.controlx2.shared.messaging

import kotlinx.coroutines.flow.Flow

/**
 * Identifies the sender of a message for routing purposes
 */
enum class MessageBusSender {
    /** Message originates from the wear UI (watch) */
    WEAR_UI,

    /** Message originates from the mobile UI (phone app) */
    MOBILE_UI,

    /** Message originates from CommService (background service on phone) */
    COMM_SERVICE
}

/**
 * Abstraction for sending and receiving messages between components.
 * Implementations include:
 * - LocalMessageBus: Process-local in-memory dispatch (with reentrance protection)
 * - BroadcastMessageBus: Cross-process Android broadcasts (mobile)
 * - WearMessageBus: Wear OS Data Layer communication (phone ↔ watch)
 * - HybridMessageBus / WearHybridMessageBus: Compose the above with role-aware routing
 */
interface MessageBus {
    /**
     * Send a message to all connected nodes
     * @param path The message path (e.g., "/from-pump/pump-connected")
     * @param data The message data as bytes
     * @param sender The component sending this message (for routing decisions)
     */
    fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender = MessageBusSender.COMM_SERVICE)

    /**
     * Add a listener for incoming messages
     */
    fun addMessageListener(listener: MessageListener)

    /**
     * Register a listener that is tagged with a sender identity. Buses that enforce
     * reentrance protection (notably [LocalMessageBus]) skip delivery of messages
     * whose `sender` equals [listenerSender] — so a component can never receive its
     * own emissions back through this bus.
     *
     * Buses that cannot determine the original sender (e.g. [WearMessageBus], which
     * only knows the remote node ID) fall back to the untagged behavior.
     */
    fun addMessageListener(listener: MessageListener, listenerSender: MessageBusSender) {
        addMessageListener(listener)
    }

    /**
     * Remove a previously added listener
     */
    fun removeMessageListener(listener: MessageListener)

    /**
     * Get list of currently connected nodes
     */
    suspend fun getConnectedNodes(): List<MessageNode>

    /**
     * Get flow of connection state changes
     */
    fun observeConnectionState(): Flow<ConnectionState>

    /**
     * Clean up resources
     */
    fun close()
}

/**
 * Listener for message events
 */
interface MessageListener {
    /**
     * Called when a message is received
     * @param path The message path
     * @param data The message data
     * @param sourceNodeId The ID of the node that sent the message
     */
    fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String)
}

/**
 * Represents a connected node (device) in the messaging network
 */
data class MessageNode(
    val id: String,
    val displayName: String,
    val isLocal: Boolean = false
)

/**
 * Connection state of the message bus
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val nodes: List<MessageNode>) : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
}
