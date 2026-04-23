package com.jwoglom.controlx2.messaging

import android.content.Context
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.messaging.ConnectionState
import com.jwoglom.controlx2.shared.messaging.LocalMessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.MessageNode
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Watch-side hybrid bus mirroring mobile's `HybridMessageBus`.
 *
 * Wraps the process-level [LocalMessageBus] singleton (for in-process dispatch
 * between `WearPumpCommService` and the UI activity) plus [WearMessageBus] (for
 * talking to the phone client over the Wear Data Layer).
 *
 * Each construction site passes an [identity] — the logical sender the owning
 * component emits as. Our [LocalMessageBus] proxy is registered tagged with
 * that identity, so the bus never re-delivers this component's own emissions
 * back to its own listeners. Reentrance is prevented by construction, not by
 * listener-side discipline.
 *
 * Routing (identical semantics to mobile's `HybridMessageBus`):
 * - PUMP_HOST: `to-client` paths go to Wear (phone client); `from-pump`
 *   paths go to Local + Wear; `to-server` and `to-pump` paths stay Local
 *   (the service in this process handles them).
 * - CLIENT: `to-server` and `to-pump` paths go to Wear (phone pump-host);
 *   everything else stays Local.
 */
class WearHybridMessageBus(
    context: Context,
    private val deviceRole: DeviceRole,
    private val identity: MessageBusSender,
) : MessageBus {

    private val wearBus: MessageBus = WearMessageBus(context)
    private val localBus: LocalMessageBus = LocalMessageBus.getInstance()
    private val externalListeners = CopyOnWriteArrayList<MessageListener>()

    private val localProxy = object : MessageListener {
        override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
            // Emissions from other identities in this process. Ours were filtered
            // out by LocalMessageBus because our proxy is registered with `identity`.
            notifyExternal(path, data, sourceNodeId)
        }
    }

    private val wearProxy = object : MessageListener {
        override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
            // Messages received from the phone over Wear DL. Apply role-directional
            // gating so a PUMP_HOST watch only accepts commands from the phone
            // CLIENT and vice versa.
            when (deviceRole) {
                DeviceRole.PUMP_HOST -> {
                    if (path.startsWith(MessagePaths.PREFIX_TO_SERVER) ||
                        path.startsWith(MessagePaths.PREFIX_TO_PUMP)
                    ) {
                        notifyExternal(path, data, sourceNodeId)
                    }
                }
                DeviceRole.CLIENT -> {
                    if (path.startsWith(MessagePaths.PREFIX_TO_CLIENT) ||
                        path.startsWith(MessagePaths.PREFIX_FROM_PUMP)
                    ) {
                        notifyExternal(path, data, sourceNodeId)
                    }
                }
            }
        }
    }

    init {
        localBus.addMessageListener(localProxy, identity)
        wearBus.addMessageListener(wearProxy)
    }

    override fun sendMessage(path: String, data: ByteArray, sender: MessageBusSender) {
        when (deviceRole) {
            DeviceRole.PUMP_HOST -> {
                when {
                    path.startsWith(MessagePaths.PREFIX_TO_CLIENT) -> {
                        wearBus.sendMessage(path, data, sender)
                    }
                    path.startsWith(MessagePaths.PREFIX_FROM_PUMP) -> {
                        localBus.sendMessage(path, data, sender)
                        wearBus.sendMessage(path, data, sender)
                    }
                    else -> {
                        localBus.sendMessage(path, data, sender)
                    }
                }
            }
            DeviceRole.CLIENT -> {
                when {
                    path.startsWith(MessagePaths.PREFIX_TO_SERVER) -> {
                        wearBus.sendMessage(path, data, sender)
                    }
                    path.startsWith(MessagePaths.PREFIX_TO_PUMP) -> {
                        wearBus.sendMessage(path, data, sender)
                    }
                    else -> {
                        localBus.sendMessage(path, data, sender)
                    }
                }
            }
        }
    }

    override fun addMessageListener(listener: MessageListener) {
        externalListeners.add(listener)
    }

    override fun removeMessageListener(listener: MessageListener) {
        externalListeners.remove(listener)
    }

    override suspend fun getConnectedNodes(): List<MessageNode> = wearBus.getConnectedNodes()

    override fun observeConnectionState(): Flow<ConnectionState> = wearBus.observeConnectionState()

    override fun close() {
        try {
            localBus.removeMessageListener(localProxy)
        } catch (_: Exception) {}
        try {
            wearBus.removeMessageListener(wearProxy)
        } catch (e: Exception) {
            Timber.w(e, "WearHybridMessageBus close: wearBus listener remove failed")
        }
        externalListeners.clear()
        // Don't close localBus — it's a process-level singleton shared with other
        // hybrid bus instances. Do close wearBus since each instance owns its own.
        try {
            wearBus.close()
        } catch (e: Exception) {
            Timber.w(e, "WearHybridMessageBus close: wearBus.close failed")
        }
    }

    private fun notifyExternal(path: String, data: ByteArray, sourceNodeId: String) {
        externalListeners.forEach { listener ->
            try {
                listener.onMessageReceived(path, data, sourceNodeId)
            } catch (e: Exception) {
                Timber.e(e, "WearHybridMessageBus listener delivery error: $path")
            }
        }
    }
}
