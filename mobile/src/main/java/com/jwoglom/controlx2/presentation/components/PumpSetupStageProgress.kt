package com.jwoglom.controlx2.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage

@Composable
fun PumpSetupStageProgress(
    initialSetup: Boolean = false,
) {
    val ds = LocalDataStore.current
    val setupStage = ds.pumpSetupStage.observeAsState()

    var progress by remember { mutableStateOf(0.0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )
    LaunchedEffect (setupStage.value) {
        progress = ((1.0 * (setupStage.value?.step ?: 0)) / PumpSetupStage.PUMPX2_PUMP_CONNECTED.step).toFloat()
    }

    if (!initialSetup && setupStage.value == PumpSetupStage.PUMPX2_PUMP_CONNECTED) {
        return
    }

    Line(
        "${setupStage.value?.description} (stage ${setupStage.value?.step ?: 0} of ${PumpSetupStage.PUMPX2_PUMP_CONNECTED.step})",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp)
    )
    LinearProgressIndicator(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .padding(10.dp)
            .fillMaxWidth(),
        progress = animatedProgress,
    )
}