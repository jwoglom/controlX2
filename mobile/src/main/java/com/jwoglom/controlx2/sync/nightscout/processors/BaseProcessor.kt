package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi

/**
 * Base class for Nightscout processors
 *
 * Each processor handles a specific type of pump data (CGM, bolus, basal, etc.)
 * and converts it to Nightscout format for upload.
 *
 * Following the tconnectsync architecture pattern.
 */
abstract class BaseProcessor(
    protected val nightscoutApi: NightscoutApi,
    protected val historyLogRepo: HistoryLogRepo
) {
    /**
     * The processor type (enum)
     */
    abstract fun processorType(): ProcessorType

    /**
     * Check if this processor is enabled in the configuration
     */
    fun isEnabled(config: NightscoutSyncConfig): Boolean {
        return config.enabledProcessors.contains(processorType())
    }

    /**
     * Set of history log type IDs that this processor handles
     * These are the numeric type IDs from HistoryLog.typeId()
     */
    abstract fun supportedTypeIds(): Set<Int>

    /**
     * Process history logs and upload to Nightscout
     *
     * @param logs The history log items to process (already filtered by supportedTypeIds)
     * @param config The Nightscout sync configuration
     * @return The number of items successfully uploaded
     */
    abstract suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int

    /**
     * Helper method to get the processor name for logging
     */
    protected fun processorName(): String = processorType().name
}
