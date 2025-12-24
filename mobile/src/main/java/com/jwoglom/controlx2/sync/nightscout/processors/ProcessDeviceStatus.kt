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
 * Converts DailyBasal records containing battery, IOB, and basal rate to Nightscout devicestatus API format
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

        // Process device status updates
        // Note: We only upload the most recent status to avoid flooding Nightscout
        val latestLog = logs.maxByOrNull { it.pumpTime }

        if (latestLog == null) {
            Timber.d("${processorName()}: No status to upload")
            return 0
        }

        try {
            val deviceStatus = deviceStatusToNightscout(latestLog)
            if (deviceStatus != null) {
                val result = nightscoutApi.uploadDeviceStatus(deviceStatus)
                return if (result.isSuccess && result.getOrNull() == true) {
                    Timber.d("${processorName()}: Uploaded device status")
                    1
                } else {
                    Timber.e("${processorName()}: Upload failed: ${result.exceptionOrNull()}")
                    0
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert device status seqId=${latestLog.seqId}")
        }

        return 0
    }

    private fun deviceStatusToNightscout(item: HistoryLogItem): com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus? {
        val parsed = item.parse()

        return when (parsed) {
            is DailyBasalHistoryLog -> {
                val batteryPercent = parsed.batteryChargePercent.toInt()
                val iob = parsed.iob.toDouble()

                createDeviceStatus(
                    timestamp = item.pumpTime,
                    batteryPercent = batteryPercent,
                    reservoirUnits = null, // Not available in DailyBasalHistoryLog
                    iob = iob,
                    pumpStatus = null, // Could be inferred but not directly available
                    uploaderBattery = null  // This would come from Android system, not pump history
                )
            }
            else -> {
                Timber.w("Unexpected device status type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
