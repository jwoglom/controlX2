package com.jwoglom.controlx2.db.historylog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


class HistoryLogDummyDao(val data: MutableList<HistoryLogItem>) : HistoryLogDao {
    override fun getCount(pumpSid: Int): Flow<Long?> = flowOf(
        data.count { it.pumpSid == pumpSid }.toLong()
    )

    override fun getCount(pumpSid: Int, min: Long, max: Long): Flow<Long?> = flowOf(
        data.count { it.pumpSid == pumpSid && it.seqId in min..max }.toLong()
    )

    override fun getAllIds(pumpSid: Int, min: Long, max: Long): List<Long> =
        data.filter { it.pumpSid == pumpSid && it.seqId in min..max }
            .sortedBy { it.seqId }
            .map { it.seqId }

    override fun getAll(pumpSid: Int): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid }.sortedBy { it.seqId }
    )

    override fun getAllForType(pumpSid: Int, typeId: Int): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.typeId == typeId }
            .sortedBy { it.seqId }
    )

    override fun getLatestItemsForType(pumpSid: Int, typeId: Int, maxItems: Int): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.typeId == typeId }
            .sortedByDescending { it.seqId }
            .take(maxItems)
    )

    override fun getLatestItemsForTypes(pumpSid: Int, typeIds: List<Int>, maxItems: Int): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.typeId in typeIds }
            .sortedByDescending { it.seqId }
            .take(maxItems)
    )

    override fun getRange(pumpSid: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.seqId in seqIdMin..seqIdMax }
            .sortedBy { it.seqId }
    )

    override fun getRangeForType(pumpSid: Int, typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.typeId == typeId && it.seqId in seqIdMin..seqIdMax }
            .sortedBy { it.seqId }
    )

    override fun getLatest(pumpSid: Int): Flow<HistoryLogItem?> = flowOf(
        data.filter { it.pumpSid == pumpSid }.maxByOrNull { it.seqId }
    )

    override fun getLatestForType(pumpSid: Int, typeId: Int): Flow<HistoryLogItem?> = flowOf(
        data.filter { it.pumpSid == pumpSid && it.typeId == typeId }
            .maxByOrNull { it.seqId }
    )

    override fun getOldest(pumpSid: Int): Flow<HistoryLogItem?> = flowOf(
        data.filter { it.pumpSid == pumpSid }.minByOrNull { it.seqId }
    )

    override suspend fun insert(historyLogItem: HistoryLogItem) {
        // OnConflictStrategy.IGNORE behavior
        if (!data.any { it.pumpSid == historyLogItem.pumpSid && it.seqId == historyLogItem.seqId }) {
            data.add(historyLogItem)
        }
    }

    override suspend fun deleteAll() {
        data.clear()
    }
}