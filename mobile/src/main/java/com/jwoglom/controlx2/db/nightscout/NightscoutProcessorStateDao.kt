package com.jwoglom.controlx2.db.nightscout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NightscoutProcessorStateDao {
    @Query("SELECT * FROM $NightscoutProcessorStateTable WHERE processorType = :type")
    suspend fun getForProcessor(type: String): NightscoutProcessorState?

    @Query("SELECT * FROM $NightscoutProcessorStateTable")
    suspend fun getAll(): List<NightscoutProcessorState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NightscoutProcessorState)

    @Query("SELECT MIN(lastProcessedSeqId) FROM $NightscoutProcessorStateTable")
    suspend fun getMinSeqId(): Long?

    @Query("DELETE FROM $NightscoutProcessorStateTable")
    suspend fun deleteAll()
}
