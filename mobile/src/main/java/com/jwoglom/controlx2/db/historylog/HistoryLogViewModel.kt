package com.jwoglom.controlx2.db.historylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber

class HistoryLogViewModel(private val repo: HistoryLogRepo): ViewModel() {
    fun all(pumpSid: Int): LiveData<List<HistoryLogItem>> {
        return repo.getAll(pumpSid).asLiveData()
    }

    fun latest(pumpSid: Int): LiveData<HistoryLogItem?> {
        Timber.i("HistoryLogViewModel.latest($pumpSid)")
        return repo.getLatest(pumpSid).asLiveData()
    }

    fun insert(historyLogItem: HistoryLogItem) = viewModelScope.launch {
        repo.insert(historyLogItem)
    }
}

class HistoryLogViewModelFactory(private val repo: HistoryLogRepo) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryLogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryLogViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}