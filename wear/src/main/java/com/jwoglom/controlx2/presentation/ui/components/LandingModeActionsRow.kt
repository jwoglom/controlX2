package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.shared.enums.UserMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.KingBed

@Composable
fun LandingModeActionsRow(onExerciseClick: () -> Unit, onSleepClick: () -> Unit) {
    val controlIQMode = LocalDataStore.current.controlIQMode.observeAsState().value
    LazyRow {
        item {
            Chip(
                onClick = onExerciseClick,
                label = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Exercise") },
                secondaryLabel = { Text(if (controlIQMode == UserMode.EXERCISE) "ON" else "OFF") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.width(12.dp)) }
        item {
            Chip(
                onClick = onSleepClick,
                label = { Icon(Icons.Filled.KingBed, contentDescription = "Sleep") },
                secondaryLabel = { Text(if (controlIQMode == UserMode.SLEEP) "ON" else "OFF") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.width(12.dp)) }
        item {
            Chip(
                onClick = {},
                label = {
                    Icon(
                        painterResource(R.drawable.pump),
                        tint = Color.Unspecified,
                        contentDescription = "Pump icon",
                        modifier = Modifier.size(24.dp),
                    )
                },
                secondaryLabel = { Text(" ") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun LandingModeActionsRowPreview() {
    LandingModeActionsRow(onExerciseClick = {}, onSleepClick = {})
}
