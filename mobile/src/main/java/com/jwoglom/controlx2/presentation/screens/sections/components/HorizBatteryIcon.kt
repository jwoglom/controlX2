package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
            Text(
                "${batteryPercent}%",
                color = color,
                textAlign = TextAlign.Center,
                modifier = modifier.height(height),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun getPainterForBatteryPercent(batteryPercent: Int): Painter {
    return painterResource(when {
        batteryPercent >= 80 -> R.drawable.battery_horiz_100
        batteryPercent >= 60 -> R.drawable.battery_horiz_075
        batteryPercent >= 40 -> R.drawable.battery_horiz_050
        batteryPercent >= 25 -> R.drawable.battery_horiz_025
        batteryPercent >= 10 -> R.drawable.battery_horiz_010
        else -> R.drawable.battery_horiz_000
    })
}