package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.icons.filled.SensorAlert

/**
 * Sensor Info Card displaying CGM sensor expiration and transmitter battery.
 */
@Composable
fun SensorInfoCard(
    cgmSessionState: CGMSessionState? = null,
    sensorExpiration: String? = null,
    transmitterBattery: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
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
                if (cgmSessionState == CGMSessionState.ACTIVE) {
                    SensorItem(
                        icon = Icons.Default.Sensors,
                        label = "Sensor Expires",
                        value = if (!sensorExpiration.isNullOrEmpty()) sensorExpiration else null,
                        color = getSensorExpirationColor(sensorExpiration)
                    )
                } else if (cgmSessionState == CGMSessionState.STARTING) {
                    SensorItem(
                        icon = Icons.Default.Sensors,
                        label = "Sensor Starting",
                        value = null,
                        color = GlucoseColors.InRange
                    )
                } else if (cgmSessionState == CGMSessionState.STOPPING) {
                    SensorItem(
                        icon = Icons.Default.Sensors,
                        label = "Sensor Stopping",
                        value = null,
                        color = GlucoseColors.InRange
                    )
                } else if (cgmSessionState == CGMSessionState.STOPPED) {
                    SensorItem(
                        icon = Icons.Default.SensorAlert,
                        label = "No Sensor Active",
                        value = null,
                        color = GlucoseColors.InRange
                    )
                }

                if (!transmitterBattery.isNullOrEmpty()) {
                    SensorItem(
                        icon = getTransmitterBatteryIcon(transmitterBattery),
                        label = "Transmitter",
                        value = transmitterBattery ?: "--",
                        color = getTransmitterBatteryColor(transmitterBattery)
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorItem(
    icon: ImageVector,
    label: String,
    value: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )

        Column {
            if (!value.isNullOrEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getSensorExpirationColor(expiration: String?): Color {
    // Color based on urgency - if contains "day" with small number, show warning
    return when {
        expiration == null -> Color.Gray
        expiration.contains("expired", ignoreCase = true) -> GlucoseColors.Severe
        expiration.contains("1 day", ignoreCase = true) ||
        expiration.contains("hours", ignoreCase = true) -> GlucoseColors.Elevated
        else -> GlucoseColors.InRange
    }
}

private fun getTransmitterBatteryIcon(battery: String?): ImageVector {
    return when {
        battery?.contains("Low", ignoreCase = true) == true -> Icons.Default.BatteryAlert
        else -> Icons.Default.BatteryFull
    }
}

private fun getTransmitterBatteryColor(battery: String?): Color {
    return when {
        battery == null -> Color.Gray
        battery.contains("Low", ignoreCase = true) -> GlucoseColors.Elevated
        battery.contains("OK", ignoreCase = true) ||
        battery.contains("Good", ignoreCase = true) -> GlucoseColors.InRange
        else -> GlucoseColors.InRange
    }
}

/**
 * SensorInfoCard that automatically reads from the DataStore.
 */
@Composable
fun SensorInfoCardFromDataStore(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val cgmSessionState = ds.cgmSessionState.observeAsState()
    val cgmSessionExpireRelative = ds.cgmSessionExpireRelative.observeAsState()
    val cgmTransmitterStatus = ds.cgmTransmitterStatus.observeAsState()

    SensorInfoCard(
        cgmSessionState = cgmSessionState.value,
        sensorExpiration = cgmSessionExpireRelative.value,
        transmitterBattery = cgmTransmitterStatus.value,
        modifier = modifier
    )
}

// Previews
@Preview(showBackground = true, name = "Good Status")
@Composable
internal fun SensorInfoCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            SensorInfoCard(
                sensorExpiration = "5 days",
                transmitterBattery = "OK"
            )
        }
    }
}

@Preview(showBackground = true, name = "Low Status")
@Composable
internal fun SensorInfoCardLowPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            SensorInfoCard(
                sensorExpiration = "1 day",
                transmitterBattery = "Low"
            )
        }
    }
}

@Preview(showBackground = true, name = "Urgent Status")
@Composable
internal fun SensorInfoCardUrgentPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            SensorInfoCard(
                sensorExpiration = "6 hours",
                transmitterBattery = "OK"
            )
        }
    }
}

@Preview(showBackground = true, name = "No Values")
@Composable
internal fun SensorInfoCardEmptyPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            SensorInfoCard(
                sensorExpiration = null,
                transmitterBattery = null
            )
        }
    }
}
