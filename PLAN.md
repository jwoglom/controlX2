# CommService Test Plan — Updated

## Design Decisions (confirmed)
1. **Test approach**: Robolectric + shadow loopers (confirmed)
2. **Architecture**: Keep current looper + handlers (PumpCommHandler & PumpFinderCommHandler) — no refactoring
3. **Fake pump**: Invest in a `FakePump` test double to enable testing connected-pump scenarios (cache hits, message forwarding, disconnect lifecycle)
4. **Both handlers tested**: Keep and test both PumpCommHandler and PumpFinderCommHandler

## Current State
- `CommServiceIntegrationTest.kt` exists with 30+ tests covering:
  - Service lifecycle (create, start, destroy, TOS/service-disabled guards)
  - Message routing (all `/to-phone/` and `/to-pump/` paths)
  - Bolus security (signature validation, blocking on normal path, cancel)
  - PumpFinder mode (start, found pumps, transition to normal)
  - Basic data forwarding via `sendWearCommMessage`
- **Gap**: No tests exercise a "connected pump" because `TandemBluetoothHandler` is mocked and `Pump` (inner class extending `TandemPump`) never connects

## What the Fake Pump Enables
A `FakePump` approach injects a test double so that `PumpCommHandler.handleMessage(INIT_PUMP_COMM)` initializes a fake pump instead of a real BT-connected one. This lets us test:

### New test scenarios with fake pump:
1. **Response caching with real cache hits** — send command, fake pump delivers response, send cached-commands, verify cached response returned without re-sending to pump
2. **Cache expiry** — verify expired cache entries cause re-send to pump
3. **Cache bust** — verify commands-bust-cache removes entry then re-sends
4. **Message forwarding to wear** — verify `onReceiveMessage` forwards via `/from-pump/receive-message` and selective `/to-wear/service-receive-message`
5. **Pump connected lifecycle** — verify `/from-pump/pump-connected` sent, `isPumpReadyForHistoryFetch()` returns true, `getPumpSession()` returns non-null
6. **Pump disconnected lifecycle** — verify session cleared, `isPumpReadyForHistoryFetch()` returns false, `/from-pump/pump-disconnected` sent
7. **Bolus with connected pump** — valid signature + connected pump → `pump.command()` called
8. **Bolus not enabled** — valid signature + connected but `insulin-delivery-actions=false` → `/to-wear/bolus-not-enabled`
9. **Debug message cache** — with populated cache, verify `/from-pump/debug-message-cache` returns data
10. **Write characteristic failed callback** — authorization vs non-authorization UUID handling

## Implementation Steps

### Step 1: Create FakePump infrastructure
Create `mobile/src/test/java/com/jwoglom/controlx2/testutil/FakePump.kt`:
- Subclass or mock `TandemPump` to record commands sent via `command()`
- Allow test to trigger `onReceiveMessage()`, `onPumpConnected()`, `onPumpDisconnected()` callbacks
- Track connection state (`isConnected`)
- Provide fake `BluetoothPeripheral` via Robolectric shadow or mockk

**Challenge**: `Pump` is a `private inner class` of `CommService`. We can't directly replace it. Options:
  - **Option A**: Use reflection to inject the fake pump into `PumpCommHandler.pump` after `INIT_PUMP_COMM` is processed (hacky but works with current architecture)
  - **Option B**: Mock `TandemBluetoothHandler.getInstance()` to return a handler that, when `startScan()` is called, immediately triggers `onPumpConnected()` on the `TandemPump` callback — simulating a connection. The `Pump` inner class callbacks would fire naturally.
  - **Option C**: Add a minimal test seam (e.g., `@VisibleForTesting` factory method) to CommService

**Recommended**: Option B — it works within the existing architecture without modifying production code. Mock `TandemBluetoothHandler` so that `startScan()` triggers the pump's `onPumpConnected` callback with a mock peripheral.

### Step 2: Implement connected-pump test helper
In the test file, add a helper like:
```kotlin
private fun startServiceAndConnectPump() {
    startServiceNormal()
    // Send INIT_PUMP_COMM which creates Pump and calls startScan()
    // Our mocked TandemBluetoothHandler.startScan() triggers onPumpConnected
    sendMessage("/to-phone/init-pump-comm", "authenticationKey AA:BB:CC:DD:EE:FF")
    shadowOf(Looper.getMainLooper()).idle()
    // Pump should now be "connected"
}
```

### Step 3: Add response caching tests (scenarios 1-3)
### Step 4: Add message forwarding tests (scenario 4)
### Step 5: Add pump lifecycle tests (scenarios 5-6)
### Step 6: Add connected-pump bolus tests (scenarios 7-8)
### Step 7: Add debug cache and write-characteristic tests (scenarios 9-10)

## Files to modify
- `mobile/src/test/java/com/jwoglom/controlx2/CommServiceIntegrationTest.kt` — add new tests
- `mobile/src/test/java/com/jwoglom/controlx2/testutil/` — possibly add FakePump or test helpers
- No production code changes needed if Option B works
