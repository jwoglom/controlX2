package com.jwoglom.controlx2.util

import android.content.Context
import com.jwoglom.controlx2.CommService
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.lang.Long.max

class HistoryLogFetcher(val context: Context, val pump: TandemPump, val peripheral: BluetoothPeripheral, val pumpSid: Int) {
    private var latestSeqId: Long = 0

    private val historyLogRepo by lazy { (context as CommService).historyLogRepo }

    private fun request(start: Long, count: Int) {
        pump.sendCommand(peripheral, HistoryLogRequest(start, count))
    }

    private fun triggerRange(
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
            val count = if (i+255 > endLog) (endLog - i).toInt() else 255
            Timber.i("HistoryLogFetcher.triggerRangeStart $i - ${i+count}")
            request(i, count)

            var waitTimeMs = 0
            while (latestSeqId < i+count) {
                Thread.sleep(100)
                waitTimeMs += 100
                if (waitTimeMs > 7000) {
                    Timber.i("HistoryLogRequest subset $i - ${i+count} timed out: latest=$latestSeqId waitTime=$waitTimeMs")
                    break
                }
            }
            Timber.i("HistoryLogFetcher.triggerRangeDone $i - ${i+count}: latest=$latestSeqId")
            num++
        }
    }

    suspend fun onStatusResponse(message: HistoryLogStatusResponse) {
        Timber.i("HistoryLogFetcher.onStatusResponse")
        val dbLatest = historyLogRepo.getLatest(pumpSid).firstOrNull()
        Timber.i("HistoryLogFetcher.onStatusResponse dbLatest=$dbLatest")
        val dbLatestId = dbLatest?.seqId ?: (message.lastSequenceNum - 5000)
        Timber.i("HistoryLogFetcher.onStatusResponse dbLatestId=$dbLatestId")
        // TODO: re-check any missed numbers
        val missingCount = message.lastSequenceNum - dbLatestId
        Timber.i("HistoryLogFetcher.onStatusResponse missingCount=$missingCount")
        Timber.i("HistoryLogFetcher.onStatusResponse message=${message.lastSequenceNum}")

        Timber.i("HistoryLogFetcher.onStatusResponse: db: $dbLatestId pump: ${message.lastSequenceNum} missingCount: $missingCount")

        if (missingCount > 0) {
            triggerRange(dbLatestId, message.lastSequenceNum)
        }

    }

    suspend fun onStreamResponse(log: HistoryLog) {
        historyLogRepo.insert(log, pumpSid)
        latestSeqId = max(latestSeqId, log.sequenceNum)
    }
}