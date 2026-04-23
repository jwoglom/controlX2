package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.LocalHistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBasalStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import java.time.format.DateTimeFormatter

/**
 * PUMP_HOST-only dedicated basal view.
 *
 * Header renders the live [com.jwoglom.controlx2.presentation.DataStore.basalRate]
 * and [com.jwoglom.controlx2.presentation.DataStore.basalStatus] (same fields
 * `LandingBasalRow` uses). Entry on the screen fires a [CurrentBasalStatusRequest]
 * so direct navigation here from `SettingsHub` doesn't render stale data the
 * user's pump has since changed; thereafter an [intervalOf] 60s tick keeps
 * it refreshed while the screen is visible.
 *
 * Below the header, a 20-row list of recent basal-related history events from
 * [HistoryLogViewModel.latestItemsForTypes]. Event types: rate changes, temp-
 * basal start/end, and pump suspend/resume.
 */
@Composable
fun BasalDetailScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val ds = LocalDataStore.current
    val basalRate by ds.basalRate.observeAsState()
    val basalStatus by ds.basalStatus.observeAsState()
    val pumpSid by ds.currentPumpSid.observeAsState(-1)

    // Fire the basal request on enter so direct navigation here doesn't show
    // stale data, and again each minute while the screen is in composition.
    val tick = intervalOf(60)
    LaunchedEffect(tick) {
        sendPumpCommands(SendType.STANDARD, listOf(CurrentBasalStatusRequest()))
    }

    // Always construct the VM, even when pumpSid is `-1`. Key by pumpSid so
    // the VM recreates when a pump pairs (viewModel() caches by key, not by
    // factory identity — without keying, the VM would stay cached with the
    // stale sid). The `coerceAtLeast(0)` is defensive: Room returns no rows
    // for pumpSid=0, and the UI gates on pumpSid < 0 anyway.
    val repo = LocalHistoryLogRepo.current
    val viewModel: HistoryLogViewModel = viewModel(
        key = "basal-detail-pump-$pumpSid",
        factory = HistoryLogViewModelFactory(repo, pumpSid.coerceAtLeast(0)),
    )
    val recentItemsLive = remember(viewModel) {
        viewModel.latestItemsForTypes(BASAL_HISTORY_TYPE_IDS, MAX_RECENT_ROWS)
    }
    val recentItems by recentItemsLive.observeAsState(emptyList())

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollableColumn(focusRequester, scalingLazyListState)
            .padding(horizontal = 8.dp),
        state = scalingLazyListState,
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
                recentItems.forEach { row ->
                    item {
                        BasalHistoryRow(row)
                    }
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
    Chip(
        onClick = {},
        label = {
            Text(
                text = formatBasalRowLabel(item),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = formatBasalRowTime(item),
                fontSize = 10.sp,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_RECENT_ROWS = 20

// Resolve basal-event classes to typeIds at class init, fail-loud if pumpx2
// ever drops one of these registrations. Matches `HistoryLogScreen.kt`'s
// CGM filter pattern.
private val BASAL_HISTORY_TYPE_IDS: Array<Int> = listOf(
    BasalRateChangeHistoryLog::class.java,
    TempRateActivatedHistoryLog::class.java,
    TempRateCompletedHistoryLog::class.java,
    PumpingSuspendedHistoryLog::class.java,
    PumpingResumedHistoryLog::class.java,
).map { clazz ->
    requireNotNull(HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz as Class<out HistoryLog>]) {
        "pumpx2 HistoryLogParser has no typeId for ${clazz.simpleName}"
    }
}.toTypedArray()

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
