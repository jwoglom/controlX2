package com.jwoglom.controlx2.pump

import timber.log.Timber

/**
 * Token-bucket rate limiter with support for temporary pauses.
 *
 * [acquire] blocks the calling thread until a token is available and any
 * active pause has elapsed. This is intentional: pump commands already
 * execute on a dedicated handler thread that processes them sequentially.
 */
class CommandRateLimiter(private val config: RateLimitConfig) {

    private var tokens: Double = config.burstRps.toDouble()
    private var lastRefillNanos: Long = System.nanoTime()

    @Volatile
    private var pauseUntilNanos: Long = 0L

    /**
     * Blocks until a token is available and any pause has elapsed.
     */
    @Synchronized
    fun acquire() {
        waitForPause()
        refill()

        while (tokens < 1.0) {
            val waitMs = ((1.0 - tokens) / config.baseRps * 1000).toLong().coerceAtLeast(1)
            try { Thread.sleep(waitMs) } catch (_: InterruptedException) { return }
            waitForPause()
            refill()
        }

        tokens -= 1.0
    }

    /**
     * Immediately pauses all sends for [durationMs]. Subsequent [acquire]
     * calls will block until the pause expires.
     */
    fun pause(durationMs: Long) {
        val until = System.nanoTime() + durationMs * 1_000_000
        pauseUntilNanos = until
        Timber.i("CommandRateLimiter: paused for ${durationMs}ms")
    }

    private fun waitForPause() {
        val until = pauseUntilNanos
        val remaining = until - System.nanoTime()
        if (remaining > 0) {
            val ms = remaining / 1_000_000
            Timber.i("CommandRateLimiter: waiting ${ms}ms for pause to expire")
            try { Thread.sleep(ms) } catch (_: InterruptedException) {}
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0
        tokens = (tokens + elapsedSec * config.baseRps).coerceAtMost(config.burstRps.toDouble())
        lastRefillNanos = now
    }
}
