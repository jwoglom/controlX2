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
        WHERE pumpSerial = :pumpSerial
        ORDER BY seqId ASC
    """)
    fun getAll(pumpSerial: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSerial = :pumpSerial
        AND typeId = :typeId
        ORDER BY seqId ASC
    """)
    fun getAllForType(pumpSerial: Int, typeId: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSerial = :pumpSerial
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRange(pumpSerial: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSerial = :pumpSerial
        AND typeId = :typeId
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRangeForType(pumpSerial: Int, typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(historyLogItem: HistoryLogItem)
}