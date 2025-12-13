package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color

val CgmReadingHistoryLogs = listOf(
    DexcomG6CGMHistoryLog::class.java,
    com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog::class.java
)

@Composable
fun DashboardCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier
) {
    VicoCgmChart(
        historyLogViewModel = historyLogViewModel,
        timeRange = TimeRange.SIX_HOURS,
        modifier = modifier.fillMaxWidth()
    )
}

private fun cgmEntry(index: Int, mgdl: Int): HistoryLog {
    return DexcomG6CGMHistoryLog(
            446191545L + index, index.toLong(),
            0, 1, -2, 6, -89,
            mgdl,
            446191545L + index,
            481, 0
        )
}

@Preview(showBackground = true)
@Composable
internal fun DashboardCgmChartDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceBackground,
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