package com.jwoglom.controlx2.db.historylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

class HistoryLogViewModel(private val repo: HistoryLogRepo, private val pumpSid: Int): ViewModel() {
    val count: LiveData<Long?> = repo.getCount(pumpSid).asLiveData()
    val all: LiveData<List<HistoryLogItem>> = repo.getAll(pumpSid).asLiveData()
    val latest: LiveData<HistoryLogItem?> = repo.getLatest(pumpSid).asLiveData()
    val oldest: LiveData<HistoryLogItem?> = repo.getOldest(pumpSid).asLiveData()

    fun getCountAboveSeqId(minSeqId: Long): LiveData<Long?> {
        return repo.getCountAboveSeqId(pumpSid, minSeqId).asLiveData()
    }

    fun latestForType(typeId: Int): LiveData<HistoryLogItem?> {
        return repo.getLatestForType(pumpSid, typeId).asLiveData()
    }

    fun latestItemsForType(typeId: Int, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return repo.getLatestItemsForType(pumpSid, typeId, maxItems).asLiveData()
    }

    fun latestItemsForType(typeClass: Class<out HistoryLog>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return latestItemsForType(LOG_MESSAGE_CLASS_TO_ID[typeClass]!!, maxItems)
    }

    fun latestItemsForTypes(typeIds: Array<Int>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return repo.getLatestItemsForTypes(pumpSid, typeIds.toList(), maxItems).asLiveData()
    }

    fun latestItemsForTypes(typeClasses: List<Class<out HistoryLog>>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return latestItemsForTypes(typeClasses.map { LOG_MESSAGE_CLASS_TO_ID[it]!!}.toTypedArray(), maxItems)
    }

    /**
     * Query history log items of the given types at or after [minPumpTimeSec].
     *
     * [minPumpTimeSec] is in the pump's time domain: local wall-clock seconds since
     * 2008-01-01. Compute it by subtracting from [com.jwoglom.pumpx2.pump.messages
     * .response.currentStatus.TimeSinceResetResponse.getCurrentTime], e.g.:
     *   `currentPumpTime - (6 * 3600)` for the last 6 hours.
     *
     * No timezone conversion needed — pump seconds compared to pump seconds.
     */
    fun itemsForTypesSince(typeClasses: List<Class<out HistoryLog>>, minPumpTimeSec: Long): LiveData<List<HistoryLogItem>> {
        val typeIds = typeClasses.map { LOG_MESSAGE_CLASS_TO_ID[it]!! }
        return repo.getItemsForTypesSince(pumpSid, typeIds, minPumpTimeSec).asLiveData()
    }

    companion object {
        /**
         * Estimate the current pump clock value from [Instant.now] when
         * [TimeSinceResetResponse] is not yet available. The pump clock tracks
         * local wall-clock time as seconds since 2008-01-01, so:
         *   pumpTimeSec ≈ localEpochSec - JANUARY_1_2008_UNIX_EPOCH
         */
        fun estimateCurrentPumpTimeSec(): Long {
            val offsetSec = java.util.TimeZone.getDefault()
                .getOffset(System.currentTimeMillis()) / 1000L
            return Instant.now().epochSecond + offsetSec - Dates.JANUARY_1_2008_UNIX_EPOCH
        }
    }

    fun insert(historyLogItem: HistoryLogItem) = viewModelScope.launch {
        repo.insert(historyLogItem)
    }

}

class HistoryLogViewModelFactory(private val repo: HistoryLogRepo, private val pumpSid: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryLogViewModel::class.java)) {
            Timber.i("HistoryLogViewModel created with pumpSid=$pumpSid")
            @Suppress("UNCHECKED_CAST")
            return HistoryLogViewModel(repo, pumpSid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}