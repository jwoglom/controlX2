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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.decoration.rememberHorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.color
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.shader.DynamicShader
import com.patrykandpatrick.vico.core.common.shape.Shape
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
                    CgmDataPoint(
                        timestamp = dao.pumpTimeSec,
                        value = value
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

// Glucose value formatter for Y-axis
class GlucoseValueFormatter : CartesianValueFormatter {
    override fun format(
        value: Float,
        chartValues: com.patrykandpatrick.vico.core.cartesian.data.ChartValues
    ): CharSequence {
        return value.toInt().toString()
    }
}

// Time value formatter for X-axis
class TimeValueFormatter : CartesianValueFormatter {
    override fun format(
        value: Float,
        chartValues: com.patrykandpatrick.vico.core.cartesian.data.ChartValues
    ): CharSequence {
        return try {
            val instant = Instant.ofEpochSecond(value.toLong())
            val hour = instant.atZone(ZoneId.systemDefault()).hour
            val ampm = if (hour < 12) "a" else "p"
            val displayHour = when (hour) {
                0 -> 12
                in 1..12 -> hour
                else -> hour - 12
            }
            "$displayHour$ampm"
        } catch (e: Exception) {
            ""
        }
    }
}

@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val cgmDataPoints = rememberCgmChartData(historyLogViewModel, timeRange)

    // Update chart data when CGM data changes
    LaunchedEffect(cgmDataPoints) {
        if (cgmDataPoints.isNotEmpty()) {
            modelProducer.tryRunTransaction {
                lineSeries {
                    series(cgmDataPoints.map { it.timestamp.toFloat() to it.value })
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Glucose line layer
            rememberLineCartesianLayer(
                lines = listOf(
                    rememberLine(
                        fill = remember {
                            LineCartesianLayer.LineFill.single(
                                fill(GlucoseColors.InRange)
                            )
                        },
                        thickness = 2.5.dp,
                        shader = DynamicShader.color(GlucoseColors.InRange)
                    )
                ),
            ),
            // Target range lines (decorations)
            decorations = listOf(
                // High target line (180 mg/dL)
                rememberHorizontalLine(
                    y = { 180f },
                    line = rememberLineComponent(
                        color = GlucoseColors.Elevated.copy(alpha = 0.5f),
                        thickness = 1.5.dp,
                        shape = remember {
                            Shape.dashedShape(
                                shape = Shape.Rectangle,
                                dashLength = 8.dp,
                                gapLength = 4.dp
                            )
                        }
                    ),
                    labelComponent = rememberTextComponent(
                        color = GlucoseColors.Elevated.copy(alpha = 0.7f),
                        padding = Dimensions(4f, 2f, 4f, 2f),
                        background = rememberShapeComponent(
                            fill(CardBackground.copy(alpha = 0.8f)),
                            Shape.Rectangle
                        )
                    ),
                ),
                // Low target line (80 mg/dL)
                rememberHorizontalLine(
                    y = { 80f },
                    line = rememberLineComponent(
                        color = GlucoseColors.Low.copy(alpha = 0.5f),
                        thickness = 1.5.dp,
                        shape = remember {
                            Shape.dashedShape(
                                shape = Shape.Rectangle,
                                dashLength = 8.dp,
                                gapLength = 4.dp
                            )
                        }
                    ),
                    labelComponent = rememberTextComponent(
                        color = GlucoseColors.Low.copy(alpha = 0.7f),
                        padding = Dimensions(4f, 2f, 4f, 2f),
                        background = rememberShapeComponent(
                            fill(CardBackground.copy(alpha = 0.8f)),
                            Shape.Rectangle
                        )
                    ),
                ),
            ),
            // Y-axis (glucose values)
            startAxis = rememberStartAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    padding = Dimensions(4f, 2f, 4f, 2f),
                    background = rememberShapeComponent(
                        fill(Color.Transparent),
                        Shape.Rectangle
                    )
                ),
                axis = rememberLineComponent(
                    color = GridLineColor,
                    thickness = 1.dp
                ),
                guideline = rememberLineComponent(
                    color = GridLineColor.copy(alpha = 0.5f),
                    thickness = 1.dp
                ),
                valueFormatter = GlucoseValueFormatter(),
                itemPlacer = remember {
                    VerticalAxis.ItemPlacer.step(
                        step = { 50f }  // Show labels at 50, 100, 150, 200, 250, etc.
                    )
                }
            ),
            // X-axis (time)
            bottomAxis = rememberBottomAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    padding = Dimensions(2f, 4f, 2f, 4f),
                    background = rememberShapeComponent(
                        fill(Color.Transparent),
                        Shape.Rectangle
                    )
                ),
                axis = rememberLineComponent(
                    color = GridLineColor,
                    thickness = 1.dp
                ),
                guideline = rememberLineComponent(
                    color = GridLineColor.copy(alpha = 0.5f),
                    thickness = 1.dp
                ),
                valueFormatter = TimeValueFormatter(),
                itemPlacer = remember {
                    HorizontalAxis.ItemPlacer.aligned(
                        spacing = 3  // Show time label every 3rd data point
                    )
                }
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        zoomState = rememberVicoZoomState(zoomEnabled = false)
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

@Preview(showBackground = true)
@Composable
internal fun VicoCgmChartCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            VicoCgmChartCard(
                historyLogViewModel = null
            )
        }
    }
}
