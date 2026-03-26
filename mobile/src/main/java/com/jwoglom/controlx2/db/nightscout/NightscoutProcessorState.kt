package com.jwoglom.controlx2.db.nightscout

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = NightscoutProcessorStateTable)
data class NightscoutProcessorState(
    @PrimaryKey
    val processorType: String,
    val lastProcessedSeqId: Long,
    val lastSuccessTime: LocalDateTime? = null
)

const val NightscoutProcessorStateTable = "nightscout_processor_state"
