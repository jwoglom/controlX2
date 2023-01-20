@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.wearx2.presentation.screens.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.wearx2.presentation.screens.BolusPreview
import com.jwoglom.wearx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.wearx2.presentation.screens.sections.components.IntegerOutlinedText

@Composable
fun BolusWindow() {
    var unitsRawValue by remember { mutableStateOf<String?>(null) }
    var carbsRawValue by remember { mutableStateOf<String?>(null) }
    var glucoseRawValue by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.75f)) {
        }
        Column(Modifier.weight(1f)) {
            DecimalOutlinedText(
                title = "Units",
                value = unitsRawValue,
                onValueChange = { unitsRawValue = it }
            )
        }
        Column(Modifier.weight(0.75f)) {
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(all = 16.dp)) {
            IntegerOutlinedText(
                title = "Carbs (grams)",
                value = carbsRawValue,
                onValueChange = { carbsRawValue = it }
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(all = 16.dp)) {
            IntegerOutlinedText(
                title = "BG (mg/dL)",
                value = glucoseRawValue,
                onValueChange = { glucoseRawValue = it }
            )
        }
    }
}

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}