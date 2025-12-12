package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process cartridge changes for Nightscout upload
 *
 * Converts cartridge change records to Nightscout "Insulin Change" or "Site Change" treatments
 */
class ProcessCartridge(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARTRIDGE

    override fun supportedTypeIds(): Set<Int> {
        // Try to find cartridge-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinCartridgeChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.InsulinChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.SiteChangeHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.LoadCartridgeHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Cartridge HistoryLog class not found: $className")
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

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                cartridgeToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert cartridge change seqId=${item.seqId}")
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

    private fun cartridgeToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val cartridgeClass = parsed.javaClass

        // Extract cartridge change data using reflection
        val cartridgeData = extractCartridgeData(cartridgeClass, parsed)

        // Determine event type based on change type
        val eventType = when {
            cartridgeData.isSiteChange -> "Site Change"
            else -> "Insulin Change"
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = eventType,
            timestamp = item.pumpTime,
            seqId = item.seqId,
            notes = cartridgeData.notes
        )
    }

    private data class CartridgeData(
        val isSiteChange: Boolean,
        val notes: String
    )

    private fun extractCartridgeData(clazz: Class<*>, obj: Any): CartridgeData {
        try {
            // Determine change type from class name
            val className = clazz.simpleName
            val isSiteChange = className.contains("Site", ignoreCase = true) ||
                              className.contains("Tubing", ignoreCase = true) ||
                              className.contains("Cannula", ignoreCase = true)

            // Try to get cartridge volume
            val cartridgeVolume = tryGetField<Int>(clazz, obj, "cartridgeVolume")
                ?: tryGetField<Int>(clazz, obj, "volume")
                ?: tryGetField<Int>(clazz, obj, "insulinVolume")

            // Try to get fill amount (for tubing/cannula fills)
            val fillAmount = tryGetField<Int>(clazz, obj, "fillAmount")
                ?.let { it / 1000.0 } // Convert milli-units to units
                ?: tryGetField<Int>(clazz, obj, "primeAmount")
                    ?.let { it / 1000.0 }
                ?: tryGetField<Double>(clazz, obj, "fillAmount")
                ?: tryGetField<Double>(clazz, obj, "primeAmount")

            // Try to get cartridge type
            val cartridgeType = tryGetField<Int>(clazz, obj, "cartridgeType")
                ?: tryGetField<String>(clazz, obj, "cartridgeType")

            // Try to get serial number
            val serialNumber = tryGetField<String>(clazz, obj, "serialNumber")
                ?: tryGetField<Long>(clazz, obj, "serialNumber")?.toString()

            // Build notes with all available info
            val notes = buildString {
                if (isSiteChange) {
                    append("Site change")
                } else {
                    append("Insulin cartridge change")
                }

                cartridgeVolume?.let {
                    append(", volume: ${it}U")
                }

                fillAmount?.let {
                    append(", filled: ${it}U")
                }

                cartridgeType?.let {
                    append(", type: $it")
                }

                serialNumber?.let {
                    append(", S/N: $it")
                }

                // Check for specific fill types
                if (className.contains("Tubing", ignoreCase = true)) {
                    append(" (tubing)")
                } else if (className.contains("Cannula", ignoreCase = true)) {
                    append(" (cannula)")
                }
            }

            return CartridgeData(
                isSiteChange = isSiteChange,
                notes = notes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting cartridge data")
            return CartridgeData(
                isSiteChange = false,
                notes = "Cartridge change (details unavailable)"
            )
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
