package com.jwoglom.controlx2.presentation.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.compose.runtime.LaunchedEffect
import com.jwoglom.controlx2.shared.FeatureFlag
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.util.StatePrefs
import com.jwoglom.controlx2.util.rescueResetThisDevice
import com.jwoglom.controlx2.util.switchDeviceRole

@Composable
fun RoleSelectionScreen(
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val ffEnabled = remember { FeatureFlag.enabled(context, FeatureFlag.BTHostSwitch) }
    if (!ffEnabled) {
        // Defense in depth: the SettingsHub chip that navigates here is itself
        // gated, so this branch is only hit if the route is reached some other
        // way (e.g. saved nav state after the flag was toggled off).
        LaunchedEffect(Unit) { onCancel() }
        return
    }
    val currentRole = remember { StatePrefs(context).deviceRole() }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRescueDialog by remember { mutableStateOf(false) }

    val roleLabel = when (currentRole) {
        DeviceRole.PUMP_HOST -> "Watch (pump-host)"
        DeviceRole.CLIENT -> "Phone (pump-host)"
    }
    val newRole = when (currentRole) {
        DeviceRole.PUMP_HOST -> DeviceRole.CLIENT
        DeviceRole.CLIENT -> DeviceRole.PUMP_HOST
    }
    val newRoleLabel = when (newRole) {
        DeviceRole.PUMP_HOST -> "Watch (pump-host)"
        DeviceRole.CLIENT -> "Phone (pump-host)"
    }

    val walkthrough = when (newRole) {
        DeviceRole.PUMP_HOST ->
            "The watch restarts as pump-host and the phone flips to client. Place the Mobi on the charging pad to re-pair."
        DeviceRole.CLIENT ->
            "The watch restarts as client and the phone takes over as pump-host. Place the Mobi on the charging pad to re-pair."
    }

    if (showRescueDialog) {
        Alert(
            title = {
                Text(
                    text = "Reset & start over?",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground,
                )
            },
            negativeButton = {
                Button(
                    onClick = { showRescueDialog = false },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Cancel")
                }
            },
            positiveButton = {
                Button(
                    onClick = {
                        showRescueDialog = false
                        val activity = context as? Activity
                        if (activity != null) {
                            rescueResetThisDevice(activity)
                        }
                    },
                    colors = ButtonDefaults.primaryButtonColors(),
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Confirm")
                }
            },
            icon = {
                Image(
                    Icons.Filled.Devices,
                    "Reset",
                    Modifier.size(24.dp),
                )
            },
        ) {
            Text(
                text = "Forces this watch to pump-host and asks the phone to flip to client. Place the Mobi on the charging pad to re-pair.",
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
            )
        }
    } else if (showConfirmDialog) {
        Alert(
            title = {
                Text(
                    text = "Switch to $newRoleLabel?",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground,
                )
            },
            negativeButton = {
                Button(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Cancel")
                }
            },
            positiveButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        val activity = context as? Activity
                        if (activity != null) {
                            switchDeviceRole(activity, newRole)
                        }
                    },
                    colors = ButtonDefaults.primaryButtonColors(),
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Confirm")
                }
            },
            icon = {
                Image(
                    Icons.Filled.Devices,
                    "Device role",
                    Modifier.size(24.dp),
                )
            },
        ) {
            Text(
                text = walkthrough,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Pump-host device",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
            Text(
                text = "Currently: $roleLabel",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            Chip(
                onClick = { showConfirmDialog = true },
                label = { Text("Switch to $newRoleLabel", fontSize = 12.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Chip(
                onClick = { showRescueDialog = true },
                label = { Text("Reset & start over", fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Chip(
                onClick = onCancel,
                label = { Text("Cancel", fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
