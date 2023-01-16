@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.wearx2.presentation.screens.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Chip
import androidx.compose.material.LocalTextStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.wearx2.presentation.screens.BolusPreview

@Composable
fun BolusWindow() {
    var carbsValue by remember { mutableStateOf<Int?>(null) }
    var glucoseValue by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PaddingValues(top = 4.dp))
    ) {
        OutlinedTextField(
            value = when (carbsValue) {
                null -> ""
                else -> "$carbsValue"
            },
            onValueChange = { carbsValue = it.toIntOrNull() },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Number,
            ),
            label = {
                Text("Carbs (grams)")
            },
            modifier = Modifier.fillMaxWidth(fraction = 0.5f)
        )

        OutlinedTextField(
            value = when (glucoseValue) {
                null -> ""
                else -> "$glucoseValue"
            },
            onValueChange = { glucoseValue = it.toIntOrNull() },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Number,
            ),
            label = {
                Text("BG (mg/dL)")
            },
            modifier = Modifier.fillMaxWidth(fraction = 0.5f)
        )
    }
}

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}