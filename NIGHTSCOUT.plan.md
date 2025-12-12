# Nightscout Integration Plan

**Status:** Draft
**Date:** 2025-12-12
**Goal:** Achieve feature parity with AndroidAPS/Loop while ensuring data integrity and architecture stability.

## Executive Summary

ControlX2's current Nightscout implementation is functional but brittle. It suffers from "partial snapshot" issues where data flickers in Nightscout, and it lacks critical features like Carb Tracking and Profile Management.

This plan prioritizes **Architectural Integrity** (fixing data loss/corruption) before **Feature Parity** (adding new data types).

---

## Phase 1: Architecture & Data Integrity (Priority 0)

*Objective: Prevent data loss and ensure consistent, complete pump status display.*

### 1.1 Per-Processor Cursors
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

### 1.2 Device Status Aggregation
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

### 1.3 Pump Model Detection (X2 vs Mobi)
**Problem:** Nightscout needs to know the device model, but `NightscoutSyncWorker` doesn't currently access it.
**Solution:**
*   **Capture:** In `CommService.onPumpModel`, save the model string (e.g., "t:slim X2", "Mobi") to `Prefs`.
*   **Read:** `ProcessDeviceStatus` reads this preference to populate `device` metadata.
*   **Fallback:** If unknown, default to "Tandem Pump".

### 1.4 Temp Basal Segmentation
**Problem:** Temp basals are uploaded as point-in-time events. Canceled temp basals may appear to run forever in Nightscout.
**Solution:**
*   Correlate `TempRateStarted`, `TempRateCompleted`, and `BasalDelivery` logs.
*   Upload coherent "Basal Segments" with explicit start time, duration, and rate.

---

## Phase 2: Critical Data Gaps (Priority 1)

*Objective: Fix data that is currently being sent incorrectly.*

### 2.1 Boolean Pump Status
**Gap:** Nightscout expects boolean flags, ControlX2 sends strings.
**Fix:** Map internal status strings to JSON booleans in `NightscoutDeviceStatus`:
*   `pump.status.suspended`: `true`
*   `pump.status.bolusing`: `true`

### 2.2 Uploader Battery
**Gap:** Field is currently null.
**Fix:** Inject Android phone battery percentage into the Device Status upload.

### 2.3 Trend Arrows (Investigation)
**Gap:** History logs for G6/G7 do not contain trend arrows.
**Plan:**
*   **Option A:** Calculate trend based on the last 15 minutes of SGV data during sync.
*   **Option B:** Leave null if Nightscout can auto-calculate (requires verification).
*   **Decision:** Tentatively implement Option A (Simple Slope Calculation) in `ProcessCGMReading`.

---

## Phase 3: Feature Parity (Priority 2)

*Objective: Add missing features expected by advanced users.*

### 3.1 Carb Tracking
**Gap:** No carb data is uploaded.
**Implementation:**
*   New Processor: `ProcessCarb`.
*   **Source:** `BolusWizardHistoryLog` (primary source of user-entered carbs) and `MealMarkerHistoryLog`.
*   **Output:** `NightscoutTreatment` with `eventType="Carb Correction"`.

### 3.2 Profile Management
**Gap:** No profile data (ISF, IC, Basal Rates).
**Implementation:**
*   New Processor: `ProcessProfile`.
*   **Trigger:** Upload on service start and when `ProfileChangedHistoryLog` is detected.
*   **Source:** Read active profile from `PumpState` or request `CurrentProfileRequest`.
*   **Endpoint:** `POST /api/v1/profile`.

### 3.3 Extended/Combo Boluses
**Gap:** Extended boluses are treated as normal boluses.
**Implementation:**
*   Enhance `ProcessBolus` to detect `ExtendedBolusHistoryLog`.
*   Upload as `NightscoutTreatment` with `enteredinsulin` (immediate) and `relative` (extended) fields.

---

## Implementation Roadmap

1.  **Database & Coordinator Refactor** (Phase 1.1)
    *   Create `NightscoutProcessorState` entity/dao.
    *   Update `NightscoutSyncCoordinator` to use per-processor logic.
2.  **Device Status Overhaul** (Phase 1.2, 1.3, 2.1, 2.2)
    *   Rewrite `ProcessDeviceStatus` with aggregation logic.
    *   Add Model detection via Prefs.
    *   Add Boolean flags and Uploader Battery.
3.  **Basal & Carbs** (Phase 1.4, 3.1)
    *   Fix Temp Basal segmentation.
    *   Implement `ProcessCarb`.
4.  **Profiles** (Phase 3.2)
    *   Implement `ProcessProfile`.
