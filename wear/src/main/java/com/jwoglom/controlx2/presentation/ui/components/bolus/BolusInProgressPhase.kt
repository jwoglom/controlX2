package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore

@Composable
fun BolusInProgressPhase(
    showInProgressDialog: Boolean,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onApproved: () -> Unit,
    onCancelled: () -> Unit,
    dataStore: DataStore,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showInProgressDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
        val bolusMinNotifyThreshold = dataStore.bolusMinNotifyThreshold.observeAsState()
        val wearAutoApproveTimeout = dataStore.wearAutoApproveTimeout.observeAsState()

        var countdownSeconds by remember { mutableIntStateOf(wearAutoApproveTimeout.value ?: 0) }

        // Countdown timer for auto-approve
        LaunchedEffect(wearAutoApproveTimeout.value) {
            countdownSeconds = wearAutoApproveTimeout.value ?: 0
            if (countdownSeconds > 0) {
                while (countdownSeconds > 0) {
                    delay(1000)
                    countdownSeconds--
                }
            }
        }

        LaunchedEffect(bolusInitiateResponse.value) {
            if (bolusInitiateResponse.value != null) {
                onApproved()
            }
        }
        LaunchedEffect(bolusCancelResponse.value) {
            if (bolusCancelResponse.value != null) {
                onCancelled()
            }
        }

        Alert(
            title = {
                Text(
                    text = bolusFinalParameters.value?.let { "${it.units}u Bolus" } ?: "",
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
            scrollState = scrollState,
            icon = {
                Image(painterResource(R.drawable.bolus_icon), "Bolus icon", Modifier.size(24.dp))
            }
        ) {
            Text(
                text = when {
                    bolusInitiateResponse.value != null -> "Bolus request received by pump, waiting for response..."
                    bolusFinalParameters.value != null && bolusMinNotifyThreshold.value != null -> when {
                        bolusFinalParameters.value!!.units >= bolusMinNotifyThreshold.value!! ->
                            if (countdownSeconds > 0)
                                "The bolus will begin unless canceled on the phone in $countdownSeconds seconds."
                            else if ((wearAutoApproveTimeout.value ?: 0) > 0)
                                "Auto-approving bolus..."
                            else
                                "A notification was sent to approve the request."
                        else -> "Sending request to pump..."
                    }

                    else -> "Sending request to phone..."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
