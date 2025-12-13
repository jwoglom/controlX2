package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground

@Composable
fun PumpStatusBar(middleContent: @Composable () -> Unit = {}) {
    val ds = LocalDataStore.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box (Modifier.padding(horizontal = 8.dp)) {
            val batteryPercent = ds.batteryPercent.observeAsState()
            HorizBatteryIcon(batteryPercent.value)
        }

        Box {
            middleContent()
        }

        Box (Modifier.padding(horizontal = 8.dp)) {
            val cartridgeRemainingUnits = ds.cartridgeRemainingUnits.observeAsState()
            val cartridgeRemainingEstimate = ds.cartridgeRemainingEstimate.observeAsState()
            HorizCartridgeIcon(cartridgeRemainingUnits.value,
                cartridgeAmountEstimate = cartridgeRemainingEstimate.value == true)
        }
    }
}

@Composable
fun PumpStatusCard(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val batteryPercent = ds.batteryPercent.observeAsState()
    val cartridgeRemainingUnits = ds.cartridgeRemainingUnits.observeAsState()
    val cartridgeRemainingEstimate = ds.cartridgeRemainingEstimate.observeAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery
            StatusIndicatorItem(
                icon = {
                    HorizBatteryIcon(batteryPercent.value)
                },
                label = "Battery",
                value = "${batteryPercent.value ?: "--"}%"
            )

            // Connection time (placeholder for now)
            StatusIndicatorItem(
                icon = {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Last updated",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = "Updated",
                value = "Now"  // TODO: Format last connection time
            )

            // Insulin
            StatusIndicatorItem(
                icon = {
                    HorizCartridgeIcon(
                        cartridgeRemainingUnits.value,
                        cartridgeAmountEstimate = cartridgeRemainingEstimate.value == true
                    )
                },
                label = "Insulin",
                value = "${cartridgeRemainingUnits.value ?: "--"}U"
            )
        }
    }
}

@Composable
private fun StatusIndicatorItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusBarDefaultPreview() {
    ControlX2Theme() {
        Surface(
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            PumpStatusBar(middleContent = {
                Line("Last updated: stub", bold = true)
            })
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            setUpPreviewState(LocalDataStore.current)
            PumpStatusCard()
        }
    }
}