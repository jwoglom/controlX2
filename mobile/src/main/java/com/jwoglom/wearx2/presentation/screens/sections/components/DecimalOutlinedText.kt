package com.jwoglom.wearx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecimalOutlinedText(
    title: String,
    value: String?,
    onValueChange: (String) -> Unit
) {
    var error = false
    val decimalPlaces = 2

    OutlinedTextField(
        value = when (value) {
            null -> ""
            else -> value
        },
        onValueChange = {
            var filtered = it.filter { (it in '0'..'9') || it == '.' }
            val dotIndex = filtered.lastIndexOf('.')
            if (dotIndex >= 0 && filtered.length - dotIndex > decimalPlaces+1) {
                filtered = filtered.substring(0, dotIndex + decimalPlaces)
            }
            error = (filtered.toDoubleOrNull() == null)
            onValueChange(filtered)
        },
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Number,
        ),
        label = {
            Text(title)
        },
        isError = error,
        modifier = Modifier.fillMaxWidth()
    )
}