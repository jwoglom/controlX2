package com.jwoglom.controlx2.db.historylog

import androidx.annotation.WorkerThread
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryLogRepo(private val historyLogDao: HistoryLogDao) {
    fun getCount(pumpSid: Int): Flow<Long?> = historyLogDao.getCount(pumpSid)
    fun getCount(pumpSid: Int, min: Long, max: Long): Flow<Long?> = historyLogDao.getCount(pumpSid, min, max)
    fun getCountAboveSeqId(pumpSid: Int, minSeqId: Long): Flow<Long?> = historyLogDao.getCountAboveSeqId(pumpSid, minSeqId)
    fun getAllIds(pumpSid: Int, min: Long, max: Long): List<Long> = historyLogDao.getAllIds(pumpSid, min, max)
    fun getAll(pumpSid: Int): Flow<List<HistoryLogItem>> = historyLogDao.getAll(pumpSid)

    fun getOldest(pumpSid: Int): Flow<HistoryLogItem?> {
        return historyLogDao.getOldest(pumpSid)
    }

    fun getLatest(pumpSid: Int): Flow<HistoryLogItem?> {
        return historyLogDao.getLatest(pumpSid)
    }

    fun getLatestForType(pumpSid: Int, typeId: Int): Flow<HistoryLogItem?> {
        return historyLogDao.getLatestForType(pumpSid, typeId)
    }

    fun getTypeStats(pumpSid: Int): List<HistoryLogTypeStats> {
        return historyLogDao.getTypeStats(pumpSid)
    }

    fun getLatestItemsForType(pumpSid: Int, typeId: Int, maxItems: Int): Flow<List<HistoryLogItem>> {
        return historyLogDao.getLatestItemsForType(pumpSid, typeId, maxItems)
    }

    fun getLatestItemsForTypes(pumpSid: Int, typeIds: List<Int>, maxItems: Int): Flow<List<HistoryLogItem>> {
        return historyLogDao.getLatestItemsForTypes(pumpSid, typeIds, maxItems)
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
            pumpTime = LocalDateTime.ofInstant(historyLog.pumpTimeSecInstant, ZoneId.systemDefault()),
            addedTime = LocalDateTime.now()
        )
        insert(historyLogItem)
    }
}