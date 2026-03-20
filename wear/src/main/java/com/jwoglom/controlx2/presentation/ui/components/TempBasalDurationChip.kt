package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun TempBasalDurationChip(
    hours: Int?,
    minutes: Int?,
    onClickHours: () -> Unit,
    onClickMinutes: () -> Unit,
) {
    val hoursText = if (hours != null) "${hours}h" else "—h"
    val minutesText = if (minutes != null) "${minutes}m" else "—m"
    val totalMinutes = (hours ?: 0) * 60 + (minutes ?: 0)
    val isValid = totalMinutes >= 15

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Chip(
            onClick = onClickHours,
            label = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = hoursText,
                        fontSize = 16.sp,
                    )
                }
            },
            secondaryLabel = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Hours",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        )

        Chip(
            onClick = onClickMinutes,
            label = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = minutesText,
                        fontSize = 16.sp,
                    )
                }
            },
            secondaryLabel = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Mins",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        )
    }
}
