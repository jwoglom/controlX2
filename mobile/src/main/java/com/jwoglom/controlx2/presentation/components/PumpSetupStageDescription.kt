@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
package com.jwoglom.controlx2.presentation.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jwoglom.controlx2.pump.ErrorAction
import com.jwoglom.controlx2.pump.ErrorPresentation
import com.jwoglom.controlx2.pump.Severity
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import timber.log.Timber

const val TroubleshootingStepsThresholdSeconds = 15

@Composable
fun PumpSetupStageDescription(
    initialSetup: Boolean = false,
    pairingCodeStage: @Composable () -> Unit = {},
    sendMessage: ((String, ByteArray) -> Unit)? = null,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpFinderPumps = ds.pumpFinderPumps.observeAsState()
    val setupDeviceName = ds.setupDeviceName.observeAsState()
    val pumpReadyState = ds.pumpReadyState.observeAsState()
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
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("For t:slim X2: ")
                    }
                    append("Open your pump and select:")
                })
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
                Line("Enable the 'Mobile Connection' option and press 'Pair Device.' If already paired, press 'Unpair Device' first.")
                Spacer(Modifier.height(16.dp))
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("For Mobi: ")
                    }
                    append("Place the pump on the wireless charger.")
                })
                Line("Ensure the Mobi is turned on and charging.")
                Line("Take the Mobi off the charger and place it back on.")
                Line("Use a USB-A to USB-C cable.")
            } else {
                Line("Searching for pump...")
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("For t:slim X2: ")
                    }
                    append("If your pump isn't appearing, open it and select:")
                })
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
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
                        val nextStage = when (determinePumpModel(it.first)) {
                            KnownDeviceModel.MOBI -> PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD
                            else -> PumpSetupStage.PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE
                        }
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(nextStage)
                        ds.setupDeviceName.value = it.first
                    }
                ) {
                    Text("${it.first} (MAC: ${it.second})")
                }
                Line("")
            }
            Line("")
        }
        PumpSetupStage.PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE -> {
            Line("Choose the correct pairing code type:")
            Line("")
            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.LONG_16CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.LONG_16CHAR
                    ds.pumpSetupStage.value =
                        ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE)
                }
            ) {
                Text("LONG: 16 alphanumeric characters")
            }

            Line("")

            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                    ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE)
                }
            ) {
                Text("SHORT: 6 numbers")
            }

            Line("")
            when (ds.setupDeviceName.value?.let { determinePumpModel(it) }) {
                KnownDeviceModel.TSLIM_X2 -> {
                    Line(buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("For t:slim X2: ")
                        }
                        append("Open the pairing code generated under Bluetooth Settings > Pairing Code on your pump now.")
                    })
                }
                else -> {}
            }
        }
        PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD -> {
            Line(buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("For Mobi: ")
                }
                append("Place your Mobi on the charging pad.")
            })
            Line("Ensure it is turned on and charging.")

            LaunchedEffect(pumpReadyState.value) {
                when {
                    pumpReadyState.value?.shouldPickUpAndTap() == true -> {
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP)
                    }
                    pumpReadyState.value?.shouldEnterPinCode() == true -> {
                        Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                        ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE)
                    }
                    else -> {}
                }
            }
        }
        PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP -> {
            Line(buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("For Mobi: ")
                }
                append("Your pump was detected.")
            })
            Line("Pick up the pump, wait a second, then double-tap the T button.")

            LaunchedEffect(pumpReadyState.value) {
                when {
                    pumpReadyState.value?.shouldPlaceOnChargingPad() == true -> {
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD)
                    }
                    pumpReadyState.value?.shouldEnterPinCode() == true -> {
                        Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                        ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                        ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE)
                    }
                    else -> {}
                }
            }
        }
        PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE,
        PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
        PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE,
        PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
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
            Line("Connecting to ${setupDeviceName.value} (${setupDeviceModel.value})")
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
        val errorState = pumpCriticalError.value
        // Decide visibility per tier. TRANSIENT errors stay hidden until threshold + time gate.
        val shouldShowError = errorState != null && when (errorState.presentation.severity) {
            Severity.TRANSIENT -> errorState.occurrences >= errorState.presentation.occurrenceThreshold &&
                java.time.Duration.between(errorState.firstSeenAt, java.time.Instant.now()).toMillis() >= errorState.presentation.timeThresholdMs
            Severity.ACTIONABLE, Severity.FATAL -> errorState.occurrences >= errorState.presentation.occurrenceThreshold
        }

        if (pumpConnectionWaitingSeconds > TroubleshootingStepsThresholdSeconds &&
            // Suppress the generic troubleshooting list when we already have a tiered card up
            // for an ACTIONABLE/FATAL error — it has its own targeted remediation.
            !(shouldShowError && errorState!!.presentation.severity != Severity.TRANSIENT)) {
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
                }
            }
        }
        if (shouldShowError) {
            // shouldShowError implies errorState != null (see definition above).
            Spacer(Modifier.height(16.dp))
            TieredCriticalErrorCard(
                state = errorState!!,
                pumpModel = setupDeviceName.value?.let { determinePumpModel(it) },
                sendMessage = sendMessage,
            )
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
    }
}

private fun colorForSeverity(severity: Severity): Pair<Color, Color> = when (severity) {
    // (background, text) — chosen to be readable against both light and dark themes.
    Severity.TRANSIENT -> Color(0xFFEEEEEE) to Color(0xFF424242)
    Severity.ACTIONABLE -> Color(0xFFFFF3CD) to Color(0xFF8B6E0F)
    Severity.FATAL -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TieredCriticalErrorCard(
    state: com.jwoglom.controlx2.presentation.PumpCriticalErrorState,
    pumpModel: KnownDeviceModel?,
    sendMessage: ((String, ByteArray) -> Unit)?,
) {
    val context = LocalContext.current
    val (bg, fg) = colorForSeverity(state.presentation.severity)
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.presentation.headline,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
        // Body, with per-model branching for PAIRING_CANNOT_BEGIN — Mobi and t:slim X2
        // have different physical pairing flows.
        if (state.presentation.name == "PAIRING_CANNOT_BEGIN") {
            Text(text = state.presentation.body, color = fg)
            Spacer(Modifier.height(4.dp))
            when (pumpModel) {
                KnownDeviceModel.MOBI -> {
                    Text("• Place the Mobi on the wireless charging pad.", color = fg)
                    Text("• Make sure it is turned on and charging.", color = fg)
                    Text("• Pick it up, wait a moment, then double-tap the T button when prompted.", color = fg)
                }
                else -> {
                    // Default to t:slim X2 instructions when model is unknown.
                    Text("1. On your pump, open Options → Device Settings → Bluetooth Settings.", color = fg)
                    Text("2. Tap 'Pair Device' and confirm OK to display a pairing code.", color = fg)
                    Text("3. Then tap Retry below.", color = fg)
                }
            }
        } else {
            Text(text = state.presentation.body, color = fg)
        }

        // Only render an elapsed-time line when the last sighting is meaningfully old.
        // Anything fresher than ~60s reads as noise next to a live error card.
        val elapsedMs = java.time.Duration.between(state.lastSeenAt, java.time.Instant.now()).toMillis()
        if (elapsedMs >= 60_000) {
            Text(
                text = "Last seen ${shortTimeAgo(state.lastSeenAt)}",
                color = fg,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.occurrences > 1) {
            Text(
                text = "Occurrences: ${state.occurrences}",
                color = fg,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.presentation.actions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Primary = first action (filled Button). Rest are TextButtons.
            // FlowRow lets buttons wrap onto a new row whole rather than splitting a single
            // label like "Enable connecti / on / sharing".
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.presentation.actions.forEachIndexed { index, action ->
                    if (index == 0) {
                        Button(onClick = { runErrorAction(context, action, state, sendMessage) }) {
                            Text(actionLabel(action))
                        }
                    } else {
                        TextButton(onClick = { runErrorAction(context, action, state, sendMessage) }) {
                            Text(actionLabel(action))
                        }
                    }
                }
            }
        }

        // FATAL tier: "Show details" is a small affordance, not an action-row competitor.
        // Sits below a thin divider so the primary action row is always visible first.
        if (state.presentation.severity == Severity.FATAL) {
            Spacer(Modifier.height(4.dp))
            Divider(color = fg.copy(alpha = 0.2f))
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                Text(
                    text = if (detailsExpanded) "Hide details" else "Show details",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (detailsExpanded) {
                Text("name: ${state.presentation.name}", color = fg, style = MaterialTheme.typography.bodySmall)
                if (state.presentation.rawMessage.isNotBlank())
                    Text("message: ${state.presentation.rawMessage}", color = fg, style = MaterialTheme.typography.bodySmall)
                if (state.presentation.extra.isNotBlank())
                    Text("extra: ${state.presentation.extra}", color = fg, style = MaterialTheme.typography.bodySmall)
                state.presentation.errorCodeId?.let {
                    Text("errorCodeId: $it", color = fg, style = MaterialTheme.typography.bodySmall)
                }
                state.presentation.requestCodeId?.let {
                    Text("requestCodeId: $it", color = fg, style = MaterialTheme.typography.bodySmall)
                }
                state.presentation.initiatingMessageClass?.let {
                    Text("initiatingMessage: $it", color = fg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun actionLabel(action: ErrorAction): String = when (action) {
    is ErrorAction.Retry -> "Retry"
    is ErrorAction.RepairPump -> "Re-pair pump"
    is ErrorAction.OpenBluetoothSettings -> "Open Bluetooth settings"
    is ErrorAction.OpenTconnectAppInfo -> "Open Tandem app info"
    is ErrorAction.EnableConnectionSharing -> "Enable connection sharing"
    is ErrorAction.CopyDiagnostics -> "Copy diagnostics"
}

private fun runErrorAction(
    context: Context,
    action: ErrorAction,
    state: com.jwoglom.controlx2.presentation.PumpCriticalErrorState,
    sendMessage: ((String, ByteArray) -> Unit)?,
) {
    when (action) {
        is ErrorAction.Retry -> {
            // Triggers CommService to drop and reconnect the pump session.
            sendMessage?.invoke(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                ?: Toast.makeText(context, "Retry unavailable here", Toast.LENGTH_SHORT).show()
        }
        is ErrorAction.RepairPump -> {
            // Mirror the "Reconfigure pump" flow from Settings.kt:408-416.
            try {
                Prefs(context).setPumpSetupComplete(false)
                Prefs(context).setPumpFinderPumpMac("")
                Prefs(context).setPumpFinderPairingCodeType("")
                Prefs(context).setPumpFinderServiceEnabled(true)
                Prefs(context).setCurrentPumpSid(-1)
                PumpState.resetState(context)
                sendMessage?.invoke(MessagePaths.TO_SERVER_APP_RELOAD, "".toByteArray())
                Toast.makeText(context, "Resetting pump pairing…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Re-pair action failed")
            }
        }
        is ErrorAction.OpenBluetoothSettings -> {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Bluetooth settings unavailable", Toast.LENGTH_SHORT).show()
            }
        }
        is ErrorAction.OpenTconnectAppInfo -> {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:com.tandemdiabetes.tconnect"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Tandem app not installed", Toast.LENGTH_SHORT).show()
            }
        }
        is ErrorAction.EnableConnectionSharing -> {
            // The pref toggles the pumpx2 sharing mode; CommService reads it on next connect.
            try {
                Prefs(context).setConnectionSharingEnabled(true)
                Toast.makeText(context, "Connection sharing enabled. Reconnecting…", Toast.LENGTH_SHORT).show()
                sendMessage?.invoke(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
            } catch (e: Exception) {
                Timber.e(e, "Failed to enable connection sharing")
            }
        }
        is ErrorAction.CopyDiagnostics -> {
            val text = buildString {
                appendLine("name: ${state.presentation.name}")
                appendLine("severity: ${state.presentation.severity}")
                appendLine("rawMessage: ${state.presentation.rawMessage}")
                if (state.presentation.extra.isNotBlank()) appendLine("extra: ${state.presentation.extra}")
                state.presentation.errorCodeId?.let { appendLine("errorCodeId: $it") }
                state.presentation.requestCodeId?.let { appendLine("requestCodeId: $it") }
                state.presentation.initiatingMessageClass?.let { appendLine("initiatingMessage: $it") }
                appendLine("occurrences: ${state.occurrences}")
                appendLine("firstSeenAt: ${state.firstSeenAt}")
                appendLine("lastSeenAt: ${state.lastSeenAt}")
            }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ControlX2 diagnostics", text))
            Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        }
    }
}
