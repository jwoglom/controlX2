package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.LocalHistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import java.time.format.DateTimeFormatter

/**
 * PUMP_HOST-only view of the watch's locally-persisted pump history log.
 *
 * Queries Room for the latest [MAX_ROWS] non-CGM rows via
 * [HistoryLogViewModel.latestItemsForTypes], so the DB does the filtering and
 * row cap — not a client-side `.filter().take()` over the whole table.
 *
 * CGM-reading rows are excluded here because they arrive every ~5 minutes and
 * would drown the event log; the dedicated trend chart (Phase 5e-3) will
 * surface them separately.
 */
@Composable
fun HistoryLogScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
) {
    val ds = LocalDataStore.current
    val pumpSid by ds.currentPumpSid.observeAsState(-1)
    val repo = LocalHistoryLogRepo.current

    // Always construct the VM, even when pumpSid is `-1` — Compose slot table
    // doesn't like conditional state calls, and keying by pumpSid makes the
    // VM recreate when a pump pairs so the new factory's sid actually takes
    // effect (viewModel() caches by key, not by factory identity).
    val viewModel: HistoryLogViewModel = viewModel(
        key = "history-log-pump-$pumpSid",
        factory = HistoryLogViewModelFactory(repo, pumpSid.coerceAtLeast(0)),
    )
    val itemsLive = remember(viewModel) {
        viewModel.latestItemsForTypes(NON_CGM_TYPE_IDS, MAX_ROWS)
    }
    val items by itemsLive.observeAsState(emptyList())

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollableColumn(focusRequester, scalingLazyListState)
            .padding(horizontal = 8.dp),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams(),
    ) {
        when {
            pumpSid < 0 -> item {
                Text(
                    text = "No pump connected yet. History will appear after the first pump connection.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            items.isEmpty() -> item {
                Text(
                    text = "No recent history events.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> items.forEach { row ->
                item {
                    HistoryLogChip(row)
                }
            }
        }
    }
}

@Composable
private fun HistoryLogChip(item: HistoryLogItem) {
    // No outer `remember` around the formatters: `HistoryLogItem.parse()` is
    // already LRU-cached (500 entries) in HistoryLogItem.kt, so the expensive
    // work is already memoized where it matters. A second cache per row in
    // the compose slot table is net-negative.
    Chip(
        onClick = {},
        label = {
            Text(
                text = formatHistoryLogLabel(item),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = formatHistoryLogTime(item),
                fontSize = 10.sp,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_ROWS = 100

// CGM-reading rows arrive every ~5 minutes, so filtering them server-side
// keeps the event list legible. Fail-loud on missing classes: if pumpx2
// renames one of these, we want a compile/runtime error rather than the
// filter silently letting the firehose through.
private val CGM_TYPE_IDS: Set<Int> = listOf(
    DexcomG6CGMHistoryLog::class.java,
    DexcomG7CGMHistoryLog::class.java,
    CgmDataGxHistoryLog::class.java,
    CgmDataFsl2HistoryLog::class.java,
    CgmDataFsl3HistoryLog::class.java,
).map { clazz ->
    requireNotNull(HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz as Class<out HistoryLog>]) {
        "pumpx2 HistoryLogParser has no typeId for ${clazz.simpleName}"
    }
}.toSet()

// Inclusion list for the DAO `IN(:typeIds)` query — the whole registered
// pumpx2 type universe minus the high-volume CGM rows. Resolved once at
// class init; fine on Wear because the map is ~30 entries.
private val NON_CGM_TYPE_IDS: Array<Int> =
    HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID.values
        .filter { it !in CGM_TYPE_IDS }
        .toTypedArray()

private val historyLogTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm")

private fun formatHistoryLogTime(item: HistoryLogItem): String =
    try {
        item.pumpTimeLocal().format(historyLogTimeFormatter)
    } catch (_: Exception) {
        "—"
    }

private fun formatHistoryLogLabel(item: HistoryLogItem): String {
    val parsed = try {
        item.parse()
    } catch (_: Exception) {
        return "Raw event #${item.typeId}"
    }
    return when (parsed) {
        is BolusDeliveryHistoryLog ->
            "Bolus %.2fU".format(parsed.deliveredTotal / 1000.0)
        is BolusCompletedHistoryLog -> "Bolus complete"
        is BasalRateChangeHistoryLog ->
            "Basal %.3fU/hr".format(parsed.commandBasalRate.toDouble())
        is TempRateActivatedHistoryLog -> "Temp basal start"
        is TempRateCompletedHistoryLog -> "Temp basal end"
        // `carbs` is a Float (pump reports 0.5g resolution). `.toInt()` would
        // silently drop half-grams, so format with one decimal.
        is CarbEnteredHistoryLog -> "Carbs %.1fg".format(parsed.carbs)
        // Prefer the enum name ("LOW_INSULIN", "OCCLUSION_DETECTED") — that's
        // what the user reads on the pump face — and fall back to the numeric
        // id when pumpx2 doesn't have a name for the id. Mirrors ProcessAlarm.
        is AlarmActivatedHistoryLog ->
            "Alarm: ${parsed.alarmResponseType?.name ?: "ID ${parsed.alarmId}"}"
        is AlarmClearedHistoryLog ->
            "Alarm cleared: ${parsed.alarmResponseType?.name ?: "ID ${parsed.alarmId}"}"
        is AlertActivatedHistoryLog ->
            "Alert: ${parsed.alertResponseType?.name ?: "ID ${parsed.alertId}"}"
        is AlertClearedHistoryLog ->
            "Alert cleared: ${parsed.alertResponseType?.name ?: "ID ${parsed.alertId}"}"
        is DailyBasalHistoryLog -> "Daily basal summary"
        is PumpingResumedHistoryLog -> "Pumping resumed"
        is PumpingSuspendedHistoryLog -> "Pumping suspended"
        is CannulaFilledHistoryLog -> "Cannula filled"
        is TubingFilledHistoryLog -> "Tubing filled"
        is CartridgeFilledHistoryLog -> "Cartridge filled"
        else -> parsed.javaClass.simpleName.removeSuffix("HistoryLog")
    }
}
