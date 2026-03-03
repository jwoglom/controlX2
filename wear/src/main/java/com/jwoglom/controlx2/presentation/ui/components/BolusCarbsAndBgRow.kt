package com.jwoglom.controlx2.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.jwoglom.controlx2.dataStore

@Immutable
data class BolusInputRowModel(
    val carbsGrams: Int?,
    val bgText: String?,
    val bgSubtitle: String?,
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BolusCarbsAndBgRow(
    carbsGrams: Int?,
    unitAbbrev: String,
    onClickCarbs: () -> Unit,
    onClickBg: () -> Unit,
) {
    val model = BolusInputRowModel(
        carbsGrams = carbsGrams,
        bgText = dataStore.bolusBGDisplayedText.observeAsState().value,
        bgSubtitle = dataStore.bolusBGDisplayedSubtitle.observeAsState().value,
    )

    Row(modifier = Modifier.fillMaxWidth().padding(PaddingValues(top = 4.dp))) {
        Chip(
            onClick = onClickCarbs,
            label = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = model.carbsGrams?.toString() ?: "0",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 34.sp,
                    )
                }
            },
            secondaryLabel = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Carbs (grams)", maxLines = 1, textAlign = TextAlign.Center, fontSize = 10.sp)
                }
            },
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(PaddingValues(end = 4.dp)).height(60.dp).weight(1f)
        )

        Chip(
            onClick = onClickBg,
            label = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = model.bgText ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontSize = 34.sp,
                    )
                }
            },
            secondaryLabel = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = model.bgSubtitle ?: "BG ($unitAbbrev)", maxLines = 1, textAlign = TextAlign.Center, fontSize = 10.sp)
                }
            },
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(PaddingValues(start = 4.dp)).height(60.dp).weight(1f)
        )
    }
}

@Preview
@Composable
private fun BolusCarbsAndBgRowPreview() {
    BolusCarbsAndBgRow(carbsGrams = 20, unitAbbrev = "mg/dL", onClickCarbs = {}, onClickBg = {})
}
