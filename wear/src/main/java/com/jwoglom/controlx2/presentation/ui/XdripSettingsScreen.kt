package com.jwoglom.controlx2.presentation.ui

import android.widget.Toast
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
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.MainActivity
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.sync.xdrip.XdripPayloadGroup
import com.jwoglom.controlx2.sync.xdrip.XdripSyncConfig

/**
 * Pump-host watch settings for xDrip+ broadcasts. Reads/writes the `"WearX2"`
 * SharedPreferences file via [XdripSyncConfig.load] / [save] — same file
 * [com.jwoglom.controlx2.sync.xdrip.XdripMessageDispatcher] reads on construction.
 *
 * When the config change requires the service to restart (see
 * [XdripSyncConfig.requiresReloadComparedTo], true for the enable toggle),
 * the mobile three-step reload sequence is dispatched via
 * [MainActivity.reEnablePumpService] so the running service picks up the change.
 */
@Composable
fun XdripSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { WearPrefs(context).prefs() }

    var config by remember { mutableStateOf(XdripSyncConfig.load(prefs)) }

    fun saveConfig(newConfig: XdripSyncConfig, toastText: String? = null) {
        val oldConfig = config
        config = newConfig
        XdripSyncConfig.save(prefs, newConfig)
        if (toastText != null) {
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
        if (newConfig.requiresReloadComparedTo(oldConfig)) {
            (context as? MainActivity)?.forceReloadService()
        }
    }

    fun togglePayload(group: XdripPayloadGroup) {
        saveConfig(config.withPayloadEnabled(group, !config.isPayloadEnabled(group)))
    }

    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        state = state,
        autoCentering = AutoCenteringParams(),
    ) {
        item {
            Chip(
                onClick = {
                    val newConfig = config.copy(enabled = !config.enabled)
                    saveConfig(
                        newConfig,
                        toastText = if (newConfig.enabled) "xDrip enabled" else "xDrip disabled",
                    )
                },
                label = { Text(if (config.enabled) "Enabled" else "Disabled", fontSize = 13.sp) },
                secondaryLabel = { Text("Tap to toggle", fontSize = 10.sp) },
                colors = if (config.enabled) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PayloadToggleChip(
                label = "Send SGV",
                enabled = config.sendCgmSgv,
                onClick = { togglePayload(XdripPayloadGroup.CGM) },
            )
        }
        item {
            PayloadToggleChip(
                label = "Device status",
                enabled = config.sendPumpDeviceStatus,
                onClick = { togglePayload(XdripPayloadGroup.PUMP_DEVICE_STATUS) },
            )
        }
        item {
            PayloadToggleChip(
                label = "Treatments",
                enabled = config.sendTreatments,
                onClick = { togglePayload(XdripPayloadGroup.TREATMENTS) },
            )
        }
        item {
            PayloadToggleChip(
                label = "Status line",
                enabled = config.sendStatusLine,
                onClick = { togglePayload(XdripPayloadGroup.STATUS_LINE) },
            )
        }
    }
}

@Composable
private fun PayloadToggleChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        secondaryLabel = { Text(if (enabled) "On" else "Off", fontSize = 10.sp) },
        colors = if (enabled) ChipDefaults.primaryChipColors()
            else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
