package com.jwoglom.controlx2.sync.nightscout

import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorState
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorStateDao
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
 *
 * Each processor tracks its own cursor independently so that a failure in one
 * processor does not cause data loss for others.
 */
class NightscoutSyncCoordinator(
    private val historyLogRepo: HistoryLogRepo,
    private val nightscoutApi: NightscoutApi,
    private val syncStateDao: NightscoutSyncStateDao,
    private val processorStateDao: NightscoutProcessorStateDao,
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
        ProcessorType.CARB to ProcessCarb(nightscoutApi, historyLogRepo),
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
        ProcessorType.CARB,
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

        // Migrate: seed per-processor cursors from global cursor on first run
        val existingProcessorStates = processorStateDao.getAll()
        if (existingProcessorStates.isEmpty() && syncState.lastProcessedSeqId > 0) {
            Timber.i("Migrating global cursor ${syncState.lastProcessedSeqId} to per-processor cursors")
            for (type in processorOrder) {
                processorStateDao.upsert(
                    NightscoutProcessorState(
                        processorType = type.name,
                        lastProcessedSeqId = syncState.lastProcessedSeqId,
                        lastSuccessTime = syncState.lastSyncTime
                    )
                )
            }
        }

        // Determine the overall sequence ID range (from lowest processor cursor to latest log)
        val (globalStartSeqId, endSeqId) = determineSeqIdRange(syncState)

        if (globalStartSeqId >= endSeqId) {
            Timber.d("No new data to sync (startSeqId=$globalStartSeqId, endSeqId=$endSeqId)")
            return SyncResult.NoData
        }

        // Fetch all history logs in the widest needed range
        val allLogs = historyLogRepo.getRange(pumpSid, globalStartSeqId, endSeqId).first()

        if (allLogs.isEmpty()) {
            Timber.d("No history logs found in range $globalStartSeqId..$endSeqId")
            return SyncResult.NoData
        }

        Timber.i("Syncing ${allLogs.size} history logs (seqId $globalStartSeqId..$endSeqId)")

        var totalUploaded = 0

        // SEQUENTIAL processing — each processor has its own cursor
        for (processorType in processorOrder) {
            val processor = processorMap[processorType] ?: continue

            if (!processor.isEnabled(config)) {
                Timber.d("Processor ${processorType.name} is disabled, skipping")
                continue
            }

            // Get this processor's cursor
            val processorState = processorStateDao.getForProcessor(processorType.name)
            val processorStartSeqId = if (processorState != null) {
                processorState.lastProcessedSeqId + 1
            } else {
                globalStartSeqId
            }

            // Filter logs for this processor's supported types AND its cursor range
            val relevantLogs = allLogs.filter { log ->
                log.seqId >= processorStartSeqId &&
                    processor.supportedTypeIds().contains(log.typeId)
            }.sortedBy { it.seqId }

            if (relevantLogs.isEmpty()) {
                Timber.d("No logs for processor ${processorType.name} (cursor=$processorStartSeqId)")
                // Still advance cursor to endSeqId if there are no relevant logs
                // (otherwise this processor's cursor would hold back the global minimum)
                processorStateDao.upsert(
                    NightscoutProcessorState(
                        processorType = processorType.name,
                        lastProcessedSeqId = allLogs.maxOf { it.seqId },
                        lastSuccessTime = LocalDateTime.now()
                    )
                )
                continue
            }

            try {
                val uploadedCount = processor.process(relevantLogs, config)
                totalUploaded += uploadedCount

                // Success — advance this processor's cursor
                val latestProcessedSeqId = allLogs.maxOf { it.seqId }
                processorStateDao.upsert(
                    NightscoutProcessorState(
                        processorType = processorType.name,
                        lastProcessedSeqId = latestProcessedSeqId,
                        lastSuccessTime = LocalDateTime.now()
                    )
                )

                Timber.i("${processorType.name}: processed ${relevantLogs.size} logs, uploaded $uploadedCount items")
            } catch (e: Exception) {
                Timber.e(e, "Error processing ${processorType.name} — cursor NOT advanced, will retry")
                // Do NOT advance this processor's cursor — data will be retried next sync
            }
        }

        // Update global sync state to min of all processor cursors (backward compat)
        val minProcessorSeqId = processorStateDao.getMinSeqId() ?: allLogs.maxOf { it.seqId }
        syncStateDao.updateLastProcessed(minProcessorSeqId, LocalDateTime.now())
        Timber.i("Updated global sync state: lastProcessedSeqId=$minProcessorSeqId")

        // Clear retroactive range if it was set
        if (syncState.retroactiveStartTime != null) {
            syncStateDao.setRetroactiveRange(null, null)
            Timber.i("Cleared retroactive sync range")
        }

        return SyncResult.Success(
            processedCount = allLogs.size,
            uploadedCount = totalUploaded,
            seqIdRange = globalStartSeqId to minProcessorSeqId
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
     * Determine the sequence ID range to process based on sync state.
     * Uses the minimum of all per-processor cursors as the start.
     */
    private suspend fun determineSeqIdRange(syncState: NightscoutSyncState): Pair<Long, Long> {
        val latestLog = historyLogRepo.getLatest(pumpSid).first()
        val endSeqId = latestLog?.seqId ?: Long.MAX_VALUE

        // Case 1: Retroactive sync requested
        if (syncState.retroactiveStartTime != null && syncState.retroactiveEndTime != null) {
            return findSeqIdRangeForTimeRange(
                syncState.retroactiveStartTime!!,
                syncState.retroactiveEndTime!!
            )
        }

        // Case 2: Use minimum of per-processor cursors (preferred)
        val minProcessorSeqId = processorStateDao.getMinSeqId()
        if (minProcessorSeqId != null && minProcessorSeqId > 0) {
            val startSeqId = minProcessorSeqId + 1
            Timber.d("Resuming from min processor cursor=$minProcessorSeqId")
            return startSeqId to endSeqId
        }

        // Case 3: Fall back to global cursor
        if (syncState.lastProcessedSeqId > 0) {
            val startSeqId = syncState.lastProcessedSeqId + 1
            Timber.d("Resuming from global lastProcessedSeqId=${syncState.lastProcessedSeqId}")
            return startSeqId to endSeqId
        }

        // Case 4: First-time sync - use lookbackHours
        val lookbackTime = LocalDateTime.now().minusHours(syncState.lookbackHours.toLong())
        val logs = historyLogRepo.getAll(pumpSid).first()
        val startSeqId = logs.firstOrNull { it.pumpTime >= lookbackTime }?.seqId ?: 0L

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
