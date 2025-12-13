package com.jwoglom.controlx2.presentation.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val Colors = lightColorScheme(
    primary = Color(0xFFAECBFA),
    secondary = Color(0xFFFDE293),
    surface = Color(0xFF303133),
    error = Color(0xFFEE675C),
    onPrimary = Color(0xFF303133),
    onSecondary = Color(0xFF303133),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFDADCE0),
    onError = Color(0xFF000000)
)

// Glucose Status Colors
object GlucoseColors {
    val Severe = Color(0xFFC62828)        // Deep Red (< 70 mg/dL)
    val Low = Color(0xFFFF5252)           // Red Accent (70-79 mg/dL)
    val InRange = Color(0xFF1976D2)       // Blue (80-180 mg/dL)
    val Elevated = Color(0xFFFF9800)      // Orange (181-250 mg/dL)
    val High = Color(0xFFE65100)          // Deep Orange (> 250 mg/dL)
}

// Insulin Colors
object InsulinColors {
    val Bolus = Color(0xFF5E35B1)         // Purple
    val AutoBolus = Color(0xFF7E57C2)     // Light Purple
    val Basal = Color(0xFF1565C0)         // Dark Blue
    val TempBasal = Color(0xFF42A5F5)     // Light Blue
}

// UI Elements & Other Colors
val CarbColor = Color(0xFFFF8F00)         // Orange
val TargetRangeColor = Color(0xFFE0F2F1) // Background (Teal)
val GridLineColor = Color(0xFFEEEEEE)     // Light Gray
val SurfaceBackground = Color(0xFFFAFAFA) // Background (Off-white)
val CardBackground = Color(0xFFFFFFFF)    // Card Background (White)