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
Phase 5 (watch pump-host UI)
```

Each phase is independently shippable. Phases 0-1 are pure refactors with no behavior change. Phase 2-3 are structural extractions. Phase 4 is the first user-visible feature. Phase 5 is the full experience.

---

## Resolved Design Decisions

### 1. Data sync in watch-as-host mode
**Decision:** The primary (pump-host) device handles external syncs directly — no forwarding.

- **Phone-as-host:** Phone does Room DB + Nightscout HTTP + xDrip+ broadcast, all local
- **Watch-as-host:** Watch does Room DB + Nightscout HTTP directly on watch. xDrip+ integration (Android broadcast intents) is forwarded to the phone via Wear Data Layer since broadcasts are device-local and can't cross devices.

**Implication for architecture:** Sync logic (Room, Nightscout upload) must live in `:shared` or a shared sync module, not phone-only code. Both `mobile` and `wear` apps link against it. xDrip+ broadcast sender is a phone-specific `DataSyncDelegate` implementation.

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
