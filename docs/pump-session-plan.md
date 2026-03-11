# PumpSession – Implementation Plan

## Problem Statement

Today, `HistoryLogFetcher` captures raw `TandemPump` and `BluetoothPeripheral` references at construction time (inside the `onPumpConnected` callback) and holds them in a long-lived `commandSender` lambda. If the pump disconnects and the peripheral object becomes stale, any in-flight coroutine that calls `commandSender` will attempt to use a dead BLE reference. The same class of bug can appear in any future code that directly captures `pump` or `peripheral`.

The root cause is architectural: pump and peripheral are **session-scoped** resources, but callers treat them as stable, long-lived objects.

## Design Goals

1. **No external code ever holds a `TandemPump` or `BluetoothPeripheral` reference.** All pump I/O goes through a `PumpSession` that validates the session is still alive before dispatching.
2. **Session tokens** make stale-reference bugs impossible: callers receive a token when the session starts, and `PumpSession` rejects commands bearing an expired token.
3. **Minimal blast radius.** `PumpSession` is a thin routing layer; the underlying `Pump` inner class and `TandemBluetoothHandler` are unchanged.
4. **Thread safety.** `PumpSession` posts work to the existing `PumpCommHandler` looper, preserving the single-threaded command sequencing the BLE stack requires.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ CommService                                                     │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PumpCommHandler (Handler on BLE looper)                  │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │ PumpSession                                        │  │   │
│  │  │                                                    │  │   │
│  │  │  - activeConnection: ActiveConnection?             │  │   │
│  │  │  - sendCommand(token, Message): Boolean            │  │   │
│  │  │  - sendHistoryLogRequest(token, start, count)      │  │   │
│  │  │  - isPumpReady(token): Boolean                     │  │   │
│  │  │  - openSession(pump, peripheral): PumpSessionToken │  │   │
│  │  │  - closeSession()                                  │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  │  Pump ─── owns ──► TandemPump / lastPeripheral           │   │
│  │  TandemBluetoothHandler                                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  HistoryLogFetcher ──► pumpSession.sendHistoryLogRequest(token,…)│
│  HistoryLogSyncWorker ──► requestSync() (unchanged)             │
│  periodicUpdateTask ──► sendPumpCommMessages() (unchanged)      │
│  HttpDebugApiService ──► sendPumpMessagesCallback (unchanged)   │
└─────────────────────────────────────────────────────────────────┘
```

---

## New Types

### 1. `PumpSessionToken` (value class)

```kotlin
// shared/src/main/java/com/jwoglom/controlx2/shared/PumpSessionToken.kt

@JvmInline
value class PumpSessionToken(val id: Long)
```

A simple opaque identifier. Each `open()` call generates a new monotonically-increasing ID. `PumpSession` rejects any command whose token does not match `activeConnection.token`.

### 2. `PumpSession` (class, owned by `PumpCommHandler`)

```kotlin
// mobile/src/main/java/com/jwoglom/controlx2/pump/PumpSession.kt

class PumpSession {

    data class ActiveConnection(
        val token: PumpSessionToken,
        val pump: TandemPump,
        val peripheral: BluetoothPeripheral
    )

    @Volatile
    private var activeConnection: ActiveConnection? = null

    private val tokenCounter = AtomicLong(0)

    /** Called from Pump.onPumpConnected inside PumpCommHandler. */
    fun open(pump: TandemPump, peripheral: BluetoothPeripheral): PumpSessionToken {
        val token = PumpSessionToken(tokenCounter.incrementAndGet())
        activeConnection = ActiveConnection(token, pump, peripheral)
        Timber.i("PumpSession: opened $token")
        return token
    }

    /** Called from Pump.onPumpDisconnected. */
    fun close() {
        val old = activeConnection
        activeConnection = null
        Timber.i("PumpSession: closed ${old?.token}")
    }

    /** Returns true if [token] is still the active session. */
    fun isActive(token: PumpSessionToken): Boolean {
        return activeConnection?.token == token
    }

    /** Sends a command if the session is still valid. Returns false on stale token. */
    fun sendCommand(token: PumpSessionToken, message: Message): Boolean {
        val conn = activeConnection
        if (conn == null || conn.token != token) {
            Timber.w("PumpSession: stale token $token (current=${conn?.token}), dropping $message")
            return false
        }
        conn.pump.sendCommand(conn.peripheral, message)
        return true
    }

    /** Convenience for HistoryLogFetcher. */
    fun sendHistoryLogRequest(token: PumpSessionToken, startSeqId: Long, count: Int): Boolean {
        return sendCommand(token, HistoryLogRequest(startSeqId.toInt(), count))
    }

    /** Query whether the pump is connected and ready. Token-independent for status checks. */
    fun isPumpReady(): Boolean {
        return activeConnection != null
    }
}
```

> **Thread-safety note:** `activeConnection` is `@Volatile` and only mutated from the BLE handler thread (via `open`/`close`). Reads from coroutine threads see the latest write. This is safe because mutation is single-writer; the volatile guarantees visibility.

---

## Step-by-Step Changes

### Phase 1 – Introduce `PumpSession` & `PumpSessionToken` (no behavior change)

| Step | File(s) | What to do |
|------|---------|------------|
| 1a | `shared/.../PumpSessionToken.kt` | Create `PumpSessionToken` value class. |
| 1b | `mobile/.../pump/PumpSession.kt` | Create `PumpSession` as described above. |
| 1c | `CommService.kt` — `PumpCommHandler` | Add a `val pumpSession = PumpSession()` field on `PumpCommHandler`. |
| 1d | `CommService.kt` — `Pump.onPumpConnected` | After setting `lastPeripheral` and `isConnected = true`, call `pumpSession.open(this, peripheral!!)`. Store the returned token in a `var currentToken: PumpSessionToken?` field on `PumpCommHandler`. |
| 1e | `CommService.kt` — `Pump.onPumpDisconnected` | Before nulling `lastPeripheral`, call `pumpSession.close()`. Set `currentToken = null`. |
| 1f | Build & verify | `./gradlew :mobile:assembleDebug` — no runtime behavior change yet. |

### Phase 2 – Migrate `HistoryLogFetcher` to use `PumpSession`

| Step | File(s) | What to do |
|------|---------|------------|
| 2a | `HistoryLogFetcher.kt` | Replace the secondary constructor that takes `pump: TandemPump, peripheral: BluetoothPeripheral` with one that takes `pumpSession: PumpSession, sessionToken: PumpSessionToken`. Wire `commandSender` to `pumpSession.sendHistoryLogRequest(sessionToken, start, count)`. Wire `canRequest` to `pumpSession.isActive(sessionToken)`. |
| 2b | `HistoryLogFetcher.kt` | Remove imports for `TandemPump` and `BluetoothPeripheral`. |
| 2c | `CommService.kt` — `Pump.onPumpConnected` | Change the `HistoryLogFetcher(...)` constructor call to pass `pumpSession` and `currentToken!!` instead of `pump` and `peripheral`. |
| 2d | `CommService.kt` — `isPumpReadyForHistoryFetch()` | Delegate to `pumpSession.isPumpReady()` instead of checking `pump.isInitialized && pump.isConnected && pump.lastPeripheral != null`. |
| 2e | Build & run unit tests | `./gradlew :mobile:assembleDebug` + `./gradlew testDebugUnitTest` |

### Phase 3 – Expose `PumpSession` for future consumers

| Step | File(s) | What to do |
|------|---------|------------|
| 3a | `CommService.kt` | Add a `fun getPumpSession(): PumpSession?` that returns `pumpCommHandler?.pumpSession`. |
| 3b | `CommService.kt` | Add a `fun getCurrentSessionToken(): PumpSessionToken?` that returns `pumpCommHandler?.currentToken`. |
| 3c | _Documentation_ | Update `AGENTS.md` to describe the `PumpSession` pattern so future agents use it instead of capturing raw pump references. |

### Phase 4 (optional, future) – Route `PumpCommHandler.handleMessage` through `PumpSession`

This phase is optional for the initial PR but shows the full vision.

| Step | File(s) | What to do |
|------|---------|------------|
| 4a | `CommService.kt` — `Pump.command(message)` | Replace the direct `sendCommand(lastPeripheral, message)` call with `pumpSession.sendCommand(currentToken!!, message)`. This makes `PumpSession` the **single exit point** for all pump I/O. |
| 4b | `CommService.kt` — `pumpConnectedPrecondition()` | Replace the multi-field check with `pumpSession.isPumpReady()`. |
| 4c | `CommService.kt` — `DEBUG_WRITE_BT_CHARACTERISTIC` | Route through a `pumpSession.writeRawCharacteristic(...)` method, or leave as-is since debug-only code accepts the risk. |

---

## Detailed File Changes

### `shared/src/main/java/com/jwoglom/controlx2/shared/PumpSessionToken.kt` (new)

```kotlin
package com.jwoglom.controlx2.shared

/**
 * Opaque token representing an active pump BLE session.
 * Tokens are monotonically increasing; a new token is issued on every
 * connect and PumpSession rejects commands bearing an expired token.
 */
@JvmInline
value class PumpSessionToken(val id: Long)
```

### `mobile/src/main/java/com/jwoglom/controlx2/pump/PumpSession.kt` (new)

Full implementation as shown in the "New Types" section above.

### `CommService.kt` changes

```diff
 private inner class PumpCommHandler(looper: Looper) : Handler(looper) {
     private lateinit var pump: Pump
     private lateinit var tandemBTHandler: TandemBluetoothHandler
+    val pumpSession = PumpSession()
+    var currentToken: PumpSessionToken? = null
+        private set

     ...

     override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
         lastPeripheral = peripheral
+        isConnected = true

         extractPumpSid(peripheral?.name ?: "")?.let {
             pumpSid = it
             Prefs(applicationContext).setCurrentPumpSid(it)
         }

-        historyLogFetcher = HistoryLogFetcher(this@CommService, pump, peripheral!!, pumpSid!!)
+        currentToken = pumpSession.open(this, peripheral!!)
+        historyLogFetcher = HistoryLogFetcher(
+            historyLogRepo = this@CommService.historyLogRepo,
+            pumpSid = pumpSid!!,
+            pumpSession = pumpSession,
+            sessionToken = currentToken!!,
+            autoFetchEnabled = { Prefs(applicationContext).autoFetchHistoryLogs() },
+            broadcastCallback = { item -> this@CommService.broadcastHistoryLogItem(item) }
+        )
         ...
     }

     override fun onPumpDisconnected(...): Boolean {
+        pumpSession.close()
+        currentToken = null
         lastPeripheral = null
         ...
     }

     fun isPumpReadyForHistoryFetch(): Boolean {
-        return this::pump.isInitialized && pump.isConnected && pump.lastPeripheral != null
+        return pumpSession.isPumpReady()
     }
 }
```

### `HistoryLogFetcher.kt` changes

```diff
 class HistoryLogFetcher(
     private val historyLogRepo: HistoryLogRepo,
     val pumpSid: Int,
     private val commandSender: (startSeqId: Long, count: Int) -> Unit,
     private val autoFetchEnabled: () -> Boolean = { true },
     private val canRequest: () -> Boolean = { true },
     private val broadcastCallback: ((HistoryLogItem) -> Unit)? = null,
     ...
 ) {
-    constructor(context: Context, pump: TandemPump, peripheral: BluetoothPeripheral, pumpSid: Int) : this(
-        historyLogRepo = (context as CommService).historyLogRepo,
-        pumpSid = pumpSid,
-        commandSender = { start, count -> pump.sendCommand(peripheral, HistoryLogRequest(start, count)) },
-        autoFetchEnabled = { Prefs(context).autoFetchHistoryLogs() },
-        canRequest = { (context as? CommService)?.isPumpReadyForHistoryFetch() ?: true },
-        broadcastCallback = { item -> (context as? CommService)?.broadcastHistoryLogItem(item) }
-    )
+    constructor(
+        historyLogRepo: HistoryLogRepo,
+        pumpSid: Int,
+        pumpSession: PumpSession,
+        sessionToken: PumpSessionToken,
+        autoFetchEnabled: () -> Boolean = { true },
+        broadcastCallback: ((HistoryLogItem) -> Unit)? = null
+    ) : this(
+        historyLogRepo = historyLogRepo,
+        pumpSid = pumpSid,
+        commandSender = { start, count -> pumpSession.sendHistoryLogRequest(sessionToken, start, count) },
+        autoFetchEnabled = autoFetchEnabled,
+        canRequest = { pumpSession.isActive(sessionToken) },
+        broadcastCallback = broadcastCallback
+    )
```

---

## What Each Component Depends On After the Change

| Component | Before | After |
|-----------|--------|-------|
| `HistoryLogFetcher` | `TandemPump` + `BluetoothPeripheral` (captured) | `PumpSession` + `PumpSessionToken` |
| `HistoryLogSyncWorker` | `requestSync` lambda → `sendPumpCommMessages` | No change (already uses handler-message path) |
| `periodicUpdateTask` | `sendPumpCommMessages` | No change (already uses handler-message path) |
| `HttpDebugApiService` | `sendPumpMessagesCallback` lambda | No change (already uses handler-message path) |
| `NightscoutSyncWorker` | No pump dependency | No change |
| Future sync code | Would have captured pump/peripheral | Must use `PumpSession` + `PumpSessionToken` |

---

## Migration Safety

- **Backward compatible primary constructor.** The primary `HistoryLogFetcher` constructor (taking lambdas) is unchanged. Existing unit tests that inject fake `commandSender` lambdas continue to work.
- **Session token rejection is fail-safe.** If the session has expired, `PumpSession` logs a warning and returns `false`. The `shouldContinue()` guard in `HistoryLogFetcher` will also see `canRequest()` return `false`, so the fetch loop exits cleanly.
- **No new threading model.** `PumpSession` is a thin validation layer; all real BLE work still happens on the `PumpCommHandler` looper thread via `Pump.sendCommand(...)`.

---

## Testing Plan

1. **Compile gate:** `./gradlew :mobile:assembleDebug :wear:assembleDebug :shared:lintDebug`
2. **Existing unit tests:** `./gradlew testDebugUnitTest` — existing `HistoryLogFetcher` tests pass because they use the primary (lambda) constructor.
3. **New unit tests for `PumpSession`:**
   - `open` / `close` lifecycle.
   - `sendCommand` with valid token succeeds (mock `TandemPump`).
   - `sendCommand` with stale token returns `false` and does **not** call `TandemPump.sendCommand`.
   - `isActive` returns `false` after `close`.
4. **Manual device test:** Connect to a pump, verify dashboard refreshes, history log fetch works, then disconnect and reconnect — confirm no stale-reference crashes.

---

## Files to Create

| Path | Description |
|------|-------------|
| `shared/src/main/java/com/jwoglom/controlx2/shared/PumpSessionToken.kt` | Value class for session tokens |
| `mobile/src/main/java/com/jwoglom/controlx2/pump/PumpSession.kt` | Session class |

## Files to Modify

| Path | Description |
|------|-------------|
| `mobile/src/main/java/com/jwoglom/controlx2/CommService.kt` | Wire `PumpSession` into `PumpCommHandler`, change `HistoryLogFetcher` construction, update `isPumpReadyForHistoryFetch` |
| `mobile/src/main/java/com/jwoglom/controlx2/util/HistoryLogFetcher.kt` | Replace `TandemPump`/`BluetoothPeripheral` secondary constructor with `PumpSession`-based one |

## Files Unchanged (confirmed safe)

| Path | Reason |
|------|--------|
| `HistoryLogSyncWorker.kt` | Already uses handler-message path via `requestSync` lambda |
| `HttpDebugApiService.kt` | Already uses `sendPumpMessagesCallback` lambda |
| `NightscoutSyncWorker.kt` | No pump dependency |
| `DataClientState.kt` | Read-only state holder, no pump commands |
| All wear module files | Communicate via Wearable MessageClient, never touch pump directly |
| All UI/Compose files | Send commands via `sendMessage`/`sendPumpCommands` lambdas |

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `PumpSession` adds latency to pump commands | Very Low | `PumpSession` is a simple null-check + token comparison; no allocation or I/O on the hot path. |
| Existing `HistoryLogFetcher` unit tests break | Low | Primary constructor is unchanged; only the secondary convenience constructor changes signature. |
| `ActiveConnection` holds pump/peripheral references | By design | `PumpSession` is owned by `PumpCommHandler` which already owns these objects. The session is closed in `onPumpDisconnected` before `lastPeripheral` is nulled, so external token holders can no longer reach the objects. |
| Future code bypasses `PumpSession` | Medium | Mitigated by documentation in `AGENTS.md`, code review, and eventually making `Pump` / `lastPeripheral` private to `PumpSession` (Phase 4). |

---

## Summary

This plan introduces two small types (`PumpSessionToken`, `PumpSession`) and modifies two existing files (`CommService.kt`, `HistoryLogFetcher.kt`). It eliminates the only current leak site (the `HistoryLogFetcher` closure over raw BLE objects) and establishes a pattern that prevents future callers from ever capturing pump/peripheral references directly. The change is backward compatible, introduces no new threading, and can be implemented and tested in a single PR.
