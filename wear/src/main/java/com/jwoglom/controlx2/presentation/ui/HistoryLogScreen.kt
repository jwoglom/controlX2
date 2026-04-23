package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.WearPrefs
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
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
 * Reads the `pumpdata_historylog` Room table via [HistoryLogRepo.getAll] and
 * renders the latest rows (capped at [MAX_ROWS]) as a scrolling list of
 * formatted one-liners.
 *
 * CGM-reading rows and raw BG readings are filtered out client-side so the
 * event log isn't drowned by 5-minute CGM samples — those have dedicated
 * surfaces (complications and, in 5e-3, the trend chart).
 *
 * CLIENT-mode watches don't receive raw history-log cargo, so this screen is
 * gated from [SettingsHubScreen] by [DeviceRole].
 */
@Composable
fun HistoryLogScreen() {
    val context = LocalContext.current
    val pumpSid = remember { WearPrefs(context).currentPumpSid() }

    if (pumpSid < 0) {
        FullScreenText("No pump connected yet.\nHistory will appear after the first pump connection.")
        return
    }

    val repo = remember(context) {
        HistoryLogRepo(HistoryLogDatabase.getDatabase(context).historyLogDao())
    }
    val viewModel: HistoryLogViewModel = viewModel(
        factory = HistoryLogViewModelFactory(repo, pumpSid),
    )

    val allHistoryItems by viewModel.all.observeAsState(emptyList())

    val displayItems = remember(allHistoryItems) {
        allHistoryItems.asReversed()
            .filter { !isHighVolumeType(it.typeId) }
            .take(MAX_ROWS)
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        state = listState,
        autoCentering = AutoCenteringParams(),
    ) {
        if (displayItems.isEmpty()) {
            item {
                Text(
                    text = "No recent history events.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            // All visible rows share the same pumpSid, so seqId alone is unique.
            items(displayItems, key = { it.seqId }) { item ->
                HistoryLogChip(item)
            }
        }
    }
}

@Composable
private fun HistoryLogChip(item: HistoryLogItem) {
    val label = remember(item.seqId, item.pumpSid) { formatHistoryLogLabel(item) }
    val timeLabel = remember(item.seqId, item.pumpSid) { formatHistoryLogTime(item) }

    Chip(
        onClick = { /* detail view: future iteration */ },
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

private const val MAX_ROWS = 100

// CGM-reading rows arrive every ~5 minutes, so filtering them keeps the event
// log usable on a small screen. Resolved once via pumpx2's class→id map rather
// than hardcoded — stable across pumpx2 versions.
private val HIGH_VOLUME_TYPE_IDS: Set<Int> = listOf(
    DexcomG6CGMHistoryLog::class.java,
    CgmDataGxHistoryLog::class.java,
    CgmDataFsl2HistoryLog::class.java,
    CgmDataFsl3HistoryLog::class.java,
).mapNotNull { clazz ->
    HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz as Class<out HistoryLog>]
}.toSet()

private fun isHighVolumeType(typeId: Int): Boolean =
    typeId in HIGH_VOLUME_TYPE_IDS

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
        is CarbEnteredHistoryLog -> "Carbs ${parsed.carbs.toInt()}g"
        is AlarmActivatedHistoryLog -> "Alarm: ID ${parsed.alarmId}"
        is AlarmClearedHistoryLog -> "Alarm cleared: ID ${parsed.alarmId}"
        is AlertActivatedHistoryLog -> "Alert activated"
        is AlertClearedHistoryLog -> "Alert cleared"
        is DailyBasalHistoryLog -> "Daily basal summary"
        is PumpingResumedHistoryLog -> "Pumping resumed"
        is PumpingSuspendedHistoryLog -> "Pumping suspended"
        is CannulaFilledHistoryLog -> "Cannula filled"
        is TubingFilledHistoryLog -> "Tubing filled"
        is CartridgeFilledHistoryLog -> "Cartridge filled"
        else -> parsed.javaClass.simpleName.removeSuffix("HistoryLog")
    }
}
