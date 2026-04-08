package com.jwoglom.controlx2.db.nightscout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface NightscoutSyncStateDao {
    @Query("SELECT * FROM $NightscoutSyncStateTable WHERE id = 1")
    suspend fun getState(): NightscoutSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NightscoutSyncState)

    @Query("UPDATE $NightscoutSyncStateTable SET lastProcessedSeqId = :seqId, lastSyncTime = :time WHERE id = 1")
    suspend fun updateLastProcessed(seqId: Long, time: LocalDateTime)

    @Query("UPDATE $NightscoutSyncStateTable SET lookbackHours = :hours WHERE id = 1")
    suspend fun updateLookbackHours(hours: Int)

    @Query("UPDATE $NightscoutSyncStateTable SET retroactiveStartTime = :start, retroactiveEndTime = :end WHERE id = 1")
    suspend fun setRetroactiveRange(start: LocalDateTime?, end: LocalDateTime?)

    @Query("DELETE FROM $NightscoutSyncStateTable")
    suspend fun deleteAll()
}
