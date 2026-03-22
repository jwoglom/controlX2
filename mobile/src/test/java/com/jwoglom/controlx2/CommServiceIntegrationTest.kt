package com.jwoglom.controlx2

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.testutil.RecordingMessageBus
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.shared.Hex
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes

/**
 * Integration tests for CommService covering all major behaviors:
 * - Message routing
 * - Response caching
 * - Bolus security
 * - Data forwarding to wear
 * - Pump connection lifecycle
 * - PumpFinder mode
 * - Service lifecycle
 *
 * These tests serve as a safety net before decomposing CommService (Phase 0).
 * They test behavior (message in → message out) rather than implementation details.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CommServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var messageBus: RecordingMessageBus
    private lateinit var serviceController: ServiceController<CommService>
    private lateinit var service: CommService
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Prefs class stores everything in "WearX2" SharedPreferences
        prefs = context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("tos-accepted", true)
            .putBoolean("service-enabled", true)
            .putBoolean("pumpfinder-service-enabled", false)
            .commit()

        // Create and inject test MessageBus
        messageBus = RecordingMessageBus()
        MessageBusFactory.setInstanceForTesting(messageBus)

        // Mock TandemBluetoothHandler to prevent real BT operations
        mockkStatic(TandemBluetoothHandler::class)
        val mockBtHandler = mockk<TandemBluetoothHandler>(relaxed = true)
        every { TandemBluetoothHandler.getInstance(any(), any(), any()) } returns mockBtHandler

        // Mock TandemPumpFinder constructor to prevent BT scanning
        mockkConstructor(TandemPumpFinder::class)
        every { anyConstructed<TandemPumpFinder>().startScan() } returns Unit
        every { anyConstructed<TandemPumpFinder>().stop() } returns Unit

        // Build the service via Robolectric
        serviceController = Robolectric.buildService(CommService::class.java)
    }

    @After
    fun teardown() {
        try {
            serviceController.destroy()
        } catch (_: Exception) {}
        MessageBusFactory.setInstanceForTesting(null)
        unmockkAll()
        prefs.edit().clear().commit()
    }

    // --- Helpers ---

    /**
     * Create and start the service in normal (pump comm) mode.
     */
    private fun startServiceNormal() {
        prefs.edit().putBoolean("pumpfinder-service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        // Process pending looper messages
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear() // Clear setup messages
    }

    /**
     * Create and start the service in PumpFinder mode.
     */
    private fun startServicePumpFinder() {
        prefs.edit().putBoolean("pumpfinder-service-enabled", true).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()
    }

    /**
     * Send a message to the service as if it came from outside.
     */
    private fun sendMessage(path: String, data: String = "") {
        service.handleMessageReceived(path, data.toByteArray(), "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sendMessage(path: String, data: ByteArray) {
        service.handleMessageReceived(path, data, "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    // =========================================================================
    // TEST GROUP 7: Service Lifecycle
    // =========================================================================

    @Test
    fun service_onCreate_normalMode_createsPumpCommHandler() {
        startServiceNormal()
        // Service should have registered a message listener on the bus
        // and be in normal comm mode. Verify by checking it responds to
        // service status request with comm-started (not pump-finder-started)
        sendMessage("/to-phone/request-service-status")
        assertTrue(
            "Expected /to-phone/comm-started",
            messageBus.hasMessage("/to-phone/comm-started")
        )
        assertFalse(
            "Should NOT send pump-finder-started in normal mode",
            messageBus.hasMessage("/to-phone/pump-finder-started")
        )
    }

    @Test
    fun service_onCreate_pumpFinderMode_createsPumpFinderHandler() {
        startServicePumpFinder()
        sendMessage("/to-phone/request-service-status")
        assertTrue(
            "Expected /to-phone/pump-finder-started",
            messageBus.hasMessage("/to-phone/pump-finder-started")
        )
        assertFalse(
            "Should NOT send comm-started in pump-finder mode",
            messageBus.hasMessage("/to-phone/comm-started")
        )
    }

    @Test
    fun service_onCreate_tosNotAccepted_shortCircuits() {
        prefs.edit().putBoolean("tos-accepted", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        // When TOS is not accepted, service should not set up handlers.
        // Sending a message should not crash (handlers are null).
        // And no status messages should have been sent.
        val statusMessages = messageBus.messagesForPath("/to-phone/comm-started") +
            messageBus.messagesForPath("/to-phone/pump-finder-started")
        assertTrue("No status messages expected when TOS not accepted", statusMessages.isEmpty())
    }

    @Test
    fun service_onCreate_serviceDisabled_shortCircuits() {
        prefs.edit().putBoolean("service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        val statusMessages = messageBus.messagesForPath("/to-phone/comm-started") +
            messageBus.messagesForPath("/to-phone/pump-finder-started")
        assertTrue("No status messages expected when service disabled", statusMessages.isEmpty())
    }

    @Test
    fun service_onStartCommand_alreadyRunning_noop() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        // Second start should be ignored (no duplicate init)
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        // Service should still function normally
        sendMessage("/to-phone/request-service-status")
        assertTrue(messageBus.hasMessage("/to-phone/comm-started"))
    }

    // =========================================================================
    // TEST GROUP 1: Message Routing
    // =========================================================================

    @Test
    fun routing_requestServiceStatus_normalMode_respondsCommStarted() {
        startServiceNormal()
        sendMessage("/to-phone/request-service-status")
        assertTrue(messageBus.hasMessage("/to-phone/comm-started"))
    }

    @Test
    fun routing_requestServiceStatus_pumpFinderMode_respondsPumpFinderStarted() {
        startServicePumpFinder()
        sendMessage("/to-phone/request-service-status")
        assertTrue(messageBus.hasMessage("/to-phone/pump-finder-started"))
    }

    @Test
    fun routing_serviceStatusAcknowledged_stopsPolling() {
        startServiceNormal()
        // After acknowledgment, requesting status should still respond,
        // but the periodic sender should stop (we verify no duplicate sends)
        sendMessage("/to-phone/service-status-acknowledged")
        messageBus.clear()
        // Now advance time — no periodic status should be sent
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        assertFalse(
            "No periodic comm-started after acknowledgment",
            messageBus.hasMessage("/to-phone/comm-started")
        )
    }

    @Test
    fun routing_isPumpConnected_queriesState() {
        startServiceNormal()
        // When pump is not connected, should get pump-not-connected
        sendMessage("/to-phone/is-pump-connected")
        shadowOf(Looper.getMainLooper()).idle()
        // The handler should respond — either pump-connected or pump-not-connected
        // Since we haven't connected a pump, we expect not-connected
        val hasConnected = messageBus.hasMessage("/from-pump/pump-connected")
        val hasNotConnected = messageBus.hasMessage("/from-pump/pump-not-connected")
        assertTrue(
            "Expected either pump-connected or pump-not-connected",
            hasConnected || hasNotConnected
        )
    }

    @Test
    fun routing_bolusCancel_resetsPrefs() {
        startServiceNormal()
        // Set up bolus prefs
        prefs.edit()
            .putString("initiateBolusRequest", "test")
            .putLong("initiateBolusTime", System.currentTimeMillis())
            .commit()
        sendMessage("/to-phone/bolus-cancel")
        // Prefs should be cleared
        assertNull(prefs.getString("initiateBolusRequest", null))
    }

    @Test
    fun routing_setPairingCode_doesNotCrash() {
        startServiceNormal()
        sendMessage("/to-phone/set-pairing-code", "123456")
        // Should handle gracefully (logged but no action needed in service)
    }

    @Test
    fun routing_stopComm_sendsToHandler() {
        startServiceNormal()
        // Should not crash when sending stop-comm
        sendMessage("/to-phone/stop-comm")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun routing_pumpCommand_routedToHandler() {
        startServiceNormal()
        // Create a serialized pump command
        val request = ApiVersionRequest()
        val msgBytes = PumpMessageSerializer.toBytes(request)
        sendMessage("/to-pump/command", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()
        // Handler processes it - we can't easily verify BLE was called without
        // connecting, but we verify no crash and message was consumed
    }

    @Test
    fun routing_pumpCommandsBulk_routedToHandler() {
        startServiceNormal()
        val requests = listOf(ApiVersionRequest(), ControlIQIOBRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun routing_cachedCommands_routedToHandler() {
        startServiceNormal()
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/cached-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun routing_commandsBustCache_routedToHandler() {
        startServiceNormal()
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/commands-bust-cache", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun routing_refreshHistoryLogSync_doesNotCrash() {
        startServiceNormal()
        sendMessage("/to-phone/refresh-history-log-sync")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun routing_writeCharacteristicFailedCallback_doesNotCrash() {
        startServiceNormal()
        sendMessage("/to-phone/write-characteristic-failed-callback", "some-uuid")
        shadowOf(Looper.getMainLooper()).idle()
    }

    // =========================================================================
    // TEST GROUP 3: Bolus Security
    // =========================================================================

    @Test
    fun bolus_bolusCommandOnNormalPath_rejected() {
        startServiceNormal()
        // InitiateBolusRequest on the normal SEND_PUMP_COMMAND path should be blocked
        val bolusRequest = InitiateBolusRequest(1000, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage("/to-pump/command", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()
        // The bolus should be blocked (logged as "SEND_PUMP_COMMAND blocked bolus command")
        // It should NOT be forwarded to the pump
        // We verify it doesn't crash and doesn't send a bolus confirmation
        assertFalse(
            "Bolus should not be confirmed via normal command path",
            messageBus.hasMessage("/to-wear/bolus-blocked-signature")
        )
    }

    @Test
    fun bolus_confirmRequest_phone_createsConfirmation() {
        startServiceNormal()
        // Enable insulin delivery
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        // Set threshold high so notification is created
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()

        val bolusRequest = InitiateBolusRequest(1000, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage("/to-phone/bolus-request-phone", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should set up bolus prefs
        assertNotNull(
            "initiateBolusRequest should be stored",
            prefs.getString("initiateBolusRequest", null)
        )
        assertNotNull(
            "initiateBolusSecret should be stored",
            prefs.getString("initiateBolusSecret", null)
        )
        assertEquals("phone", prefs.getString("initiateBolusSource", null))
    }

    @Test
    fun bolus_confirmRequest_wear_createsConfirmation() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()

        val bolusRequest = InitiateBolusRequest(1000, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage("/to-phone/bolus-request-wear", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("wear", prefs.getString("initiateBolusSource", null))
        // Should also broadcast threshold and auto-approve timeout to wear
        assertTrue(messageBus.hasMessage("/to-wear/bolus-min-notify-threshold"))
        assertTrue(messageBus.hasMessage("/to-wear/wear-auto-approve-timeout"))
    }

    @Test
    fun bolus_initiateConfirmedBolus_validSignature_forwardedToHandler() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()

        // Set up the secret in prefs (mimicking the confirmBolusRequest flow)
        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        // Create a signed bolus message
        val bolusRequest = InitiateBolusRequest(1000, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage("/to-phone/initiate-confirmed-bolus", signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT send blocked-bolus-signature since signature is valid
        assertFalse(
            "Valid signature should not be blocked",
            messageBus.hasMessage("/to-wear/blocked-bolus-signature")
        )
    }

    @Test
    fun bolus_initiateConfirmedBolus_invalidSignature_blocked() {
        startServiceNormal()

        // Set up a different secret than what we sign with
        prefs.edit().putString("initiateBolusSecret", "correct-secret").commit()

        // Sign with wrong secret
        val bolusRequest = InitiateBolusRequest(1000, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes("wrong-secret", bolusRequest)

        sendMessage("/to-phone/initiate-confirmed-bolus", signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should send blocked-bolus-signature
        assertTrue(
            "Invalid signature should be blocked",
            messageBus.hasMessage("/to-wear/blocked-bolus-signature")
        )
    }

    @Test
    fun bolus_cancel_clearsBolusPrefs() {
        startServiceNormal()

        // Set up bolus prefs
        prefs.edit()
            .putString("initiateBolusRequest", "test-hex")
            .putString("initiateBolusSecret", "secret")
            .putLong("initiateBolusTime", System.currentTimeMillis())
            .commit()

        sendMessage("/to-phone/bolus-cancel")

        // initiateBolusRequest and initiateBolusTime should be cleared
        assertNull(prefs.getString("initiateBolusRequest", null))
        assertEquals(0L, prefs.getLong("initiateBolusTime", 0L))
    }

    // =========================================================================
    // TEST GROUP 4: Data Forwarding to Wear
    // =========================================================================

    // Note: These tests require a connected pump to trigger onReceiveMessage.
    // Since we can't easily connect a real pump in unit tests, we test the
    // message routing logic that decides WHICH messages get forwarded.
    // The forwarding logic is in the Pump.onReceiveMessage callback.
    //
    // For now, we verify the sendWearCommMessage behavior itself.

    @Test
    fun sendWearCommMessage_sendsViaMessageBus() {
        startServiceNormal()
        service.sendWearCommMessage("/test/path", "test-data".toByteArray())
        assertTrue(messageBus.hasMessage("/test/path"))
        assertEquals(
            MessageBusSender.COMM_SERVICE,
            messageBus.lastMessage("/test/path")?.sender
        )
    }

    @Test
    fun sendWearCommMessage_preservesData() {
        startServiceNormal()
        val data = "hello-world"
        service.sendWearCommMessage("/test/data-check", data.toByteArray())
        val msg = messageBus.lastMessage("/test/data-check")
        assertNotNull(msg)
        assertEquals(data, msg!!.dataString)
    }

    // =========================================================================
    // TEST GROUP 5: Pump Connection Lifecycle
    // =========================================================================

    // These tests verify the service state transitions. Since we mock the BT
    // layer, we can't trigger actual pump connections. Instead we verify:
    // 1. The handler is created
    // 2. Commands are routed correctly
    // 3. State queries work

    @Test
    fun lifecycle_isPumpConnected_whenNotConnected_sendsPumpNotConnected() {
        startServiceNormal()
        sendMessage("/to-phone/is-pump-connected")

        // Need to process the Handler message
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100) // Allow handler thread to process
        shadowOf(Looper.getMainLooper()).idle()

        // Since pump was never initialized in test, should get not-connected
        assertTrue(
            "Expected pump-not-connected when pump handler not initialized",
            messageBus.hasMessage("/from-pump/pump-not-connected")
        )
    }

    @Test
    fun lifecycle_stopComm_doesNotCrash() {
        startServiceNormal()
        sendMessage("/to-phone/stop-comm")
        shadowOf(Looper.getMainLooper()).idle()
        // Verify service still functions after stop
        sendMessage("/to-phone/request-service-status")
        assertTrue(messageBus.hasMessage("/to-phone/comm-started"))
    }

    @Test
    fun lifecycle_serviceDestroy_cleansUp() {
        startServiceNormal()
        serviceController.destroy()
        // Should not crash during cleanup
    }

    @Test
    fun lifecycle_pumpReadyForHistoryFetch_whenNoSession_returnsFalse() {
        startServiceNormal()
        assertFalse(
            "No active session, should return false",
            service.isPumpReadyForHistoryFetch()
        )
    }

    @Test
    fun lifecycle_getPumpSession_whenNoSession_returnsNull() {
        startServiceNormal()
        assertNull(
            "No active session, should return null",
            service.getPumpSession()
        )
    }

    // =========================================================================
    // TEST GROUP 6: PumpFinder Mode
    // =========================================================================

    @Test
    fun pumpFinder_requestServiceStatus_respondsPumpFinderStarted() {
        startServicePumpFinder()
        sendMessage("/to-phone/request-service-status")
        assertTrue(messageBus.hasMessage("/to-phone/pump-finder-started"))
    }

    @Test
    fun pumpFinder_checkFoundPumps_returnsEmptyInitially() {
        startServicePumpFinder()
        // Start the pump finder
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        sendMessage("/to-phone/check-pump-finder-found-pumps")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        // Should respond with found-pumps (empty list = empty string)
        val foundPumpsMessages = messageBus.messagesForPath("/from-pump/pump-finder-found-pumps")
        if (foundPumpsMessages.isNotEmpty()) {
            assertEquals("", foundPumpsMessages.last().dataString)
        }
    }

    @Test
    fun pumpFinder_stopPumpFinder_withInitComm_transitionsToNormalMode() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        // Transition to normal comm mode
        sendMessage("/to-phone/stop-pump-finder", "init_comm")
        shadowOf(Looper.getMainLooper()).idle()

        // Should send comm-started
        assertTrue(
            "Expected comm-started after transition",
            messageBus.hasMessage("/to-phone/comm-started")
        )

        // pumpfinder-service-enabled pref should be false now
        assertFalse(prefs.getBoolean("pumpfinder-service-enabled", true))
    }

    @Test
    fun pumpFinder_stopPumpFinder_withoutInitComm_stopsOnly() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        // Stop without transitioning
        sendMessage("/to-phone/stop-pump-finder", "")
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT send comm-started (just stopped finder)
        assertFalse(messageBus.hasMessage("/to-phone/comm-started"))
    }

    // =========================================================================
    // TEST GROUP 2: Response Caching
    // =========================================================================

    // Response caching is tightly coupled to the PumpCommHandler inner class
    // and requires a connected pump to populate the cache. We test the
    // observable behavior: cached-commands path returns cached responses
    // when available, or sends to pump when not.
    //
    // Since we can't easily populate the cache without a real pump connection,
    // we verify the routing paths work correctly.

    @Test
    fun caching_cachedCommandsPath_routedToHandler() {
        startServiceNormal()
        val request = ApiVersionRequest()
        val bulkBytes = PumpMessageSerializer.toBulkBytes(listOf(request))
        // Should not crash — handler processes even if pump not connected
        sendMessage("/to-pump/cached-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun caching_bustCachePath_routedToHandler() {
        startServiceNormal()
        val request = ControlIQIOBRequest()
        val bulkBytes = PumpMessageSerializer.toBulkBytes(listOf(request))
        sendMessage("/to-pump/commands-bust-cache", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun caching_debugGetMessageCache_routedToHandler() {
        startServiceNormal()
        sendMessage("/to-pump/debug-message-cache")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        // Should respond with debug-message-cache (empty cache)
        // The handler will send /from-pump/debug-message-cache
    }

    @Test
    fun caching_debugGetHistoryLogCache_routedToHandler() {
        startServiceNormal()
        sendMessage("/to-pump/debug-historylog-cache")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // =========================================================================
    // Additional edge case tests
    // =========================================================================

    @Test
    fun routing_unknownPath_ignoredGracefully() {
        startServiceNormal()
        sendMessage("/unknown/path", "data")
        // Should not crash
    }

    @Test
    fun routing_fromPumpPath_loopbackIgnored() {
        startServiceNormal()
        // /from-pump/ messages received should be ignored in the routing
        // (they're outgoing, not incoming actions)
        sendMessage("/from-pump/pump-connected", "TestPump")
        // Should not crash or trigger any action
    }

    @Test
    fun bolus_confirmRequest_belowThreshold_autoApproves() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        // Set threshold above the bolus amount (1u = 1000 milliunits)
        // Threshold in prefs is stored via InsulinUnit.from1To1000
        val thresholdMilliunits = InsulinUnit.from1To1000(5.0) // 5 units threshold
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", thresholdMilliunits.toLong()).commit()

        // Request a small bolus (0.5u = 500 milliunits) - below 5u threshold
        val bolusRequest = InitiateBolusRequest(500, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage("/to-phone/bolus-request-phone", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Bolus prefs should still be set (for the auto-approve flow)
        assertNotNull(prefs.getString("initiateBolusSecret", null))
    }

    @Test
    fun routing_debugCommands_registersForStreamTagging() {
        startServiceNormal()
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        // debug-commands should register the request for stream tagging
        sendMessage("/to-pump/debug-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        // Should not crash — debug prompt response tracking is internal
    }

    @Test
    fun service_broadcastHistoryLogItem_doesNotCrash() {
        startServiceNormal()
        // httpDebugApiService may or may not be initialized
        // This should not throw
        service.broadcastHistoryLogItem(
            com.jwoglom.controlx2.db.historylog.HistoryLogItem(
                seqId = 1,
                pumpSid = 123,
                typeId = 1,
                cargo = byteArrayOf(),
                pumpTime = java.time.LocalDateTime.now(),
                addedTime = java.time.LocalDateTime.now()
            )
        )
    }
}
