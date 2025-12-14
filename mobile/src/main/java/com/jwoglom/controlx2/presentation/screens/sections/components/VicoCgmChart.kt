package com.jwoglom.controlx2.presentation.screens.sections.components

import android.graphics.Typeface
import android.text.Layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.GridLineColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.TargetRangeColor
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.toVicoShape
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.CartesianChart.PersistentMarkerScope
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.Component
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
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

data class BolusEvent(
    val timestamp: Long,       // Pump time in seconds
    val units: Float,          // Insulin units delivered
    val isAutomated: Boolean,  // True if Control-IQ auto-bolus
    val bolusType: String      // Type identifier
)

data class BasalDataPoint(
    val timestamp: Long,       // Pump time in seconds
    val rate: Float,           // Units per hour
    val isTemp: Boolean,       // True if temporary basal
    val duration: Int?         // Duration in minutes (for temp basal)
)

private const val BOLUS_MARKER_DIAMETER_DP = 12f
private const val BOLUS_MARKER_STROKE_DP = 2f
private const val BOLUS_LABEL_TEXT_SIZE_SP = 12f
private const val BASAL_DISPLAY_RANGE = 60.0
private const val MIN_BASAL_RATE_UNITS_PER_HOUR = 3f

private data class BolusMarkerPoint(
    val position: Float,
    val event: BolusEvent
)

private data class BasalSeriesResult(
    val scheduled: List<Double>,
    val temp: List<Double>
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
    )?.observeAsState(emptyList())

    return remember(cgmData?.value, timeRange) {
        cgmData?.value?.mapNotNull { dao ->
            try {
                val parsed = dao.parse()
                val value = when (parsed) {
                    is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                    is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                    is CgmDataGxHistoryLog -> parsed.value.toFloat()
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

// Helper function to safely extract field values using reflection
private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
    return try {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        val value = field.get(obj)
        if (value is T) value else null
    } catch (e: NoSuchFieldException) {
        null
    } catch (e: Exception) {
        null
    }
}

private fun formatBolusUnits(units: Float): String {
    val pattern = if (units >= 1f) "%.1fU" else "%.2fU"
    return String.format(Locale.getDefault(), pattern, units)
}

private fun createBolusMarker(
    labelComponent: TextComponent,
    indicatorComponent: Component,
    labelText: String
): DefaultCartesianMarker {
    val formatter = DefaultCartesianMarker.ValueFormatter { _, _ -> labelText }
    return DefaultCartesianMarker(
        label = labelComponent,
        valueFormatter = formatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.Top,
        indicator = { indicatorComponent },
        indicatorSizeDp = BOLUS_MARKER_DIAMETER_DP,
        guideline = null
    )
}

// Helper function to fetch and convert Bolus data
@Composable
private fun rememberBolusData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<BolusEvent> {
    val bolusHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(BolusDeliveryHistoryLog::class.java),
        // Estimate: ~10-30 boluses per day, get enough for time range
        (timeRange.hours * 2) + 10
    )?.observeAsState()

    return remember(bolusHistoryLogs?.value, timeRange) {
        bolusHistoryLogs?.value?.mapNotNull { dao ->
            try {
                val parsed = dao.parse()
                if (parsed is BolusDeliveryHistoryLog) {
                    val bolusClass = parsed.javaClass

                    // Extract insulin units
                    val units = tryGetField<Int>(bolusClass, parsed, "totalVolumeDelivered")
                        ?.let { it / 100f }  // Convert to units (0.01U precision)
                        ?: 0f

                    // Determine if automated bolus
                    val bolusSource = tryGetField<Any>(bolusClass, parsed, "bolusSource")?.toString() ?: ""
                    val isAutomated = bolusSource.contains("CLOSED_LOOP_AUTO_BOLUS", ignoreCase = true)

                    // Get bolus type
                    val bolusType = tryGetField<Any>(bolusClass, parsed, "bolusType")?.toString() ?: "UNKNOWN"

                    val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

                    if (units > 0) {
                        BolusEvent(
                            timestamp = timestamp,
                            units = units,
                            isAutomated = isAutomated,
                            bolusType = bolusType
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

// Helper function to fetch and convert Basal data
@Composable
private fun rememberBasalData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<BasalDataPoint> {
    // Try to find basal-related HistoryLog types
    val basalClasses = listOfNotNull(
            com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog::class.java,
            com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog::class.java,
            com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog::class.java
        )

    val basalHistoryLogs = if (basalClasses.isNotEmpty()) {
        historyLogViewModel?.latestItemsForTypes(
            basalClasses,
            // Basal changes: ~1-4 per hour for temp basals
            (timeRange.hours * 4) + 10
        )?.observeAsState()
    } else null

    return remember(basalHistoryLogs?.value, timeRange) {
        basalHistoryLogs?.value?.mapNotNull { dao: com.jwoglom.controlx2.db.historylog.HistoryLogItem ->
            try {
                val parsed = dao.parse()
                val basalClass = parsed.javaClass

                // Extract basal rate
                val rate = tryGetField<Int>(basalClass, parsed, "basalRate")
                    ?.let { it / 1000f }  // Convert from milli-units/hr to units/hr
                    ?: tryGetField<Int>(basalClass, parsed, "rate")
                        ?.let { it / 1000f }
                    ?: 0f

                // Determine if temp basal
                val isTemp = tryGetField<Boolean>(basalClass, parsed, "isTemp")
                    ?: tryGetField<String>(basalClass, parsed, "basalType")?.contains("TEMP", ignoreCase = true)
                    ?: false

                // Get duration
                val durationSeconds = tryGetField<Int>(basalClass, parsed, "duration")
                    ?: tryGetField<Long>(basalClass, parsed, "duration")?.toInt()
                val duration = durationSeconds?.let { it / 60 } // Convert to minutes

                val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

                if (rate > 0) {
                    BasalDataPoint(
                        timestamp = timestamp,
                        rate = rate,
                        isTemp = isTemp,
                        duration = duration
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

private fun buildBasalSeries(
    cgmDataPoints: List<CgmDataPoint>,
    basalDataPoints: List<BasalDataPoint>
): BasalSeriesResult {
    if (cgmDataPoints.isEmpty() || basalDataPoints.isEmpty()) {
        return BasalSeriesResult(emptyList(), emptyList())
    }

    val basalMaxRate = max(MIN_BASAL_RATE_UNITS_PER_HOUR, basalDataPoints.maxOfOrNull { it.rate } ?: MIN_BASAL_RATE_UNITS_PER_HOUR)
    val scheduled = mutableListOf<Double>()
    val temp = mutableListOf<Double>()

    cgmDataPoints.forEach { cgmPoint ->
        val relevantBasal = basalDataPoints
            .filter { it.timestamp <= cgmPoint.timestamp }
            .maxByOrNull { it.timestamp }

        if (relevantBasal != null) {
            val normalized = (relevantBasal.rate / basalMaxRate) * BASAL_DISPLAY_RANGE
            if (relevantBasal.isTemp) {
                scheduled.add(Double.NaN)
                temp.add(normalized)
            } else {
                scheduled.add(normalized)
                temp.add(Double.NaN)
            }
        } else {
            scheduled.add(Double.NaN)
            temp.add(Double.NaN)
        }
    }

    return BasalSeriesResult(
        scheduled = scheduled,
        temp = temp
    )
}


@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier,
    timeRange: TimeRange = TimeRange.SIX_HOURS
) {
    // Fetch all chart data
    val cgmDataPoints = rememberCgmChartData(historyLogViewModel, timeRange)
    val bolusEvents = rememberBolusData(historyLogViewModel, timeRange)
    val basalDataPoints = rememberBasalData(historyLogViewModel, timeRange)

    val cgmSeries = remember(cgmDataPoints) {
        cgmDataPoints.map { it.value.toDouble() }
    }
    val basalSeriesResult = remember(cgmDataPoints, basalDataPoints) {
        buildBasalSeries(cgmDataPoints, basalDataPoints)
    }
    val hasScheduledBasalSeries = basalSeriesResult.scheduled.any { !it.isNaN() }
    val hasTempBasalSeries = basalSeriesResult.temp.any { !it.isNaN() }

    val circleShape = remember { CircleShape.toVicoShape() }
    val bolusLabelColor = MaterialTheme.colorScheme.onSurface
    val bolusLabelComponent = remember(bolusLabelColor) {
        TextComponent(
            color = bolusLabelColor.toArgb(),
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            textSizeSp = BOLUS_LABEL_TEXT_SIZE_SP,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            lineHeightSp = null,
            lineCount = 1,
            truncateAt = null,
            margins = Insets(0f, 0f, 0f, 4f),
            padding = Insets(6f, 2f, 6f, 2f),
            background = null,
            minWidth = TextComponent.MinWidth.Companion.fixed(0f)
        )
    }

    val manualIndicatorComponent = rememberShapeComponent(
        fill(InsulinColors.Bolus),
        circleShape,
        Insets(),
        fill(ComposeColor.White),
        BOLUS_MARKER_STROKE_DP.dp,
        null
    )
    val autoIndicatorComponent = rememberShapeComponent(
        fill(InsulinColors.AutoBolus),
        circleShape,
        Insets(),
        fill(ComposeColor.White),
        BOLUS_MARKER_STROKE_DP.dp,
        null
    )

    // Create model producer for chart data
    val modelProducer = remember { CartesianChartModelProducer() }

    // Calculate glucose range for positioning markers
    val glucoseRange = remember(cgmDataPoints) {
        if (cgmDataPoints.isNotEmpty()) {
            val values = cgmDataPoints.map { it.value }
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 300f
            Pair(min, max)
        } else {
            Pair(0f, 300f)
        }
    }
    val maxGlucose = glucoseRange.second
    val minGlucose = glucoseRange.first
    val glucoseSpan = maxGlucose - minGlucose

    // Update chart data when data changes
    LaunchedEffect(cgmSeries, basalSeriesResult) {
        if (cgmSeries.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(cgmSeries)
                    if (hasScheduledBasalSeries) {
                        series(basalSeriesResult.scheduled)
                    }
                    if (hasTempBasalSeries) {
                        series(basalSeriesResult.temp)
                    }
                }
            }
        }
    }

    if (cgmDataPoints.isEmpty()) {
        // Show placeholder when no data
        Text(
            text = "No CGM data available for selected time range",
            modifier = modifier.fillMaxWidth().height(300.dp).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        // Create persistent markers for bolus events
        // Map bolus events to their x-positions (indices in the CGM data series) along with bolus data
        val bolusMarkerPoints = remember(bolusEvents, cgmDataPoints) {
            if (bolusEvents.isEmpty() || cgmDataPoints.isEmpty()) {
                emptyList<BolusMarkerPoint>()
            } else {
                bolusEvents.mapNotNull { bolus ->
                    // Find the closest CGM data point index for this bolus timestamp
                    val closestIndex = cgmDataPoints.indexOfFirst { 
                        it.timestamp >= bolus.timestamp 
                    }.takeIf { it >= 0 } ?: cgmDataPoints.size - 1
                    
                    // Convert index to x-value (float position in the series)
                    BolusMarkerPoint(
                        position = closestIndex.toFloat(),
                        event = bolus
                    )
                }
            }
        }
        
        val bolusMarkers = remember(
            bolusMarkerPoints,
            bolusLabelComponent,
            manualIndicatorComponent,
            autoIndicatorComponent
        ) {
            bolusMarkerPoints.map { markerPoint ->
                val indicator = if (markerPoint.event.isAutomated) {
                    autoIndicatorComponent
                } else {
                    manualIndicatorComponent
                }
                val marker = createBolusMarker(
                    labelComponent = bolusLabelComponent,
                    indicatorComponent = indicator,
                    labelText = formatBolusUnits(markerPoint.event.units)
                )
                markerPoint.position to marker
            }
        }

        val persistentMarkers = remember(bolusMarkers) {
            if (bolusMarkers.isEmpty()) {
                null
            } else {
                { scope: PersistentMarkerScope, _: ExtraStore ->
                    bolusMarkers.forEach { (xValue, marker) ->
                        with(scope) {
                            marker.at(xValue)
                        }
                    }
                }
            }
        }
        
        // Display the chart with Vico
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                persistentMarkers = persistentMarkers
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth().height(300.dp)
        )
        
        // TODO Phase 4: Enhance marker styling
        // - Handle overlapping markers and optional guideline styling
        
        // TODO Phase 4: Enhance basal rate visualization
        // - Consider stepped columns/area fill for clearer temp vs scheduled states
    }
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
            // Header with time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
//                Text(
//                    "Glucose History",
//                    style = MaterialTheme.typography.titleLarge,
//                    color = MaterialTheme.colorScheme.onSurface
//                )

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

private fun createPreviewHistoryLogViewModel(items: List<HistoryLogItem>): HistoryLogViewModel {
    return HistoryLogViewModel(
        HistoryLogRepo(
            HistoryLogDummyDao(items.toMutableList())
        ), 0
    )
}

// Preview helper function to create Bolus entries

private fun createBolusEntry(
    index: Int,
    timestamp: Long,
    units: Float,
    isAutomated: Boolean = false
): BolusDeliveryHistoryLog {
    val actualTimestamp = timestamp
    return BolusDeliveryHistoryLog(
        actualTimestamp,
        (index + 1000).toLong(),  // Unique sequence ID
        (index + 1000),  // Bolus ID
        LastBolusStatusAbstractResponse.BolusStatus.COMPLETE.id(),
        setOf(BolusDeliveryHistoryLog.BolusType.FOOD1),
        if (isAutomated)
            BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
        else
            BolusDeliveryHistoryLog.BolusSource.GUI,
        0,
        0,
        0,
        0,
        0,
        InsulinUnit.from1To1000(units).toInt()
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
            val previewViewModel = remember { createPreviewHistoryLogViewModel(sampleData) }

            VicoCgmChartCard(
                historyLogViewModel = previewViewModel
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
            val previewViewModel = remember { createPreviewHistoryLogViewModel(sampleData) }

            VicoCgmChartCard(
                historyLogViewModel = previewViewModel
            )
        }
    }
}

@Preview(showBackground = true, name = "Low Glucose")
@Composable
internal fun VicoCgmChartCardLowPreview() {
    val sampleData = listOf(
        120, 115, 110, 105, 100, 95, 90, 85, 80, 75, 70, 65, 60, 58, 55, 58, 62, 68, 75, 82,
        90, 98, 105, 112, 118, 125, 130, 135, 138, 140, 142, 140, 138, 135, 132, 130, 128, 125, 122, 120
    ).mapIndexed { index, mgdl ->
        createCgmEntry(index, mgdl)
    }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val previewViewModel = remember { createPreviewHistoryLogViewModel(sampleData) }
            VicoCgmChartCard(
                historyLogViewModel = previewViewModel,
            )
        }
    }
}

@Preview(showBackground = true, name = "Volatile Glucose")
@Composable
internal fun VicoCgmChartCardVolatilePreview() {
    val sampleData = listOf(
        150, 165, 145, 170, 140, 180, 135, 190, 130, 200, 125, 210, 120, 205, 125, 195, 135, 180, 145, 165,
        155, 150, 160, 145, 170, 140, 175, 138, 180, 135, 185, 132, 180, 135, 170, 140, 160, 145, 150, 148
    ).mapIndexed { index, mgdl ->
        createCgmEntry(index, mgdl)
    }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val previewViewModel = remember { createPreviewHistoryLogViewModel(sampleData) }
            VicoCgmChartCard(
                historyLogViewModel = previewViewModel,
            )
        }
    }
}

@Preview(showBackground = true, name = "Steady Trend")
@Composable
internal fun VicoCgmChartCardSteadyPreview() {
    val sampleData = listOf(
        110, 110, 111, 111, 112, 112, 113, 113, 114, 114, 115, 115, 116, 116, 117, 117, 118, 118, 119, 119,
        120, 120, 121, 121, 122, 122, 123, 123, 124, 124, 125, 125, 126, 126, 127, 127, 128, 128, 129, 129
    ).mapIndexed { index, mgdl ->
        createCgmEntry(index, mgdl)
    }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val previewViewModel = remember { createPreviewHistoryLogViewModel(sampleData) }
            VicoCgmChartCard(
                historyLogViewModel = previewViewModel,
            )
        }
    }
}


@Preview(showBackground = true, name = "With Boluses")
@Composable
internal fun VicoCgmChartCardWithBolusPreview() {

    val baseTimestamp = 1700000000L

    // CGM data
    val cgmData = listOf(
        120, 125, 130, 135, 140, 150, 160, 170, 180, 190, 200, 210, 220, 225, 220, 210, 200, 190, 180, 170,
        160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125, 122, 120, 118, 115
    ).mapIndexed { index, mgdl ->
        createCgmEntry(index, mgdl, baseTimestamp)
    }.map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

    // Bolus data - add some manual and auto boluses
    val bolusData = listOf(
        // Manual bolus at index 5 (before meal spike)
        createBolusEntry(0, baseTimestamp + (5 * 300L), 5.2f, false),
        // Auto bolus at index 10 (during spike)
        createBolusEntry(1, baseTimestamp + (10 * 300L), 1.5f, true),
        // Auto bolus at index 15 (peak)
        createBolusEntry(2, baseTimestamp + (15 * 300L), 2.0f, true),
        // Manual bolus at index 30 (separate meal)
        createBolusEntry(3, baseTimestamp + (30 * 300L), 4.0f, false)
    ).map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

    val allData = (cgmData + bolusData).toMutableList()

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val previewViewModel = remember { createPreviewHistoryLogViewModel(allData) }
            VicoCgmChartCard(
                historyLogViewModel = previewViewModel,
            )
        }
    }
}
