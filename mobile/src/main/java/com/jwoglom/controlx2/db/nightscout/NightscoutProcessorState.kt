package com.jwoglom.controlx2.db.nightscout

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

const val NightscoutProcessorStateTable = "nightscout_processor_state"

@Entity(tableName = NightscoutProcessorStateTable)
data class NightscoutProcessorState(
    @PrimaryKey val processorType: String, // e.g., "BOLUS", "DEVICE_STATUS"
    val lastProcessedSeqId: Long,
    val lastSuccessTime: LocalDateTime?
)
