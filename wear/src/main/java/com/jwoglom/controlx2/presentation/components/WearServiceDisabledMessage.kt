package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.MainActivity
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.util.StatePrefs

/**
 * Watch port of [com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage]
 * from `:mobile`. Shown on pump-host screens when [WearPrefs.serviceEnabled] is
 * `false` so the user can re-enable the service without opening settings.
 *
 * Tap delegates to [MainActivity.reEnablePumpService], which runs the same
 * three-step sequence mobile uses (toggle pref + TO_SERVER_FORCE_RELOAD +
 * TO_SERVER_APP_RELOAD).
 *
 * Renders nothing when the device role is not [DeviceRole.PUMP_HOST] — the
 * phone handles this case on its own landing screen.
 */
@Composable
fun WearServiceDisabledMessage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val role = remember { StatePrefs(context).deviceRole() }

    if (role != DeviceRole.PUMP_HOST) {
        return
    }

    var enabled by remember { mutableStateOf(WearPrefs(context).serviceEnabled()) }
    val pumpConnected = ds.pumpConnected.observeAsState()

    LaunchedEffect(pumpConnected.value) {
        enabled = WearPrefs(context).serviceEnabled()
    }

    if (!enabled) {
        Chip(
            onClick = {
                (context as? MainActivity)?.reEnablePumpService()
            },
            label = { Text("Service disabled", fontSize = 12.sp) },
            secondaryLabel = { Text("Tap to re-enable", fontSize = 10.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
