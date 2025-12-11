package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.createDeviceStatus
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process device status updates for Nightscout upload
 *
 * Converts device status records (battery, reservoir, IOB) to Nightscout devicestatus API format
 */
class ProcessDeviceStatus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.DEVICE_STATUS

    override fun supportedTypeIds(): Set<Int> {
        // Try to find device status-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BatteryStatusHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.ReservoirStatusHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.IOBStatusHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpStatusHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.DeviceStatusHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.StatusUpdateHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Device status HistoryLog class not found: $className")
                null
            }
        }.toSet()
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
        val statusClass = parsed.javaClass

        // Extract device status data using reflection
        val statusData = extractStatusData(statusClass, parsed)

        // Only create a device status if we have at least some data
        if (statusData.batteryPercent == null &&
            statusData.reservoirUnits == null &&
            statusData.iob == null &&
            statusData.pumpStatus == null) {
            Timber.d("Skipping device status with no data")
            return null
        }

        return createDeviceStatus(
            timestamp = item.pumpTime,
            batteryPercent = statusData.batteryPercent,
            reservoirUnits = statusData.reservoirUnits,
            iob = statusData.iob,
            pumpStatus = statusData.pumpStatus,
            uploaderBattery = null  // This would come from Android system, not pump history
        )
    }

    private data class StatusData(
        val batteryPercent: Int?,
        val reservoirUnits: Double?,
        val iob: Double?,
        val pumpStatus: String?
    )

    private fun extractStatusData(clazz: Class<*>, obj: Any): StatusData {
        try {
            // Try to get battery percentage
            val batteryPercent = tryGetField<Int>(clazz, obj, "batteryPercent")
                ?: tryGetField<Int>(clazz, obj, "batteryLevel")
                ?: tryGetField<Int>(clazz, obj, "battery")

            // Try to get reservoir level
            val reservoirMilliUnits = tryGetField<Int>(clazz, obj, "reservoirLevel")
                ?: tryGetField<Int>(clazz, obj, "reservoir")
                ?: tryGetField<Int>(clazz, obj, "insulinRemaining")
                ?: tryGetField<Long>(clazz, obj, "reservoirLevel")?.toInt()
                ?: tryGetField<Long>(clazz, obj, "reservoir")?.toInt()
                ?: tryGetField<Long>(clazz, obj, "insulinRemaining")?.toInt()

            val reservoirUnits = reservoirMilliUnits?.let { it / 1000.0 }

            // Try to get IOB
            val iobMilliUnits = tryGetField<Int>(clazz, obj, "iob")
                ?: tryGetField<Int>(clazz, obj, "activeInsulin")
                ?: tryGetField<Long>(clazz, obj, "iob")?.toInt()
                ?: tryGetField<Long>(clazz, obj, "activeInsulin")?.toInt()

            val iob = iobMilliUnits?.let { it / 1000.0 }
                ?: tryGetField<Double>(clazz, obj, "iob")
                ?: tryGetField<Double>(clazz, obj, "activeInsulin")

            // Try to get pump status
            val statusCode = tryGetField<Int>(clazz, obj, "status")
                ?: tryGetField<Int>(clazz, obj, "pumpStatus")

            val isSuspended = tryGetField<Boolean>(clazz, obj, "suspended")
                ?: tryGetField<Boolean>(clazz, obj, "isSuspended")

            val isBolusing = tryGetField<Boolean>(clazz, obj, "bolusing")
                ?: tryGetField<Boolean>(clazz, obj, "isBolusing")

            // Determine pump status string
            val pumpStatus = when {
                isSuspended == true -> "suspended"
                isBolusing == true -> "bolusing"
                statusCode != null -> "status_$statusCode"
                else -> null
            }

            return StatusData(
                batteryPercent = batteryPercent,
                reservoirUnits = reservoirUnits,
                iob = iob,
                pumpStatus = pumpStatus
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting device status data")
            return StatusData(null, null, null, null)
        }
    }

    private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(obj)
            if (value is T) value else null
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to access field: $fieldName")
            null
        }
    }
}
