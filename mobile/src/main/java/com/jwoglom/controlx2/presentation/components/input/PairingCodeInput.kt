package com.jwoglom.controlx2.presentation.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import timber.log.Timber

private const val LONG_PAIRING_CODE_LENGTH = 16
private const val SHORT_PAIRING_CODE_LENGTH = 6

@Composable
fun LongPairingCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val initialValue = sanitizeLongPairingCode(value)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialValue, selection = TextRange(initialValue.length)))
    }

    LaunchedEffect(value) {
        val sanitized = sanitizeLongPairingCode(value)
        if (value != sanitized) {
            onValueChange(sanitized)
        }
        textFieldValue = TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Timber.w(e)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val code = textFieldValue.text

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            val sanitized = sanitizeLongPairingCode(it.text)
            if (code.length < LONG_PAIRING_CODE_LENGTH && sanitized.length == LONG_PAIRING_CODE_LENGTH) {
                focusManager.clearFocus()
            }
            textFieldValue = TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))
            if (sanitized != value) {
                onValueChange(sanitized)
            }
        },
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Ascii,
        ),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
            ) {
                repeat(4) { index ->
                    val chars = code.chunked(4).getOrNull(index).orEmpty()
                    val isFocused = index == (code.length / 4)

                    Text(
                        text = chars,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(70.dp)
                            .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(
                                if (isFocused) 2.dp else 1.dp,
                                if (isFocused) colorScheme.primary else colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(2.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    if (index != 3) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    )
}

@Composable
fun ShortPairingCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onClearSavedPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val initialValue = sanitizeShortPairingCode(value)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialValue, selection = TextRange(initialValue.length)))
    }

    LaunchedEffect(value) {
        val sanitized = sanitizeShortPairingCode(value)
        if (value != sanitized) {
            onValueChange(sanitized)
        }
        textFieldValue = TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Timber.w(e)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val code = textFieldValue.text
    val hasInvalidSavedValue = value.any { !it.isDigit() } || value.length > SHORT_PAIRING_CODE_LENGTH

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = {
                val sanitized = sanitizeShortPairingCode(it.text)
                if (code.length < SHORT_PAIRING_CODE_LENGTH && sanitized.length == SHORT_PAIRING_CODE_LENGTH) {
                    focusManager.clearFocus()
                }
                textFieldValue = TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))
                if (sanitized != value) {
                    onValueChange(sanitized)
                }
            },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Number,
            ),
            modifier = Modifier.focusRequester(focusRequester),
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth()
                ) {
                    repeat(SHORT_PAIRING_CODE_LENGTH) { index ->
                        val chars = code.getOrNull(index)?.toString().orEmpty()
                        val isFocused = index == code.length

                        Text(
                            text = chars,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(30.dp)
                                .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(
                                    if (isFocused) 2.dp else 1.dp,
                                    if (isFocused) colorScheme.primary else colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(2.dp),
                        )
                        if (index != SHORT_PAIRING_CODE_LENGTH - 1) {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                }
            }
        )

        if (hasInvalidSavedValue) {
            Text(
                text = "Saved PIN was invalid and has been sanitized.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
            )
        }

        if (code.isNotEmpty() || hasInvalidSavedValue) {
            TextButton(onClick = onClearSavedPin) {
                Text("Clear saved PIN")
            }
        }
    }
}

private fun sanitizeLongPairingCode(text: String): String {
    return text.filter { it.isLetterOrDigit() }.take(LONG_PAIRING_CODE_LENGTH)
}

private fun sanitizeShortPairingCode(text: String): String {
    return text.filter { it.isDigit() }.take(SHORT_PAIRING_CODE_LENGTH)
}
