package com.jwoglom.controlx2.db.historylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HistoryLogViewModel(private val repo: HistoryLogRepo): ViewModel() {
    val all: LiveData<List<HistoryLogItem>> = repo.all.asLiveData()

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