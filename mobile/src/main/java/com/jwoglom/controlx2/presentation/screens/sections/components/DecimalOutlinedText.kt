package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.jwoglom.controlx2.presentation.util.onFocusSelectAll
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecimalOutlinedText(
    title: String,
    value: String?,
    onValueChange: (String) -> Unit,
    onSubmitRequested: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions? = null,
    modifier: Modifier = Modifier
) {
    var error = false
    val decimalPlaces = 2

    var textFieldValue = remember {
        mutableStateOf(TextFieldValue(value ?: ""))
    }
    val localeDecimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

    LaunchedEffect (value) {
        textFieldValue.value = textFieldValue.value.copy(text = value ?: "")
    }

    OutlinedTextField(
        value = textFieldValue.value,
        onValueChange = { it: TextFieldValue ->
            textFieldValue.value = it
            val text = it.text

            val acceptedDecimalSeparators = setOf('.', ',', localeDecimalSeparator)
            val normalized = buildString {
                var decimalSeen = false
                text.forEach { char ->
                    when {
                        char in '0'..'9' -> append(char)
                        char in acceptedDecimalSeparators && !decimalSeen -> {
                            append('.')
                            decimalSeen = true
                        }
                    }
                }
            }

            var filtered = normalized
            val dotIndex = filtered.indexOf('.')
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
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions ?: KeyboardActions(
            onDone = {
                onSubmitRequested?.invoke()
            }
        ),
        label = {
            Text(title)
        },
        isError = error,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth().onFocusSelectAll(textFieldValue)
    )
}
