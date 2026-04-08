# controlX2 Architecture Refactor: Enabling Watch as Pump-Host

## Context

Currently, the phone always manages the Bluetooth connection to the Tandem pump, and the watch acts as a thin client via Wear OS Data Layer. The pump only supports a single BT connection, and switching requires re-pairing. The goal is to refactor the architecture so that **either** the phone or watch can be the primary pump-connected device, chosen at setup time (re-pairing required to switch).

This is a bottom-up refactor done gradually across multiple phases. Each phase should be independently shippable and testable.

## Key Files (Current State)

- `mobile/src/main/java/com/jwoglom/controlx2/CommService.kt` (~1825 lines) — monolithic pump comm + message routing + bolus + pairing
- `wear/src/main/java/com/jwoglom/controlx2/PhoneCommService.kt` — thin watch client
- `shared/src/main/java/com/jwoglom/controlx2/shared/messaging/MessageBus.kt` — messaging interface
- `mobile/src/main/java/com/jwoglom/controlx2/messaging/HybridMessageBus.kt` — routes messages by prefix
- `shared/src/main/java/com/jwoglom/controlx2/shared/CommServiceCodes.kt` — handler command enum

---

## Phase 0: Decompose CommService Internally

**Goal:** Break CommService.kt into smaller, testable pieces without changing behavior or module structure.

**Steps:**
1. Extract `PumpCommHandler` (inner class) into its own top-level class in `mobile/src/main/java/com/jwoglom/controlx2/pump/`
2. Extract `PumpFinderCommHandler` similarly
3. Extract bolus handling logic (bolus request, confirm, cancel) into a `BolusManager` class
4. Extract pairing flow (pairing code handling, discovery) into a `PairingManager` class
5. Extract wear message forwarding logic into a `WearMessageForwarder` or similar
6. CommService becomes a thin orchestrator that delegates to these components

**Verification:** All existing functionality works identically. Run app on phone + watch, verify pump connection, bolus flow, pairing flow, and watch data updates all work.

---

## Phase 1: Message Path Naming Cleanup

**Goal:** Rename message paths from device-specific to role-based naming. No behavior change.

**Naming Convention (hybrid role + semantic):**
- `/to-pump/*` — stays as-is (pump commands: `/to-pump/command`, `/to-pump/pair`, etc.)
- `/from-pump/*` — stays as-is (pump events: `/from-pump/pump-connected`, `/from-pump/receive-message`, etc.)
- `/to-phone/*` → `/to-server/*` — commands sent TO the pump-host device (start-comm, bolus-request, is-pump-connected, etc.)
- `/to-wear/*` → `/to-client/*` — data/events sent TO the client device (service-receive-message, glucose-unit, bolus status, etc.)

**Files to modify:**
- `mobile/src/main/java/com/jwoglom/controlx2/CommService.kt` (or its decomposed pieces from Phase 0)
- `mobile/src/main/java/com/jwoglom/controlx2/MainActivity.kt`
- `mobile/src/main/java/com/jwoglom/controlx2/messaging/HybridMessageBus.kt` (routing logic on prefix)
- `wear/src/main/java/com/jwoglom/controlx2/PhoneCommService.kt`
- `wear/src/main/java/com/jwoglom/controlx2/MainActivity.kt`
- Any other files referencing these string paths

**Approach:** Define path constants in `shared` (e.g., `MessagePaths.kt` object) rather than using string literals everywhere. This makes future renames trivial and prevents typos.

**Verification:** Same as Phase 0 — full functional test of all flows.

---

## Phase 2: Extract PumpCommService Library Module

**Goal:** Create a new gradle module (`:pumpcomm`) containing the pump BT communication layer, extracted from the mobile app.

**What goes into `:pumpcomm`:**
- Pump BT connection management (the decomposed PumpCommHandler from Phase 0)
- Pump discovery / PumpFinder logic
- Pairing flow
- PumpSession (session + rate limiting)
- Message send/receive to/from pump
- Dependencies: pumpX2 libraries, blessed-android

**What stays in mobile:**
- PhoneCommService (the orchestrator that *uses* PumpCommService)
- Bolus UI/confirmation logic
- Nightscout sync, Room DB, xDrip+ integration
- WearMessageForwarder

**Module structure:**
```
controlX2/
├── pumpcomm/          # NEW - pump BT communication library
│   ├── build.gradle
│   └── src/main/java/com/jwoglom/controlx2/pumpcomm/
│       ├── PumpCommService.kt       # Core pump connection service
│       ├── PumpFinder.kt            # Pump discovery
│       ├── PumpPairingManager.kt    # Pairing flow
│       └── PumpSession.kt           # Session management
├── mobile/            # Now depends on :pumpcomm
├── wear/              # No change yet
└── shared/            # No change
```

**Key interface:** `PumpCommService` exposes a clean API that the phone (or later, watch) can call to: connect, disconnect, send command, receive messages, start discovery, pair.

**Verification:** Phone app works identically using the extracted library. Build both modules, run full flow.

---

## Phase 3: Extract ClientCommService Library Module

**Goal:** Create a new gradle module (`:clientcomm`) that generalizes the "I'm a client of the pump-host" pattern.

**What goes into `:clientcomm`:**
- Abstract client that connects to a pump-host device (currently via Wear Data Layer, but transport-agnostic interface)
- Message forwarding: client UI → pump-host → pump
- State sync reception (pump battery, IOB, CGM, etc.)
- Complication/UI data provider interface

**What stays in wear:**
- WearCommService implements ClientCommService with Wear OS Data Layer transport
- Watch-specific UI, complications

**Module structure addition:**
```
controlX2/
├── pumpcomm/          # Pump BT library
├── clientcomm/        # NEW - pump-host client library
│   ├── build.gradle
│   └── src/main/java/com/jwoglom/controlx2/clientcomm/
│       ├── ClientCommService.kt     # Abstract client interface
│       ├── ClientStateManager.kt    # State sync
│       └── ClientMessageRouter.kt   # Message forwarding
├── mobile/            # Depends on :pumpcomm, :clientcomm (for future use)
├── wear/              # Depends on :clientcomm
└── shared/
```

**Verification:** Watch app works identically as a client of the phone.

---

## Phase 4: Role-Switching — Setup-Time Configuration

**Goal:** Allow either phone or watch to be the pump-host, selected via a preference. Requires re-pairing to switch.

**Steps:**
1. Add a shared preference / setting: "Primary device" = Phone | Watch
2. **Phone in pump-host mode (default, current behavior):**
   - Phone starts PumpCommService (BT to pump)
   - Watch starts ClientCommService (Wear Data Layer to phone)
3. **Watch in pump-host mode (new):**
   - Watch starts PumpCommService (BT to pump)
   - Phone starts ClientCommService (Wear Data Layer to watch)
4. Both `mobile` and `wear` gradle modules now depend on both `:pumpcomm` and `:clientcomm`
5. A startup orchestrator on each device reads the preference and starts the appropriate service
6. The HybridMessageBus routing needs to be symmetric — currently it assumes phone = server

**Key challenges:**
- Watch has more limited resources (battery, memory) — PumpCommService needs to be efficient
- Foreground service management differs between phone and Wear OS
- BT permissions model may differ on Wear OS
- Data sync: the pump-host device runs Nightscout/Room sync directly. Sync logic must be in shared code. xDrip+ broadcasts (Android-local) forwarded to phone via Wear Data Layer when watch is host.

**Verification:** Test both configurations end-to-end: phone-as-host (regression), watch-as-host (new). Verify pump connection, data flow, bolus, pairing in both modes.

---

## Phase 4.5: Extract `:db` Module

**Goal:** Move the history-log Room database, the Nightscout sync state DB, the Nightscout sync engine, and the xDrip+ sync engine into a new `:db` Android library module so both `mobile` and `wear` can drive external uplinks when they are the pump-host. Eliminate the history-log DB duplication that Phase 4 introduced.

**Why this comes between Phase 4 and Phase 5:** Phase 4 made `WearPumpCommService` connect to the pump but left it without any external sync — Nightscout and xDrip+ code still lived only in `mobile`. Phase 4 also intentionally duplicated the history-log Room DB into `wear/.../db/historylog/` as a hack so the watch could persist history rows. This phase fixes both gaps before any new watch UI lands.

**What goes into `:db`:**

- `db/historylog/` — `HistoryLogDatabase`, `HistoryLogDao`, `HistoryLogDummyDao`, `HistoryLogItem`, `HistoryLogRepo`, `HistoryLogViewModel`
- `db/util/Converters.kt`
- `db/nightscout/` — `NightscoutSyncStateDatabase`, `NightscoutSyncState`, `NightscoutSyncStateDao`, `NightscoutProcessorState`, `NightscoutProcessorStateDao`
- `sync/nightscout/**` — worker, coordinator, config, status store, auth, profile converter, URL/timestamp formatters, processor type, trend arrow calculator, `api/`, `models/`, `processors/`
- `sync/xdrip/**` — `XdripBroadcastSender`, `XdripMessageDispatcher`, `XdripPayloadGroup`, `XdripSyncConfig`, `models/`

Package paths are preserved end-to-end — call sites in `mobile` and `wear` keep their existing `import com.jwoglom.controlx2.db.*` and `import com.jwoglom.controlx2.sync.*` lines.

**Resolving the host-app coupling:** Both `NightscoutSyncWorker` and `XdripMessageDispatcher` previously imported `com.jwoglom.controlx2.Prefs` to look up `pumpModelName` / xDrip config. The `:db` module replaces these with direct `context.getSharedPreferences("WearX2", MODE_PRIVATE)` calls — `"WearX2"` is the legacy file name shared between `mobile/Prefs` and `wear/WearPrefs`, so behavior is preserved without dragging the host's `Prefs` class into the library.

**Wiring on the watch side:** `WearPumpCommService.onPumpConnectedSync()` now calls `NightscoutSyncWorker.startIfEnabled(...)` exactly the way `CommService` does on mobile, and `dispatchExternalMessage()` now constructs an `XdripMessageDispatcher` and forwards every pump message into it.

**xDrip+ on Wear OS — TODO / open question:** `XdripBroadcastSender` calls `Context.sendBroadcast()`, which on Wear OS dispatches device-locally. Whether xDrip+ exposes a watch-side broadcast receiver is unverified — the broadcast may simply have no listener when the watch is the pump-host. The code lives in `:db` regardless so a future watch-side xDrip+ install (or a future Wear Data Layer forward back to the phone) can consume it. Investigate before relying on xDrip+ uplinks in watch-as-host mode.

**Test layout:**

- Pure-JVM unit tests for moved code live in `db/src/test/` (Nightscout client, URL/timestamp formatter, profile converter, processor type, trend arrow, models, xDrip broadcast/dispatcher/payload, history log item).
- Instrumentation tests (`NightscoutPipelineIntegrationTest`, `NightscoutSyncCoordinatorTest`, `NightscoutSyncConfigTest`, `NightscoutSyncStateDatabaseTest`) live in `db/src/androidTest/`. Root `build.gradle` keeps a CI allow-list (`["mobile", "db"]`) for `connectedAndroidTest` so the `:db` Room/Nightscout tests run alongside `:mobile`'s while every other subproject's connected tests stay disabled (to avoid the historical emulator hangs).

**Module layout after this phase:**

```
controlX2/
├── db/                # NEW — history log + Nightscout + xDrip
│   ├── build.gradle
│   ├── lint-baseline.xml
│   └── src/main/java/com/jwoglom/controlx2/
│       ├── db/historylog/
│       ├── db/nightscout/
│       ├── db/util/
│       ├── sync/nightscout/{api,models,processors}/
│       └── sync/xdrip/models/
├── pumpcomm/
├── clientcomm/
├── mobile/            # Now depends on :db
├── wear/              # Now depends on :db (and no longer ships its own Room copy)
└── shared/
```

**Verification:**

- `./gradlew :db:assembleDebug :mobile:assembleDebug :wear:assembleDebug` succeed.
- `./gradlew :db:testDebugUnitTest :mobile:testDebugUnitTest` pass.
- `./gradlew :mobile:connectedDebugAndroidTest` runs the migrated Nightscout instrumentation tests against the new `:db` classes.
- Manual phone-as-host regression: history log persists, Nightscout uploads still happen, xDrip+ broadcasts still flow.
- Manual watch-as-host smoke: flip `DeviceRole` to `PUMP_HOST` on the watch (still requires a SharedPreferences edit until DeviceRole settings UI lands), pair pump to watch, set Nightscout URL/secret in the watch's `controlx2` SharedPreferences via `adb shell run-as`, confirm history log rows persist on the watch and `NightscoutSyncWorker` uploads them.

---

## Phase 5: Watch UI for Core Operations

**Goal:** Add full pump management UI on the watch for when it's the pump-host.

**Scope (high-level, to be detailed when we get here):**
- Pump setup / pairing flow on watch
- Connection status and management
- Bolus delivery (already partially exists via BolusActivity)
- Basal rate display
- CGM display (already partially exists)
- History / recent events
- Settings management

**Note:** Much of the watch UI already exists for the client role. The additions are mainly for pump-host-specific flows (pairing, connection management, error handling).

---

## Implementation Order & Dependencies

```
Phase 0 (decompose CommService)
    ↓
Phase 1 (rename message paths)
    ↓
Phase 2 (extract :pumpcomm module)
    ↓
Phase 3 (extract :clientcomm module)
    ↓
Phase 4 (role-switching logic)
    ↓
Phase 4.5 (extract :db module — sync engines + DB)
    ↓
Phase 5 (watch pump-host UI)
```

Each phase is independently shippable. Phases 0-1 are pure refactors with no behavior change. Phase 2-3 are structural extractions. Phase 4 is the first user-visible feature. Phase 5 is the full experience.

---

## Resolved Design Decisions

### 1. Data sync in watch-as-host mode
**Decision:** The primary (pump-host) device handles external syncs directly — no forwarding. Sync code lives in a dedicated shared `:db` module (extracted in Phase 4.5).

- **Phone-as-host:** Phone runs the shared `NightscoutSyncWorker` and `XdripMessageDispatcher` from `:db`, all local.
- **Watch-as-host:** Watch runs the same `:db` `NightscoutSyncWorker` and `XdripMessageDispatcher` directly on the watch. Nightscout uploads work end-to-end. xDrip+ behavior on Wear OS is an open runtime question — the broadcast Intent dispatched by `XdripBroadcastSender` is device-local, and whether xDrip+ exposes a watch-side receiver is unverified (see Phase 4.5 TODO).

**Implication for architecture:** Sync logic (Room DB, Nightscout HTTP, xDrip+ broadcasts) lives in `:db`. Both `mobile` and `wear` apps link against it. No `DataSyncDelegate` indirection — `WearPumpCommService` and `CommService` both call the same `:db` entry points directly.

### 2. PumpCommService layering
**Decision:** Two layers within the single `:pumpcomm` module.

- **Core layer:** BT connection, message send/receive, session management. Steady-state "pump is connected" operations.
- **Lifecycle layer:** Discovery, pairing, reconnection, error recovery. Wraps core layer, manages the full connection lifecycle.

Both are separate classes in `:pumpcomm`. Callers typically use the lifecycle layer. Separation aids testing and allows different lifecycle strategies (e.g., watch may handle reconnection differently due to Wear OS BT power management).

### 3. Message path naming
**Decision:** Use `/to-server/` and `/to-client/` scheme. Collapse device-specific bolus paths.

| Current | New | Notes |
|---------|-----|-------|
| `/to-phone/start-comm` | `/to-server/start-comm` | |
| `/to-phone/stop-comm` | `/to-server/stop-comm` | |
| `/to-phone/comm-started` | `/to-server/comm-started` | |
| `/to-phone/bolus-request-wear` | `/to-server/bolus-request` | Origin tracked by MessageBusSender |
| `/to-phone/bolus-request-phone` | `/to-server/bolus-request` | Same path, differentiated by sender |
| `/to-phone/bolus-cancel` | `/to-server/bolus-cancel` | |
| `/to-phone/is-pump-connected` | `/to-server/is-pump-connected` | |
| `/to-phone/set-pairing-code` | `/to-server/set-pairing-code` | |
| `/to-phone/start-pump-finder` | `/to-server/start-pump-finder` | |
| `/to-wear/service-receive-message` | `/to-client/pump-message` | Cleaner name |
| `/to-wear/glucose-unit` | `/to-client/glucose-unit` | |
| `/to-wear/bolus-not-enabled` | `/to-client/bolus-not-enabled` | |
| `/to-wear/connected` | `/to-client/connected` | |
| `/to-pump/*` | `/to-pump/*` | No change |
| `/from-pump/*` | `/from-pump/*` | No change |

Path constants defined in `shared/MessagePaths.kt`.
