package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.FixedHeightContainer
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID
import io.github.dautovicharis.charts.LineChart
import io.github.dautovicharis.charts.model.toMultiChartDataSet
import io.github.dautovicharis.charts.style.ChartViewDefaults
import io.github.dautovicharis.charts.style.LineChartDefaults

@Composable
fun DashboardCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    width: Dp = 400.dp,
    height: Dp = 300.dp
) {
    val cgmData = historyLogViewModel?.latestItemsForType(
        CGMHistoryLog::class.java,
        100
    )?.observeAsState()

    val cgmValues = (cgmData?.value?.map { (it.parse() as CGMHistoryLog).currentGlucoseDisplayValue.toFloat() } ?: listOf()).asReversed()
    val dataSet = listOf(
        "CGM" to cgmValues,
        "High target" to (1..cgmValues.size).map { 200f },
        "Low target" to (1..cgmValues.size).map { 80f },
    ).toMultiChartDataSet(
        title = "",
    )

    if (cgmValues.isEmpty()) {
        Line("No CGM values found in HistoryLog")
        return
    }

    Box(Modifier.height(height)) {
        FixedHeightContainer(height) {
            LineChart(
                dataSet,
                style = LineChartDefaults.style(
                    chartViewStyle = ChartViewDefaults.style(
                        outerPadding = 10.dp,
                        innerPadding = 10.dp,
                        cornerRadius = 0.dp,
                        shadow = 0.dp,
                        width = width,
                        backgroundColor = Color.Transparent
                    ),
                    pointVisible = false,
                    lineColors = listOf(Color.Black, Color(0xFF, 0xA5, 0x00), Color.Red)
                )
            )
        }
    }
}

private fun cgmEntry(index: Int, mgdl: Int): HistoryLog {
    return CGMHistoryLog(
            446191545L + index, index.toLong(),
            0, 1, -2, 6, -89,
            mgdl,
            446191545L + index,
            481, 0
        )
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            DashboardCgmChart(
                HistoryLogViewModel(HistoryLogRepo(HistoryLogDummyDao(
                        listOf(
                            160, 165, 170, 173, 179, 183, 188, 193, 199, 205, 215, 220, 225, 220, 215, 205, 199, 193, 188, 183, 179, 173, 170, 160, 145, 130, 125, 115, 108, 102, 97, 90, 85, 79, 73, 70, 68, 70, 75
                        )
                        .mapIndexed { index, mgdl -> cgmEntry(index, mgdl) }
                        .map { HistoryLogItem(it) }
                        .toMutableList()
                )), 0)
            )
        }
    }
}