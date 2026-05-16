package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.screens.sections.components.CgmReadingHistoryLogs
import com.jwoglom.controlx2.presentation.screens.sections.components.toCgmDataPoint
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.CarbColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground

/**
 * Therapy Metrics Card displaying IOB
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
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(Spacing.CardPadding),
            verticalArrangement = Arrangement.Center
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

                if (cob != null) {
                    MetricDisplay(
                        label = "COB",
                        value = cob?.let { "%.0f g".format(it) } ?: "--",
                        color = CarbColor
                    )
                }

                if (timeInRange != null) {
                    MetricDisplay(
                        label = "TIR",
                        value = timeInRange?.let { "${it.toInt()}%" } ?: "--",
                        color = GlucoseColors.InRange
                    )
                }
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
            style = MaterialTheme.typography.headlineLarge,
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
 * TherapyMetricsCard that reads IOB from the DataStore and computes 24h Time-In-Range
 * (70–180 mg/dL) from the CGM history log.
 */
@Composable
fun TherapyMetricsCardFromDataStore(
    historyLogViewModel: HistoryLogViewModel? = null,
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val iobUnits = ds.iobUnits.observeAsState()

    val iob = iobUnits.value

    val cgmHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        CgmReadingHistoryLogs,
        288 // ~24h at one reading per 5 minutes
    )?.observeAsState()

    val timeInRange = remember(cgmHistoryLogs?.value) {
        cgmHistoryLogs?.value?.let { logs ->
            val readings = logs.mapNotNull { it.toCgmDataPoint()?.value }
            if (readings.isEmpty()) null
            else readings.count { it in 70f..180f }.toFloat() / readings.size * 100f
        }
    }

    TherapyMetricsCard(
        iob = iob?.toFloat(),
        cob = null,
        timeInRange = timeInRange,
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
