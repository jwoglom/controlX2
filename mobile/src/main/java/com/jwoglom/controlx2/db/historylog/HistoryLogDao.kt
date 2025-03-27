package com.jwoglom.controlx2.db.historylog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryLogDao {
    @Query("""
        SELECT COUNT(seqId) FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
    """)
    fun getCount(pumpSid: Int): Flow<Long?>

    @Query("""
        SELECT COUNT(seqId) FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId >= :min
        AND seqId <= :max
    """)
    fun getCount(pumpSid: Int, min: Long, max: Long): Flow<Long?>

    @Query("""
        SELECT DISTINCT seqId
        FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId >= :min
        AND seqId <= :max
        ORDER BY seqId ASC
    """)
    fun getAllIds(pumpSid: Int, min: Long, max: Long): List<Long>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        ORDER BY seqId ASC
    """)
    fun getAll(pumpSid: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId ASC
    """)
    fun getAllForType(pumpSid: Int, typeId: Int): Flow<List<HistoryLogItem>>


    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId DESC
        LIMIT :maxItems
    """)
    fun getLatestItemsForType(pumpSid: Int, typeId: Int, maxItems: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId IN(:typeIds)
        ORDER BY seqId DESC
        LIMIT :maxItems
    """)
    fun getLatestItemsForTypes(pumpSid: Int, typeIds: List<Int>, maxItems: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRange(pumpSid: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRangeForType(pumpSid: Int, typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId = (
            SELECT MAX(seqId) FROM $HistoryLogTable
            WHERE pumpSid = :pumpSid
        )
        LIMIT 1
    """)
    fun getLatest(pumpSid: Int): Flow<HistoryLogItem?>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId DESC
        LIMIT 1
    """)
    fun getLatestForType(pumpSid: Int, typeId: Int): Flow<HistoryLogItem?>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        ORDER BY seqId ASC
        LIMIT 1
    """)
    fun getOldest(pumpSid: Int): Flow<HistoryLogItem?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(historyLogItem: HistoryLogItem)

    @Query("DELETE FROM $HistoryLogTable")
    suspend fun deleteAll()
}