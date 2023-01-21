package com.jwoglom.controlx2.presentation.components

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
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.OutlinedChip
import androidx.wear.compose.material.PlaceholderDefaults
import androidx.wear.compose.material.PlaceholderState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.placeholder
import androidx.wear.compose.material.placeholderShimmer

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
internal fun LineInfoChip(
    labelText: String,
    secondaryLabelText: String = "",
    onClick: () -> Unit = {},
    fontSize: TextUnit = TextUnit.Unspecified,
    placeholderState: PlaceholderState? = null,
) {
    OutlinedChip(
        onClick = onClick,
        label = {
            Box(
                modifier = (placeholderState?.let {
                    Modifier.placeholder(placeholderState)
                } ?: Modifier).fillMaxSize()
            ) {
                Text(
                    labelText,
                    maxLines = 1,
                    fontSize = fontSize,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    secondaryLabelText,
                    maxLines = 1,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        },
        contentPadding = PaddingValues(
            top = 2.dp, bottom = 2.dp,
            start = 10.dp, end = 10.dp
        ),
        colors = placeholderState?.let {
            PlaceholderDefaults.placeholderChipColors(
                placeholderState = placeholderState,
                originalChipColors = ChipDefaults.outlinedChipColors(),
            )
        } ?: ChipDefaults.outlinedChipColors(),
        modifier = (placeholderState?.let {
            Modifier.placeholderShimmer(placeholderState)
        } ?: Modifier)
            .fillMaxWidth()
            .height(32.dp)
    )
}