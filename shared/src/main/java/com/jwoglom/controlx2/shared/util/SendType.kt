package com.jwoglom.controlx2.shared.util

/**
 * Controls how an outgoing pump command interacts with the 30-second response
 * cache maintained in PumpCommHandler.
 *
 * Use the smallest hammer that does the job:
 * - Prefer [STANDARD] for periodic refreshes and lifecycle/onStart fetches.
 * - Use [BUST_CACHE] only when the user explicitly asked for fresh data
 *   (pull-to-refresh) or immediately after a write that invalidates known state.
 * - Use [CACHED] inside retry loops (e.g. waitForLoaded) where a fresh read just
 *   happened and we are willing to accept the cached response if available.
 * - [DEBUG_PROMPT] is reserved for the developer Debug screen.
 */
enum class SendType(val slug: String) {
    /**
     * Sends the command to the pump normally. Does not read or clear the cache
     * on the request side; the response will populate the cache for subsequent
     * CACHED reads. Default choice for periodic / lifecycle refreshes.
     */
    STANDARD("commands"),

    /**
     * Removes any cached entry for this opcode and then sends to the pump.
     * Guarantees the next response is fresh from the pump. Use for explicit
     * user-initiated refreshes and immediately after mutating commands where
     * we know the cached value is now stale.
     */
    BUST_CACHE("commands-bust-cache"),

    /**
     * Returns the cached response immediately if it is <= 30 seconds old.
     * Otherwise falls through to a normal pump send. Intended for retry loops
     * (waitForLoaded) where a real fetch was issued moments ago and we want to
     * pick up the response without re-sending if it has already arrived.
     */
    CACHED("cached-commands"),

    /**
     * Routes the command through the developer debug path. Responses are
     * tracked separately for the Debug screen. Do not use from production UI.
     */
    DEBUG_PROMPT("debug-commands"),
}
