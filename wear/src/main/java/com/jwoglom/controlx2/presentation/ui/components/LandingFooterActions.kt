package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text

/**
 * Landing footer. Collapsed in Phase 5d from three individual action chips
 * (force-reload, open-phone, role-selection) to a single Settings entry point
 * so `SettingsHubScreen` can own the full list — keeping the Landing footer
 * uncluttered as 5d/5e add more settings.
 */
@Composable
fun LandingFooterActions(
    onSettings: () -> Unit,
) {
    Chip(
        onClick = onSettings,
        icon = {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        label = { Text("Settings", fontSize = 13.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 25.dp),
    )
}

@Preview
@Composable
private fun LandingFooterActionsPreview() {
    LandingFooterActions(onSettings = {})
}
