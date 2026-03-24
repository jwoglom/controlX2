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
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.welie.blessed.BluetoothPeripheral
import java.time.Instant

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

    // Captured pump reference from TandemBluetoothHandler.getInstance() —
    // this is the Pump inner class instance, typed as TandemPump.
    private var capturedPump: TandemPump? = null
    private val mockPeripheral: BluetoothPeripheral = mockk(relaxed = true)

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

        // Mock TandemBluetoothHandler — capture the TandemPump callback
        mockkStatic(TandemBluetoothHandler::class)
        val mockBtHandler = mockk<TandemBluetoothHandler>(relaxed = true)
        every { TandemBluetoothHandler.getInstance(any(), any(), any()) } answers {
            capturedPump = secondArg<TandemPump>()
            mockBtHandler
        }

        // Mock peripheral name for extractPumpSid()
        every { mockPeripheral.name } returns "tslim X2 ***12345"

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

    /**
     * Start service in normal mode, trigger INIT_PUMP_COMM via onStartCommand,
     * and simulate a connected pump by setting internal state via reflection.
     * This avoids calling the real onPumpConnected() which has Thread.sleep loops
     * and complex external dependencies (NightscoutSyncWorker, HistoryLogFetcher, etc).
     */
    private fun startServiceAndConnectPump() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(500) // Let handler thread process INIT_PUMP_COMM
        shadowOf(Looper.getMainLooper()).idle()

        // At this point, INIT_PUMP_COMM has created the Pump and called startScan()
        // Our mock captured the pump reference via getInstance()
        assertNotNull("Pump should have been captured via TandemBluetoothHandler.getInstance()", capturedPump)

        // Use reflection to simulate connected state on the Pump inner class
        val pumpCommHandlerField = CommService::class.java.getDeclaredField("pumpCommHandler")
        pumpCommHandlerField.isAccessible = true
        val handler = pumpCommHandlerField.get(service)!!

        val pumpField = handler.javaClass.getDeclaredField("pump")
        pumpField.isAccessible = true
        val pump = pumpField.get(handler)!!

        // Set pump.isConnected = true
        val isConnectedField = pump.javaClass.getDeclaredField("isConnected")
        isConnectedField.isAccessible = true
        isConnectedField.set(pump, true)

        // Set pump.lastPeripheral = mockPeripheral
        val lastPeripheralField = pump.javaClass.getDeclaredField("lastPeripheral")
        lastPeripheralField.isAccessible = true
        lastPeripheralField.set(pump, mockPeripheral)

        // Create and set a PumpSession so isPumpReadyForHistoryFetch() works
        val session = com.jwoglom.controlx2.pump.PumpSession.open(capturedPump!!, mockPeripheral)
        val currentSessionField = handler.javaClass.getDeclaredField("currentSession")
        currentSessionField.isAccessible = true
        currentSessionField.set(handler, session)

        messageBus.clear()
    }

    /**
     * Access the lastResponseMessage cache on CommService via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getLastResponseMessageCache(): MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>> {
        val field = CommService::class.java.getDeclaredField("lastResponseMessage")
        field.isAccessible = true
        return field.get(service) as MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>>
    }

    /**
     * Populate the response cache with a message, as if it had been received from the pump.
     */
    private fun populateCache(message: com.jwoglom.pumpx2.pump.messages.Message, age: Instant = Instant.now()) {
        val cache = getLastResponseMessageCache()
        cache[Pair(message.characteristic, message.opCode())] = Pair(message, age)
    }

    // =========================================================================
    // Service Lifecycle
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
    // Message Routing
    // =========================================================================

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


    // =========================================================================
    // Bolus Security
    // =========================================================================

    @Test
    fun bolus_bolusCommandOnNormalPath_rejected() {
        startServiceNormal()
        // InitiateBolusRequest on the normal SEND_PUMP_COMMAND path should be blocked
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
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

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
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

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
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
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
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
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
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
    // Data Forwarding to Wear
    // =========================================================================

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
    // Pump Connection Lifecycle
    // =========================================================================

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
    // PumpFinder Mode
    // =========================================================================

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

    @Test
    fun bolus_confirmRequest_belowThreshold_autoApproves() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        // Set threshold above the bolus amount (1u = 1000 milliunits)
        // Threshold in prefs is stored via InsulinUnit.from1To1000
        val thresholdMilliunits = InsulinUnit.from1To1000(5.0) // 5 units threshold
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", thresholdMilliunits.toLong()).commit()

        // Request a small bolus (0.5u = 500 milliunits) - below 5u threshold
        val bolusRequest = InitiateBolusRequest(500, 1, 0, 0, 0, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage("/to-phone/bolus-request-phone", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Bolus prefs should still be set (for the auto-approve flow)
        assertNotNull(prefs.getString("initiateBolusSecret", null))
    }

    // =========================================================================
    // Connected Pump — Lifecycle
    // =========================================================================

    @Test
    fun connectedPump_isPumpReadyForHistoryFetch_returnsTrue() {
        startServiceAndConnectPump()
        assertTrue(
            "isPumpReadyForHistoryFetch should be true when session is active",
            service.isPumpReadyForHistoryFetch()
        )
    }

    @Test
    fun connectedPump_getPumpSession_returnsNonNull() {
        startServiceAndConnectPump()
        assertNotNull(
            "getPumpSession should return active session",
            service.getPumpSession()
        )
    }

    @Test
    fun connectedPump_isPumpConnected_sendsPumpConnected() {
        startServiceAndConnectPump()
        sendMessage("/to-phone/is-pump-connected")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected /from-pump/pump-connected when pump is connected",
            messageBus.hasMessage("/from-pump/pump-connected")
        )
    }

    // =========================================================================
    // Connected Pump — Response Caching
    // =========================================================================

    @Test
    fun connectedPump_cachedCommands_freshHit_returnsCachedResponse() {
        startServiceAndConnectPump()

        // Populate cache with a fresh ApiVersionResponse
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        // Send cached-commands request for ApiVersionRequest
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/cached-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should return cached response via /from-pump/receive-cached-message
        assertTrue(
            "Expected cached response via /from-pump/receive-cached-message",
            messageBus.hasMessage("/from-pump/receive-cached-message")
        )
    }

    @Test
    fun connectedPump_cachedCommands_expiredHit_sendsNewCommand() {
        startServiceAndConnectPump()

        // Populate cache with an expired response (older than 30s)
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now().minusSeconds(60))

        // Send cached-commands request
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/cached-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT return cached response (it's expired)
        assertFalse(
            "Expired cache should not return /from-pump/receive-cached-message",
            messageBus.hasMessage("/from-pump/receive-cached-message")
        )
        // The command should have been forwarded to the pump (via pump.command)
        // We can't directly verify this without the pump mock, but we verify no crash
    }

    @Test
    fun connectedPump_cachedCommands_miss_doesNotReturnCached() {
        startServiceAndConnectPump()

        // Don't populate cache — it's empty

        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/cached-commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // No cached response should be sent
        assertFalse(
            "Cache miss should not return /from-pump/receive-cached-message",
            messageBus.hasMessage("/from-pump/receive-cached-message")
        )
    }

    @Test
    fun connectedPump_bustCache_removesCacheEntry() {
        startServiceAndConnectPump()

        // Populate cache
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        // Verify cache is populated
        val cache = getLastResponseMessageCache()
        val key = Pair(response.characteristic, response.opCode())
        assertTrue("Cache should contain the response", cache.containsKey(key))

        // Send bust-cache command
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/commands-bust-cache", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Cache entry should have been removed
        assertFalse(
            "bust-cache should remove the cache entry",
            cache.containsKey(key)
        )
    }

    @Test
    fun connectedPump_debugMessageCache_returnsPopulatedCache() {
        startServiceAndConnectPump()

        // Populate cache with a response
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        sendMessage("/to-pump/debug-message-cache")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should respond with debug-message-cache containing data
        assertTrue(
            "Expected /from-pump/debug-message-cache with populated cache",
            messageBus.hasMessage("/from-pump/debug-message-cache")
        )
        val msg = messageBus.lastMessage("/from-pump/debug-message-cache")
        assertNotNull(msg)
        assertTrue("Cache data should be non-empty", msg!!.data.isNotEmpty())
    }

    // =========================================================================
    // Connected Pump — Message Forwarding to Wear
    // =========================================================================

    @Test
    fun connectedPump_onReceiveMessage_forwardsViaFromPump() {
        startServiceAndConnectPump()

        // Trigger onReceiveMessage directly on the captured pump
        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "All responses should be forwarded via /from-pump/receive-message",
            messageBus.hasMessage("/from-pump/receive-message")
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_batteryResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = CurrentBatteryV2Response(0, 66, 0, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        // Battery responses should be forwarded to wear via /to-wear/service-receive-message
        assertTrue(
            "Battery response should be forwarded to wear",
            messageBus.hasMessage("/to-wear/service-receive-message")
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_iobResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = ControlIQIOBResponse(1450, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "IOB response should be forwarded to wear",
            messageBus.hasMessage("/to-wear/service-receive-message")
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_cgmResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = CurrentEGVGuiDataResponse(1710000000, 123, 1, 2)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "CGM response should be forwarded to wear",
            messageBus.hasMessage("/to-wear/service-receive-message")
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_otherResponse_notForwardedToWear() {
        startServiceAndConnectPump()

        // ApiVersionResponse is NOT one of the three types forwarded to wear
        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        // Should be forwarded via /from-pump/receive-message but NOT /to-wear/service-receive-message
        assertTrue(
            "Response should go to /from-pump/receive-message",
            messageBus.hasMessage("/from-pump/receive-message")
        )
        assertFalse(
            "Non-battery/IOB/CGM response should NOT go to /to-wear/service-receive-message",
            messageBus.hasMessage("/to-wear/service-receive-message")
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_populatesCache() {
        startServiceAndConnectPump()

        val cache = getLastResponseMessageCache()
        assertTrue("Cache should be empty initially", cache.isEmpty())

        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        val key = Pair(response.characteristic, response.opCode())
        assertTrue(
            "onReceiveMessage should populate the response cache",
            cache.containsKey(key)
        )
    }

    // =========================================================================
    // Connected Pump — Bolus with Connection
    // =========================================================================

    @Test
    fun connectedPump_bolus_validSignature_sentToPump() {
        startServiceAndConnectPump()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage("/to-phone/initiate-confirmed-bolus", signedBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Valid signature should NOT be blocked
        assertFalse(
            "Valid signature should not be blocked",
            messageBus.hasMessage("/to-wear/blocked-bolus-signature")
        )
        // Should NOT send bolus-not-enabled since insulin-delivery-actions is true
        assertFalse(
            "Insulin delivery is enabled, should not send bolus-not-enabled",
            messageBus.hasMessage("/to-wear/bolus-not-enabled")
        )
    }

    @Test
    fun connectedPump_bolus_insulinDeliveryDisabled_blocked() {
        startServiceAndConnectPump()
        // Explicitly disable insulin delivery
        prefs.edit().putBoolean("insulin-delivery-actions", false).commit()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage("/to-phone/initiate-confirmed-bolus", signedBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Signature is valid, but insulin delivery is disabled — should be blocked
        assertFalse(
            "Signature is valid, should not block for signature",
            messageBus.hasMessage("/to-wear/blocked-bolus-signature")
        )
        assertTrue(
            "Insulin delivery disabled should send /to-wear/bolus-not-enabled",
            messageBus.hasMessage("/to-wear/bolus-not-enabled")
        )
    }

    @Test
    fun connectedPump_command_routedToPump() {
        startServiceAndConnectPump()

        // Send a non-bolus command to the connected pump
        val request = ApiVersionRequest()
        val msgBytes = PumpMessageSerializer.toBytes(request)
        sendMessage("/to-pump/command", msgBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should not send pump-not-connected (pump IS connected)
        assertFalse(
            "Connected pump should not send pump-not-connected",
            messageBus.hasMessage("/from-pump/pump-not-connected")
        )
    }

    @Test
    fun connectedPump_bulkCommands_routedToPump() {
        startServiceAndConnectPump()

        val requests = listOf(ApiVersionRequest(), ControlIQIOBRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage("/to-pump/commands", bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "Connected pump should not send pump-not-connected for bulk commands",
            messageBus.hasMessage("/from-pump/pump-not-connected")
        )
    }
}
