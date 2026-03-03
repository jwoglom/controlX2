package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import com.google.accompanist.flowlayout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh

@Composable
fun LandingFooterActions(onForceReload: () -> Unit, onOpenPhone: () -> Unit) {
    FlowRow(modifier = Modifier.padding(top = 25.dp)) {
        Chip(
            onClick = onForceReload,
            label = { Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Force reload app") },
        )
        Spacer(Modifier.width(16.dp))
        Chip(
            onClick = onOpenPhone,
            label = { Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = "Open on phone") },
        )
    }
}

@Preview
@Composable
private fun LandingFooterActionsPreview() {
    LandingFooterActions(onForceReload = {}, onOpenPhone = {})
}
