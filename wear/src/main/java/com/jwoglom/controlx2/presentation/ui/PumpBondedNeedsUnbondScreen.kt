package com.jwoglom.controlx2.presentation.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore

/**
 * Shown when pumpcomm emits `FROM_PUMP_PUMP_BONDED_NEEDS_MANUAL_UNBOND`. The
 * pump is still bonded in Android's Bluetooth stack (likely paired to another
 * device first) and the auto-unbond attempt failed. The user must open system
 * Bluetooth settings and unpair the pump manually before retrying.
 */
@Composable
fun PumpBondedNeedsUnbondScreen() {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = "Bluetooth",
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Unpair pump first",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
        val name = deviceName.value
        Text(
            text = if (!name.isNullOrBlank()) {
                "$name is bonded to another device. Open Bluetooth settings, forget it, then retry."
            } else {
                "The pump is bonded to another device. Open Bluetooth settings, forget it, then retry."
            },
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        Chip(
            onClick = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                } catch (_: Exception) {
                    // On some Wear devices the intent may not resolve; the user
                    // can still unpair manually from the system Settings app.
                }
            },
            label = { Text("Open BT settings", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
