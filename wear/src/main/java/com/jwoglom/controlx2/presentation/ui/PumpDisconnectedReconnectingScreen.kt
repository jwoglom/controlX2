package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
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
import com.jwoglom.controlx2.MainActivity
import com.jwoglom.controlx2.presentation.components.LastConnectionText

/**
 * Shown when the pump-host watch has lost its BT connection and the service is
 * auto-reconnecting. Replaces the former `IndeterminateProgressIndicator`
 * placeholder.
 *
 * Layout:
 *  - Warning icon + "Disconnected, reconnecting…" header.
 *  - [LastConnectionText] for "Last seen N ago".
 *  - Stop button that disables the service via
 *    [MainActivity.stopPumpService]. After Stop, `triggerAppReload` restarts
 *    the app to `LandingScreen`, where
 *    [com.jwoglom.controlx2.presentation.components.WearServiceDisabledMessage]
 *    provides the re-enable affordance — no duplicate in-screen banner here.
 */
@Composable
fun PumpDisconnectedReconnectingScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Disconnected",
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Disconnected, reconnecting…",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
        LastConnectionText(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        Chip(
            onClick = { (context as? MainActivity)?.stopPumpService() },
            label = { Text("Stop", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
