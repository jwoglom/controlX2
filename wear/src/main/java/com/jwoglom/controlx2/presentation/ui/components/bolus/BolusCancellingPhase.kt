package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.dialog.Dialog
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.ui.IndeterminateProgressIndicator

@Composable
fun BolusCancellingPhase(
    showCancellingDialog: Boolean,
    onDismiss: () -> Unit,
    onCancelled: () -> Unit,
    dataStore: DataStore,
) {
    val scrollState = rememberScalingLazyListState()
    Dialog(showDialog = showCancellingDialog, onDismissRequest = onDismiss, scrollState = scrollState) {
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
        LaunchedEffect(bolusCancelResponse.value) {
            if (bolusCancelResponse.value != null) {
                onCancelled()
            }
        }
        IndeterminateProgressIndicator(text = "The bolus is being cancelled..")
    }
}
