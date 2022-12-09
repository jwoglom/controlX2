package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.Text
import com.jwoglom.wearx2.presentation.defaultTheme


@Composable
internal fun LineTextDescription(
    labelText: String,
    secondaryLabelText: String = "",
    onClick: () -> Unit = {},
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    Chip(
        onClick = onClick,
        label = {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    labelText,
                    fontSize = fontSize,
                    modifier = Modifier.align(Alignment.CenterStart),
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
            contentColor = defaultTheme.colors.primary,
        ),
        contentPadding = PaddingValues(
            top = 2.dp, bottom = 2.dp,
            start = 10.dp, end = 10.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    )
}