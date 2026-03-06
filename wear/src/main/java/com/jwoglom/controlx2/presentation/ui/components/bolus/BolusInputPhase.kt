package com.jwoglom.controlx2.presentation.ui.components.bolus

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.components.LineTextDescription
import com.jwoglom.controlx2.presentation.defaultTheme
import com.jwoglom.controlx2.presentation.ui.components.BolusCarbsAndBgRow
import com.jwoglom.controlx2.presentation.ui.components.BolusUnitsChip
import com.jwoglom.controlx2.shared.util.firstLetterCapitalized
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import timber.log.Timber

@Composable
fun BolusInputPhase(
    modifier: Modifier,
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    dataStore: DataStore,
    bolusCarbsGramsUserInput: Int?,
    unitAbbrev: String,
    onClickUnits: (Double?) -> Unit,
    onClickCarbs: () -> Unit,
    onClickBG: () -> Unit,
    onContinue: () -> Unit,
    onPromptAcknowledgedClick: (List<BolusCalcCondition>) -> Unit,
) {
    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()
    val bolusConditionsPromptAcknowledged = dataStore.bolusConditionsPromptAcknowledged.observeAsState()
    val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()
    val bolusCurrentConditions = dataStore.bolusCurrentConditions.observeAsState()

    ScalingLazyColumn(
        modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams()
    ) {
        item {
            BolusUnitsChip(
                onClickUnits = onClickUnits,
                currentUnits = bolusCurrentParameters.value?.units,
            )
        }

        item {
            BolusCarbsAndBgRow(
                carbsGrams = bolusCarbsGramsUserInput,
                unitAbbrev = unitAbbrev,
                onClickCarbs = onClickCarbs,
                onClickBg = onClickBG,
            )
        }

        item {
            if (!bolusConditionsPromptAcknowledged.value.isNullOrEmpty()) {
                bolusConditionsPromptAcknowledged.value?.forEach {
                    LineTextDescription(
                        when {
                            bolusConditionsExcluded.value?.contains(it) == true -> "${it.prompt?.whenIgnoredNotice}"
                            else -> "${it.prompt?.whenAcceptedNotice}"
                        },
                        textColor = when {
                            bolusConditionsExcluded.value?.contains(it) == true -> Color.Red
                            else -> defaultTheme.colors.primary
                        },
                        fontSize = 12.sp,
                        align = Alignment.Center,
                        height = 28.dp,
                        onClick = {
                            Timber.i("bolusConditionsPromptAcknowledged click")
                            onPromptAcknowledgedClick(bolusConditionsPromptAcknowledged.value!!)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .height(0.dp)
                        .fillMaxWidth()
                )
            }
        }

        item {
            Button(onClick = onContinue, enabled = true) {
                Row {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "continue",
                        modifier = Modifier
                            .size(ButtonDefaults.SmallIconSize)
                            .wrapContentSize(align = Alignment.Center),
                    )
                }
            }
        }

        items(5) { index ->
            if (bolusCurrentConditions.value == null) {
                Spacer(modifier = Modifier.height(0.dp))
            } else if (index < (bolusCurrentConditions.value?.size ?: 0)) {
                LineTextDescription(
                    labelText = firstLetterCapitalized(bolusCurrentConditions.value?.get(index)?.msg ?: ""),
                    fontSize = 12.sp,
                )
            } else {
                Spacer(modifier = Modifier.height(0.dp))
            }
        }
    }
}
