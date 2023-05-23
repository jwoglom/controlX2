package com.jwoglom.controlx2.db.historylog

import androidx.room.Entity
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import java.time.LocalDateTime

const val HistoryLogTable = "pumpdata_historylog"

@Entity(
    tableName = HistoryLogTable,
    primaryKeys = ["seqId", "pumpSid"]
)
class HistoryLogItem(
    val seqId: Long,
    // pumpSid is the short pump serial: the final 3 digits of the serial number from the BT device name
    val pumpSid: Int = 0,
    val typeId: Int,
    val cargo: ByteArray,
    val pumpTime: LocalDateTime,
    val addedTime: LocalDateTime
) {
    fun parse(): HistoryLog {
        return HistoryLogParser.parse(cargo)
    }
}
