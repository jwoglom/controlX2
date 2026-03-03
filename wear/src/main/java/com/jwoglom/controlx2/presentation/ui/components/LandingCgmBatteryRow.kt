package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.LineInfoChip

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun LandingCgmBatteryRow() {
    val status = LocalDataStore.current.cgmTransmitterStatus.observeAsState().value
    LineInfoChip("CGM Battery", status?.toString() ?: "?")
}

@Preview
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
private fun LandingCgmBatteryRowPreview() {
    LandingCgmBatteryRow()
}
