package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.flowlayout.FlowRow
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.FirstRowChip
import com.jwoglom.controlx2.presentation.defaultTheme
import com.jwoglom.controlx2.presentation.greenTheme
import com.jwoglom.controlx2.presentation.redTheme

@Immutable
data class LandingTopRowModel(
    val batteryPercent: Int?,
    val iobUnits: Double?,
    val cartridgeRemainingUnits: Int?,
    val cartridgeRemainingEstimate: Boolean?,
)

@Composable
fun LandingTopRow() {
    val store = LocalDataStore.current
    val model = LandingTopRowModel(
        batteryPercent = store.batteryPercent.observeAsState().value,
        iobUnits = store.iobUnits.observeAsState().value,
        cartridgeRemainingUnits = store.cartridgeRemainingUnits.observeAsState().value,
        cartridgeRemainingEstimate = store.cartridgeRemainingEstimate.observeAsState().value,
    )

    FlowRow {
        FirstRowChip(
            labelText = model.batteryPercent?.let { "$it%" } ?: "?",
            secondaryLabelText = "Battery",
            theme = when {
                model.batteryPercent == null -> defaultTheme
                model.batteryPercent > 50 -> greenTheme
                model.batteryPercent > 25 -> defaultTheme
                else -> redTheme
            },
            numItems = 3,
        )

        FirstRowChip(
            labelText = model.iobUnits?.let { "${String.format("%.1f", it)}u" } ?: "?",
            secondaryLabelText = "IOB",
            theme = defaultTheme,
            numItems = 3,
        )

        FirstRowChip(
            labelText = model.cartridgeRemainingUnits?.let {
                "${it}u${if (model.cartridgeRemainingEstimate == true) "+" else ""}"
            } ?: "?",
            secondaryLabelText = "Cartridge",
            theme = when {
                model.cartridgeRemainingUnits == null -> defaultTheme
                model.cartridgeRemainingUnits > 75 -> greenTheme
                model.cartridgeRemainingUnits > 35 -> defaultTheme
                else -> redTheme
            },
            numItems = 3,
        )
    }
}

@Preview
@Composable
private fun LandingTopRowPreview() {
    LandingTopRow()
}
