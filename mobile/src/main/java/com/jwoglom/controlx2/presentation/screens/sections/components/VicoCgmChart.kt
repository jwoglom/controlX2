package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.shape.Shape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CgmDataPoint(
    val timestamp: Long,
    val value: Float
)

@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer.build() }

    // Observe CGM data
    val cgmData = historyLogViewModel?.latestItemsForTypes(
        CgmReadingHistoryLogs,
        100
    )?.value

    // Convert to chart data and update model
    LaunchedEffect(cgmData) {
        if (cgmData != null && cgmData.isNotEmpty()) {
            val dataPoints = cgmData.mapNotNull { dao ->
                try {
                    val parsed = dao.parse()
                    val value = when (parsed) {
                        is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                        is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
                        else -> null
                    }
                    val timestamp = dao.pumpTimeSec.toFloat()

                    if (value != null && value > 0) {
                        timestamp to value
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (dataPoints.isNotEmpty()) {
                modelProducer.tryRunTransaction {
                    lineSeries {
                        series(dataPoints)
                    }
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lines = listOf(
                    rememberLineSpec(
                        lineColor = GlucoseColors.InRange,
                        lineThicknessDp = 2.5f,
                    )
                ),
            ),
            startAxis = rememberStartAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    padding = com.patrykandpatrick.vico.core.common.Dimensions(4f, 2f, 4f, 2f),
                    background = rememberShapeComponent(
                        fill(Color.Transparent),
                        Shape.Rectangle
                    )
                ),
                axis = rememberShapeComponent(
                    fill(GridLineColor),
                    Shape.Rectangle
                ),
                guideline = rememberShapeComponent(
                    fill(GridLineColor),
                    Shape.Rectangle
                ),
            ),
            bottomAxis = rememberBottomAxis(
                label = rememberTextComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    padding = com.patrykandpatrick.vico.core.common.Dimensions(2f, 4f, 2f, 4f),
                    background = rememberShapeComponent(
                        fill(Color.Transparent),
                        Shape.Rectangle
                    )
                ),
                axis = rememberShapeComponent(
                    fill(GridLineColor),
                    Shape.Rectangle
                ),
                guideline = rememberShapeComponent(
                    fill(GridLineColor),
                    Shape.Rectangle
                ),
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}

@Composable
fun VicoCgmChartCard(
    historyLogViewModel: HistoryLogViewModel?,
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
            Text(
                "Glucose History",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            VicoCgmChart(
                historyLogViewModel = historyLogViewModel,
                modifier = Modifier.padding(top = Spacing.Medium)
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
