package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.PumpSessionToken
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single pump BLE connection. Created when the pump connects,
 * closed when it disconnects. Implements [AutoCloseable] so the lifecycle
 * is explicit and idiomatic.
 *
 * Once closed, every [sendCommand] / [sendHistoryLogRequest] call is
 * silently dropped with a log warning. Callers that hold a reference to a
 * stale session can never accidentally reach the underlying BLE objects.
 */
class PumpSession private constructor(
    val token: PumpSessionToken,
    private val pump: TandemPump,
    private val peripheral: BluetoothPeripheral
) : AutoCloseable {

    @Volatile
    private var closed = false

    val isActive: Boolean get() = !closed

    fun sendCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
        if (closed) {
            Timber.w("PumpSession($token): closed, dropping $message")
            return false
        }
        pump.sendCommand(peripheral, message)
        return true
    }

    fun sendHistoryLogRequest(startSeqId: Long, count: Int): Boolean {
        return sendCommand(HistoryLogRequest(startSeqId, count))
    }

    override fun close() {
        closed = true
        Timber.i("PumpSession: closed $token")
    }

    override fun toString(): String = "PumpSession(token=$token, isActive=$isActive)"

    companion object {
        private val tokenCounter = AtomicLong(0)

        fun open(pump: TandemPump, peripheral: BluetoothPeripheral): PumpSession {
            val token = PumpSessionToken(tokenCounter.incrementAndGet())
            Timber.i("PumpSession: opened $token")
            return PumpSession(token, pump, peripheral)
        }
    }
}
