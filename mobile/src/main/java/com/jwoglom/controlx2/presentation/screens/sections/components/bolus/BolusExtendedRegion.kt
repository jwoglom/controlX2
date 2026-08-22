@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.screens.sections.components.IntegerOutlinedText

enum class BolusExtendedInputMode {
    /** The "deliver now" portion is entered as a percent of the total bolus. */
    PERCENT,

    /** The "deliver now" portion is entered as an absolute unit amount. */
    UNITS,
}

/**
 * Optional extended ("square wave") bolus controls shown below the units/carbs/BG entry.
 *
 * When [enabled] is false nothing other than the toggle renders, so the default bolus
 * layout (and its snapshot baselines) is unchanged. When enabled, the user picks how the
 * "deliver now" portion is entered (a percent of the total, or an absolute unit amount)
 * and a duration; the derived split is shown in [previewText].
 *
 * When [available] is false (e.g. Control-IQ is on and this pump's firmware doesn't allow
 * extended boluses) the toggle is shown disabled with [unavailableReason], and no inputs render.
 */
@Composable
fun BolusExtendedRegion(
    available: Boolean,
    unavailableReason: String?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    inputMode: BolusExtendedInputMode,
    onInputModeChange: (BolusExtendedInputMode) -> Unit,
    nowPercentRawValue: String?,
    onNowPercentChange: (String) -> Unit,
    nowUnitsRawValue: String?,
    onNowUnitsChange: (String) -> Unit,
    hoursRawValue: String?,
    onHoursChange: (String) -> Unit,
    minutesRawValue: String?,
    onMinutesChange: (String) -> Unit,
    previewText: String?,
    errorText: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Extended bolus", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = enabled && available,
            onCheckedChange = onEnabledChange,
            enabled = available,
        )
    }

    if (!available) {
        unavailableReason?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        return
    }

    if (!enabled) {
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FilterChip(
            selected = inputMode == BolusExtendedInputMode.PERCENT,
            onClick = { onInputModeChange(BolusExtendedInputMode.PERCENT) },
            label = { Text("Now %") },
            modifier = Modifier.padding(end = 8.dp),
        )
        FilterChip(
            selected = inputMode == BolusExtendedInputMode.UNITS,
            onClick = { onInputModeChange(BolusExtendedInputMode.UNITS) },
            label = { Text("Now (u)") },
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(0.5f)) {}
        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)
        ) {
            if (inputMode == BolusExtendedInputMode.PERCENT) {
                IntegerOutlinedText(
                    title = "Now %",
                    value = nowPercentRawValue,
                    onValueChange = onNowPercentChange,
                    imeAction = ImeAction.Next,
                )
            } else {
                DecimalOutlinedText(
                    title = "Now (u)",
                    value = nowUnitsRawValue,
                    onValueChange = onNowUnitsChange,
                    imeAction = ImeAction.Next,
                )
            }
        }
        Column(Modifier.weight(0.5f)) {}
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)
        ) {
            IntegerOutlinedText(
                title = "hours",
                value = hoursRawValue,
                onValueChange = onHoursChange,
                imeAction = ImeAction.Next,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)
        ) {
            IntegerOutlinedText(
                title = "mins",
                value = minutesRawValue,
                onValueChange = onMinutesChange,
                imeAction = ImeAction.Done,
            )
        }
    }

    errorText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    previewText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}
