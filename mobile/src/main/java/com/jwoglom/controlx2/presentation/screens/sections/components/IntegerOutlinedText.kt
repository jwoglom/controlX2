package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegerOutlinedText(
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
        onValueChange = {
            val filtered = it.filter { (it in '0'..'9') }
            error = (filtered.toIntOrNull() == null)
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
        colors = if (isSystemInDarkTheme())
            TextFieldDefaults.outlinedTextFieldColors(textColor = Color.DarkGray, placeholderColor = Color.DarkGray)
        else TextFieldDefaults.outlinedTextFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}