package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.foundation.radialGradientBackground
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.redTheme

@Composable
fun BottomText() {
    val text = dataStore.connectionStatus.observeAsState()
    val primaryColor = redTheme.colors.primary
    // Places curved text at the bottom of round devices and straight text at the bottom of
    // non-round devices.
    if (LocalConfiguration.current.isScreenRound) {
        CurvedLayout(
            anchor = 90F,
            anchorType = AnchorType.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            curvedRow {
                curvedText(
                    text = "${text.value}",
                    angularDirection = CurvedDirection.Angular.CounterClockwise,
                    style = CurvedTextStyle(
                        fontSize = 16.sp,
                        color = primaryColor
                    ),
                    modifier = CurvedModifier
                        .radialGradientBackground(
                            0f to Color.Transparent,
                            0.2f to Color.DarkGray.copy(alpha = 0.2f),
                            0.6f to Color.DarkGray.copy(alpha = 0.2f),
                            0.7f to Color.DarkGray.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ).padding(
                            all = when (text.value) {
                                null, "" -> 0.dp
                                else -> 2.dp
                            }
                        )
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.DarkGray.copy(alpha = 0.05f),
                            0.4f to Color.DarkGray.copy(alpha = 0.2f),
                            0.8f to Color.DarkGray.copy(alpha = 0.2f),
                            1f to Color.Transparent,
                        )
                    ).padding(
                        all = when (text.value) {
                            null, "" -> 0.dp
                            else -> 2.dp
                        }
                    ),
                textAlign = TextAlign.Center,
                color = primaryColor,
                text = "${text.value}",
                fontSize = 16.sp
            )
        }
    }
}