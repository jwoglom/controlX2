package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadSpinner(loadingText: String = "Loading...") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(16.dp))
        Line(loadingText, style = TextStyle(textAlign = TextAlign.Center))
    }
}