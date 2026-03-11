package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.PumpSessionToken
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Session-scoped gateway for pump commands, owned by PumpCommHandler.
 *
 * External code receives a [PumpSessionToken] when the pump connects and
 * passes it with every command. If the token no longer matches the active
 * connection (because the pump disconnected and possibly reconnected),
 * the command is silently dropped with a log warning. This eliminates the
 * class of bugs where callers hold stale TandemPump / BluetoothPeripheral
 * references.
 */
class PumpSession {

    data class ActiveConnection(
        val token: PumpSessionToken,
        val pump: TandemPump,
        val peripheral: BluetoothPeripheral
    )

    @Volatile
    private var activeConnection: ActiveConnection? = null

    private val tokenCounter = AtomicLong(0)

    fun open(pump: TandemPump, peripheral: BluetoothPeripheral): PumpSessionToken {
        val token = PumpSessionToken(tokenCounter.incrementAndGet())
        activeConnection = ActiveConnection(token, pump, peripheral)
        Timber.i("PumpSession: opened $token")
        return token
    }

    fun close() {
        val old = activeConnection
        activeConnection = null
        Timber.i("PumpSession: closed ${old?.token}")
    }

    fun isActive(token: PumpSessionToken): Boolean {
        return activeConnection?.token == token
    }

    fun sendCommand(token: PumpSessionToken, message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
        val conn = activeConnection
        if (conn == null || conn.token != token) {
            Timber.w("PumpSession: stale token $token (current=${conn?.token}), dropping $message")
            return false
        }
        conn.pump.sendCommand(conn.peripheral, message)
        return true
    }

    fun sendHistoryLogRequest(token: PumpSessionToken, startSeqId: Long, count: Int): Boolean {
        return sendCommand(token, HistoryLogRequest(startSeqId, count))
    }

    fun isPumpReady(): Boolean {
        return activeConnection != null
    }
}
