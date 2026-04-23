package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.MainActivity

/**
 * Shown while [WearPumpCommService] is establishing the initial pump connection
 * in pump-host mode. Replaces the former `IndeterminateProgressIndicator`
 * placeholder.
 *
 * Provides a Stop button that delegates to [MainActivity.stopPumpService],
 * which mirrors mobile's disable-service path (set pref false, send
 * TO_SERVER_FORCE_RELOAD). After the service exits the re-enable affordance
 * is provided by [com.jwoglom.controlx2.presentation.components.WearServiceDisabledMessage].
 */
@Composable
fun ConnectingToPumpScreen() {
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
        CircularProgressIndicator()
        Text(
            text = "Connecting to pump",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        val name = deviceName.value
        if (!name.isNullOrBlank()) {
            Text(
                text = name,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Chip(
            onClick = { (context as? MainActivity)?.stopPumpService() },
            label = { Text("Stop", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
