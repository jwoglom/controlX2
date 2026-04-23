package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop

@Composable
fun LandingModeActionsRow(
    onExerciseClick: () -> Unit,
    onSleepClick: () -> Unit,
    onSuspendPumpingClick: () -> Unit,
) {
    val ds = LocalDataStore.current
    val controlIQMode = ds.controlIQMode.observeAsState().value
    val basalStatus = ds.basalStatus.observeAsState().value
    LazyRow {
        item {
            Chip(
                onClick = onExerciseClick,
                label = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Exercise") },
                secondaryLabel = { Text(if (controlIQMode == UserMode.EXERCISE) "ON" else "OFF", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.width(12.dp)) }
        item {
            Chip(
                onClick = onSleepClick,
                label = { Icon(Icons.Filled.KingBed, contentDescription = "Sleep") },
                secondaryLabel = { Text(if (controlIQMode == UserMode.SLEEP) "ON" else "OFF", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.width(12.dp)) }
        item {
            // Suspend/Resume insulin. State-aware icon mirrors mobile Actions.kt:
            // PlayArrow ("resume") when the pump is suspended, otherwise Stop
            // ("suspend"). Secondary label shows the current insulin state so
            // the user doesn't tap blind.
            val suspended = basalStatus == BasalStatus.PUMP_SUSPENDED
            Chip(
                onClick = onSuspendPumpingClick,
                label = {
                    Icon(
                        imageVector = if (suspended) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                        contentDescription = if (suspended) "Resume insulin" else "Stop insulin",
                    )
                },
                secondaryLabel = {
                    Text(
                        text = if (suspended) "STOPPED" else "ON",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun LandingModeActionsRowPreview() {
    LandingModeActionsRow(onExerciseClick = {}, onSleepClick = {}, onSuspendPumpingClick = {})
}
