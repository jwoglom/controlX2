package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import com.jwoglom.controlx2.dataStore

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BolusUnitsChip(
    onClickUnits: (Double?) -> Unit,
    currentUnits: Double?,
) {
    val unitsText = dataStore.bolusUnitsDisplayedText.observeAsState().value
    val unitsSubtitle = dataStore.bolusUnitsDisplayedSubtitle.observeAsState().value

    Box(modifier = Modifier.padding(top = 24.dp)) {
        Chip(
            onClick = { onClickUnits(currentUnits) },
            label = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "$unitsText",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                    )
                }
            },
            secondaryLabel = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = unitsSubtitle ?: "Units",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                    )
                }
            },
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(fraction = 0.5f).height(40.dp)
        )
    }
}

@Preview
@Composable
private fun BolusUnitsChipPreview() {
    BolusUnitsChip(onClickUnits = {}, currentUnits = 0.0)
}
