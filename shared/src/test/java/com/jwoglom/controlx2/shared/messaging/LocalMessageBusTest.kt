package com.jwoglom.controlx2.shared.messaging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalMessageBusTest {

    private lateinit var bus: LocalMessageBus

    @Before
    fun setUp() {
        bus = LocalMessageBus.getInstance()
        bus.close() // singleton is shared; clear any leftover listeners
    }

    @After
    fun tearDown() {
        bus.close()
    }

    @Test
    fun `untagged listener receives all emissions`() {
        val received = mutableListOf<String>()
        bus.addMessageListener(listenerCollecting(received))

        bus.sendMessage("/foo", ByteArray(0), MessageBusSender.MOBILE_UI)
        bus.sendMessage("/bar", ByteArray(0), MessageBusSender.COMM_SERVICE)

        assertEquals(listOf("/foo", "/bar"), received)
    }

    @Test
    fun `tagged listener never receives its own emissions`() {
        val uiSeen = mutableListOf<String>()
        val svcSeen = mutableListOf<String>()

        bus.addMessageListener(listenerCollecting(uiSeen), MessageBusSender.MOBILE_UI)
        bus.addMessageListener(listenerCollecting(svcSeen), MessageBusSender.COMM_SERVICE)

        bus.sendMessage("/from-ui", ByteArray(0), MessageBusSender.MOBILE_UI)
        bus.sendMessage("/from-svc", ByteArray(0), MessageBusSender.COMM_SERVICE)

        // UI's listener sees service emissions but not its own.
        assertEquals(listOf("/from-svc"), uiSeen)
        // Service's listener sees UI emissions but not its own.
        assertEquals(listOf("/from-ui"), svcSeen)
    }

    @Test
    fun `mixed tagged and untagged listeners coexist`() {
        val taggedUi = mutableListOf<String>()
        val untagged = mutableListOf<String>()

        bus.addMessageListener(listenerCollecting(taggedUi), MessageBusSender.MOBILE_UI)
        bus.addMessageListener(listenerCollecting(untagged))

        bus.sendMessage("/a", ByteArray(0), MessageBusSender.MOBILE_UI)
        bus.sendMessage("/b", ByteArray(0), MessageBusSender.COMM_SERVICE)

        assertEquals(listOf("/b"), taggedUi)
        assertEquals(listOf("/a", "/b"), untagged)
    }

    @Test
    fun `removeMessageListener stops delivery`() {
        val seen = mutableListOf<String>()
        val listener = listenerCollecting(seen)

        bus.addMessageListener(listener, MessageBusSender.WEAR_UI)
        bus.sendMessage("/1", ByteArray(0), MessageBusSender.MOBILE_UI)
        bus.removeMessageListener(listener)
        bus.sendMessage("/2", ByteArray(0), MessageBusSender.MOBILE_UI)

        assertEquals(listOf("/1"), seen)
    }

    @Test
    fun `singleton returns the same instance`() {
        val a = LocalMessageBus.getInstance()
        val b = LocalMessageBus.getInstance()
        assertTrue(a === b)
    }

    private fun listenerCollecting(sink: MutableList<String>): MessageListener = object : MessageListener {
        override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
            sink.add(path)
        }
    }
}
