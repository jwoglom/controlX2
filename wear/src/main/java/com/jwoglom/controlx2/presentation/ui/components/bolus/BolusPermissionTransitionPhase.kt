package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import com.jwoglom.controlx2.presentation.DataStore

@Composable
fun BolusPermissionTransitionPhase(
    showPermissionCheckDialog: Boolean,
    onPermissionAccepted: () -> Unit,
    dataStore: DataStore,
) {
    val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()
    LaunchedEffect(bolusPermissionResponse.value) {
        if (bolusPermissionResponse.value != null && showPermissionCheckDialog) {
            onPermissionAccepted()
        }
    }
}
