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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.R

/**
 * @param batteryPercent from 0 to 100
 */
@Composable
fun HorizBatteryIcon(
    batteryPercent: Int?,
    height: Dp = 24.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    batteryPercent?.let {
        Row(modifier.height(height)) {
            Icon(
                getPainterForBatteryPercent(it),
                "Battery icon",
                tint = color,
                modifier = modifier.height(height)
            )
            // HACK: text should be aligned with the icon as-is...
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(height - 2.dp)
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
private fun Preview0() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(0)
    }
}

@Preview
@Composable
private fun Preview20() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(20)
    }
}

@Preview
@Composable
private fun Preview30() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(30)
    }
}

@Preview
@Composable
private fun Preview40() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(40)
    }
}

@Preview
@Composable
private fun Preview60() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(60)
    }
}

@Preview
@Composable
private fun Preview80() {
    Surface(
        color = Color.White,
    ) {
        HorizBatteryIcon(80)
    }
}