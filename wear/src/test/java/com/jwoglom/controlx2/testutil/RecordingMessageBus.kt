package com.jwoglom.controlx2.testutil

import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test MessageBus that records all sent messages and supports simulating incoming messages.
 * Used as a test double for CommService integration tests.
 */
class RecordingMessageBus : MessageBus {

    data class SentMessage(
        val path: String,
        val data: ByteArray,
        val sender: MessageBusSender
    ) {
        val dataString: String get() = String(data)

        override fun toString(): String = "SentMessage(path=$path, data=${dataString}, sender=$sender)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SentMessage) return false
            return path == other.path && data.contentEquals(other.data) && sender == other.sender
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + sender.hashCode()
            return result
        }
    }

    val sentMessages = CopyOnWriteArrayList<SentMessage>()
    private val listeners = CopyOnWriteArrayList<MessageListener>()

    private val localNode = MessageNode(
        id = "test-node",
        displayName = "Test",
        isLocal = true
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(listOf(localNode))
    )

    override fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender) {
        sentMessages.add(SentMessage(path, data, sender))
    }

    override fun addMessageListener(listener: MessageListener) {
        listeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        listeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> = listOf(localNode)

    override fun observeConnectionState(): Flow<ConnectionState> = _connectionState.asStateFlow()

    override fun close() {
        listeners.clear()
    }

    // --- Test helpers ---

    /**
     * Simulate an incoming message (as if received from wear/UI).
     * Delivers to all registered listeners.
     */
    fun simulateIncomingMessage(path: String, data: ByteArray, sourceNodeId: String = "test-node") {
        listeners.forEach { it.onMessageReceived(path, data, sourceNodeId) }
    }

    fun messagesForPath(path: String): List<SentMessage> =
        sentMessages.filter { it.path == path }

    fun messagesForPathPrefix(prefix: String): List<SentMessage> =
        sentMessages.filter { it.path.startsWith(prefix) }

    fun lastMessage(path: String): SentMessage? =
        sentMessages.lastOrNull { it.path == path }

    fun hasMessage(path: String): Boolean =
        sentMessages.any { it.path == path }

    fun hasMessageWithData(path: String, data: String): Boolean =
        sentMessages.any { it.path == path && it.dataString == data }

    fun clear() = sentMessages.clear()
}
