package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel

@Composable
fun CartridgeActionsMenuScreen(
    innerPadding: PaddingValues,
    deviceName: String,
    changeCartridgeEnabled: Boolean = true,
    changeCartridgeDisabledReason: String? = null,
    fillTubingEnabled: Boolean = true,
    fillTubingDisabledReason: String? = null,
    fillCannulaEnabled: Boolean = true,
    fillCannulaDisabledReason: String? = null,
    onChangeCartridge: () -> Unit,
    onFillTubing: () -> Unit,
    onFillCannula: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            HeaderLine("Cartridge Actions")
            Divider()

            val model = determinePumpModel(deviceName)
            if (model == KnownDeviceModel.TSLIM_X2) {
                Line("Insulin control is not supported on this device model (${model}).")
                Line("")
            }
        }

        item {
            MenuActionItem(
                title = "Change Cartridge",
                enabled = changeCartridgeEnabled,
                disabledReason = changeCartridgeDisabledReason,
                onClick = onChangeCartridge,
            )
        }

        item {
            MenuActionItem(
                title = "Fill Tubing",
                enabled = fillTubingEnabled,
                disabledReason = fillTubingDisabledReason,
                onClick = onFillTubing,
            )
        }

        item {
            MenuActionItem(
                title = "Fill Cannula",
                enabled = fillCannulaEnabled,
                disabledReason = fillCannulaDisabledReason,
                onClick = onFillCannula,
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Back") },
                leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}

@Composable
private fun MenuActionItem(
    title: String,
    enabled: Boolean,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    ListItem(
        headlineContent = { Text(title, color = textColor) },
        supportingContent = {
            if (!enabled && !disabledReason.isNullOrBlank()) {
                Text(disabledReason, color = textColor)
            }
        },
        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null, tint = textColor) },
        modifier = Modifier
            .alpha(alpha)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
fun CartridgeActionsMenuScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            CartridgeActionsMenuScreen(
                innerPadding = PaddingValues(),
                deviceName = "Mobi",
                onChangeCartridge = {},
                onFillTubing = {},
                onFillCannula = {},
                onBack = {},
            )
        }
    }
}
