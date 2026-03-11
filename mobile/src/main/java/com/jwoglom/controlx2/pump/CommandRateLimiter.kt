package com.jwoglom.controlx2.pump

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Token-bucket rate limiter with support for temporary pauses.
 *
 * [acquire] is a suspending function that waits (without blocking a thread)
 * until a token is available and any active pause has elapsed. Concurrent
 * callers are serialised by a [Mutex].
 */
class CommandRateLimiter(private val config: RateLimitConfig) {

    private val mutex = Mutex()
    private var tokens: Double = config.burstRps.toDouble()
    private var lastRefillNanos: Long = System.nanoTime()

    @Volatile
    private var pauseUntilNanos: Long = 0L

    /**
     * Suspends until a token is available and any pause has elapsed.
     */
    suspend fun acquire() {
        mutex.withLock {
            awaitPause()
            refill()

            while (tokens < 1.0) {
                val waitMs = ((1.0 - tokens) / config.baseRps * 1000).toLong().coerceAtLeast(1)
                delay(waitMs)
                awaitPause()
                refill()
            }

            tokens -= 1.0
        }
    }

    /**
     * Immediately pauses all sends for [durationMs]. Subsequent [acquire]
     * calls will suspend until the pause expires.
     */
    fun pause(durationMs: Long) {
        pauseUntilNanos = System.nanoTime() + durationMs * 1_000_000
        Timber.i("CommandRateLimiter: paused for ${durationMs}ms")
    }

    private suspend fun awaitPause() {
        val remaining = pauseUntilNanos - System.nanoTime()
        if (remaining > 0) {
            val ms = remaining / 1_000_000
            Timber.i("CommandRateLimiter: waiting ${ms}ms for pause to expire")
            delay(ms)
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0
        tokens = (tokens + elapsedSec * config.baseRps).coerceAtMost(config.burstRps.toDouble())
        lastRefillNanos = now
    }
}
