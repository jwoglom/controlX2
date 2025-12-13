package com.jwoglom.controlx2.presentation.screens.sections.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.GridLineColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.TargetRangeColor
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
// Vico imports temporarily commented out - API needs to be updated for 2.3.6
// import com.patrykandpatrick.vico.compose.cartesian.*
// import com.patrykandpatrick.vico.core.cartesian.*
// import com.patrykandpatrick.vico.core.common.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// Data models
data class CgmDataPoint(
    val timestamp: Long,
    val value: Float
)

enum class TimeRange(val label: String, val hours: Int) {
    THREE_HOURS("3h", 3),
    SIX_HOURS("6h", 6),
    TWELVE_HOURS("12h", 12),
    TWENTY_FOUR_HOURS("24h", 24)
}

// Helper function to fetch and convert CGM data
@Composable
private fun rememberCgmChartData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<CgmDataPoint> {
    val cgmData = historyLogViewModel?.latestItemsForTypes(
        CgmReadingHistoryLogs,
        // Calculate how many data points we need (CGM reading every 5 min)
        (timeRange.hours * 60 / 5) + 20  // Extra buffer
    )?.observeAsState()

    return remember(cgmData?.value, timeRange) {
        cgmData?.value?.mapNotNull { dao ->
            try {
                val parsed = dao.parse()
                val value = when (parsed) {
                    is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                    is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                    else -> null
                }

                if (value != null && value > 0) {
                    val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                    CgmDataPoint(
                        timestamp = timestamp,
                        value = value
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

// Formatters temporarily commented out - will be restored when Vico API is fixed
// class GlucoseValueFormatter : CartesianValueFormatter { ... }
// class TimeValueFormatter : CartesianValueFormatter { ... }

@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    modifier: Modifier = Modifier
) {
    // TODO: Fix Vico 2.3.6 API compatibility
    // Temporarily showing placeholder until Vico API is properly configured
    Text(
        text = "CGM Chart (Vico API needs update)",
        modifier = modifier.fillMaxWidth().height(300.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// Time range selector component
@Composable
fun ChartTimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        TimeRange.values().forEach { range ->
            FilterChip(
                selected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        range.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (range == selectedRange) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
fun VicoCgmChartCard(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier
) {
    var selectedTimeRange by remember { mutableStateOf(TimeRange.SIX_HOURS) }

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
            // Header with title and time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Glucose History",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ChartTimeRangeSelector(
                    selectedRange = selectedTimeRange,
                    onRangeSelected = { selectedTimeRange = it }
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            // The chart
            VicoCgmChart(
                historyLogViewModel = historyLogViewModel,
                timeRange = selectedTimeRange
            )
        }
    }
}

// Preview helper function to create CGM entries
private fun createCgmEntry(index: Int, mgdl: Int, baseTimestamp: Long = 1700000000L): com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog {
    return DexcomG6CGMHistoryLog(
        baseTimestamp + (index * 300L), // 5 minutes apart
        index.toLong(),
        0, 1, -2, 6, -89,
        mgdl,
        baseTimestamp + (index * 300L),
        481, 0
    )
}

@Preview(showBackground = true, name = "Normal Range")
@Composable
internal fun VicoCgmChartCardNormalPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleData = listOf(
                120, 125, 130, 128, 125, 122, 118, 115, 120, 125, 130, 135, 140, 145, 142, 138, 135, 130, 125, 120,
                118, 115, 112, 110, 115, 120, 125, 130, 135, 138, 140, 142, 145, 148, 150, 152, 155, 158, 160, 162
            ).mapIndexed { index, mgdl ->
                createCgmEntry(index, mgdl)
            }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(sampleData.toMutableList())
                    ), 0
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "High Glucose Spike")
@Composable
internal fun VicoCgmChartCardHighPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleData = listOf(
                140, 145, 150, 160, 175, 190, 205, 220, 235, 250, 265, 280, 290, 295, 290, 280, 270, 255, 240, 225,
                210, 195, 180, 170, 160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125
            ).mapIndexed { index, mgdl ->
                createCgmEntry(index, mgdl)
            }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(sampleData.toMutableList())
                    ), 0
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "Low Glucose")
@Composable
internal fun VicoCgmChartCardLowPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleData = listOf(
                120, 115, 110, 105, 100, 95, 90, 85, 80, 75, 70, 65, 60, 58, 55, 58, 62, 68, 75, 82,
                90, 98, 105, 112, 118, 125, 130, 135, 138, 140, 142, 140, 138, 135, 132, 130, 128, 125, 122, 120
            ).mapIndexed { index, mgdl ->
                createCgmEntry(index, mgdl)
            }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(sampleData.toMutableList())
                    ), 0
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "Volatile Glucose")
@Composable
internal fun VicoCgmChartCardVolatilePreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleData = listOf(
                150, 165, 145, 170, 140, 180, 135, 190, 130, 200, 125, 210, 120, 205, 125, 195, 135, 180, 145, 165,
                155, 150, 160, 145, 170, 140, 175, 138, 180, 135, 185, 132, 180, 135, 170, 140, 160, 145, 150, 148
            ).mapIndexed { index, mgdl ->
                createCgmEntry(index, mgdl)
            }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(sampleData.toMutableList())
                    ), 0
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "Steady Trend")
@Composable
internal fun VicoCgmChartCardSteadyPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleData = listOf(
                110, 110, 111, 111, 112, 112, 113, 113, 114, 114, 115, 115, 116, 116, 117, 117, 118, 118, 119, 119,
                120, 120, 121, 121, 122, 122, 123, 123, 124, 124, 125, 125, 126, 126, 127, 127, 128, 128, 129, 129
            ).mapIndexed { index, mgdl ->
                createCgmEntry(index, mgdl)
            }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(sampleData.toMutableList())
                    ), 0
                )
            )
        }
    }
}
