package com.jwoglom.controlx2.db.nightscout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface NightscoutProcessorStateDao {
    @Query("SELECT * FROM $NightscoutProcessorStateTable WHERE processorType = :processorType")
    suspend fun getProcessorState(processorType: String): NightscoutProcessorState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NightscoutProcessorState)

    @Query("SELECT * FROM $NightscoutProcessorStateTable")
    suspend fun getAllStates(): List<NightscoutProcessorState>
}
