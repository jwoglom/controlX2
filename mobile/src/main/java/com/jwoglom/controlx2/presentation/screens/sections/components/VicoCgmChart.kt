package com.jwoglom.controlx2.presentation.screens.sections.components

/**
 * CGM Chart using Vico charting library.
 **/

import android.graphics.Typeface
import android.text.Layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog.BolusSource
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.toVicoShape
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.CartesianChart.PersistentMarkerScope
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
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
import kotlin.math.roundToInt

// Data models
enum class CgmTarget(val pointColor: ComposeColor) {
    SEVERE_LOW(GlucoseColors.SevereLow),
    LOW(GlucoseColors.Low),
    IN_RANGE(GlucoseColors.InRange),
    HIGH(GlucoseColors.High),
    SEVERE_HIGH(GlucoseColors.SevereHigh)
}

private fun targetForGlucoseValue(value: Float): CgmTarget {
    // TODO(jwoglom): configurable
    return when (value) {
        in 0f..59f -> CgmTarget.SEVERE_LOW
        in 60f..79f -> CgmTarget.LOW
        in 80f..159f -> CgmTarget.IN_RANGE
        in 160f..219f -> CgmTarget.HIGH
        in 220f..400f -> CgmTarget.SEVERE_HIGH
        else -> CgmTarget.IN_RANGE
    }
}

data class CgmDataPoint(
    val timestamp: Long,
    val value: Float,
    val target: CgmTarget
)

data class BolusEvent(
    val timestamp: Long,       // Pump time in seconds
    val units: Float,         // Insulin units delivered
    val isAutomated: Boolean,  // True if Control-IQ auto-bolus
    val bolusType: String      // Type identifier
)

data class BasalDataPoint(
    val timestamp: Long,       // Pump time in seconds
    val rate: Float,           // Units per hour
    val isTemp: Boolean,       // True if temporary basal
    val duration: Int?         // Duration in minutes (for temp basal)
)

private class FixedYAxisRangeProvider(
    private val fixedMinX: Double,
    private val fixedMaxX: Double,
    private val fixedMinY: Double,
    private val fixedMaxY: Double
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = fixedMinX
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = fixedMaxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = fixedMinY
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = fixedMaxY
}

data class ChartPreviewData(
    val cgmDataPoints: List<CgmDataPoint>,
    val bolusEvents: List<BolusEvent> = emptyList(),
    val basalDataPoints: List<BasalDataPoint> = emptyList(),
    val currentTimeSeconds: Long? = null
)

private data class ChartBucket(
    val timestamp: Long,
    val value: Double?
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
                        value = value,
                        target = targetForGlucoseValue(value)
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
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
            val parsed = dao.parse()
            if (parsed is BolusDeliveryHistoryLog) {
                val units = InsulinUnit.from1000To1(parsed.deliveredTotal.toLong())

                // Determine if automated bolus
                val bolusSource = parsed.bolusSource
                val isAutomated = when (bolusSource) {
                    BolusSource.GUI -> false
                    BolusSource.BLUETOOTH_REMOTE_BOLUS -> false
                    BolusSource.QUICK_BOLUS -> false
                    else -> true
                }

                // Get bolus type
                val bolusType = parsed.bolusTypes.map { it.name }.joinToString(", ")

                val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

                if (units > 0) {
                    BolusEvent(
                        timestamp = timestamp,
                        units = units.toFloat(),
                        isAutomated = isAutomated,
                        bolusType = bolusType
                    )
                } else null
            } else null
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

// Helper function to fetch and convert Basal data
@Composable
private fun rememberBasalData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<BasalDataPoint> {

    val basalHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(
            BasalRateChangeHistoryLog::class.java
        ),
        (timeRange.hours * 60)
    )?.observeAsState()

    return remember(basalHistoryLogs?.value, timeRange) {
        basalHistoryLogs?.value?.mapNotNull { dao: HistoryLogItem ->
            val parsed = dao.parse()

            val rate = when (parsed) {
                is BasalRateChangeHistoryLog -> parsed.commandBasalRate
                else -> null
            }

            val isTemp = false
            val durationMins = 5
            val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

            if (rate != null && rate > 0) {
                BasalDataPoint(
                    timestamp = timestamp,
                    rate = rate,
                    isTemp = isTemp,
                    duration = durationMins
                )
            } else null
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

private fun buildBasalSeries(
    bucketTimes: List<Long>,
    basalDataPoints: List<BasalDataPoint>
): BasalSeriesResult {
    if (bucketTimes.isEmpty() || basalDataPoints.isEmpty()) {
        return BasalSeriesResult(emptyList(), emptyList())
    }

    // Use NaN to represent gaps - should work with segmented series approach
    val basalMaxRate = max(MIN_BASAL_RATE_UNITS_PER_HOUR, basalDataPoints.maxOfOrNull { it.rate } ?: MIN_BASAL_RATE_UNITS_PER_HOUR)
    val scheduled = mutableListOf<Double>()
    val temp = mutableListOf<Double>()

    bucketTimes.forEach { bucketTime ->
        val relevantBasal = basalDataPoints
            .filter { it.timestamp <= bucketTime }
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
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    previewData: ChartPreviewData? = null
) {
    // Fetch all chart data
    val cgmDataPoints = previewData?.cgmDataPoints ?: rememberCgmChartData(historyLogViewModel, timeRange)
    val bolusEvents = previewData?.bolusEvents ?: rememberBolusData(historyLogViewModel, timeRange)
    val basalDataPoints = previewData?.basalDataPoints ?: rememberBasalData(historyLogViewModel, timeRange)

    val currentTimeSeconds = previewData?.currentTimeSeconds ?: Instant.now().epochSecond
    val fixedGlucoseRange = Pair(30f, 410f)
    val fixedMaxGlucose = fixedGlucoseRange.second
    val fixedMinGlucose = fixedGlucoseRange.first
    val fixedGlucoseSpan = fixedMaxGlucose - fixedMinGlucose

    val rangeSeconds = timeRange.hours * 3600L
    val bucketSeconds = 5L * 60L
    val bucketCount = max(1, (rangeSeconds / bucketSeconds).toInt() + 1)
    val startTimeSeconds = currentTimeSeconds - rangeSeconds
    val bucketTimes = List(bucketCount) { startTimeSeconds + it * bucketSeconds }

    val chartBuckets = remember(cgmDataPoints, currentTimeSeconds, timeRange) {
        val values = MutableList(bucketCount) { Double.NaN }
        cgmDataPoints.forEach { point ->
            if (point.timestamp in startTimeSeconds..currentTimeSeconds) {
                val index = ((point.timestamp - startTimeSeconds) / bucketSeconds).toInt().coerceIn(0, bucketCount - 1)
                values[index] = point.value.toDouble()
            }
        }
        bucketTimes.mapIndexed { index, ts ->
            ChartBucket(ts, values[index].takeUnless { it.isNaN() })
        }
    }

    // Split CGM data into segments to avoid drawing lines across gaps > 5 minutes
    // Each segment is padded to full length with NaN values, with valid data at correct x-positions
    // Using NaN should work better with segmented series approach
    val cgmSegments = remember(chartBuckets) {
        val segments = mutableListOf<List<Double>>()
        var currentSegmentStart: Int? = null
        
        chartBuckets.forEachIndexed { index, bucket ->
            val hasValue = bucket.value != null
            
            if (hasValue) {
                // Start a new segment if we don't have one, or continue current segment
                if (currentSegmentStart == null) {
                    currentSegmentStart = index
                }
            } else {
                // Gap detected - close current segment if exists
                if (currentSegmentStart != null) {
                    // Create a full-length series with NaN values everywhere except the segment range
                    val fullSeries = MutableList(chartBuckets.size) { Double.NaN }
                    (currentSegmentStart until index).forEach { idx ->
                        fullSeries[idx] = chartBuckets[idx].value ?: Double.NaN
                    }
                    segments.add(fullSeries)
                    currentSegmentStart = null
                }
            }
        }
        
        // Close any remaining open segment
        if (currentSegmentStart != null) {
            val fullSeries = MutableList(chartBuckets.size) { Double.NaN }
            (currentSegmentStart until chartBuckets.size).forEach { idx ->
                fullSeries[idx] = chartBuckets[idx].value ?: Double.NaN
            }
            segments.add(fullSeries)
        }
        
        segments
    }
    
    val hasValidCgmData = cgmSegments.isNotEmpty() && cgmSegments.any { segment ->
        segment.any { !it.isNaN() }
    }
    val basalSeriesResult = remember(bucketTimes, basalDataPoints) {
        buildBasalSeries(bucketTimes, basalDataPoints)
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

    val axisTimeFormatter = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    val markerValueFormatter = remember(cgmDataPoints) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
            val entry = lineTarget?.points?.firstOrNull()?.entry ?: return@ValueFormatter ""
            // Handle NaN values and invalid data safely
            if (entry.x.isNaN() || entry.y.isNaN() || cgmDataPoints.isEmpty()) {
                return@ValueFormatter ""
            }
            val index = entry.x.roundToInt().coerceIn(0, cgmDataPoints.lastIndex)
            val point = cgmDataPoints.getOrNull(index) ?: return@ValueFormatter ""
            val timeText = axisTimeFormatter.format(Date(point.timestamp * 1000))
            val glucoseText = "${entry.y.roundToInt()} mg/dL"
            "$timeText\n$glucoseText"
        }
    }
    val dragMarkerLabelColor = MaterialTheme.colorScheme.onSurface
    val dragMarkerLabel = remember(dragMarkerLabelColor) {
        TextComponent(
            color = dragMarkerLabelColor.toArgb(),
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            textSizeSp = 12f,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            lineHeightSp = null,
            lineCount = 2,
            truncateAt = null,
            margins = Insets(4f, 4f, 4f, 4f),
            padding = Insets(6f, 4f, 6f, 4f),
            background = null,
            minWidth = TextComponent.MinWidth.Companion.fixed(0f)
        )
    }
    // Re-enable the drag marker with the NaN-safe fork
    val dragMarker = rememberDefaultCartesianMarker(
        label = dragMarkerLabel,
        valueFormatter = markerValueFormatter
    )
    val axisLabels = remember(startTimeSeconds, currentTimeSeconds) {
        val offsets = listOf(0L, rangeSeconds / 2, rangeSeconds)
        offsets.map { offset ->
            val ts = startTimeSeconds + offset
            axisTimeFormatter.format(Date(ts * 1000))
        }
    }

    // Calculate glucose range for positioning markers
    val glucoseRange = fixedGlucoseRange
    val maxGlucose = fixedMaxGlucose
    val minGlucose = fixedMinGlucose
    val glucoseSpan = fixedGlucoseSpan

    // Update chart data when data changes
    // Use multiple series for CGM data to avoid drawing lines across gaps
    LaunchedEffect(cgmSegments, basalSeriesResult, hasValidCgmData) {
        if (hasValidCgmData && cgmSegments.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    // Add each CGM segment as a separate series
                    // Each segment is a full-length series with NaN values outside the segment range
                    // This prevents Vico from drawing lines connecting across gaps
                    // Using segmented series should help Vico handle NaN values correctly
                    cgmSegments.forEach { segment ->
                        series(segment)
                    }
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

    if (cgmDataPoints.isEmpty() || !hasValidCgmData) {
        // Show placeholder when no data
        Text(
            text = "No CGM data available for selected time range",
            modifier = modifier.fillMaxWidth().height(300.dp).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val yAxisLabels = remember(fixedGlucoseRange) {
            val tickCount = 6
            val step = fixedGlucoseSpan / (tickCount - 1)
            (0 until tickCount).map { (fixedMaxGlucose - step * it).toInt() }
        }

        // Create persistent markers for bolus events
        // Map bolus events to their x-positions (indices in the CGM series buckets)
        // Note: With segmented series, markers are positioned by absolute bucket index
        val bolusMarkerPoints = remember(bolusEvents, bucketTimes, chartBuckets, hasValidCgmData) {
            if (bolusEvents.isEmpty() || bucketTimes.isEmpty() || !hasValidCgmData) {
                emptyList<BolusMarkerPoint>()
            } else {
                bolusEvents.mapNotNull { bolus ->
                    // Find the bucket index that this bolus timestamp falls into
                    val bucketIndex = bucketTimes.indexOfFirst { 
                        it >= bolus.timestamp 
                    }.takeIf { it >= 0 } ?: bucketTimes.size - 1
                    
                    // Ensure index is valid and corresponds to a valid (non-NaN) data point
                    if (bucketIndex < 0 || bucketIndex >= chartBuckets.size) {
                        null
                    } else if (chartBuckets[bucketIndex].value == null) {
                        // Skip markers at gaps - find the nearest valid data point
                        val nearestValidIndex = chartBuckets
                            .mapIndexedNotNull { idx, bucket -> if (bucket.value != null) idx else null }
                            .minByOrNull { kotlin.math.abs(it - bucketIndex) }
                        
                        if (nearestValidIndex != null && nearestValidIndex >= 0 && nearestValidIndex < chartBuckets.size) {
                            BolusMarkerPoint(
                                position = nearestValidIndex.toFloat(),
                                event = bolus
                            )
                        } else {
                            null
                        }
                    } else {
                        // Valid position - use the bucket index
                        BolusMarkerPoint(
                            position = bucketIndex.toFloat(),
                            event = bolus
                        )
                    }
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
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.Small),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { label ->
                    Text(
                        text = label.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val lineRangeProvider = remember {
                FixedYAxisRangeProvider(
                    fixedMinX = (currentTimeSeconds - rangeSeconds).toDouble(),
                    fixedMaxX = currentTimeSeconds.toDouble(),
                    fixedMinY = 39.0,
                    fixedMaxY = 401.0
                )
            }
            val lineLayer = rememberLineCartesianLayer(rangeProvider = lineRangeProvider)
            val scrollState = rememberVicoScrollState(
                scrollEnabled = false,
                initialScroll = Scroll.Absolute.End
            )

            LaunchedEffect(timeRange, currentTimeSeconds) {
                scrollState.scroll(Scroll.Absolute.End)
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    lineLayer,
                    startAxis = VerticalAxis.rememberStart(
                        label = rememberAxisLabelComponent(),
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = rememberAxisLabelComponent(),
                    ),
                    marker = dragMarker, // May be null if series contains NaN
                    persistentMarkers = persistentMarkers
                ),
                scrollState = scrollState,
                consumeMoveEvents = true,
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxHeight().weight(1f)
            )
        }

        if (axisLabels.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                axisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
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
    modifier: Modifier = Modifier,
    previewData: ChartPreviewData? = null
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
                timeRange = selectedTimeRange,
                previewData = previewData
            )
        }
    }
}

private fun createCgmPreviewData(values: List<Int?>, baseTimestamp: Long = 1700000000L): List<CgmDataPoint> {
    return values.mapIndexedNotNull { index, mgdl ->
        if (mgdl == null) null
        else CgmDataPoint(
            timestamp = baseTimestamp + (index * 300L),
            value = mgdl.toFloat(),
            target = targetForGlucoseValue(mgdl.toFloat())
        )
    }
}

private fun createBolusPreviewData(
    baseTimestamp: Long,
    entries: List<Triple<Int, Double, Boolean>>
): List<BolusEvent> {
    return entries.mapIndexed { idx, (fiveMinuteIndex, units, automated) ->
        BolusEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            units = units.toFloat(),
            isAutomated = automated,
            bolusType = ""
        )
    }
}

@Preview(showBackground = true, name = "Normal Range")
@Composable
internal fun VicoCgmChartCardNormalPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleValues = listOf(
                120, 125, 130, 128, 125, 122, 118, 115, 120, 125, 130, 135, 140, 145, 142, 138, 135, 130, 125, 120,
                118, 115, 112, 110, 115, 120, 125, 130, 135, 138, 140, 142, 145, 148, 150, 152, 155, 158, 160, 162
            )
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )

            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
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
            val sampleValues = listOf(
                140, 145, 150, 160, 175, 190, 205, 220, 235, 250, 265, 280, 290, 295, 290, 280, 270, 255, 240, 225,
                210, 195, 180, 170, 160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125
            )
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )

            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Low Glucose")
@Composable
internal fun VicoCgmChartCardLowPreview() {
    val sampleValues = listOf(
        120, 115, 110, 105, 100, 95, 90, 85, 80, 75, 70, 65, 60, 58, 55, 58, 62, 68, 75, 82,
        90, 98, 105, 112, 118, 125, 130, 135, 138, 140, 142, 140, 138, 135, 132, 130, 128, 125, 122, 120
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Volatile Glucose")
@Composable
internal fun VicoCgmChartCardVolatilePreview() {
    val sampleValues = listOf(
        150, 165, 145, 170, 140, 180, 135, 190, 130, 200, 125, 210, 120, 205, 125, 195, 135, 180, 145, 165,
        155, 150, 160, 145, 170, 140, 175, 138, 180, 135, 185, 132, 180, 135, 170, 140, 160, 145, 150, 148
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Steady Trend")
@Composable
internal fun VicoCgmChartCardSteadyPreview() {
    val sampleValues = listOf(
        110, 110, 111, 111, 112, 112, 113, 113, 114, 114, 115, 115, 116, 116, 117, 117, 118, 118, 119, 119,
        120, 120, 121, 121, 122, 122, 123, 123, 124, 124, 125, 125, 126, 126, 127, 127, 128, 128, 129, 129
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}


@Preview(showBackground = true, name = "With Boluses")
@Composable
internal fun VicoCgmChartCardWithBolusPreview() {

    val baseTimestamp = 1700000000L

    // CGM data
    val cgmValues = listOf(
        120, 125, 130, 135, 140, 150, 160, 170, 180, 190, 200, 210, 220, 225, 220, 210, 200, 190, 180, 170,
        160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125, 122, 120, 118, 115
    )

    val bolusEvents = createBolusPreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Triple(5, 5.2, false),
            Triple(10, 1.5, true),
            Triple(15, 2.0, true),
            Triple(30, 4.0, false)
        )
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(cgmValues, baseTimestamp)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                bolusEvents = bolusEvents,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Test Data With Gaps")
@Composable
internal fun TestVicoCgmChart() {
    val baseTimestamp = 1700000000L

    // Create CGM data with intentional gaps
    // Segment 1: indices 0-9 (normal readings)
    // Gap: indices 10-15 (no data - simulates sensor loss/signal issues)
    // Segment 2: indices 16-24 (readings resume)
    // Gap: indices 25-29 (another gap)
    // Segment 3: indices 30-39 (final segment)
    val allDataPoints = mutableListOf<CgmDataPoint>()

    // Segment 1: Normal range (120-140 mg/dL)
    val segment1Values = listOf(120, 122, 125, 128, 130, 132, 135, 137, 138, 140)
    segment1Values.forEachIndexed { index, value ->
        allDataPoints.add(
            CgmDataPoint(
                timestamp = baseTimestamp + (index * 300L),
                value = value.toFloat(),
                target = targetForGlucoseValue(value.toFloat())
            )
        )
    }

    // Gap 1: indices 10-15 (30 minutes gap = 6 readings missing)
    // No data points added here

    // Segment 2: Rising glucose (145-195 mg/dL)
    val segment2Values = listOf(145, 150, 155, 165, 175, 180, 185, 190, 195)
    segment2Values.forEachIndexed { index, value ->
        allDataPoints.add(
            CgmDataPoint(
                timestamp = baseTimestamp + ((16 + index) * 300L),
                value = value.toFloat(),
                target = targetForGlucoseValue(value.toFloat())
            )
        )
    }

    // Gap 2: indices 25-29 (25 minutes gap = 5 readings missing)
    // No data points added here

    // Segment 3: Declining glucose (190-120 mg/dL)
    val segment3Values = listOf(190, 180, 170, 160, 150, 145, 140, 135, 130, 120)
    segment3Values.forEachIndexed { index, value ->
        allDataPoints.add(
            CgmDataPoint(
                timestamp = baseTimestamp + ((30 + index) * 300L),
                value = value.toFloat(),
                target = targetForGlucoseValue(value.toFloat())
            )
        )
    }

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val previewData = ChartPreviewData(
                cgmDataPoints = allDataPoints,
                currentTimeSeconds = allDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}
