package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexcomG6SensorCode(
    title: String,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var error = false

    OutlinedTextField(
        value = when (value) {
            null -> ""
            else -> value
        },
        onValueChange = { it ->
            val filtered = it.filter { c -> (c in '0'..'9') }
            error = filtered.length < it.length || filtered.length != 4
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
        colors = if (isSystemInDarkTheme()) {
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.DarkGray,
                unfocusedTextColor = Color.DarkGray,
                focusedPlaceholderColor = Color.DarkGray,
                unfocusedPlaceholderColor = Color.DarkGray
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = modifier.fillMaxWidth()
    )
}