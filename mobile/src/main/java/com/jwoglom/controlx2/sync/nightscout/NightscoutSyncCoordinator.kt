package com.jwoglom.controlx2.sync.nightscout

import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDao
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncState
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.processors.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Main coordinator for Nightscout sync operations
 *
 * Orchestrates sequential processing of pump data through specialized processors,
 * following the tconnectsync architecture pattern.
 */
class NightscoutSyncCoordinator(
    private val historyLogRepo: HistoryLogRepo,
    private val nightscoutApi: NightscoutApi,
    private val syncStateDao: NightscoutSyncStateDao,
    private val config: NightscoutSyncConfig,
    private val pumpSid: Int
) {
    // Map processor types to instances
    private val processorMap: Map<ProcessorType, BaseProcessor> = mapOf(
        ProcessorType.CGM_READING to ProcessCGMReading(nightscoutApi, historyLogRepo),
        ProcessorType.BOLUS to ProcessBolus(nightscoutApi, historyLogRepo),
        ProcessorType.BASAL to ProcessBasal(nightscoutApi, historyLogRepo),
        ProcessorType.BASAL_SUSPENSION to ProcessBasalSuspension(nightscoutApi, historyLogRepo),
        ProcessorType.BASAL_RESUME to ProcessBasalResume(nightscoutApi, historyLogRepo),
        ProcessorType.ALARM to ProcessAlarm(nightscoutApi, historyLogRepo),
        ProcessorType.CGM_ALERT to ProcessCGMAlert(nightscoutApi, historyLogRepo),
        ProcessorType.USER_MODE to ProcessUserMode(nightscoutApi, historyLogRepo),
        ProcessorType.CARTRIDGE to ProcessCartridge(nightscoutApi, historyLogRepo),
        ProcessorType.DEVICE_STATUS to ProcessDeviceStatus(nightscoutApi, historyLogRepo)
    )

    // Processors in processing order (following tconnectsync)
    private val processorOrder = listOf(
        ProcessorType.CGM_READING,
        ProcessorType.BOLUS,
        ProcessorType.BASAL,
        ProcessorType.BASAL_SUSPENSION,
        ProcessorType.BASAL_RESUME,
        ProcessorType.ALARM,
        ProcessorType.CGM_ALERT,
        ProcessorType.USER_MODE,
        ProcessorType.CARTRIDGE,
        ProcessorType.DEVICE_STATUS
    )

    /**
     * Sync all enabled processors
     */
    suspend fun syncAll(): SyncResult {
        if (!config.enabled) {
            Timber.d("Nightscout sync is disabled")
            return SyncResult.Disabled
        }

        if (!config.isValid()) {
            Timber.e("Nightscout configuration is invalid")
            return SyncResult.InvalidConfig
        }

        var syncState = syncStateDao.getState()

        // First-time setup
        if (syncState == null) {
            syncState = NightscoutSyncState(
                firstEnabledTime = LocalDateTime.now(),
                lookbackHours = config.initialLookbackHours
            )
            syncStateDao.upsert(syncState)
            Timber.i("Initialized Nightscout sync state with ${config.initialLookbackHours}h lookback")
        }

        // Determine the sequence ID range to process
        val (startSeqId, endSeqId) = determineSeqIdRange(syncState)

        if (startSeqId >= endSeqId) {
            Timber.d("No new data to sync (startSeqId=$startSeqId, endSeqId=$endSeqId)")
            return SyncResult.NoData
        }

        // Fetch all history logs in range
        val allLogs = historyLogRepo.getRange(pumpSid, startSeqId, endSeqId).first()

        if (allLogs.isEmpty()) {
            Timber.d("No history logs found in range $startSeqId..$endSeqId")
            return SyncResult.NoData
        }

        Timber.i("Syncing ${allLogs.size} history logs (seqId $startSeqId..$endSeqId)")

        var totalUploaded = 0

        // SEQUENTIAL processing (not parallel!)
        for (processorType in processorOrder) {
            val processor = processorMap[processorType] ?: continue

            if (!processor.isEnabled(config)) {
                Timber.d("Processor ${processorType.name} is disabled, skipping")
                continue
            }

            // Filter logs for this processor's supported types
            val relevantLogs = allLogs.filter { log ->
                processor.supportedTypeIds().contains(log.typeId)
            }.sortedBy { it.seqId }  // Ensure chronological order

            if (relevantLogs.isEmpty()) {
                Timber.d("No logs for processor ${processorType.name}")
                continue
            }

            try {
                val uploadedCount = processor.process(relevantLogs, config)
                totalUploaded += uploadedCount

                Timber.i("${processorType.name}: processed ${relevantLogs.size} logs, uploaded $uploadedCount items")
            } catch (e: Exception) {
                Timber.e(e, "Error processing ${processorType.name}")
                // Continue with next processor (don't fail entire sync)
            }
        }

        // Update sync state with last processed sequence ID
        val latestSeqId = allLogs.maxOf { it.seqId }
        syncStateDao.updateLastProcessed(latestSeqId, LocalDateTime.now())
        Timber.i("Updated sync state: lastProcessedSeqId=$latestSeqId")

        // Clear retroactive range if it was set
        if (syncState.retroactiveStartTime != null) {
            syncStateDao.setRetroactiveRange(null, null)
            Timber.i("Cleared retroactive sync range")
        }

        return SyncResult.Success(
            processedCount = allLogs.size,
            uploadedCount = totalUploaded,
            seqIdRange = startSeqId to latestSeqId
        )
    }

    /**
     * Perform retroactive sync for a specific time range
     */
    suspend fun syncRetroactive(startTime: LocalDateTime, endTime: LocalDateTime): SyncResult {
        Timber.i("Starting retroactive sync: $startTime to $endTime")
        syncStateDao.setRetroactiveRange(startTime, endTime)
        return syncAll()
    }

    /**
     * Determine the sequence ID range to process based on sync state
     */
    private suspend fun determineSeqIdRange(syncState: NightscoutSyncState): Pair<Long, Long> {
        // Case 1: Retroactive sync requested
        if (syncState.retroactiveStartTime != null && syncState.retroactiveEndTime != null) {
            return findSeqIdRangeForTimeRange(
                syncState.retroactiveStartTime!!,
                syncState.retroactiveEndTime!!
            )
        }

        // Case 2: Resume from last processed (IGNORES lookbackHours changes!)
        if (syncState.lastProcessedSeqId > 0) {
            val startSeqId = syncState.lastProcessedSeqId + 1
            val latestLog = historyLogRepo.getLatest(pumpSid).first()
            val endSeqId = latestLog?.seqId ?: Long.MAX_VALUE

            Timber.d("Resuming from lastProcessedSeqId=${syncState.lastProcessedSeqId}")
            return startSeqId to endSeqId
        }

        // Case 3: First-time sync - use lookbackHours
        val lookbackTime = LocalDateTime.now().minusHours(syncState.lookbackHours.toLong())
        val logs = historyLogRepo.getAll(pumpSid).first()
        val startSeqId = logs.firstOrNull { it.pumpTime >= lookbackTime }?.seqId ?: 0L

        val latestLog = historyLogRepo.getLatest(pumpSid).first()
        val endSeqId = latestLog?.seqId ?: Long.MAX_VALUE

        Timber.d("First-time sync with ${syncState.lookbackHours}h lookback from $lookbackTime")
        return startSeqId to endSeqId
    }

    /**
     * Find sequence ID range for a specific time range
     */
    private suspend fun findSeqIdRangeForTimeRange(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Pair<Long, Long> {
        val logs = historyLogRepo.getAll(pumpSid).first()

        val startSeqId = logs.firstOrNull { it.pumpTime >= startTime }?.seqId ?: 0L
        val endSeqId = logs.lastOrNull { it.pumpTime <= endTime }?.seqId ?: Long.MAX_VALUE

        Timber.d("Retroactive sync range: seqId $startSeqId..$endSeqId for time $startTime..$endTime")
        return startSeqId to endSeqId
    }
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    object Disabled : SyncResult()
    object InvalidConfig : SyncResult()
    object NoData : SyncResult()
    data class Success(
        val processedCount: Int,
        val uploadedCount: Int,
        val seqIdRange: Pair<Long, Long>
    ) : SyncResult()
}
