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

## Phase 0: Decompose CommService Internally — ✅ Partial

**Goal:** Break CommService.kt into smaller, testable pieces without changing behavior or module structure.

**Steps (actual state):**
1. ✅ Extract `PumpCommHandler` — extracted in commit `b9e96e1`, later relocated to `pumpcomm/src/main/java/com/jwoglom/controlx2/pump/PumpCommHandler.kt` in Phase 2
2. ✅ Extract `PumpFinderCommHandler` — at `pumpcomm/src/main/java/com/jwoglom/controlx2/pump/PumpFinderCommHandler.kt` (wraps an inner `PumpFinder` class)
3. ✅ Extract bolus handling into `BolusManager` — commit `9dfc152`, at `mobile/src/main/java/com/jwoglom/controlx2/pump/BolusManager.kt`
4. ❌ **NOT done** — No `PairingManager` class exists. `sendPumpPairingMessage()`, `sendInitPumpComm()`, and pairing-code handling in `handleMessageReceived()` remain in `CommService.kt`.
5. ❌ **NOT done** — No `WearMessageForwarder` class exists. Wear forwarding lives as direct `sendWearCommMessage()` calls scattered throughout `CommService.kt`.
6. ⚠️ **Partial** — `CommService.kt` shrank from ~1825 → ~818 lines and delegates pump/bolus, but still directly owns pairing and wear forwarding, so it's not yet a pure orchestrator.

**Outstanding work (tracked as Phase 5 prerequisites below):** If/when the pairing UI and watch-side pairing flow land in Phase 5, revisit extracting `PairingManager` and `WearMessageForwarder` so the same pairing code can be reused by `WearPumpCommService` without copy-paste.

**Verification:** All existing functionality works identically. Run app on phone + watch, verify pump connection, bolus flow, pairing flow, and watch data updates all work.

---

## Phase 1: Message Path Naming Cleanup — ✅ Complete (with two documented deviations)

**Goal:** Rename message paths from device-specific to role-based naming. No behavior change.

**Naming Convention (hybrid role + semantic):**
- `/to-pump/*` — stays as-is (pump commands: `/to-pump/command`, `/to-pump/pair`, etc.)
- `/from-pump/*` — stays as-is (pump events: `/from-pump/pump-connected`, `/from-pump/receive-message`, etc.)
- `/to-phone/*` → `/to-server/*` — commands sent TO the pump-host device (start-comm, bolus-request, is-pump-connected, etc.)
- `/to-wear/*` → `/to-client/*` — data/events sent TO the client device (service-receive-message, glucose-unit, bolus status, etc.)

**Outcome (commits `69acbba` Phase 1A, `c1a18c7` Phase 1B):**
- ✅ `shared/src/main/java/com/jwoglom/controlx2/shared/MessagePaths.kt` holds all path constants.
- ✅ Zero remaining `/to-phone/` or `/to-wear/` string literals anywhere in the codebase.
- ✅ `HybridMessageBus` routes on `MessagePaths.PREFIX_TO_SERVER`, `PREFIX_TO_CLIENT`, `PREFIX_TO_PUMP`, `PREFIX_FROM_PUMP`.

**Deviations from the original table:**
- ❌ The bolus path collapse (`bolus-request-wear` + `bolus-request-phone` → `bolus-request`) was **not** performed. Both paths kept the `-wear`/`-phone` suffix and just gained the `/to-server/` prefix: `TO_SERVER_BOLUS_REQUEST_WEAR` = `/to-server/bolus-request-wear`, `TO_SERVER_BOLUS_REQUEST_PHONE` = `/to-server/bolus-request-phone`. The router still distinguishes by path rather than by `MessageBusSender` origin. This can be revisited opportunistically; not a blocker for Phase 5.
- ❌ `/to-wear/service-receive-message` was renamed to `/to-client/service-receive-message` (not `/to-client/pump-message` as proposed in the table). The semantic rename was skipped to minimize diff churn.

**Files modified:** CommService, MainActivity (mobile + wear), HybridMessageBus, PhoneCommService, and helpers — all now reference `MessagePaths.*` constants instead of raw strings.

**Verification:** Regression-tested; all flows unchanged.

---

## Phase 2: Extract :pumpcomm Gradle Module — ✅ Complete (class names differ)

**Goal:** Create a new gradle module (`:pumpcomm`) containing the pump BT communication layer, extracted from the mobile app.

**Outcome (commits `2ef84c3` Phase 2 prep, `a439e21` Phase 2, `01dc31d` lint baseline, `8a6777a` test fix):**
- ✅ `pumpcomm/` module exists, depends on pumpX2 (android/messages/shared), blessed-android 2.4.0, commons-codec, guava, bouncycastle.
- ✅ `mobile/build.gradle` declares `implementation project(path: ':pumpcomm')`.
- ✅ The phone app works identically using the extracted library.

**Actual class layout** (package `com.jwoglom.controlx2.pump`, not `.pumpcomm`):
```
pumpcomm/src/main/java/com/jwoglom/controlx2/pump/
├── PumpCommHandler.kt          # Core BT/message handler (NOT named PumpCommService)
├── PumpFinderCommHandler.kt    # Wraps inner PumpFinder class for discovery
├── PumpSession.kt              # Session + rate limiting
├── PumpCommState.kt            # State tracking
├── CommandRateLimiter.kt       # Rate limiting
├── RateLimitConfig.kt          # Rate limit configuration
├── CommServiceCallbacks.kt     # Callback interface used by the hosting Service
├── BleChangeReceiver.kt        # BT adapter state receiver
├── PumpHistoryLogFetcher.kt    # History log paging / fetching
└── PumpHistoryLogSyncWorker.kt # WorkManager entry point for history log sync
```

**Deviations from original plan:**
- The core class is `PumpCommHandler`, not `PumpCommService`. It's a plain class driven by the hosting Android `Service` (`CommService` on mobile, `WearPumpCommService` on wear), not a Service itself.
- No separate `PumpPairingManager` — pairing is handled inline in `PumpCommHandler` plus the still-inlined pairing logic in `CommService` (see Phase 0 outstanding work).
- Two-layer (core vs lifecycle) split is not realized as distinct classes. Lifecycle concerns sit behind the `CommServiceCallbacks` interface and the hosting Service; the core/lifecycle separation discussed in "Resolved Design Decision 2" is effectively achieved via the callback seam rather than via two layered classes.
- Package path kept at `com.jwoglom.controlx2.pump` (not `.pumpcomm`) to avoid churning every import site.

**Module structure now:**
```
controlX2/
├── pumpcomm/          # pump BT communication library (package: com.jwoglom.controlx2.pump)
├── mobile/            # depends on :pumpcomm
├── wear/              # no change in Phase 2 (added in Phase 4)
└── shared/            # no change
```

**Verification:** ✅ Phone app works identically using the extracted library.

---

## Phase 3: Extract :clientcomm Gradle Module — ✅ Complete (interface-based design instead of abstract base class)

**Goal:** Create a new gradle module (`:clientcomm`) that generalizes the "I'm a client of the pump-host" pattern.

**Outcome (commit `0013f44`):**
- ✅ `clientcomm/` module exists with `build.gradle`.
- ✅ `wear/build.gradle` declares `implementation project(path: ':clientcomm')`.
- ✅ `mobile/build.gradle` declares `implementation project(path: ':clientcomm')` (prep for Phase 4 phone-as-client mode).
- ✅ Watch app works identically as a client of the phone.

**Actual class layout** — the extraction uses **interface composition** rather than the planned "abstract ClientCommService base class":
```
clientcomm/src/main/java/com/jwoglom/controlx2/clientcomm/
├── ClientMessageHandler.kt     # Message routing + state update logic
├── ClientStateStore.kt         # Interface: persistent pump-data store (implemented by host apps)
├── ClientSideEffects.kt        # Interface: transport + UI callbacks (implemented by host apps)
└── ClientConnectionState.kt    # Enum for role-aware connection states
                                #  (HOST_CONNECTED_PUMP_CONNECTED, HOST_CONNECTED_PUMP_DISCONNECTED, etc.)
```

**Deviations from original plan:**
- No `ClientCommService` abstract class. Instead, host Services (`PhoneCommService` on wear, `MobileClientService` on mobile) instantiate `ClientMessageHandler` and pass in their own implementations of `ClientStateStore` + `ClientSideEffects`. This is more flexible and avoids inheritance-based coupling.
- `ClientStateManager` → `ClientStateStore` (interface only; implementations live in host apps using their own preferences).
- `ClientMessageRouter` → `ClientMessageHandler` (handles both routing and state updates in one class).

**Module structure now:**
```
controlX2/
├── pumpcomm/          # Pump BT library
├── clientcomm/        # Pump-host client library (interface-driven)
├── mobile/            # Depends on :pumpcomm and :clientcomm
├── wear/              # Depends on :clientcomm (and later :pumpcomm via Phase 4)
└── shared/
```

**Verification:** ✅ Watch app works identically as a client of the phone.

---

## Phase 4: Role-Switching — Setup-Time Configuration — ✅ Complete (UI pending in Phase 5)

**Goal:** Allow either phone or watch to be the pump-host, selected via a preference. Requires re-pairing to switch.

**Outcome (commit `20d35d9`):**
1. ✅ `DeviceRole` enum in `shared/src/main/java/com/jwoglom/controlx2/shared/enums/DeviceRole.kt` with values `PUMP_HOST` and `CLIENT`.
2. ✅ **Phone-as-host mode (default):**
   - Phone reads `Prefs(this).deviceRole()` (defaults to `PUMP_HOST`) in `mobile/MainActivity.kt:321` and starts `CommService`.
   - Watch reads `StatePrefs(this).deviceRole()` (defaults to `CLIENT`) in `wear/MainActivity.kt:234` and starts `PhoneCommService`.
3. ✅ **Watch-as-host mode (new):**
   - Watch in `PUMP_HOST` mode starts `WearPumpCommService` (`wear/src/main/java/com/jwoglom/controlx2/WearPumpCommService.kt`) — runs BT to pump directly.
   - Phone in `CLIENT` mode starts `MobileClientService` (`mobile/src/main/java/com/jwoglom/controlx2/MobileClientService.kt`) — receives pump data from the watch via Wear Data Layer.
4. ✅ Both modules depend on both `:pumpcomm` and `:clientcomm`:
   - `mobile/build.gradle` lines 113–114
   - `wear/build.gradle` lines 137–138
5. ✅ Startup orchestrator: each MainActivity reads the role and starts the appropriate service.
6. ✅ `HybridMessageBus` routing is symmetric:
   - `PUMP_HOST` mode sends `/to-client/*` outbound, receives `/to-server/*` + `/to-pump/*`.
   - `CLIENT` mode sends `/to-server/*` + `/to-pump/*` outbound, receives `/to-client/*` + `/from-pump/*`.
   - No more "phone = server" assumptions.

**UI status:** ❌ Role selection still requires a SharedPreferences edit (either via `adb shell run-as` or calling `setDeviceRole(role)` from a test/debug path). The user-facing settings UI is part of Phase 5 (section 5a below).

**Key challenges (addressed):**
- Watch resource constraints — handled: `PumpCommHandler` in `:pumpcomm` is the same efficient code path on both devices.
- Foreground service management — both `CommService` (mobile) and `WearPumpCommService` (wear) run as foreground services with appropriate Wear OS foreground-service type.
- Data sync: resolved in Phase 4.5 by extracting `:db` so both devices can run `NightscoutSyncWorker` + `XdripMessageDispatcher` directly.

**Verification:** ✅ Phone-as-host regression passes. Watch-as-host smoke-tested (pair, bolus, data flow) — requires SharedPreferences edit until Phase 5a lands.

---

## Phase 4.5: Extract `:db` Module — ✅ Complete

**Goal:** Move the history-log Room database, the Nightscout sync state DB, the Nightscout sync engine, and the xDrip+ sync engine into a new `:db` Android library module so both `mobile` and `wear` can drive external uplinks when they are the pump-host. Eliminate the history-log DB duplication that Phase 4 introduced.

**Status:** All 11 planned items verified in the codebase. Commits `6b301c5` (main extraction), `871ad27` (CI test config), `d099a92` (KSP cleanup + `WearPrefs.deviceRole()` removal).

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

**xDrip+ on Wear OS — partially resolved (commit `2758ad2`):** Originally this was framed as "unverified whether xDrip+ exposes a watch-side receiver." In practice the bigger issue was on our end: `XdripBroadcastSender` sets `intent.package = "com.eveningoutpost.dexdrip"`, and on API 30+ a targeted broadcast requires the sender to declare the target package in `<queries>` or the intent is silently dropped before leaving the app. Both mobile (`targetSdk 35`) and wear (`targetSdk 33`) were hitting this — so the mobile path was also affected, not just wear. Commit `2758ad2` added the `<queries>` entry with `<package android:name="com.eveningoutpost.dexdrip" />` to both `mobile/src/main/AndroidManifest.xml` and `wear/src/main/AndroidManifest.xml`. The separate question of whether xDrip+ has a watch-side broadcast receiver (vs only a mobile-side one) is still open and needs an ADB smoke test against a real watch xDrip+ install, but our end is no longer blocking.

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

## Phase 5: Watch UI for Core Operations — ✅ Complete (5a–5f all shipped on this branch)

**Goal:** Add full pump management UI on the watch for when it's the pump-host, starting with the items that unblock a non-adb developer loop.

### Inventory of what already exists on the watch

- `WearPumpCommService` drives BT + `:db` Nightscout/xDrip when `DeviceRole.PUMP_HOST` is set (Phase 4.5).
- `wear/MainActivity.kt` already branches on role and starts the right service.
- Screens in `wear/.../presentation/navigation/Screen.kt`: `WaitingForPhone`, `WaitingToFindPump`, `ConnectingToPump`, `PairingToPump`, `MissingPairingCode`, `PumpDisconnectedReconnecting`, `Landing`, `SleepModeSet`, `ExerciseModeSet`, `Bolus*`, plus the new Phase 5 screens: `RoleSelection`, `PumpFinderSelect`, `PairingCodeEntry`, `PairingUnsupportedOnWatch`, `PumpBondedNeedsUnbond`, `SettingsHub`, `NightscoutSettings`, `XdripSettings`.
- Complications: CGM reading, pump battery, IOB, bolus-button, pump-button.
- `BolusActivity` + `WearBolusManager` in `wear/pump/` handle watch-side bolus request flow.

### Gaps blocking watch-as-host ship-readiness — updated status

1. ✅ DeviceRole UI — landed in 5a; the `adb`-only dev loop is gone on both phone and watch.
2. ✅ Native pump pairing flow on the watch — landed in 5b (finder, pairing-code RemoteInput, `PairingUnsupportedOnWatch` for `LONG_16CHAR`, and `PumpBondedNeedsUnbond` from Audit Tier 1).
3. ✅ Nightscout URL / API-secret entry UI on the watch — landed in 5d, with an xDrip+ toggle and role-gated `SettingsHub` entry point.
4. ✅ Basal / history / settings surfaces native to the watch in pump-host mode — **closed**: history (5e-1), basal detail (5e-2), CGM trend chart (5e-3, Canvas-based), suspend/resume (5f-1), active profile picker (5f-2), temp basal + Sleep/Exercise hub (5f-3), pump-alert dismissal (5f-4), CGM transmitter / sensor session (5f-5) all shipped. Every Phase 5 surface is in place.

### Phase 5 sub-steps (ordered, each independently shippable)

Each sub-step should ship with a phone-as-host regression pass plus a watch-as-host end-to-end smoke test.

#### 5a. DeviceRole settings UI — ✅ Complete (commit `6660f11`)

**Shipped:**
- `wear/.../presentation/ui/RoleSelectionScreen.kt` reads/writes `StatePrefs(ctx).deviceRole()` with a wear Alert dialog re-pair warning. `Screen.RoleSelection` route wired into `SwipeDismissableNavHost`.
- Matching mobile entry in `mobile/.../presentation/screens/sections/Settings.kt` ("Pump-host device" ListItem + `AlertDialog` spelling out the three manual re-pair steps).
- `mobile/util/RoleSwitcher.kt` and `wear/util/RoleSwitcher.kt` stop both services and call `Activity.recreate()` so `MainActivity.onCreate` role-branching is the single source of truth.

**Deviations from the plan:**
- Service swap is done via `Activity.recreate()` (not programmatic in-MainActivity start/stop swap) so each `MainActivity` reads `deviceRole()` once on entry.
- No cross-device notification: `TO_SERVER_DEVICE_ROLE_CHANGED` / `TO_CLIENT_DEVICE_ROLE_CHANGED` constants (added speculatively in Phase 1) remain **unused** — the re-pair warning is the cross-device contract instead.
- Wear-side dialog copy was brought to parity with mobile in Audit Tier 2 (explicit "unpair in old host's Bluetooth settings first" step).

#### 5b. Watch-side pump pairing flow — ✅ Complete (commit `6c6d4d6`)

**Shipped:**
- Screens: `PumpFinderSelectScreen` (restart-scan fallback), `PairingCodeEntryScreen` (uses Wear system numeric `RemoteInput` via `RemoteInputIntentHelper`, not a custom rotary keypad), `PairingUnsupportedOnWatchScreen` (graceful `LONG_16CHAR` degradation).
- Watch-local `PumpSetupStage` enum (9-value subset of mobile's) + new DataStore fields (`pumpSetupStage`, `pumpFinderPumps`, `setupDeviceName`, `setupPairingCodeType`, `pumpReadyState`, `pumpPairingError`).
- Watch `handleMessage()` gains PUMP_HOST handlers for `FROM_PUMP_PUMP_FINDER_FOUND_PUMPS` / `PUMP_DISCOVERED`, `FROM_PUMP_MISSING_PAIRING_CODE` (splits on `PairingCodeType` to either code-entry or unsupported), `FROM_PUMP_INVALID_PAIRING_CODE` (clears code, shows banner, relaunches entry), `FROM_PUMP_INITIAL_PUMP_CONNECTION`. CLIENT-role behavior on shared paths preserved.
- Shared helper `pumpcomm/pump/pairing/PairingCodeEntry.kt` dedupes the post-`SET_PAIRING_CODE` dispatch (persists the code via `PumpState.setPairingCode` and sends `TO_SERVER_STOP_PUMP_FINDER` / `TO_PUMP_PAIR` based on stage). Mobile `MainActivity` now delegates to the same helper. Audit Tier 3 later split it into two typed entry points: `applyForInitialPumpComm` / `applyForRePair`, with a loud `Timber.w` else-branch on callers.

**Message-bus plumbing (a structural change not anticipated by the plan):**
- `LocalMessageBus` moved `:mobile` → `:shared` as a **process-level singleton** so both platforms share the in-process transport.
- `MessageBus` gained a sender-tagged overload `addMessageListener(listener, listenerSender)`, and `LocalMessageBus.deliver()` skips any listener whose registered sender matches the emission's sender — **reentrance prevention is now enforced by the bus, not by listener-side discipline**.
- New `wear/.../messaging/WearHybridMessageBus.kt` mirrors mobile's `HybridMessageBus`: wraps the singleton `LocalMessageBus` + a per-instance `WearMessageBus`, takes an `identity: MessageBusSender`, and registers its local proxy tagged with that identity. `WearPumpCommService` uses `COMM_SERVICE`; the watch `MainActivity` uses `MOBILE_UI` and **stops being a `MessageClient.OnMessageReceivedListener`** — all routing goes through the hybrid bus.
- `shared/src/test` gains a `LocalMessageBusTest` covering sender-tagged filtering (untagged sees all, tagged never sees own, mixed, `removeMessageListener` stops delivery, singleton identity).

**Post-ship audit fix (Audit Tier 1, commit `15b4686`):**
- `PumpBondedNeedsUnbondScreen` added to handle `FROM_PUMP_PUMP_BONDED_NEEDS_MANUAL_UNBOND` (previously silently dropped on watch); screen opens system Bluetooth settings directly.

#### 5c. Watch-side connection status + reconnection UX — ✅ Complete (commit `ca97612`)

**Shipped:**
- Real `ConnectingToPumpScreen` + `PumpDisconnectedReconnectingScreen` replacing the `IndeterminateProgressIndicator` placeholders; both surface a Stop button that triggers mobile's disable-sequence idiom.
- `LastConnectionText` (wear port of `LastConnectionUpdatedTimestamp`) and `WearServiceDisabledMessage` (wear port of `ServiceDisabledMessage`) mounted on `LandingScreen` and in the disconnected screen.
- New watch `stopPumpService()` / `reEnablePumpService()` in `MainActivity` mirroring mobile `Debug.kt` and `ServiceDisabledMessage.kt`; watch `TO_SERVER_APP_RELOAD` → `triggerAppReload`.
- `WearPumpCommService.TO_SERVER_FORCE_RELOAD` now actually cycles the service (was previously a no-op log).
- DataStore: `pumpConnected`, `pumpLastConnectionTimestamp`, `pumpLastMessageTimestamp` (mobile-identical field names).

**Deviations from the plan:**
- RSSI is **not** surfaced (plan listed it as optional — not currently plumbed through from `PumpCommHandler`).
- Manual reconnect + disconnect UX is collapsed into a single Stop button that triggers the mobile-idiomatic disable sequence; the re-enable path is via the `WearServiceDisabledMessage` on Landing, not a separate reconnect button.
- No new message paths were introduced — reuses `TO_SERVER_FORCE_RELOAD` + `TO_SERVER_APP_RELOAD` end-to-end.

**Post-ship audit fixes (Audit Tier 1, commit `15b4686`):**
- `LastConnectionText` fallback branches no longer render raw `Instant.toString()` (ISO-8601); both fallbacks now route through `shortTimeAgo` and drive recomposition from `intervalOf`.
- Dropped a dead `serviceEnabled` branch in `PumpDisconnectedReconnectingScreen` whose `LaunchedEffect` only fired on `pumpConnected` changes.
- Stop buttons switched to `primaryChipColors` for readable contrast on small watch faces.

#### 5d. Nightscout / xDrip+ settings UI on watch — ✅ Complete (commit `c61435f`)

**Shipped:**
- `SettingsHubScreen` — single entry point with role-gated entries (Role, Nightscout, xDrip+ shown only in `PUMP_HOST`; Force reload, Open on phone always). Replaces the three-chip Landing footer with a single Settings chip.
- `NightscoutSettingsScreen` — enable toggle (starts/stops `NightscoutSyncWorker` using mobile's pattern), URL + API secret via `RemoteInput`, sync-status text ticking via `intervalOf`. Reads/writes the `"controlx2"` prefs that mobile and `WearPumpCommService.onPumpConnectedSync` already use.
- `XdripSettingsScreen` — enable toggle + four payload toggles (CGM, device status, treatments, status line). Reads/writes `"WearX2"` prefs matching `XdripMessageDispatcher`. Dispatches the mobile two-step reload sequence when `requiresReloadComparedTo()` is true.
- Shared `RemoteTextInput` helper extracted from the `PairingCodeEntryScreen` pattern.
- `MainActivity.forceReloadService()` mirroring mobile `XdripSettings.kt:62-68`.

**Post-ship audit fixes:**
- Tier 1: `NightscoutSettings` enable toggle guarded against `WearPrefs.currentPumpSid() == -1` so `startIfEnabled` is never called with an invalid sid.
- Tier 2: `AutoCenteringParams()` added to all three new `ScalingLazyColumn`s; Settings chip gets a visible "Settings" label; Nightscout URL chip truncates via `compactUrlLabel` with `TextOverflow.Ellipsis`; `StatePrefs.deviceRole()` reads cached via `remember { ... }` in composables.

**Still open:** xDrip+ on Wear OS receiver behavior remains unverified (inherited from Phase 4.5). UI ships the toggle and dispatches `sendBroadcast`, but whether a watch-side xDrip+ receiver exists is still an open runtime question.

#### 5e. Pump data surfaces on watch (reuse existing flows) — ✅ Complete (5e-1, 5e-2, 5e-3 all shipped)

Split into three sub-phases ordered by complexity so each is independently shippable. Post-ship audit (commit `c9fe04b`) landed after 5e-1 + 5e-2 to address correctness / design drift before the CGM chart work — see the "5e post-ship audit" block below.

##### 5e-1. History / events screen — ✅ Complete (commit `0125d58`)

**Shipped:**
- New `wear/.../presentation/ui/HistoryLogScreen.kt` — `ScalingLazyColumn` rendering up to 100 recent rows from `HistoryLogRepo.getAll(pumpSid)` as formatted one-liners. ViewModel scoped to the `NavBackStackEntry` so it's cleared on navigation-away.
- `HistoryLogRepo` obtained via `HistoryLogDatabase.getDatabase(context)` — same Room singleton `WearPumpCommService` already uses, so no duplicate DB instance.
- Per-type formatters: bolus delivery/complete, basal rate change, temp basal start/end, carbs, alarms, alerts, cannula/tubing/cartridge fills, pumping-resumed/suspended, daily-basal summary. Unknown types render as the pumpx2 class name minus the `HistoryLog` suffix.
- CGM-reading rows (DexcomG6, CgmDataGx/Fsl2/Fsl3) filtered client-side so ~5-minute samples don't drown the event log. Filter set is computed once from `HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID` rather than hardcoded, so it tracks pumpx2 typeId changes without touching the watch.
- `Screen.HistoryLog` route added; wired into `SwipeDismissableNavHost` in `WearApp.kt`.
- Role-gated entry in `SettingsHubScreen` ("Pump history" chip, `PUMP_HOST` only — CLIENT-mode watches don't receive raw history cargo).
- Empty-state for `pumpSid < 0` (first run before any pump connection) reuses 5d's `NightscoutSettings` guard pattern.

**Deviations / out-of-scope:**
- No per-row detail view (tapping a chip is currently a no-op). Future iteration.
- No type filter UI — keeping it simple; the CGM-reading filter is the only one that matters in practice.
- No pagination — 100-row cap is fixed. Fine for a watch; can be revisited if users want deeper history.

##### 5e-2. Basal rate display screen — ✅ Complete (commit `6581260`)

**Shipped:**
- New `wear/.../presentation/ui/BasalDetailScreen.kt` — headline render of the live `basalRate` + `BasalStatus` from `LocalDataStore` (same fields `LandingBasalRow` uses) above a 20-row list of recent basal-related history events from `HistoryLogRepo`.
- Event types queried: `BasalRateChangeHistoryLog`, `TempRateActivatedHistoryLog`, `TempRateCompletedHistoryLog`, `PumpingSuspendedHistoryLog`, `PumpingResumedHistoryLog`. Backed by `HistoryLogViewModel.latestItemsForTypes(typeClasses, 20)`, reusing the class→id resolution path from 5e-1.
- ViewModel keyed `"basal-history-$pumpSid"` so it's distinct from the 5e-1 `HistoryLogScreen`'s default-keyed ViewModel.
- Per-row formatting is basal-specific (`"→ 1.250U/hr"` rate-change prefix, "Temp basal start/end", "Pump suspended/resumed"); time formatter duplicated from 5e-1 intentionally, will consolidate if 5e-3 also needs it.
- `Screen.BasalDetail` route added; wired into `WearApp.kt` next to `HistoryLog`.
- Role-gated "Basal" chip added to `SettingsHubScreen` above the "Pump history" chip. Same `PUMP_HOST`-only gating as 5e-1.

**Deviations / out-of-scope:**
- `DataStore.basalRate`/`basalStatus` flow in both roles, so technically the header could render in CLIENT mode. Gated to `PUMP_HOST` anyway for consistency with 5e-1 and because the history list below the header needs local `HistoryLogRepo` rows that only exist on `PUMP_HOST` watches.
- `pumpSid` captured once via `remember { WearPrefs(context).currentPumpSid() }`; if it flips from `-1` to valid during the screen's visibility, the user must navigate away and back to see history populate — matches the 5e-1 / Nightscout-settings pattern.
- No per-row detail view (tapping a chip is a no-op). Future iteration.

##### 5e-3. CGM trend-graph screen — ✅ Complete (Canvas-based, this branch)

**Shipped:**
- New `wear/.../presentation/ui/CgmChartScreen.kt` (~210 lines). PUMP_HOST-only since it reads from the watch-local `HistoryLogRepo`.
- **Renderer:** plain `androidx.compose.foundation.Canvas` — chose Canvas over Vico-compose to avoid pulling `vico-compose` + `vico-core` (~1.2 MB) into the wear APK for what reduces to a single-line chart on a circular watch face. Mobile's `VicoCgmChart.kt` is 2434 lines because it juggles bolus / basal / threshold / CGM layers with rich Vico configuration; this watch port covers just the CGM trace and lands at <215 lines.
- **Data flow** mirrors mobile `VicoCgmChart.toCgmDataPoint`: query `HistoryLogViewModel.latestItemsForTypes(CGM_TYPE_CLASSES, 200)` (G6, G7, Gx, FSL2, FSL3 — the same list mobile filters on), parse each item, extract glucose via the same subclass dispatch (`currentGlucoseDisplayValue` for G6/G7, `.value` for Gx/FSL), drop zero/negative readings. 200 readings ≈ 16+ hours at the typical 5-minute sample rate — comfortably more than the 6-hour window.
- **Window anchoring:** the right edge anchors to the most recent reading rather than wall-clock so a watch that's been disconnected for a few hours still shows continuous data instead of an empty chart. Plan-doc deviation: original spec said `itemsForTypesSince(...)` with a wall-clock cutoff; the latest-N + filter approach is simpler and avoids pump-time-vs-real-time conversion in the query.
- **Render layers** (bottom-up):
  - Hourly dashed vertical grid lines.
  - Dashed horizontal threshold lines at low (70 mg/dL or 70/18 mmol/L) and high (180 mg/dL or 180/18 mmol/L). Standard CGM bands; not user-configurable yet.
  - CGM polyline (`Path` with `Stroke(width = 2.5f)`) using `MaterialTheme.colors.primary`.
  - Endpoint dot at the most recent reading.
- **Unit awareness:** observes `dataStore.glucoseUnitPreference`. mg/dL renders raw; mmol/L divides by 18. Threshold lines + Y-axis padding (±10) scale via a `Float.ofUnit(unit)` extension so both units use the same code path.
- **Footer:** single-line "{value} {unit} · h:mm a" formatted from the most recent reading (LocalDateTime via `pumpTimeLocal()` + `DateTimeFormatter.ofPattern("h:mm a")`).
- **Empty states:** "No CGM readings in the last 6h." when the windowed series is empty; "CGM history will appear after the first pump connection." when `pumpSid < 0` or `LocalHistoryLogRepo.current` is null. Reuses the same guard pattern 5d Nightscout / 5e-1 / 5e-2 use.
- `Screen.CgmChart` route added under "Pump data surfaces (DeviceRole.PUMP_HOST)" in `Screen.kt`. Wired in `WearApp.kt` as a plain `composable(Screen.CgmChart.route)` — no `SCROLL_TYPE_NAV_ARGUMENT`, no `scalingLazyListState`, no `RequestFocusOnResume` because the screen is a fixed Canvas with no scroll surface.
- `SettingsHubScreen.kt` gains a "CGM chart" chip in the `PUMP_HOST` block above "Pump history".

**Deviations from the plan / mobile:**
- **Canvas instead of Vico.** No new dependencies in `wear/build.gradle`. Plan doc previously called this "the Vico-vs-Canvas decision" — this commit picks Canvas.
- **No multi-layer overlays.** Mobile's chart shows boluses, basal segments, Control-IQ predictions, and time-in-range bands; watch shows just the CGM line + thresholds. A 192×192 pixel circular watch face can't legibly carry the rest.
- **No interactivity.** No tap-to-inspect-point, no zoom, no pan. The chart is a static glance surface; if users want detail they have the History Log screen (5e-1) which pulls from the same DB.
- **No "Vico-style" smoothing.** Linear interpolation between 5-minute samples is fine — the data is already discrete, smoothing would imply false precision.
- **No live `cgmReading` overlay.** Mobile splices in the live (`dataStore.cgmReading`) value at the right edge; watch shows only what's in the historical DB. Adding the live point is one composable read but defers naturally to a future iteration if users notice the lag.

##### 5e post-ship audit — ✅ Complete (commit `c9fe04b`)

Multi-agent code review of 5e-1 (`0125d58`) and 5e-2 (`6581260`) surfaced correctness + design drift. Consolidated into commit `c9fe04b`:

**Correctness fixes:**
- `HistoryLogScreen`: replaced `viewModel.all` (full-table Flow, thousands of rows re-emitted on every insert) with `latestItemsForTypes(NON_CGM_TYPE_IDS, 100)` so Room caps + filters at query time. CGM filter now includes `DexcomG7CGMHistoryLog` (was missing — G7 users saw the 5-minute firehose).
- `BasalDetailScreen`: unconditional `viewModel()` + `remember(vm) { ... }` around `latestItemsForTypes(...)` so LiveData has stable observer identity. Also fires `CurrentBasalStatusRequest` on entry + every 60s so the header isn't indefinitely stale.
- Both screens key `viewModel(key = "...-pump-$pumpSid")` so the VM recreates when the sid changes after first pair.
- Both screens take a `FocusRequester` and apply `Modifier.scrollableColumn(focusRequester, listState)` — rotary crown was previously a no-op on the 100-row history.
- `WearApp` registers both routes with `SCROLL_TYPE_NAV_ARGUMENT(SCALING_LAZY_COLUMN_SCROLLING)` so `PositionIndicator` / `Vignette` / `TopText` render.
- `HistoryLogViewModel`: dropped three `!!` bangs on `LOG_MESSAGE_CLASS_TO_ID[clazz]!!` that would NPE the screen on any pumpx2 typeId rename; replaced with `mapNotNull`. CGM type-id resolution uses `requireNotNull` with a helpful message — a renamed CGM class should fail loud, not let the firehose through.
- Label formatters: alarm/alert rows prefer `alarmResponseType?.name` / `alertResponseType?.name` (e.g. `LOW_INSULIN`) with numeric-ID fallback; carb rows use `%.1fg` so 0.5g-resolution pump carbs don't truncate to zero.

**Design fixes:**
- New reactive `dataStore.currentPumpSid: MutableLiveData<Int>` — `WearPumpCommService.prefSetCurrentPumpSid` mirrors pref writes into DataStore via `postValue`; `MainActivity.onCreate` seeds it from the persisted pref. Both screens now observe `LocalDataStore.current.currentPumpSid` instead of the `remember { WearPrefs(...).currentPumpSid() }` snapshot — fixes "open the screen before the pump pairs, it stays stuck on 'no pump yet'."
- New `LocalHistoryLogRepo` CompositionLocal provided from `MainActivity.onCreate` — both screens read it instead of each constructing their own `HistoryLogRepo` wrapper around the singleton Room DB. (Mobile still triple-constructs across `MainActivity` / `CommService` / `HttpDebugApiService`; that pre-existing drift is called out as out-of-scope.)
- `LandingBasalRow` gains an `onClick` that navigates to `BasalDetail`; the "Basal" chip is **removed** from `SettingsHubScreen` — basal is pump-data, not settings. Pump history stays under SettingsHub for now.
- Dropped the `basal-history-$pumpSid` VM key from 5e-2 (based on a misread of `ViewModel` scoping — VMs scope per `NavBackStackEntry`, not per factory). Replaced with the now-meaningful `basal-detail-pump-$pumpSid` key that makes the reactive-pumpSid fix work.
- Dropped per-row `remember { formatHistoryLogLabel(item) }` caches: `HistoryLogItem.parse()` is already LRU-cached (500 entries), so the outer `remember` only burns Compose slots without saving work.

**Follow-up CI fix (commit `d1dd43a`):** 5e audit wired both screens through the shared `scalingLazyListState(...)` helper in `WearApp.kt`, which returns `androidx.wear.compose.material.ScalingLazyListState`. The screens had imported the newer `androidx.wear.compose.foundation.lazy.*` variants, causing a signature mismatch. Unified both screens to the `material.*` imports matching `BolusInputPhase` / `LandingScreen` and dropped `key = { it.seqId }` (the two variants of `items` have incompatible `key` lambda signatures).

#### 5f. Settings management parity — ⏳ In progress (5f-1 ✅, 5f-2 ✅, more remaining)

Expose pump settings the phone already offers as watch screens, routed through the existing `/to-pump/*` paths — shared code path, no new backend work.

##### 5f-1. Watch-side suspend/resume insulin action — ✅ Complete (commit `1935e0d`)

**Shipped:**
- New `Screen.SuspendPumpingSet` route; confirmation rendered as an in-place wear `Alert` composable directly in `WearApp.kt`, matching the existing `SleepModeSet` / `ExerciseModeSet` pattern (not a full screen).
- State-aware Stop/Start chip in the Landing modes row replaces the placeholder pump-icon chip:
  - `BasalStatus.PUMP_SUSPENDED` → "Resume insulin deliveries?" with `PlayArrow` button.
  - `UNKNOWN` / `null` → "Insulin state unknown, try again in a moment." with no positive button (prevents blind taps).
  - else → "Suspend all insulin deliveries?" with `Check` button.
  - Landing chip label/icon flip: `PlayArrow` + "STOPPED" when suspended, `Stop` + "ON" when running.
- On confirm: fires `SuspendPumpingRequest()` or `ResumePumpingRequest()` via `SendType.BUST_CACHE`, then polls `HomeScreenMirrorRequest` 5× at 1s intervals from `rememberCoroutineScope().launch { ... }`. Direct mirror of mobile `Actions.kt:296–304, 345–353`.
- Works in both `DeviceRole.PUMP_HOST` and `CLIENT` — `HybridMessageBus` routes `/to-pump/*` to either the local BT link or forwards to the phone-host, so one code path covers both modes. No role gating.

**Deviations / out-of-scope:**
- No "resume guidance" cartridge-state gate (mobile checks `LoadStatusResponse` before allowing resume). The `UNKNOWN`-state dialog is an adequate MVP; revisit if users report hitting bad resume states.

##### 5f-2. Watch-side active profile picker — ✅ Complete (commit `744fa12`)

**Shipped:**
- New `ProfileSwitchScreen` (`wear/.../presentation/ui/ProfileSwitchScreen.kt`): `ScalingLazyColumn` of the pump's IDP profiles with the currently-active one highlighted (primary vs secondary chip color + "Active" / "Tap to activate" label).
- Tap a non-active profile → in-screen wear `Alert` → dispatches `profile.setActiveProfileMessage()` with `SendType.BUST_CACHE`, then re-issues `IDPManager.nextMessages()` to refresh the active marker without waiting for the next 60s tick.
- Data source: `LocalDataStore.current.idpManager` — same `MutableLiveData<IDPManager>` that `MainActivity.onPumpMessageReceived` populates via `processMessage`. On entry + every 60s the screen issues `idpManager.nextMessages()` to keep the IDP cache fresh (mirrors mobile `ProfileActions.kt:118`).
- Scroll type + `FocusRequester` wired through the shared `scalingLazyListState(it)` helper + `RequestFocusOnResume` (matches 5e-1 / 5e-2).
- `Screen.ProfileSwitch` entry lives in `SettingsHubScreen` **not** role-gated (above the `PUMP_HOST`-only block) — profile switching is useful in either mode.

**Design notes / deviations:**
- Active state conveyed by chip color + text label, not a conditional icon. `Chip.icon` is `(@Composable BoxScope.() -> Unit)?` and passing `null` through a ternary ran into `@Composable` annotation propagation issues — color+label was sufficient.
- Confirm alert is in-screen (state-branched rendering), not a route-level dialog, because the profile list is the "previous step." State-tracking calls (`remember`, `observeAsState`, `LaunchedEffect`, `intervalOf`) are all unconditional before the branch so Compose invariant holds.

**Follow-up CI fixes:**
- Commit `e659bab`: dropped `androidx.wear.compose.material.items` in favor of `list.forEach { row -> item { ... } }` across `HistoryLogScreen`, `BasalDetailScreen`, and `ProfileSwitchScreen`. The 1.4.1 `items` function's signature wasn't lining up with the list-plus-itemContent-only call shape used here; `forEach` on `List` is inline so the outer `ScalingLazyListScope` receiver stays accessible to `item { }`. Works with both `material.*` and `foundation.lazy.*`.
- Commits `a9df6ac` and `61a0262`: two rounds of the nested-KDoc-comment trap. `ProfileSwitchScreen`'s KDoc contained `` `/to-pump/*` ``, which Kotlin 2.2's block-comment grammar interprets as a nested comment opener. Both rewrites replace the literal slash-star sequence (referenced in prose as `to-pump` instead), mirroring the earlier `7db3dc9` fix in `WearHybridMessageBus.kt`.

##### 5f-3. Watch-side temp basal set/cancel + Sleep/Exercise hub hookup — ✅ Complete

**Shipped:**
- New `wear/.../presentation/ui/TempBasalScreen.kt` — state-aware screen backed by two new DataStore fields (`tempRateActive: MutableLiveData<Boolean>`, `tempRateDetails: MutableLiveData<TempRateResponse>`) mirroring mobile's DataStore shape. On entry, fires `TempRateRequest()` + `CurrentBasalStatusRequest()` with `SendType.BUST_CACHE` so the active/inactive branch and the U/hr→% derivation both use fresh pump state.
- **Active branch:** headline shows `percentage% for Hh Mm` (pulled from `TempRateResponse`) with a single "Cancel temp basal" chip. Tap → wear `Alert` confirm → dispatch `StopTempRateRequest()` with `SendType.BUST_CACHE`, then 5× 1s poll of `TempRateRequest()` (mirror of 5f-1's suspend/resume polling, but polling `TempRateRequest` instead of `HomeScreenMirrorRequest` because that's what mobile's `TempRateWindow.kt:522` / `Actions.kt:606-611` does).
- **Inactive branch (mode-choice → value picker → duration picker → confirm):**
  - **Step 0 — PickMode:** two chips, "Percent" (always enabled) and "U/hr" (enabled only when `dataStore.basalRate` has been populated from a `CurrentBasalStatusResponse`; disabled chip shows a "Basal rate unavailable" secondary label).
  - **Percent path:** `SingleNumberPicker` range 0–250, default 100.
  - **U/hr path:** `DecimalNumberPicker` range 0.00–5.99, default = current profile basal rate. On confirm, computes `derivedPercent = ((rawUnits / currentBasalRate) * 100).roundToInt()` and validates: basal rate must be known (non-null, > 0), `rawUnits` must be 0 or ≥ 0.05, derived percent must land in 0–250. Any violation pops a wear `Alert` with the specific error (modelled as a `UnitsValidation.Error` sum type) and returns to the U/hr picker. This mirrors mobile `TempRateWindow.kt:190-265` exactly so U/hr→% conversion doesn't drift between platforms.
  - **Step 2 — total-minutes picker:** `SingleNumberPicker` range 15–480, default 30. Reuses the same component `BolusSelectCarbs` / `BolusSelectBG` use — handles its own rotary focus internally. `SingleNumberPicker` returns 0 when a trailing blank slot is selected — floored to 15 before dispatch so a mis-scroll can't send a pump-rejected duration.
  - **Final confirm `Alert`:** mode-aware headline — "Set N% for Hh Mm?" (Percent mode) or "Set N.NN U/hr (N%) for Hh Mm?" (U/hr mode — shows both the entered absolute rate and the derived percent). Positive-button tap dispatches `SetTempRateRequest(totalMinutes, derivedPercent)` with `SendType.BUST_CACHE` + same 5× poll.
- `Screen.TempBasalSet` route added; wired into `WearApp.kt` next to `ProfileSwitch` **without** an outer `FocusRequester` / `scalingLazyListState` (matches the `BolusSelectUnits` / `BolusSelectCarbs` pattern — the picker manages focus internally, and calling `RequestFocusOnResume` with a never-attached `FocusRequester` would throw).
- `MainActivity.onPumpMessageReceived` adds a `TempRateResponse` arm that writes both `dataStore.tempRateActive.value = message.active` and `dataStore.tempRateDetails.value = message`, directly mirroring `mobile/MainActivity.kt:932-935`.
- **Sleep / Exercise hub entries** (bonus, ~20 lines in `SettingsHubScreen.kt`): new "Sleep mode" and "Exercise mode" chips below "Active profile", navigating to the existing `Screen.SleepModeSet` / `Screen.ExerciseModeSet` Alert routes in `WearApp.kt`. Not role-gated (works in both `PUMP_HOST` and `CLIENT` via the shared `/to-pump/*` routing).
- **Temp basal chip** added to `SettingsHubScreen` above "Active profile", also not role-gated.

**Deviations from mobile:**
- **U/hr cap at 5.99 U/hr on watch** (mobile allows higher values). `DecimalNumberPicker` `maxNumber = 5` keeps the picker manageable on a small screen; realistic Tandem basal ceilings are well below that for almost all users, and anyone needing a higher absolute rate can fall back to the Percent path (which maps 1:1 to the pump's 0–250% range). No correctness impact — the pump itself enforces limits on the dispatched `SetTempRateRequest`.
- **Single total-minutes picker instead of hours + minutes.** Mobile splits into hours (0–72) + minutes (0–59 step 1). Watch uses one picker for total minutes (15–480) — simpler UX on the smaller screen; the 480-minute cap is below mobile's 72h but covers the realistic day-to-day range.
- **No presets** (e.g. "Exercise: 50% for 1h"). Plain entry only.
- **No cartridge-state gate** — same rationale as 5f-1's resume-gate omission.
- **Reuses `basalStatus` elsewhere; only `tempRateActive` is consumed here.** The DataStore exposes both `basalStatus` (`TEMP_RATE` / `ZERO_TEMP_RATE` values from `HomeScreenMirrorResponse`) and the new `tempRateActive` Boolean. The screen uses `tempRateActive` directly because it's the same signal mobile uses; the `basalStatus` TEMP_RATE value remains available for other surfaces like `LandingBasalRow`.
- **Sleep/Exercise hub hookup is NOT strict parity** — mobile doesn't have a "Sleep" / "Exercise" settings entry either; these modes are set from the phone home screen. The hub chips make these already-existing wear screens discoverable from Settings in addition to the Landing modes row.

##### 5f-4. Watch-side pump alerts / alarms / reminders / CGM-alerts dismissal — ✅ Complete

**Shipped:**
- New `wear/.../presentation/ui/WatchNotificationsScreen.kt` — `ScalingLazyColumn` listing every active pump notification (alerts, alarms, reminders, CGM alerts, plus the read-only Tandem-malfunction `HighestAamResponse`) with per-row tap-to-dismiss. Backed by a new `dataStore.notificationBundle: MutableLiveData<NotificationBundle>` that mirrors mobile's shape. On entry, fires every `NotificationBundle.allRequests()` with `SendType.BUST_CACHE` so the list reflects current pump state, not a stale cache.
- `MainActivity.onPumpMessageReceived` adds a `NotificationBundle.isNotificationResponse(message)` arm next to the existing `IDPManager` arm, populating `dataStore.notificationBundle` via `bundle.add(message)` + re-wrapping in a fresh `NotificationBundle()` to trigger LiveData observers — direct mirror of mobile `MainActivity.kt:820-825`.
- Per-row dismiss mirrors mobile `NotificationItem.dismissNotification()` exactly: `DismissNotificationRequest(NotificationType, bitmask-or-id)` dispatched via `SendType.STANDARD`, then `delay(500)`, then full `NotificationBundle.allRequests()` refresh. Type/payload mapping:
  - `AlertStatusResponse.AlertResponseType` → `NotificationType.ALERT, bitmask().toLong()`
  - `AlarmStatusResponse.AlarmResponseType` → `NotificationType.ALARM, bitmask().toLong()`
  - `ReminderStatusResponse.ReminderType` → `NotificationType.REMINDER, id().toLong()`
  - `CGMAlertStatusResponse.CGMAlert` → `NotificationType.CGM_ALERT, id().toLong()`
- `HighestAamResponse` (pump malfunction, e.g. ERROR-1) renders read-only with secondary label "Cannot be dismissed" and a disabled chip — matches mobile's UX (mobile shows a static informational line for it).
- Confirm `Alert` per-tap with a `Warning` icon for alarms / `Notifications` icon for everything else; positive button dispatches and closes, negative cancels.
- `Screen.Notifications` route added with the `SCROLL_TYPE_NAV_ARGUMENT` + `scalingLazyListState` + `RequestFocusOnResume` plumbing (matches `HistoryLog` / `BasalDetail` pattern, since the screen is a scrolling list).
- `SettingsHubScreen.kt` gains a "Pump alerts" chip below "Exercise mode" (not role-gated — works in both `PUMP_HOST` and `CLIENT` via the shared `/to-pump/*` routing).

**Deviations from mobile:**
- **No swipe-to-dismiss UI.** Mobile uses `SwipeToDismissBox` (Material3 wrapper) for swipe-to-delete. Wear has its own `SwipeDismissableNavHost` for back-navigation gestures; using a horizontal swipe inside that container is conflict-prone. Tap-to-dismiss with confirm is the safer wear idiom and matches every other 5f screen.
- **No TSLIM_X2 caveat banner.** Mobile shows "Notifications cannot be dismissed on this device model" for t:slim X2 pumps. The watch dispatches the request anyway — pump silently rejects on TSLIM_X2 — so users on that model will see the alert remain after tapping. Tracked as a known limitation; can add a banner later via `dataStore.deviceName` parsing if it becomes a support burden.
- **No per-type filter UI.** All four notification types render in one mixed list; users typically have at most one or two active alerts so filtering would add chrome without value.

##### 5f-5. Watch-side CGM transmitter / sensor session management — ✅ Complete

**Shipped:**
- New `wear/.../presentation/ui/CGMTransmitterScreen.kt` — `ScalingLazyColumn` with three primary action chips plus a status header showing the current `cgmSessionState` (Active / Stopped / Starting / etc.) and `cgmTransmitterStatus` (OK / Expired / OOR / Error). On entry, fires `CGMStatusRequest()` with `SendType.BUST_CACHE` to refresh state.
- **Start G6 sensor session** chip → chained `rememberRemoteTextInputLauncher` calls: G6 transmitter ID (6 chars, validated for length, uppercased) → G6 sensor code (digits, parsed via `toIntOrNull`, `0000` accepted to attach to an in-progress sensor) → in-place wear `Alert` confirmation (shows both the entered tx ID and the sensor code). On confirm, dispatches `SetG6TransmitterIdRequest(txId)` via BUST_CACHE, sleeps 750 ms (mirrors mobile `CGMActions.kt:300-318`'s 3×250 ms wait so the pump applies the tx ID before the start command), dispatches `StartDexcomG6SensorSessionRequest(sensorCode)`, then `CGMStatusRequest()` to refresh.
- **Pair G7 sensor** chip → single `rememberRemoteTextInputLauncher` for the 8-digit pairing code → confirm `Alert` → `SetDexcomG7PairingCodeRequest(code)` + `CGMStatusRequest` refresh. G7 pairs atomically; no separate "start sensor" step.
- **Stop sensor session** chip → confirm `Alert` → `StopDexcomCGMSensorSessionRequest()` + `CGMStatusRequest` refresh.
- Validation errors (non-numeric code, wrong-length tx ID) surface via a generic `InfoAlert` and return the user to the chip menu — same `pendingError` early-return pattern 5f-3 (temp basal U/hr) introduced.
- The two G6 launchers are defined in dependency order inside the composable: `g6SensorCodeLauncher` first, then `g6TxIdLauncher` whose `onResult` chains into it on success. Each `rememberRemoteTextInputLauncher` returns a stable `() -> Unit`, so the chain is just a direct call from one callback to the next — no intermediate state machine needed.
- `Screen.CGMTransmitter` route added with the `SCROLL_TYPE_NAV_ARGUMENT` + `scalingLazyListState` + `RequestFocusOnResume` plumbing (matches `HistoryLog` / `BasalDetail` / `Notifications` pattern).
- `SettingsHubScreen.kt` gains a "CGM sensor" chip below "Pump alerts" (not role-gated — works in both `PUMP_HOST` and `CLIENT` via the shared `/to-pump/*` routing).

**Deviations from mobile:**
- **Validation is intentionally minimal.** Mobile uses `OutlinedTextField` with format constraints (regex / hex digits) plus separate `DexcomG6TransmitterCode` / `DexcomG6SensorCode` composables. Watch checks length on the tx ID (must be exactly 6 chars) and `toIntOrNull` on the codes; everything stricter is enforced by the pump itself, which surfaces rejections via the existing `cgmSessionState` state flow on the next `CGMStatusRequest`. Less defensive but adequate — the pump is the authoritative validator.
- **No two-field text input on a single screen.** Mobile shows tx ID + sensor code together in one `AlertDialog` with two `OutlinedTextField`s. Watch breaks them into two separate `RemoteTextInput` system dialogs because that's the only multi-line entry primitive available on Wear OS at this `wear-input` version.
- **No "in-progress" gating.** Mobile guards the Start button with `enabled = startG6CgmSessionInProgressTxId == null` to prevent double-clicks. Watch dispatches synchronously inside a `refreshScope.launch { … }` block, so a double-tap on the confirm Alert just sends the same dispatch twice — pump will reject the duplicate. Acceptable on watch where the confirm dialog itself is a stronger gate than mobile's button-disable.
- **No `GetSavedG7PairingCodeRequest` round-trip to display the existing G7 code.** Mobile fetches it on entry. Watch just shows the session state header. Adding the saved-code display is a one-line addition once `dataStore.savedG7PairingCode` is plumbed; deferred since users typically don't need to read back the code they just entered.

##### Phase 5 complete

5e-3 also shipped on this branch (Canvas renderer, see the 5e-3 block above). Every Phase 5 surface is now in place: 5a–5d, 5e-1/5e-2/5e-3, and 5f-1 through 5f-5. The only remaining Phase 5 work is build/verification (a clean `./gradlew :wear:assembleDebug :mobile:assembleDebug :shared:testDebugUnitTest :db:testDebugUnitTest` pass on this branch) and the open xDrip+ Wear OS receiver question from Phase 4.5 — both tracked in the "Build/verification gaps" callout below.

### Audit work completed alongside Phase 5 (commits `15b4686`, `ff130ac`, `5eeafa3`)

Three rounds of self-audit landed on top of 5a–5d:

- **Tier 1 (correctness):** `PumpBondedNeedsUnbondScreen` for 5b; `LastConnectionText` formatting + dead-branch removal + primary-chip contrast for 5c; `currentPumpSid()` guard for 5d Nightscout enable.
- **Tier 2 (polish):** `AutoCenteringParams()` on Phase-5d lists; Settings chip text label; `compactUrlLabel` truncation; `remember { ... }` caching of `deviceRole()` reads; dead `currentDeviceRole = newRole` removed; re-pair dialog copy parity.
- **Tier 3 (systemic cleanups):** `triggerAppReload` extracted to new `shared/util/AppReload.kt` — replaces **five near-identical copies** (mobile `CommService`, mobile `MainActivity`, mobile `PumpSetup.kt`, watch `MainActivity`, `WearPumpCommService`). Typed `PairingCodeEntry` dispatch (`applyForInitialPumpComm` / `applyForRePair`) so stringly-typed stage names can't silently mis-match. Pump-finder enable-pref write reordered on watch to shrink the race window before `STOP_PUMP_FINDER` is processed.

### Verification pattern per sub-step

- **Phone-as-host regression:** flip role back via 5a UI, confirm nothing broke.
- **Watch-as-host end-to-end:** pair from watch UI, bolus, confirm Nightscout upload, verify history-log rows persist, swap role back.

**Build/verification status before merging to `dev`:**
- ✅ **Closed (April 25, 2026):** ran `./gradlew :mobile:assembleDebug :wear:assembleDebug :shared:testDebugUnitTest :db:testDebugUnitTest --console=plain` in this environment. Build + unit-test closeout suite passed end-to-end.
- ⚠️ **Still open:** xDrip+ **receiver-side** behavior on Wear OS. Sender-side plumbing is resolved (commit `2758ad2` added the `<queries>` block on both mobile and wear), but there was no connected watch available in this environment (`adb devices -l` returned no devices), so the on-device ADB smoke test against a real watch xDrip+ install is still required before relying on xDrip+ uplinks in watch-as-host mode.

### Outstanding Phase 0 extractions — updated status

- `PairingManager` (Phase 0 step 4) — **partially addressed.** 5b extracted the small post-`SET_PAIRING_CODE` dispatch into `pumpcomm/pump/pairing/PairingCodeEntry.kt` (later split into typed `applyForInitialPumpComm` / `applyForRePair` in Audit Tier 3). The larger pairing surface — `sendPumpPairingMessage()`, `sendInitPumpComm()`, and pairing-code handling inside `handleMessageReceived()` — still lives inlined in `CommService.kt`, with the watch-side handlers duplicating parts of it in `WearPumpCommService.kt`. Worth revisiting before 5f pushes more into that seam.
- `WearMessageForwarder` (Phase 0 step 5) — **still not done.** `sendWearCommMessage()` calls remain scattered in `CommService.kt`. Deferrable unless 5e/5f extends that surface.

**Decision (April 25, 2026):** Defer both extractions to a post-merge hardening pass ("Phase 6: service-internals cleanup"). They are maintainability improvements, not blockers for watch-as-host feature completeness, and deferring avoids late risk to a now-stable 5a–5f surface.

---

## Implementation Order & Dependencies

```
Phase 0   (decompose CommService)                    ✅ Partial (PairingManager partially addressed via 5b PairingCodeEntry;
                                                                 WearMessageForwarder still deferred)
    ↓
Phase 1   (rename message paths)                     ✅ Complete (bolus path collapse skipped)
    ↓
Phase 2   (extract :pumpcomm module)                 ✅ Complete (class names differ from plan)
    ↓
Phase 3   (extract :clientcomm module)               ✅ Complete (interface-based, not abstract class)
    ↓
Phase 4   (role-switching logic)                     ✅ Complete (UI shipped in Phase 5a)
    ↓
Phase 4.5 (extract :db module — sync engines + DB)   ✅ Complete
    ↓
Phase 5   (watch pump-host UI)                       ✅ Complete:
           5a DeviceRole settings UI                 ✅ Complete (commit 6660f11)
           5b Watch-side pump pairing flow           ✅ Complete (commit 6c6d4d6)
           5c Connection status + reconnection UX    ✅ Complete (commit ca97612)
           5d Nightscout / xDrip+ settings on watch  ✅ Complete (commit c61435f)
           + Audit Tiers 1/2/3                       ✅ Complete (commits 15b4686, ff130ac, 5eeafa3)
           5e Pump data surfaces on watch            ✅ Complete:
             5e-1 Watch-side history/events screen   ✅ Complete (commit 0125d58)
             5e-2 Basal rate display screen          ✅ Complete (commit 6581260)
             + 5e post-ship audit                    ✅ Complete (commit c9fe04b)
             5e-3 CGM trend-graph screen             ✅ Complete (this branch, Canvas)
           5f Settings management parity             ✅ Complete:
             5f-1 Watch-side suspend/resume insulin  ✅ Complete (commit 1935e0d)
             5f-2 Watch-side active profile picker   ✅ Complete (commit 744fa12)
             5f-3 Temp basal + Sleep/Exercise hub    ✅ Complete (this branch)
             5f-4 Pump alert / alarm dismissal       ✅ Complete (this branch)
             5f-5 CGM transmitter / sensor session   ✅ Complete (this branch)
           + xDrip+ queries fix (enables broadcasts) ✅ Complete (commit 2758ad2, cross-cutting 4.5 + 5)
```

Each phase is independently shippable. Phases 0-1 are pure refactors with no behavior change. Phase 2-3 are structural extractions. Phase 4 is the first user-visible feature (role selection shipped in 5a). Phase 5 is the full watch-as-host experience and is complete on this branch — 5a–5f all shipped and the closeout Gradle suite passed on April 25, 2026. The only remaining release-readiness item is the unresolved xDrip+ watch-receiver runtime question from Phase 4.5 (requires real-watch validation).

---

## Resolved Design Decisions

### 1. Data sync in watch-as-host mode
**Decision:** The primary (pump-host) device handles external syncs directly — no forwarding. Sync code lives in a dedicated shared `:db` module (extracted in Phase 4.5).

- **Phone-as-host:** Phone runs the shared `NightscoutSyncWorker` and `XdripMessageDispatcher` from `:db`, all local.
- **Watch-as-host:** Watch runs the same `:db` `NightscoutSyncWorker` and `XdripMessageDispatcher` directly on the watch. Nightscout uploads work end-to-end. xDrip+ behavior on Wear OS is an open runtime question — the broadcast Intent dispatched by `XdripBroadcastSender` is device-local, and whether xDrip+ exposes a watch-side receiver is unverified (see Phase 4.5 TODO).

**Implication for architecture:** Sync logic (Room DB, Nightscout HTTP, xDrip+ broadcasts) lives in `:db`. Both `mobile` and `wear` apps link against it. No `DataSyncDelegate` indirection — `WearPumpCommService` and `CommService` both call the same `:db` entry points directly.

### 2. PumpCommService layering
**Original decision:** Two distinct classes for core vs lifecycle within `:pumpcomm`.

**Actual implementation:** The core/lifecycle split is realized as a **callback seam** rather than two classes. `PumpCommHandler` holds connection state + messaging; lifecycle concerns (service lifecycle, foreground notification, pairing UI callbacks, reconnection decisions) sit behind the `CommServiceCallbacks` interface, which the hosting Service (`CommService` on mobile, `WearPumpCommService` on wear) implements. Different lifecycle strategies on phone vs watch are achieved by each hosting Service implementing `CommServiceCallbacks` differently — not by having two separate library classes.

This lets `:pumpcomm` stay transport-aware-but-UI-agnostic without forcing two mandatory classes on every call site.

### 3. Message path naming
**Decision:** Use `/to-server/` and `/to-client/` scheme. Path constants in `shared/MessagePaths.kt`.

| Original (pre-Phase 1) | Actual (post-Phase 1) | Notes |
|------------------------|-----------------------|-------|
| `/to-phone/start-comm` | `/to-server/start-comm` | |
| `/to-phone/stop-comm` | `/to-server/stop-comm` | |
| `/to-phone/comm-started` | `/to-server/comm-started` | |
| `/to-phone/bolus-request-wear` | `/to-server/bolus-request-wear` | ⚠️ Collapse to `/to-server/bolus-request` was skipped; sender-origin disambiguation still encoded in path |
| `/to-phone/bolus-request-phone` | `/to-server/bolus-request-phone` | ⚠️ Same — still a separate path from the wear variant |
| `/to-phone/bolus-cancel` | `/to-server/bolus-cancel` | |
| `/to-phone/is-pump-connected` | `/to-server/is-pump-connected` | |
| `/to-phone/set-pairing-code` | `/to-server/set-pairing-code` | |
| `/to-phone/start-pump-finder` | `/to-server/start-pump-finder` | |
| `/to-wear/service-receive-message` | `/to-client/service-receive-message` | ⚠️ Original proposal was `/to-client/pump-message`; kept the literal name to minimize diff |
| `/to-wear/glucose-unit` | `/to-client/glucose-unit` | |
| `/to-wear/bolus-not-enabled` | `/to-client/bolus-not-enabled` | |
| `/to-wear/connected` | `/to-client/connected` | |
| `/to-pump/*` | `/to-pump/*` | No change |
| `/from-pump/*` | `/from-pump/*` | No change |

**Deviations** (see Phase 1 section for rationale): the two bolus-request paths kept their `-wear` / `-phone` suffixes, and `service-receive-message` was not renamed to `pump-message`. Both can be cleaned up later without blocking any feature.
