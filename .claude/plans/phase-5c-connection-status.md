# Phase 5c — Watch-side connection status + reconnection UX

Per `docs/watch-as-host-refactor-plan.md` §5c. Builds on 5a (DeviceRole UI) and 5b
(watch-side pairing). Goal: promote the placeholder `ConnectingToPump` and
`PumpDisconnectedReconnecting` screens to real status UX, matching the idioms
mobile already uses.

## Consistency anchors (mobile patterns we mirror)

| Concern | Mobile pattern | Watch equivalent |
| --- | --- | --- |
| "Restart the whole stack" | `setServiceEnabled(true)` → 250 ms → `TO_SERVER_FORCE_RELOAD` → 250 ms → `TO_SERVER_APP_RELOAD` (`ServiceDisabledMessage.kt:57-65`) | Same sequence, via `WearPrefs` + watch service/activity handlers. |
| "Stop trying until I say so" | `Prefs.setServiceEnabled(false)` (`Settings.kt:148`) | `WearPrefs.setServiceEnabled(false)` — pref already exists. |
| "Re-enable" affordance | `ServiceDisabledMessage` top-level card | `WearServiceDisabledMessage` — direct port. |
| "Last seen" display | `LastConnectionUpdatedTimestamp` using `ds.pumpConnected` + `ds.pumpLastConnectionTimestamp` + `ds.pumpLastMessageTimestamp` | Same component name on watch, same DataStore field names. |
| Disconnect-button semantics | None explicit on mobile; stop = toggle `serviceEnabled` | Same. Disconnect = `setServiceEnabled(false)` + `TO_SERVER_FORCE_RELOAD`. |
| New message paths | — | **None.** Reuse existing `TO_SERVER_FORCE_RELOAD` / `TO_SERVER_APP_RELOAD`. |

## Concrete changes

### 1. DataStore fields (`wear/.../presentation/DataStore.kt`)

Add three fields with mobile-identical names so a future DataStore share is trivial:

- `pumpConnected: MutableLiveData<Boolean>`
- `pumpLastConnectionTimestamp: MutableLiveData<Instant>`
- `pumpLastMessageTimestamp: MutableLiveData<Instant>`

Add `logOnChange` entries.

### 2. Service → DataStore wiring

- `WearPumpCommService.markConnectionTime()` (line 389) already fires on connect.
  Additionally, the service already emits `FROM_PUMP_PUMP_CONNECTED` /
  `FROM_PUMP_PUMP_DISCONNECTED` via `PumpCommHandler`. **No service change needed.**
- Update `MainActivity.kt` message handlers at 628 (`FROM_PUMP_PUMP_CONNECTED`)
  and 637 (`FROM_PUMP_PUMP_DISCONNECTED`):
  - On connected: `dataStore.pumpConnected.value = true`, set
    `pumpLastConnectionTimestamp = Instant.now()`.
  - On disconnected: `dataStore.pumpConnected.value = false`.
- Update `WearPumpCommService.updateNotificationWithPumpData` to also stamp
  `dataStore.pumpLastMessageTimestamp` — **but** DataStore is an Activity-scoped
  singleton, service can't touch it. Instead: send a tiny local
  bus message? No — cheaper: in `MainActivity` observe any `FROM_PUMP_*` message
  arrival and stamp `pumpLastMessageTimestamp = Instant.now()` at the top of
  `handleMessageReceived`. Matches mobile, which updates the same way in
  `MainActivity.kt:703`.

### 3. Reload-path parity on watch

- `WearPumpCommService.kt:196` `TO_SERVER_FORCE_RELOAD` currently just logs.
  Change it to `triggerAppReload(applicationContext)` — port the helper from
  mobile `CommService.kt:810`.
- Watch `MainActivity` has no `TO_SERVER_APP_RELOAD` handler. Add one that
  calls the same `triggerAppReload(applicationContext)`. Port the helper from
  mobile `MainActivity.kt:1285`.

### 4. `LastConnectionText` component (`wear/.../presentation/components/`)

Direct port of mobile `LastConnectionUpdatedTimestamp.kt`. Uses Wear
`androidx.wear.compose.material.Text`. Compact single-line form since screen
real estate is tight: "Last seen 3m ago".

### 5. `WearServiceDisabledMessage` component (`wear/.../presentation/components/`)

Port of mobile `ServiceDisabledMessage.kt`:
- Observes `ds.pumpConnected` to refresh `WearPrefs.serviceEnabled()` state.
- If disabled: render a `Chip` or small card that on click runs the
  three-step re-enable sequence.
- Rendered as a top-level overlay in `WearApp.kt` so it's visible anywhere
  after the user disconnects.
- Only active when `DeviceRole.PUMP_HOST`; in CLIENT mode the phone handles
  this and the watch shouldn't duplicate.

### 6. `ConnectingToPumpScreen` (`wear/.../presentation/ui/`)

Replaces placeholder `IndeterminateProgressIndicator(text = "Connecting to pump")`
in `WearApp.kt:275`.

Content:
- `CircularProgressIndicator` (Wear material).
- "Connecting to…" text, device name from `ds.setupDeviceName`.
- Stop button: sets `WearPrefs.setServiceEnabled(false)` + sends
  `TO_SERVER_FORCE_RELOAD` (mirror `Settings.kt:148-155` sequence).

### 7. `PumpDisconnectedReconnectingScreen` (`wear/.../presentation/ui/`)

Replaces placeholder at `WearApp.kt:287`.

Content:
- Warning icon, "Disconnected, reconnecting…" text.
- `LastConnectionText` component.
- Stop button, same as ConnectingToPumpScreen.
- **No** explicit "Reconnect" button — `WearServiceDisabledMessage` banner
  handles re-enable after Stop, consistent with mobile.

### 8. Foreground-notification tap routing

- `WearPumpCommService.createNotification()` (line 443) — `pendingIntent` just
  starts `MainActivity` with no extras.
- Add an intent extra `EXTRA_OPEN_ROUTE` read on MainActivity launch; if
  present and the disconnect state warrants it, route accordingly.
- Minimal change: keep existing intent; MainActivity already restores
  the correct route via the `FROM_PUMP_*` listener path when the service
  re-emits on reconnect. **Defer intent-extra plumbing** unless the user
  asks — not core to 5c's ask.

## Out of scope

- **RSSI display** — not surfaced in the BT stack today; defer.
- **`WearMessageForwarder` extraction** (Phase 0 leftover) — not user-visible;
  separate commit if/when it gets in the way.
- **Mobile UI parity** — the existing mobile UX already meets the bar the plan
  doc defines; no mobile visual changes.

## Files touched (estimate)

- `wear/.../presentation/DataStore.kt` — new fields
- `wear/.../MainActivity.kt` — event handler wiring + TO_SERVER_APP_RELOAD + triggerAppReload helper
- `wear/.../WearPumpCommService.kt` — TO_SERVER_FORCE_RELOAD becomes real reload
- `wear/.../presentation/WearApp.kt` — route two screens + mount disabled banner
- `wear/.../presentation/ui/ConnectingToPumpScreen.kt` — **new**
- `wear/.../presentation/ui/PumpDisconnectedReconnectingScreen.kt` — **new**
- `wear/.../presentation/components/LastConnectionText.kt` — **new**
- `wear/.../presentation/components/WearServiceDisabledMessage.kt` — **new**

No `:shared`, `:mobile`, `:pumpcomm`, or `:clientcomm` changes.

## Verification

Sandbox has no Android toolchain — rely on code-review + structural correctness
(imports, types, Compose parameters match Wear material signatures used elsewhere).
On-device verification is the user's follow-up, same pattern as 5b.
