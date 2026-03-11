package com.jwoglom.controlx2.shared

/**
 * Opaque token representing an active pump BLE session.
 * Tokens are monotonically increasing; a new token is issued on every
 * connect and [com.jwoglom.controlx2.pump.PumpSession] rejects commands
 * bearing an expired token.
 */
@JvmInline
value class PumpSessionToken(val id: Long)
