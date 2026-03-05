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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel

@Composable
fun CartridgeActionsMenuScreen(
    innerPadding: PaddingValues,
    deviceName: String,
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
            ListItem(
                headlineContent = { Text("Change Cartridge") },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onChangeCartridge),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Fill Tubing") },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onFillTubing),
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Fill Cannula") },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onFillCannula),
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

@Preview(showBackground = true)
@Composable
private fun CartridgeActionsMenuScreenPreview() {
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
