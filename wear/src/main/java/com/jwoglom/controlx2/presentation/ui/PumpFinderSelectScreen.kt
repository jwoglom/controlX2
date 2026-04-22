package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.presentation.navigation.PumpSetupStage
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType

/**
 * List of pumps discovered by PumpFinder. Tapping a pump persists the selected
 * MAC + pairing code type, advances [PumpSetupStage] to WAITING_PUMP_FINDER_CLEANUP,
 * and navigates to the pairing-code entry screen.
 */
@Composable
fun PumpFinderSelectScreen(
    navController: NavHostController,
    sendPhoneCommand: (String) -> Unit,
) {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val pumps = dataStore.pumpFinderPumps.observeAsState()
    val listState = rememberScalingLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Select pump",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )

        val discovered = pumps.value.orEmpty()
        if (discovered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Scanning for pumps...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Chip(
                        onClick = {
                            // sendPhoneCommand prepends PREFIX_TO_SERVER; suffix only
                            sendPhoneCommand("restart-pump-finder")
                        },
                        label = { Text("Restart scan", fontSize = 12.sp) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(discovered) { (name, mac) ->
                    Chip(
                        onClick = {
                            val prefs = WearPrefs(context)
                            prefs.setPumpFinderPumpMac(mac)
                            prefs.setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                            dataStore.pumpSetupStage.value = PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP
                            dataStore.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                            navController.navigate(Screen.PairingCodeEntry.route)
                        },
                        label = { Text(name, fontSize = 12.sp) },
                        secondaryLabel = { Text(mac, fontSize = 10.sp) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
