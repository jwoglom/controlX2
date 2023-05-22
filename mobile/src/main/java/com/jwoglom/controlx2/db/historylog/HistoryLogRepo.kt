package com.jwoglom.controlx2.db.historylog

import androidx.annotation.WorkerThread
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDateTime

class HistoryLogRepo(private val historyLogDao: HistoryLogDao) {
    fun getAll(pumpSid: Int): Flow<List<HistoryLogItem>> = historyLogDao.getAll(pumpSid)

    fun getLatest(pumpSid: Int): Flow<HistoryLogItem?> {
        return historyLogDao.getLatest(pumpSid)
    }

    fun allForType(pumpSid: Int, typeId: Int): Flow<List<HistoryLogItem>> {
        return historyLogDao.getAllForType(pumpSid, typeId)
    }

    fun getRange(pumpSid: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> {
        return historyLogDao.getRange(pumpSid, seqIdMin, seqIdMax)
    }

    fun getRangeForType(pumpSid: Int, typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> {
        return historyLogDao.getRangeForType(pumpSid, typeId, seqIdMin, seqIdMax)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(historyLogItem: HistoryLogItem) {
        Timber.d("HistoryLogRepo.insert(${historyLogItem.seqId}, ${historyLogItem.pumpSid}, ${historyLogItem.typeId})")
        historyLogDao.insert(historyLogItem)
    }

    suspend fun insert(historyLog: HistoryLog, pumpSid: Int) {
        val historyLogItem = HistoryLogItem(
            seqId = historyLog.sequenceNum,
            pumpSid = pumpSid,
            typeId = historyLog.typeId(),
            cargo = historyLog.cargo,
            addedTime = LocalDateTime.now()
        )
        insert(historyLogItem)
    }
}