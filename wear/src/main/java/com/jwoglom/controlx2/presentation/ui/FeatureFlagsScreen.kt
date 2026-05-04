package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.shared.FeatureFlag

/**
 * Watch-side toggle for [FeatureFlag] values. Each row mirrors the
 * `PayloadToggleChip` style in `XdripSettingsScreen`: primary chip colors when
 * on, secondary when off. Storage is per-device — flipping here does not
 * affect the phone, by design.
 */
@Composable
fun FeatureFlagsScreen() {
    val context = LocalContext.current
    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        state = state,
        autoCentering = AutoCenteringParams(),
    ) {
        FeatureFlag.values().forEach { flag ->
            item {
                var enabled by remember { mutableStateOf(FeatureFlag.enabled(context, flag)) }
                Chip(
                    onClick = {
                        enabled = !enabled
                        FeatureFlag.set(context, flag, enabled)
                    },
                    label = { Text(flag.slug, fontSize = 12.sp) },
                    secondaryLabel = { Text(if (enabled) "On" else "Off", fontSize = 10.sp) },
                    colors = if (enabled) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
