package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.presentation.defaultTheme


@Composable
internal fun LineTextDescription(
    labelText: String,
    secondaryLabelText: String = "",
    onClick: () -> Unit = {},
    fontSize: TextUnit = TextUnit.Unspecified,
    textColor: Color = defaultTheme.colors.primary,
    height: Dp = 40.dp,
    align: Alignment = Alignment.CenterStart,
    bottomPadding: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Chip(
        onClick = onClick,
        label = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    labelText,
                    fontSize = fontSize,
                    modifier = Modifier.align(align),
                )
                Text(
                    secondaryLabelText,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = defaultTheme.colors.background,
            contentColor = textColor,
        ),
        contentPadding = PaddingValues(
            top = 2.dp, bottom = bottomPadding,
            start = 10.dp, end = 10.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    )
}