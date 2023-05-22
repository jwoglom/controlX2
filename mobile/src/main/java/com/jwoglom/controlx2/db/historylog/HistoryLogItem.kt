package com.jwoglom.controlx2.db.historylog

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import java.time.Instant

const val HistoryLogTable = "pumpdata_historylog"

@Entity(
    tableName = HistoryLogTable,
    primaryKeys = ["seqId", "pumpSerial"]
)
class HistoryLogItem(
    @PrimaryKey val seqId: Long,
    // pumpSerial is not the full serial, it is just the final 3 digits of the serial number
    // from the bluetooth device name, since that allows DB initialization to occur earlier.
    @PrimaryKey val pumpSerial: Int = 0,
    val typeId: Int,
    val cargo: ByteArray,
    val addedTime: Instant
) {
    fun parse(): HistoryLog {
        return HistoryLogParser.parse(cargo)
    }
}
