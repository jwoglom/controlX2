package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.createDeviceStatus
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerResumeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerSuspendHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import timber.log.Timber

/**
 * Process device status updates for Nightscout upload.
 *
 * Aggregates battery, IOB, and pump status from multiple history log types
 * to build a composite device status, rather than relying on a single record.
 */
class ProcessDeviceStatus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.DEVICE_STATUS

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[DailyBasalHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[PumpingSuspendedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[PumpingResumedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[HypoMinimizerSuspendHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[HypoMinimizerResumeHistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Build composite state by iterating chronologically
        var battery: Int? = null
        var iob: Double? = null
        var suspended: Boolean? = null
        var latestTime = logs.first().pumpTime

        for (log in logs.sortedBy { it.pumpTime }) {
            try {
                val parsed = log.parse()
                latestTime = log.pumpTime
                when (parsed) {
                    is DailyBasalHistoryLog -> {
                        battery = parsed.batteryChargePercent.toInt()
                        iob = parsed.iob.toDouble()
                    }
                    is PumpingSuspendedHistoryLog -> suspended = true
                    is PumpingResumedHistoryLog -> suspended = false
                    is HypoMinimizerSuspendHistoryLog -> suspended = true
                    is HypoMinimizerResumeHistoryLog -> suspended = false
                }
            } catch (e: Exception) {
                Timber.e(e, "${processorName()}: Failed to parse log seqId=${log.seqId}")
            }
        }

        // Only upload if we have meaningful data
        if (battery == null && iob == null && suspended == null) {
            Timber.d("${processorName()}: No meaningful device status data to upload")
            return 0
        }

        val pumpStatus = when (suspended) {
            true -> "suspended"
            false -> "normal"
            null -> null
        }

        val deviceStatus = createDeviceStatus(
            timestamp = latestTime,
            batteryPercent = battery,
            reservoirUnits = null,
            iob = iob,
            pumpStatus = pumpStatus,
            suspended = suspended,
            bolusing = null,
            uploaderBattery = config.uploaderBattery,
            deviceName = config.pumpModel ?: "Tandem Pump"
        )

        val result = nightscoutApi.uploadDeviceStatus(deviceStatus)
        return if (result.isSuccess && result.getOrNull() == true) {
            Timber.d("${processorName()}: Uploaded aggregated device status (battery=$battery, iob=$iob, suspended=$suspended)")
            1
        } else {
            Timber.e("${processorName()}: Upload failed: ${result.exceptionOrNull()}")
            0
        }
    }
}
