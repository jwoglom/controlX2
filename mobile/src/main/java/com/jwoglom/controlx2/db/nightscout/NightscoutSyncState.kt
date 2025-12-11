package com.jwoglom.controlx2.db.nightscout

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

const val NightscoutSyncStateTable = "nightscout_sync_state"

@Entity(tableName = NightscoutSyncStateTable)
data class NightscoutSyncState(
    @PrimaryKey
    val id: Int = 1,  // Single row for global state

    // Last sequence ID successfully processed and uploaded
    val lastProcessedSeqId: Long = 0L,

    // Last sync timestamp
    val lastSyncTime: LocalDateTime? = null,

    // When Nightscout sync was first enabled
    val firstEnabledTime: LocalDateTime? = null,

    // Configured lookback period in hours (default 24)
    val lookbackHours: Int = 24,

    // Optional: manual date range for retroactive sync
    val retroactiveStartTime: LocalDateTime? = null,
    val retroactiveEndTime: LocalDateTime? = null
)
