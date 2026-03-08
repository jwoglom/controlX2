package com.jwoglom.controlx2.shared.util

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple keyed fixed-window rate limiter.
 *
 * Each key may pass once per [minInterval]. Calls within the interval are denied.
 */
class RateLimiter<K>(
    minInterval: Duration,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val minIntervalNanos = minInterval.toNanos().coerceAtLeast(0L)
    private val lastAllowedNanos = ConcurrentHashMap<K, Long>()

    /**
     * Attempts to acquire permission for [key].
     *
     * @return true if allowed now, false if still within the rate-limit window.
     */
    fun tryAcquire(key: K): Boolean {
        val now = nowNanos()
        if (minIntervalNanos == 0L) {
            lastAllowedNanos[key] = now
            return true
        }

        while (true) {
            val previous = lastAllowedNanos[key]
            if (previous == null) {
                if (lastAllowedNanos.putIfAbsent(key, now) == null) {
                    return true
                }
                continue
            }

            if (now - previous < minIntervalNanos) {
                return false
            }

            if (lastAllowedNanos.replace(key, previous, now)) {
                return true
            }
        }
    }

    /**
     * Returns how long until [key] can be acquired again.
     */
    fun remaining(key: K): Duration {
        val previous = lastAllowedNanos[key] ?: return Duration.ZERO
        val remainingNanos = minIntervalNanos - (nowNanos() - previous)
        return if (remainingNanos <= 0L) Duration.ZERO else Duration.ofNanos(remainingNanos)
    }

    /**
     * Runs [block] only if [key] is currently allowed.
     *
     * @return true if [block] was executed.
     */
    inline fun runIfAllowed(key: K, block: () -> Unit): Boolean {
        if (!tryAcquire(key)) {
            return false
        }
        block()
        return true
    }

    fun reset(key: K) {
        lastAllowedNanos.remove(key)
    }

    fun resetAll() {
        lastAllowedNanos.clear()
    }
}
