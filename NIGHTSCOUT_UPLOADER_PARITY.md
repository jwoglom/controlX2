### Nightscout Uploader Parity Checklist (ControlX2 vs AndroidAPS / Trio / Loop)

This document is a **field-by-field parity checklist** for ControlX2’s Nightscout uploader.

- **Goal**: Make Nightscout display the same *pump status functionality* users expect from AndroidAPS, Trio, and Loop.
- **Scope**: What is written to Nightscout, how Nightscout UI uses it, and what ControlX2 currently provides.
- **Out of scope**: Predictive/loop algorithm outputs (e.g., OpenAPS/Loop predictions) *unless* ControlX2 decides to compute and publish them.

---

### Quick pointers to current ControlX2 implementation

- **Coordinator**: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/NightscoutSyncCoordinator.kt`
- **API client**: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/api/NightscoutClient.kt`
- **Models**:
  - `NightscoutEntry`: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/models/NightscoutEntry.kt`
  - `NightscoutTreatment`: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/models/NightscoutTreatment.kt`
  - `NightscoutDeviceStatus`: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/models/NightscoutDeviceStatus.kt`
- **Processors**: `mobile/src/main/java/com/jwoglom/controlx2/sync/nightscout/processors/*`

Current processors (in order):
- CGM readings → `/entries`
- Bolus → `/treatments`
- Basal + temp basal → `/treatments`
- Basal suspension → `/treatments`
- Basal resume → `/treatments`
- Alarms → `/treatments`
- CGM alerts → `/treatments`
- User mode → `/treatments`
- Cartridge/site/fills → `/treatments`
- Device status → `/devicestatus`

---

### Parity targets: what Nightscout UI commonly relies on

Nightscout “pump status” (battery/reservoir/IOB/suspended/bolusing) is primarily driven by:

- **`/api/v1/devicestatus`** (current snapshot)
  - `pump.battery.percent`
  - `pump.reservoir`
  - `pump.iob.iob` (and sometimes `pump.iob.bolusiob`)
  - `pump.status.suspended` / `pump.status.bolusing` (boolean)
  - `pump.clock`
  - `uploaderBattery` (or richer uploader block, depending on uploader)

And secondarily by:

- **`/api/v1/treatments`** (historical events)
  - Temp basal segments (including suspend intervals)
  - Bolus events (including extended/dual semantics)
  - Mode/override/target change events (sleep/exercise, etc.)

---

## Checklist A — `devicestatus` parity (highest priority for “pump status”)

**Endpoint**: `POST /api/v1/devicestatus`

### A1. Snapshot completeness (don’t send partial status)
- [ ] **Aggregate across multiple pump status sources** (battery + reservoir + iob + suspended/bolusing) into one coherent snapshot.
  - **Why**: If you only serialize the “latest” status log, Nightscout can show *partial* pump status.
  - **ControlX2 today**: Uses only the single most recent status-like history log item.
    - See `ProcessDeviceStatus.process()` → `latestLog = logs.maxByOrNull { it.pumpTime }`.
  - **Parity expectation (APS/Loop/Trio)**: Each upload is a complete, coherent pump snapshot.

### A2. Pump battery
- [ ] Write `pump.battery.percent`.
  - **ControlX2 today**: Best-effort via reflection (works when the chosen log contains the field).
  - **Gap**: If the latest chosen log lacks battery data, battery may disappear from NS.

### A3. Reservoir
- [ ] Write `pump.reservoir` (units).
  - **ControlX2 today**: Best-effort via reflection; same “partial snapshot” risk as battery.

### A4. IOB
- [ ] Write `pump.iob.iob` (total IOB).
  - **ControlX2 today**: Best-effort via reflection.
- [ ] Write `pump.iob.bolusiob` when available (or derivable).
  - **ControlX2 today**: Not currently populated.
  - **Parity expectation**: Often present in APS/Loop-like payloads.

### A5. Suspended / bolusing booleans
- [ ] Write `pump.status.suspended` (boolean).
- [ ] Write `pump.status.bolusing` (boolean).
  - **ControlX2 today**:
    - `NightscoutDeviceStatus.PumpStatusInfo` supports both booleans.
    - `createDeviceStatus()` currently only sets the **string** `status`, not the booleans.
    - `ProcessDeviceStatus` derives a `pumpStatus` string (e.g., “suspended”), but does not set `suspended=true`.
  - **Parity expectation**: Boolean fields are what downstream UI logic typically uses.

### A6. Pump clock/time and timestamp coherence
- [ ] Write `pump.clock` consistently in ISO format and keep it aligned with `created_at`.
  - **ControlX2 today**: Sets `pump.clock = timestamp.toString()`.
  - **Parity expectation**: Clock should always be present and monotonic-ish.

### A7. Uploader battery / phone status
- [ ] Write `uploaderBattery` (phone battery percent).
  - **ControlX2 today**: Always `null`.
  - **Parity expectation**: Most mature uploaders populate this.
- [ ] (Optional) Include uploader metadata (device model, app version, connectivity state) in a structured block.
  - **Note**: Different ecosystems use slightly different keys; keep it stable and documented.

### A8. Control-IQ / mode state in devicestatus
- [ ] Include “current mode” (sleep/exercise) and Control-IQ enablement/state in `devicestatus`.
  - **ControlX2 today**: Only writes mode changes as treatments (often `Note`).
  - **Parity expectation**: Loop/Trio/APS-like experiences show current override/mode state without parsing treatments.

### A9. Device identifiers (safe identifiers)
- [ ] Include pump model and stable identifier (hash) if available (and privacy acceptable).
  - **ControlX2 today**: `device="ControlX2"` but no pump model field.
  - **Parity expectation**: Helpful when multiple devices write to same NS.

---

## Checklist B — Treatments parity (historical pump actions)

**Endpoint**: `POST /api/v1/treatments`

### B1. Bolus fidelity (normal vs extended/dual, programmed vs delivered)
- [ ] Record **bolus type** (normal vs extended vs dual/combo) as structured fields and/or consistent notes.
- [ ] Record **programmed/requested** vs **delivered** insulin.
- [ ] Record **cancellation/interruption** and reason.
- [ ] Record **automatic vs manual** (if identifiable).
  - **ControlX2 today**:
    - Uploads `eventType="Bolus"` with `insulin` (best-effort) and maybe `carbs`.
    - No duration/extended semantics.
    - No delivered-vs-programmed distinction.
    - See `ProcessBolus.kt`.
  - **Parity expectation**:
    - APS/Loop-family typically preserve enough detail to reconstruct what happened.

### B2. Bolus association with carbs (if present)
- [ ] Upload carbs as separate events when appropriate (or consistently in bolus event if that’s your chosen schema).
  - **ControlX2 today**: Tries to extract carbs from bolus history log; may be absent.
  - **Parity expectation**: Carb entries are first-class in APS/Loop ecosystems.

### B3. Temp basal segmentation (true start/duration/end)
- [ ] Ensure temp basal uploads represent a coherent segment:
  - start time
  - duration
  - rate
  - explicit cancel/end if interrupted
- [ ] Distinguish scheduled basal changes from temp basal changes.
  - **ControlX2 today**:
    - Converts many basal-related logs into `eventType="Temp Basal"` independently.
    - Does not correlate TempRateStarted/Completed into a single segment.
    - See `ProcessBasal.kt`.
  - **Parity expectation**:
    - Clean segments, minimal duplicates, consistent end semantics.

### B4. Suspend/resume as a coherent basal interval
- [ ] Represent suspension such that Nightscout can show a correct suspended interval.
  - **ControlX2 today**:
    - Suspension → `Temp Basal` rate=0, duration best-effort.
    - Resume → `Note` (not an explicit end/boundary).
    - See `ProcessBasalSuspension.kt`, `ProcessBasalResume.kt`.
  - **Parity expectation**:
    - Suspended intervals render cleanly and align with `devicestatus.pump.status.suspended`.

### B5. Profile changes and settings changes
- [ ] Upload profile switch events in a consistent way.
  - **ControlX2 today**: Mode/profile-ish events are uploaded as `Exercise` or `Note`.
  - **Parity expectation**: Profile switch is typically visible and machine-readable.

### B6. Mode/override targets (sleep/exercise)
- [ ] Upload sleep/exercise enable/disable as structured treatments (and mirror current state in devicestatus).
  - **ControlX2 today**: Often ends up as `Note` (sleep), `Exercise` sometimes.
  - **Parity expectation**: Clear event type naming and consistent fields.

### B7. Pump maintenance events (site/insulin/tubing/cannula/fills)
- [ ] Ensure granular event types (or consistent notes schema) for:
  - insulin cartridge change
  - site change
  - tubing fill
  - cannula fill
  - prime volume
- [ ] Include volumes where applicable.
  - **ControlX2 today**: Coarse mapping to `Site Change` vs `Insulin Change`, includes some volume in notes.

### B8. Alerts and alarms
- [ ] Deduplicate/rate-limit so NS isn’t flooded.
- [ ] Use consistent event types for activated vs cleared.
  - **ControlX2 today**: Both are `Announcement` with reason/notes.

---

## Checklist C — Entries parity (CGM UX)

**Endpoint**: `POST /api/v1/entries`

### C1. Trend direction arrows
- [ ] Populate `direction` (e.g., Flat, FortyFiveUp, SingleUp, etc.).
  - **ControlX2 today**: Always `null` for both G6 and G7 history logs.
  - **Parity expectation**: Trend arrows are a core UI parity feature.

### C2. Optional CGM metadata
- [ ] Consider including sensor noise/quality/raw fields if available.
  - **Note**: Only if the pump/CGM data source provides this reliably.

---

## Checklist D — Profiles/settings parity (common in APS/Loop/Trio)

**Endpoint (commonly used)**: `POST /api/v1/profile`

- [ ] Upload a Nightscout profile including:
  - basal schedule
  - carb ratio schedule
  - insulin sensitivity schedule
  - glucose targets
  - DIA / insulin model
  - units (mg/dL vs mmol/L)
  - timezone
  - active profile name

**ControlX2 today**: No `/profile` support.

**Why it matters**: These apps feel “pump rich” because Nightscout has the therapy settings needed to interpret delivery.

---

## Checklist E — Data integrity / dedupe / reliability

### E1. Dedupe keys
- [x] **Use stable dedupe keys**:
  - Entries: `identifier = seqId`
  - Treatments: `pumpId = seqId`

### E2. Cursor advancement correctness
- [ ] Avoid permanently skipping data when one processor fails.
  - **ControlX2 today**: Advances a single global `lastProcessedSeqId` even if some processors fail for that range.
  - **Parity expectation**: Per-type cursors, or only advance when all enabled processors succeed.

### E3. Server-side reconciliation
- [ ] Optionally query NS for recent items and avoid re-upload churn.
  - **ControlX2 today**: API has `getLastEntries` and `getLastTreatment`, but processors do not use them yet.

---

## Implementation notes (ControlX2-specific)

- **Most important change for pump status**: Rewrite `ProcessDeviceStatus` to build a coherent snapshot by aggregating latest known battery/reservoir/iob/suspended/bolusing across the eligible log types.
- **Most important change for basal realism**: Correlate temp basal start/stop/completion logs into a single segment with duration and proper endings.
- **Most important change for UI parity**: Populate `pump.status.suspended`/`bolusing` booleans and `uploaderBattery`.

---

### Definition of done (pump-status parity)

ControlX2 can claim “Nightscout pump status parity” when Nightscout consistently shows:

- [ ] Pump battery %
- [ ] Reservoir units
- [ ] IOB
- [ ] Suspended state (boolean, current)
- [ ] Bolusing state (boolean, current)
- [ ] Correct historical suspend intervals (treatments aligned with devicestatus)
- [ ] Correct temp basal segments
- [ ] Current mode/override state (sleep/exercise/Control-IQ) visible without manual log parsing
