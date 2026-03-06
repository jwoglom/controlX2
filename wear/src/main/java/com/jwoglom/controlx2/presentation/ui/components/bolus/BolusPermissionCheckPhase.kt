package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.ui.IndeterminateProgressIndicator

@Composable
fun BolusPermissionCheckPhase(
    showPermissionCheckDialog: Boolean,
    onDismiss: () -> Unit,
    dataStore: DataStore,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showPermissionCheckDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
        IndeterminateProgressIndicator(
            text = bolusFinalParameters.value?.units?.let { "Requesting permission" } ?: "Invalid request!"
        )
    }
}
