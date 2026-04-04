package com.jwoglom.controlx2.clientcomm

import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientMessageHandlerTest {

    private lateinit var stateStore: ClientStateStore
    private lateinit var sideEffects: ClientSideEffects
    private lateinit var handler: ClientMessageHandler
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        stateStore = mockk(relaxed = true)
        sideEffects = mockk(relaxed = true)
        handler = ClientMessageHandler(
            stateStore = stateStore,
            sideEffects = sideEffects,
            scope = testScope,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `FROM_PUMP_PUMP_CONNECTED sets state and notifies`() {
        handler.handleMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED, "".toByteArray())

        verify {
            stateStore.connectionState = ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED
            sideEffects.onConnectionStateChanged(ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED)
        }
    }

    @Test
    fun `FROM_PUMP_PUMP_DISCONNECTED sets state and notifies`() {
        handler.handleMessage(MessagePaths.FROM_PUMP_PUMP_DISCONNECTED, "".toByteArray())

        verify {
            stateStore.connectionState = ClientConnectionState.HOST_CONNECTED_PUMP_DISCONNECTED
            sideEffects.onConnectionStateChanged(ClientConnectionState.HOST_CONNECTED_PUMP_DISCONNECTED)
        }
    }

    @Test
    fun `TO_CLIENT_OPEN_ACTIVITY calls onOpenActivityRequested`() {
        handler.handleMessage(MessagePaths.TO_CLIENT_OPEN_ACTIVITY, "".toByteArray())

        verify { sideEffects.onOpenActivityRequested() }
    }

    @Test
    fun `TO_CLIENT_BLOCKED_BOLUS_SIGNATURE calls onBolusBlockedSignature`() {
        handler.handleMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE, "".toByteArray())

        verify { sideEffects.onBolusBlockedSignature() }
    }

    @Test
    fun `TO_CLIENT_BOLUS_NOT_ENABLED calls onBolusNotEnabled`() {
        handler.handleMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED, "".toByteArray())

        verify { sideEffects.onBolusNotEnabled() }
    }

    @Test
    fun `TO_CLIENT_GLUCOSE_UNIT with valid unit calls updateGlucoseUnit and notifies`() {
        handler.handleMessage(MessagePaths.TO_CLIENT_GLUCOSE_UNIT, GlucoseUnit.MMOL.name.toByteArray())

        verify {
            stateStore.updateGlucoseUnit(GlucoseUnit.MMOL)
            sideEffects.onGlucoseUnitUpdated()
        }
    }

    @Test
    fun `TO_CLIENT_GLUCOSE_UNIT with invalid unit does not crash`() {
        handler.handleMessage(MessagePaths.TO_CLIENT_GLUCOSE_UNIT, "INVALID_UNIT".toByteArray())

        // fromName returns null for invalid names, so no calls should happen
        verify(exactly = 0) { stateStore.updateGlucoseUnit(any()) }
        verify(exactly = 0) { sideEffects.onGlucoseUnitUpdated() }
    }

    @Test
    fun `unknown path causes no side effects`() {
        handler.handleMessage("/unknown/path", "test".toByteArray())

        verify(exactly = 0) { sideEffects.onConnectionStateChanged(any()) }
        verify(exactly = 0) { sideEffects.onOpenActivityRequested() }
        verify(exactly = 0) { sideEffects.onBolusBlockedSignature() }
        verify(exactly = 0) { sideEffects.onBolusNotEnabled() }
        verify(exactly = 0) { sideEffects.onGlucoseUnitUpdated() }
        verify(exactly = 0) { sideEffects.onPumpDataUpdated(any()) }
    }
}
