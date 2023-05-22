package com.jwoglom.controlx2.db.historylog

import androidx.annotation.WorkerThread
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime

class HistoryLogRepo(private val historyLogDao: HistoryLogDao, private val pumpSerial: Int) {
    val all: Flow<List<HistoryLogItem>> = historyLogDao.getAll(pumpSerial)

    fun allForType(typeId: Int): Flow<List<HistoryLogItem>> {
        return historyLogDao.getAllForType(pumpSerial, typeId)
    }

    fun getRange(seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> {
        return historyLogDao.getRange(pumpSerial, seqIdMin, seqIdMax)
    }

    fun getRangeForType(typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> {
        return historyLogDao.getRangeForType(pumpSerial, typeId, seqIdMin, seqIdMax)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(historyLogItem: HistoryLogItem) {
        Timber.d("HistoryLogRepo.insert($historyLogItem)")
        historyLogDao.insert(historyLogItem)
    }

    suspend fun insert(historyLog: HistoryLog) {
        val historyLogItem = HistoryLogItem(
            seqId = historyLog.sequenceNum,
            pumpSerial = pumpSerial,
            typeId = historyLog.typeId(),
            cargo = historyLog.cargo,
            addedTime = LocalDateTime.now()
        )
        insert(historyLogItem)
    }
}