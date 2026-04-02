package com.jwoglom.controlx2.db.historylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
     * Query history log items of the given types at or after [startTime].
     *
     * The [startTime] should be a standard UTC [Instant] (e.g. from [Instant.now]).
     * This method converts it to match the DB's pumpTime encoding internally, so
     * callers never need to account for the pump's local-time-as-UTC storage quirk.
     *
     * Background: the pump stores wall-clock time (local, no timezone) as seconds
     * since 2008-01-01, but [Dates.fromJan12008EpochSecondsToDate] treats those
     * seconds as UTC. The resulting [Instant] is offset from true UTC by the system
     * timezone. [HistoryLogItem.pumpTime] is then created via
     * `LocalDateTime.ofInstant(fakeUtcInstant, systemDefault())`, and Room persists
     * it as millis of that fake-UTC instant. To query correctly we must produce the
     * same fake-UTC instant from a real UTC [Instant].
     */
    fun itemsForTypesSince(typeClasses: List<Class<out HistoryLog>>, startTime: Instant): LiveData<List<HistoryLogItem>> {
        val typeIds = typeClasses.map { LOG_MESSAGE_CLASS_TO_ID[it]!! }
        // The pump records local wall-clock time as seconds-since-2008, but
        // Dates.fromJan12008EpochSecondsToDate treats those as UTC, producing
        // a "fake-UTC" Instant. HistoryLogItem.pumpTime is created via
        // LocalDateTime.ofInstant(fakeUtcInstant, systemDefault()), and Room
        // persists this as the fake-UTC epoch millis. To compare correctly,
        // shift the real-UTC startTime to fake-UTC by adding the TZ offset
        // (e.g. EDT offset is -4h, so adding it subtracts 4 hours).
        val offsetMs = java.util.TimeZone.getDefault().getOffset(startTime.toEpochMilli()).toLong()
        val fakeUtcInstant = startTime.plusMillis(offsetMs)
        val queryTime = LocalDateTime.ofInstant(fakeUtcInstant, ZoneId.systemDefault())
        return repo.getItemsForTypesSince(pumpSid, typeIds, queryTime).asLiveData()
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