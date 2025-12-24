package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process pump alarms for Nightscout upload
 *
 * Converts alarm records to Nightscout "Announcement" treatments
 */
class ProcessAlarm(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.ALARM

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[AlarmActivatedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[AlarmClearedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[AlertActivatedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[AlertClearedHistoryLog::class.java]
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
                alarmToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert alarm seqId=${item.seqId}")
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

    private fun alarmToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is AlarmActivatedHistoryLog -> {
                val alarmType = parsed.alarmResponseType
                val reason = if (alarmType != null) {
                    "Alarm activated: ${alarmType.name}"
                } else {
                    "Alarm activated: ID ${parsed.alarmId}"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Pump alarm activated, ID: ${parsed.alarmId}"
                )
            }
            is AlarmClearedHistoryLog -> {
                val alarmType = parsed.alarmResponseType
                val reason = if (alarmType != null) {
                    "Alarm cleared: ${alarmType.name}"
                } else {
                    "Alarm cleared: ID ${parsed.alarmId}"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Pump alarm cleared, ID: ${parsed.alarmId}"
                )
            }
            is AlertActivatedHistoryLog -> {
                val alertType = parsed.alertResponseType
                val reason = if (alertType != null) {
                    "Alert activated: ${alertType.name}"
                } else {
                    "Alert activated: ID ${parsed.alertId}"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Pump alert activated, ID: ${parsed.alertId}"
                )
            }
            is AlertClearedHistoryLog -> {
                val alertType = parsed.alertResponseType
                val reason = if (alertType != null) {
                    "Alert cleared: ${alertType.name}"
                } else {
                    "Alert cleared: ID ${parsed.alertId}"
                }

                NightscoutTreatment.fromTimestamp(
                    eventType = "Announcement",
                    timestamp = item.pumpTime,
                    seqId = item.seqId,
                    reason = reason,
                    notes = "Pump alert cleared, ID: ${parsed.alertId}"
                )
            }
            else -> {
                Timber.w("Unexpected alarm type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
