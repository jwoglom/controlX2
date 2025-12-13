# CGM Chart Component Gap Analysis

**Date:** December 13, 2025
**Branch:** claude/redesign-cgm-graph-01BfK4mapfPsxsvgN6GLXDd4
**Component:** `DashboardCgmChart.kt`

---

## Executive Summary

This document provides a comprehensive gap analysis between the current ControlX2 CGM chart implementation and industry-leading diabetes management applications: **Tandem Mobi**, **Loop**, and **Trio**. The analysis reveals significant opportunities for enhancing the chart's functionality, visual clarity, and clinical utility.

---

## Current Implementation Overview

### File Location
`/home/user/controlX2/mobile/src/main/java/com/jwoglom/controlx2/presentation/screens/sections/components/DashboardCgmChart.kt`

### Current Features
- **Chart Library:** Dautovicharis Charts v2.0.0 (LineChart component)
- **Data Displayed:**
  - CGM glucose values (black line)
  - Static high target line (200 mg/dL, orange)
  - Static low target line (80 mg/dL, red)
- **Data Points:** Up to 100 most recent CGM readings
- **Data Sources:** Dexcom G6 and G7 CGM sensors
- **Time Range:** Variable (depends on CGM reading frequency, typically 6-8 hours for 100 points at 5-minute intervals)
- **Visual Elements:**
  - No points visible on line (points hidden)
  - No axis labels
  - No time indicators
  - No grid lines
  - Transparent background
  - Fixed height (300dp default)

### Current Limitations
1. **No insulin delivery visualization** (basal or bolus)
2. **No carbohydrate tracking display**
3. **No Control-IQ/therapy mode indicators**
4. **No glucose predictions/forecasting**
5. **No time axis labels**
6. **No interactive features** (zoom, pan, detail view)
7. **Static target ranges** (not user-configurable)
8. **No event markers** (exercise, sleep, meals, etc.)
9. **No insulin-on-board (IOB) or carbs-on-board (COB) visualization**
10. **No contextual information** about pump activity

---

## Competitive Analysis

### 1. Tandem Mobi App

#### Chart Features
**Time Range:**
- 3-hour CGM graph on mobile dashboard
- Extended views available on web platform (t:connect)

**CGM Data Display:**
- Current CGM reading prominently displayed
- Continuous glucose line graph
- Target range shading (in-range visualization)

**Insulin Delivery Indicators:**
- **Blue squares:** Manual bolus events
- **Blue squares with white outline:** Control-IQ automatic correction boluses
- Basal rate information displayed (current basal, active profile)
- Insulin-on-board (IOB) metric

**Additional Metrics:**
- Time-in-Range (TIR) for past 24 hours
- Carbohydrate entries
- Pump status and settings
- Sensor status
- Alert/alarm display

**Known Limitations (from user feedback):**
- Graph has very large y-axis range, making it hard to see detail
- In-range portion occupies less than 25% of vertical space
- Limited zoom/interaction capabilities on mobile
- Better detail view only available on desktop/web version

**Key Differentiators:**
- ✅ Bolus event markers with distinction between manual and automated
- ✅ Basal rate display
- ✅ IOB tracking
- ✅ Carb entry tracking
- ✅ Time-in-Range metrics
- ⚠️ Graph scaling issues noted by users

---

### 2. Loop App (Open-Source AID)

#### Display Architecture
**Multi-Chart Layout:**
Loop uses a comprehensive multi-chart approach with separate, specialized visualizations:

1. **Glucose Chart**
   - **Color-coded glucose values:**
     - Red: ≤55 mg/dL (severe hypoglycemia)
     - Yellow: 56-79 mg/dL and ≥200 mg/dL (out of range)
     - Blue: 80-199 mg/dL (in range)
   - **Blue bar:** Correction range indicator
   - **Darker shading:** Temporary override ranges (e.g., exercise, sleep mode)
   - **Prediction line:** Forward-looking glucose forecast
   - **Time range:** Extends backward based on screen width, forward through insulin duration period
   - **Manual glucose entries:** Marked separately from CGM data

2. **Active Insulin Chart**
   - Shows total insulin contribution from both temp basals and boluses
   - **Positive values:** Insulin contributing to glucose reduction
   - **Negative values:** Suspended/reduced basal (less insulin than scheduled)
   - **Current IOB value:** Displayed in upper right corner
   - Updates immediately when Loop issues pump commands

3. **Insulin Delivery Chart**
   - **Temporary basal visualization:** Shown relative to scheduled basal rate
   - **Orange triangles:** Individual bolus events
   - **+0 units display:** Indicates scheduled basal running unmodified
   - **Total daily insulin:** Displayed in upper right (since midnight)
   - Historical insulin administration tracking

4. **Carbohydrate Chart**
   - Active carbohydrate visualization
   - **Current COB value:** Displayed in upper right corner
   - Tap to access detailed meal entry history
   - Edit previous carb entries

**Heads-Up Display (HUD):**
- Portrait mode only
- Quick status summary at top of screen

**Toolbar:**
- Bottom of screen (portrait and landscape)
- Quick actions: Meal Entry, Pre-Meal Range, Manual Bolus, Override, Settings

**Key Differentiators:**
- ✅ Dedicated charts for different data types (separation of concerns)
- ✅ Color-coded glucose values with clinical significance
- ✅ Glucose prediction/forecasting
- ✅ Temporary override visualization (exercise, sleep modes)
- ✅ Comprehensive bolus tracking with visual markers
- ✅ Temporal basal rate changes clearly shown
- ✅ Active insulin and carb values prominently displayed
- ✅ Interactive meal history with edit capability
- ✅ Total daily insulin tracking

---

### 3. Trio App (Open-Source AID based on OpenAPS)

#### Integrated Chart Display
**Main Chart Components:**
Trio uses a single, highly integrated chart that combines multiple data layers:

**Glucose Visualization:**
- **CGM values:** Smoothed glucose line
- **Manual glucose entries:** Distinct markers
- **Color coding:**
  - Toggle between static and dynamic coloring
  - High/low threshold indicators
  - Above-range, in-range, below-range distinction

**Basal Rate Display:**
- **Blue reference line:** "Therapy basal rate" (configured baseline)
- **Basal adjustments:**
  - **Increases:** Displayed above the reference line
  - **Decreases:** Displayed below the reference line
  - **No adjustment:** Maintains standard rate on the line
- **Status indicator:** Below graph shows current adjustment type
  - "No Basal Adjustment"
  - "Basal Increase"
  - "Basal Decrease"

**Insulin and Carbohydrate Decay:**
- **Blue:** Insulin activity decay visualization
- **Orange:** Carbohydrate activity decay visualization
- Shows how insulin and carbs diminish over time post-administration

**Treatment Markers:**
- **Bolus events:** Dedicated icons for SMB (super micro bolus) and manual boluses
- **Carb entries:** Specific markers with values
- **FPU (Fat-Protein Units):** Tracked separately from regular carbs
- **Active overrides:** Distinct icons (e.g., exercise mode)
- **Temp targets:** Visual indicators for temporary glucose targets

**Glucose Forecasting:**
- **"Cone of Uncertainty":** Visual representation of possible glucose trajectories
- **Four forecast types:**
  - **IOB:** Insulin-only-based prediction
  - **ZT (Zero Temp):** Worst-case scenario (if all insulin delivery stopped)
  - **COB:** Carbohydrate-based prediction
  - **UAM (Unannounced Meal):** Prediction accounting for unlogged meals

**Interactive Features:**
- Swipe left to delete inaccurate bolus/SMB entries
- History screen with detailed event information
- Shows SMB vs. External (manual) bolus classification

**Key Differentiators:**
- ✅ Highly integrated single-chart approach (all data in one view)
- ✅ Sophisticated basal rate visualization with reference line
- ✅ Insulin and carb activity decay curves
- ✅ Multiple prediction algorithms with uncertainty visualization
- ✅ Fat-protein unit tracking for complex meals
- ✅ Override and temp target visualization
- ✅ Interactive event management (swipe to delete)
- ✅ Comprehensive treatment marker system
- ✅ Real-time status indicators below graph

---

## Detailed Gap Analysis

### 1. Insulin Delivery Visualization

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Basal rate display | ❌ None | ✅ Text display | ✅ Dedicated chart | ✅ Blue reference line with adjustments | **CRITICAL** |
| Bolus markers | ❌ None | ✅ Blue squares | ✅ Orange triangles | ✅ Dedicated icons | **CRITICAL** |
| Automated vs manual bolus distinction | ❌ None | ✅ Square outline | ❌ Not shown | ✅ SMB vs External labels | **HIGH** |
| Temp basal visualization | ❌ None | ⚠️ Text only | ✅ Relative to scheduled | ✅ Above/below reference | **HIGH** |
| Total daily insulin | ❌ None | ❌ None | ✅ Since midnight | ❌ None | **MEDIUM** |
| Insulin-on-board (IOB) | ❌ Not on chart | ✅ Dashboard metric | ✅ Dedicated chart | ✅ Integrated display | **HIGH** |

**Impact:** Users cannot see when insulin was delivered or how pump therapy correlates with glucose changes. This is fundamental for understanding glucose patterns.

**Recommendation:**
- Add bolus markers as vertical lines or icons at delivery times
- Show basal rate as a stacked area chart or stepped line at bottom of graph
- Distinguish between user-initiated and Control-IQ automated boluses
- Display current IOB value on or near the chart

---

### 2. Carbohydrate Tracking

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Carb entries display | ❌ None | ✅ Shown on dashboard | ✅ Dedicated chart | ✅ Orange markers | **HIGH** |
| Carbs-on-board (COB) | ❌ None | ❌ None | ✅ Dedicated chart | ✅ Orange decay curve | **HIGH** |
| Meal markers | ❌ None | ✅ Tracked | ✅ Visual markers | ✅ Icons with values | **HIGH** |
| Fat-protein units (FPU) | ❌ None | ❌ None | ❌ None | ✅ Separate tracking | **LOW** |
| Edit capability | ❌ N/A | ⚠️ Limited | ✅ Tap to edit history | ✅ Swipe to delete | **MEDIUM** |

**Impact:** Carbohydrates are the primary driver of glucose changes. Without carb visualization, users cannot correlate meals with glucose excursions or evaluate bolus timing/dosing effectiveness.

**Recommendation:**
- Add carb entry markers (e.g., orange icons) at meal times
- Show carb values on markers
- Display COB decay curve or integrate with chart
- Allow users to tap markers to view/edit carb entries

---

### 3. Glucose Prediction & Forecasting

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Glucose prediction line | ❌ None | ❌ None | ✅ Single prediction | ✅ Multiple forecast types | **MEDIUM** |
| Prediction algorithm(s) | ❌ N/A | ❌ N/A | ✅ IOB-based | ✅ IOB, ZT, COB, UAM | **MEDIUM** |
| Uncertainty visualization | ❌ None | ❌ None | ❌ None | ✅ Cone of uncertainty | **LOW** |
| Forecast time range | ❌ N/A | ❌ N/A | ✅ Through insulin duration | ✅ Configurable | **MEDIUM** |

**Impact:** Predictions help users make proactive therapy decisions. Without forecasting, users can only react to current glucose levels rather than anticipating changes.

**Recommendation:**
- Initially implement basic IOB-based glucose prediction
- Show prediction as dotted/dashed line extending from current glucose
- Future: Add COB-based predictions and uncertainty ranges
- Consider making predictions toggleable for users who prefer simpler display

---

### 4. Control-IQ / Therapy Mode Indicators

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Sleep mode indicator | ❌ None | ✅ Shown | ✅ Darker shading on range | ✅ Override icon | **HIGH** |
| Exercise mode indicator | ❌ None | ✅ Shown | ✅ Darker shading on range | ✅ Override icon | **HIGH** |
| Temporary target display | ❌ None | ⚠️ Limited | ✅ Range shading | ✅ Visual indicator | **HIGH** |
| Active mode duration | ❌ None | ⚠️ Status only | ✅ Visual time span | ✅ Time-based marker | **MEDIUM** |

**Impact:** Control-IQ therapy modes (Sleep, Exercise) significantly affect insulin delivery and target ranges. Without visualization, users cannot see when modes were active or evaluate their effectiveness.

**Recommendation:**
- Add background shading or colored bars when Sleep/Exercise mode active
- Show temporary target range lines when active
- Display mode name/icon during active period
- Show mode duration on chart timeline

---

### 5. Chart Interactivity & Usability

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Time axis labels | ❌ None | ✅ Shown | ✅ Shown | ✅ Shown | **CRITICAL** |
| Y-axis glucose labels | ⚠️ Implied by targets | ✅ Shown | ✅ Shown | ✅ Shown | **CRITICAL** |
| Grid lines | ❌ None | ✅ Shown | ✅ Shown | ✅ Shown | **HIGH** |
| Zoom/pan capability | ❌ None | ❌ None | ⚠️ Limited | ⚠️ Limited | **MEDIUM** |
| Tap for details | ❌ None | ❌ None | ✅ Some charts | ✅ Event details | **MEDIUM** |
| Time range selection | ❌ Fixed | ⚠️ 3hr fixed | ✅ Variable | ✅ Variable | **MEDIUM** |
| Landscape orientation | ⚠️ Not optimized | ⚠️ Not optimized | ✅ Optimized | ✅ Optimized | **LOW** |

**Impact:** Without time labels and grid lines, the chart provides limited actionable information. Users cannot determine when events occurred or correlate glucose patterns with specific times of day.

**Recommendation:**
- Add time axis with hour markers (e.g., 6am, 9am, 12pm)
- Add glucose value labels on y-axis (e.g., 50, 100, 150, 200, 250 mg/dL)
- Add subtle grid lines for easier reading
- Implement tap-to-view-details for specific data points
- Allow users to select time range (3hr, 6hr, 12hr, 24hr)

---

### 6. Color Coding & Visual Hierarchy

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Glucose color coding | ❌ Single color | ⚠️ Basic | ✅ Red/Yellow/Blue by range | ✅ Dynamic/static modes | **HIGH** |
| Target range visualization | ✅ Static lines | ✅ Shaded area | ✅ Blue bar | ✅ Threshold lines | **MEDIUM** |
| Clinical significance coding | ❌ None | ⚠️ Basic | ✅ Color by severity | ✅ Configurable | **MEDIUM** |
| Visual contrast | ⚠️ Low | ⚠️ Reported issues | ✅ Good | ✅ Good | **HIGH** |

**Impact:** Single-color glucose line doesn't convey clinical urgency. Loop's color coding (red for severe hypoglycemia, yellow for out-of-range) immediately draws attention to concerning values.

**Recommendation:**
- Implement color-coded glucose line segments:
  - Red: <70 mg/dL (hypoglycemia)
  - Yellow: 70-79 mg/dL and 181-250 mg/dL (elevated/borderline)
  - Green/Blue: 80-180 mg/dL (in range)
  - Orange/Red: >250 mg/dL (severe hyperglycemia)
- Add shaded target range background
- Ensure colors meet accessibility standards (colorblind-friendly palette)

---

### 7. Data Density & Information Architecture

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Single integrated chart | ✅ Yes | ✅ Yes | ❌ Multiple charts | ✅ Yes | **N/A** |
| Chart separation | ❌ One chart only | ❌ One chart | ✅ 4 dedicated charts | ❌ One integrated | **N/A** |
| Data layer control | ❌ None | ❌ None | ⚠️ Via separate charts | ✅ Toggle options | **MEDIUM** |
| Information density | ⚠️ Very low | ⚠️ Low-Medium | ✅ High (via separation) | ✅ Very high | **HIGH** |
| Cognitive load | ✅ Very low | ✅ Low | ⚠️ Medium-High | ⚠️ High | **N/A** |

**Impact:** Current chart shows minimal information. Loop's multi-chart approach provides comprehensive data but requires more screen space. Trio packs maximum information into one chart but may be overwhelming for some users.

**Recommendation:**
- Start with integrated approach (like Trio/Tandem)
- Add progressive disclosure: show key data by default, allow expansion for details
- Consider toggles to show/hide data layers (basal, bolus, carbs, predictions)
- Future: Explore separate chart views as advanced option

---

### 8. Target Range Configuration

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| User-configurable ranges | ❌ Hardcoded (80-200) | ✅ Follows pump settings | ✅ User configured | ✅ User configured | **HIGH** |
| Multiple target ranges | ❌ No | ✅ Via pump profiles | ✅ Temp targets | ✅ Dynamic targets | **MEDIUM** |
| Time-based targets | ❌ No | ✅ Via basal profiles | ✅ Via schedules | ✅ Temp overrides | **MEDIUM** |

**Impact:** Hardcoded targets (80-200 mg/dL) don't match all users' clinical goals. Many users target 70-180 mg/dL or other ranges based on provider recommendations.

**Recommendation:**
- Fetch target ranges from pump settings (already available in PumpState)
- Allow user customization in app settings
- Display active target range on chart
- Support temporary target ranges for exercise/sleep modes

---

### 9. Event Markers & Annotations

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Exercise markers | ❌ None | ⚠️ Via modes | ✅ Override shading | ✅ Icons | **MEDIUM** |
| Sleep markers | ❌ None | ⚠️ Via modes | ✅ Override shading | ✅ Icons | **MEDIUM** |
| Alert/alarm markers | ❌ None | ⚠️ Separate view | ❌ None | ❌ None | **LOW** |
| Sensor changes | ❌ None | ❌ None | ❌ None | ❌ None | **LOW** |
| Pump site changes | ❌ None | ❌ None | ❌ None | ❌ None | **LOW** |
| Notes/annotations | ❌ None | ❌ None | ❌ None | ❌ None | **LOW** |

**Impact:** Context around glucose patterns is valuable. Knowing when exercise occurred or sensor was changed helps interpret glucose data.

**Recommendation:**
- Phase 1: Show Control-IQ mode changes (Sleep, Exercise)
- Phase 2: Add sensor change markers from HistoryLog
- Phase 3: Consider user-added notes/annotations

---

### 10. Time Range & Historical Data

| Feature | Current | Tandem Mobi | Loop | Trio | Priority |
|---------|---------|-------------|------|------|----------|
| Default time range | ⚠️ ~6-8hr (100 pts) | 3 hours | 6 hours | Variable | **MEDIUM** |
| Adjustable time range | ❌ No | ❌ No | ✅ Via screen width | ✅ Yes | **MEDIUM** |
| Historical view | ❌ Not easily | ✅ Via web portal | ⚠️ Limited | ⚠️ Limited | **LOW** |
| Data point limit | ✅ 100 points | ⚠️ Unknown | ⚠️ Screen-based | ⚠️ Unknown | **LOW** |

**Impact:** Fixed time range limits flexibility. Some users want quick 3-hour view, others prefer 12-24 hour trends.

**Recommendation:**
- Add time range selector (3hr, 6hr, 12hr, 24hr buttons)
- Make 6 hours the default (aligns with industry standard)
- Maintain efficient data loading (don't fetch unnecessary history)
- Consider pull-to-refresh for latest data

---

## Priority Matrix

### CRITICAL (Must Have - Core Functionality)
1. **Time axis labels** - Users need to know when events occurred
2. **Y-axis glucose value labels** - Users need glucose scale reference
3. **Basal rate visualization** - Fundamental therapy component
4. **Bolus markers** - Critical therapy event indicators

### HIGH (Should Have - Clinical Value)
5. **Grid lines** - Improves readability
6. **Carb entry markers** - Essential for pattern analysis
7. **Color-coded glucose values** - Conveys clinical urgency
8. **IOB display on/near chart** - Key decision-making metric
9. **COB display on/near chart** - Complements IOB for therapy decisions
10. **Sleep/Exercise mode indicators** - Affects target ranges and insulin delivery
11. **User-configurable target ranges** - Personalization for clinical goals
12. **Visual contrast improvements** - Accessibility and usability
13. **Automated vs manual bolus distinction** - Understanding Control-IQ behavior
14. **Temp basal visualization** - Shows when Control-IQ adjusts delivery

### MEDIUM (Nice to Have - Enhanced UX)
15. **Glucose predictions** - Proactive decision support
16. **Time range selection** - User preference flexibility
17. **Tap for details** - Interactive data exploration
18. **Temp target visualization** - Context for therapy modes
19. **Exercise/Sleep mode duration** - Historical pattern analysis
20. **Data layer toggles** - Reduce information overload
21. **Active mode duration display** - Understanding mode effectiveness
22. **Multiple target range support** - Advanced therapy management

### LOW (Future Enhancements)
23. **Uncertainty visualization** - Advanced prediction confidence
24. **Fat-protein unit tracking** - Complex meal management
25. **Landscape optimization** - Screen real estate utilization
26. **Sensor/pump site change markers** - Troubleshooting tool
27. **User annotations** - Personal context notes
28. **Zoom/pan capability** - Detailed examination

---

## Technical Recommendations

### Architecture Considerations

**Chart Library Evaluation:**
- **Current:** Dautovicharis Charts v2.0.0
  - Pros: Simple API, lightweight
  - Cons: Limited customization, may not support all required features
- **Alternatives to consider:**
  - **Vico** (Patrykandpatrick) - Modern, Compose-native, highly customizable
  - **MPAndroidChart** - Feature-rich, widely used, proven
  - **Custom Canvas/Compose drawing** - Maximum control, performance

**Recommendation:** Evaluate Vico library first - it's designed for Jetpack Compose and offers excellent customization for complex multi-layer charts like those needed here.

### Data Layer Enhancements

**Current Data Sources (HistoryLog):**
- ✅ CGM readings (DexcomG6CGMHistoryLog, DexcomG7CGMHistoryLog)
- ✅ Bolus events (available in HistoryLog - need to identify types)
- ✅ Basal rate changes (available in HistoryLog)
- ✅ Control-IQ events (available via existing dataStore)

**Additional Data Needed:**
- [ ] Carbohydrate entries (if available in pump memory)
- [ ] Therapy mode changes with timestamps
- [ ] Insulin-on-board calculations (may need client-side calculation)
- [ ] Carbs-on-board calculations (client-side)
- [ ] Glucose predictions (client-side algorithm)

**HistoryLog Types to Investigate:**
```kotlin
// Explore these HistoryLog message types for chart data:
- BasalRateChangeHistoryLog
- BolusHistoryLog / StandardBolusHistoryLog
- CarbHistoryLog (if available)
- ControlIQModeChangeHistoryLog
- ExerciseModeChangeHistoryLog / SleepModeChangeHistoryLog
```

### Performance Considerations

**Data Volume:**
- 24 hours at 5-minute CGM intervals = 288 data points
- Plus basal rate changes (~48-96 data points with temp basals)
- Plus bolus events (~10-30 per day)
- Plus carb entries (~5-15 per day)
- **Total:** ~350-450 data points for 24-hour view

**Optimization Strategies:**
- Implement data downsampling for longer time ranges (>12 hours)
- Use LazyColumn/LazyRow for scrollable historical views
- Cache processed chart data (don't recalculate on every recomposition)
- Consider using derivedStateOf for computed chart values
- Implement efficient data queries (already done: latestItemsForTypes with limit)

### UI/UX Design Principles

**Progressive Disclosure:**
1. **Level 1 (Default):** CGM line, target range, current boluses, time/value axes
2. **Level 2 (Expanded):** Add basal rate, IOB/COB values, mode indicators
3. **Level 3 (Advanced):** Add predictions, detailed event markers, carb decay

**Accessibility:**
- Use colorblind-friendly palette (avoid red-green only distinctions)
- Ensure text labels meet WCAG contrast ratios
- Provide text descriptions of chart data for screen readers
- Support dynamic text sizing

**Information Hierarchy:**
- **Most important:** Current glucose, trend direction
- **Very important:** Recent glucose history, active insulin/carbs
- **Important:** Bolus events, target range
- **Helpful:** Basal rates, mode indicators, predictions

---

## Implementation Roadmap

### Phase 1: Core Chart Improvements (Week 1-2)
**Goals:** Make chart readable and show insulin therapy basics

1. ✅ Add time axis with hour labels
2. ✅ Add glucose value labels on y-axis
3. ✅ Add subtle grid lines (horizontal for glucose values, vertical for time)
4. ✅ Implement color-coded glucose line by range
5. ✅ Add shaded target range background
6. ✅ Add basic bolus markers (vertical lines or icons)
7. ✅ Fetch and display user's actual target ranges from pump

**Deliverable:** Chart shows CGM data with proper axes, targets, and basic bolus events

### Phase 2: Insulin Delivery Visualization (Week 3-4)
**Goals:** Show complete insulin therapy picture

8. ✅ Add basal rate visualization (stepped line or stacked area at bottom)
9. ✅ Distinguish automated vs manual boluses (different icons/colors)
10. ✅ Show temp basal changes relative to scheduled rate
11. ✅ Display IOB value on chart (top corner or below chart)
12. ✅ Add Control-IQ mode indicators (Sleep, Exercise background shading)

**Deliverable:** Chart shows all insulin delivery with context for pump automation

### Phase 3: Carbohydrate Tracking (Week 5)
**Goals:** Correlate meals with glucose patterns

13. ✅ Query and display carb entries from HistoryLog
14. ✅ Add carb markers (icons with gram values)
15. ✅ Display COB value on chart
16. ✅ Optional: Show carb decay curve (orange area under curve)

**Deliverable:** Chart shows carb intake and active carbs

### Phase 4: Interactivity & Usability (Week 6-7)
**Goals:** Improve user interaction and customization

17. ✅ Add time range selector (3hr, 6hr, 12hr, 24hr buttons)
18. ✅ Implement tap-to-view-details for data points
19. ✅ Add pull-to-refresh for latest data
20. ✅ Allow toggling data layers (basal, bolus, carbs on/off)
21. ✅ Settings for target range customization

**Deliverable:** Interactive, configurable chart matching user preferences

### Phase 5: Advanced Features (Week 8+)
**Goals:** Predictive and analytical capabilities

22. ✅ Implement basic glucose prediction (IOB-based algorithm)
23. ✅ Show prediction line with dotted/dashed styling
24. ✅ Add uncertainty range (optional cone)
25. ✅ Optimize landscape orientation
26. ✅ Add sensor change markers
27. ✅ Consider multi-chart layout option (Loop-style)

**Deliverable:** Advanced chart with predictive insights

---

## User Stories

### As a user, I want to...

1. **See when I took insulin** so I can understand why my glucose dropped
   - *Current:* ❌ No insulin data shown
   - *Tandem Mobi:* ✅ Blue squares for boluses
   - *Loop:* ✅ Orange triangles for boluses, dedicated insulin chart
   - *Trio:* ✅ Bolus icons with values

2. **Know what my basal rate is doing** so I can see when Control-IQ makes adjustments
   - *Current:* ❌ No basal data shown
   - *Tandem Mobi:* ⚠️ Text display only
   - *Loop:* ✅ Dedicated basal chart showing increases/decreases
   - *Trio:* ✅ Blue reference line with adjustments above/below

3. **Correlate meals with glucose spikes** so I can adjust my carb counting or timing
   - *Current:* ❌ No carb data shown
   - *Tandem Mobi:* ✅ Carb entries tracked
   - *Loop:* ✅ Dedicated carb chart
   - *Trio:* ✅ Orange carb markers with decay curve

4. **See if I'm trending high or low** so I can take action before going out of range
   - *Current:* ⚠️ Can see trend from line but no prediction
   - *Tandem Mobi:* ⚠️ Current trend only
   - *Loop:* ✅ Prediction line shows future glucose
   - *Trio:* ✅ Multiple prediction types with uncertainty

5. **Understand when Control-IQ Sleep/Exercise mode was active** so I can evaluate if modes are helping
   - *Current:* ❌ Mode shown separately, not on chart timeline
   - *Tandem Mobi:* ⚠️ Mode status shown but not on chart timeline
   - *Loop:* ✅ Override shading shows mode duration
   - *Trio:* ✅ Override icons show mode periods

6. **Quickly identify dangerous glucose levels** so I can respond to emergencies
   - *Current:* ⚠️ Red/orange target lines but glucose line is black
   - *Tandem Mobi:* ⚠️ Basic in-range indication
   - *Loop:* ✅ Red color for severe hypoglycemia, yellow for out-of-range
   - *Trio:* ✅ Color-coded thresholds

7. **See what time events happened** so I can make timing adjustments to my therapy
   - *Current:* ❌ No time axis labels
   - *Tandem Mobi:* ✅ Time axis shown
   - *Loop:* ✅ Time axis with hour labels
   - *Trio:* ✅ Time axis with labels

8. **View different time ranges** so I can see short-term patterns or long-term trends
   - *Current:* ❌ Fixed at ~6-8 hours (100 data points)
   - *Tandem Mobi:* ⚠️ Fixed at 3 hours (web portal has more options)
   - *Loop:* ✅ Adjustable based on screen orientation/width
   - *Trio:* ✅ User-selectable time ranges

9. **Know how much insulin is still active** so I can avoid stacking boluses
   - *Current:* ⚠️ IOB shown as text below chart
   - *Tandem Mobi:* ✅ IOB shown on dashboard
   - *Loop:* ✅ IOB chart with current value
   - *Trio:* ✅ IOB decay curve on integrated chart

10. **See if a bolus was from me or from Control-IQ** so I can learn how the automation works
    - *Current:* ❌ No bolus data shown
    - *Tandem Mobi:* ✅ Outlined vs solid squares
    - *Loop:* ⚠️ Not distinguished
    - *Trio:* ✅ SMB vs External labels

---

## Accessibility & Compliance

### Clinical Safety Considerations
- Ensure color coding doesn't obscure actual glucose values
- Provide redundant indicators (not color-only) for critical information
- Clear labeling of predictions as "estimated" vs actual data
- Maintain high contrast for readability in various lighting conditions

### Regulatory Considerations
- If predictions are added, ensure they're clearly marked as estimates
- Maintain data accuracy (no interpolation that could mislead)
- Preserve audit trail (don't allow editing of historical data from chart)
- Consider FDA guidance on mobile medical apps if applicable

### Accessibility (WCAG 2.1 AA)
- Text contrast ratio ≥ 4.5:1 for normal text
- Large text contrast ratio ≥ 3:1
- Don't rely solely on color to convey information
- Ensure chart is describable by screen readers
- Support system font size scaling

---

## Conclusion

The current ControlX2 CGM chart provides basic glucose visualization but lacks critical features present in competing diabetes management applications. The most significant gaps are:

1. **No insulin delivery visualization** - Users cannot see bolus or basal therapy
2. **No carbohydrate tracking** - Missing key context for glucose patterns
3. **No time/value axis labels** - Chart is difficult to interpret quantitatively
4. **No therapy mode indicators** - Control-IQ automation context not shown
5. **Static target ranges** - Not personalized to user's clinical goals

Implementing the recommendations in this analysis will transform the chart from a basic glucose line graph into a comprehensive diabetes management visualization tool that rivals or exceeds the functionality of Tandem Mobi, Loop, and Trio applications.

The phased implementation roadmap provides a structured approach to incrementally add features while maintaining code quality and user experience. Priority should be given to CRITICAL and HIGH items that provide the most clinical value and usability improvements.

---

## References

### Tandem Mobi
- [Tandem Diabetes Mobile Apps](https://www.tandemdiabetes.com/products/software-apps/mobile-apps)
- [t:connect Mobile App](https://www.tandemdiabetes.com/providers/products/tconnect-mobile-app)
- [Connected in Motion - Tandem Mobile Bolus Update](https://www.connectedinmotion.ca/blog/bolus-with-your-phone-tandem-tslim-x2-mobile-bolus-update/)
- [DiaTribe - t:connect Mobile App Review](https://diatribe.org/personal-take-tandems-new-tconnect-mobile-app)

### Loop
- [LoopDocs - Displays](https://loopkit.github.io/loopdocs/loop-3/displays-v3/)
- [LoopDocs - Bolus Features](https://loopkit.github.io/loopdocs/operation/features/bolus/)
- [Loop and Learn - Display Guide](https://www.loopandlearn.org/sl-display/)
- [DiaTribe - How I Loop](https://diatribe.org/how-i-loop-two-years-using-iphone-app-automate-my-insulin-delivery)

### Trio
- [TrioDocs - User Interface](https://triodocs.org/usage/interface/)
- [TrioDocs - New User Setup](https://triodocs.org/configuration/new-user-setup/)
- [TrioDocs - SMB Settings](https://triodocs.org/configuration/settings/algorithm/smb-settings/)

### General CGM/Pump Data Visualization
- [ADA - Interpreting Insulin Pump & CGM Data](https://professional.diabetes.org/sites/default/files/media/sat_130_and_330_issacs.pdf)
- [ADCES - Advanced Features of Open-Source AID Apps](https://www.adces.org/education/danatech/insulin-pumps/diy-looping-open-source/advanced-features-of-open-source-aid-apps-for-diabetes-care)

---

**Document Version:** 1.0
**Last Updated:** December 13, 2025
**Author:** Claude (Anthropic AI)
**Review Status:** Draft - Awaiting User Feedback
