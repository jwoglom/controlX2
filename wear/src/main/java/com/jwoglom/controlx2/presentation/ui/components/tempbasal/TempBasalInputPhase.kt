package com.jwoglom.controlx2.presentation.ui.components.tempbasal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.presentation.ui.components.TempBasalRateChip
import com.jwoglom.controlx2.presentation.ui.components.TempBasalDurationChip

enum class TempBasalRateMode {
    PERCENT,
    UNITS_PER_HR
}

@Composable
fun TempBasalInputPhase(
    modifier: Modifier,
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    rateMode: TempBasalRateMode,
    percentValue: Int?,
    unitsPerHrValue: Double?,
    currentBasalRate: Double?,
    durationHours: Int?,
    durationMinutes: Int?,
    onClickRate: () -> Unit,
    onToggleRateMode: () -> Unit,
    onClickHours: () -> Unit,
    onClickMinutes: () -> Unit,
    onContinue: () -> Unit,
) {
    val hasRate = when (rateMode) {
        TempBasalRateMode.PERCENT -> percentValue != null
        TempBasalRateMode.UNITS_PER_HR -> unitsPerHrValue != null
    }
    val hasDuration = durationHours != null || durationMinutes != null
    val totalMinutes = (durationHours ?: 0) * 60 + (durationMinutes ?: 0)
    val canContinue = hasRate && hasDuration && totalMinutes >= 15

    ScalingLazyColumn(
        modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams()
    ) {
        item {
            TempBasalRateChip(
                rateMode = rateMode,
                percentValue = percentValue,
                unitsPerHrValue = unitsPerHrValue,
                currentBasalRate = currentBasalRate,
                onClick = onClickRate,
                onToggleMode = onToggleRateMode,
            )
        }

        item {
            TempBasalDurationChip(
                hours = durationHours,
                minutes = durationMinutes,
                onClickHours = onClickHours,
                onClickMinutes = onClickMinutes,
            )
        }

        item {
            Spacer(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
            )
        }

        item {
            Button(onClick = onContinue, enabled = canContinue) {
                Row {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "continue",
                        modifier = Modifier
                            .size(ButtonDefaults.SmallIconSize)
                            .wrapContentSize(align = Alignment.Center),
                    )
                }
            }
        }
    }
}
