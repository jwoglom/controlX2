# Nightscout Integration Plan

**Status:** In Progress
**Date:** 2025-12-12
**Goal:** Achieve feature parity with AndroidAPS/Loop while ensuring data integrity and architecture stability.

## Executive Summary

ControlX2's current Nightscout implementation is functional but brittle. It suffers from "partial snapshot" issues where data flickers in Nightscout, and it lacks critical features like Carb Tracking and Profile Management.

This plan prioritizes **Architectural Integrity** (fixing data loss/corruption) before **Feature Parity** (adding new data types).

---

## Phase 1: Architecture & Data Integrity (Priority 0)

*Objective: Prevent data loss and ensure consistent, complete pump status display.*

### 1.1 Per-Processor Cursors (Completed)
**Problem:** Currently, a single global `lastProcessedSeqId` tracks sync progress. If one processor (e.g., Bolus) fails but another (e.g., DeviceStatus) succeeds, the cursor advances, and the failed bolus is permanently lost.
**Solution:** Track sync progress independently for each data type.

*   **Schema Change:** Create `NightscoutProcessorState` entity.
    ```kotlin
    @Entity(tableName = "nightscout_processor_state")
    data class NightscoutProcessorState(
        @PrimaryKey val processorType: String, // e.g., "BOLUS", "DEVICE_STATUS"
        val lastProcessedSeqId: Long,
        val lastSuccessTime: LocalDateTime?
    )
    ```
*   **Logic Change:** `NightscoutSyncCoordinator` will iterate through processors. Each processor manages its own cursor. The global `lastProcessedSeqId` in `NightscoutSyncState` will be deprecated or used only as a fallback minimum.

### 1.2 Device Status Aggregation (Completed)
**Problem:** `ProcessDeviceStatus` currently uploads only the *single latest* status log. If the last log was a simple "Battery Update", derived data like Reservoir and IOB is lost, causing Nightscout to display incomplete status.
**Solution:** Build a composite state object from the entire batch.

*   **Logic:**
    1.  Fetch all status-related logs in the sync range.
    2.  Iterate chronologically to build a `LatestPumpState` object:
        *   Update `battery` if log has battery.
        *   Update `reservoir` if log has reservoir.
        *   Update `iob` if log has IOB.
        *   Update `status` (suspended/bolusing) if log has status.
    3.  Upload this composite object as the Device Status.

### 1.3 Pump Model Detection (X2 vs Mobi) (Completed)
**Problem:** Nightscout needs to know the device model, but `NightscoutSyncWorker` doesn't currently access it.
**Solution:**
*   **Capture:** In `CommService.onPumpModel`, save the model string (e.g., "t:slim X2", "Mobi") to `Prefs`.
*   **Read:** `ProcessDeviceStatus` reads this preference to populate `device` metadata.
*   **Fallback:** If unknown, default to "Tandem Pump".

### 1.4 Temp Basal Segmentation (Pending)
**Problem:** Temp basals are uploaded as point-in-time events. Canceled temp basals may appear to run forever in Nightscout.
**Solution:**
*   Correlate `TempRateStarted`, `TempRateCompleted`, and `BasalDelivery` logs.
*   Upload coherent "Basal Segments" with explicit start time, duration, and rate.

---

## Phase 2: Critical Data Gaps (Priority 1)

*Objective: Fix data that is currently being sent incorrectly.*

### 2.1 Boolean Pump Status (Completed)
**Gap:** Nightscout expects boolean flags, ControlX2 sends strings.
**Fix:** Map internal status strings to JSON booleans in `NightscoutDeviceStatus`:
*   `pump.status.suspended`: `true`
*   `pump.status.bolusing`: `true`

### 2.2 Uploader Battery (Completed)
**Gap:** Field is currently null.
**Fix:** Inject Android phone battery percentage into the Device Status upload.

### 2.3 Trend Arrows (Completed)
**Gap:** History logs for G6/G7 do not contain trend arrows.
**Solution:** Calculate trend based on linear regression of the last 15 minutes of SGV data.
**Implementation:**
*   `ProcessCGMReading` now fetches recent history logs.
*   Calculates slope (mg/dL per minute).
*   Maps slope to Nightscout direction strings (DoubleUp, SingleUp, etc.).

---

## Phase 3: Feature Parity (Priority 2)

*Objective: Add missing features expected by advanced users.*

### 3.1 Carb Tracking (Completed)
**Gap:** No carb data is uploaded.
**Implementation:**
*   New Processor: `ProcessCarb`.
*   **Source:** `BolusWizardHistoryLog` (primary source of user-entered carbs) and `MealMarkerHistoryLog`.
*   **Output:** `NightscoutTreatment` with `eventType="Carb Correction"`.

### 3.2 Profile Management (Completed)
**Gap:** No profile data (ISF, IC, Basal Rates).
**Implementation:**
*   New Processor: `ProcessProfile`.
*   **Trigger:** Upload on service start and when `ProfileChangedHistoryLog` is detected.
*   **Source:** Read active profile from `PumpState` or request `CurrentProfileRequest`.
*   **Endpoint:** `POST /api/v1/profile`.
*   **Note:** Partial implementation; logs event but full profile sync requires more infrastructure.

### 3.3 Extended/Combo Boluses (Completed)
**Gap:** Extended boluses are treated as normal boluses.
**Implementation:**
*   Enhanced `ProcessBolus` to detect `ExtendedBolusHistoryLog`.
*   Uploads as `NightscoutTreatment` with `enteredinsulin` (immediate) and `relative` (extended) fields.

---

## Implementation Roadmap

1.  **Database & Coordinator Refactor** (Phase 1.1) [COMPLETED]
    *   Create `NightscoutProcessorState` entity/dao.
    *   Update `NightscoutSyncCoordinator` to use per-processor logic.
2.  **Device Status Overhaul** (Phase 1.2, 1.3, 2.1, 2.2) [COMPLETED]
    *   Rewrite `ProcessDeviceStatus` with aggregation logic.
    *   Add Model detection via Prefs.
    *   Add Boolean flags and Uploader Battery.
3.  **Basal & Carbs** (Phase 1.4, 3.1, 2.3) [COMPLETED]
    *   Fix Temp Basal segmentation. (Pending)
    *   Implement `ProcessCarb`.
    *   Implement Trend Arrows.
4.  **Profiles** (Phase 3.2) [COMPLETED]
    *   Implement `ProcessProfile`.
