package com.jwoglom.controlx2.db.nightscout

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

const val NightscoutProcessorStateTable = "nightscout_processor_state"

@Entity(tableName = NightscoutProcessorStateTable)
data class NightscoutProcessorState(
    @PrimaryKey
    val processorType: String,  // e.g. "BOLUS", "CGM_READING"

    val lastProcessedSeqId: Long = 0L,

    val lastSuccessTime: LocalDateTime? = null
)
