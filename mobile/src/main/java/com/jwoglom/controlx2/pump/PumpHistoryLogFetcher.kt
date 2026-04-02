package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.CoroutineScope

/**
 * Interface for history log fetching, abstracting the concrete HistoryLogFetcher
 * so PumpCommHandler doesn't depend on mobile-app DB classes.
 */
interface PumpHistoryLogFetcher {
    suspend fun onStatusResponse(message: HistoryLogStatusResponse, scope: CoroutineScope)
    suspend fun onStreamResponse(log: HistoryLog)
    fun cancel()
}
