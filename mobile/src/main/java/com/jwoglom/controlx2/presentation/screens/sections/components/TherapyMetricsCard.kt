package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.CarbColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground

/**
 * Therapy Metrics Card displaying IOB, COB, and Time in Range.
 * Provides a quick overview of current therapy status.
 */
@Composable
fun TherapyMetricsCard(
    iob: Float? = null,
    cob: Float? = null,
    timeInRange: Float? = null,
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
                MetricDisplay(
                    label = "IOB",
                    value = iob?.let { "%.2f U".format(it) } ?: "--",
                    color = InsulinColors.Bolus
                )

                MetricDisplay(
                    label = "COB",
                    value = cob?.let { "%.0f g".format(it) } ?: "--",
                    color = CarbColor
                )

                MetricDisplay(
                    label = "TIR",
                    value = timeInRange?.let { "${it.toInt()}%" } ?: "--",
                    color = GlucoseColors.InRange
                )
            }
        }
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * TherapyMetricsCard that automatically reads from the DataStore.
 */
@Composable
fun TherapyMetricsCardFromDataStore(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val iobUnits = ds.iobUnits.observeAsState()

    // Parse IOB string to float (format: "X.XX U")
    val iob = iobUnits.value?.let { iobString ->
        try {
            iobString.replace(" U", "").replace("U", "").toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // COB and TIR would need to be added to DataStore or calculated from history
    // For now, we'll show -- for these values
    TherapyMetricsCard(
        iob = iob,
        cob = null,  // TODO: Implement COB calculation from carb history
        timeInRange = null,  // TODO: Implement TIR calculation from CGM history
        modifier = modifier
    )
}

// Previews
@Preview(showBackground = true, name = "All Values")
@Composable
internal fun TherapyMetricsCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            TherapyMetricsCard(
                iob = 2.45f,
                cob = 35f,
                timeInRange = 78f
            )
        }
    }
}

@Preview(showBackground = true, name = "Some Values")
@Composable
internal fun TherapyMetricsCardPartialPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            TherapyMetricsCard(
                iob = 1.23f,
                cob = null,
                timeInRange = 85f
            )
        }
    }
}

@Preview(showBackground = true, name = "No Values")
@Composable
internal fun TherapyMetricsCardEmptyPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            TherapyMetricsCard(
                iob = null,
                cob = null,
                timeInRange = null
            )
        }
    }
}
