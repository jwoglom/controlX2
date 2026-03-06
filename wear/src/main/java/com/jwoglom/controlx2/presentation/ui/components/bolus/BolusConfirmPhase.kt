package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces

@Composable
fun BolusConfirmPhase(
    showConfirmDialog: Boolean,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
    dataStore: DataStore,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showConfirmDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
        val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()

        Alert(
            title = {
                Text(
                    text = bolusFinalParameters.value?.units?.let { "${twoDecimalPlaces(it)}u Bolus" } ?: "",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(onClick = onReject, colors = ButtonDefaults.secondaryButtonColors()) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Do not deliver bolus")
                }
            },
            positiveButton = {
                bolusFinalParameters.value?.let { finalParameters ->
                    bolusPermissionResponse.value?.let { permissionResponse ->
                        if (permissionResponse.isPermissionGranted && finalParameters.units >= 0.05) {
                            Button(onClick = onConfirm, colors = ButtonDefaults.primaryButtonColors()) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = "Deliver bolus")
                            }
                        }
                    }
                }
            },
            icon = {
                Image(painterResource(R.drawable.bolus_icon), "Bolus icon", Modifier.size(24.dp))
            },
            scrollState = scrollState,
        ) {
            Text(
                text = bolusPermissionResponse.value?.let {
                    when {
                        bolusFinalParameters.value?.units == null -> ""
                        bolusFinalParameters.value!!.units < 0.05 -> "Insulin amount too small."
                        it.status == 0 -> "Do you want to deliver the bolus?"
                        else -> "Cannot deliver bolus: ${it.nackReason}"
                    }
                } ?: "",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
