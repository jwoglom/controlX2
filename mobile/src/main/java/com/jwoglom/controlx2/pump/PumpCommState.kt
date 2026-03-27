package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import java.time.Instant
import java.util.Collections

/**
 * Holds shared mutable state that both CommService and PumpCommHandler access.
 * Extracted from CommService to enable independent testing and later module extraction.
 */
class PumpCommState {
    val lastResponseMessage: MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>> =
        Collections.synchronizedMap(mutableMapOf())
    val debugPromptResponseCounts: MutableMap<Pair<Characteristic, Byte>, Int> =
        Collections.synchronizedMap(mutableMapOf())
    val historyLogCache: MutableMap<Long, HistoryLog> =
        Collections.synchronizedMap(mutableMapOf())
    var lastTimeSinceReset: TimeSinceResetResponse? = null

    /**
     * Checks if a debug-prompt response was expected for this characteristic+opCode,
     * and if so, consumes one count. Returns true if it was a debug-prompt response.
     */
    fun consumeDebugPromptResponse(characteristic: Characteristic, opCode: Byte): Boolean {
        val key = Pair(characteristic, opCode)
        synchronized(debugPromptResponseCounts) {
            val current = debugPromptResponseCounts[key] ?: return false
            if (current <= 1) {
                debugPromptResponseCounts.remove(key)
            } else {
                debugPromptResponseCounts[key] = current - 1
            }
            return true
        }
    }

    /**
     * Clears transient state on pump disconnect.
     */
    fun clearOnDisconnect() {
        lastResponseMessage.clear()
        debugPromptResponseCounts.clear()
        lastTimeSinceReset = null
    }
}
