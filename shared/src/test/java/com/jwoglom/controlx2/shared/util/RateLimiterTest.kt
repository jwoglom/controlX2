package com.jwoglom.controlx2.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class RateLimiterTest {
    @Test
    fun tryAcquire_blocksWithinWindow() {
        val now = AtomicLong(0L)
        val limiter = RateLimiter<String>(Duration.ofMillis(500)) { now.get() }

        assertTrue(limiter.tryAcquire("pump"))
        assertFalse(limiter.tryAcquire("pump"))

        now.set(Duration.ofMillis(499).toNanos())
        assertFalse(limiter.tryAcquire("pump"))

        now.set(Duration.ofMillis(500).toNanos())
        assertTrue(limiter.tryAcquire("pump"))
    }

    @Test
    fun tryAcquire_isKeyed() {
        val now = AtomicLong(0L)
        val limiter = RateLimiter<String>(Duration.ofSeconds(1)) { now.get() }

        assertTrue(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
        assertFalse(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("b"))
    }

    @Test
    fun remaining_reportsZeroWhenAllowed() {
        val now = AtomicLong(0L)
        val limiter = RateLimiter<String>(Duration.ofMillis(500)) { now.get() }

        assertEquals(Duration.ZERO, limiter.remaining("pump"))

        assertTrue(limiter.tryAcquire("pump"))
        now.set(Duration.ofMillis(200).toNanos())
        assertEquals(Duration.ofMillis(300), limiter.remaining("pump"))

        now.set(Duration.ofMillis(500).toNanos())
        assertEquals(Duration.ZERO, limiter.remaining("pump"))
    }

    @Test
    fun runIfAllowed_executesOnlyWhenAllowed() {
        val now = AtomicLong(0L)
        val limiter = RateLimiter<String>(Duration.ofSeconds(1)) { now.get() }
        var runs = 0

        assertTrue(limiter.runIfAllowed("pump") { runs += 1 })
        assertFalse(limiter.runIfAllowed("pump") { runs += 1 })
        assertEquals(1, runs)

        now.set(Duration.ofSeconds(1).toNanos())
        assertTrue(limiter.runIfAllowed("pump") { runs += 1 })
        assertEquals(2, runs)
    }

    @Test
    fun reset_clearsKeyWindow() {
        val now = AtomicLong(0L)
        val limiter = RateLimiter<String>(Duration.ofSeconds(1)) { now.get() }

        assertTrue(limiter.tryAcquire("pump"))
        assertFalse(limiter.tryAcquire("pump"))

        limiter.reset("pump")
        assertTrue(limiter.tryAcquire("pump"))
    }
}
