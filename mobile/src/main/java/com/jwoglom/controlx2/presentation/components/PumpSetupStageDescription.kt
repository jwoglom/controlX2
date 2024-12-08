package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import timber.log.Timber

const val TroubleshootingStepsThresholdSeconds = 15

@Composable
fun PumpSetupStageDescription(
    initialSetup: Boolean = false,
    pairingCodeStage: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpFinderPumps = ds.pumpFinderPumps.observeAsState()
    val setupDeviceName = ds.setupDeviceName.observeAsState()
    val setupDeviceModel = ds.setupDeviceModel.observeAsState()
    val pumpCriticalError = ds.pumpCriticalError.observeAsState()

    var pumpConnectionWaitingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect (intervalOf(1)) {
        if (setupStage.value == PumpSetupStage.PUMPX2_PUMP_CONNECTED) {
            if (pumpConnectionWaitingSeconds > 0) {
                Timber.d("PumpSetupStageProgress pumpConnectionWaitingSeconds=$pumpConnectionWaitingSeconds")
                pumpConnectionWaitingSeconds = 0
            }
        } else {
            pumpConnectionWaitingSeconds += 1
            if (pumpConnectionWaitingSeconds % 20 == 0) {
                Timber.d("PumpSetupStageProgress pumpConnectionWaitingSeconds=$pumpConnectionWaitingSeconds")
            }
        }
    }

    when (setupStage.value) {
        PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
            Line("Notification permissions weren't granted, which are needed to make remote boluses.")
        }
        PumpSetupStage.WAITING_PUMP_FINDER_INIT -> {
            if (Prefs(context).pumpFinderServiceEnabled()) {
                Line("Waiting for PumpFinder library initialization...")
            }
        }
        PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS, PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP -> {
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
        PumpSetupStage.PUMP_FINDER_SELECT_PUMP -> {
            Line("Select a pump to connect to:")
            Line("")
            pumpFinderPumps.value?.forEach {
                Button(
                    onClick = {
                        Prefs(context).setPumpFinderPumpMac(it.second)
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_CHOOSE_PAIRING_CODE_TYPE)
                        ds.setupDeviceName.value = it.first
                    }
                ) {
                    Text("${it.first} (MAC: ${it.second})")
                }
                Line("")
            }
            Line("")
        }
        PumpSetupStage.PUMP_FINDER_CHOOSE_PAIRING_CODE_TYPE -> {
            Line("Choose the correct pairing code type:")
            Line("")
            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.LONG_16CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.LONG_16CHAR
                    ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_ENTER_PAIRING_CODE)
                }
            ) {
                Text("LONG: 16 alphanumeric characters")
            }

            Line("")

            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                    ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_ENTER_PAIRING_CODE)
                }
            ) {
                Text("SHORT: 6 numbers")
            }
            Line("")
            Line("Open the pairing code generated under Bluetooth Settings > Pairing Code on your pump now.")
        }
        PumpSetupStage.PUMP_FINDER_ENTER_PAIRING_CODE, PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE, PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
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
                        if (initialSetup) {
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("\n\nTo resolve the issue, press the Retry button below and enter the correct pairing code.")
                            }
                        } else {
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("\n\nTo resolve the issue, you must re-pair the app in Settings > Reconfigure pump.")
                            }
                        }
                    })
                } else {
                    Line("Initial connection made to ${setupDeviceName.value}, attempting to pair...")
                }
            }
        }
        PumpSetupStage.WAITING_PUMPX2_INIT -> {
            if (Prefs(context).serviceEnabled()) {
                Line("Waiting for library initialization...")
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
        PumpSetupStage.PUMPX2_PUMP_CONNECTED -> {
            if (initialSetup) {
                Line("Connected to ${setupDeviceName.value}!", bold = true)
                Spacer(modifier = Modifier.height(16.dp))
                Line("Press 'Next' to continue.")
            }
        }
        else -> {}
    }

    if (setupStage.value != PumpSetupStage.PUMPX2_PUMP_CONNECTED) {
        if (pumpConnectionWaitingSeconds > TroubleshootingStepsThresholdSeconds) {
            Spacer(Modifier.height(16.dp))
            Line("Troubleshooting Steps:", bold = true)
            when (setupStage.value) {
                PumpSetupStage.WAITING_PUMPX2_INIT -> {
                    if (!Prefs(context).serviceEnabled()) {
                        Line("0. Enable the ControlX2 service (Settings > Enable ControlX2 service)")
                    }
                    Line("1. Toggle Bluetooth on and off.")
                    Line("2. Restart the ControlX2 app: open the app switcher and long-press on the app icon to open the App Info page, then click 'Force Stop' followed by 'Open'")
                    Line("3. Ensure the ControlX2 app has sufficient permissions: on the App Info page for ControlX2, ensure that Bluetooth/Connected Devices-related permissions have been granted")
                    Line("4. If everything still isn't working, hit 'Clear Data' on the App Info page which will reset the app's settings")
                }
                else -> {
                    Line("1. Toggle Bluetooth on and off.")
                    Line("2. If the t:connect Android application is open, force-stop it: long-press the app, select App Info, then 'Force Stop'")
                    Line("3. Open your pump and select:")
                    Line("Options > Device Settings > Bluetooth Settings", bold = true)
                    Line("Disable and then re-enable 'Mobile Connection'.")
                    Line("If 'Pair Device' is displayed, press it.")
                }
            }
        }
        if (pumpCriticalError.value != null) {
            Spacer(Modifier.height(16.dp))
            Line("Connection Error${pumpCriticalError.value?.second?.let { " ${shortTimeAgo(it)}" }}:", bold = true)
            Line("${pumpCriticalError.value?.first}")
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
    }
}