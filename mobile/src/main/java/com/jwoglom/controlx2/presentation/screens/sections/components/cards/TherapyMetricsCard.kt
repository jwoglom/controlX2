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
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.CarbColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog

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
                        value = "%.0f g".format(cob),
                        color = CarbColor
                    )
                }

                if (timeInRange != null) {
                    MetricDisplay(
                        label = "TIR",
                        value = "${timeInRange.toInt()}%",
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
 * TherapyMetricsCard that automatically reads from the DataStore and calculates COB/TIR.
 */
@Composable
fun TherapyMetricsCardFromDataStore(
    historyLogViewModel: HistoryLogViewModel? = null,
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val iobUnits = ds.iobUnits.observeAsState()

    val iob = iobUnits.value

    // Calculate TIR from CGM history (last 24 hours)
    val cgmHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(
            DexcomG6CGMHistoryLog::class.java,
            DexcomG7CGMHistoryLog::class.java,
            CgmDataGxHistoryLog::class.java
        ),
        288 // ~24 hours of 5-min readings
    )?.observeAsState()

    val timeInRange = remember(cgmHistoryLogs?.value) {
        cgmHistoryLogs?.value?.let { logs ->
            val validReadings = logs.mapNotNull { dao ->
                val parsed = dao.parse()
                when (parsed) {
                    is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue
                    is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue
                    is CgmDataGxHistoryLog -> parsed.value
                    else -> null
                }?.takeIf { it > 0 }
            }
            if (validReadings.isNotEmpty()) {
                val inRange = validReadings.count { it in 70..180 }
                (inRange.toFloat() / validReadings.size.toFloat()) * 100f
            } else null
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
