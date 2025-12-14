# Dashboard UI Redesign - Detailed Design Plan

**Date:** December 13, 2025
**Branch:** claude/redesign-cgm-graph-01BfK4mapfPsxsvgN6GLXDd4
**Target:** Dashboard.kt and DashboardCgmChart.kt
**Chart Library:** Vico 2.3.6 (compose-m3)
**Design Language:** Material Design 3, inspired by Tandem Mobi UI

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Design Philosophy](#design-philosophy)
3. [Visual Design System](#visual-design-system)
4. [Component Architecture](#component-architecture)
5. [CGM Chart Design with Vico](#cgm-chart-design-with-vico)
6. [Dashboard Layout Design](#dashboard-layout-design)
7. [Jetpack Compose Best Practices](#jetpack-compose-best-practices)
8. [Implementation Roadmap](#implementation-roadmap)
9. [Technical Specifications](#technical-specifications)

---

## Executive Summary

This document outlines a comprehensive redesign of the ControlX2 Dashboard screen and CGM chart component, transitioning from the current basic implementation to a medical-grade, visually polished interface inspired by Tandem Mobi's design language. The redesign will:

- **Replace** Dautovicharis Charts with Vico 2.3.6 for superior customization and performance
- **Implement** Material Design 3 patterns with medical UI best practices
- **Reorganize** the Dashboard into logical, card-based sections
- **Enhance** the CGM chart with insulin delivery, carb tracking, and therapy mode visualization
- **Follow** Jetpack Compose best practices for maintainability and performance

**Key Goals:**
- ✅ Medical-grade UI clarity and reliability
- ✅ Tandem Mobi visual similarity (clean, professional, accessible)
- ✅ Rich data visualization without cognitive overload
- ✅ Modular, testable, maintainable Compose architecture
- ✅ Excellent performance (smooth scrolling, fast recomposition)

---

## Design Philosophy

### Tandem-Inspired Principles

Based on Tandem Mobi app analysis and medical UI best practices:

1. **Clarity Over Complexity**
   - Information hierarchy: Critical data (glucose) → Important data (insulin, IOB) → Contextual data (battery, settings)
   - White space and breathing room prevent visual clutter
   - Clear typography hierarchy with consistent sizing

2. **Medical-Grade Reliability**
   - Color coding must convey clinical significance (red = danger, yellow = caution, green/blue = safe)
   - No decorative elements that distract from health data
   - High contrast for readability in various lighting conditions
   - Accessibility-first design (colorblind-safe palettes, screen reader support)

3. **Glanceable Information**
   - Most important data visible without scrolling
   - Large, readable numbers for current glucose
   - Visual status indicators (icons, colors) supplement text
   - Progressive disclosure: summary on top, details below

4. **Calm, Professional Aesthetic**
   - Soft colors, rounded corners (Material Design 3)
   - Subtle shadows and elevation
   - Smooth animations and transitions
   - Medical white/light background with colored accents

---

## Visual Design System

### Color Palette

**Primary Colors (Material 3 Dynamic):**
```kotlin
// Leverage system dynamic colors for modern Android 12+ feel
val colorScheme = dynamicLightColorScheme(context)

// Custom health-specific colors
val GlucoseColors = object {
    val Severe = Color(0xFFC62828)        // Deep Red (< 70 mg/dL)
    val Low = Color(0xFFFF5252)           // Red Accent (70-79 mg/dL)
    val InRange = Color(0xFF1976D2)       // Blue (80-180 mg/dL)
    val Elevated = Color(0xFFFF9800)      // Orange (181-250 mg/dL)
    val High = Color(0xFFE65100)          // Deep Orange (> 250 mg/dL)
}

val InsulinColors = object {
    val Bolus = Color(0xFF5E35B1)         // Purple
    val AutoBolus = Color(0xFF7E57C2)     // Light Purple
    val Basal = Color(0xFF1565C0)         // Dark Blue
    val TempBasal = Color(0xFF42A5F5)     // Light Blue
}

val CarbColor = Color(0xFFFF8F00)         // Orange
val TargetRangeColor = Color(0xFFE0F2F1) // Background (Teal)
val GridLineColor = Color(0xFFEEEEEE)     // Light Gray
```

**Background & Surface:**
```kotlin
val SurfaceBackground = Color(0xFFFAFAFA)   // Background (Off-white)
val CardBackground = Color(0xFFFFFFFF)      // Card Background (White)
val CardElevation = 2.dp                     // Subtle shadow
```

### Typography

**Material 3 Typography Scale:**
```kotlin
// Based on existing Type.kt, enhanced for medical UI
val Typography = Typography(
    // Current glucose reading (hero number)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        letterSpacing = (-0.5).sp
    ),

    // Section headers
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),

    // Data labels (IOB, Carbs, etc.)
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),

    // Chart axis labels
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),

    // Body text
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)
```

### Spacing System

```kotlin
object Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp

    // Card-specific
    val CardPadding = 16.dp
    val CardSpacing = 12.dp
    val CardCornerRadius = 12.dp
}
```

### Elevation

```kotlin
object Elevation {
    val Card = 2.dp
    val ChartMarker = 4.dp
    val Modal = 8.dp
}
```

---

## Component Architecture

### Component Hierarchy

```
DashboardScreen
├── TopAppBar (existing)
├── LazyColumn
│   ├── GlucoseHeroCard              [NEW]
│   │   ├── CurrentGlucoseDisplay
│   │   └── TrendArrowIndicator
│   │
│   ├── PumpStatusCard               [ENHANCED]
│   │   ├── BatteryIndicator
│   │   ├── LastConnectionTime
│   │   └── CartridgeIndicator
│   │
│   ├── CgmChartCard                 [REDESIGNED]
│   │   ├── ChartTimeRangeSelector
│   │   ├── VicoCgmChart
│   │   │   ├── GlucoseLayer
│   │   │   ├── TargetRangeLayer
│   │   │   ├── BasalLayer
│   │   │   ├── BolusMarkers
│   │   │   ├── CarbMarkers
│   │   │   ├── ModeIndicators
│   │   │   └── AxisAndGrid
│   │   └── ChartLegend
│   │
│   ├── TherapyMetricsCard           [NEW]
│   │   ├── IOBDisplay
│   │   ├── COBDisplay
│   │   └── TimeInRangeDisplay
│   │
│   ├── ActiveTherapyCard            [REORGANIZED]
│   │   ├── BasalRateDisplay
│   │   ├── LastBolusDisplay
│   │   └── ControlIQModeDisplay
│   │
│   └── SensorInfoCard               [REORGANIZED]
│       ├── SensorExpirationDisplay
│       └── TransmitterBatteryDisplay
```

### Component Breakdown

#### 1. GlucoseHeroCard (NEW)

**Purpose:** Prominently display current glucose reading with clinical color coding

**Compose Structure:**
```kotlin
@Composable
fun GlucoseHeroCard(
    glucoseValue: Int?,
    deltaArrow: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label
            Text(
                "Current Glucose",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.Small))

            // Hero number with color coding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = glucoseValue?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = getGlucoseColor(glucoseValue)
                )

                Spacer(Modifier.width(Spacing.Medium))

                // Trend arrow
                Text(
                    text = deltaArrow ?: "",
                    style = MaterialTheme.typography.displayLarge,
                    color = getGlucoseColor(glucoseValue)
                )
            }

            // Unit label
            Text(
                "mg/dL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getGlucoseColor(glucose: Int?): Color {
    return when (glucose) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        in 0..69 -> GlucoseColors.Severe
        in 70..79 -> GlucoseColors.Low
        in 80..180 -> GlucoseColors.InRange
        in 181..250 -> GlucoseColors.Elevated
        else -> GlucoseColors.High
    }
}
```

**Visual Specifications:**
- Card width: Fill parent with 16dp horizontal margin
- Card padding: 16dp all sides
- Corner radius: 12dp
- Elevation: 2dp
- Current glucose: displayLarge (64sp, bold)
- Label text: titleMedium (16sp, medium weight)
- Unit text: labelMedium (12sp)
- Color transitions based on clinical ranges

---

#### 2. PumpStatusCard (ENHANCED)

**Purpose:** Show pump hardware status (battery, insulin, connection)

**Compose Structure:**
```kotlin
@Composable
fun PumpStatusCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery
            StatusIndicatorItem(
                icon = { HorizBatteryIcon(batteryPercent.value) },
                label = "Battery",
                value = "${batteryPercent.value ?: "--"}%"
            )

            // Connection time
            StatusIndicatorItem(
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                label = "Updated",
                value = formatLastConnectionTime(lastConnectionTime.value)
            )

            // Insulin
            StatusIndicatorItem(
                icon = { HorizCartridgeIcon(cartridgeUnits.value) },
                label = "Insulin",
                value = "${cartridgeUnits.value ?: "--"}U"
            )
        }
    }
}

@Composable
fun StatusIndicatorItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

**Visual Specifications:**
- Horizontal layout with 3 equal-width items
- Icons: 32x32dp
- Spacing between icon and text: 4dp
- Label: labelMedium (12sp), onSurfaceVariant color
- Value: bodyMedium (14sp, semibold), onSurface color

---

#### 3. CgmChartCard (REDESIGNED with Vico)

**Purpose:** Rich glucose visualization with insulin, carbs, and therapy context

This is the centerpiece component and will be detailed in the next section.

---

#### 4. TherapyMetricsCard (NEW)

**Purpose:** Display active insulin, carbs, and time-in-range

**Compose Structure:**
```kotlin
@Composable
fun TherapyMetricsCard(
    iob: Float?,
    cob: Float?,
    timeInRange: Float?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding)
        ) {
            Text(
                "Therapy Metrics",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Spacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricDisplay(
                    label = "Insulin On Board",
                    value = iob?.let { "%.2f U".format(it) } ?: "--",
                    color = InsulinColors.Bolus
                )

                MetricDisplay(
                    label = "Carbs On Board",
                    value = cob?.let { "%.0f g".format(it) } ?: "--",
                    color = CarbColor
                )

                MetricDisplay(
                    label = "Time in Range",
                    value = timeInRange?.let { "${it.toInt()}%" } ?: "--",
                    color = GlucoseColors.InRange
                )
            }
        }
    }
}

@Composable
fun MetricDisplay(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
```

**Visual Specifications:**
- 3 equal-width columns
- Metric values: headlineMedium (28sp, bold), color-coded
- Labels: labelMedium (12sp), centered, onSurfaceVariant

---

## CGM Chart Design with Vico

### Chart Overview

**Dimensions:**
- Default height: 300dp
- Width: Fill parent (minus card padding)
- Aspect ratio maintained for different screen sizes

**Time Range Options:**
- 3 hours (default)
- 6 hours
- 12 hours
- 24 hours

### Vico Implementation Structure

```kotlin
@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    modifier: Modifier = Modifier
) {
    // Data collection
    val cgmData = rememberCgmChartData(historyLogViewModel, timeRange)
    val insulinData = rememberInsulinData(historyLogViewModel, timeRange)
    val carbData = rememberCarbData(historyLogViewModel, timeRange)
    val modeData = rememberModeData(timeRange)

    // Vico model producer
    val modelProducer = rememberCartesianChartModelProducer()

    LaunchedEffect(cgmData, insulinData, carbData) {
        modelProducer.tryRunTransaction {
            // Build multi-series data
            lineSeries {
                // Main glucose series
                series(cgmData.map { it.timestamp to it.value })

                // Target range lines (optional, can use decorations instead)
                series(cgmData.map { it.timestamp to 180f }) // High target
                series(cgmData.map { it.timestamp to 80f })  // Low target
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Layers
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    // Glucose line with dynamic color
                    rememberLineSpec(
                        lineColor = GlucoseColors.InRange,
                        lineThicknessDp = 2.5f,
                        lineFill = null,  // No fill under line
                        pointProvider = null  // No points visible
                    ),
                    // High target line
                    rememberLineSpec(
                        lineColor = GlucoseColors.Elevated.copy(alpha = 0.5f),
                        lineThicknessDp = 1.5f,
                        lineStyle = DashedLineStyle(intervals = floatArrayOf(8f, 4f))
                    ),
                    // Low target line
                    rememberLineSpec(
                        lineColor = GlucoseColors.Low.copy(alpha = 0.5f),
                        lineThicknessDp = 1.5f,
                        lineStyle = DashedLineStyle(intervals = floatArrayOf(8f, 4f))
                    )
                )
            ),

            // Decorations
            decorations = listOf(
                // Target range background shading
                rememberTargetRangeDecoration(lowTarget = 80f, highTarget = 180f),

                // Therapy mode indicators (Sleep, Exercise)
                rememberModeIndicatorDecoration(modeData),

                // Grid lines
                rememberGridLineDecoration()
            ),

            // Markers (bolus, carbs, etc.)
            persistentMarkers = buildPersistentMarkers(insulinData, carbData),

            // Axes
            startAxis = rememberStartAxis(
                valueFormatter = GlucoseValueFormatter(),
                guideline = rememberAxisGuidelineComponent(
                    color = GridLineColor,
                    thickness = 1.dp
                ),
                label = rememberAxisLabelComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textSize = 12.sp
                )
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = TimeValueFormatter(),
                guideline = rememberAxisGuidelineComponent(
                    color = GridLineColor,
                    thickness = 1.dp
                ),
                label = rememberAxisLabelComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textSize = 12.sp
                )
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}
```

### Vico Layer Details

#### 1. Glucose Line Layer

**Configuration:**
```kotlin
private fun rememberGlucoseLineSpec(): LineCartesianLayer.Line {
    return LineCartesianLayer.Line(
        fill = remember {
            LineCartesianLayer.LineFill.single(
                fill = Fill(Color.Transparent)  // No fill under line
            )
        },
        thickness = 2.5.dp,
        areaFill = null,
        cap = StrokeCap.Round,
        // Dynamic color based on glucose value (requires custom implementation)
        shader = DynamicShaders.fromComponent(
            component = { context ->
                // Return different shaders based on Y value
                // This requires custom shader logic
            }
        )
    )
}
```

**Color Segmentation:**
- Implement custom `LineCartesianLayer` to support multi-colored line segments
- Each segment colored based on glucose range
- Smooth transitions between color zones

#### 2. Target Range Decoration

**Purpose:** Show target glucose range as subtle background shading

```kotlin
@Composable
private fun rememberTargetRangeDecoration(
    lowTarget: Float,
    highTarget: Float
): Decoration {
    return remember(lowTarget, highTarget) {
        object : Decoration {
            override fun draw(
                context: CartesianDrawContext,
                bounds: RectF
            ) {
                // Calculate Y positions for target lines
                val lowY = context.chartValues.getYForValue(lowTarget)
                val highY = context.chartValues.getYForValue(highTarget)

                // Draw shaded rectangle
                context.canvas.drawRect(
                    bounds.left,
                    highY,
                    bounds.right,
                    lowY,
                    Paint().apply {
                        color = TargetRangeColor.toArgb()
                        style = Paint.Style.FILL
                    }
                )
            }
        }
    }
}
```

#### 3. Basal Rate Layer

**Approach:** Use `ColumnCartesianLayer` or custom decoration to show basal rates

```kotlin
@Composable
private fun rememberBasalLayer(
    basalData: List<BasalDataPoint>
): CartesianLayer {
    return rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
            rememberColumnSpec(
                fill = fill(InsulinColors.Basal.copy(alpha = 0.3f)),
                thickness = 4.dp
            )
        ),
        // Position at bottom of chart
        verticalAxisPosition = AxisPosition.Vertical.Start,
        // Stacked behind glucose line
        drawingModelInterpolator = StepInterpolator()
    )
}
```

**Visual:**
- Stepped line or stacked area at bottom 20% of chart
- Blue color with transparency
- Shows basal rate changes over time
- Temp basal changes highlighted in lighter blue

#### 4. Bolus Markers

**Approach:** Use `persistentMarkers` for bolus events

```kotlin
private fun buildBolusMarkers(
    bolusData: List<BolusEvent>
): Map<Float, CartesianMarker> {
    return bolusData.associate { bolus ->
        bolus.timestamp to DefaultCartesianMarker(
            label = rememberTextComponent(
                color = Color.White,
                background = rememberShapeComponent(
                    fill = fill(InsulinColors.Bolus),
                    shape = RoundedCornerShape(4.dp)
                ),
                padding = dimensionsOf(4.dp, 2.dp)
            ).apply {
                text = "${bolus.units}U"
            },
            indicator = rememberShapeComponent(
                fill = fill(
                    if (bolus.isAutomated) InsulinColors.AutoBolus
                    else InsulinColors.Bolus
                ),
                shape = CircleShape,
                strokeColor = Color.White,
                strokeThickness = 2.dp
            ).apply {
                size = 12.dp
            },
            guideline = rememberLineComponent(
                color = InsulinColors.Bolus.copy(alpha = 0.3f),
                thickness = 1.dp,
                style = DashedLineStyle(intervals = floatArrayOf(4f, 4f))
            )
        )
    }
}
```

**Visual:**
- Solid purple circle for manual bolus
- Light purple circle for auto bolus (Control-IQ)
- Dashed vertical line from marker to bottom
- Label showing units above marker
- Circle diameter: 12dp
- Stroke: 2dp white outline

#### 5. Carb Markers

**Similar to bolus markers:**
```kotlin
private fun buildCarbMarkers(
    carbData: List<CarbEvent>
): Map<Float, CartesianMarker> {
    return carbData.associate { carb ->
        carb.timestamp to DefaultCartesianMarker(
            label = rememberTextComponent(
                color = Color.White,
                background = rememberShapeComponent(
                    fill = fill(CarbColor),
                    shape = RoundedCornerShape(4.dp)
                )
            ).apply {
                text = "${carb.grams}g"
            },
            indicator = rememberShapeComponent(
                fill = fill(CarbColor),
                shape = RoundedCornerShape(4.dp)  // Square with rounded corners
            ).apply {
                size = 12.dp
            }
        )
    }
}
```

**Visual:**
- Orange rounded square (4dp corner radius)
- Label showing grams above marker
- No guideline (less visual clutter than bolus)

#### 6. Mode Indicator Decoration

**Purpose:** Show Sleep/Exercise mode periods as colored background bands

```kotlin
@Composable
private fun rememberModeIndicatorDecoration(
    modeData: List<ModeEvent>
): Decoration {
    return remember(modeData) {
        object : Decoration {
            override fun draw(
                context: CartesianDrawContext,
                bounds: RectF
            ) {
                modeData.forEach { mode ->
                    val startX = context.chartValues.getXForValue(mode.startTime)
                    val endX = context.chartValues.getXForValue(mode.endTime)

                    // Draw colored band at top of chart
                    context.canvas.drawRect(
                        startX,
                        bounds.top,
                        endX,
                        bounds.top + 8.dp.toPx(),
                        Paint().apply {
                            color = when (mode.type) {
                                ModeType.SLEEP -> Color(0xFF3F51B5).copy(alpha = 0.3f)
                                ModeType.EXERCISE -> Color(0xFFFF9800).copy(alpha = 0.3f)
                                else -> Color.Transparent
                            }.toArgb()
                            style = Paint.Style.FILL
                        }
                    )

                    // Draw mode label
                    // (text rendering code)
                }
            }
        }
    }
}
```

**Visual:**
- 8dp height colored band at top of chart
- Blue for Sleep mode (30% opacity)
- Orange for Exercise mode (30% opacity)
- Small text label inside band

#### 7. Axis Configuration

**Start Axis (Y-axis - Glucose):**
```kotlin
rememberStartAxis(
    label = rememberAxisLabelComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = 12.sp,
        padding = dimensionsOf(4.dp, 2.dp)
    ),
    axis = rememberAxisLineComponent(
        color = GridLineColor,
        thickness = 1.dp
    ),
    tick = null,  // No tick marks
    guideline = rememberAxisGuidelineComponent(
        color = GridLineColor,
        thickness = 1.dp
    ),
    itemPlacer = remember {
        AxisItemPlacer.Vertical.default(
            maxItemCount = 6,  // e.g., 50, 100, 150, 200, 250, 300
            shiftExtremeTicks = false
        )
    },
    valueFormatter = GlucoseValueFormatter()
)
```

**Bottom Axis (X-axis - Time):**
```kotlin
rememberBottomAxis(
    label = rememberAxisLabelComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = 12.sp,
        padding = dimensionsOf(2.dp, 4.dp)
    ),
    axis = rememberAxisLineComponent(
        color = GridLineColor,
        thickness = 1.dp
    ),
    tick = null,
    guideline = rememberAxisGuidelineComponent(
        color = GridLineColor,
        thickness = 1.dp
    ),
    itemPlacer = remember {
        AxisItemPlacer.Horizontal.default(
            spacing = 1,  // Show every hour for 6hr view
            offset = 0,
            shiftExtremeTicks = false,
            addExtremeLabelPadding = true
        )
    },
    valueFormatter = TimeValueFormatter()
)
```

**Value Formatters:**
```kotlin
class GlucoseValueFormatter : CartesianValueFormatter {
    override fun format(value: Float, chartValues: ChartValues): String {
        return value.toInt().toString()
    }
}

class TimeValueFormatter : CartesianValueFormatter {
    override fun format(value: Float, chartValues: ChartValues): String {
        // Convert timestamp to hour label (e.g., "6am", "12pm", "6pm")
        val instant = Instant.ofEpochMilli(value.toLong())
        val hour = instant.atZone(ZoneId.systemDefault()).hour
        val ampm = if (hour < 12) "am" else "pm"
        val displayHour = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
        return "$displayHour$ampm"
    }
}
```

### Chart Legend

**Purpose:** Explain chart elements (optional, can be toggleable)

```kotlin
@Composable
fun ChartLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Medium),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(
            color = GlucoseColors.InRange,
            label = "Glucose",
            shape = LegendShape.Line
        )
        LegendItem(
            color = InsulinColors.Bolus,
            label = "Bolus",
            shape = LegendShape.Circle
        )
        LegendItem(
            color = CarbColor,
            label = "Carbs",
            shape = LegendShape.Square
        )
        LegendItem(
            color = InsulinColors.Basal,
            label = "Basal",
            shape = LegendShape.Area
        )
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    shape: LegendShape,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (shape) {
            LegendShape.Line -> {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(color)
                )
            }
            LegendShape.Circle -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, CircleShape)
                )
            }
            LegendShape.Square -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
            LegendShape.Area -> {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(8.dp)
                        .background(color.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### Time Range Selector

```kotlin
@Composable
fun ChartTimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeRange.values().forEach { range ->
            FilterChip(
                selected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        range.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (range == selectedRange) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

enum class TimeRange(val label: String, val hours: Int) {
    THREE_HOURS("3h", 3),
    SIX_HOURS("6h", 6),
    TWELVE_HOURS("12h", 12),
    TWENTY_FOUR_HOURS("24h", 24)
}
```

---

## Dashboard Layout Design

### Overall Layout Structure

```kotlin
@Composable
fun Dashboard(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    // State
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var selectedTimeRange by remember { mutableStateOf(TimeRange.SIX_HOURS) }

    // ... (existing refresh logic)

    val state = rememberPullRefreshState(refreshing, ::refresh)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)  // Off-white background
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.CardSpacing),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = Spacing.Small),
            content = {
                // Service/Setup messages
                item {
                    ServiceDisabledMessage(sendMessage = sendMessage)
                    PumpSetupStageProgress(initialSetup = false)
                    PumpSetupStageDescription(initialSetup = false)
                }

                // Glucose Hero Card
                item {
                    val cgmReading = ds.cgmReading.observeAsState()
                    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()

                    GlucoseHeroCard(
                        glucoseValue = cgmReading.value,
                        deltaArrow = cgmDeltaArrow.value
                    )
                }

                // Pump Status Card
                item {
                    PumpStatusCard()
                }

                // CGM Chart Card
                item {
                    CgmChartCard(
                        historyLogViewModel = historyLogViewModel,
                        timeRange = selectedTimeRange,
                        onTimeRangeChanged = { selectedTimeRange = it }
                    )
                }

                // Therapy Metrics Card
                item {
                    val iobUnits = ds.iobUnits.observeAsState()
                    // TODO: Calculate COB and TIR

                    TherapyMetricsCard(
                        iob = iobUnits.value?.toFloatOrNull(),
                        cob = null,  // To be implemented
                        timeInRange = null  // To be implemented
                    )
                }

                // Active Therapy Card
                item {
                    ActiveTherapyCard()
                }

                // Sensor Info Card
                item {
                    SensorInfoCard()
                }

                // Debug info (collapsible in production)
                if (BuildConfig.DEBUG) {
                    item {
                        DebugInfoCard(historyLogViewModel)
                    }
                }

                // Bottom spacing
                item {
                    Spacer(Modifier.height(Spacing.Large))
                }
            }
        )
    }
}
```

### CgmChartCard Wrapper

```kotlin
@Composable
fun CgmChartCard(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange,
    onTimeRangeChanged: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding)
        ) {
            // Header with title and time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Glucose History",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ChartTimeRangeSelector(
                    selectedRange = timeRange,
                    onRangeSelected = onTimeRangeChanged,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            // The chart itself
            VicoCgmChart(
                historyLogViewModel = historyLogViewModel,
                timeRange = timeRange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.Small))

            // Legend
            ChartLegend()
        }
    }
}
```

---

## Jetpack Compose Best Practices

### 1. State Management

**Use appropriate state holders:**
```kotlin
// ViewModel for business logic
class DashboardViewModel(
    private val historyLogViewModel: HistoryLogViewModel,
    private val dataStore: DataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun loadDashboardData() {
        viewModelScope.launch {
            // Load and combine data
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val glucoseReading: Int? = null,
    val deltaArrow: String? = null,
    val iob: Float? = null,
    val cob: Float? = null,
    val batteryPercent: Int? = null,
    // ... other fields
)

// In Composable
@Composable
fun Dashboard(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // Use uiState instead of multiple observeAsState() calls
}
```

**Benefits:**
- Single source of truth
- Easier to test
- Better performance (fewer recompositions)

### 2. Performance Optimization

**Use `remember` for expensive computations:**
```kotlin
@Composable
fun VicoCgmChart(data: List<CgmReading>) {
    val processedData = remember(data) {
        // Expensive data transformation
        data.map { /* ... */ }
    }
}
```

**Use `derivedStateOf` for computed values:**
```kotlin
val glucoseColor by remember {
    derivedStateOf {
        getGlucoseColor(glucoseValue)
    }
}
```

**Use `key()` in LazyColumn for stability:**
```kotlin
LazyColumn {
    items(
        items = dataList,
        key = { item -> item.id }  // Stable key for recomposition
    ) { item ->
        // Item content
    }
}
```

### 3. Modular Components

**Extract reusable components:**
```kotlin
// Instead of inline composition
@Composable
fun MetricCard(label: String, value: String, color: Color) {
    // Reusable component
}

// Use in multiple places
MetricCard("IOB", iobValue, InsulinColors.Bolus)
MetricCard("COB", cobValue, CarbColor)
```

### 4. Preview Support

**Create comprehensive previews:**
```kotlin
@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large Font", showBackground = true, fontScale = 1.5f)
@Composable
fun GlucoseHeroCardPreview() {
    ControlX2Theme {
        GlucoseHeroCard(
            glucoseValue = 142,
            deltaArrow = "↗"
        )
    }
}
```

### 5. Accessibility

**Add content descriptions:**
```kotlin
Icon(
    Icons.Default.Sync,
    contentDescription = "Last updated time"
)

Text(
    glucoseValue.toString(),
    modifier = Modifier.semantics {
        contentDescription = "Current glucose is $glucoseValue milligrams per deciliter"
    }
)
```

**Support dynamic type:**
```kotlin
// Use MaterialTheme.typography instead of hardcoded sizes
Text(
    "Glucose",
    style = MaterialTheme.typography.titleLarge
)
```

### 6. Testing

**Create testable composables:**
```kotlin
// Separate UI from logic
@Composable
fun GlucoseHeroCard(
    uiState: GlucoseHeroUiState,  // Data class
    modifier: Modifier = Modifier
) {
    // Pure UI, easy to test
}

// Test with Paparazzi (already configured in project)
@Test
fun glucoseHeroCard_displaysCorrectValue() {
    paparazzi.snapshot {
        GlucoseHeroCard(
            uiState = GlucoseHeroUiState(
                glucoseValue = 142,
                deltaArrow = "↗"
            )
        )
    }
}
```

---

## Implementation Roadmap

### Phase 1: Foundation (Week 1)

**Goal:** Set up Vico, create base components, establish design system

**Tasks:**
1. ✅ Add Vico dependency to build.gradle
   ```gradle
   implementation("com.patrykandpatrick.vico:compose-m3:2.3.6")
   ```

2. ✅ Update Color.kt with medical color palette
   - Add GlucoseColors object
   - Add InsulinColors object
   - Add CarbColor, TargetRangeColor, etc.

3. ✅ Create spacing and elevation constants
   - Create Spacing.kt
   - Create Elevation.kt

4. ✅ Create base card components
   - GlucoseHeroCard
   - PumpStatusCard (enhance existing)
   - Card wrapper utilities

5. ✅ Set up Vico chart foundation
   - Basic CartesianChartHost setup
   - Configure axes
   - Add grid lines
   - Test with sample data

**Deliverable:** Base UI components with Tandem-inspired styling, basic Vico chart rendering

### Phase 2: Chart Core Features (Week 2)

**Goal:** Implement glucose visualization with target ranges

**Tasks:**
1. ✅ Create data fetching layer
   - `rememberCgmChartData()` composable function
   - Filter by time range
   - Convert HistoryLog to chart data model

2. ✅ Implement glucose line layer
   - LineCartesianLayer with dynamic colors
   - Smooth line rendering
   - Handle missing data points

3. ✅ Add target range decoration
   - Shaded background for 80-180 mg/dL
   - Dashed target lines
   - Make target configurable

4. ✅ Configure axes properly
   - Y-axis: Glucose values (50-300 mg/dL range)
   - X-axis: Time labels (formatted hours)
   - Proper tick spacing

5. ✅ Add time range selector
   - ChartTimeRangeSelector component
   - State management
   - Data reloading on range change

**Deliverable:** Functional CGM chart with glucose line, target ranges, and time selection

### Phase 3: Insulin Visualization (Week 3)

**Goal:** Add insulin delivery visualization (basal and bolus)

**Tasks:**
1. ✅ Query insulin data from HistoryLog
   - Identify bolus history log types
   - Identify basal rate change types
   - Create data models

2. ✅ Implement bolus markers
   - Persistent markers at bolus times
   - Purple circles for manual bolus
   - Light purple for auto bolus
   - Labels with units

3. ✅ Implement basal rate layer
   - Stepped line or area at bottom of chart
   - Blue color scheme
   - Highlight temp basal changes
   - Show rate values on hover/tap

4. ✅ Add IOB calculation
   - Client-side IOB algorithm
   - Update TherapyMetricsCard

5. ✅ Test insulin visualization
   - Verify marker positioning
   - Check color distinction
   - Validate data accuracy

**Deliverable:** Chart shows bolus events and basal rates with proper styling

### Phase 4: Carbs and Therapy Modes (Week 4)

**Goal:** Add carbohydrate tracking and therapy mode indicators

**Tasks:**
1. ✅ Query carb data from HistoryLog
   - Identify carb entry history log types
   - Create CarbEvent model

2. ✅ Implement carb markers
   - Orange rounded squares
   - Labels with gram values
   - Position above glucose line

3. ✅ Add COB calculation
   - Client-side COB algorithm
   - Configurable absorption time
   - Update TherapyMetricsCard

4. ✅ Implement mode indicator decoration
   - Query Sleep/Exercise mode changes
   - Colored bands at top of chart
   - Mode labels

5. ✅ Create TherapyMetricsCard
   - IOB, COB, Time-in-Range display
   - Color-coded metrics
   - Responsive layout

**Deliverable:** Complete chart with carbs, IOB, COB, and therapy modes

### Phase 5: Polish and Optimization (Week 5)

**Goal:** Refine UI, optimize performance, add interactions

**Tasks:**
1. ✅ Add chart legend
   - Toggleable visibility
   - Compact layout
   - Clear labels

2. ✅ Implement tap interactions
   - Show detailed marker info on tap
   - Custom marker tooltips
   - Glucose value at any point

3. ✅ Performance optimization
   - Profile recomposition
   - Optimize data queries
   - Add data caching
   - Implement pagination for long time ranges

4. ✅ Accessibility improvements
   - Screen reader support
   - Content descriptions
   - High contrast mode
   - Dynamic type scaling

5. ✅ Responsive design
   - Landscape orientation support
   - Tablet layout optimizations
   - Foldable device support

**Deliverable:** Production-ready chart with excellent UX and performance

### Phase 6: Additional Cards and Features (Week 6)

**Goal:** Implement remaining dashboard cards and refinements

**Tasks:**
1. ✅ Create ActiveTherapyCard
   - Basal rate display
   - Last bolus info
   - Control-IQ mode status

2. ✅ Create SensorInfoCard
   - Sensor expiration
   - Transmitter battery
   - Session info

3. ✅ Add Time-in-Range calculation
   - Calculate from CGM data
   - Show percentage
   - Color-coded indicator

4. ✅ Implement data refresh animations
   - Smooth loading states
   - Pull-to-refresh feedback
   - Skeleton loading

5. ✅ Final polish
   - Review spacing and alignment
   - Fix edge cases
   - Add error handling
   - Create comprehensive tests

**Deliverable:** Complete dashboard redesign with all features

---

## Technical Specifications

### Dependencies

**Update build.gradle:**
```gradle
dependencies {
    // Remove old charts library
    // implementation("io.github.dautovicharis:charts:2.0.0")

    // Add Vico for charts
    implementation("com.patrykandpatrick.vico:compose:2.3.6")
    implementation("com.patrykandpatrick.vico:compose-m3:2.3.6")
    implementation("com.patrykandpatrick.vico:core:2.3.6")

    // Existing dependencies remain
    implementation "androidx.compose.material3:material3:$material3_version"
    // ... etc
}
```

### Data Models

**Chart Data Models:**
```kotlin
data class CgmDataPoint(
    val timestamp: Long,
    val value: Float,
    val source: CgmSource
)

enum class CgmSource {
    DEXCOM_G6,
    DEXCOM_G7
}

data class BolusEvent(
    val timestamp: Long,
    val units: Float,
    val isAutomated: Boolean,  // Control-IQ auto bolus
    val bolusType: BolusType
)

enum class BolusType {
    STANDARD,
    EXTENDED,
    CORRECTION
}

data class BasalDataPoint(
    val timestamp: Long,
    val rate: Float,  // Units per hour
    val isTemp: Boolean,
    val duration: Int?  // Minutes for temp basal
)

data class CarbEvent(
    val timestamp: Long,
    val grams: Int,
    val carbType: CarbType?
)

enum class CarbType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}

data class ModeEvent(
    val startTime: Long,
    val endTime: Long?,
    val type: ModeType
)

enum class ModeType {
    SLEEP,
    EXERCISE,
    NORMAL
}
```

### State Management

**Chart State:**
```kotlin
data class ChartUiState(
    val cgmData: List<CgmDataPoint> = emptyList(),
    val bolusEvents: List<BolusEvent> = emptyList(),
    val basalData: List<BasalDataPoint> = emptyList(),
    val carbEvents: List<CarbEvent> = emptyList(),
    val modeEvents: List<ModeEvent> = emptyList(),
    val timeRange: TimeRange = TimeRange.SIX_HOURS,
    val targetLow: Float = 80f,
    val targetHigh: Float = 180f,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### Performance Targets

**Chart Rendering:**
- Initial load: < 500ms
- Time range switch: < 200ms
- Smooth scrolling: 60 FPS
- Memory usage: < 50MB for 24hr data

**Data Queries:**
- CGM data fetch: < 100ms
- Insulin data fetch: < 100ms
- Carb data fetch: < 50ms

### Testing Strategy

**Unit Tests:**
```kotlin
class GlucoseColorTest {
    @Test
    fun `glucose below 70 returns severe color`() {
        assertEquals(GlucoseColors.Severe, getGlucoseColor(65))
    }

    @Test
    fun `glucose 80-180 returns in-range color`() {
        assertEquals(GlucoseColors.InRange, getGlucoseColor(120))
    }
}
```

**Snapshot Tests (Paparazzi):**
```kotlin
class DashboardSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun glucoseHeroCard_normal() {
        paparazzi.snapshot {
            GlucoseHeroCard(glucoseValue = 120, deltaArrow = "→")
        }
    }

    @Test
    fun glucoseHeroCard_high() {
        paparazzi.snapshot {
            GlucoseHeroCard(glucoseValue = 250, deltaArrow = "↑")
        }
    }
}
```

**Integration Tests:**
```kotlin
@Test
fun dashboard_displays_all_cards() {
    composeTestRule.setContent {
        Dashboard(/* ... */)
    }

    composeTestRule.onNodeWithText("Current Glucose").assertIsDisplayed()
    composeTestRule.onNodeWithText("Glucose History").assertIsDisplayed()
    composeTestRule.onNodeWithText("Therapy Metrics").assertIsDisplayed()
}
```

---

## Migration Strategy

### Gradual Rollout

**Option 1: Feature Flag**
```kotlin
object FeatureFlags {
    const val USE_NEW_DASHBOARD = true  // Toggle for testing
}

@Composable
fun Dashboard(...) {
    if (FeatureFlags.USE_NEW_DASHBOARD) {
        NewDashboard(...)
    } else {
        OldDashboard(...)
    }
}
```

**Option 2: Parallel Files**
- Keep old Dashboard.kt
- Create DashboardV2.kt
- Switch navigation route when ready
- Remove old file after validation

### Data Migration

**No database changes required** - using existing HistoryLog data

**New calculations needed:**
- IOB (Insulin on Board) - client-side algorithm
- COB (Carbs on Board) - client-side algorithm
- Time in Range - calculate from CGM data

### Rollback Plan

**If issues arise:**
1. Revert navigation to old Dashboard
2. Fix issues in new implementation
3. Re-enable when ready

**Low risk because:**
- No database schema changes
- No API changes
- Self-contained UI changes
- Easy to A/B test

---

## Conclusion

This design plan provides a comprehensive roadmap for transforming the ControlX2 Dashboard into a medical-grade, Tandem-inspired interface using Vico charting library and Jetpack Compose best practices.

**Key Achievements:**
1. ✅ Professional, medical UI aesthetic matching Tandem Mobi
2. ✅ Rich data visualization with Vico's powerful charting
3. ✅ Modular, testable Compose architecture
4. ✅ Excellent performance and accessibility
5. ✅ Clear implementation path with 6-week roadmap

**Next Steps:**
1. Review and approve design plan
2. Begin Phase 1 implementation
3. Iterate based on feedback
4. Launch incrementally with feature flags

---

## References

### Vico Documentation
- [Vico GitHub Repository](https://github.com/patrykandpatrick/vico)
- [Vico Documentation Guide](https://guide.vico.patrykandpatrick.com)
- [Vico Markers Guide](https://patrykandpatrick.com/vico/wiki/cartesian-charts/markers/)

### Material Design 3
- [Material Design 3 Guidelines](https://m3.material.io/)
- [Compose Material 3](https://developer.android.com/jetpack/compose/designsystems/material3)

### Medical UI Best Practices
- [Healthcare UI Design Best Practices](https://www.eleken.co/blog-posts/user-interface-design-for-healthcare-applications)
- [Medical App UI/UX Design](https://fuselabcreative.com/healthcare-app-ui-ux-design-best-practices/)

### Tandem References
- [Tandem Mobi Mobile App](https://apps.apple.com/us/app/tandem-mobi-mobile-app/id6449297027)
- [Tandem Mobile Apps](https://www.tandemdiabetes.com/products/software-apps/mobile-apps)

---

## Implementation Status

**Last Updated:** December 14, 2025
**Branch:** `dev` (consolidated from multiple feature branches)
**Overall Progress:** Phase 3 Complete (95%), Phases 1-2 Complete (100%)

### ✅ Completed Implementation (Phases 1-3)

#### Phase 1: Foundation (100% Complete)
**Branch:** `claude/redesign-cgm-graph-01BfK4mapfPsxsvgN6GLXDd4`
**Commit:** `f36c193`

- ✅ Vico 2.3.6 dependency added to build.gradle
- ✅ Complete color palette in `Color.kt` (GlucoseColors, InsulinColors, UI colors)
- ✅ Spacing system in `Spacing.kt` (4dp to 32dp scale)
- ✅ Elevation system in `Elevation.kt` (Material Design 3 levels)
- ✅ GlucoseHeroCard component with 5 preview states
- ✅ Enhanced PumpStatusCard with card-based layout
- ✅ VicoCgmChart foundation with CartesianChartHost

#### Phase 2: Chart Core Features (100% Complete)
**Branch:** `claude/redesign-cgm-graph-01BfK4mapfPsxsvgN6GLXDd4`
**Commit:** `b5761e5`

- ✅ `rememberCgmChartData()` composable data fetching
- ✅ CgmDataPoint data model (timestamp, value)
- ✅ TimeRange enum (3h, 6h, 12h, 24h)
- ✅ GlucoseValueFormatter for Y-axis
- ✅ TimeValueFormatter for X-axis (12-hour format)
- ✅ Target range decorations (80-180 mg/dL with dashed lines)
- ✅ Glucose line styling (2.5dp thickness, blue color)
- ✅ Grid lines with proper transparency
- ✅ ChartTimeRangeSelector with Material 3 FilterChips
- ✅ VicoCgmChartCard wrapper component
- ✅ Zoom disabled for medical safety

#### Phase 3: Insulin Visualization (95% Complete)
**Branch:** `claude/phase-3-insulin-data-layer-01Xj4H5B8Y9q37xERVcNQzdK` → merged to `dev`

**Data Layer (100%):**
- ✅ BolusEvent data model (timestamp, units, isAutomated, bolusType)
- ✅ BasalDataPoint data model (timestamp, rate, isTemp, duration)
- ✅ `rememberBolusData()` with reflection-based field extraction
- ✅ `rememberBasalData()` with dynamic class loading
- ✅ Support for multiple basal log types (BasalRateChange, TempRateActivated)
- ✅ Automated bolus detection via bolusSource field
- ✅ Unit conversion (milli-units to units)

**Visualization (95%):**
- ✅ Bolus markers implemented as persistent markers
- ✅ Purple circles for manual bolus (#5E35B1)
- ✅ Light purple circles for auto bolus (#7E57C2)
- ✅ 12dp diameter with 2dp white stroke
- ✅ Unit labels above markers (e.g., "5.2U", "1.5U")
- ✅ Smart positioning to nearest valid data point
- ✅ Basal rate dual series (scheduled vs temp)
- ✅ Normalized basal display (bottom 20% of chart = 60 mg/dL range)
- ✅ Color distinction (dark blue #1565C0 for scheduled, light blue #42A5F5 for temp)
- ✅ NaN-based gap handling for discontinuous basal periods

**Chart Infrastructure (100%):**
- ✅ Data bucketing (5-minute intervals)
- ✅ Segmented CGM series to prevent lines across gaps
- ✅ NaN-safe data handling throughout
- ✅ Custom CartesianLayerRangeProvider for fixed Y-axis (30-410 mg/dL)
- ✅ Drag marker with custom value formatter
- ✅ Time-based X-axis labels (3 labels: start, middle, end)
- ✅ Comprehensive preview data generators

**Remaining (5%):**
- ⏳ Overlapping marker handling refinement
- ⏳ Performance testing with large datasets (24h+ data)
- ⏳ Accessibility testing for bolus markers
- ⏳ Optional: Marker guideline styling

### 🔧 Critical Technical Implementation Details

#### Vico Fork for NaN Handling

**Important:** This implementation uses a **forked version of Vico** (`https://github.com/jwoglom/vico`) to fix a critical NaN handling bug in the upstream library.

**Issue:** Upstream Vico 2.3.6 crashes when `LineCartesianLayer.updateMarkerTargets` attempts to round NaN values during marker position calculations. This occurs when CGM data has gaps (missing readings).

**Fix:** The fork adds null-safety checks before rounding y-values in `updateMarkerTargets`:
```kotlin
// Before (crashes on NaN):
val roundedY = y.roundToInt()

// After (safe):
if (!y.isNaN()) {
    val roundedY = y.roundToInt()
    // ... continue
}
```

**Impact:** Enables robust handling of real-world CGM data with gaps, common in medical devices due to sensor signal loss, compression lows, calibration periods, etc.

#### Data Fetching with Reflection

Due to pumpx2 library version variations, all insulin data fetching uses **reflection-based field extraction**:

```kotlin
private inline fun <reified T> tryGetField(clazz: Class<*>, obj: Any, fieldName: String): T? {
    return try {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        val value = field.get(obj)
        if (value is T) value else null
    } catch (e: Exception) {
        null
    }
}
```

**Benefits:**
- Gracefully handles missing fields across pumpx2 versions
- No runtime crashes from schema changes
- Returns null for missing data (handled safely downstream)

**Usage:**
- `totalVolumeDelivered` → insulin units (÷100 for precision)
- `bolusSource` → automated detection ("CLOSED_LOOP_AUTO_BOLUS")
- `basalRate` / `rate` → units per hour (÷1000 from milli-units)
- `isTemp` / `basalType` → temporary basal detection

#### Segmented Series for Gap Handling

CGM data is split into **segments** to prevent Vico from drawing lines across data gaps > 5 minutes:

```kotlin
val cgmSegments = remember(chartBuckets) {
    val segments = mutableListOf<List<Double>>()
    var currentSegmentStart: Int? = null

    chartBuckets.forEachIndexed { index, bucket ->
        if (bucket.value != null) {
            if (currentSegmentStart == null) {
                currentSegmentStart = index
            }
        } else {
            // Gap detected - close current segment
            if (currentSegmentStart != null) {
                val fullSeries = MutableList(chartBuckets.size) { Double.NaN }
                (currentSegmentStart until index).forEach { idx ->
                    fullSeries[idx] = chartBuckets[idx].value ?: Double.NaN
                }
                segments.add(fullSeries)
                currentSegmentStart = null
            }
        }
    }
    segments
}
```

**Why:** Each segment is a full-length series with `NaN` values outside the segment range. This prevents visual artifacts from interpolation across sensor dropouts.

#### Basal Rate Visualization Strategy

Basal rates use **dual series** approach for scheduled vs temporary basals:

```kotlin
private data class BasalSeriesResult(
    val scheduled: List<Double>,  // NaN when temp is active
    val temp: List<Double>         // NaN when scheduled is active
)

bucketTimes.forEach { bucketTime ->
    val relevantBasal = basalDataPoints
        .filter { it.timestamp <= bucketTime }
        .maxByOrNull { it.timestamp }

    if (relevantBasal != null) {
        val normalized = (relevantBasal.rate / basalMaxRate) * BASAL_DISPLAY_RANGE
        if (relevantBasal.isTemp) {
            scheduled.add(Double.NaN)
            temp.add(normalized)
        } else {
            scheduled.add(normalized)
            temp.add(Double.NaN)
        }
    }
}
```

**Normalization:** Basal rates scaled to bottom 60 mg/dL of chart (e.g., 30-90 mg/dL range) to avoid obscuring glucose data.

**Color Distinction:** Dark blue (#1565C0) for scheduled, light blue (#42A5F5) for temp basals.

#### Bolus Marker Positioning

Bolus markers use **bucket-based positioning** with fallback to nearest valid data:

```kotlin
val bolusMarkerPoints = remember(bolusEvents, bucketTimes, chartBuckets) {
    bolusEvents.mapNotNull { bolus ->
        // Find bucket index for bolus timestamp
        val bucketIndex = bucketTimes.indexOfFirst { it >= bolus.timestamp }
            .takeIf { it >= 0 } ?: bucketTimes.size - 1

        if (chartBuckets[bucketIndex].value == null) {
            // Skip markers at gaps - find nearest valid point
            val nearestValidIndex = chartBuckets
                .mapIndexedNotNull { idx, bucket -> if (bucket.value != null) idx else null }
                .minByOrNull { kotlin.math.abs(it - bucketIndex) }

            if (nearestValidIndex != null) {
                BolusMarkerPoint(nearestValidIndex.toFloat(), bolus)
            } else null
        } else {
            BolusMarkerPoint(bucketIndex.toFloat(), bolus)
        }
    }
}
```

**Rationale:** Ensures markers always appear at valid chart positions, even if bolus occurred during CGM gap.

### 📋 Updated Implementation Roadmap

#### ✅ Phase 1: Foundation (COMPLETE)
- All tasks completed
- Design system fully established
- Base components created and tested

#### ✅ Phase 2: Chart Core Features (COMPLETE)
- All tasks completed
- Glucose visualization working
- Target ranges implemented
- Time range selection functional

#### ✅ Phase 3: Insulin Visualization (95% COMPLETE)
- All core tasks completed
- Bolus markers rendered and styled
- Basal rates visualized with dual series
- Data fetching robust with reflection
- **Remaining:** Fine-tuning for overlapping markers, performance testing

#### ⏳ Phase 4: Carbs and Therapy Modes (NOT STARTED)
**Estimated Effort:** 8-10 hours

**Tasks:**
1. Create CarbEvent data model
2. Implement `rememberCarbData()` composable
3. Add carb markers (orange rounded squares)
4. Calculate COB (Carbs on Board) algorithm
5. Query Sleep/Exercise mode changes
6. Implement mode indicator decoration (colored bands)
7. Create TherapyMetricsCard component
8. Update Dashboard layout to include new card

#### ⏳ Phase 5: Polish and Optimization (NOT STARTED)
**Estimated Effort:** 6-8 hours

**Tasks:**
1. Add toggleable chart legend
2. Enhance tap interactions and tooltips
3. Profile and optimize recomposition
4. Implement data caching for frequently accessed ranges
5. Add accessibility improvements (content descriptions, high contrast)
6. Responsive design testing (landscape, tablets, foldables)
7. Create comprehensive snapshot tests

#### ⏳ Phase 6: Additional Dashboard Cards (NOT STARTED)
**Estimated Effort:** 8-10 hours

**Tasks:**
1. Create ActiveTherapyCard (basal, last bolus, Control-IQ mode)
2. Create SensorInfoCard (sensor expiration, transmitter battery)
3. Calculate Time-in-Range (TIR) from CGM data
4. Implement refresh animations and loading states
5. Add skeleton loading for cards
6. Comprehensive error handling
7. Final polish and edge case fixes

### 🎯 Next Steps

**Immediate (Complete Phase 3):**
1. Test with real pump data across all time ranges
2. Validate marker positioning with overlapping boluses
3. Performance test with 24-hour datasets
4. Document any edge cases discovered

**Short-term (Phase 4):**
1. Research COB calculation algorithm
2. Identify carb entry HistoryLog types
3. Design therapy mode indicator visuals
4. Implement TherapyMetricsCard

**Long-term (Phases 5-6):**
1. Accessibility audit
2. Performance optimization
3. Complete dashboard reorganization
4. Production release preparation

---

**Document Version:** 2.0
**Last Updated:** December 14, 2025
**Author:** Claude (Anthropic AI)
**Status:** Implementation In Progress (Phase 3: 95% Complete)
