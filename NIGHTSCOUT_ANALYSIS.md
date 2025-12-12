# Nightscout Publishing Feature Analysis
## AndroidAPS, Trio, Loop vs ControlX2

**Analysis Date:** 2025-12-12
**Author:** Claude
**Purpose:** Deep analysis of Nightscout data publishing implementations across diabetes management apps

---

## Executive Summary

This document provides a comprehensive comparison of Nightscout data publishing between ControlX2 and three leading automated insulin delivery applications: AndroidAPS, Trio (iOS), and Loop (iOS). The analysis identifies significant gaps in ControlX2's current implementation and provides specific, actionable recommendations to achieve feature parity.

**Key Finding:** ControlX2's current Nightscout implementation covers fundamental pump data (CGM, bolus, basal, device status) but is missing critical advanced features present in all three comparison apps, particularly:
1. **Prediction data** (glucose forecasts, eventual BG)
2. **Carb tracking** (COB - Carbs on Board)
3. **Profile management** (basal profiles, IC ratios, ISF)
4. **Treatment diversity** (temp targets, profile switches, carb entries)
5. **Algorithm transparency** (decision rationale, recommended vs enacted)

---

## 1. Current ControlX2 Implementation

### 1.1 What ControlX2 Currently Uploads

Based on analysis of the dev branch implementation, ControlX2 uploads:

#### **Entries (CGM Data)**
- Sensor glucose values (SGV)
- Trend direction
- Timestamp and device identifier

#### **Treatments**
- **Bolus** deliveries (insulin amount, optional carbs, notes)
- **Basal** rate changes
- **Basal Suspension** events
- **Basal Resume** events
- **Alarms** (pump alarms as annotations)
- **CGM Alerts** (sensor alerts as annotations)
- **User Mode** changes (sleep, exercise modes)
- **Cartridge** changes (site change events)

#### **Device Status**
- **Pump Battery** percentage
- **Reservoir** level (units remaining)
- **IOB** (Insulin on Board - total)
- **Pump Status** (normal/suspended/bolusing)
- **Clock** (pump timestamp)
- **Uploader Battery** (phone battery level)

### 1.2 Architecture Strengths

ControlX2's implementation follows the **tconnectsync architecture pattern**:
- ✅ Sequential processor model (prevents race conditions)
- ✅ Processor-based architecture (easy to extend)
- ✅ Deduplication via pumpId/identifier
- ✅ Sync state management with lookback
- ✅ Retroactive sync capability

---

## 2. Comparison Applications Analysis

### 2.1 AndroidAPS (Android)

**Platform:** Android
**Pump Support:** Omnipod, Medtronic, Dana, Combo, Insight, others
**Architecture:** Modular plugin system with NSClient/NSClientV3

#### Key Nightscout Features

**Treatments Uploaded:**
- ✅ Bolus (normal, combo/extended, SMB)
- ✅ Temp Basal (start/end with rate and duration)
- ✅ **Carb entries** (separate from boluses)
- ✅ **Profile Switch** (with profile JSON)
- ✅ **Temp Target** (with target range and duration)
- ✅ Site changes, sensor changes, pump battery changes
- ✅ Announcements, notes, questions
- ✅ Exercise events

**Device Status Fields:**
```json
{
  "pump": {
    "battery": { "percent": 85 },
    "reservoir": 45.2,
    "status": { "status": "normal", "bolusing": false, "suspended": false },
    "extended": { "LastBolus": "...", "BaseBasalRate": 0.85 },
    "clock": "2025-12-12T10:30:00"
  },
  "openaps": {
    "suggested": {
      "temp": "absolute",
      "bg": 120,
      "tick": "+5",
      "eventualBG": 110,
      "insulinReq": 0.5,
      "deliverAt": "2025-12-12T10:30:05",
      "reason": "..."
    },
    "enacted": {
      "rate": 1.2,
      "duration": 30,
      "recieved": true,
      "reason": "...",
      "timestamp": "..."
    },
    "iob": {
      "iob": 2.5,
      "basaliob": 0.8,
      "bolusiob": 1.7,
      "activity": 0.05,
      "time": "..."
    }
  },
  "uploaderBattery": 78
}
```

**Profile Data:**
- Uploads complete profile definitions to `/api/v1/profile`
- Includes basal rates (time segments)
- IC (Insulin-to-Carb) ratios by time
- ISF (Insulin Sensitivity Factor) by time
- Target BG ranges
- DIA (Duration of Insulin Action)

**Advanced Features:**
- SMB (Super Micro Bolus) tracking
- Autosens ratio
- Variable sensitivity calculations
- Profile percentage adjustments

### 2.2 Loop (iOS)

**Platform:** iOS
**Pump Support:** Omnipod (Eros, DASH), Medtronic
**Architecture:** Swift-based with NightscoutService

#### Key Nightscout Features

**Treatments Uploaded:**
- ✅ Bolus
- ✅ Temp Basal
- ✅ **Carbs** (with absorption time)
- ✅ Override (similar to profile switch)
- ✅ **Temporary override** (temp target with presets)

**Device Status Fields:**
```json
{
  "loop": {
    "name": "Loop",
    "version": "3.8.2",
    "timestamp": "2025-12-12T10:30:00Z",
    "iob": {
      "timestamp": "...",
      "iob": 2.3
    },
    "cob": {
      "cob": 25,
      "timestamp": "..."
    },
    "predicted": {
      "startDate": "...",
      "values": [120, 118, 115, 112, ...]  // 5-min intervals
    },
    "recommendedTempBasal": {
      "timestamp": "...",
      "rate": 1.5,
      "duration": 30
    },
    "enacted": {
      "timestamp": "...",
      "rate": 1.5,
      "duration": 30,
      "received": true
    }
  },
  "pump": {
    "manufacturer": "Insulet",
    "model": "Eros",
    "pumpID": "1F01ACAB",
    "clock": "...",
    "battery": { "percent": 100 },
    "reservoir": 45.8,
    "bolusing": false,
    "suspended": false,
    "secondsFromGMT": -18000
  },
  "override": {
    "active": true,
    "name": "Exercise",
    "timestamp": "...",
    "multiplier": 0.5
  },
  "uploaderBattery": 82
}
```

**Prediction Arrays:**
Loop uploads **multiple prediction curves**:
- **IOBpredBG**: Prediction based on insulin on board
- **COBpredBG**: Prediction based on carbs on board
- **ZTpredBG**: Zero-temp prediction (no insulin delivery)
- **UAM**: Unannounced meal prediction

**Advanced Features:**
- Eventual BG calculation
- Minimum/maximum predicted glucose
- Active override information
- Detailed timestamp tracking with timezone offset

### 2.3 Trio (iOS)

**Platform:** iOS
**Pump Support:** Omnipod, Medtronic, via OpenAPS algorithm
**Architecture:** Swift-based, trio-oref for NS uploads

#### Key Nightscout Features

**Treatments Uploaded:**
- ✅ Bolus
- ✅ Temp Basal
- ✅ **Carbs with absorption**
- ✅ Profile switches
- ✅ Temp targets
- ✅ Meal announcements

**Device Status:**
Similar to Loop but with OpenAPS-specific fields:
```json
{
  "openaps": {
    "iob": {
      "iob": 2.1,
      "basaliob": 0.5,
      "bolusiob": 1.6,
      "time": "..."
    },
    "suggested": {
      "temp": "absolute",
      "bg": 125,
      "tick": "+3",
      "eventualBG": 115,
      "snoozeBG": 120,
      "predBGs": {
        "IOB": [125, 123, 120, 117, ...],
        "COB": [125, 128, 130, 128, ...],
        "UAM": [125, 122, 118, 115, ...]
      },
      "reason": "COB: 30, Dev: -5, BGI: -2.5, ..."
    },
    "enacted": { ... }
  },
  "pump": { ... }
}
```

**Advanced Features:**
- Detailed reason strings explaining algorithm decisions
- Multiple prediction scenarios
- Autosens adjustments
- SMB tracking

---

## 3. Gap Analysis

### 3.1 Missing Treatment Types

| Treatment Type | AndroidAPS | Loop | Trio | ControlX2 |
|----------------|------------|------|------|-----------|
| Bolus | ✅ | ✅ | ✅ | ✅ |
| Temp Basal | ✅ | ✅ | ✅ | ✅ |
| **Carbs** | ✅ | ✅ | ✅ | ❌ |
| **Profile Switch** | ✅ | ✅ | ✅ | ❌ |
| **Temp Target** | ✅ | ✅ | ✅ | ❌ |
| **Combo/Extended Bolus** | ✅ | ⚠️ | ⚠️ | ❌ |
| Site Change | ✅ | ✅ | ✅ | ⚠️* |
| Sensor Start/Stop | ✅ | ✅ | ✅ | ❌ |
| Exercise/Activity | ✅ | ⚠️ | ⚠️ | ⚠️* |
| Notes/Announcements | ✅ | ⚠️ | ✅ | ❌ |

*\*ControlX2 has cartridge changes and user mode, which partially cover these*

### 3.2 Missing DeviceStatus Fields

#### **Algorithm/Loop Data (Critical Gap)**

| Field | AndroidAPS | Loop | Trio | ControlX2 |
|-------|------------|------|------|-----------|
| **Predicted BG** | ❌ | ✅ | ✅ | ❌ |
| **Eventual BG** | ✅ | ✅ | ✅ | ❌ |
| **COB** | ✅ | ✅ | ✅ | ❌ |
| **Basal IOB** | ✅ | ⚠️ | ✅ | ❌ |
| **Recommended Temp** | ✅ | ✅ | ✅ | ❌ |
| **Enacted Temp** | ✅ | ✅ | ✅ | ❌ |
| **Reason/Rationale** | ✅ | ⚠️ | ✅ | ❌ |
| **Algorithm Status** | ✅ | ✅ | ✅ | ❌ |

#### **Pump Metadata**

| Field | AndroidAPS | Loop | Trio | ControlX2 |
|-------|------------|------|------|-----------|
| **Manufacturer** | ✅ | ✅ | ✅ | ❌ |
| **Model** | ✅ | ✅ | ✅ | ❌ |
| **Pump ID/Serial** | ✅ | ✅ | ✅ | ❌ |
| Battery Percent | ✅ | ✅ | ✅ | ✅ |
| **Battery Voltage** | ⚠️ | ⚠️ | ⚠️ | ❌ |
| Reservoir | ✅ | ✅ | ✅ | ✅ |
| **Timezone Offset** | ⚠️ | ✅ | ⚠️ | ❌ |

### 3.3 Missing Profile Data

| Feature | AndroidAPS | Loop | Trio | ControlX2 |
|---------|------------|------|------|-----------|
| **Basal Rate Segments** | ✅ | ✅ | ✅ | ❌ |
| **IC Ratios** | ✅ | ✅ | ✅ | ❌ |
| **ISF** | ✅ | ✅ | ✅ | ❌ |
| **Target BG Range** | ✅ | ✅ | ✅ | ❌ |
| **DIA** | ✅ | ✅ | ✅ | ❌ |

---

## 4. Recommendations

### 4.1 Priority 1: Critical Gaps (Implement First)

These features provide the most value and are present in **all three** comparison apps:

#### **1.1 Carb Tracking**

**Implementation:**
```kotlin
// New processor: ProcessCarb
class ProcessCarb : BaseProcessor {
    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            // CarbEntryHistoryLog, MealBolusHistoryLog with carbs
        )
    }

    override suspend fun process(logs: List<HistoryLogItem>): Int {
        val treatments = logs.mapNotNull { carbToNightscoutTreatment(it) }
        return nightscoutApi.uploadTreatments(treatments)
    }

    private fun carbToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment {
        return NightscoutTreatment.fromTimestamp(
            eventType = "Carb Correction",  // or "Meal Bolus" if with insulin
            timestamp = item.pumpTime,
            seqId = item.seqId,
            carbs = extractCarbAmount(item),
            notes = "Carb entry from pump"
        )
    }
}
```

**Tandem X2 Support:** Tandem pumps track carbs entered for bolus wizard calculations. Extract from:
- Bolus history with carb entries
- Meal events in history log

**Value:** Essential for Nightscout's COB calculations and bolus calculator features.

#### **1.2 Pump Manufacturer and Model**

**Implementation:**
```kotlin
// Update NightscoutDeviceStatus.kt
data class PumpStatus(
    @SerializedName("manufacturer")
    val manufacturer: String = "Tandem",

    @SerializedName("model")
    val model: String,  // "t:slim X2", "Mobi"

    @SerializedName("pumpID")
    val pumpID: String,  // Serial number

    @SerializedName("battery")
    val battery: Battery? = null,

    // ... existing fields
)

// Update ProcessDeviceStatus
fun createDeviceStatus(
    pumpModel: String,
    pumpSerialNumber: String,
    // ... other params
): NightscoutDeviceStatus {
    return NightscoutDeviceStatus(
        pump = PumpStatus(
            manufacturer = "Tandem",
            model = pumpModel,
            pumpID = pumpSerialNumber,
            // ...
        )
    )
}
```

**Tandem X2 Support:**
- Model: "t:slim X2" or "Mobi" (detect from pump info)
- Serial: Available from pump status messages

**Value:** Nightscout displays pump type, essential for multi-pump households.

#### **1.3 Enhanced IOB Tracking**

**Implementation:**
```kotlin
// Update IOB data class
data class IOB(
    @SerializedName("iob")
    val iob: Double,  // Total IOB

    @SerializedName("bolusiob")
    val bolusIob: Double? = null,  // NEW

    @SerializedName("basaliob")
    val basalIob: Double? = null,  // NEW

    @SerializedName("activity")
    val activity: Double? = null,  // NEW: insulin activity

    @SerializedName("timestamp")
    val timestamp: String
)
```

**Tandem X2 Support:**
- Tandem provides total IOB in status messages
- Calculate basal vs bolus IOB using delivery history
- Activity = current insulin effect rate (units/hour)

**Value:** Better understanding of insulin distribution, critical for algorithm analysis.

### 4.2 Priority 2: High-Value Enhancements

#### **2.1 Profile Upload**

**Implementation:**
```kotlin
// New model: NightscoutProfile.kt
data class NightscoutProfile(
    @SerializedName("defaultProfile")
    val defaultProfile: String,

    @SerializedName("store")
    val store: Map<String, ProfileStore>
)

data class ProfileStore(
    @SerializedName("dia")
    val dia: Double,  // Duration of Insulin Action (hours)

    @SerializedName("carbratio")
    val carbRatio: List<TimeValue>,  // IC ratios by time

    @SerializedName("sens")
    val sensitivity: List<TimeValue>,  // ISF by time

    @SerializedName("basal")
    val basal: List<TimeValue>,  // Basal rates by time

    @SerializedName("target_low")
    val targetLow: List<TimeValue>,

    @SerializedName("target_high")
    val targetHigh: List<TimeValue>,

    @SerializedName("timezone")
    val timezone: String
)

data class TimeValue(
    @SerializedName("time")
    val time: String,  // "00:00"

    @SerializedName("value")
    val value: Double,

    @SerializedName("timeAsSeconds")
    val timeAsSeconds: Int
)

// New API endpoint
interface NightscoutApi {
    @POST("/api/v1/profile")
    suspend fun uploadProfile(profile: NightscoutProfile): Response<...>
}

// New processor: ProcessProfile
class ProcessProfile : BaseProcessor {
    override suspend fun process(...): Int {
        val profile = buildProfileFromPumpSettings()
        nightscoutApi.uploadProfile(profile)
    }
}
```

**Tandem X2 Support:**
- Extract active profile settings from pump
- Basal rate segments (up to 16 time blocks)
- IC ratios (may be single value or time-based)
- ISF settings
- Target BG range

**Value:**
- Essential for remote profile viewing
- Enables Nightscout's profile comparison tools
- Critical for understanding pump configuration

#### **2.2 Temp Target Support**

**Implementation:**
```kotlin
// Process temp target changes
fun tempTargetToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment {
    return NightscoutTreatment.fromTimestamp(
        eventType = "Temporary Target",
        timestamp = item.pumpTime,
        seqId = item.seqId,
        duration = extractDuration(item),  // minutes
        notes = "Target: ${extractTargetBG(item)} mg/dL, Reason: ${extractReason(item)}"
    )
}
```

**Tandem X2 Support:**
- Tandem has "Exercise Mode" with modified targets
- "Sleep Mode" with modified settings
- Extract from user mode changes

**Value:** Track when user adjusts targets for exercise, illness, etc.

#### **2.3 Profile Switch Events**

**Implementation:**
```kotlin
fun profileSwitchToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment {
    val profileData = extractProfileData(item)

    return NightscoutTreatment(
        eventType = "Profile Switch",
        createdAt = item.pumpTime.toString(),
        timestamp = item.pumpTime.toEpochMilli(),
        pumpId = item.seqId.toString(),
        notes = "Switched to profile: ${profileData.name}",
        // Optional: include full profile JSON
        // profileJson = profileData.toJson()
    )
}
```

**Tandem X2 Support:**
- Tandem supports multiple Personal Profiles
- Track when user switches between profiles
- Extract from profile change history logs

**Value:** Critical for understanding why settings changed.

### 4.3 Priority 3: Advanced Features

These features differentiate Loop/AndroidAPS but require algorithm implementation:

#### **3.1 Prediction Data (For Future Algorithm)**

**Status:** ⚠️ **NOT CURRENTLY APPLICABLE** - ControlX2 doesn't implement closed-loop algorithm

**Future Implementation (if algorithm added):**
```kotlin
// Future: when ControlX2 implements prediction algorithm
data class LoopStatus(
    @SerializedName("predicted")
    val predicted: PredictedBG? = null,

    @SerializedName("cob")
    val cob: CarbsOnBoard? = null,

    @SerializedName("recommendedTempBasal")
    val recommendedTempBasal: TempBasal? = null
)

data class PredictedBG(
    @SerializedName("startDate")
    val startDate: String,

    @SerializedName("values")
    val values: List<Int>  // BG predictions at 5-min intervals
)
```

**Requirements:**
- BG prediction algorithm
- COB calculation algorithm
- Basal recommendation algorithm

**Value:** Essential for closed-loop visualization, but not needed for open-loop pump monitoring.

#### **3.2 Enacted vs Recommended Temp Basals**

**Status:** ⚠️ **PARTIALLY APPLICABLE** - User-initiated temps only

**Current Implementation:**
- ControlX2 tracks user-initiated temp basals
- No algorithm recommendations (open-loop system)

**Enhanced Implementation:**
```kotlin
// Track temp basal more explicitly
data class TempBasalStatus(
    @SerializedName("rate")
    val rate: Double,

    @SerializedName("duration")
    val duration: Int,  // minutes remaining

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("type")
    val type: String  // "user" vs "algorithm" (future)
)
```

**Value:** Shows active temp basal in Nightscout, useful for understanding current state.

#### **3.3 Extended/Combo Bolus Support**

**Implementation:**
```kotlin
fun extendedBolusToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment {
    val bolusData = extractExtendedBolusData(item)

    return NightscoutTreatment.fromTimestamp(
        eventType = "Combo Bolus",
        timestamp = item.pumpTime,
        seqId = item.seqId,
        insulin = bolusData.immediateInsulin,
        duration = bolusData.extendedDuration,  // minutes
        rate = bolusData.extendedRate,  // U/hr for extended portion
        notes = "Immediate: ${bolusData.immediateInsulin}U, Extended: ${bolusData.extendedInsulin}U over ${bolusData.extendedDuration}min"
    )
}
```

**Tandem X2 Support:**
- Tandem supports extended boluses
- Extract from ExtendedBolusHistoryLog
- Track both immediate and extended portions

**Value:** Essential for tracking extended meal boluses.

---

## 5. Implementation Roadmap

### Phase 1: Foundation (Priority 1)
**Timeline:** 2-3 weeks
**Effort:** Medium

1. ✅ Add carb tracking processor
2. ✅ Add manufacturer/model/pumpID to device status
3. ✅ Split IOB into basal/bolus components
4. ✅ Add timezone offset (secondsFromGMT)

**Testing:**
- Verify carbs appear in Nightscout treatments
- Confirm pump model displays in Nightscout
- Validate IOB breakdown accuracy

### Phase 2: Profile Management (Priority 2)
**Timeline:** 3-4 weeks
**Effort:** High

1. ✅ Implement profile data extraction from pump
2. ✅ Create profile upload processor
3. ✅ Add profile switch event tracking
4. ✅ Add temp target support

**Testing:**
- Verify profile displays correctly in Nightscout
- Confirm profile switches tracked
- Validate temp target events

### Phase 3: Enhanced Treatments (Priority 2-3)
**Timeline:** 2-3 weeks
**Effort:** Medium

1. ✅ Add extended/combo bolus support
2. ✅ Add sensor start/stop events
3. ✅ Enhance site change tracking
4. ✅ Add notes/announcement support

**Testing:**
- Verify all treatment types display correctly
- Confirm proper categorization in Nightscout

### Phase 4: Advanced Features (Priority 3)
**Timeline:** 4-6 weeks (Future)
**Effort:** Very High

1. ⚠️ Implement BG prediction algorithm (if moving to closed-loop)
2. ⚠️ Add COB calculation
3. ⚠️ Add algorithm decision tracking
4. ⚠️ Add recommendation vs enacted tracking

**Note:** Phase 4 features require algorithm development and are not needed for open-loop pump monitoring.

---

## 6. Technical Considerations

### 6.1 Data Extraction Challenges

**Tandem X2 Specifics:**

1. **Limited API Documentation**
   - Tandem's APIs are undocumented/reverse-engineered
   - Some fields may not be directly available
   - May need to infer data from multiple sources

2. **Profile Data Complexity**
   - Profiles may have complex time segments
   - Need to handle profile inheritance
   - May require pump memory queries

3. **Real-Time vs Historical**
   - Some data only available in real-time
   - Historical data may be incomplete
   - Sync timing considerations

### 6.2 Nightscout API Compatibility

**Version Considerations:**

- ✅ Nightscout v14+ (current standard)
- ✅ Nightscout v15+ (NSClientV3 support)
- ⚠️ API v1 vs v3 differences

**Field Support:**
- Most recommended fields are widely supported
- Some advanced fields may require recent Nightscout versions
- Test compatibility with common configurations

### 6.3 Performance Impact

**Upload Volume:**

Current implementation uploads:
- ~288 CGM entries/day (every 5 min)
- ~20-50 treatments/day
- ~144 device status/day (every 10 min)

Adding recommended features:
- ~1 profile upload/day (or on change)
- +10-30 additional treatments/day (carbs, switches)
- Device status size increases ~2-3x

**Mitigation:**
- Batch uploads where possible
- Implement rate limiting
- Use efficient JSON serialization
- Monitor battery impact

### 6.4 User Configuration

**Recommended Settings UI:**

```kotlin
// NightscoutSyncConfig extensions
data class NightscoutSyncConfig(
    // ... existing fields ...

    // NEW: Granular feature toggles
    val uploadCarbs: Boolean = true,
    val uploadProfiles: Boolean = true,
    val uploadProfileSwitches: Boolean = true,
    val uploadTempTargets: Boolean = true,
    val uploadExtendedBoluses: Boolean = true,

    // NEW: Advanced options
    val uploadPumpMetadata: Boolean = true,
    val splitIOB: Boolean = true,  // basal vs bolus
    val includeTimezone: Boolean = true,

    // NEW: Profile sync frequency
    val profileSyncMode: ProfileSyncMode = ProfileSyncMode.ON_CHANGE
)

enum class ProfileSyncMode {
    DISABLED,
    ONCE_DAILY,
    ON_CHANGE,
    EVERY_SYNC
}
```

---

## 7. Comparison Summary

### Feature Parity Matrix

| Category | Feature | AndroidAPS | Loop | Trio | ControlX2 Current | ControlX2 Recommended |
|----------|---------|------------|------|------|-------------------|----------------------|
| **CGM** | Glucose Entries | ✅ | ✅ | ✅ | ✅ | ✅ |
| **CGM** | Trend Direction | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Treatments** | Bolus | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Treatments** | Temp Basal | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Treatments** | Carbs | ✅ | ✅ | ✅ | ❌ | **✅ P1** |
| **Treatments** | Profile Switch | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Treatments** | Temp Target | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Treatments** | Extended Bolus | ✅ | ⚠️ | ⚠️ | ❌ | **✅ P3** |
| **Treatments** | Site Change | ✅ | ✅ | ✅ | ⚠️ | **✅ P3** |
| **Pump Status** | Battery % | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Pump Status** | Reservoir | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Pump Status** | Manufacturer | ✅ | ✅ | ✅ | ❌ | **✅ P1** |
| **Pump Status** | Model | ✅ | ✅ | ✅ | ❌ | **✅ P1** |
| **Pump Status** | Pump ID | ✅ | ✅ | ✅ | ❌ | **✅ P1** |
| **IOB** | Total IOB | ✅ | ✅ | ✅ | ✅ | ✅ |
| **IOB** | Basal IOB | ✅ | ⚠️ | ✅ | ❌ | **✅ P1** |
| **IOB** | Bolus IOB | ✅ | ⚠️ | ✅ | ❌ | **✅ P1** |
| **Profiles** | Basal Rates | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Profiles** | IC Ratios | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Profiles** | ISF | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Profiles** | Targets | ✅ | ✅ | ✅ | ❌ | **✅ P2** |
| **Algorithm** | Predictions | ❌* | ✅ | ✅ | ❌ | **⚠️ P4** |
| **Algorithm** | COB | ✅ | ✅ | ✅ | ❌ | **⚠️ P4** |
| **Algorithm** | Recommendations | ✅ | ✅ | ✅ | ❌ | **⚠️ P4** |

*Note: AndroidAPS predictions are different from Loop/Trio implementation
**Legend:** P1 = Priority 1, P2 = Priority 2, P3 = Priority 3, P4 = Priority 4 (Future)

---

## 8. Code Examples

### 8.1 Complete Carb Processor Implementation

```kotlin
package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process carb entries for Nightscout upload
 *
 * Converts carb intake records to Nightscout treatments.
 * Carbs may come from:
 * - Standalone carb entries
 * - Bolus wizard calculations with carb input
 * - Meal announcements
 */
class ProcessCarb(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARB

    override fun supportedTypeIds(): Set<Int> {
        // Try to find carb-related HistoryLog types
        val possibleClasses = listOf(
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEntryHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusWizardHistoryLog",
            "com.jwoglom.pumpx2.pump.messages.response.historyLog.MealMarkerHistoryLog"
        )

        return possibleClasses.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[clazz]
            } catch (e: ClassNotFoundException) {
                Timber.d("Carb HistoryLog class not found: $className")
                null
            }
        }.toSet()
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                carbToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert carb entry seqId=${item.seqId}")
                null
            }
        }.filter { it.carbs != null && it.carbs > 0 }  // Only upload if carbs > 0

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No carb treatments to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun carbToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()
        val carbData = extractCarbData(parsed.javaClass, parsed)

        // Skip if no carbs
        if (carbData.carbs == null || carbData.carbs <= 0) {
            return null
        }

        // Determine event type based on whether insulin was also delivered
        val eventType = when {
            carbData.hasInsulin -> "Meal Bolus"
            else -> "Carb Correction"
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = eventType,
            timestamp = item.pumpTime,
            seqId = item.seqId,
            carbs = carbData.carbs,
            insulin = carbData.insulin,
            notes = buildCarbNotes(carbData)
        )
    }

    private data class CarbData(
        val carbs: Double?,
        val insulin: Double?,
        val hasInsulin: Boolean,
        val carbType: String?,
        val absorptionTime: Int?  // minutes
    )

    private fun extractCarbData(clazz: Class<*>, obj: Any): CarbData {
        try {
            // Try to get carb amount (common field names)
            val carbGrams = tryGetField<Int>(clazz, obj, "carbSize")?.toDouble()
                ?: tryGetField<Int>(clazz, obj, "carbs")?.toDouble()
                ?: tryGetField<Double>(clazz, obj, "carbAmount")
                ?: tryGetField<Int>(clazz, obj, "carbGrams")?.toDouble()

            // Try to get associated insulin
            val insulin = tryGetField<Int>(clazz, obj, "insulinDelivered")?.let { it / 1000.0 }
                ?: tryGetField<Double>(clazz, obj, "insulinAmount")

            // Try to get carb type/absorption time
            val carbType = tryGetField<String>(clazz, obj, "carbType")
                ?: tryGetField<Int>(clazz, obj, "carbTypeId")?.let { "Type $it" }

            val absorptionTime = tryGetField<Int>(clazz, obj, "absorptionTime")
                ?: tryGetField<Int>(clazz, obj, "absorptionMinutes")

            return CarbData(
                carbs = carbGrams,
                insulin = insulin,
                hasInsulin = insulin != null && insulin > 0,
                carbType = carbType,
                absorptionTime = absorptionTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting carb data")
            return CarbData(null, null, false, null, null)
        }
    }

    private fun buildCarbNotes(data: CarbData): String? {
        val parts = mutableListOf<String>()

        data.carbType?.let { parts.add("Type: $it") }
        data.absorptionTime?.let { parts.add("Absorption: ${it}min") }

        return parts.joinToString(", ").takeIf { it.isNotEmpty() }
    }

    private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(obj)
            if (value is T) value else null
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to access field: $fieldName")
            null
        }
    }
}
```

### 8.2 Enhanced Device Status with Pump Metadata

```kotlin
// Update createDeviceStatus function in NightscoutDeviceStatus.kt
fun createDeviceStatus(
    timestamp: LocalDateTime,
    // Existing params
    batteryPercent: Int? = null,
    reservoirUnits: Double? = null,
    iob: Double? = null,
    pumpStatus: String? = null,
    uploaderBattery: Int? = null,
    // NEW: Pump metadata
    pumpManufacturer: String = "Tandem",
    pumpModel: String? = null,  // "t:slim X2", "Mobi"
    pumpId: String? = null,  // Serial number
    // NEW: Enhanced IOB
    bolusIob: Double? = null,
    basalIob: Double? = null,
    iobActivity: Double? = null,
    // NEW: Timezone
    secondsFromGMT: Int? = null
): NightscoutDeviceStatus {
    return NightscoutDeviceStatus(
        createdAt = timestamp.toString(),
        pump = PumpStatus(
            manufacturer = pumpManufacturer,
            model = pumpModel,
            pumpID = pumpId,
            battery = batteryPercent?.let { Battery(it) },
            reservoir = reservoirUnits,
            iob = createIOB(iob, bolusIob, basalIob, iobActivity, timestamp),
            status = pumpStatus?.let { PumpStatusInfo(status = it, timestamp = timestamp.toString()) },
            clock = timestamp.toString(),
            secondsFromGMT = secondsFromGMT
        ),
        uploaderBattery = uploaderBattery
    )
}

private fun createIOB(
    totalIob: Double?,
    bolusIob: Double?,
    basalIob: Double?,
    activity: Double?,
    timestamp: LocalDateTime
): IOB? {
    if (totalIob == null) return null

    return IOB(
        iob = totalIob,
        bolusIob = bolusIob,
        basalIob = basalIob,
        activity = activity,
        timestamp = timestamp.toString()
    )
}

// Update PumpStatus data class
data class PumpStatus(
    @SerializedName("manufacturer")
    val manufacturer: String = "Tandem",

    @SerializedName("model")
    val model: String? = null,

    @SerializedName("pumpID")
    val pumpID: String? = null,

    @SerializedName("battery")
    val battery: Battery? = null,

    @SerializedName("reservoir")
    val reservoir: Double? = null,

    @SerializedName("iob")
    val iob: IOB? = null,

    @SerializedName("status")
    val status: PumpStatusInfo? = null,

    @SerializedName("clock")
    val clock: String? = null,

    @SerializedName("secondsFromGMT")
    val secondsFromGMT: Int? = null
)

// Update IOB data class
data class IOB(
    @SerializedName("iob")
    val iob: Double,  // Total IOB

    @SerializedName("bolusiob")
    val bolusIob: Double? = null,

    @SerializedName("basaliob")
    val basalIob: Double? = null,

    @SerializedName("activity")
    val activity: Double? = null,  // Current insulin activity (U/hr)

    @SerializedName("timestamp")
    val timestamp: String? = null
)
```

---

## 9. Conclusion

ControlX2's current Nightscout implementation provides a **solid foundation** with core pump data upload capabilities. However, to achieve **feature parity** with AndroidAPS, Loop, and Trio, significant enhancements are needed in three key areas:

### Critical Gaps to Address:
1. **Carb Tracking** - Essential for complete meal tracking
2. **Pump Metadata** - Manufacturer, model, serial number
3. **Enhanced IOB** - Split basal/bolus components
4. **Profile Data** - Basal rates, IC ratios, ISF, targets

### High-Value Additions:
5. **Profile Management** - Upload and switch tracking
6. **Temp Targets** - Exercise/illness target adjustments
7. **Extended Boluses** - Complete bolus type coverage

### Future Enhancements (Algorithm-Dependent):
8. **Prediction Data** - Requires BG prediction algorithm
9. **COB Tracking** - Requires carb absorption algorithm
10. **Algorithm Decisions** - Requires closed-loop logic

### Implementation Priority:
- **Phase 1** (Priority 1): ~2-3 weeks - Foundation features
- **Phase 2** (Priority 2): ~3-4 weeks - Profile management
- **Phase 3** (Priority 3): ~2-3 weeks - Enhanced treatments
- **Phase 4** (Future): Algorithm-dependent features

By implementing **Phases 1-3**, ControlX2 will achieve **near-complete parity** with AndroidAPS, Loop, and Trio for open-loop pump monitoring and data visibility in Nightscout. Phase 4 features can be deferred until/unless ControlX2 implements closed-loop algorithm capabilities.

---

## References

### Documentation
- [Nightscout Documentation](https://nightscout.github.io/)
- [AndroidAPS Documentation](https://wiki.aaps.app/en/latest/SettingUpAaps/Nightscout.html)
- [Loop Documentation - Nightscout Overview](https://loopkit.github.io/loopdocs/nightscout/overview/)
- [OpenAPS Documentation - Nightscout Setup](https://openaps.readthedocs.io/en/latest/docs/While%20You%20Wait%20For%20Gear/nightscout-setup.html)

### GitHub Repositories
- [AndroidAPS](https://github.com/nightscout/AndroidAPS)
- [Loop](https://github.com/LoopKit/Loop)
- [Trio](https://github.com/nightscout/Trio)
- [tconnectsync](https://github.com/jwoglom/tconnectsync)
- [Nightscout CGM Remote Monitor](https://github.com/nightscout/cgm-remote-monitor)

### API Specifications
- [Nightscout API Documentation](https://nightscout-test.readthedocs.io/en/latest/Nightscout/EN/Technical%20info/api.html)
- [Nightscout Configuration Variables](https://nightscout.github.io/nightscout/setup_variables/)
- [Go Nightscout Package](https://pkg.go.dev/github.com/ecc1/nightscout) - Detailed struct definitions

### Key GitHub Issues & PRs
- [Loop PR #947 - Pump manufacturer and model](https://github.com/LoopKit/Loop/pull/947)
- [Loop Issue #1686 - DeviceStatus upload issues](https://github.com/LoopKit/Loop/issues/1686)
- [AndroidAPS Issue #3276 - Data loss with NSClientV3](https://github.com/nightscout/AndroidAPS/issues/3276)
- [Nightscout Issue #2227 - Ignore pump IOB if Loop IOB available](https://github.com/nightscout/cgm-remote-monitor/issues/2227)

---

**Analysis prepared by:** Claude (Anthropic AI Assistant)
**For:** ControlX2 Nightscout Enhancement Project
**Date:** 2025-12-12
