# Phase 5d — Watch-side Nightscout / xDrip+ settings + Settings hub

Per `docs/watch-as-host-refactor-plan.md` §5d. Builds on 5a/5b/5c. Goal: let
the user configure Nightscout sync and xDrip+ broadcasts from the watch when
it's running as pump-host, without round-tripping to the phone.

## Plan-doc corrections (code is source of truth)

- Nightscout config lives in `"controlx2"` prefs (not `"WearX2"`) — matches
  mobile `NightscoutSettings.kt:62` and watch service wiring
  `WearPumpCommService.kt:330`.
- xDrip config lives in `"WearX2"` prefs — matches `XdripMessageDispatcher.kt:43`.
- Status store is `NightscoutSyncStatusStore` (plan doc called it
  `NightscoutStatusStore`).
- User has authorized treating the xDrip+ broadcast path as working on Wear OS;
  we'll expose the toggle without a verification caveat.

## Consistency anchors (mobile patterns we mirror)

| Concern | Mobile pattern | Watch equivalent |
| --- | --- | --- |
| Persist Nightscout config | `NightscoutSyncConfig.save(prefs)` to `"controlx2"` | Same — no new API. |
| Persist xDrip config | `XdripSyncConfig.save(prefs)` to `"WearX2"` | Same — no new API. |
| Reload on config change | `XdripSettings.kt:62-69`: if `requiresReloadComparedTo()`, 250 ms + FORCE_RELOAD + 250 ms + APP_RELOAD | Same sequence via the helpers we added in 5c (`MainActivity.reEnablePumpService` is enable-only; we'll inline the sequence in settings screens). |
| Text entry for URL/secret | Material3 `OutlinedTextField` dialogs | `RemoteInputIntentHelper` — same pattern `PairingCodeEntryScreen` uses. |
| Sync status display | `NightscoutSyncStatusStore.load` + formatted timestamps | Same. |

## Concrete changes

### 1. Screen routes (`wear/.../presentation/navigation/Screen.kt`)

```
object SettingsHub : Screen("SettingsHub")
object NightscoutSettings : Screen("NightscoutSettings")
object XdripSettings : Screen("XdripSettings")
```

### 2. `SettingsHubScreen` (new)

`ScalingLazyColumn` with entries that are visible only when relevant:

- "Role" → `Screen.RoleSelection`
- "Nightscout" → `Screen.NightscoutSettings` (only if `DeviceRole.PUMP_HOST`)
- "xDrip+" → `Screen.XdripSettings` (only if `DeviceRole.PUMP_HOST`)
- "Force reload" → `sendPhoneCommand("force-reload")`
- "Open on phone" → `sendPhoneOpenActivity`

Role-gating keeps CLIENT mode from showing pump-host-only settings; the phone
owns those in client mode.

### 3. `NightscoutSettingsScreen` (new)

Reads/writes `"controlx2"` prefs via `NightscoutSyncConfig.load/save`.

UI (top-to-bottom in a `ScalingLazyColumn`):
- Status card: "Enabled" / "Disabled" toggle chip.
- "URL: <current-or-Not-set>" — tap → `RemoteInput` for URL.
- "API secret: <masked>" — tap → `RemoteInput` for secret.
- Sync status row: "Last synced N ago" / "Last error: <msg>" via
  `NightscoutSyncStatusStore.load`. Hidden when no status recorded.

Save path: build new `NightscoutSyncConfig`, call `NightscoutSyncConfig.save(prefs, newConfig)`, and dispatch the mobile reload sequence if the enable flag changed (mirroring `XdripSettings.kt:62-69`). URL/secret edits don't need a reload — the worker re-reads config each tick.

**Deferred to phone-only**: processors, sync interval, initial lookback. The
screen can display "Edit advanced settings on phone" as a footer line.

**Deferred**: explicit "Sync now" button. `NightscoutSyncWorker.syncNow()` is an
instance method that only works when the worker is already running; from a
Settings screen there's no reliable handle. Skipping keeps the screen simpler.

### 4. `XdripSettingsScreen` (new)

Reads/writes `"WearX2"` prefs via `XdripSyncConfig.load/save`.

UI:
- Enable toggle chip.
- Four payload toggles: CGM SGV, pump device status, treatments, status line.
- Save triggers the reload sequence if
  `requiresReloadComparedTo(old)` returns true (only enable toggle does).

No sync status to show (xDrip+ is fire-and-forget broadcast; no failure
feedback from the receiver).

### 5. Landing footer collapse (`LandingFooterActions.kt`, `LandingScreen.kt`)

Replace the three chips (force-reload, open-phone, role) with a single
"Settings" chip (icon: `Icons.Filled.Settings`) that navigates to
`Screen.SettingsHub`. Move the three existing actions into the hub.

- `LandingFooterActions` param collapses from
  `(onForceReload, onOpenPhone, onRoleSelection)` to `(onSettings)`.
- `LandingScreen` callsite updates; `sendPhoneCommand`/`sendPhoneOpenActivity`
  are plumbed through to `SettingsHubScreen` directly.

### 6. Text input helper (`wear/.../presentation/ui/`)

The URL / secret input flow is repeated between the two settings screens.
Extract a small helper — either a composable wrapper around
`rememberLauncherForActivityResult` + `RemoteInputIntentHelper` or just a
shared `launchRemoteTextInput(label, initial, onResult)` function. Keep it
close to `PairingCodeEntryScreen`'s approach.

## Out of scope

- **"Sync now" button** — deferred (worker instance is only addressable while
  running).
- **Processors / interval / lookback** editors — the screen will mention these
  live on the phone.
- **xDrip+ broadcast verification on Wear OS** — user has authorized proceeding
  as if it works.
- **Mobile changes** — none. Mobile already has its own settings screens.
- **`:shared` / `:db` / `:pumpcomm` / `:clientcomm`** — no changes.

## Files touched (estimate)

- `wear/.../presentation/navigation/Screen.kt` — three new routes
- `wear/.../presentation/WearApp.kt` — three new composables wired
- `wear/.../presentation/ui/SettingsHubScreen.kt` — **new**
- `wear/.../presentation/ui/NightscoutSettingsScreen.kt` — **new**
- `wear/.../presentation/ui/XdripSettingsScreen.kt` — **new**
- `wear/.../presentation/ui/components/LandingFooterActions.kt` — collapse
- `wear/.../presentation/ui/LandingScreen.kt` — update footer wiring

## Verification

Sandbox has no Android toolchain — rely on code-review + structural correctness
against mobile's parallel screens. On-device verification is the user's
follow-up (same pattern as 5b / 5c).
