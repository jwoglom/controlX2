package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.jwoglom.controlx2.presentation.util.onFocusSelectAll
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecimalOutlinedText(
    title: String,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var error = false
    val decimalPlaces = 2

    var textFieldValue = remember {
        mutableStateOf(TextFieldValue(value ?: ""))
    }

    LaunchedEffect (value) {
        textFieldValue.value = textFieldValue.value.copy(text = value ?: "")
    }

    OutlinedTextField(
        value = textFieldValue.value,
        onValueChange = { it: TextFieldValue ->
            textFieldValue.value = it
            val text = it.text
            var filtered = text.filter { (it in '0'..'9') || it == '.' }
            val dotIndex = filtered.lastIndexOf('.')
            if (dotIndex >= 0 && filtered.length - dotIndex > decimalPlaces + 1) {
                filtered = filtered.substring(0, dotIndex + decimalPlaces + 1)
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
        modifier = modifier.fillMaxWidth().onFocusSelectAll(textFieldValue)
    )
}