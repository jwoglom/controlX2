package com.jwoglom.controlx2.clientcomm

import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

/**
 * Transport-agnostic handler for messages received by a client device from a pump-host.
 * Encapsulates the message routing + state update logic that was previously inline
 * in PhoneCommService.handleMessageReceived() and onPumpMessageReceived().
 *
 * Platform-specific reactions (notifications, complications, activity launches)
 * are delegated to [sideEffects].
 */
class ClientMessageHandler(
    private val stateStore: ClientStateStore,
    private val sideEffects: ClientSideEffects,
    private val scope: CoroutineScope,
) {
    /**
     * Route an incoming message by path. Called from a MessageBus listener.
     */
    fun handleMessage(path: String, data: ByteArray) {
        Timber.d("ClientMessageHandler: $path")
        when (path) {
            MessagePaths.TO_CLIENT_OPEN_ACTIVITY -> {
                sideEffects.onOpenActivityRequested()
            }
            MessagePaths.FROM_PUMP_PUMP_CONNECTED -> {
                stateStore.connectionState = ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED
                sideEffects.onConnectionStateChanged(ClientConnectionState.HOST_CONNECTED_PUMP_CONNECTED)
            }
            MessagePaths.FROM_PUMP_PUMP_DISCONNECTED -> {
                stateStore.connectionState = ClientConnectionState.HOST_CONNECTED_PUMP_DISCONNECTED
                sideEffects.onConnectionStateChanged(ClientConnectionState.HOST_CONNECTED_PUMP_DISCONNECTED)
            }
            MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE -> {
                Timber.w("ClientMessageHandler: blocked bolus signature")
                sideEffects.onBolusBlockedSignature()
            }
            MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED -> {
                Timber.w("ClientMessageHandler: bolus not enabled")
                sideEffects.onBolusNotEnabled()
            }
            MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE -> {
                scope.launch {
                    val message = withContext(Dispatchers.Default) {
                        PumpMessageSerializer.fromBytes(data)
                    }
                    onPumpMessageReceived(message)
                }
            }
            MessagePaths.TO_CLIENT_GLUCOSE_UNIT -> {
                val unitName = String(data)
                val unit = GlucoseUnit.fromName(unitName)
                if (unit != null) {
                    stateStore.updateGlucoseUnit(unit)
                    sideEffects.onGlucoseUnitUpdated()
                }
            }
        }
    }

    private fun onPumpMessageReceived(message: com.jwoglom.pumpx2.pump.messages.Message) {
        Timber.i("ClientMessageHandler onPumpMessageReceived($message)")
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                stateStore.updatePumpBattery("${message.batteryPercent}", Instant.now())
                sideEffects.onPumpDataUpdated("pumpBattery")
            }
            is ControlIQIOBResponse -> {
                stateStore.updatePumpIOB("${InsulinUnit.from1000To1(message.pumpDisplayedIOB)}", Instant.now())
                sideEffects.onPumpDataUpdated("pumpIOB")
            }
            is CurrentEGVGuiDataResponse -> {
                stateStore.updateCgmReading(
                    "${message.cgmReading}",
                    Dates.fromJan12008EpochSecondsToDate(message.bgReadingTimestampSeconds)
                )
                sideEffects.onPumpDataUpdated("cgmReading")
            }
        }
    }
}
