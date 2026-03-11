package com.jwoglom.controlx2.pump

/**
 * Configuration for pump command rate limiting.
 *
 * @property baseRps   Sustained send rate (tokens refilled per second).
 * @property burstRps  Maximum burst capacity (token bucket size).
 * @property commSuspendedPauseMs  How long to pause all sends when a
 *           PUMP_COMMUNICATIONS_SUSPENDED qualifying event is received.
 */
data class RateLimitConfig(
    val baseRps: Double = 10.0,
    val burstRps: Int = 15,
    val commSuspendedPauseMs: Long = 5_000
)
