package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.shared.enums.UserMode

/**
 * Active Therapy Card displaying current basal rate, last bolus, and Control-IQ mode.
 */
@Composable
fun ActiveTherapyCard(
    basalRate: String? = null,
    lastBolus: String? = null,
    controlIQMode: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TherapyItem(
                    icon = Icons.Default.Water,
                    label = "Basal",
                    value = basalRate ?: "--",
                    color = InsulinColors.Basal
                )

                TherapyItem(
                    icon = Icons.Default.Vaccines,
                    label = "Last Bolus",
                    value = lastBolus ?: "--",
                    color = InsulinColors.Bolus
                )

                TherapyItem(
                    icon = getControlIQIcon(controlIQMode),
                    label = "Mode",
                    value = controlIQMode ?: "--",
                    color = getControlIQColor(controlIQMode)
                )
            }
        }
    }
}

@Composable
private fun TherapyItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.height(24.dp)
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getControlIQIcon(mode: String?): ImageVector {
    return when {
        mode?.contains("Sleep", ignoreCase = true) == true -> Icons.Default.Bedtime
        mode?.contains("Exercise", ignoreCase = true) == true -> Icons.Default.DirectionsRun
        else -> Icons.Default.Speed
    }
}

private fun getControlIQColor(mode: String?): Color {
    return when {
        mode?.contains("Sleep", ignoreCase = true) == true -> Color(0xFF3F51B5)  // Indigo
        mode?.contains("Exercise", ignoreCase = true) == true -> Color(0xFFFF9800)  // Orange
        else -> GlucoseColors.InRange
    }
}

/**
 * ActiveTherapyCard that automatically reads from the DataStore.
 */
@Composable
fun ActiveTherapyCardFromDataStore(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val basalRate = ds.basalRate.observeAsState()
    val lastBolusStatus = ds.lastBolusStatus.observeAsState()
    val controlIQMode = ds.controlIQMode.observeAsState()
    val controlIQStatus = ds.controlIQStatus.observeAsState()

    val displayedMode = when (controlIQMode.value) {
        UserMode.SLEEP -> "Sleep"
        UserMode.EXERCISE -> "Exercise"
        else -> controlIQStatus.value?.toString() ?: "None"
    }

    ActiveTherapyCard(
        basalRate = basalRate.value?.toString(),
        lastBolus = lastBolusStatus.value?.toString(),
        controlIQMode = displayedMode,
        modifier = modifier
    )
}

// Previews
@Preview(showBackground = true, name = "Normal Mode")
@Composable
internal fun ActiveTherapyCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.85 U/hr",
                lastBolus = "2.5 U",
                controlIQMode = "Active"
            )
        }
    }
}

@Preview(showBackground = true, name = "Sleep Mode")
@Composable
internal fun ActiveTherapyCardSleepPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.65 U/hr",
                lastBolus = "1.2 U",
                controlIQMode = "Sleep"
            )
        }
    }
}

@Preview(showBackground = true, name = "Exercise Mode")
@Composable
internal fun ActiveTherapyCardExercisePreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.50 U/hr",
                lastBolus = "0.8 U",
                controlIQMode = "Exercise"
            )
        }
    }
}

@Preview(showBackground = true, name = "No Values")
@Composable
internal fun ActiveTherapyCardEmptyPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = null,
                lastBolus = null,
                controlIQMode = null
            )
        }
    }
}
