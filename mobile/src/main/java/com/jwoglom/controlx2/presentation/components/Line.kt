package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun Line(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        fontWeight = when (bold) {
            true -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        modifier = modifier
    )
}
@Composable
fun Line(
    text: AnnotatedString,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        fontWeight = when (bold) {
            true -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        modifier = modifier
    )
}