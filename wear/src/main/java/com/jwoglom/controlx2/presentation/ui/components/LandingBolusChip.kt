package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import com.jwoglom.controlx2.LocalDataStore

@Composable
fun LandingBolusChip(onClick: () -> Unit) {
    val ds = LocalDataStore.current
    val lastBolusStatus = ds.lastBolusStatus.observeAsState().value
    Chip(
        onClick = onClick,
        label = { Text("Bolus", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(lastBolusStatus?.let { "Last: $it" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
private fun LandingBolusChipPreview() {
    LandingBolusChip(onClick = {})
}
