package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.LineInfoChip

@Immutable
data class CgmSensorRowModel(
    val sessionState: String?,
    val expireRelative: String?,
    val expireExact: String?,
)

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun LandingCgmSensorRow() {
    var showExact by remember { mutableStateOf(false) }
    val store = LocalDataStore.current
    val model = CgmSensorRowModel(
        sessionState = store.cgmSessionState.observeAsState().value,
        expireRelative = store.cgmSessionExpireRelative.observeAsState().value,
        expireExact = store.cgmSessionExpireExact.observeAsState().value,
    )

    val text = when (model.sessionState) {
        null -> "?"
        "Active" -> if (showExact) "${model.expireExact}" else "${model.expireRelative}"
        else -> "${model.sessionState}"
    }

    LineInfoChip("CGM Sensor", text, onClick = { showExact = !showExact })
}

@Preview
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
private fun LandingCgmSensorRowPreview() {
    LandingCgmSensorRow()
}
