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
import kotlin.math.max
import kotlin.math.min

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
    private val processorStateDao: NightscoutProcessorStateDao,
    private val config: NightscoutSyncConfig,
    private val pumpSid: Int,
    private val context: android.content.Context
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
        ProcessorType.DEVICE_STATUS to ProcessDeviceStatus(nightscoutApi, historyLogRepo, context)
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

        // Check for retroactive sync
        if (syncState.retroactiveStartTime != null && syncState.retroactiveEndTime != null) {
            return syncRetroactiveInternal(syncState)
        }

        return syncIncremental(syncState)
    }

    private suspend fun syncIncremental(syncState: NightscoutSyncState): SyncResult {
        // Get latest log once to know the ceiling
        val latestLog = historyLogRepo.getLatest(pumpSid).first()
        val latestSeqId = latestLog?.seqId ?: 0L

        if (latestSeqId == 0L) {
            Timber.d("No data available to sync (latestSeqId=0)")
            return SyncResult.NoData
        }

        var totalUploaded = 0
        var minStartSeqId = Long.MAX_VALUE
        var maxEndSeqId = 0L
        var processedCount = 0

        for (processorType in processorOrder) {
            val processor = processorMap[processorType] ?: continue
            if (!processor.isEnabled(config)) {
                continue
            }

            // Determine start SeqId for this processor
            val procState = processorStateDao.getProcessorState(processorType.name)
            val startSeqId = if (procState != null) {
                procState.lastProcessedSeqId + 1
            } else {
                // Fallback to global state or lookback
                if (syncState.lastProcessedSeqId > 0) {
                    syncState.lastProcessedSeqId + 1
                } else {
                    getLookbackStartSeqId(syncState)
                }
            }

            if (startSeqId > latestSeqId) {
                continue
            }

            // Fetch logs for this processor
            // We fetch the full range but filter by supported types for efficiency
            val logs = historyLogRepo.getRange(pumpSid, startSeqId, latestSeqId).first()
                .filter { processor.supportedTypeIds().contains(it.typeId) }
                .sortedBy { it.seqId }

            if (logs.isEmpty()) {
                // Even if empty, we should update the cursor to latestSeqId so we don't scan empty ranges forever?
                // Yes, if we scanned up to latestSeqId and found nothing, we are caught up.
                updateProcessorState(processorType.name, latestSeqId)
                continue
            }

            minStartSeqId = min(minStartSeqId, startSeqId)
            
            try {
                val uploaded = processor.process(logs, config)
                totalUploaded += uploaded
                processedCount += logs.size

                val newLastSeqId = logs.maxOf { it.seqId }
                maxEndSeqId = max(maxEndSeqId, newLastSeqId)
                
                // Update processor state to the last log we actually processed
                // If we skipped some logs (e.g. unsupported within the type), we still advanced?
                // The logs list contains only supported types. 
                // Ideally we should advance to latestSeqId if we successfully processed the range.
                // But if we crash midway, we want to resume.
                // Process methods typically process all or throw.
                // Let's mark up to the last log in the batch.
                // Or better: if we successfully ran the processor on the range startSeqId..latestSeqId,
                // we should update to latestSeqId, assuming the processor handled everything in that range it cared about.
                
                // However, logs is filtered. If there were logs of other types after the last relevant log,
                // we would re-scan them next time if we only update to newLastSeqId.
                // So we should update to latestSeqId, because we have confirmed there are no relevant logs 
                // between newLastSeqId and latestSeqId (since we fetched the range).
                
                updateProcessorState(processorType.name, latestSeqId)

            } catch (e: Exception) {
                Timber.e(e, "Error processing ${processorType.name}")
                // Don't update cursor
            }
        }

        if (processedCount == 0 && totalUploaded == 0) {
            return SyncResult.NoData
        }

        // Update global state for reference (using the max processed)
        // We use this as a "last active" indicator
        syncStateDao.updateLastProcessed(latestSeqId, LocalDateTime.now())

        return SyncResult.Success(
            processedCount = processedCount,
            uploadedCount = totalUploaded,
            seqIdRange = minStartSeqId to latestSeqId
        )
    }

    private suspend fun syncRetroactiveInternal(syncState: NightscoutSyncState): SyncResult {
        val (startSeqId, endSeqId) = findSeqIdRangeForTimeRange(
            syncState.retroactiveStartTime!!,
            syncState.retroactiveEndTime!!
        )

        Timber.i("Starting retroactive sync: seqId $startSeqId..$endSeqId")

        // Fetch all history logs in range
        val allLogs = historyLogRepo.getRange(pumpSid, startSeqId, endSeqId).first()

        if (allLogs.isEmpty()) {
            clearRetroactiveState()
            return SyncResult.NoData
        }

        var totalUploaded = 0

        for (processorType in processorOrder) {
            val processor = processorMap[processorType] ?: continue
            if (!processor.isEnabled(config)) continue

            val relevantLogs = allLogs.filter { log ->
                processor.supportedTypeIds().contains(log.typeId)
            }.sortedBy { it.seqId }

            if (relevantLogs.isEmpty()) continue

            try {
                val uploadedCount = processor.process(relevantLogs, config)
                totalUploaded += uploadedCount
                
                // We do NOT update processor cursors for retroactive, 
                // unless we want to "fast forward" them. 
                // Safe option: don't touch cursors, just upload.
                // This prevents messing up the incremental sync if retroactive is for an old range.
            } catch (e: Exception) {
                Timber.e(e, "Error processing ${processorType.name} (retroactive)")
            }
        }

        clearRetroactiveState()

        return SyncResult.Success(
            processedCount = allLogs.size,
            uploadedCount = totalUploaded,
            seqIdRange = startSeqId to endSeqId
        )
    }

    private suspend fun clearRetroactiveState() {
        syncStateDao.setRetroactiveRange(null, null)
        Timber.i("Cleared retroactive sync range")
    }

    private suspend fun updateProcessorState(type: String, seqId: Long) {
        processorStateDao.upsert(NightscoutProcessorState(
            processorType = type,
            lastProcessedSeqId = seqId,
            lastSuccessTime = LocalDateTime.now()
        ))
    }

    private suspend fun getLookbackStartSeqId(syncState: NightscoutSyncState): Long {
        val lookbackTime = LocalDateTime.now().minusHours(syncState.lookbackHours.toLong())
        val logs = historyLogRepo.getAll(pumpSid).first()
        return logs.firstOrNull { it.pumpTime >= lookbackTime }?.seqId ?: 0L
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
     * Find sequence ID range for a specific time range
     */
    private suspend fun findSeqIdRangeForTimeRange(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Pair<Long, Long> {
        val logs = historyLogRepo.getAll(pumpSid).first()

        val startSeqId = logs.firstOrNull { it.pumpTime >= startTime }?.seqId ?: 0L
        val endSeqId = logs.lastOrNull { it.pumpTime <= endTime }?.seqId ?: Long.MAX_VALUE

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
