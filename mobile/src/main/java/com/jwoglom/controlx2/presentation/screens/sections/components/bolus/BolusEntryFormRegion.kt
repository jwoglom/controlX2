package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
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
}
