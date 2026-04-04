package com.jwoglom.controlx2.messaging

import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.testutil.RecordingMessageBus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HybridMessageBusTest {

    private lateinit var broadcastBus: RecordingMessageBus
    private lateinit var wearBus: RecordingMessageBus

    @Before
    fun setUp() {
        broadcastBus = RecordingMessageBus()
        wearBus = RecordingMessageBus()
    }

    // ---- PUMP_HOST send routing ----

    @Test
    fun `PUMP_HOST - to-client messages route to wear only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        bus.sendMessage(MessagePaths.TO_CLIENT_CONNECTED, "test".toByteArray())

        assertTrue(wearBus.hasMessage(MessagePaths.TO_CLIENT_CONNECTED))
        assertFalse(broadcastBus.hasMessage(MessagePaths.TO_CLIENT_CONNECTED))
    }

    @Test
    fun `PUMP_HOST - from-pump messages route to broadcast and wear`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        bus.sendMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "test".toByteArray())

        assertTrue(broadcastBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
        assertTrue(wearBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
    }

    @Test
    fun `PUMP_HOST - to-server messages route to broadcast only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        bus.sendMessage(MessagePaths.TO_SERVER_COMM_STARTED, "test".toByteArray())

        assertTrue(broadcastBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
        assertFalse(wearBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
    }

    @Test
    fun `PUMP_HOST - to-pump messages route to broadcast only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        bus.sendMessage(MessagePaths.TO_PUMP_COMMAND, "test".toByteArray())

        assertTrue(broadcastBus.hasMessage(MessagePaths.TO_PUMP_COMMAND))
        assertFalse(wearBus.hasMessage(MessagePaths.TO_PUMP_COMMAND))
    }

    // ---- CLIENT send routing ----

    @Test
    fun `CLIENT - to-server messages route to wear only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        bus.sendMessage(MessagePaths.TO_SERVER_COMM_STARTED, "test".toByteArray())

        assertTrue(wearBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
        assertFalse(broadcastBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
    }

    @Test
    fun `CLIENT - to-pump messages route to wear only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        bus.sendMessage(MessagePaths.TO_PUMP_COMMAND, "test".toByteArray())

        assertTrue(wearBus.hasMessage(MessagePaths.TO_PUMP_COMMAND))
        assertFalse(broadcastBus.hasMessage(MessagePaths.TO_PUMP_COMMAND))
    }

    @Test
    fun `CLIENT - to-client messages route to broadcast only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        bus.sendMessage(MessagePaths.TO_CLIENT_CONNECTED, "test".toByteArray())

        assertFalse(wearBus.hasMessage(MessagePaths.TO_CLIENT_CONNECTED))
        assertTrue(broadcastBus.hasMessage(MessagePaths.TO_CLIENT_CONNECTED))
    }

    @Test
    fun `CLIENT - from-pump messages route to broadcast only`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        bus.sendMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "test".toByteArray())

        assertFalse(wearBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
        assertTrue(broadcastBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
    }

    // ---- Listener filtering (PUMP_HOST) ----

    @Test
    fun `PUMP_HOST - wear proxy forwards to-server and to-pump from watch`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        // Simulate incoming from wear transport (watch client sending commands)
        wearBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        wearBus.simulateIncomingMessage(MessagePaths.TO_PUMP_COMMAND, "".toByteArray())

        assertTrue(received.contains(MessagePaths.TO_SERVER_COMM_STARTED))
        assertTrue(received.contains(MessagePaths.TO_PUMP_COMMAND))
    }

    @Test
    fun `PUMP_HOST - wear proxy ignores from-pump and to-client from watch`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        // These should NOT be delivered via wear proxy in PUMP_HOST mode
        wearBus.simulateIncomingMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "".toByteArray())
        wearBus.simulateIncomingMessage(MessagePaths.TO_CLIENT_CONNECTED, "".toByteArray())

        assertFalse(received.contains(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
        assertFalse(received.contains(MessagePaths.TO_CLIENT_CONNECTED))
    }

    // ---- Listener filtering (CLIENT) ----

    @Test
    fun `CLIENT - wear proxy forwards to-client and from-pump from watch`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        // Watch pump-host sending data to phone client
        wearBus.simulateIncomingMessage(MessagePaths.TO_CLIENT_CONNECTED, "".toByteArray())
        wearBus.simulateIncomingMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "".toByteArray())

        assertTrue(received.contains(MessagePaths.TO_CLIENT_CONNECTED))
        assertTrue(received.contains(MessagePaths.FROM_PUMP_PUMP_CONNECTED))
    }

    @Test
    fun `CLIENT - wear proxy ignores to-server and to-pump from watch`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.CLIENT)
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        // These should NOT be delivered via wear proxy in CLIENT mode
        wearBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        wearBus.simulateIncomingMessage(MessagePaths.TO_PUMP_COMMAND, "".toByteArray())

        assertFalse(received.contains(MessagePaths.TO_SERVER_COMM_STARTED))
        assertFalse(received.contains(MessagePaths.TO_PUMP_COMMAND))
    }

    // ---- Broadcast proxy always forwards ----

    @Test
    fun `broadcast proxy always forwards messages to listeners`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        broadcastBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        broadcastBus.simulateIncomingMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "".toByteArray())
        broadcastBus.simulateIncomingMessage(MessagePaths.TO_CLIENT_CONNECTED, "".toByteArray())

        assertEquals(3, received.size)
    }

    // ---- getConnectedNodes ----

    @Test
    fun `getConnectedNodes combines and deduplicates from both buses`() = runTest {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        val nodes = bus.getConnectedNodes()

        // Both RecordingMessageBus instances return the same "test-node" id,
        // so deduplication should yield 1 node
        assertEquals(1, nodes.size)
        assertEquals("test-node", nodes[0].id)
    }

    // ---- close ----

    @Test
    fun `close removes proxy listeners and closes both buses`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)

        // Add a listener to verify it gets cleared
        val received = mutableListOf<String>()
        bus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        })

        bus.close()

        // After close, simulating messages on underlying buses should not reach our listener
        // (proxy listeners were removed)
        broadcastBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        wearBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())

        assertTrue(received.isEmpty())
    }

    // ---- Sender is preserved ----

    @Test
    fun `sender parameter is passed through to underlying bus`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        bus.sendMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "test".toByteArray(), MessageBusSender.COMM_SERVICE)

        // from-pump goes to both in PUMP_HOST mode
        assertEquals(MessageBusSender.COMM_SERVICE, broadcastBus.lastMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED)?.sender)
        assertEquals(MessageBusSender.COMM_SERVICE, wearBus.lastMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED)?.sender)
    }

    // ---- removeMessageListener ----

    @Test
    fun `removeMessageListener stops delivery`() {
        val bus = HybridMessageBus(broadcastBus, wearBus, DeviceRole.PUMP_HOST)
        val received = mutableListOf<String>()
        val listener = object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                received.add(path)
            }
        }

        bus.addMessageListener(listener)
        broadcastBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        assertEquals(1, received.size)

        bus.removeMessageListener(listener)
        broadcastBus.simulateIncomingMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
        assertEquals(1, received.size) // still 1, no new delivery
    }
}
