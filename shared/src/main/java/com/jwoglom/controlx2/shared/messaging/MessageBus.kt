package com.jwoglom.controlx2.shared.messaging

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for sending and receiving messages between components.
 * Implementations include:
 * - LocalMessageBus: In-process communication for phone-only mode
 * - WearMessageBus: Wear OS Data Layer communication for phone-watch mode
 */
interface MessageBus {
    /**
     * Send a message to all connected nodes
     * @param path The message path (e.g., "/from-pump/pump-connected")
     * @param data The message data as bytes
     */
    fun sendMessage(path: String, data: ByteArray)

    /**
     * Add a listener for incoming messages
     */
    fun addMessageListener(listener: MessageListener)

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
