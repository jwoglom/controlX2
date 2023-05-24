package com.jwoglom.controlx2.util

import android.content.Context
import android.util.LruCache
import com.jwoglom.controlx2.CommService
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

const val InitialHistoryLogCount = 5000
const val FetchGroupTimeoutMs = 7000

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
        var num = 1
        var totalNums = 1
        for (i in startLog..endLog step 256) {
            totalNums++
        }
        for (i in startLog..endLog step 256) {
            val count = if (i+255 > endLog) (endLog - i + 1).toInt() else 255
            val endI = i+count-1
            Timber.i("HistoryLogFetcher.triggerRangeStart $i - $endI ($count)")

            request(i, count)

            var waitTimeMs = 0
            while (true) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(250)
                }
                waitTimeMs += 250
                val dbCount = historyLogRepo.getCount(pumpSid, i, endI).firstOrNull() ?: 0
                Timber.d("HistoryLogFetcher dbCount=${dbCount} / count=$count")
                if (dbCount >= count) {
                    break
                }
                if (waitTimeMs >= FetchGroupTimeoutMs) {
                    Timber.i("HistoryLogFetcher subset $i - $endI timed out: latest=$latestSeqId waitTime=$waitTimeMs")
                    break
                }
            }
            Timber.i("HistoryLogFetcher.triggerRangeDone $i - $endI: latest=$latestSeqId")
            num++
        }
    }
    suspend fun onStatusResponse(message: HistoryLogStatusResponse, scope: CoroutineScope) {
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

        var startId = dbLatestId ?: (message.lastSequenceNum - InitialHistoryLogCount)
        if ((dbLatestId ?: 0) < message.lastSequenceNum - InitialHistoryLogCount) {
            startId = message.lastSequenceNum - InitialHistoryLogCount
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
            missingIds.forEach {
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