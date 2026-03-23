package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.screens.sections.components.IntegerOutlinedText

data class BolusEntrySnapshot(
    val unitsValue: String?,
    val carbsValue: String?,
    val glucoseValue: String?
)

@Composable
fun BolusEntryFormRegion(
    unitsSubtitle: String,
    carbsSubtitle: String,
    glucoseSubtitle: String,
    onUnitsChanged: (String) -> Unit,
    onUnitsFocusChanged: (Boolean) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onGlucoseChanged: (String) -> Unit,
    onSubmitRequested: () -> Unit,
) {
    val dataStore = LocalDataStore.current
    val snapshot = BolusEntrySnapshot(
        unitsValue = dataStore.bolusUnitsRawValue.observeAsState().value,
        carbsValue = dataStore.bolusCarbsRawValue.observeAsState().value,
        glucoseValue = dataStore.bolusGlucoseRawValue.observeAsState().value,
    )

    val extendedEnabled = dataStore.bolusExtendedEnabled.observeAsState(false).value
    val extendedDuration = dataStore.bolusExtendedDurationMinutes.observeAsState().value
    val extendedPercentNow = dataStore.bolusExtendedPercentNow.observeAsState().value

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(0.75f)) {}
        Column(Modifier.weight(1f)) {
            DecimalOutlinedText(
                title = unitsSubtitle,
                value = snapshot.unitsValue,
                onValueChange = onUnitsChanged,
                onSubmitRequested = onSubmitRequested,
                modifier = Modifier.onFocusChanged { onUnitsFocusChanged(it.isFocused) }
            )
        }
        Column(Modifier.weight(0.75f)) {}
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)
        ) {
            IntegerOutlinedText(
                title = carbsSubtitle,
                value = snapshot.carbsValue,
                onValueChange = onCarbsChanged,
                onSubmitRequested = onSubmitRequested
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)
        ) {
            IntegerOutlinedText(
                title = glucoseSubtitle,
                value = snapshot.glucoseValue,
                onValueChange = onGlucoseChanged,
                onSubmitRequested = onSubmitRequested
            )
        }
    }

    // Extended Bolus Controls
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Extended Bolus", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = extendedEnabled,
            onCheckedChange = {
                dataStore.bolusExtendedEnabled.value = it
                if (!it) {
                    dataStore.bolusExtendedDurationMinutes.value = null
                    dataStore.bolusExtendedPercentNow.value = null
                }
            }
        )
    }

    AnimatedVisibility(visible = extendedEnabled) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Mode: All Extended vs Split
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilterChip(
                    selected = extendedPercentNow == null,
                    onClick = { dataStore.bolusExtendedPercentNow.value = null },
                    label = { Text("All Extended") }
                )
                FilterChip(
                    selected = extendedPercentNow != null,
                    onClick = {
                        if (extendedPercentNow == null) {
                            dataStore.bolusExtendedPercentNow.value = 50
                        }
                    },
                    label = { Text("Split") }
                )
            }

            // Split slider
            AnimatedVisibility(visible = extendedPercentNow != null) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "Now: ${extendedPercentNow ?: 50}%  /  Extended: ${100 - (extendedPercentNow ?: 50)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = (extendedPercentNow ?: 50).toFloat(),
                        onValueChange = { dataStore.bolusExtendedPercentNow.value = it.toInt() },
                        valueRange = 0f..100f,
                        steps = 19 // 5% increments
                    )
                }
            }

            // Duration input
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .weight(0.75f)
                        .padding(all = 8.dp)
                ) {}
                Column(
                    Modifier
                        .weight(1f)
                        .padding(all = 8.dp)
                ) {
                    IntegerOutlinedText(
                        title = "Duration (min)",
                        value = extendedDuration,
                        onValueChange = { dataStore.bolusExtendedDurationMinutes.value = it }
                    )
                }
                Column(
                    Modifier
                        .weight(0.75f)
                        .padding(all = 8.dp)
                ) {}
            }
        }
    }
}
