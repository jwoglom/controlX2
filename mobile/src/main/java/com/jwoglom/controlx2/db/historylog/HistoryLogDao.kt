package com.jwoglom.controlx2.db.historylog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryLogDao {
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
        ORDER BY seqId DESC
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
}