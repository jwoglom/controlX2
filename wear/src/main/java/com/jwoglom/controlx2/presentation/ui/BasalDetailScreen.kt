package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import java.time.format.DateTimeFormatter

/**
 * PUMP_HOST-only dedicated basal view.
 *
 * Top of the screen reads the live [com.jwoglom.controlx2.presentation.DataStore.basalRate]
 * and [com.jwoglom.controlx2.presentation.DataStore.basalStatus] (same fields
 * `LandingBasalRow` uses). Below the header, a 20-row list of recent basal-
 * related history events from `HistoryLogRepo`: rate changes, temp-basal
 * start/end, and pump suspend/resume.
 *
 * The header renders in both roles if the pump is connected (the DataStore
 * fields populate in CLIENT mode too, forwarded from the phone), but the
 * history list requires local `HistoryLogRepo` rows — which only exist on
 * PUMP_HOST watches. Entry point in `SettingsHubScreen` is gated accordingly.
 */
@Composable
fun BasalDetailScreen() {
    val ds = LocalDataStore.current
    val basalRate by ds.basalRate.observeAsState()
    val basalStatus by ds.basalStatus.observeAsState()

    val context = LocalContext.current
    val pumpSid = remember { WearPrefs(context).currentPumpSid() }

    val recentItems: List<HistoryLogItem> = if (pumpSid >= 0) {
        val repo = remember(context) {
            HistoryLogRepo(HistoryLogDatabase.getDatabase(context).historyLogDao())
        }
        val vm: HistoryLogViewModel = viewModel(
            key = "basal-history-$pumpSid",
            factory = HistoryLogViewModelFactory(repo, pumpSid),
        )
        vm.latestItemsForTypes(BASAL_HISTORY_TYPES, MAX_RECENT_ROWS)
            .observeAsState(emptyList()).value
    } else {
        emptyList()
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        state = listState,
        autoCentering = AutoCenteringParams(),
    ) {
        item {
            BasalCurrentHeader(
                basalRate = basalRate,
                basalStatus = basalStatus,
            )
        }

        when {
            pumpSid < 0 -> item {
                Text(
                    text = "History will appear after the first pump connection.",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            recentItems.isEmpty() -> item {
                Text(
                    text = "No recent basal changes.",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            else -> {
                item {
                    Text(
                        text = "Recent changes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(recentItems, key = { it.seqId }) { item ->
                    BasalHistoryRow(item)
                }
            }
        }
    }
}

@Composable
private fun BasalCurrentHeader(
    basalRate: String?,
    basalStatus: BasalStatus?,
) {
    val rateText = basalRate ?: "—"
    val statusText = basalStatus?.str ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = rateText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BasalHistoryRow(item: HistoryLogItem) {
    val label = remember(item.seqId) { formatBasalRowLabel(item) }
    val timeLabel = remember(item.seqId) { formatBasalRowTime(item) }

    Chip(
        onClick = { /* detail view is future iteration */ },
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = timeLabel,
                fontSize = 10.sp,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_RECENT_ROWS = 20

private val BASAL_HISTORY_TYPES: List<Class<out HistoryLog>> = listOf(
    BasalRateChangeHistoryLog::class.java,
    TempRateActivatedHistoryLog::class.java,
    TempRateCompletedHistoryLog::class.java,
    PumpingSuspendedHistoryLog::class.java,
    PumpingResumedHistoryLog::class.java,
)

private val basalRowTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm")

private fun formatBasalRowTime(item: HistoryLogItem): String =
    try {
        item.pumpTimeLocal().format(basalRowTimeFormatter)
    } catch (_: Exception) {
        "—"
    }

private fun formatBasalRowLabel(item: HistoryLogItem): String {
    val parsed = try {
        item.parse()
    } catch (_: Exception) {
        return "Raw event #${item.typeId}"
    }
    return when (parsed) {
        is BasalRateChangeHistoryLog ->
            "→ %.3fU/hr".format(parsed.commandBasalRate.toDouble())
        is TempRateActivatedHistoryLog -> "Temp basal start"
        is TempRateCompletedHistoryLog -> "Temp basal end"
        is PumpingSuspendedHistoryLog -> "Pump suspended"
        is PumpingResumedHistoryLog -> "Pump resumed"
        else -> parsed.javaClass.simpleName.removeSuffix("HistoryLog")
    }
}
