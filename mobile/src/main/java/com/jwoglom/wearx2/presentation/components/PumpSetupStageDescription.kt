package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.screens.PumpSetupStage
import com.jwoglom.wearx2.shared.presentation.intervalOf
import timber.log.Timber

const val TroubleshootingStepsThresholdSeconds = 5

@Composable
fun PumpSetupStageDescription(
    initialSetup: Boolean = false,
    pairingCodeStage: @Composable () -> Unit = {},
) {
    val ds = LocalDataStore.current
    val setupStage = ds.pumpSetupStage.observeAsState()
    val setupDeviceName = ds.setupDeviceName.observeAsState()
    val setupDeviceModel = ds.setupDeviceModel.observeAsState()

    var secondsWaitingToConnect by remember { mutableStateOf(0) }

    LaunchedEffect (intervalOf(1)) {
        if (setupStage.value == PumpSetupStage.PUMPX2_PUMP_CONNECTED) {
            if (secondsWaitingToConnect > 0) {
                Timber.d("PumpSetupStageProgress secondsWaitingToConnect=$secondsWaitingToConnect")
                secondsWaitingToConnect = 0
            }
        } else {
            secondsWaitingToConnect += 1
            Timber.d("PumpSetupStageProgress secondsWaitingToConnect=$secondsWaitingToConnect")
        }
    }

    when (setupStage.value) {
        PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
            Line("Notification permissions weren't granted, which are needed to make remote boluses.")
        }
        PumpSetupStage.WAITING_PUMPX2_INIT -> {
            Line("Waiting for library initialization...")
        }
        PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP -> {
            if (initialSetup) {
                Line("Open your pump and select:")
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
                Spacer(Modifier.height(16.dp))
                Line("Enable the 'Mobile Connection' option and press 'Pair Device.' If already paired, press 'Unpair Device' first.")
            } else {
                Line("Searching for pump...")
                Line("If your pump isn't appearing, open it and select:")
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
                Spacer(Modifier.height(16.dp))
                Line("Ensure the 'Mobile Connection' option is enabled.")
            }
        }
        PumpSetupStage.PUMPX2_PUMP_DISCONNECTED -> {
            Line("Disconnected from '${setupDeviceName.value}', reconnecting...")
        }
        PumpSetupStage.PUMPX2_PUMP_DISCOVERED -> {
            Line("Connecting to ${setupDeviceName.value}")
        }
        PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA -> {
            Line("Connecting to ${setupDeviceName.value} (t:slim ${setupDeviceModel.value})")
        }
        PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION -> {
            Line("Initial connection made to ${setupDeviceName.value}")
        }
        PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE, PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
           if (initialSetup) {
               pairingCodeStage()
           } else {
               if (setupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE) {
                   Line(buildAnnotatedString {
                       withStyle(
                           style = SpanStyle(
                               color = Color.Red,
                               fontWeight = FontWeight.Bold
                           )
                       ) {
                           append("The pairing code was invalid. ")
                       }
                       append("The code was either entered incorrectly or timed out. Make sure the 'Pair Device' dialog is open on your pump.")
                   })
               } else {
                   Line("Initial connection made to ${setupDeviceName.value}, attempting to pair...")
               }
           }
        }
        PumpSetupStage.PUMPX2_WAITING_TO_PAIR -> {
            Line("Pairing with ${setupDeviceName.value}...")
        }
        PumpSetupStage.PUMPX2_PUMP_CONNECTED -> {
            if (initialSetup) {
                Line("Connected to ${setupDeviceName.value}!", bold = true)
                Spacer(modifier = Modifier.height(16.dp))
                Line("Press 'Next' to continue.")
            }
        }
        else -> {}
    }

    if (!initialSetup && setupStage.value != PumpSetupStage.PUMPX2_PUMP_CONNECTED) {
        if (secondsWaitingToConnect > TroubleshootingStepsThresholdSeconds) {
            Spacer(Modifier.height(16.dp))
            Line("Troubleshooting Steps:", bold = true)
            Line("1. Toggle Bluetooth on and off.")
            Line("2. If the t:connect Android application is open, force-stop it: long-press the app, select App Info, then 'Force Stop'")
            Line("3. Open your pump and select:")
            Line("Options > Device Settings > Bluetooth Settings", bold = true)
            Line("Disable and then re-enable 'Mobile Connection'")
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
    }
}