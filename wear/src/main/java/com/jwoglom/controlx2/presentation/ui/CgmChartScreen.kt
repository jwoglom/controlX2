package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.LocalHistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Watch CGM trend chart. PUMP_HOST-only since the chart reads from the
 * locally-persisted `HistoryLogRepo` which only carries data on watches
 * that are the pump-host.
 *
 * Renders a 6-hour CGM trace using `androidx.compose.foundation.Canvas`
 * — deliberately Canvas rather than Vico-compose. Vico would add
 * `vico-compose` + `vico-core` to `wear/build.gradle` (~1.2 MB to APK)
 * for what amounts to a single-line chart on a circular watch face.
 * Mobile's `VicoCgmChart.kt` is 2434 lines because it juggles bolus,
 * basal, threshold, and CGM layers with rich Vico configuration; this
 * watch port aims for under 350 lines covering just the CGM trace.
 *
 * Data flow (mirrors mobile `VicoCgmChart.toCgmDataPoint`):
 *   - Query `HistoryLogViewModel.latestItemsForTypes(cgmTypes, 200)`
 *     where `cgmTypes` is the same list mobile uses (G6, G7, Gx, FSL2,
 *     FSL3). 200 readings ≈ 16+ hours at the typical 5-minute sample
 *     rate, comfortably more than the 6-hour render window.
 *   - For each parsed item, extract glucose value via the same
 *     subclass dispatch mobile uses (`currentGlucoseDisplayValue` for
 *     G6/G7, `.value` for Gx/FSL).
 *   - Filter to readings within `WINDOW_HOURS` of the most recent one;
 *     the most recent reading anchors the right edge of the chart, so
 *     the watch shows continuous data even if it's been off-pump for
 *     a few hours.
 *   - Drop zero/negative readings (mobile does the same).
 */
private const val WINDOW_HOURS = 6L

private val CGM_TYPE_CLASSES: List<Class<out HistoryLog>> = listOf(
    DexcomG6CGMHistoryLog::class.java,
    DexcomG7CGMHistoryLog::class.java,
    CgmDataGxHistoryLog::class.java,
    CgmDataFsl2HistoryLog::class.java,
    CgmDataFsl3HistoryLog::class.java,
)

@Composable
fun CgmChartScreen() {
    val ds = LocalDataStore.current
    val repo = LocalHistoryLogRepo.current
    val pumpSidLive by ds.currentPumpSid.observeAsState(initial = -1)
    val glucoseUnit by ds.glucoseUnitPreference.observeAsState(initial = GlucoseUnit.MGDL)
    val pumpSid = pumpSidLive ?: -1

    if (pumpSid < 0 || repo == null) {
        EmptyMessage(text = "CGM history will appear after the first pump connection.")
        return
    }

    val viewModel: HistoryLogViewModel = viewModel(
        key = "cgm-chart-pump-$pumpSid",
        factory = HistoryLogViewModelFactory(repo, pumpSid),
    )
    val itemsLive = remember(viewModel) {
        viewModel.latestItemsForTypes(CGM_TYPE_CLASSES, 200)
    }
    val items by itemsLive.observeAsState(initial = emptyList())

    val unit = glucoseUnit ?: GlucoseUnit.MGDL
    val series = remember(items, unit) { buildSeries(items, unit) }

    if (series.points.isEmpty()) {
        EmptyMessage(text = "No CGM readings in the last ${WINDOW_HOURS}h.")
        return
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val mostRecent = series.points.last()
    val mostRecentLabel = mostRecent.value.toInt().toString() +
        " " + unit.abbreviation +
        " · " + timeFormatter.format(
            java.time.LocalDateTime.ofEpochSecond(
                mostRecent.epochSecond, 0, ZoneOffset.UTC,
            )
        )

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "CGM ${WINDOW_HOURS}h",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .weight(1f),
        ) {
            CgmChartCanvas(series = series, unit = unit)
        }
        Text(
            text = mostRecentLabel,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CgmChartCanvas(series: CgmSeries, unit: GlucoseUnit) {
    // Drop the rendering into a Canvas — fixed bounds derived from the
    // series. Threshold lines for the standard low (70 mg/dL) and high
    // (180 mg/dL) bands; values are in mg/dL space and converted via the
    // same factor as the series points.
    val lowThreshold = if (unit == GlucoseUnit.MGDL) 70f else 70f / 18f
    val highThreshold = if (unit == GlucoseUnit.MGDL) 180f else 180f / 18f
    val yMin = min(series.minValue, lowThreshold) - 10f.ofUnit(unit)
    val yMax = max(series.maxValue, highThreshold) + 10f.ofUnit(unit)
    val xStart = series.points.first().epochSecond
    val xEnd = series.points.last().epochSecond

    val lineColor = MaterialTheme.colors.primary
    val gridColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    val thresholdColor = MaterialTheme.colors.onSurface.copy(alpha = 0.30f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val xRange = (xEnd - xStart).coerceAtLeast(1L).toFloat()
        val yRange = (yMax - yMin).coerceAtLeast(0.001f)

        fun toPx(p: CgmPoint): Offset {
            val nx = (p.epochSecond - xStart).toFloat() / xRange
            val ny = 1f - (p.value - yMin) / yRange
            return Offset(nx * w, ny * h)
        }

        // Hour grid (vertical dashed lines at the start of each hour
        // boundary inside the window).
        val firstHour = ((xStart + 3600 - 1) / 3600) * 3600
        var hour = firstHour
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        while (hour <= xEnd) {
            val nx = (hour - xStart).toFloat() / xRange
            drawLine(
                color = gridColor,
                start = Offset(nx * w, 0f),
                end = Offset(nx * w, h),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            hour += 3600
        }

        // Low / high threshold lines.
        listOf(lowThreshold, highThreshold).forEach { ty ->
            val ny = 1f - (ty - yMin) / yRange
            drawLine(
                color = thresholdColor,
                start = Offset(0f, ny * h),
                end = Offset(w, ny * h),
                strokeWidth = 1f,
                pathEffect = dash,
            )
        }

        // CGM polyline.
        val path = Path()
        series.points.forEachIndexed { idx, p ->
            val px = toPx(p)
            if (idx == 0) path.moveTo(px.x, px.y) else path.lineTo(px.x, px.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5f),
        )

        // Endpoint dot for the current reading.
        val last = toPx(series.points.last())
        drawCircle(color = lineColor, radius = 3f, center = last)
    }
}

private fun Float.ofUnit(unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MGDL) this else this / 18f

private data class CgmPoint(val epochSecond: Long, val value: Float)

private data class CgmSeries(
    val points: List<CgmPoint>,
    val minValue: Float,
    val maxValue: Float,
)

private fun buildSeries(items: List<HistoryLogItem>, unit: GlucoseUnit): CgmSeries {
    if (items.isEmpty()) return CgmSeries(emptyList(), 0f, 0f)

    val raw = items.mapNotNull { item ->
        val parsed = item.parse()
        val mgdl = when (parsed) {
            is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
            is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
            is CgmDataGxHistoryLog -> parsed.value.toFloat()
            is CgmDataFsl2HistoryLog -> parsed.value.toFloat()
            is CgmDataFsl3HistoryLog -> parsed.value.toFloat()
            else -> null
        }
        if (mgdl == null || mgdl <= 0f) return@mapNotNull null
        val display = if (unit == GlucoseUnit.MGDL) mgdl else mgdl / 18f
        val epoch = item.pumpTimeLocal().toEpochSecond(ZoneOffset.UTC)
        CgmPoint(epoch, display)
    }.sortedBy { it.epochSecond }

    if (raw.isEmpty()) return CgmSeries(emptyList(), 0f, 0f)

    // Anchor window to the most recent reading rather than wall-clock so
    // a watch that's been disconnected for a few hours still shows
    // continuous data instead of a blank chart.
    val cutoff = raw.last().epochSecond - WINDOW_HOURS * 3600
    val windowed = raw.filter { it.epochSecond >= cutoff }
    if (windowed.isEmpty()) return CgmSeries(emptyList(), 0f, 0f)

    val values = windowed.map { it.value }
    return CgmSeries(
        points = windowed,
        minValue = values.min(),
        maxValue = values.max(),
    )
}
