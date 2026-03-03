package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.LineInfoChip
import com.jwoglom.controlx2.shared.enums.UserMode

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun LandingControlIQRow() {
    val store = LocalDataStore.current
    val status = store.controlIQStatus.observeAsState().value
    val mode = store.controlIQMode.observeAsState().value
    val text = when (mode) {
        UserMode.SLEEP, UserMode.EXERCISE -> "$mode"
        else -> status?.toString() ?: "?"
    }
    LineInfoChip("Control-IQ", text)
}

@Preview
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
private fun LandingControlIQRowPreview() {
    LandingControlIQRow()
}
