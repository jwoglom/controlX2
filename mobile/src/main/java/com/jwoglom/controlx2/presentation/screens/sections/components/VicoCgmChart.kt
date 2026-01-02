package com.jwoglom.controlx2.presentation.screens.sections.components

/**
 * CGM Chart using Vico charting library.
 * 
 * NOTE: This uses a fork of Vico (https://github.com/jwoglom/vico) which includes a fix
 * for NaN handling in LineCartesianLayer.updateMarkerTargets. The upstream Vico library
 * crashes when trying to round NaN values during marker position calculations.
 * 
 * The fork adds null-safety checks before rounding y-values in updateMarkerTargets.
 */

import android.graphics.Typeface
import android.text.Layout
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
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
import com.jwoglom.controlx2.presentation.theme.CarbColor
import com.jwoglom.controlx2.presentation.theme.ModeColors
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.CartesianChart.PersistentMarkerScope
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.Component
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

data class CarbEvent(
    val timestamp: Long,       // Pump time in seconds
    val grams: Int,            // Carbohydrate grams
    val note: String? = null   // Optional note (e.g., "meal", "snack")
)

data class ModeEvent(
    val timestamp: Long,       // Pump time in seconds
    val mode: TherapyMode,     // Sleep, Exercise, or Standard
    val duration: Int?         // Duration in minutes (null = ongoing)
)

enum class TherapyMode {
    SLEEP,
    EXERCISE,
    STANDARD
}

private class FixedYAxisRangeProvider(
    private val minYLimit: Double,
    private val maxYLimit: Double
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = minYLimit
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = maxYLimit
}

data class ChartPreviewData(
    val cgmDataPoints: List<CgmDataPoint>,
    val bolusEvents: List<BolusEvent> = emptyList(),
    val basalDataPoints: List<BasalDataPoint> = emptyList(),
    val carbEvents: List<CarbEvent> = emptyList(),
    val modeEvents: List<ModeEvent> = emptyList(),
    val currentTimeSeconds: Long? = null
)

private data class ChartBucket(
    val timestamp: Long,
    val value: Double?
)

private data class ChartSeries(
    val xValues: List<Long>,  // Timestamps in seconds
    val yValues: List<Double>  // Y-axis values (glucose or basal rate)
)

private const val BOLUS_MARKER_DIAMETER_DP = 12f
private const val BOLUS_MARKER_STROKE_DP = 2f
private const val BOLUS_LABEL_TEXT_SIZE_SP = 12f
private const val CARB_MARKER_SIZE_DP = 14f
private const val CARB_MARKER_CORNER_RADIUS_DP = 3f
private const val CARB_MARKER_STROKE_DP = 2f
private const val CARB_LABEL_TEXT_SIZE_SP = 12f
private const val BASAL_DISPLAY_RANGE = 60.0
private const val MIN_BASAL_RATE_UNITS_PER_HOUR = 3f

private data class BolusMarkerPoint(
    val timestamp: Long,  // X position (timestamp in seconds)
    val event: BolusEvent
)

private data class CarbMarkerPoint(
    val timestamp: Long,  // X position (timestamp in seconds)
    val event: CarbEvent
)

private data class BasalSeriesResult(
    val scheduled: ChartSeries?,
    val temp: ChartSeries?
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
            val parsed = dao.parse()
            val value = when (parsed) {
                is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                is CgmDataGxHistoryLog -> parsed.value.toFloat()
                else -> null
            }

            val pumpTimestamp = when (parsed) {
                is DexcomG6CGMHistoryLog -> parsed.timeStampSeconds
                is DexcomG7CGMHistoryLog -> parsed.egvTimestamp
                is CgmDataGxHistoryLog -> parsed.transmitterTimestamp
                else -> parsed.pumpTimeSec

            }

            if (value != null && value > 0) {
                val timestamp = Dates.fromJan12008EpochSecondsToDate(pumpTimestamp)
                CgmDataPoint(
                    timestamp = timestamp.epochSecond,
                    value = value
                )
            } else null
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}


private fun formatBolusUnits(units: Float): String {
    val pattern = if (units >= 1f) "%.1fU" else "%.2fU"
    return String.format(Locale.getDefault(), pattern, units)
}

private fun formatCarbGrams(grams: Int): String {
    return "${grams}g"
}

private fun formatBolusTooltip(units: Float, isAutomated: Boolean, timestamp: Long): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(timestamp * 1000))
    val typeStr = if (isAutomated) "Auto" else "Bolus"
    val pattern = if (units >= 1f) "%.1fU" else "%.2fU"
    val unitsStr = String.format(Locale.getDefault(), pattern, units)
    return "$unitsStr\n$typeStr @ $timeStr"
}

private fun formatCarbTooltip(grams: Int, timestamp: Long): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(timestamp * 1000))
    return "${grams}g\n@ $timeStr"
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

private fun createCarbMarker(
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
        indicatorSizeDp = CARB_MARKER_SIZE_DP,
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
                    // Extract insulin units delivered (in 0.01U precision)
                    val units = parsed.deliveredTotal / 100f

                    // Determine if automated bolus
                    val bolusSource = parsed.bolusSource
                    val isAutomated = bolusSource == BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS

                    // Get bolus type as string representation
                    val bolusType = parsed.bolusTypes.toString()

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
    // Use BasalRateChangeHistoryLog and TempRateActivatedHistoryLog
    val basalClasses = listOf(
        com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog::class.java,
        com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog::class.java
    )

    val basalHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        basalClasses,
        // Basal changes: ~1-4 per hour for temp basals
        (timeRange.hours * 4) + 10
    )?.observeAsState()

    return remember(basalHistoryLogs?.value, timeRange) {
        basalHistoryLogs?.value?.mapNotNull { dao: com.jwoglom.controlx2.db.historylog.HistoryLogItem ->
            try {
                val parsed = dao.parse()
                val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

                when (parsed) {
                    is com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog -> {
                        // Extract basal rate (already in units/hr)
                        val rate = parsed.commandBasalRate
                        
                        // Determine if temp basal based on change type ID
                        // TEMP_RATE_START = 4, TEMP_RATE_END = 8
                        val changeTypeId = parsed.changeTypeId
                        val isTemp = changeTypeId == 4  // TEMP_RATE_START

                        if (rate > 0) {
                            BasalDataPoint(
                                timestamp = timestamp,
                                rate = rate,
                                isTemp = isTemp,
                                duration = null  // Duration not available in BasalRateChangeHistoryLog
                            )
                        } else null
                    }
                    is com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog -> {
                        // TempRateActivatedHistoryLog provides percent and duration
                        // We need the base rate to calculate actual rate, which we don't have here
                        // For now, we'll just mark this as a temp basal event
                        val duration = parsed.duration.toInt()  // Already in minutes

                        // Note: We can't determine the actual rate without the base basal rate
                        // This would require looking up the corresponding BasalRateChangeHistoryLog
                        // For now, skip these or use a placeholder
                        null  // Skip TempRateActivatedHistoryLog for now since we need base rate
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()  // Reverse to get chronological order
    }
}

// Helper function to fetch and convert Carb data
@Composable
private fun rememberCarbData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<CarbEvent> {
    // Carbs are recorded in CarbEnteredHistoryLog
    val carbHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog::class.java),
        (timeRange.hours * 2) + 10
    )?.observeAsState()

    return remember(carbHistoryLogs?.value, timeRange) {
        carbHistoryLogs?.value?.mapNotNull { dao ->
            try {
                val parsed = dao.parse()
                if (parsed is com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog) {
                    val carbs = parsed.carbs.toInt()

                    if (carbs > 0) {
                        val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                        CarbEvent(
                            timestamp = timestamp,
                            grams = carbs,
                            note = null
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()
    }
}

// Helper function to fetch and convert Mode data (Sleep/Exercise)
@Composable
private fun rememberModeData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange
): List<ModeEvent> {
    // Use ControlIQUserModeChangeHistoryLog to track mode changes
    val modeClasses = listOf(
        com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog::class.java
    )

    val modeHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        modeClasses,
        (timeRange.hours * 2) + 10
    )?.observeAsState()

    return remember(modeHistoryLogs?.value, timeRange) {
        modeHistoryLogs?.value?.mapNotNull { dao: com.jwoglom.controlx2.db.historylog.HistoryLogItem ->
            try {
                val parsed = dao.parse()
                if (parsed is com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog) {
                    // Get the current user mode using the typed accessor
                    val userModeId = parsed.currentUserMode
                    val userModeType = com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse.UserModeType.fromId(userModeId)
                    
                    // Map UserModeType to TherapyMode
                    val mode = when (userModeType) {
                        com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse.UserModeType.SLEEP -> TherapyMode.SLEEP
                        com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> TherapyMode.EXERCISE
                        else -> TherapyMode.STANDARD
                    }

                    val timestamp = dao.pumpTime.atZone(ZoneId.systemDefault()).toEpochSecond()

                    // Only return mode events for Sleep/Exercise activations
                    // We don't have duration data in this history log, so use null
                    if (mode == TherapyMode.SLEEP || mode == TherapyMode.EXERCISE) {
                        ModeEvent(
                            timestamp = timestamp,
                            mode = mode,
                            duration = null  // Duration not available in ControlIQUserModeChangeHistoryLog
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()
    }
}

// Calculate Time-in-Range percentage from CGM data
fun calculateTimeInRange(
    cgmDataPoints: List<CgmDataPoint>,
    lowTarget: Float = 70f,
    highTarget: Float = 180f
): Float? {
    if (cgmDataPoints.isEmpty()) return null

    val validReadings = cgmDataPoints.filter { it.value > 0 }
    if (validReadings.isEmpty()) return null

    val inRange = validReadings.count { it.value >= lowTarget && it.value <= highTarget }
    return (inRange.toFloat() / validReadings.size.toFloat()) * 100f
}

// Calculate Carbs-on-Board using exponential decay model
// Default absorption time is 3 hours (180 minutes)
fun calculateCarbsOnBoard(
    carbEvents: List<CarbEvent>,
    currentTimeSeconds: Long,
    absorptionTimeMinutes: Int = 180
): Float? {
    if (carbEvents.isEmpty()) return null

    val absorptionTimeSeconds = absorptionTimeMinutes * 60L
    val tau = absorptionTimeSeconds / 3.0 // Time constant for exponential decay

    var totalCob = 0f
    carbEvents.forEach { carb ->
        val elapsedSeconds = currentTimeSeconds - carb.timestamp
        if (elapsedSeconds >= 0 && elapsedSeconds < absorptionTimeSeconds * 2) {
            // Exponential decay: COB = carbs * e^(-t/tau)
            val remaining = carb.grams * kotlin.math.exp(-elapsedSeconds / tau)
            totalCob += remaining.toFloat()
        }
    }

    return if (totalCob > 0.5f) totalCob else null
}

private fun buildBasalSeries(
    bucketTimes: List<Long>,
    basalDataPoints: List<BasalDataPoint>
): BasalSeriesResult {
    if (bucketTimes.isEmpty() || basalDataPoints.isEmpty()) {
        return BasalSeriesResult(null, null)
    }

    // Build separate series for scheduled and temp basal with explicit X,Y coordinates
    val basalMaxRate = max(MIN_BASAL_RATE_UNITS_PER_HOUR, basalDataPoints.maxOfOrNull { it.rate } ?: MIN_BASAL_RATE_UNITS_PER_HOUR)

    val scheduledX = mutableListOf<Long>()
    val scheduledY = mutableListOf<Double>()
    val tempX = mutableListOf<Long>()
    val tempY = mutableListOf<Double>()

    bucketTimes.forEach { bucketTime ->
        val relevantBasal = basalDataPoints
            .filter { it.timestamp <= bucketTime }
            .maxByOrNull { it.timestamp }

        if (relevantBasal != null) {
            val normalized = (relevantBasal.rate / basalMaxRate) * BASAL_DISPLAY_RANGE
            if (relevantBasal.isTemp) {
                tempX.add(bucketTime)
                tempY.add(normalized)
            } else {
                scheduledX.add(bucketTime)
                scheduledY.add(normalized)
            }
        }
    }

    return BasalSeriesResult(
        scheduled = if (scheduledX.isNotEmpty()) ChartSeries(scheduledX, scheduledY) else null,
        temp = if (tempX.isNotEmpty()) ChartSeries(tempX, tempY) else null
    )
}


@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier,
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    previewData: ChartPreviewData? = null
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)

    // Fetch all chart data
    val cgmDataPoints = previewData?.cgmDataPoints ?: rememberCgmChartData(historyLogViewModel, timeRange)
    val bolusEvents = previewData?.bolusEvents ?: rememberBolusData(historyLogViewModel, timeRange)
    val basalDataPoints = previewData?.basalDataPoints ?: rememberBasalData(historyLogViewModel, timeRange)
    val carbEvents = previewData?.carbEvents ?: rememberCarbData(historyLogViewModel, timeRange)
    val modeEvents = previewData?.modeEvents ?: rememberModeData(historyLogViewModel, timeRange)

    val currentTimeSeconds = remember(timeRange) {
        previewData?.currentTimeSeconds ?: Instant.now().epochSecond
    }
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
    // Each segment contains explicit X (timestamp) and Y (glucose) values
    // Using separate X,Y lists allows proper positioning on time axis
    val cgmSegments = remember(chartBuckets, bucketTimes) {
        val segments = mutableListOf<ChartSeries>()
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
                    // Create series with explicit X (timestamps) and Y (values) for this segment
                    val xValues = mutableListOf<Long>()
                    val yValues = mutableListOf<Double>()

                    (currentSegmentStart until index).forEach { idx ->
                        xValues.add(bucketTimes[idx])
                        yValues.add(chartBuckets[idx].value ?: Double.NaN)
                    }

                    if (xValues.isNotEmpty()) {
                        segments.add(ChartSeries(xValues, yValues))
                    }
                    currentSegmentStart = null
                }
            }
        }

        // Close any remaining open segment
        if (currentSegmentStart != null) {
            val xValues = mutableListOf<Long>()
            val yValues = mutableListOf<Double>()

            (currentSegmentStart until chartBuckets.size).forEach { idx ->
                xValues.add(bucketTimes[idx])
                yValues.add(chartBuckets[idx].value ?: Double.NaN)
            }

            if (xValues.isNotEmpty()) {
                segments.add(ChartSeries(xValues, yValues))
            }
        }

        segments
    }

    val hasValidCgmData = cgmSegments.isNotEmpty() && cgmSegments.any { segment ->
        segment.yValues.any { !it.isNaN() }
    }
    val basalSeriesResult = remember(bucketTimes, basalDataPoints) {
        buildBasalSeries(bucketTimes, basalDataPoints)
    }
    val hasScheduledBasalSeries = basalSeriesResult.scheduled != null
    val hasTempBasalSeries = basalSeriesResult.temp != null

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

    // Carb marker components (orange rounded square)
    val carbRoundedSquareShape = remember { RoundedCornerShape(CARB_MARKER_CORNER_RADIUS_DP.dp).toVicoShape() }
    val carbLabelComponent = remember(bolusLabelColor) {
        TextComponent(
            color = bolusLabelColor.toArgb(),
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            textSizeSp = CARB_LABEL_TEXT_SIZE_SP,
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
    val carbIndicatorComponent = rememberShapeComponent(
        fill(CarbColor),
        carbRoundedSquareShape,
        Insets(),
        fill(ComposeColor.White),
        CARB_MARKER_STROKE_DP.dp,
        null
    )

    // Create model producer for chart data
    val modelProducer = remember { CartesianChartModelProducer() }

    val axisTimeFormatter = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    val markerValueFormatter = remember(glucoseUnit) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
            val entry = lineTarget?.points?.firstOrNull()?.entry ?: return@ValueFormatter ""
            // Handle NaN values and invalid data safely
            if (entry.x.isNaN() || entry.y.isNaN()) {
                return@ValueFormatter ""
            }
            // entry.x is now a timestamp in seconds
            val timestamp = entry.x.toLong()
            val timeText = axisTimeFormatter.format(Date.from(Dates.fromJan12008EpochSecondsToDate(timestamp)))
            val glucoseValue = entry.y.roundToInt()
            val glucoseText = when (glucoseUnit) {
                GlucoseUnit.MGDL -> "$glucoseValue mg/dL"
                GlucoseUnit.MMOL -> "${String.format("%.1f", glucoseValue * GlucoseConverter.MGDL_TO_MMOL_FACTOR)} mmol/L"
            }
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
    // Use explicit X,Y coordinates for all series
    LaunchedEffect(cgmSegments, basalSeriesResult, hasValidCgmData) {
        if (hasValidCgmData && cgmSegments.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    // Add each CGM segment with explicit timestamps (X) and glucose values (Y)
                    // This allows proper positioning on the time axis
                    cgmSegments.forEach { segment ->
                        series(segment.xValues, segment.yValues)
                    }
                    // Add basal series with explicit timestamps and normalized rates
                    if (hasScheduledBasalSeries) {
                        basalSeriesResult.scheduled?.let { scheduled ->
                            series(scheduled.xValues, scheduled.yValues)
                        }
                    }
                    if (hasTempBasalSeries) {
                        basalSeriesResult.temp?.let { temp ->
                            series(temp.xValues, temp.yValues)
                        }
                    }
                }
            }
        }
    }

    if (cgmDataPoints.isEmpty() || !hasValidCgmData) {
        // Show placeholder when no data
        Text(
            text = "CGM data unavailable",
            modifier = modifier.fillMaxWidth().height(300.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    } else {
        val yAxisLabels = remember(fixedGlucoseRange) {
            //listOf("400", "360", "320", "280", "240", "200", "160", "120", "80", "40")
            listOf("400", " ", " ", "280", " ", "200", "160", "120", "80", "40")
        }

        // Create persistent markers for bolus events
        // Use actual timestamps as X positions for markers
        val bolusMarkerPoints = remember(bolusEvents, startTimeSeconds, currentTimeSeconds, hasValidCgmData) {
            if (bolusEvents.isEmpty() || !hasValidCgmData) {
                emptyList<BolusMarkerPoint>()
            } else {
                bolusEvents.mapNotNull { bolus ->
                    // Only include boluses within the time range
                    if (bolus.timestamp in startTimeSeconds..currentTimeSeconds) {
                        BolusMarkerPoint(
                            timestamp = bolus.timestamp,
                            event = bolus
                        )
                    } else {
                        null
                    }
                }
            }
        }
        
        // Enhanced label component for detailed tooltips (multi-line)
        val detailedBolusLabelComponent = remember(bolusLabelColor) {
            TextComponent(
                color = bolusLabelColor.toArgb(),
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
                textSizeSp = 10f,  // Slightly smaller for multi-line
                textAlignment = Layout.Alignment.ALIGN_CENTER,
                lineHeightSp = null,
                lineCount = 2,  // Allow 2 lines
                truncateAt = null,
                margins = Insets(0f, 0f, 0f, 4f),
                padding = Insets(6f, 2f, 6f, 2f),
                background = null,
                minWidth = TextComponent.MinWidth.Companion.fixed(0f)
            )
        }

        val detailedCarbLabelComponent = remember(bolusLabelColor) {
            TextComponent(
                color = bolusLabelColor.toArgb(),
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
                textSizeSp = 10f,
                textAlignment = Layout.Alignment.ALIGN_CENTER,
                lineHeightSp = null,
                lineCount = 2,
                truncateAt = null,
                margins = Insets(0f, 0f, 0f, 4f),
                padding = Insets(6f, 2f, 6f, 2f),
                background = null,
                minWidth = TextComponent.MinWidth.Companion.fixed(0f)
            )
        }

        val bolusMarkers = remember(
            bolusMarkerPoints,
            detailedBolusLabelComponent,
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
                    labelComponent = detailedBolusLabelComponent,
                    indicatorComponent = indicator,
                    labelText = formatBolusTooltip(
                        markerPoint.event.units,
                        markerPoint.event.isAutomated,
                        markerPoint.event.timestamp
                    )
                )
                markerPoint.timestamp.toFloat() to marker
            }
        }

        // Create carb marker points
        val carbMarkerPoints = remember(carbEvents, startTimeSeconds, currentTimeSeconds, hasValidCgmData) {
            if (carbEvents.isEmpty() || !hasValidCgmData) {
                emptyList<CarbMarkerPoint>()
            } else {
                carbEvents.mapNotNull { carb ->
                    if (carb.timestamp in startTimeSeconds..currentTimeSeconds) {
                        CarbMarkerPoint(
                            timestamp = carb.timestamp,
                            event = carb
                        )
                    } else {
                        null
                    }
                }
            }
        }

        val carbMarkers = remember(
            carbMarkerPoints,
            detailedCarbLabelComponent,
            carbIndicatorComponent
        ) {
            carbMarkerPoints.map { markerPoint ->
                val marker = createCarbMarker(
                    labelComponent = detailedCarbLabelComponent,
                    indicatorComponent = carbIndicatorComponent,
                    labelText = formatCarbTooltip(markerPoint.event.grams, markerPoint.event.timestamp)
                )
                markerPoint.timestamp.toFloat() to marker
            }
        }

        val persistentMarkers = remember(bolusMarkers, carbMarkers) {
            if (bolusMarkers.isEmpty() && carbMarkers.isEmpty()) {
                null
            } else {
                { scope: PersistentMarkerScope, _: ExtraStore ->
                    bolusMarkers.forEach { (xValue, marker) ->
                        with(scope) {
                            marker.at(xValue)
                        }
                    }
                    carbMarkers.forEach { (xValue, marker) ->
                        with(scope) {
                            marker.at(xValue)
                        }
                    }
                }
            }
        }
        
        // Display the chart with Vico and mode bands overlay
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            // Mode bands overlay at top of chart
            if (modeEvents.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    val chartWidth = size.width
                    val bandHeight = size.height

                    modeEvents.forEach { modeEvent ->
                        // Calculate X position based on timestamp
                        val modeStartRatio = ((modeEvent.timestamp - startTimeSeconds).toFloat() / rangeSeconds).coerceIn(0f, 1f)
                        val startX = modeStartRatio * chartWidth

                        // Calculate end position (use duration or extend to current time)
                        val durationSeconds = (modeEvent.duration ?: 120) * 60L  // Default 2 hours if no duration
                        val modeEndTime = modeEvent.timestamp + durationSeconds
                        val modeEndRatio = ((modeEndTime - startTimeSeconds).toFloat() / rangeSeconds).coerceIn(0f, 1f)
                        val endX = modeEndRatio * chartWidth

                        // Only draw if within visible range
                        if (endX > 0 && startX < chartWidth) {
                            val bandColor = when (modeEvent.mode) {
                                TherapyMode.SLEEP -> ModeColors.SleepBand
                                TherapyMode.EXERCISE -> ModeColors.ExerciseBand
                                else -> ComposeColor.Transparent
                            }

                            drawRect(
                                color = bandColor,
                                topLeft = androidx.compose.ui.geometry.Offset(startX.coerceAtLeast(0f), 0f),
                                size = androidx.compose.ui.geometry.Size(
                                    width = (endX - startX).coerceAtLeast(8f),
                                    height = bandHeight
                                )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxSize()
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
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val lineRangeProvider = remember {
                    object : CartesianLayerRangeProvider {
                        override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
                        override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
                        override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = fixedMinGlucose.toDouble()
                        override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = fixedMaxGlucose.toDouble()
                    }
                }

                // Configure line styling for each series
                val lineLayer = rememberLineCartesianLayer(
                    rangeProvider = lineRangeProvider
                )
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
                        marker = dragMarker, // May be null if series contains NaN
                        persistentMarkers = persistentMarkers
                    ),
                    scrollState = scrollState,
                    consumeMoveEvents = true,
                    modelProducer = modelProducer,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
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

        // Mode legend if modes are present
        if (modeEvents.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.ExtraSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSleep = modeEvents.any { it.mode == TherapyMode.SLEEP }
                val hasExercise = modeEvents.any { it.mode == TherapyMode.EXERCISE }

                if (hasSleep) {
                    Surface(
                        modifier = Modifier.width(16.dp).height(8.dp),
                        color = ModeColors.SleepBand,
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                    Spacer(Modifier.width(Spacing.ExtraSmall))
                    Text(
                        "Sleep",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasSleep && hasExercise) {
                    Spacer(Modifier.width(Spacing.Medium))
                }
                if (hasExercise) {
                    Surface(
                        modifier = Modifier.width(16.dp).height(8.dp),
                        color = ModeColors.ExerciseBand,
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                    Spacer(Modifier.width(Spacing.ExtraSmall))
                    Text(
                        "Exercise",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Chart Legend Component
@Composable
fun ChartLegend(
    showCarbs: Boolean = true,
    showBasal: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = GlucoseColors.InRange,
            label = "Glucose",
            shape = LegendShape.LINE
        )
        LegendItem(
            color = InsulinColors.Bolus,
            label = "Bolus",
            shape = LegendShape.CIRCLE
        )
        if (showCarbs) {
            LegendItem(
                color = CarbColor,
                label = "Carbs",
                shape = LegendShape.SQUARE
            )
        }
        if (showBasal) {
            LegendItem(
                color = InsulinColors.Basal,
                label = "Basal",
                shape = LegendShape.LINE
            )
        }
    }
}

enum class LegendShape {
    LINE, CIRCLE, SQUARE
}

@Composable
private fun LegendItem(
    color: ComposeColor,
    label: String,
    shape: LegendShape,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        when (shape) {
            LegendShape.LINE -> {
                Surface(
                    modifier = Modifier
                        .width(16.dp)
                        .height(3.dp),
                    color = color,
                    shape = RoundedCornerShape(1.dp)
                ) {}
            }
            LegendShape.CIRCLE -> {
                Surface(
                    modifier = Modifier.width(10.dp).height(10.dp),
                    color = color,
                    shape = CircleShape
                ) {}
            }
            LegendShape.SQUARE -> {
                Surface(
                    modifier = Modifier.width(10.dp).height(10.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    previewData: ChartPreviewData? = null,
    showLegend: Boolean = true
) {
    var selectedTimeRange by remember { mutableStateOf(TimeRange.SIX_HOURS) }

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
            // Header with time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // Chart legend
            if (showLegend) {
                Spacer(Modifier.height(Spacing.Small))
                ChartLegend(
                    showCarbs = previewData?.carbEvents?.isNotEmpty() == true,
                    showBasal = previewData?.basalDataPoints?.isNotEmpty() == true
                )
            }
        }
    }
}

private fun createCgmPreviewData(values: List<Int>, baseTimestamp: Long = 1700000000L): List<CgmDataPoint> {
    return values.mapIndexed { index, mgdl ->
        CgmDataPoint(
            timestamp = baseTimestamp + (index * 300L),
            value = mgdl.toFloat()
        )
    }
}

private fun createBolusPreviewData(
    baseTimestamp: Long,
    entries: List<Triple<Int, Float, Boolean>>
): List<BolusEvent> {
    return entries.mapIndexed { idx, (fiveMinuteIndex, units, automated) ->
        BolusEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            units = units,
            isAutomated = automated,
            bolusType = if (automated) "AUTO" else "MANUAL-$idx"
        )
    }
}

private fun createCarbPreviewData(
    baseTimestamp: Long,
    entries: List<Pair<Int, Int>>  // Pair of (fiveMinuteIndex, grams)
): List<CarbEvent> {
    return entries.map { (fiveMinuteIndex, grams) ->
        CarbEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            grams = grams,
            note = null
        )
    }
}

private fun createModePreviewData(
    baseTimestamp: Long,
    entries: List<Triple<Int, TherapyMode, Int?>>  // Triple of (fiveMinuteIndex, mode, durationMinutes)
): List<ModeEvent> {
    return entries.map { (fiveMinuteIndex, mode, duration) ->
        ModeEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            mode = mode,
            duration = duration
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


@Preview(showBackground = true, name = "With Boluses, Basal, and Carbs")
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
            Triple(5, 5.2f, false),   // Manual bolus at 25 minutes
            Triple(10, 1.5f, true),   // Auto bolus at 50 minutes
            Triple(15, 2.0f, true),   // Auto bolus at 75 minutes
            Triple(30, 4.0f, false)   // Manual bolus at 150 minutes
        )
    )

    // Basal data - mix of scheduled and temp basals
    val basalDataPoints = listOf(
        BasalDataPoint(timestamp = baseTimestamp, rate = 1.0f, isTemp = false, duration = null),
        BasalDataPoint(timestamp = baseTimestamp + (15 * 300L), rate = 0.5f, isTemp = true, duration = 30),  // Temp basal at 75 min
        BasalDataPoint(timestamp = baseTimestamp + (21 * 300L), rate = 1.0f, isTemp = false, duration = null), // Back to scheduled at 105 min
        BasalDataPoint(timestamp = baseTimestamp + (28 * 300L), rate = 1.5f, isTemp = false, duration = null)  // Scheduled increase at 140 min
    )

    // Carb data - meals and snacks
    val carbEvents = createCarbPreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Pair(4, 45),    // 45g carbs at 20 minutes (breakfast)
            Pair(25, 30),   // 30g carbs at 125 minutes (snack)
            Pair(35, 60)    // 60g carbs at 175 minutes (lunch)
        )
    )

    // Mode events - Sleep and Exercise modes
    val modeEvents = createModePreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Triple(0, TherapyMode.SLEEP, 60),      // Sleep mode at start for 60 min
            Triple(20, TherapyMode.EXERCISE, 45)  // Exercise mode at 100 min for 45 min
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
                basalDataPoints = basalDataPoints,
                carbEvents = carbEvents,
                modeEvents = modeEvents,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}
