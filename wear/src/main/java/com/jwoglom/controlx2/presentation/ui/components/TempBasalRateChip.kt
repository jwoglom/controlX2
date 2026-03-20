package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.presentation.ui.components.tempbasal.TempBasalRateMode
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces

@Composable
fun TempBasalRateChip(
    rateMode: TempBasalRateMode,
    percentValue: Int?,
    unitsPerHrValue: Double?,
    currentBasalRate: Double?,
    onClick: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val rateText = when (rateMode) {
        TempBasalRateMode.PERCENT -> if (percentValue != null) "${percentValue}%" else "—%"
        TempBasalRateMode.UNITS_PER_HR -> if (unitsPerHrValue != null) "${twoDecimalPlaces(unitsPerHrValue)} u/hr" else "— u/hr"
    }

    val modeLabel = when (rateMode) {
        TempBasalRateMode.PERCENT -> "Percent"
        TempBasalRateMode.UNITS_PER_HR -> "Units/hr"
    }

    val subtitle = when (rateMode) {
        TempBasalRateMode.PERCENT -> {
            if (percentValue != null && currentBasalRate != null) {
                val effectiveRate = currentBasalRate * percentValue / 100.0
                "${twoDecimalPlaces(effectiveRate)} u/hr"
            } else {
                "Tap to set"
            }
        }
        TempBasalRateMode.UNITS_PER_HR -> {
            if (unitsPerHrValue != null && currentBasalRate != null && currentBasalRate > 0) {
                val effectivePercent = (unitsPerHrValue / currentBasalRate * 100).toInt()
                "${effectivePercent}%"
            } else {
                "Tap to set"
            }
        }
    }

    Column {
        Chip(
            onClick = onClick,
            label = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = rateText,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            },
            secondaryLabel = {
                Text(
                    text = modeLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        Chip(
            onClick = onToggleMode,
            label = {
                Text(
                    text = when (rateMode) {
                        TempBasalRateMode.PERCENT -> "Switch to u/hr"
                        TempBasalRateMode.UNITS_PER_HR -> "Switch to %"
                    },
                    fontSize = 10.sp,
                )
            },
            colors = ChipDefaults.childChipColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
        )
    }
}
