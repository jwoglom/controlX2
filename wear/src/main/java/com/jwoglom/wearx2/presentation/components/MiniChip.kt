package com.jwoglom.wearx2.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text


@Composable
internal fun MiniChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        contentPadding = PaddingValues(
            top = 2.dp, bottom = 2.dp,
            start = 10.dp, end = 10.dp
        ),
        modifier = modifier,
    )
}