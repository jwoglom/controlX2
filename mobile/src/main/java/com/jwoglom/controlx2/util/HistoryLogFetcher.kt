package com.jwoglom.controlx2.util

import android.content.Context
import android.util.LruCache
import com.jwoglom.controlx2.CommService
import com.jwoglom.controlx2.Prefs
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.Long.max
import java.lang.Long.min

const val InitialHistoryLogCount = 5000
const val FetchGroupTimeoutMs = 2500

val triggerRangeMutex = Mutex()
val statusResponseLock = Mutex()
val streamResponseLock = Mutex()

class HistoryLogFetcher(val context: Context, val pump: TandemPump, val peripheral: BluetoothPeripheral, val pumpSid: Int) {
    private var recentSeqIds = LruCache<Long, Long>(256)
    private var latestSeqId: Long = 0

    private val historyLogRepo by lazy { (context as CommService).historyLogRepo }

    private fun request(start: Long, count: Int) {
        pump.sendCommand(peripheral, HistoryLogRequest(start, count))
    }

    private suspend fun triggerRange(startLog: Long, endLog: Long) {
        triggerRangeMutex.withLock {
            Timber.i("HistoryLogFetcher.triggerRange<lock>")
            triggerRangeInternal(startLog, endLog)
            Timber.i("HistoryLogFetcher.triggerRange<unlock>")
        }
    }

    private suspend fun triggerRangeInternal(
        startLog: Long,
        endLog: Long
    )  {
        Timber.i("HistoryLogFetcher.triggerRange($startLog, $endLog)")
        val chunkSize = 32
        var num = 1
        var totalNums = 1
        for (i in startLog..endLog step chunkSize.toLong()) {
            totalNums++
        }

        // Iterate backwards from endLog to startLog to fetch newest chunks first
        var i = endLog
        while (i >= startLog) {
            val count = min(chunkSize.toLong(), i - startLog + 1).toInt()
            val startI = i - count + 1
            Timber.i("HistoryLogFetcher.triggerRangeStart $startI - $i ($count)")

            // HistoryLogRequest returns items backward from the start ID,
            // so we request from the END of the range to get all items in [startI, i]
            request(i, count)

            withContext(Dispatchers.IO) {
                Thread.sleep(500)
            }

            var waitTimeMs = 0
            while (true) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(250)
                }
                waitTimeMs += 250
                val dbCount = historyLogRepo.getCount(pumpSid, startI, i).firstOrNull() ?: 0
                Timber.d("HistoryLogFetcher dbCount=${dbCount} / count=$count")
                if (dbCount >= count) {
                    break
                }
                if (waitTimeMs >= FetchGroupTimeoutMs) {
                    Timber.i("HistoryLogFetcher subset $startI - $i timed out: latest=$latestSeqId waitTime=$waitTimeMs")
                    break
                }
            }
            Timber.i("HistoryLogFetcher.triggerRangeDone $startI - $i: latest=$latestSeqId")
            num++
            i = startI - 1
        }
    }
    suspend fun onStatusResponse(message: HistoryLogStatusResponse, scope: CoroutineScope) {
        if (!Prefs(context).autoFetchHistoryLogs()) return;
        statusResponseLock.withLock {
            Timber.i("HistoryLogFetcher.onStatusResponse<lock>")
            onStatusResponseInternal(message, scope)
            Timber.i("HistoryLogFetcher.onStatusResponse<unlock>")
        }
    }

    private suspend fun onStatusResponseInternal(message: HistoryLogStatusResponse, scope: CoroutineScope) {
        Timber.i("HistoryLogFetcher.onStatusResponse")
        val dbLatest = historyLogRepo.getLatest(pumpSid).firstOrNull()
        val dbLatestId = dbLatest?.seqId
        val dbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0
        
        val catchupThreshold = message.lastSequenceNum - InitialHistoryLogCount
        var startId = when {
            dbLatestId != null && dbLatestId >= catchupThreshold && dbLatestId <= message.lastSequenceNum -> dbLatestId
            else -> catchupThreshold
        }

        // don't try and fetch earlier than the first available seq number on the pump
        // (this is not always 0; after a pump has been used for a long period old
        // data is retentioned away and deleted while keeping id numbers consistent)
        startId = max(startId, message.firstSequenceNum)
        // ...or the latest sequence num
        startId = min(startId, message.lastSequenceNum)

        // should not be possible
        if (startId < 0) {
            startId = 0
        }

        val diffCount = message.lastSequenceNum - startId

        Timber.i("HistoryLogFetcher.onStatusResponse: db: $dbLatestId pump: ${message.lastSequenceNum} (diff: $diffCount)")

        val allIds = historyLogRepo.getAllIds(pumpSid, startId, message.lastSequenceNum)
        val missingIds = getMissingIds(allIds, startId, message.lastSequenceNum)
        var missingIdsTotal: Long = 0
        missingIds.forEach {
            missingIdsTotal += (it.last - it.first + 1)
        }

        
        Timber.i("HistoryLogFetcher.onStatusResponse: missingIds=${missingIdsTotal} $missingIds")

        scope.launch {
            Timber.i("HistoryLogFetcher.onStatusResponse: launching ranges: $missingIds (dbCount=$dbCount)")
            missingIds.reversed().forEach {
                triggerRange(it.first, it.last)
            }
            Timber.i("HistoryLogFetcher.onStatusResponse: completed, fetched $missingIdsTotal")
            val finalDbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0
            Timber.i("HistoryLogFetcher.onStatusResponse: completed, ${finalDbCount - dbCount} actual: $dbCount -> $finalDbCount")
        }
    }

    suspend fun onStreamResponse(log: HistoryLog) {
        streamResponseLock.withLock {
            Timber.d("HistoryLogFetcher onStreamResponse ${log.sequenceNum}")
            historyLogRepo.insert(log, pumpSid)
            latestSeqId = max(latestSeqId, log.sequenceNum)
            recentSeqIds.put(log.sequenceNum, log.sequenceNum)
        }
    }
}

fun getMissingIds(present: List<Long>, min: Long, max: Long): List<LongRange> {
    if (present.isEmpty()) {
        return listOf(min..max)
    }
    val missing = mutableListOf<LongRange>()
    if (present[0] > min) {
        missing.add(min until present[0])
    }
    var prev: Long = present[0]
    for ((k, i) in present.withIndex()) {
        if (k == 0) continue
        if (i > prev + 1) {
            missing.add(prev+1..i-1)
        }
        prev = i
    }
    if (prev < max) {
        missing.add(prev+1..max)
    }
    return missing
}