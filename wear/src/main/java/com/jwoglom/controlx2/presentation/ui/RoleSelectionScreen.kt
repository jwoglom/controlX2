package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Watch-side screen for selecting the device role:
 * - PUMP_HOST: Watch connects directly to the pump via Bluetooth
 * - CLIENT: Watch receives pump data from the phone (default)
 */
@Composable
fun RoleSelectionScreen(
    onSelectPumpHost: () -> Unit,
    onSelectClient: () -> Unit,
) {
    androidx.wear.compose.material.ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                text = "Device Role",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        item {
            Text(
                text = "Choose how this watch connects to the pump.",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
        item {
            Chip(
                onClick = onSelectPumpHost,
                label = { Text("Watch as Pump Host") },
                secondaryLabel = { Text("Direct Bluetooth to pump") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onSelectClient,
                label = { Text("Watch as Client") },
                secondaryLabel = { Text("Receive data from phone") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
