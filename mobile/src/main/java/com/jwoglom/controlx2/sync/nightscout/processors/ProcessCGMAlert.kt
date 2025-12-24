package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedDexHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedDexHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process CGM alerts (high/low glucose) for Nightscout upload
 *
 * Converts CGM alert records to Nightscout "Announcement" treatments
 */
class ProcessCGMAlert(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CGM_ALERT

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertActivatedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertClearedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertActivatedDexHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertClearedDexHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertActivatedFsl2HistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CgmAlertClearedFsl2HistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                cgmAlertToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert CGM alert seqId=${item.seqId}")
                null
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun cgmAlertToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is CgmAlertActivatedHistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "CGM Alert: ${alert.name}"
                } else {
                    "CGM Alert (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "CGM alert activated, ID: ${parsed.alertId}"
                )
            }
            is CgmAlertClearedHistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "CGM Alert Cleared: ${alert.name}"
                } else {
                    "CGM Alert Cleared (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "CGM alert cleared, ID: ${parsed.alertId}"
                )
            }
            is CgmAlertActivatedDexHistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "Dexcom Alert: ${alert.name}"
                } else {
                    "Dexcom Alert (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Dexcom alert activated, ID: ${parsed.alertId}"
                )
            }
            is CgmAlertClearedDexHistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "Dexcom Alert Cleared: ${alert.name}"
                } else {
                    "Dexcom Alert Cleared (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Dexcom alert cleared, ID: ${parsed.alertId}"
                )
            }
            is CgmAlertActivatedFsl2HistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "FreeStyle Libre Alert: ${alert.name}"
                } else {
                    "FreeStyle Libre Alert (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "FreeStyle Libre alert activated, ID: ${parsed.alertId}"
                )
            }
            is CgmAlertClearedFsl2HistoryLog -> {
                val alert = parsed.alert
                val reason = if (alert != null) {
                    "FreeStyle Libre Alert Cleared: ${alert.name}"
                } else {
                    "FreeStyle Libre Alert Cleared (ID: ${parsed.alertId})"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "FreeStyle Libre alert cleared, ID: ${parsed.alertId}"
                )
            }
            else -> {
                Timber.w("Unexpected CGM alert type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
