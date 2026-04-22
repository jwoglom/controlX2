package com.jwoglom.controlx2.shared.messaging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-local in-memory implementation of [MessageBus].
 *
 * Used as the in-process transport inside hybrid buses (mobile's `HybridMessageBus`,
 * watch's `WearHybridMessageBus`) so components living in the same process — e.g.
 * the foreground activity and a bound service — can exchange messages without a
 * cross-process broadcast.
 *
 * A process-level singleton is exposed via [getInstance] so that independently
 * constructed hybrid-bus instances share a single listener list.
 *
 * Reentrance protection: listeners registered via
 * [addMessageListener(listener, listenerSender)] are skipped on delivery when the
 * emission's `sender` matches the listener's `listenerSender`. A component cannot
 * observe its own emissions — enforced by the bus, not by listener-side discipline.
 */
class LocalMessageBus private constructor() : MessageBus {
    private val listeners = CopyOnWriteArrayList<Entry>()

    private val localNode = MessageNode(
        id = LOCAL_NODE_ID,
        displayName = "Local",
        isLocal = true,
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(listOf(localNode))
    )

    override fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender) {
        listeners.forEach { entry ->
            if (entry.listenerSender != null && entry.listenerSender == sender) return@forEach
            try {
                entry.listener.onMessageReceived(path, data, localNode.id)
            } catch (e: Exception) {
                Timber.e(e, "LocalMessageBus delivery error: $path")
            }
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        listeners.add(Entry(listener, null))
    }

    override fun addMessageListener(listener: MessageListener, listenerSender: MessageBusSender) {
        listeners.add(Entry(listener, listenerSender))
    }

    override fun removeMessageListener(listener: MessageListener) {
        listeners.removeAll { it.listener === listener }
    }

    override suspend fun getConnectedNodes(): List<MessageNode> = listOf(localNode)

    override fun observeConnectionState(): Flow<ConnectionState> = _connectionState.asStateFlow()

    override fun close() {
        listeners.clear()
    }

    private data class Entry(
        val listener: MessageListener,
        val listenerSender: MessageBusSender?,
    )

    companion object {
        const val LOCAL_NODE_ID = "local-process"

        @Volatile
        private var instance: LocalMessageBus? = null

        /**
         * Returns the process-level singleton. All in-process callers share one
         * listener list so messages sent by one component reach listeners
         * registered by others.
         */
        fun getInstance(): LocalMessageBus {
            return instance ?: synchronized(this) {
                instance ?: LocalMessageBus().also { instance = it }
            }
        }
    }
}
