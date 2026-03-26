package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.createDeviceStatus
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process device status updates for Nightscout upload
 *
 * Aggregates battery, IOB, and reservoir data from all logs in the batch
 * to build a composite device status, preventing data loss when the latest
 * log doesn't contain all fields.
 */
class ProcessDeviceStatus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.DEVICE_STATUS

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[DailyBasalHistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Sort chronologically and accumulate composite state from all logs
        val sortedLogs = logs.sortedBy { it.pumpTime }

        var latestBattery: Int? = null
        var latestIob: Double? = null
        var latestReservoir: Double? = null

        for (log in sortedLogs) {
            try {
                val parsed = log.parse()
                when (parsed) {
                    is DailyBasalHistoryLog -> {
                        latestBattery = parsed.batteryChargePercent.toInt()
                        latestIob = parsed.iob.toDouble()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "${processorName()}: Failed to parse log seqId=${log.seqId}")
            }
        }

        if (latestBattery == null && latestIob == null && latestReservoir == null) {
            Timber.d("${processorName()}: No meaningful device status data")
            return 0
        }

        val latestTimestamp = sortedLogs.last().pumpTime
        val deviceStatus = createDeviceStatus(
            timestamp = latestTimestamp,
            batteryPercent = latestBattery,
            reservoirUnits = latestReservoir,
            iob = latestIob,
            pumpStatus = null,
            uploaderBattery = null,
            device = config.pumpModelName
        )

        val result = nightscoutApi.uploadDeviceStatus(deviceStatus)
        return if (result.isSuccess && result.getOrNull() == true) {
            Timber.d("${processorName()}: Uploaded composite device status")
            1
        } else {
            Timber.e("${processorName()}: Upload failed: ${result.exceptionOrNull()}")
            0
        }
    }
}
