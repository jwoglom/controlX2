package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import android.content.Context
import android.os.Handler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.snakeCaseToSpace
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun BolusDeliverActionRegion(
    refreshing: Boolean,
    bolusButtonEnabled: Boolean,
    bolusUnits: Double?,
    onPerformPermissionCheck: () -> Unit,
    onPrepareFinalParameters: () -> Unit,
    isValidBolus: () -> Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Button(
                onClick = {
                    if (isValidBolus()) {
                        onPrepareFinalParameters()
                        onPerformPermissionCheck()
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = bolusButtonEnabled,
                colors = if (isSystemInDarkTheme()) {
                    ButtonDefaults.filledTonalButtonColors(containerColor = Color.LightGray)
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Image(
                    painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    "Deliver ${bolusUnits?.let { "${twoDecimalPlaces(it)}u " }}bolus",
                    fontSize = 18.sp,
                    color = if (isSystemInDarkTheme()) Color.Black else Color.Unspecified
                )
            }
        }
    }
}

@Composable
fun BolusPermissionDialogRegion(
    showPermissionCheckDialog: Boolean,
    onDismiss: () -> Unit,
    onShowInProgress: () -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Long, Long) -> Unit,
) {
    if (!showPermissionCheckDialog) {
        return
    }
    val dataStore = LocalDataStore.current
    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()
    val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
    val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()

    fun sendBolusRequest(
        bolusParameters: BolusParameters?,
        unitBreakdown: BolusCalcUnits?,
        dataSnapshot: BolusCalcDataSnapshotResponse?,
        timeSinceReset: TimeSinceResetResponse?
    ) {
        if (bolusParameters == null || dataStore.bolusPermissionResponse.value == null || dataStore.bolusCalcDataSnapshot.value == null || unitBreakdown == null || dataSnapshot == null || timeSinceReset == null) {
            Timber.w("sendBolusRequest: null parameters")
            return
        }

        val bolusId = dataStore.bolusPermissionResponse.value!!.bolusId

        // Calculate extended bolus volumes
        val extendedEnabled = dataStore.bolusExtendedEnabled.value == true
        val durationMinutes = dataStore.bolusExtendedDurationMinutes.value?.toIntOrNull() ?: 0
        val percentNow = dataStore.bolusExtendedPercentNow.value // null = all extended

        var extendedVolume = 0L
        var extendedSeconds = 0L
        var immediateParams = bolusParameters

        if (extendedEnabled && durationMinutes > 0) {
            val totalUnits1000 = InsulinUnit.from1To1000(bolusParameters.units)
            extendedSeconds = durationMinutes.toLong() * 60L

            if (percentNow == null) {
                // All extended: no immediate portion
                extendedVolume = totalUnits1000
                immediateParams = BolusParameters(0.0, bolusParameters.carbsGrams, bolusParameters.glucoseMgdl)
            } else {
                // Split: percentNow delivered immediately, rest extended
                val immediateUnits1000 = totalUnits1000 * percentNow / 100
                extendedVolume = totalUnits1000 - immediateUnits1000
                immediateParams = BolusParameters(
                    InsulinUnit.from1000To1(immediateUnits1000),
                    bolusParameters.carbsGrams,
                    bolusParameters.glucoseMgdl
                )
            }
        }

        Timber.i("sendBolusRequest: sending bolus request to phone: bolusId=$bolusId bolusParameters=$immediateParams extendedVolume=$extendedVolume extendedSeconds=$extendedSeconds unitBreakdown=$unitBreakdown dataSnapshot=$dataSnapshot timeSinceReset=$timeSinceReset")
        sendServiceBolusRequest(bolusId, immediateParams, unitBreakdown, dataSnapshot, timeSinceReset, extendedVolume, extendedSeconds)
    }

    val extendedEnabled = dataStore.bolusExtendedEnabled.observeAsState(false).value
    val extDurationMin = dataStore.bolusExtendedDurationMinutes.observeAsState().value?.toIntOrNull() ?: 0
    val extPercentNow = dataStore.bolusExtendedPercentNow.observeAsState().value

    val dialogTitle = bolusCurrentParameters.value?.units?.let { totalUnits ->
        if (extendedEnabled && extDurationMin > 0) {
            if (extPercentNow == null) {
                "Deliver ${twoDecimalPlaces(totalUnits)}u extended over ${extDurationMin}min?"
            } else {
                val nowUnits = totalUnits * extPercentNow / 100.0
                val extUnits = totalUnits - nowUnits
                "Deliver ${twoDecimalPlaces(nowUnits)}u now + ${twoDecimalPlaces(extUnits)}u over ${extDurationMin}min?"
            }
        } else {
            "Deliver ${twoDecimalPlaces(totalUnits)}u bolus?"
        }
    } ?: "Deliver bolus?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(dialogTitle)
        },
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    bolusFinalParameters.value?.let { finalParameters ->
                        bolusPermissionResponse.value?.let { permissionResponse ->
                            if (permissionResponse.isPermissionGranted && finalParameters.units >= 0.05) {
                                onShowInProgress()
                                sendBolusRequest(
                                    dataStore.bolusFinalParameters.value,
                                    dataStore.bolusFinalCalcUnits.value,
                                    dataStore.bolusCalcDataSnapshot.value,
                                    dataStore.timeSinceResetResponse.value,
                                )
                            }
                        }
                    }
                },
                enabled = (
                    bolusPermissionResponse.value?.isPermissionGranted == true &&
                        bolusFinalParameters.value?.takeIf { it.units >= 0.05 } != null
                    )
            ) {
                Text("Deliver")
            }
        }
    )
}

@Composable
fun InProgressDialogRegion(
    onShowApproved: () -> Unit,
    onShowCancelled: () -> Unit,
    onCancel: () -> Unit,
    context: Context,
) {
    val dataStore = LocalDataStore.current
    val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
    val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()
    val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

    val bolusMinNotifyThreshold = Prefs(context).bolusConfirmationInsulinThreshold()

    LaunchedEffect(bolusInitiateResponse.value) {
        if (bolusInitiateResponse.value != null) {
            onShowApproved()
        }
    }

    LaunchedEffect(bolusCancelResponse.value) {
        if (bolusCancelResponse.value != null) {
            onShowCancelled()
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        title = {
            Text("Requesting ${bolusCurrentParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus")
        },
        text = {
            Text(
                when {
                    bolusInitiateResponse.value != null -> "Bolus request received by pump, waiting for response..."
                    bolusFinalParameters.value != null -> when {
                        bolusFinalParameters.value!!.units >= bolusMinNotifyThreshold -> "A notification was sent to approve the request."
                        else -> "Sending request to pump..."
                    }
                    else -> "Sending request to pump..."
                }
            )
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Cancel Bolus Delivery")
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ApprovedDialogRegion(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refreshScopeLaunch: (suspend () -> Unit) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val dataStore = LocalDataStore.current
    val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
    val bolusCurrentResponse = dataStore.bolusCurrentResponse.observeAsState()
    val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        title = {
            Text(
                when {
                    bolusInitiateResponse.value != null -> when {
                        bolusInitiateResponse.value!!.wasBolusInitiated() -> "Bolus Initiated"
                        else -> "Bolus Rejected by Pump"
                    }
                    else -> "Fetching Bolus Status..."
                }
            )
        },
        text = {
            LaunchedEffect(Unit) {
                sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                refreshScopeLaunch {
                    var requestCount = 0
                    while (requestCount < 60) {
                        delay(1000)
                        val currentResponse = dataStore.bolusCurrentResponse.value
                        if (currentResponse?.bolusId == 0) {
                            Timber.d("BolusWindow: bolusId is 0, stopping status polling")
                            break
                        }
                        sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                        requestCount++
                    }
                }
            }

            LaunchedEffect(bolusCurrentResponse.value) {
                Timber.i("bolusCurrentResponse: ${bolusCurrentResponse.value}")
                if (bolusCurrentResponse.value?.bolusId != 0) {
                    refreshScopeLaunch {
                        repeat(5) {
                            delay(1000)
                            val currentResponse = dataStore.bolusCurrentResponse.value
                            if (currentResponse?.bolusId == 0) {
                                Timber.d("BolusWindow: bolusId is 0, stopping status polling")
                                return@repeat
                            }
                            sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                        }
                    }
                }
            }

            Text(
                text = when {
                    bolusInitiateResponse.value != null -> when {
                        bolusInitiateResponse.value!!.wasBolusInitiated() -> "The ${bolusFinalParameters.value?.let { twoDecimalPlaces(it.units) }}u bolus ${
                            when (bolusCurrentResponse.value) {
                                null -> "was requested."
                                else -> when (bolusCurrentResponse.value!!.status) {
                                    CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "is being prepared."
                                    CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "is being delivered."
                                    else -> "was completed."
                                }
                            }
                        }"
                        else -> "The bolus could not be delivered: ${
                            bolusInitiateResponse.value?.let { snakeCaseToSpace(it.statusType.toString()) }
                        }"
                    }
                    else -> "The bolus status is unknown. Please check your pump to identify the status of the bolus."
                }
            )
        },
        dismissButton = {
            val bolusStillActive = bolusCurrentResponse.value?.let {
                it.bolusId != 0 && (it.status == CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING ||
                    it.status == CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING)
            } ?: false
            if (bolusStillActive) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Cancel Bolus Delivery")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onClose,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    if (bolusCurrentResponse.value?.bolusId == 0 ||
                        bolusCurrentResponse.value?.status?.let {
                            it != CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING &&
                            it != CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING
                        } == true
                    ) "Done" else "OK"
                )
            }
        }
    )
}

@Composable
fun CancellingDialogRegion(
    onShowCancelled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dataStore = LocalDataStore.current
    val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()

    LaunchedEffect(bolusCancelResponse.value) {
        if (bolusCancelResponse.value != null) {
            onShowCancelled()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        title = {
            Text("Cancelling..")
        },
        text = {
            Text("The bolus is being cancelled...")
        },
        confirmButton = {}
    )
}

@Composable
fun CancelledDialogRegion(
    mainHandler: Handler,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    onDismiss: () -> Unit,
) {
    val dataStore = LocalDataStore.current
    val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
    val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
    val lastBolusStatusResponse = dataStore.lastBolusStatusResponse.observeAsState()

    fun apiVersion(): ApiVersion = PumpState.getPumpAPIVersion() ?: KnownApiVersion.API_V2_5.get()
    fun lastBolusStatusRequest(): Message = LastBolusStatusRequestBuilder.create(apiVersion())

    LaunchedEffect (bolusCancelResponse.value, Unit) {
        Timber.d("showCancelledDialog querying LastBolusStatus")
        sendPumpCommands(SendType.STANDARD, listOf(lastBolusStatusRequest()))
        mainHandler.postDelayed({
            sendPumpCommands(SendType.STANDARD, listOf(lastBolusStatusRequest()))
        }, 500)
    }

    fun matchesBolusId(): Boolean? {
        lastBolusStatusResponse.value?.let { last ->
            bolusInitiateResponse.value?.let { initiate ->
                return (last.bolusId == initiate.bolusId)
            }
        }
        return null
    }

    LaunchedEffect (lastBolusStatusResponse.value) {
        Timber.d("showCancelledDialog lastBolusStatusResponse effect: ${matchesBolusId()}")
        if (matchesBolusId() == false) {
            mainHandler.postDelayed({
                Timber.d("showCancelledDialog lastBolusStatus postDelayed")
                sendPumpCommands(
                    SendType.STANDARD,
                    listOf(lastBolusStatusRequest())
                )
            }, 500)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        title = {
            Text(when (bolusCancelResponse.value?.status) {
                CancelBolusResponse.CancelStatus.SUCCESS ->
                    "Bolus Cancelled"
                CancelBolusResponse.CancelStatus.FAILED ->
                    when (bolusInitiateResponse.value) {
                        null -> "Bolus Cancelled"
                        else -> "Could Not Be Cancelled"
                    }
                else -> "Bolus Status Unknown"
            })
        },
        text = {
            Text(
                "${when (bolusCancelResponse.value?.status) {
                    CancelBolusResponse.CancelStatus.SUCCESS ->
                        "The bolus was cancelled."
                    CancelBolusResponse.CancelStatus.FAILED ->
                        when (bolusInitiateResponse.value) {
                            null -> "A bolus request was not sent to the pump, so there is nothing to cancel."
                            else -> "The bolus could not be cancelled: ${
                                snakeCaseToSpace(
                                    bolusCancelResponse.value?.reason.toString()
                                )
                            }"
                        }
                    else -> "Please check your pump to confirm whether the bolus was cancelled."
                }}\n\n${when {
                    matchesBolusId() == true ->
                        lastBolusStatusResponse.value?.deliveredVolume?.let {
                            if (it == 0L) "A bolus was started and no insulin was delivered." else "${twoDecimalPlaces1000Unit(it)}u was delivered."
                        } ?: ""
                    matchesBolusId() == false -> "No insulin was delivered."
                    else -> "Checking if any insulin was delivered..."
                }}"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
