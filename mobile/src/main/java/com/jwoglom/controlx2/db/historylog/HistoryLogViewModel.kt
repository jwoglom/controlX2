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