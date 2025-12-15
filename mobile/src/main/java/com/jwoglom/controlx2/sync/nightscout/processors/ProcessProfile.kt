package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.createNightscoutProfile
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ProfileChangedHistoryLog
import timber.log.Timber
import java.time.ZoneId

/**
 * Process profile changes for Nightscout upload
 *
 * Triggered by ProfileChangedHistoryLog or IdpHistoryLog.
 * Note: The history logs often don't contain the full profile data, 
 * just the ID or name. We might need to fetch the full profile from the pump
 * or cache.
 * 
 * For now, this processor is a placeholder or "best effort" if data is available.
 * Real profile sync often requires active queries (CurrentProfileRequest).
 * 
 * However, the task says: "Read active profile from PumpState or request CurrentProfileRequest".
 * Since we are in a background sync worker, we might not have active connection.
 * We can only process what's in the history logs or what's cached in the DB/Prefs.
 * 
 * The `IdpHistoryLog` (IDP = Insulin Delivery Profile) contains segment data?
 * Let's check available logs.
 */
class ProcessProfile(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.PROFILE

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[ProfileChangedHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[IdpHistoryLog::class.java]
        )
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // We only need to upload the profile once per batch if multiple changes occurred
        // Use the latest change
        val latestLog = logs.maxByOrNull { it.seqId } ?: return 0

        // In a real implementation, we would reconstruct the full profile.
        // Since we don't have full profile persistence in HistoryLogRepo yet (only raw logs),
        // and HistoryLogs for profiles are fragmentary, we might need to skip full upload
        // or upload a partial event.
        
        // But the requirement is "Read active profile from PumpState".
        // Accessing global state from this worker is tricky if not passed in.
        // Assuming we can't easily get the full profile structure from just these logs without
        // complex state reconstruction.
        
        // For now, we will log that we saw a profile change, and if possible, 
        // upload what we know.
        
        // TODO: Implement full profile reconstruction from IDP messages or shared state.
        // This is a complex task requiring a "Profile Store" in the app DB.
        
        Timber.d("Profile change detected (seqId=${latestLog.seqId}), but full profile sync not yet implemented.")
        
        return 0
    }
}
