package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.R

/**
 * @param batteryPercent from 0 to 100
 * @param batteryCharging if charging, shows lightning bolt
 */
@Composable
fun HorizBatteryIcon(
    batteryPercent: Int?,
    batteryCharging: Boolean? = null,
    height: Dp = 24.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    batteryPercent?.let {
        Row(modifier.height(height)) {
            Box(
                modifier = Modifier.height(height),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getPainterForBatteryPercent(it),
                    "Battery icon",
                    tint = color,
                    modifier = modifier.height(height)
                )
                if (batteryCharging == true) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(height)
                    ) {
                        Text(
                            when (batteryCharging) {
                                true -> " ⚡"
                                else -> ""
                            },
                            color = color,
                            textAlign = TextAlign.Center,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            // HACK: text should be aligned with the icon as-is...
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(height - 0.dp)
            ) {
                Text(
                    "${batteryPercent}%",
                    color = color,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun getPainterForBatteryPercent(batteryPercent: Int): Painter {
    return painterResource(
        when {
            batteryPercent >= 80 -> R.drawable.battery_horiz_100
            batteryPercent >= 60 -> R.drawable.battery_horiz_075
            batteryPercent >= 40 -> R.drawable.battery_horiz_050
            batteryPercent >= 25 -> R.drawable.battery_horiz_025
            batteryPercent >= 10 -> R.drawable.battery_horiz_010
            else -> R.drawable.battery_horiz_000
        }
    )
}

@Preview
@Composable
internal fun HorizBatteryIconPreview0() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(0, false)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview20() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(20, false)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview30() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(30, false)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview40() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(40, false)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview60() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(60, false)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview80() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(80, false)
    }
}


@Preview
@Composable
internal fun HorizBatteryIconPreview0Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(0, true)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview20Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(20, true)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview30Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(30, true)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview40Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(40, true)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview60Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(60, true)
    }
}

@Preview
@Composable
internal fun HorizBatteryIconPreview80Charging() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(80, true)
    }
}