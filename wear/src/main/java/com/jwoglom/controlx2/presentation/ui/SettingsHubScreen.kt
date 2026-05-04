package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.shared.FeatureFlag
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.util.StatePrefs

/**
 * Watch settings hub. Single entry point for all watch-local configuration so
 * the `LandingScreen` footer can collapse to one chip.
 *
 * Entries gated by [DeviceRole] — Nightscout and xDrip settings are pump-host
 * only; in CLIENT mode the phone owns those and showing them on the watch
 * would be misleading.
 */
@Composable
fun SettingsHubScreen(
    navController: NavHostController,
    sendPhoneCommand: (String) -> Unit,
    sendPhoneOpenActivity: () -> Unit,
) {
    val context = LocalContext.current
    val role = remember { StatePrefs(context).deviceRole() }
    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        state = state,
        autoCentering = AutoCenteringParams(),
    ) {
        item {
            var ffEnabled by remember { mutableStateOf(FeatureFlag.enabled(context,
                FeatureFlag.BTHostSwitch
            ))}

            if (ffEnabled) {
                Chip(
                    onClick = { navController.navigate(Screen.RoleSelection.route) },
                    label = { Text("Role", fontSize = 13.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Pump-command entries below work in both DeviceRole values. The
        // to-pump message is routed by HybridMessageBus either to the local
        // BT link (PUMP_HOST) or forwarded to the phone (CLIENT), so no role
        // gate is needed.
        item {
            Chip(
                onClick = { navController.navigate(Screen.TempBasalSet.route) },
                label = { Text("Temp basal", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.ProfileSwitch.route) },
                label = { Text("Active profile", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.SleepModeSet.route) },
                label = { Text("Sleep mode", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.ExerciseModeSet.route) },
                label = { Text("Exercise mode", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.Notifications.route) },
                label = { Text("Pump alerts", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.CGMTransmitter.route) },
                label = { Text("CGM sensor", fontSize = 13.sp) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (role == DeviceRole.PUMP_HOST) {
            // Basal is a pump-data surface, not a setting — reached by tapping
            // the basal row on Landing. Pump history + CGM chart live here
            // because they're diagnostic surfaces backed by the watch-local
            // HistoryLogRepo (PUMP_HOST-only data).
            item {
                Chip(
                    onClick = { navController.navigate(Screen.CgmChart.route) },
                    label = { Text("CGM chart", fontSize = 13.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(Screen.HistoryLog.route) },
                    label = { Text("Pump history", fontSize = 13.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(Screen.NightscoutSettings.route) },
                    label = { Text("Nightscout", fontSize = 13.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(Screen.XdripSettings.route) },
                    label = { Text("xDrip+", fontSize = 13.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Chip(
                onClick = { sendPhoneCommand("force-reload") },
                label = { Text("Force reload", fontSize = 13.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = sendPhoneOpenActivity,
                label = { Text("Open on phone", fontSize = 13.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { navController.navigate(Screen.FeatureFlags.route) },
                label = { Text("Feature flags", fontSize = 13.sp) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
