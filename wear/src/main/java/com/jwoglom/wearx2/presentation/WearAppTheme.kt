package com.jwoglom.wearx2.presentation

/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography


internal data class ThemeValues(val description: String, val colors: Colors)
internal val blueTheme = ThemeValues("Blue (Default AECBFA)", Colors())
internal val darkBlueTheme = ThemeValues(
    "Blue 2 (7FCFFF)",
    Colors(
        primary = Color(0xFF7FCFFF),
        primaryVariant = Color(0xFF3998D3),
        secondary = Color(0xFF6DD58C),
        secondaryVariant = Color(0xFF1EA446)
    )
)
internal val greenTheme = ThemeValues(
    "Green (6DD58C)",
    Colors(
        primary = Color(0xFF6DD58C),
        primaryVariant = Color(0xFF1EA446),
        secondary = Color(0xFFFFBB29),
        secondaryVariant = Color(0xFFD68400)
    )
)
internal val redTheme = ThemeValues(
    "Red (D56D6D)",
    Colors(
        primary = Color(0xFFD56D6D),
        primaryVariant = Color(0xFFA41E1E),
        secondary = Color(0xFFFFBB29),
        secondaryVariant = Color(0xFFD68400)
    )
)
internal val purpleTheme = ThemeValues(
    "Lilac (D0BCFF)",
    Colors(
        primary = Color(0xFFD0BCFF),
        primaryVariant = Color(0xFF9A82DB),
        secondary = Color(0xFF7FCFFF),
        secondaryVariant = Color(0xFF3998D3)
    )
)

internal val defaultTheme = blueTheme

val WearTypography = Typography(
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)

@Composable
fun WearAppTheme(
    colors: Colors = defaultTheme.colors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = colors,
        typography = WearTypography,
        // For shapes, we generally recommend using the default Material Wear shapes which are
        // optimized for round and non-round devices.
        content = content
    )
}
