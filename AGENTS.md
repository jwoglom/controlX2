# ControlX2 – Repository Guide

This document summarizes the structure, design, and common workflows inside the ControlX2 project. It is intended to help future AI agents orient themselves quickly when making changes.

## High-level architecture
- The project is an Android multi-module Gradle build with `mobile` (phone UI + background service), `wear` (Wear OS UI + complications), and `shared` (cross-module utilities and serializers) modules.
- Both apps depend on the external PumpX2 libraries (`pumpx2-android`, `pumpx2-messages`, `pumpx2-shared`) for the Tandem pump protocol, alongside Android Jetpack (Compose, Room, DataStore) and Google Play Services for Wearable messaging.
- The mobile app runs a long-lived `CommService` that speaks Bluetooth to the pump and mirrors data to the wear app through the Wearable Message/Data APIs. Compose-based UIs in both apps observe shared `DataStore` instances backed by `MutableLiveData` to display the latest state.

## Build & dependency notes
- Global Gradle config in `build.gradle` defines versions (`controlx2_version`, `pumpx2_version`, `compose_version`, `kotlin_version`, etc.) and optionally switches PumpX2 dependencies between Maven Central/JitPack vs. locally published artifacts via the `use_local_pumpx2` property.
- `mobile/build.gradle` and `wear/build.gradle` apply Compose and `hu.supercluster.paperwork` plugins, depend on the `shared` module, and include Play Services Wearable, Room (mobile only), and numerous Compose material libraries. The wear app also bundles Horologist and wear-compose libraries, plus watchface APIs for complications.
- Signing defaults to the provided debug keystore unless overridden with `RELEASE_*` system properties.
- To work on PumpX2 protocol messages you often need the PumpX2 repo checked out locally; follow the README’s instructions if you need to publish new jars/aar into `~/.m2` and flip `use_local_pumpx2`.

## Running & testing
- Android Studio remains the easiest path: open the repo, select either the `mobile` or `wear` run configuration, and click **Run** to install on a connected device or emulator. Pair an actual Tandem pump only when you need hardware tests—most UI adjustments can be previewed without hardware.
- Command-line builds:
  - `./gradlew :mobile:assembleDebug` / `:wear:assembleDebug` produce installable debug APKs.
  - `./gradlew :mobile:installDebug` (and the wear equivalent) deploy directly to a connected device over ADB.
  - `./gradlew :shared:lintDebug` runs lint on the shared code; `:mobile:lintDebug` and `:wear:lintDebug` catch Compose/manifest issues for each app.
  - `./gradlew testDebugUnitTest` executes JVM unit tests across modules (there are few today, but the task guards against regressions when new ones are added).
  - `./gradlew :mobile:connectedDebugAndroidTest` (or `:wear:connectedDebugAndroidTest`) runs instrumentation tests; requires an attached device/emulator with the service enabled.
- Compose previews are heavily relied upon for iteration. Use `setUpPreviewState` (mobile) or the preview helpers inside wear UI files to seed fake data—this prevents Compose previews from crashing when new observable fields are introduced.
- When PumpX2 artifacts change, clear Gradle caches with `./gradlew --stop && ./gradlew clean` or `./gradlew build --refresh-dependencies` before rebuilding so the new protocol definitions flow through to both apps.

## `shared` module
- Holds code shared between phone and watch:
  - `CommServiceCodes` enumerates the message types that `CommService`’s handlers understand.
  - Serializers (`PumpMessageSerializer`, `InitiateConfirmedBolusSerializer`, `PumpQualifyingEventsSerializer`) bridge PumpX2 message objects to JSON/byte blobs suitable for Wear messaging, notification extras, or persistence.
  - Enum wrappers (`BasalStatus`, `CGMSessionState`, `UserMode`) give more meaningful display strings.
  - Utility helpers (`SendType`, `UnitsRenderer`, `TimberUtil`, `DebugTree`) centralize formatting, logging configuration, and message routing into Timber. `setupTimber` is used by both apps to pipe PumpX2’s `L` logging callbacks through Timber with file logging gates via `ShouldLogToFile` (mobile) or verbose toggles.
  - `SendType` determines whether pump commands should be sent freshly (`STANDARD`), bust the cached response, re-use cached data, or trigger debug prompts. Respect these when adding new message flows so caching keeps working.

## `mobile` module
### Entry points & global state
- `MainActivity` hosts the Compose UI. It initializes logging, determines the navigation start destination (FirstLaunch → PumpSetup → AppSetup → Landing), binds to the Wearable Message API, and proxies UI actions back to the background service through lambdas (`sendMessage`, `sendPumpCommands`, `sendServiceBolusRequest`, etc.).
- `DataStore` (mobile) is a `MutableLiveData` hub for pump, CGM, and bolus state. UI layers observe it; service and message handlers update it. It also logs every mutation via Timber (heavy logging in production).
- `LocalDataStore` composition local exposes the singleton `DataStore` to Composables.
- `Prefs` wraps shared preferences and controls feature toggles (service enablement, pairing, insulin delivery actions, connection sharing, verbose logging, history-log auto-fetch). Always update prefs via this helper to keep behaviour consistent.

### Background service (`CommService`)
- Extends `WearableListenerService` and runs inside the phone app even when the UI is closed.
- Internally owns two `Handler` subclasses:
  - `PumpCommHandler` manages a `TandemPump` instance for real pump communication. It handles scanning, pairing (including `PumpFinder` hand-off), sending commands, caching recent responses (`lastResponseMessage`), bulk command routing, cache busting, bolus handling (with safety checks to require confirmation unless below threshold), and periodic refresh tasks.
  - `PumpFinderCommHandler` is a lighter handler that uses `TandemPumpFinder` to discover pumps during initial setup.
- Message routing:
  - Wear ↔ Phone path strings follow a convention (`/to-phone/...` for watch→phone, `/to-wear/...` for phone→watch, `/from-pump/...` for pump→clients). The service re-broadcasts pump responses to both the phone UI and wear app using `sendWearCommMessage`.
  - `CommServiceCodes` values map to handler operations (start/stop, send command, cached command, pump finder, debug dumps, etc.). When adding new service actions, add enum cases and handle them in the relevant handler.
- Bolus flow: `confirmBolusRequest` builds a signed payload stored in prefs, sends notifications for user confirmation, and eventually routes to `sendPumpCommBolusMessage`. `BolusNotificationBroadcastReceiver` listens for notification actions, validates signatures via `InitiateConfirmedBolusSerializer`, and forwards commands (or cancellation) to the pump.
- `HistoryLogFetcher` coordinates background retrieval of pump history logs (with `LruCache`, concurrency control via coroutines + `Mutex`, and DB persistence).
- `AppVersionCheck` scheduled via `checkForUpdatesTask` hits `https://versioncheck.controlx2.org` shortly after service start when the user allows update checks.
- Service also listens to BLE state broadcasts, maintains a persistent notification summarizing pump status, and saves recent pump data into `DataClientState` (so the wear app can access last-known values via DataClient/SharedPreferences).

### Data persistence & DB layer
- `HistoryLogDatabase` is a Room database storing `HistoryLogItem` entities keyed by sequence ID + pump SID. `HistoryLogDao` exposes typed queries for counts, ranges, and latest logs, while `HistoryLogRepo`/`HistoryLogViewModel` provide coroutine/LiveData wrappers for the UI.
- `HistoryLogFetcher` uses the repo to detect missing IDs and issues batches of `HistoryLogRequest`s until the DB is filled.
- `db/util/Converters` handle `LocalDateTime` ↔ epoch conversions.

### UI structure (Compose)
- `MobileApp` wraps the root `NavHost` and injects the lambdas that ultimately talk to `CommService`. Its four destinations form the top-level flow (FirstLaunch → PumpSetup → AppSetup → Landing) and can be deep-linked when onboarding state in `Prefs` changes.
- Primary screens:
  - **FirstLaunch** presents the legal disclaimer inside `DialogScreen`; agreeing flips ToS/service prefs and kicks off pump discovery via `/to-phone/start-pump-finder`, while cancelling terminates the process.
  - **PumpSetup** walks through pairing using `PumpSetupStage`. It surfaces pairing code entry, advanced settings, and progress UI (`PumpSetupStageProgress`/`Description`). Button wiring manipulates `Prefs`, advances or rewinds `PumpSetupStage`, and pushes commands like `/to-phone/set-pairing-code` or `/to-phone/start-pump-finder`.
  - **AppSetup** configures global prefs (connection sharing, insulin actions, confirmation thresholds, update checks, history-log fetching). Each toggle mutates `Prefs`, triggers `/to-phone/app-reload`, and conditionally shows warnings (e.g., AlertDialog before enabling insulin delivery actions).
  - **Landing** is the authenticated home surface. It renders a top bar reflecting pump connectivity, a Material3 bottom nav built from `LandingSection`, and a `BottomSheetScaffold` that hosts bolus/temp-rate sheets. It hoists the currently selected section, bottom-sheet visibility, and nav actions (`navigateToSection`, `openTempRateWindow`).
- Landing sections (all live in `presentation/screens/sections`):
  - **Dashboard** (`Dashboard.kt`) drives the overview cards. It fetches pump status (`dashboardCommands`), hydrates `dashboardFields`, shows `PumpStatusBar`, CGM chart (`DashboardCgmChart`), Control-IQ status, and latest notifications/history banners. It reuses `ServiceDisabledMessage` and setup progress widgets when the pump is offline.
  - **Notifications** lists active pump alerts by observing `notificationBundle` and mapping items into `NotificationItem` rows. Refresh logic mirrors the dashboard but emphasises that some devices cannot dismiss notifications.
  - **Actions** surfaces pump control toggles. It reads `actionsFields`, exposes suspend/resume pumping menus, manual temp-rate entry (via `TempRateWindow`), and shortcuts to bolus/debug sections. Menu booleans gate confirmation dialogs before dispatching control requests (e.g., `SuspendPumpingRequest`).
  - **CGMActions** handles Dexcom management. It offers menus for starting/stopping G6/G7 sessions, transmitter ID entry (`DexcomG6TransmitterCode`, `DexcomG6SensorCode` components), and fetches saved pairing codes. Alerts guard destructive actions and ensure the appropriate PumpX2 request (e.g., `SetDexcomG7PairingCodeRequest`) fires.
  - **CartridgeActions** drives cartridge/fill workflows. It enables enter/exit cartridge and fill modes, tracks state streams (`EnterChangeCartridgeModeStateStreamResponse`, etc.), and requests cannula fill volumes with validation through `DecimalOutlinedText`.
  - **ProfileActions** orchestrates profile (IDP) management by iterating `IDPManager.nextMessages()`, presenting basal schedule/program lists, and exposing profile-activation/delete actions with confirmation dialogs. It tracks state progress across multiple request rounds.
  - **Settings** hosts service toggles (start/stop service, reconfigure pump, debug options entry). Items send `/to-phone/force-reload`, navigate back into setup flows, and provide build metadata (`VersionInfo`).
  - **Debug** is the power-user toolbox: it shows cached pump messages, provides arbitrary message senders, log export/clear options, database wipe, history log fetch utilities, and in-app calculators. Most actions require double-checking `Prefs` and dispatching raw PumpX2 messages for diagnostics.
- Modal sheets:
  - **BolusWindow** collects bolus units/carbs/BG with `DecimalOutlinedText` & `IntegerOutlinedText`, watches bolus calculator LiveData, and orchestrates the multi-step permission/signature flow (`BolusPermissionRequest`, `sendServiceBolusRequest`, cancellation handling). It aggressively resets state using `resetBolusDataStoreState` to avoid stale calculator inputs.
  - **TempRateWindow** manages temporary basal rates with numeric inputs, `SetTempRateRequest` validation, and progress dialogs. It intentionally skips cached reads for safety and resets `DataStore` temp-rate fields whenever the sheet closes.
- Shared Compose components live in `presentation/components` and `presentation/screens/sections/components`:
  - `DialogScreen` standardizes title/body/button layout for modal-style screens (FirstLaunch, setup steps).
  - `HeaderLine`, `Line`, and `ServiceDisabledMessage` provide consistent typography and service gating banners.
  - `PumpStatusBar`, `HorizBatteryIcon`, and `HorizCartridgeIcon` summarize pump vitals.
  - `DashboardCgmChart`, `CurrentCGMText`, and related CGM widgets visualize glucose trends.
  - Form inputs (`DecimalOutlinedText`, `IntegerOutlinedText`, Dexcom code fields) convert between human-entered strings and the raw ints/floats stored in `DataStore`.
  - `VersionInfo` reads Paperwork build metadata for display across both setup and settings flows.
- `setUpPreviewState` primes `LocalDataStore` with mock data for previews; when adding new LiveData fields update it to prevent preview crashes.
- `LifecycleStateObserver`, `FixedHeightContainer`, and focus helpers such as `TextFieldOnFocusSelect` avoid duplicate refreshes, enforce layout constraints, and improve text-field UX.

### Messaging helpers & utilities
- `DataClientState` writes summarized pump data into the Wearable `DataClient` (key/value pairs) for complications; `PhoneCommService` on the watch reads them back via `StatePrefs`.
- `AppVersionCheck/AppVersionInfo` handle version telemetry and update notifications.
- `ShouldLogToFile` gates file logging based on prefs.
- `AppVersionCheck`, `VolleyQueue`, and network calls rely on the `android.permission.INTERNET` declared in the manifest.
- `LaunchCommServiceBroadcastReceiver` exists as a stub (currently no-op) if you need to bootstrap the service from broadcast events (e.g., BOOT_COMPLETED).

## `wear` module
### Entry points & navigation
- `MainActivity` (wear) initializes Timber, reads the start route from the launching intent, creates `WearApp`, and wires send lambdas similar to the mobile activity. It also listens for Wearable messages to update the local `DataStore` and routes actions to the phone via `sendMessage` or `sendPumpCommands`.
- `WearApp` defines a `SwipeDismissableNavHost` whose destinations come from `wear.presentation.navigation.Screen` (waiting states, landing dashboard, bolus flow, mode prompts, etc.). It wraps everything in a Horologist `Scaffold` so the time text, vignette, and position indicator automatically hide/show while scrolling.
  - Theme colors are read from `WearAppTheme`; user toggles for vignette/time text are persisted via `rememberSaveable`.
  - Scroll state is hoisted through `ScalingLazyListStateViewModel`/`ScrollStateViewModel`, allowing `PositionIndicator` and fade-out time text to reuse state on process death.
  - Bolus inputs (units/carbs/BG) live in hoisted mutable state so intermediate pickers can round-trip values before calling the phone service.
  - `RequestFocusOnResume` ensures rotary input focuses the active list when returning to Landing/Bolus, and `BottomText` renders the watch face footer on each screen.
  - Waiting/alert routes lean on `IndeterminateProgressIndicator`, `Alert`, or `FullScreenText` helpers, while `ReportFullyDrawn` marks startup completion for performance tools.
- The wear `DataStore` mirrors the fields in the phone store but omits some phone-only aspects. It also logs every update via Timber for debugging.

### Communication service & state sharing
- `PhoneCommService` (wear) mirrors `CommService` but only handles Wearable messaging, notifications about connection state, and updates `StatePrefs` for complications. It starts in the foreground, listens for pump status messages from the phone (`/from-pump/...` paths), and forwards them to the watch UI via `LocalDataStore`. It also ensures notifications are shown when the phone disconnects or background actions occur.
- `StatePrefs` persists small bits of state (connection, battery, IOB, CGM reading) in shared preferences; use this when complications need last-known values offline.
- `UpdateComplication` triggers watchface complication refreshes when new data arrives.
- `BolusActivity` is a simple launcher that opens `MainActivity` directly to the bolus screen (used by complications).

### UI & theming
- `WearAppTheme` bundles multiple color palettes (`defaultTheme`, `greenTheme`, `redTheme`, etc.) and exposes typography that mirrors ControlX2 branding on the round display. Screens call `WearAppTheme` indirectly through `WearApp`, so update the palette definitions when adding new color families.
- Primary watch screens in `presentation/ui`:
  - **WaitingForPhone / WaitingToFindPump / ConnectingToPump / PairingToPump / MissingPairingCode / PumpDisconnectedReconnecting** show progress via `IndeterminateProgressIndicator`, keeping the user informed during hand-offs between the phone service and pump discovery.
  - **LandingScreen** renders the watch dashboard. It uses a `ScalingLazyColumn` with `FlowRow` chips to display pump vitals (`LineInfoChip`, `CurrentCGMText`), quick actions (sleep/exercise toggles, open-on-phone, bolus entry), and status summaries. It drives refreshes through `sendPumpCommands`, reacts to `LocalDataStore`, and delegates navigation to `Screen.*` routes when chips are tapped.
  - **SleepModeSet** and **ExerciseModeSet** pop Horologist `Alert` dialogs that let the user toggle Control-IQ modes. Buttons dispatch `SetModesRequest` commands, and the dialog text adapts to the current `UserMode` from `DataStore`.
  - **BolusScreen** mirrors the phone bolus window: it walks through calculator states, displays condition prompts, drives permission/confirmation dialogs, and forwards approved requests to the phone via `sendPhoneBolusRequest`. The screen maintains booleans for each dialog (permission, confirm, in-progress, cancelled, approved) and listens to `LocalDataStore` for calculator updates.
  - **BolusSelectUnits/Carbs/BG** screens provide rotary-friendly pickers (`DecimalNumberPicker`, `SingleNumberPicker`) with exponential scrolling, respect pump-imposed maxima, and write results back to the hoisted bolus state before popping.
  - **BolusBlocked**, **BolusNotEnabled**, and **BolusRejectedOnPhone** surface blocking conditions with `FullScreenText` or `Alert` UI, guiding the user back to Landing when manual intervention is required.
- Supporting composables:
  - `FullScreenText`, `IndeterminateProgressIndicator`, and `ReportFullyDrawn` (startup reporting) live alongside the screens for reuse.
  - `presentation/components` houses chips (`FirstRowChip`, `LineInfoChip`, `MiniChip`), text widgets (`TopText`, `BottomText`, `CustomTimeText`), glucose displays (`CurrentCGMText`), and numeric pickers. Each component expects a `LocalDataStore` context and handles round-watch ergonomics (padded touch targets, rotary input, large text).
  - `presentation/util` adds helpers like `LifecycleStateObserver` that mirror the mobile patterns for periodic refresh and state reset.

### Complications
- Complication services under `wear/complications` supply pump battery, pump IOB, CGM reading, and quick actions (bolus/app launch). They share helper data classes (e.g., `ButtonComplicationData`) and use `StatePrefs` to read cached values. When adding new complications, follow the existing pattern: build data via `DataFields`, respect recency thresholds, and provide preview data.
- `ComplicationToggleReceiver` and `ComplicationToggleArgs` support interactive complications (currently mostly boilerplate for future toggles).

## Messaging patterns & helper enums
- Message paths:
  - Phone UI ↔ service: Compose screens call `sendMessage` or `sendPumpCommands` (with `SendType`) which forward to `MainActivity` → Wearable MessageClient → `CommService`.
  - Phone service → UI/watch: `CommService` uses `sendWearCommMessage` to broadcast pump events (`/from-pump/...`) and service state updates.
  - Watch → phone: `MainActivity` (wear) or `PhoneCommService` sends `/to-phone/...` messages that `CommService` handles in `onMessageReceived`.
- When introducing new pump operations, decide whether they should bypass cache (`BUST_CACHE`), request cached data (`CACHED`), or just send raw commands. Update the corresponding command list constants so the periodic refresh logic keeps them in sync.
- `CommServiceCodes` + handlers are the authoritative map of what the service understands. Always add new codes there and ensure both handlers deal with them if needed (e.g., PumpFinder vs PumpComm).

## Debugging & logging
- Timber is globally configured via `setupTimber` with a custom `DebugTree` that can write to files (`debugLog-*.txt`) when enabled. File logging path is `/data/user/0/com.jwoglom.controlx2/files/`.
- The mobile Debug screen lets users send arbitrary pump messages, inspect caches/history logs, share logs, and toggle developer settings (Only Snoop Bluetooth, verbose logging, etc.). When you add new debugging utilities, hook them into this screen for easy access.
- `ShouldLogToFile` uses prefs to restrict which tags are persisted; keep tags consistent (e.g., `L:Messages`, `CommService`) to benefit from existing filters.

## Versioning & update checks
- Build metadata is provided by the Paperwork plugin (`build_time`, `build_version`) and shown in the UI via `VersionInfo` components.
- `AppVersionCheck` posts device metadata to the version server and notifies users via `NotificationCompat` if a newer build exists. Respect the `Prefs.checkForUpdates()` toggle before initiating network calls.

## Compose previews & testing tips
- `setUpPreviewState` (mobile) and default preview functions populate `LocalDataStore` for Compose previews; reuse when creating new screens/components so previews render meaningful data.
- Several previews rely on `LocalDataStore` being a mutable singleton. If you change `DataStore`’s constructor, ensure previews still initialize the expected fields.
- For watch UI, `WearApp` previews can use `rememberSwipeDismissableNavController()` and reuse data store stubs similar to the phone.

## Safety toggles & preferences
- Insulin delivery (bolus) actions are disabled by default: `Prefs.insulinDeliveryActions()` must return true before bolus commands are forwarded. UI surfaces and notifications respect this, so any new insulin-affecting feature must check the same preference.
- Connection sharing (`Prefs.connectionSharingEnabled()`) toggles whether the service enables PumpX2 features that coexist with the official t:connect app. Preserve this behaviour when altering pairing/connection flows.
- Many screens abort their refresh loops if `Prefs.serviceEnabled()` is false—keep this guard in mind when adding new polling coroutines to avoid busy loops when the service is stopped.

## Additional development tips
- LiveData observers in `DataStore` log aggressively. When adding new fields, follow the same pattern (initialize `MutableLiveData`, optionally seed a default, and add `observeForever` logging if useful).
- When creating new pump commands, prefer using PumpX2 request builders (`CurrentBatteryRequestBuilder`, etc.) to ensure correct opcode/cargo formatting.
- The watch relies on phone state via Wearable DataClient (`DataClientState`) and SharedPreferences (`StatePrefs`); keep both in sync when introducing new data that complications or offline screens should display.
- Bolus/Temp rate windows manage raw user input via dedicated `MutableLiveData` fields (`bolusUnitsRawValue`, etc.). If you introduce new modal workflows, model them similarly so that state survives recomposition and can be reset cleanly when the modal closes.
- Respect the message throttling/caching logic (`CacheSeconds`, `lastResponseMessage` map). Clearing the cache too aggressively can increase BLE load and battery consumption.
- `HistoryLogFetcher` uses coroutines with `Mutex` to serialize fetch ranges. If you adjust fetch sizes/timeouts, update the constants (`InitialHistoryLogCount`, `FetchGroupTimeoutMs`) thoughtfully.

