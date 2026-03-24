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
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse.CancelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BolusCancelledPhase(
    showCancelledDialog: Boolean,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    dataStore: DataStore,
    refreshScope: CoroutineScope,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    fun apiVersion(): ApiVersion = PumpState.getPumpAPIVersion() ?: KnownApiVersion.API_V2_5.get()
    fun lastBolusStatusRequest(): Message = LastBolusStatusRequestBuilder.create(apiVersion())

    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showCancelledDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val lastBolusStatusResponse = dataStore.lastBolusStatusResponse.observeAsState()

        LaunchedEffect(bolusCancelResponse.value, Unit) {
            sendPumpCommands(SendType.STANDARD, listOf(lastBolusStatusRequest()))
        }

        fun matchesBolusId(): Boolean? {
            lastBolusStatusResponse.value?.let { last ->
                bolusInitiateResponse.value?.let { initiate ->
                    return last.bolusId == initiate.bolusId
                }
            }
            return null
        }

        LaunchedEffect(lastBolusStatusResponse.value) {
            if (matchesBolusId() == false) {
                refreshScope.launch {
                    delay(250)
                    sendPumpCommands(SendType.STANDARD, listOf(lastBolusStatusRequest()))
                }
            }
        }

        Alert(
            title = {
                Text(
                    text = when (bolusCancelResponse.value?.status) {
                        CancelStatus.SUCCESS -> "The bolus was cancelled."
                        CancelStatus.FAILED -> when (bolusInitiateResponse.value) {
                            null -> "A bolus request was not sent to the pump, so there is nothing to cancel."
                            else -> "The bolus could not be cancelled: ${snakeCaseToSpace(bolusCancelResponse.value?.reason.toString())}"
                        }

                        else -> "Please check your pump to confirm whether the bolus was cancelled."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            content = {
                Text(
                    text = when {
                        matchesBolusId() == true -> lastBolusStatusResponse.value?.deliveredVolume?.let {
                            if (it == 0L) "A bolus was started and no insulin was delivered." else "${twoDecimalPlaces1000Unit(it)}u was delivered."
                        } ?: ""

                        matchesBolusId() == false -> "No insulin was delivered."
                        else -> "Checking if any insulin was delivered..."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OK")
                }
            },
            positiveButton = {},
            icon = {
                Image(painterResource(R.drawable.bolus_icon), "Bolus icon", Modifier.size(24.dp))
            },
            scrollState = scrollState,
        )
    }
}
