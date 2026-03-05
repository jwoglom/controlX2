package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.LineInfoChip
import com.jwoglom.controlx2.shared.enums.BasalStatus

@Immutable
data class BasalRowModel(
    val basalRate: String?,
    val basalStatus: BasalStatus?,
)

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun LandingBasalRow() {
    val store = LocalDataStore.current
    val model = BasalRowModel(
        basalRate = store.basalRate.observeAsState().value,
        basalStatus = store.basalStatus.observeAsState().value,
    )
    val text = when (model.basalStatus) {
        BasalStatus.ON,
        BasalStatus.ZERO,
        BasalStatus.CONTROLIQ_INCREASED,
        BasalStatus.CONTROLIQ_REDUCED,
        BasalStatus.UNKNOWN,
        null -> model.basalRate

        BasalStatus.PUMP_SUSPENDED,
        BasalStatus.BASALIQ_SUSPENDED -> model.basalStatus.str

        else -> if (model.basalRate == null) model.basalStatus.str else "${model.basalStatus.str} (${model.basalRate})"
    } ?: "?"

    LineInfoChip("Basal", text)
}

@Preview
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
private fun LandingBasalRowPreview() {
    LandingBasalRow()
}
