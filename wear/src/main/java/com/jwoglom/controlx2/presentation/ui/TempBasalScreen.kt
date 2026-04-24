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
import com.jwoglom.controlx2.presentation.components.SingleNumberPicker
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 *  - On entry, fires `TempRateRequest()` with BUST_CACHE so the active/inactive
 *    decision uses fresh state instead of cached values.
 *  - If active, offers a single "Cancel temp basal" confirmation.
 *  - If inactive, runs a two-step picker: percent (0–250, default 100) →
 *    total minutes (15–480, default 30) → final confirm → dispatch
 *    `SetTempRateRequest(totalMinutes, percent)` + 5× 1s poll of
 *    `TempRateRequest()` to catch the post-set state change.
 *
 * Percent-only entry; no U/hr mode. Mobile's `TempRateWindow.kt` supports
 * both; the U/hr path requires reading the current profile's basal rate to
 * convert, which is extra complexity for the less-common path on a small
 * screen. See docs/watch-as-host-refactor-plan.md 5f-3 for rationale.
 */
@Composable
fun TempBasalScreen(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    onDone: () -> Unit,
) {
    val ds = LocalDataStore.current
    val tempRateActive by ds.tempRateActive.observeAsState()
    val tempRateDetails by ds.tempRateDetails.observeAsState()

    // Refresh state once on entry so a stale cached response doesn't put us
    // on the wrong branch (active vs inactive).
    LaunchedEffect(Unit) {
        sendPumpCommands(SendType.BUST_CACHE, listOf(TempRateRequest()))
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
    var percent by remember { mutableIntStateOf(100) }
    var minutes by remember { mutableIntStateOf(30) }
    var pendingCancel by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }

    // Transition Loading → CancelActive / PickPercent once tempRateActive is
    // known. Done in LaunchedEffect rather than inline so recomposition during
    // picker interaction doesn't bounce the user back through the state.
    LaunchedEffect(tempRateActive) {
        if (step == TempBasalStep.Loading && tempRateActive != null) {
            step = if (tempRateActive == true) TempBasalStep.CancelActive
                else TempBasalStep.PickPercent
        }
    }

    // Render confirm Alerts first via early return — same in-place pattern as
    // ProfileSwitchScreen's ProfileSwitchConfirmAlert.
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
    PickPercent,
    PickMinutes,
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
private fun CancelActivePanel(
    details: TempRateResponse?,
    onCancelTapped: () -> Unit,
) {
    val subtitle = details?.let {
        "${it.percentage}% for ${formatDurationMinutes(it.duration / 60)}"
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
        "Cancel ${it.percentage}% for ${formatDurationMinutes(it.duration / 60)}?"
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
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Alert(
        title = {
            Text(
                text = "Set $percent% for ${formatDurationMinutes(minutes)}?",
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
