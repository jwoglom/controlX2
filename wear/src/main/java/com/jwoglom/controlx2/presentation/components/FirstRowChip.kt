package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.presentation.ThemeValues
import com.jwoglom.controlx2.presentation.defaultTheme

@Composable
internal fun FirstRowChip(
    labelText: String,
    numItems: Int,
    secondaryLabelText: String = "",
    onClick: () -> Unit = {},
    theme: ThemeValues = defaultTheme,
) {
    Chip(
        onClick = onClick,
        label = {
            Column(
                Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    labelText,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                )
            }
        },
        secondaryLabel = {
            Column(
                Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    secondaryLabelText,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    style = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                )
            }
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = theme.colors.background,
            contentColor = theme.colors.primary,
        ),
        shape = RoundedCornerShape(0),
        contentPadding = PaddingValues(
            start = 0.dp, end = 0.dp,
            top = 15.dp, bottom = 0.dp
        ),
        modifier = when (numItems) {
            2 -> Modifier.width(75.dp)
            3 -> Modifier.width(50.dp)
            else -> Modifier.fillMaxWidth()
        }.wrapContentHeight()
    )
}