package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.PumpSessionToken
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single pump BLE connection. Created when the pump connects,
 * closed when it disconnects. Implements [AutoCloseable] so the lifecycle
 * is explicit and idiomatic.
 *
 * All pump commands flow through [sendCommand], which enforces a
 * [CommandRateLimiter] (token-bucket at the configured base/burst rates)
 * and honours temporary pauses triggered by qualifying events.
 *
 * Once closed, every [sendCommand] call is silently dropped with a log
 * warning. Callers that hold a reference to a stale session can never
 * accidentally reach the underlying BLE objects.
 */
class PumpSession private constructor(
    val token: PumpSessionToken,
    private val pump: TandemPump,
    private val peripheral: BluetoothPeripheral,
    val rateLimitConfig: RateLimitConfig
) : AutoCloseable {

    private val rateLimiter = CommandRateLimiter(rateLimitConfig)

    @Volatile
    private var closed = false

    val isActive: Boolean get() = !closed

    suspend fun sendCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
        if (closed) {
            Timber.w("PumpSession($token): closed, dropping $message")
            return false
        }
        rateLimiter.acquire()
        if (closed) {
            Timber.w("PumpSession($token): closed during rate-limit wait, dropping $message")
            return false
        }
        pump.sendCommand(peripheral, message)
        return true
    }

    /**
     * Immediately pauses all command sends for [durationMs].
     * In-flight [sendCommand] calls will block until the pause expires.
     */
    fun pauseSends(durationMs: Long) {
        Timber.i("PumpSession($token): pausing sends for ${durationMs}ms")
        rateLimiter.pause(durationMs)
    }

    override fun close() {
        closed = true
        Timber.i("PumpSession: closed $token")
    }

    override fun toString(): String = "PumpSession(token=$token, isActive=$isActive)"

    companion object {
        private val tokenCounter = AtomicLong(0)

        fun open(
            pump: TandemPump,
            peripheral: BluetoothPeripheral,
            rateLimitConfig: RateLimitConfig = RateLimitConfig()
        ): PumpSession {
            val token = PumpSessionToken(tokenCounter.incrementAndGet())
            Timber.i("PumpSession: opened $token")
            return PumpSession(token, pump, peripheral, rateLimitConfig)
        }
    }
}
