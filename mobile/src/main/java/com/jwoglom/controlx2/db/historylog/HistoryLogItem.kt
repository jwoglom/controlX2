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
    // Raw pump clock: local wall-clock seconds since 2008-01-01 00:00:00 as reported by the pump.
    val pumpTimeSec: Long = 0,
    @Deprecated("Replaced with pumpTimeSec. This field has ambiguous timezone encoding.")
    val pumpTime: LocalDateTime,
    val addedTime: LocalDateTime = LocalDateTime.now()
) {

    constructor(message: HistoryLog, addedTime: LocalDateTime = LocalDateTime.now()) : this(
        seqId=message.sequenceNum,
        pumpSid=0,
        typeId=message.typeId(),
        cargo=message.cargo,
        pumpTimeSec=message.pumpTimeSec,
        pumpTime=LocalDateTime.ofInstant(message.pumpTimeSecInstant, ZoneId.systemDefault()),
        addedTime=addedTime
    )

    @Ignore
    fun parse(): HistoryLog {
        itemLruCache[Pair(seqId, pumpSid)]?.let {
            return it
        }
        val cachedObj = try {
            val historyLogClass = HistoryLogParser.LOG_MESSAGE_IDS[typeId]
            if (historyLogClass != null) {
                val historyLog = historyLogClass.getDeclaredConstructor().newInstance()
                historyLog.parse(cargo)
                historyLog
            } else {
                HistoryLogParser.parse(cargo)
            }
        } catch (_: Exception) {
            HistoryLogParser.parse(cargo)
        }
        itemLruCache.put(Pair(seqId, pumpSid), cachedObj)
        return cachedObj
    }
}
