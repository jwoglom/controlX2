package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HeaderLine(
    text: String
) {
    Line(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(all = 20.dp))
    Divider()
}