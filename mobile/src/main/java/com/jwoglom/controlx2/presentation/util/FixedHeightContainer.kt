package com.jwoglom.controlx2.presentation.util

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp

@Composable
fun FixedHeightContainer(
    height: Dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = Modifier.height(height)
    ) { measurables, constraints ->
        // Force child to measure using the parent's maxHeight,
        // effectively overriding wrapContentHeight.
        val fixedHeightConstraints = constraints.copy(
            minHeight = constraints.maxHeight,
            maxHeight = constraints.maxHeight
        )

        val placeable = measurables.first().measure(fixedHeightConstraints)

        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
