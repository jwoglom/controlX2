package com.jwoglom.controlx2.sync.nightscout.processors

import android.content.Context
import android.os.BatteryManager
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.*
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process device status updates for Nightscout upload
 *
 * Converts device status records (battery, reservoir, IOB) to Nightscout devicestatus API format
 * Aggregates multiple logs to form a complete status.
 */
class ProcessDeviceStatus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo,
    private val context: Context
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.DEVICE_STATUS

    override fun supportedTypeIds(): Set<Int> {
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
                // Timber.d("Device status HistoryLog class not found: $className")
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

        // Aggregate state from all logs in the batch
        var batteryPercent: Int? = null
        var reservoirUnits: Double? = null
        var iob: Double? = null
        var pumpStatusStr: String? = null
        var isSuspended: Boolean? = null
        var isBolusing: Boolean? = null
        
        // We use the timestamp of the LATEST log for the update
        val latestLog = logs.maxByOrNull { it.pumpTime } ?: return 0
        val timestamp = latestLog.pumpTime

        // Iterate chronologically to build state
        logs.sortedBy { it.seqId }.forEach { log ->
            try {
                val parsed = log.parse()
                val data = extractStatusData(parsed.javaClass, parsed)
                
                if (data.batteryPercent != null) batteryPercent = data.batteryPercent
                if (data.reservoirUnits != null) reservoirUnits = data.reservoirUnits
                if (data.iob != null) iob = data.iob
                if (data.pumpStatus != null) pumpStatusStr = data.pumpStatus
                if (data.isSuspended != null) isSuspended = data.isSuspended
                if (data.isBolusing != null) isBolusing = data.isBolusing
            } catch (e: Exception) {
                Timber.e(e, "Error parsing log seqId=${log.seqId}")
            }
        }

        if (batteryPercent == null && reservoirUnits == null && iob == null && pumpStatusStr == null) {
            Timber.d("${processorName()}: No status data found in batch")
            return 0
        }

        // Get uploader battery
        val uploaderBat = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Timber.w("Could not get uploader battery: ${e.message}")
            null
        }

        // Get pump model
        val pumpModel = Prefs(context).pumpModel().ifEmpty { "Tandem Pump" }

        try {
            val deviceStatus = NightscoutDeviceStatus(
                createdAt = timestamp.toString(),
                device = pumpModel,
                uploaderBattery = uploaderBat,
                pump = PumpStatus(
                    battery = batteryPercent?.let { Battery(it) },
                    reservoir = reservoirUnits,
                    iob = iob?.let { IOB(iob = it, timestamp = timestamp.toString()) },
                    status = PumpStatusInfo(
                        status = pumpStatusStr,
                        bolusing = isBolusing,
                        suspended = isSuspended,
                        timestamp = timestamp.toString()
                    ),
                    clock = timestamp.toString()
                )
            )

            val result = nightscoutApi.uploadDeviceStatus(deviceStatus)
            return if (result.isSuccess && result.getOrNull() == true) {
                Timber.d("${processorName()}: Uploaded aggregated device status")
                1
            } else {
                Timber.e("${processorName()}: Upload failed: ${result.exceptionOrNull()}")
                0
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload device status")
            return 0
        }
    }

    private data class StatusData(
        val batteryPercent: Int?,
        val reservoirUnits: Double?,
        val iob: Double?,
        val pumpStatus: String?,
        val isSuspended: Boolean?,
        val isBolusing: Boolean?
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
                pumpStatus = pumpStatus,
                isSuspended = isSuspended,
                isBolusing = isBolusing
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting device status data")
            return StatusData(null, null, null, null, null, null)
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
