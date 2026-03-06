package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.snakeCaseToSpace
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse.CurrentBolusStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun BolusApprovedPhase(
    showApprovedDialog: Boolean,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    dataStore: DataStore,
    refreshScope: CoroutineScope,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showApprovedDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val bolusCurrentResponse = dataStore.bolusCurrentResponse.observeAsState()

        Alert(
            title = {
                Text(
                    text = when {
                        bolusInitiateResponse.value != null -> if (bolusInitiateResponse.value!!.wasBolusInitiated()) "Bolus Initiated" else "Bolus Rejected by Pump"
                        else -> "Fetching Bolus Status..."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            },
            positiveButton = {},
            icon = {
                Image(painterResource(R.drawable.bolus_icon), "Bolus icon", Modifier.size(24.dp))
            },
            scrollState = scrollState,
        ) {
            LaunchedEffect(Unit) {
                sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                refreshScope.launch {
                    repeat(5) {
                        delay(1000)
                        sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                    }
                }
            }

            LaunchedEffect(bolusCurrentResponse.value) {
                Timber.i("bolusCurrentResponse: ${bolusCurrentResponse.value}")
                if (bolusCurrentResponse.value?.bolusId != 0) {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                    refreshScope.launch {
                        repeat(5) {
                            delay(1000)
                            sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                        }
                    }
                }
            }

            Text(
                text = when {
                    bolusInitiateResponse.value != null -> if (bolusInitiateResponse.value!!.wasBolusInitiated()) {
                        "The ${bolusFinalParameters.value?.let { twoDecimalPlaces(it.units) }}u bolus ${
                            when (bolusCurrentResponse.value) {
                                null -> "was requested."
                                else -> when (bolusCurrentResponse.value!!.status) {
                                    CurrentBolusStatus.REQUESTING -> "is being prepared."
                                    CurrentBolusStatus.DELIVERING -> "is being delivered."
                                    else -> "was completed."
                                }
                            }
                        }"
                    } else {
                        "The bolus could not be delivered: ${bolusInitiateResponse.value?.let { snakeCaseToSpace(it.statusType.toString()) }}"
                    }

                    else -> "The bolus status is unknown. Please check your pump to identify the status of the bolus."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
