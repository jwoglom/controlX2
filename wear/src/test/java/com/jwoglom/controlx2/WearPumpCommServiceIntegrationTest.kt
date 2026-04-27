package com.jwoglom.controlx2

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.testutil.RecordingMessageBus
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.shared.Hex
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.welie.blessed.BluetoothPeripheral

/**
 * Integration tests for WearPumpCommService — the watch-as-host pump comm service.
 * Mirrors CommServiceIntegrationTest in :mobile, providing a regression harness for
 * the watch-as-host code path.
 *
 * Architecture:
 * - WearPumpCommService.messageBusOverrideForTesting is set before create() so the
 *   service registers its MessageListener on our RecordingMessageBus instead of
 *   WearHybridMessageBus (which requires a real Wear Data Layer).
 * - TandemBluetoothHandler and TandemPumpFinder are mocked via MockK to prevent real
 *   BT scanning while still capturing the TandemPump callback reference for
 *   simulateConnectedPump tests.
 * - WearPrefs reads from the "WearX2" SharedPreferences file, which Robolectric
 *   provides as a real in-memory implementation.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WearPumpCommServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var messageBus: RecordingMessageBus
    private lateinit var serviceController: ServiceController<WearPumpCommService>
    private lateinit var service: WearPumpCommService
    private lateinit var wearPrefs: SharedPreferences

    private var capturedPump: TandemPump? = null
    private val mockPeripheral: BluetoothPeripheral = mockk(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // WearPrefs reads from the "WearX2" prefs file
        wearPrefs = context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
        wearPrefs.edit()
            .putBoolean("tos-accepted", true)
            .putBoolean("service-enabled", true)
            .putBoolean("pumpfinder-service-enabled", false)
            .commit()

        messageBus = RecordingMessageBus()
        WearPumpCommService.messageBusOverrideForTesting = messageBus

        mockkStatic(TandemBluetoothHandler::class)
        val mockBtHandler = mockk<TandemBluetoothHandler>(relaxed = true)
        every { TandemBluetoothHandler.getInstance(any(), any(), any()) } answers {
            capturedPump = secondArg<TandemPump>()
            mockBtHandler
        }

        every { mockPeripheral.name } returns "tslim X2 ***12345"

        mockkConstructor(TandemPumpFinder::class)
        every { anyConstructed<TandemPumpFinder>().startScan() } returns Unit
        every { anyConstructed<TandemPumpFinder>().stop() } returns Unit

        serviceController = Robolectric.buildService(WearPumpCommService::class.java)
    }

    @After
    fun teardown() {
        try { serviceController.destroy() } catch (_: Exception) {}
        WearPumpCommService.messageBusOverrideForTesting = null
        unmockkAll()
        wearPrefs.edit().clear().commit()
    }

    // --- Helpers ---

    private fun startServiceNormal() {
        wearPrefs.edit().putBoolean("pumpfinder-service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()
    }

    private fun startServicePumpFinder() {
        wearPrefs.edit().putBoolean("pumpfinder-service-enabled", true).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()
    }

    private fun send(path: String, data: String = "") {
        service.handleMessageReceived(path, data.toByteArray(), "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun send(path: String, data: ByteArray) {
        service.handleMessageReceived(path, data, "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun startServiceAndConnectPump() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("Pump should have been captured via TandemBluetoothHandler.getInstance()", capturedPump)
        service.simulateConnectedPump(mockPeripheral)
        messageBus.clear()
    }

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    @Test
    fun service_onCreate_normalMode_respondsToStatusWithCommStarted() {
        startServiceNormal()
        send(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(
            "Expected /to-server/comm-started in normal mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
        assertFalse(
            "Should NOT send pump-finder-started in normal mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        )
    }

    @Test
    fun service_onCreate_pumpFinderMode_respondsToStatusWithPumpFinderStarted() {
        startServicePumpFinder()
        send(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(
            "Expected /to-server/pump-finder-started in pump-finder mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        )
        assertFalse(
            "Should NOT send comm-started in pump-finder mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }

    @Test
    fun service_onCreate_tosNotAccepted_shortCircuits() {
        wearPrefs.edit().putBoolean("tos-accepted", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        val statusMessages = messageBus.messagesForPath(MessagePaths.TO_SERVER_COMM_STARTED) +
            messageBus.messagesForPath(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        assertTrue("No status messages expected when TOS not accepted", statusMessages.isEmpty())
    }

    @Test
    fun service_onCreate_serviceDisabled_shortCircuits() {
        wearPrefs.edit().putBoolean("service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        val statusMessages = messageBus.messagesForPath(MessagePaths.TO_SERVER_COMM_STARTED) +
            messageBus.messagesForPath(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        assertTrue("No status messages expected when service disabled", statusMessages.isEmpty())
    }

    @Test
    fun service_onStartCommand_alreadyRunning_noop() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        // Second start should be ignored
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        // Service still functions normally
        send(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
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

        send(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, "init_comm")
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Expected comm-started after pump-finder → normal transition",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
        assertFalse(
            "pumpfinder-service-enabled should be false after transition",
            wearPrefs.getBoolean("pumpfinder-service-enabled", true)
        )
    }

    @Test
    fun pumpFinder_stopPumpFinder_withoutInitComm_stopsOnly() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        send(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, "")
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "No comm-started expected when stopping finder without transitioning",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }

    @Test
    fun pumpFinder_stopPumpFinder_usesPrefsCodeType() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        // Write a specific pairing code type to prefs (must match PairingCodeType enum name / label)
        wearPrefs.edit().putString("pumpfinder-pairing-code-type", "LONG_16CHAR").commit()
        messageBus.clear()

        send(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, "init_comm")
        shadowOf(Looper.getMainLooper()).idle()

        // Transition happened: pumpfinder-service-enabled should be false
        assertFalse(
            "pumpfinder-service-enabled should be false, confirming prefs-code-type was read successfully",
            wearPrefs.getBoolean("pumpfinder-service-enabled", true)
        )
        assertTrue(
            "Expected comm-started to be sent after the transition",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }

    // =========================================================================
    // Pump Connection Lifecycle
    // =========================================================================

    @Test
    fun lifecycle_isPumpConnected_whenNotConnected_sendsPumpNotConnected() {
        startServiceNormal()
        send(MessagePaths.TO_SERVER_IS_PUMP_CONNECTED)

        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Expected pump-not-connected when pump has not connected",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED)
        )
    }

    // =========================================================================
    // Bolus Security
    // =========================================================================

    @Test
    fun bolus_initiateConfirmedBolus_invalidSignature_blocked() {
        startServiceNormal()

        wearPrefs.edit().putString("initiateBolusSecret", "correct-secret").commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes("wrong-secret", bolusRequest)

        send(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Invalid signature should send /to-client/blocked-bolus-signature",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
    }

    @Test
    fun bolus_initiateConfirmedBolus_validSignature_notBlocked() {
        startServiceNormal()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        wearPrefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        send(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "Valid signature should NOT send /to-client/blocked-bolus-signature",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
    }

    @Test
    fun bolus_cancel_clearsBolusPrefs() {
        startServiceNormal()

        wearPrefs.edit()
            .putString("initiateBolusRequest", "test-hex")
            .putString("initiateBolusSecret", "secret")
            .putLong("initiateBolusTime", System.currentTimeMillis())
            .commit()

        send(MessagePaths.TO_SERVER_BOLUS_CANCEL)

        assertNull(
            "initiateBolusRequest should be cleared after cancel",
            wearPrefs.getString("initiateBolusRequest", null)
        )
    }

    @Test
    fun bolus_confirmRequest_wear_setsPrefs() {
        startServiceNormal()
        wearPrefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        wearPrefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        send(MessagePaths.TO_SERVER_BOLUS_REQUEST_WEAR, PumpMessageSerializer.toBytes(bolusRequest))

        assertNotNull(
            "initiateBolusSecret should be set after wear bolus request",
            wearPrefs.getString("initiateBolusSecret", null)
        )
        assertEquals("wear", wearPrefs.getString("initiateBolusSource", null))
    }

    @Test
    fun bolus_confirmRequest_phone_setsPrefs() {
        startServiceNormal()
        wearPrefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        wearPrefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        send(MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE, PumpMessageSerializer.toBytes(bolusRequest))

        assertNotNull(
            "initiateBolusSecret should be set after phone bolus request",
            wearPrefs.getString("initiateBolusSecret", null)
        )
        assertEquals("phone", wearPrefs.getString("initiateBolusSource", null))
    }

    @Test
    fun bolus_insulinDeliveryDisabled_validSignature_blocked() {
        startServiceAndConnectPump()
        // Explicitly disable insulin delivery after connecting
        wearPrefs.edit().putBoolean("insulin-delivery-actions", false).commit()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        wearPrefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        send(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "Insulin delivery disabled: signature is valid so no blocked-signature message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
        assertTrue(
            "Insulin delivery disabled should send /to-client/bolus-not-enabled",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED)
        )
    }

    // =========================================================================
    // Message Forwarding to Client
    // =========================================================================

    @Test
    fun sendWearCommMessage_sendsViaMessageBus() {
        startServiceNormal()
        service.sendWearCommMessage("/test/path", "test-data".toByteArray())
        assertTrue(messageBus.hasMessage("/test/path"))
    }

    @Test
    fun sendWearCommMessage_preservesData() {
        startServiceNormal()
        service.sendWearCommMessage("/test/data-check", "hello-watch".toByteArray())
        val msg = messageBus.lastMessage("/test/data-check")
        assertNotNull(msg)
        assertEquals("hello-watch", msg!!.dataString)
    }

    @Test
    fun sendWearCommMessage_usesCommServiceSender() {
        startServiceNormal()
        service.sendWearCommMessage("/test/sender-check", ByteArray(0))
        val msg = messageBus.lastMessage("/test/sender-check")
        assertNotNull(msg)
        assertEquals(MessageBusSender.COMM_SERVICE, msg!!.sender)
    }

    // =========================================================================
    // Service Status Acknowledgment
    // =========================================================================

    @Test
    fun routing_serviceStatusAcknowledged_stopsPeriodicSender() {
        startServiceNormal()
        send(MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED)
        messageBus.clear()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        assertFalse(
            "No periodic comm-started after acknowledgment",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }

    // =========================================================================
    // Connected Pump
    // =========================================================================

    @Test
    fun connectedPump_isPumpConnected_sendsPumpConnected() {
        startServiceAndConnectPump()
        send(MessagePaths.TO_SERVER_IS_PUMP_CONNECTED)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected /from-pump/pump-connected when pump is connected",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_batteryResponse_forwardedToClient() {
        startServiceAndConnectPump()

        val response = CurrentBatteryV2Response(0, 66, 0, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Battery response should be forwarded to client via /to-client/service-receive-message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_iobResponse_forwardedToClient() {
        startServiceAndConnectPump()

        val response = ControlIQIOBResponse(1450, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "IOB response should be forwarded to client via /to-client/service-receive-message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_cgmResponse_forwardedToClient() {
        startServiceAndConnectPump()

        val response = CurrentEGVGuiDataResponse(1710000000, 123, 1, 2)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "CGM response should be forwarded to client via /to-client/service-receive-message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_otherResponse_notForwardedToClient() {
        startServiceAndConnectPump()

        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "All responses should be recorded via /from-pump/receive-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_MESSAGE)
        )
        assertFalse(
            "Non-battery/IOB/CGM response should NOT be forwarded to /to-client/service-receive-message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_populatesResponseCache() {
        startServiceAndConnectPump()

        assertTrue("Cache should be empty initially", service.pumpCommState.lastResponseMessage.isEmpty())

        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "onReceiveMessage should populate the response cache",
            service.pumpCommState.lastResponseMessage.isEmpty()
        )
    }
}
