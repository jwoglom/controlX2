package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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

const val CartridgeMax = 200

@Composable
fun HorizCartridgeIcon(
    cartridgeAmount: Int?,
    cartridgeMax: Int = CartridgeMax,
    height: Dp = 24.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    cartridgeAmount?.let {
        val percent = (100 * it) / cartridgeMax
        Row(modifier.height(height)) {
            // HACK: text should be aligned with the icon as-is...
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(height - 2.dp)
            ) {
                Text(
                    "${cartridgeAmount}u",
                    color = color,
                    textAlign = TextAlign.Center
                )
            }
            Box {
                Icon(
                    getPainterForCartridgePercent(percent),
                    "Pump cartridge icon",
                    tint = color,
                    modifier = modifier.height(height)
                )
                if (it == 0) {
                    Icon(
                        painterResource(R.drawable.cartridge_horiz_x),
                        "Pump cartridge empty",
                        tint = Color.Red,
                        modifier = modifier.height(height)
                    )
                }
            }
        }
    }
}

@Composable
fun getPainterForCartridgePercent(percent: Int): Painter {
    return painterResource(
        when {
            percent >= 80 -> R.drawable.cartridge_horiz_100
            percent >= 60 -> R.drawable.cartridge_horiz_075
            percent >= 40 -> R.drawable.cartridge_horiz_050
            percent >= 25 -> R.drawable.cartridge_horiz_025
            percent >= 10 -> R.drawable.cartridge_horiz_010
            else -> R.drawable.cartridge_horiz_000
        }
    )
}

@Preview
@Composable
private fun Preview0() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon(0)
    }
}

@Preview
@Composable
private fun Preview20() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon((0.20 * CartridgeMax).toInt())
    }
}

@Preview
@Composable
private fun Preview30() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon((0.30 * CartridgeMax).toInt())
    }
}

@Preview
@Composable
private fun Preview40() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon((0.40 * CartridgeMax).toInt())
    }
}

@Preview
@Composable
private fun Preview60() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon((0.60 * CartridgeMax).toInt())
    }
}

@Preview
@Composable
private fun Preview80() {
    Surface(
        color = Color.White,
    ) {
        HorizCartridgeIcon((0.80 * CartridgeMax).toInt())
    }
}