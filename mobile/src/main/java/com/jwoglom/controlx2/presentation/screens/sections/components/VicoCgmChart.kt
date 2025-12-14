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
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
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
    val basalClasses = try {
        listOfNotNull(
            tryLoadClass("com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog"),
            tryLoadClass("com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateStartedHistoryLog"),
            tryLoadClass("com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalActivatedHistoryLog")
        )
    } catch (e: Exception) {
        emptyList()
    }

    val basalHistoryLogs = if (basalClasses.isNotEmpty()) {
        historyLogViewModel?.latestItemsForTypes(
            basalClasses as List<Class<out com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog>>,
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

// Helper to load class by name
private fun tryLoadClass(className: String): Class<*>? {
    return try {
        Class.forName(className)
    } catch (e: ClassNotFoundException) {
        null
    }
}

@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    modifier: Modifier = Modifier
) {
    // Fetch all chart data
    val cgmDataPoints = rememberCgmChartData(historyLogViewModel, timeRange)
    val bolusEvents = rememberBolusData(historyLogViewModel, timeRange)
    val basalDataPoints = rememberBasalData(historyLogViewModel, timeRange)

    // Create model producer for chart data
    val modelProducer = remember { CartesianChartModelProducer() }

    // Update chart data when CGM data changes
    LaunchedEffect(cgmDataPoints) {
        if (cgmDataPoints.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    // Use glucose values as Y data
                    series(cgmDataPoints.map { it.value.toDouble() })
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
        // Display the chart with Vico
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth().height(300.dp)
        )
    }

    // TODO: Add insulin visualizations (Phase 4)
    // - Bolus markers (purple circles with labels)
    // - Basal rate line (bottom 20% of chart)
    // - Target range shading
    // - Glucose line color coding based on ranges
    // Data is ready: ${bolusEvents.size} boluses, ${basalDataPoints.size} basal points
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

// Preview helper function to create Bolus entries
// TODO: Fix constructor - BolusDeliveryHistoryLog API has changed
// Constructor signature and enums need to be verified
/*
private fun createBolusEntry(
    index: Int,
    timestamp: Long,
    units: Float,
    isAutomated: Boolean = false,
    baseTimestamp: Long = 1700000000L
): BolusDeliveryHistoryLog {
    val actualTimestamp = timestamp
    return BolusDeliveryHistoryLog(
        actualTimestamp,
        (index + 1000).toLong(),  // Unique sequence ID
        (units * 100).toInt(),  // Total volume in 0.01U
        0,  // Extended volume
        BolusDeliveryHistoryLog.BolusType.FOOD,
        if (isAutomated)
            BolusDeliveryHistoryLog.BolusSource.CLOSED_LOOP_AUTO_BOLUS
        else
            BolusDeliveryHistoryLog.BolusSource.MANUAL
    )
}
*/

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

// TODO: Re-enable once createBolusEntry is fixed
/*
@Preview(showBackground = true, name = "With Boluses")
@Composable
internal fun VicoCgmChartCardWithBolusPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
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
                createBolusEntry(0, baseTimestamp + (5 * 300L), 5.2f, false, baseTimestamp),
                // Auto bolus at index 10 (during spike)
                createBolusEntry(1, baseTimestamp + (10 * 300L), 1.5f, true, baseTimestamp),
                // Auto bolus at index 15 (peak)
                createBolusEntry(2, baseTimestamp + (15 * 300L), 2.0f, true, baseTimestamp),
                // Manual bolus at index 30 (separate meal)
                createBolusEntry(3, baseTimestamp + (30 * 300L), 4.0f, false, baseTimestamp)
            ).map { com.jwoglom.controlx2.db.historylog.HistoryLogItem(it) }

            val allData = (cgmData + bolusData).toMutableList()

            VicoCgmChartCard(
                historyLogViewModel = com.jwoglom.controlx2.db.historylog.HistoryLogViewModel(
                    com.jwoglom.controlx2.db.historylog.HistoryLogRepo(
                        com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao(allData)
                    ), 0
                )
            )
        }
    }
}
*/
