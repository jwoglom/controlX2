@file:Suppress("FunctionName", "TestFunctionName")
package com.jwoglom.controlx2.presentation.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.PumpCriticalErrorState
import com.jwoglom.controlx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.controlx2.presentation.screens.PumpSetup
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.screens.sections.Dashboard
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.pump.PumpCriticalErrorClassifier
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import java.time.Instant

/*
 * Snapshot previews for the tier-aware pump-critical-error UI.
 *
 * Three layers:
 *   A. Bare component (PumpSetupStageDescription) — one preview per error case.
 *   B. Initial-pairing surface (PumpSetup) — embedding inside the setup screen.
 *   C. Dashboard reconnect surface (Dashboard) — embedding alongside populated dashboard.
 *
 * State is seeded by mutating the global LocalDataStore singleton, matching the existing
 * preview pattern (see PumpSetup.kt:492 and Landing.kt:600). Every preview calls
 * resetDataStore() first so residual state from a prior preview in the same JVM run
 * doesn't leak in.
 *
 * ErrorPresentation values come from PumpCriticalErrorClassifier.classify() — never
 * hand-built — so snapshots stay aligned with classifier copy changes.
 */

private fun resetDataStore(ds: DataStore) {
    ds.pumpSetupStage.value = PumpSetupStage.WAITING_PUMP_FINDER_INIT
    ds.setupDeviceName.value = null
    ds.setupDeviceModel.value = null
    ds.pumpFinderPumps.value = null
    ds.pumpReadyState.value = null
    ds.setupPairingCodeType.value = null
    ds.pumpCriticalError.value = null
    ds.pumpConnected.value = false
    ds.pumpLastConnectionTimestamp.value = null
    // Dashboard fields seeded by setUpPreviewState — clear so non-Layer-C previews don't render dashboard chrome.
    ds.cgmReading.value = null
    ds.cgmDeltaArrow.value = null
    ds.batteryPercent.value = null
    ds.iobUnits.value = null
    ds.cartridgeRemainingUnits.value = null
    ds.basalStatus.value = null
    ds.controlIQMode.value = null
    // Reset TandemError mutation state. The enum is a JVM singleton whose `extra` and
    // `errorResponse` are mutable across previews.
    for (e in TandemError.values()) {
        e.withExtra("")
        e.withErrorResponse(null)
    }
}

private fun seedErrorState(
    ds: DataStore,
    error: TandemError,
    occurrences: Int = 1,
    stage: PumpSetupStage = PumpSetupStage.PUMPX2_PUMP_DISCONNECTED,
    deviceName: String = "tslim X2 ***789",
    extra: String? = null,
    errorResponse: ErrorResponse? = null,
) {
    ds.pumpSetupStage.value = stage
    ds.setupDeviceName.value = deviceName
    if (extra != null) error.withExtra(extra)
    if (errorResponse != null) error.withErrorResponse(errorResponse)
    val presentation = PumpCriticalErrorClassifier.classify(error)
    val now = Instant.now()
    ds.pumpCriticalError.value = PumpCriticalErrorState(
        presentation = presentation,
        firstSeenAt = now,
        lastSeenAt = now,
        occurrences = occurrences,
        errorStage = stage,
    )
}

/** Mirrors Landing.kt:setUpPreviewState but skips wall-clock so snapshots don't drift. */
private fun seedConnectedState(ds: DataStore) {
    ds.setupDeviceName.value = "tslim X2 ***789"
    ds.setupDeviceModel.value = "X2"
    ds.pumpConnected.value = false // we're previewing the reconnect path
    ds.pumpLastConnectionTimestamp.value = Instant.now().minusSeconds(45)
    ds.cgmReading.value = 123
    ds.cgmDeltaArrow.value = "⬈"
    ds.batteryPercent.value = 50
    ds.iobUnits.value = 0.5
    ds.cartridgeRemainingUnits.value = 100
    ds.basalStatus.value = BasalStatus.ON
    ds.controlIQMode.value = UserMode.EXERCISE
}

@Composable
private fun BareCardWrapper(content: @Composable () -> Unit) {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            // PumpSetupStageDescription emits a flat list of Line()s; needs a Column
            // container with horizontal padding to render legibly outside the LazyColumn
            // it normally lives inside.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun bareCard() {
    PumpSetupStageDescription(initialSetup = false, sendMessage = { _, _ -> })
}

// =====================================================================================
// Layer A — bare component, every error case
// =====================================================================================

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_SetMtu_BelowThreshold_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.SET_MTU_FAILED, occurrences = 1)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_SetMtu_AboveThreshold_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.SET_MTU_FAILED, occurrences = 4)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_NotificationStateFailed_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.NOTIFICATION_STATE_FAILED, occurrences = 3)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_CharWriteFailed_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.CHARACTERISTIC_WRITE_FAILED, occurrences = 4)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_ConnectionUpdateFailed_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.CONNECTION_UPDATE_FAILED, occurrences = 3)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_BtConn_AuthFailure_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.BT_CONNECTION_FAILED, extra = "status=AUTH_FAILURE")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_BtConn_Timeout_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.BT_CONNECTION_FAILED, extra = "status=CONNECTION_TIMEOUT")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_BtConn_Other_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.BT_CONNECTION_FAILED, extra = "status=GATT_ERROR")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_UnexpectedTxId_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.UNEXPECTED_TRANSACTION_ID, occurrences = 2)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_UnexpectedOpcode_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.UNEXPECTED_OPCODE_REPLY)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_UnprocessableMessage_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.UNPROCESSABLE_MESSAGE, extra = "raw bytes: deadbeef")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_PairingCannotBegin_Tslim_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.PAIRING_CANNOT_BEGIN, deviceName = "tslim X2 ***789")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_PairingCannotBegin_Mobi_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.PAIRING_CANNOT_BEGIN, deviceName = "Tandem Mobi ***456")
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_PairingPrompt_RetryAttempt0_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET,
            extra = "retryAttempt=0, bondState=BONDING",
        )
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_PairingPrompt_RetryAttempt2_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET,
            extra = "retryAttempt=2, bondState=BONDING",
        )
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_TconnectShare_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(ds, TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP)
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_InvalidHmac_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.INVALID_SIGNED_HMAC_SIGNATURE,
            extra = "provided pairing code is likely invalid",
        )
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_ErrorResponse_InvalidAuth_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.ERROR_RESPONSE,
            errorResponse = ErrorResponse(0, ErrorResponse.ErrorCode.INVALID_AUTHENTICATION_ERROR.id()),
        )
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_ErrorResponse_TxIdMismatch_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.ERROR_RESPONSE,
            errorResponse = ErrorResponse(0, ErrorResponse.ErrorCode.TRANSACTION_ID_MISMATCH.id()),
        )
        bareCard()
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpCriticalError_ErrorResponse_BadOpcode_Preview() {
    BareCardWrapper {
        val ds = LocalDataStore.current
        resetDataStore(ds)
        seedErrorState(
            ds,
            TandemError.ERROR_RESPONSE,
            errorResponse = ErrorResponse(0x42, ErrorResponse.ErrorCode.BAD_OPCODE.id()),
        )
        bareCard()
    }
}

// =====================================================================================
// Layer B — initial-pairing surface (PumpSetup)
// =====================================================================================

@Composable
private fun PumpSetupSurface() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            PumpSetup(sendMessage = { _, _ -> })
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_PairingCannotBegin_Tslim_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.PAIRING_CANNOT_BEGIN,
        deviceName = "tslim X2 ***789",
        stage = PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_PairingCannotBegin_Mobi_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.PAIRING_CANNOT_BEGIN,
        deviceName = "Tandem Mobi ***456",
        stage = PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_PairingPrompt_RetryAttempt2_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.PAIRING_PROMPT_NOT_ACCEPTED_YET,
        extra = "retryAttempt=2, bondState=BONDING",
        stage = PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_InvalidHmac_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.INVALID_SIGNED_HMAC_SIGNATURE,
        extra = "provided pairing code is likely invalid",
        stage = PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_TconnectShare_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP,
        stage = PumpSetupStage.PUMPX2_PUMP_DISCOVERED,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_BtConn_AuthFailure_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.BT_CONNECTION_FAILED,
        extra = "status=AUTH_FAILURE",
        stage = PumpSetupStage.PUMPX2_PUMP_DISCONNECTED,
    )
    PumpSetupSurface()
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetup_SetMtu_AboveThreshold_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedErrorState(
        ds,
        TandemError.SET_MTU_FAILED,
        occurrences = 4,
        stage = PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION,
    )
    PumpSetupSurface()
}

// =====================================================================================
// Layer C — dashboard reconnect surface
// =====================================================================================

@Composable
private fun DashboardSurface() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Dashboard(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_NoError_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    ds.pumpSetupStage.value = PumpSetupStage.PUMPX2_PUMP_DISCONNECTED
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_SetMtu_BelowThreshold_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.SET_MTU_FAILED, occurrences = 1)
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_SetMtu_AboveThreshold_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.SET_MTU_FAILED, occurrences = 4)
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_BtConn_Timeout_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.BT_CONNECTION_FAILED, extra = "status=CONNECTION_TIMEOUT")
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_BtConn_AuthFailure_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.BT_CONNECTION_FAILED, extra = "status=AUTH_FAILURE")
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_TconnectShare_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.SHARING_CONNECTION_WITH_TCONNECT_APP)
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_UnexpectedTxId_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(ds, TandemError.UNEXPECTED_TRANSACTION_ID, occurrences = 2)
    DashboardSurface()
}

@Preview(showBackground = true)
@Composable
internal fun Dashboard_Reconnect_ErrorResponse_BadOpcode_Preview() {
    val ds = LocalDataStore.current
    resetDataStore(ds)
    seedConnectedState(ds)
    seedErrorState(
        ds,
        TandemError.ERROR_RESPONSE,
        errorResponse = ErrorResponse(0x42, ErrorResponse.ErrorCode.BAD_OPCODE.id()),
    )
    DashboardSurface()
}
