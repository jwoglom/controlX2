package com.jwoglom.controlx2.db.historylog

import android.util.LruCache
import androidx.room.Entity
import androidx.room.Ignore
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import java.time.LocalDateTime
import java.time.ZoneId

val itemLruCache = LruCache<Pair<Long, Int>, HistoryLog>(500)

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
    val addedTime: LocalDateTime = LocalDateTime.now()
) {

    constructor(message: HistoryLog, addedTime: LocalDateTime = LocalDateTime.now()) : this(
        seqId=message.sequenceNum,
        pumpSid=0,
        typeId=message.typeId(),
        cargo=message.cargo,
        pumpTime=LocalDateTime.ofInstant(message.pumpTimeSecInstant, ZoneId.systemDefault()),
        addedTime=addedTime
    )

    @Ignore
    fun parse(): HistoryLog {
        itemLruCache[Pair(seqId, pumpSid)]?.let {
            return it
        }
        val cachedObj = HistoryLogParser.parse(cargo)
        itemLruCache.put(Pair(seqId, pumpSid), cachedObj)
        return cachedObj
    }
}
