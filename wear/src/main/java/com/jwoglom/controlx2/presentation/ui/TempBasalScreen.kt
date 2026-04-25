package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.DecimalNumberPicker
import com.jwoglom.controlx2.presentation.components.SingleNumberPicker
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBasalStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Watch temp basal set/cancel. Works in both `DeviceRole` values — `to-pump`
 * messages route through `HybridMessageBus` to either the local BT link
 * (PUMP_HOST) or the phone (CLIENT), so one code path covers both modes.
 * Path reference written without the slash-star suffix on purpose; Kotlin
 * treats it as a nested comment opener inside a KDoc block (same trap that
 * bit WearHybridMessageBus.kt in commit 7db3dc9 and ProfileSwitchScreen.kt
 * in a9df6ac / 61a0262).
 *
 * Flow:
 *  - On entry, fires `TempRateRequest()` + `CurrentBasalStatusRequest()` with
 *    BUST_CACHE so the active/inactive decision + the U/hr→% derivation use
 *    fresh state instead of cached values.
 *  - If active, offers a single "Cancel temp basal" confirmation.
 *  - If inactive, asks for entry mode first (Percent or U/hr), then runs
 *    the value picker for that mode, then total minutes, then the final
 *    confirm dialog. On confirm: dispatch `SetTempRateRequest(minutes,
 *    derivedPercent)` + 5× 1s poll of `TempRateRequest()` to catch the
 *    post-set state change.
 *
 * U/hr mode mirrors mobile `TempRateWindow.kt:190-265`: reads the current
 * profile's basal rate out of `dataStore.basalRate` (String like "1.250u",
 * populated from `CurrentBasalStatusResponse`), converts the entered U/hr
 * to a percent via `((rawUnits / currentBasalRate) * 100).roundToInt()`,
 * and validates that the derived percent lands in 0–250%. If the basal
 * rate isn't known yet (no `CurrentBasalStatusResponse` in DataStore) the
 * U/hr chip on `PickMode` is disabled with a hint, and the user can still
 * take the Percent path.
 */
@Composable
fun TempBasalScreen(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    onDone: () -> Unit,
) {
    val ds = LocalDataStore.current
    val tempRateActive by ds.tempRateActive.observeAsState()
    val tempRateDetails by ds.tempRateDetails.observeAsState()
    val basalRateText by ds.basalRate.observeAsState()
    val currentBasalRate: Double? = parseBasalRateString(basalRateText)

    // Refresh both temp-rate state and the current basal rate on entry so a
    // stale cache doesn't put us on the wrong branch (active vs inactive)
    // and so the U/hr→% derivation uses the live profile rate.
    LaunchedEffect(Unit) {
        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(TempRateRequest(), CurrentBasalStatusRequest()),
        )
    }

    val pollScope = rememberCoroutineScope()
    fun pollTempRate() {
        pollScope.launch {
            repeat(5) {
                delay(1000)
                sendPumpCommands(SendType.BUST_CACHE, listOf(TempRateRequest()))
            }
        }
    }

    var step by remember { mutableStateOf(TempBasalStep.Loading) }
    var mode by remember { mutableStateOf(TempBasalMode.PERCENT) }
    var percent by remember { mutableIntStateOf(100) }
    var enteredUnits by remember { mutableStateOf(0.0) }
    var minutes by remember { mutableIntStateOf(30) }
    var pendingCancel by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }
    var pendingError by remember { mutableStateOf<String?>(null) }

    // Transition Loading → CancelActive / PickMode once tempRateActive is
    // known. Done in LaunchedEffect rather than inline so recomposition during
    // picker interaction doesn't bounce the user back through the state.
    LaunchedEffect(tempRateActive) {
        if (step == TempBasalStep.Loading && tempRateActive != null) {
            step = if (tempRateActive == true) TempBasalStep.CancelActive
                else TempBasalStep.PickMode
        }
    }

    // Early-return alerts — same in-place pattern ProfileSwitchScreen uses for
    // its confirm. Keep `pendingError` first so an error from the PickUnits
    // validation wins over an already-open confirm dialog.
    if (pendingError != null) {
        ErrorAlert(
            message = pendingError ?: "",
            onDismiss = { pendingError = null },
        )
        return
    }
    if (pendingCancel) {
        CancelTempBasalAlert(
            details = tempRateDetails,
            onCancel = { pendingCancel = false },
            onConfirm = {
                sendPumpCommands(SendType.BUST_CACHE, listOf(StopTempRateRequest()))
                pollTempRate()
                pendingCancel = false
                onDone()
            },
        )
        return
    }
    if (pendingConfirm) {
        SetTempBasalConfirmAlert(
            percent = percent,
            minutes = minutes,
            mode = mode,
            enteredUnits = enteredUnits,
            onCancel = { pendingConfirm = false },
            onConfirm = {
                sendPumpCommands(
                    SendType.BUST_CACHE,
                    listOf(SetTempRateRequest(minutes, percent)),
                )
                pollTempRate()
                pendingConfirm = false
                onDone()
            },
        )
        return
    }

    when (step) {
        TempBasalStep.Loading -> LoadingMessage()
        TempBasalStep.CancelActive -> CancelActivePanel(
            details = tempRateDetails,
            onCancelTapped = { pendingCancel = true },
        )
        TempBasalStep.PickMode -> PickModePanel(
            currentBasalRate = currentBasalRate,
            onPercentTapped = {
                mode = TempBasalMode.PERCENT
                step = TempBasalStep.PickPercent
            },
            onUnitsTapped = {
                mode = TempBasalMode.UNITS
                step = TempBasalStep.PickUnits
            },
        )
        TempBasalStep.PickPercent -> SingleNumberPicker(
            modifier = Modifier.fillMaxSize(),
            label = "%",
            minNumber = 0,
            maxNumber = 250,
            defaultNumber = percent,
            onNumberConfirm = { value ->
                percent = value
                step = TempBasalStep.PickMinutes
            },
        )
        TempBasalStep.PickUnits -> DecimalNumberPicker(
            modifier = Modifier.fillMaxSize(),
            label = "U/hr",
            // Cap at 5 U/hr to keep the picker manageable on a watch. The
            // realistic Tandem basal ceiling is well below that for almost
            // all users; if someone genuinely needs a higher absolute rate
            // they can take the Percent path (which maps 1:1 to the pump's
            // 0-250% range).
            maxNumber = 5,
            defaultNumber = currentBasalRate ?: 1.0,
            onNumberConfirm = { value ->
                val rate = currentBasalRate
                val derived = validateUnitsToPercent(value, rate)
                when (derived) {
                    is UnitsValidation.Ok -> {
                        enteredUnits = value
                        percent = derived.percent
                        step = TempBasalStep.PickMinutes
                    }
                    is UnitsValidation.Error -> {
                        pendingError = derived.message
                    }
                }
            },
        )
        TempBasalStep.PickMinutes -> SingleNumberPicker(
            modifier = Modifier.fillMaxSize(),
            label = "min",
            minNumber = 15,
            maxNumber = 480,
            defaultNumber = minutes,
            onNumberConfirm = { value ->
                // SingleNumberPicker returns 0 if a trailing blank option was
                // selected. Floor that to the minimum so we never send a
                // duration the pump will reject.
                minutes = if (value < 15) 15 else value
                pendingConfirm = true
            },
        )
    }
}

private enum class TempBasalStep {
    Loading,
    CancelActive,
    PickMode,
    PickPercent,
    PickUnits,
    PickMinutes,
}

private enum class TempBasalMode {
    PERCENT,
    UNITS,
}

private sealed class UnitsValidation {
    data class Ok(val percent: Int) : UnitsValidation()
    data class Error(val message: String) : UnitsValidation()
}

/**
 * Mirrors mobile's TempRateWindow.buildTempRateValidationResult for the
 * UNITS branch: returns the derived percent 0–250 or an Error describing
 * why the entered U/hr value is unacceptable.
 */
private fun validateUnitsToPercent(
    rawUnits: Double,
    currentBasalRate: Double?,
): UnitsValidation {
    if (currentBasalRate == null || currentBasalRate <= 0.0) {
        return UnitsValidation.Error("Current basal rate unavailable.")
    }
    if (rawUnits < 0.0) {
        return UnitsValidation.Error("Rate must be 0 or greater.")
    }
    if (rawUnits != 0.0 && rawUnits < 0.05) {
        return UnitsValidation.Error("Rate must be 0 or at least 0.05 U/hr.")
    }
    val derived = ((rawUnits / currentBasalRate) * 100.0).roundToInt()
    if (derived < 0 || derived > 250) {
        return UnitsValidation.Error("Effective rate ${derived}% exceeds 250% max.")
    }
    return UnitsValidation.Ok(derived)
}

/** Extracts the Double basal rate from the DataStore's String form ("1.250u"). */
private fun parseBasalRateString(raw: String?): Double? {
    if (raw == null) return null
    val numeric = raw.filter { it.isDigit() || it == '.' }
    return numeric.toDoubleOrNull()
}

@Composable
private fun LoadingMessage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Checking temp basal…",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PickModePanel(
    currentBasalRate: Double?,
    onPercentTapped: () -> Unit,
    onUnitsTapped: () -> Unit,
) {
    val unitsAvailable = currentBasalRate != null && currentBasalRate > 0.0
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Temp basal by…",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Chip(
            onClick = onPercentTapped,
            label = { Text("Percent", fontSize = 13.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        Chip(
            onClick = { if (unitsAvailable) onUnitsTapped() },
            enabled = unitsAvailable,
            label = { Text("U/hr", fontSize = 13.sp) },
            secondaryLabel = {
                Text(
                    text = if (unitsAvailable) "Base ${formatRate(currentBasalRate!!)} U/hr"
                        else "Basal rate unavailable",
                    fontSize = 10.sp,
                )
            },
            colors = if (unitsAvailable) ChipDefaults.primaryChipColors()
                else ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CancelActivePanel(
    details: TempRateResponse?,
    onCancelTapped: () -> Unit,
) {
    val subtitle = details?.let {
        "${it.percentage}% for ${formatDurationMinutes((it.duration / 60).toInt())}"
    } ?: "Active temp basal"

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Temp basal",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        Chip(
            onClick = onCancelTapped,
            label = { Text("Cancel temp basal", fontSize = 13.sp) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CancelTempBasalAlert(
    details: TempRateResponse?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val titleText = details?.let {
        "Cancel ${it.percentage}% for ${formatDurationMinutes((it.duration / 60).toInt())}?"
    } ?: "Cancel this temp basal?"
    Alert(
        title = {
            Text(
                text = titleText,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
        },
        negativeButton = {
            Button(onClick = onCancel, colors = ButtonDefaults.secondaryButtonColors()) {
                Icon(Icons.Filled.Clear, contentDescription = "Keep temp basal")
            }
        },
        positiveButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.primaryButtonColors()) {
                Icon(Icons.Filled.Check, contentDescription = "Cancel temp basal")
            }
        },
        icon = {
            Image(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Cancel temp basal",
                modifier = Modifier.size(24.dp),
            )
        },
    ) {}
}

@Composable
private fun SetTempBasalConfirmAlert(
    percent: Int,
    minutes: Int,
    mode: TempBasalMode,
    enteredUnits: Double,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val headline = when (mode) {
        TempBasalMode.PERCENT -> "Set $percent% for ${formatDurationMinutes(minutes)}?"
        TempBasalMode.UNITS -> "Set ${formatRate(enteredUnits)} U/hr ($percent%) for ${formatDurationMinutes(minutes)}?"
    }
    Alert(
        title = {
            Text(
                text = headline,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
        },
        negativeButton = {
            Button(onClick = onCancel, colors = ButtonDefaults.secondaryButtonColors()) {
                Icon(Icons.Filled.Clear, contentDescription = "Cancel")
            }
        },
        positiveButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.primaryButtonColors()) {
                Icon(Icons.Filled.Check, contentDescription = "Confirm temp basal")
            }
        },
        icon = {
            Image(
                imageVector = Icons.Filled.Speed,
                contentDescription = "Temp basal",
                modifier = Modifier.size(24.dp),
            )
        },
    ) {}
}

@Composable
private fun ErrorAlert(
    message: String,
    onDismiss: () -> Unit,
) {
    Alert(
        title = {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
            )
        },
        negativeButton = {},
        positiveButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.primaryButtonColors()) {
                Icon(Icons.Filled.Check, contentDescription = "OK")
            }
        },
        icon = {
            Image(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Error",
                modifier = Modifier.size(24.dp),
            )
        },
    ) {}
}

private fun formatDurationMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0 min"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun formatRate(rate: Double): String = "%.2f".format(rate)
