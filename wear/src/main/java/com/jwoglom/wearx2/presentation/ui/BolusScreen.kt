package com.jwoglom.wearx2.presentation.ui

/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.rememberScalingLazyListState
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculator
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.components.LineInfoChip
import com.jwoglom.wearx2.util.twoDecimalPlaces
import timber.log.Timber


@Composable
fun BolusScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?,
    onClickUnits: () -> Unit,
    onClickCarbs: () -> Unit,
    onClickBG: () -> Unit,
    sendPumpCommand: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }

    val dataStore = LocalDataStore.current

    var bolusCalculatorBuilder: BolusCalculatorBuilder? = null
    var bolusFinalParameters: BolusParameters? = null
    var bolusFinalConditions: List<BolusCalcCondition>? = null
    fun runBolusCalculator(dataSnapshot: BolusCalcDataSnapshotResponse?, lastBG: LastBGResponse?, bolusUnitsUserInput: Double?, bolusCarbsGramsUserInput: Int?, bolusBgMgdlUserInput: Int?): BolusCalculatorBuilder {
        Timber.i("runBolusCalculator: INPUT units=$bolusUnitsUserInput carbs=$bolusCarbsGramsUserInput bg=$bolusBgMgdlUserInput dataSnapshot=$dataSnapshot lastBG=$lastBG")
        val bolusCalc = BolusCalculatorBuilder()
        if (dataSnapshot != null) {
            bolusCalc.onBolusCalcDataSnapshotResponse(dataSnapshot)
        }
        if (lastBG != null) {
            bolusCalc.onLastBGResponse(lastBG)
        }

        bolusCalc.setInsulinUnits(bolusUnitsUserInput)
        bolusCalc.setCarbsValueGrams(bolusCarbsGramsUserInput)
        bolusCalc.setGlucoseMgdl(bolusBgMgdlUserInput)
        return bolusCalc
    }

    fun bolusCalcDecision(bolusCalc: BolusCalculatorBuilder?): BolusCalcDecision? {
        val decision = bolusCalc?.build()?.parse()
        Timber.i("bolusCalcDecision: OUTPUT units=${decision?.units} conditions=${decision?.conditions}")
        return decision
    }

    fun bolusCalcParameters(bolusCalc: BolusCalculatorBuilder?): BolusParameters {
        val decision = bolusCalc?.build()?.parse()
        return BolusParameters(decision?.units?.total, bolusCalc?.carbsValueGrams?.orElse(null), bolusCalc?.glucoseMgdl?.orElse(null))
    }

    ScalingLazyColumn(
        modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams()
    ) {
        item {
            val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
            val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()

            sendPumpCommand(BolusCalcDataSnapshotRequest())
            sendPumpCommand(LastBGRequest())

            Chip(
                onClick = onClickUnits,
                label = {
                    Text(
                        "Units",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = {
                    Text(
                        text = when (bolusUnitsUserInput) {
                            null -> "Calculated: ${twoDecimalPlaces(bolusCalcParameters(runBolusCalculator(bolusCalcDataSnapshot.value, bolusCalcLastBG.value, bolusUnitsUserInput, bolusCarbsGramsUserInput, bolusBgMgdlUserInput)).units)}"
                            else -> "Entered: ${String.format("%.2f", bolusUnitsUserInput)}"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                onClick = onClickCarbs,
                label = {
                    Text(
                        "Carbs (g)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = {
                    Text(
                        text = when (bolusCarbsGramsUserInput) {
                            null -> "Not Entered"
                            else -> "$bolusCarbsGramsUserInput"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
            val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
            val autofilledBg = runBolusCalculator(bolusCalcDataSnapshot.value, bolusCalcLastBG.value, bolusUnitsUserInput, bolusCarbsGramsUserInput, bolusBgMgdlUserInput).glucoseMgdl.orElse(null)
            Chip(
                onClick = onClickBG,
                label = {
                    Text(
                        "BG (mg/dL)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = {
                    Text(
                        text = when {
                            bolusBgMgdlUserInput != null -> "$bolusBgMgdlUserInput"
                            autofilledBg != null -> "From CGM: $autofilledBg"
                            else -> "Not Entered"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
            val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
            CompactButton(
                onClick = {
                    bolusCalculatorBuilder = runBolusCalculator(bolusCalcDataSnapshot.value!!, bolusCalcLastBG.value!!, bolusUnitsUserInput, bolusCarbsGramsUserInput, bolusBgMgdlUserInput)
                    bolusFinalConditions = bolusCalcDecision(bolusCalculatorBuilder)?.conditions
                    bolusFinalParameters = bolusCalcParameters(bolusCalculatorBuilder)
                    showConfirmDialog = true
                },
                enabled = true
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "continue",
                    modifier = Modifier
                        .size(ButtonDefaults.SmallIconSize)
                        .wrapContentSize(align = Alignment.Center),
                )
            }
        }

        item {
            val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
            val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
            val conditions = bolusCalcDecision(runBolusCalculator(bolusCalcDataSnapshot.value, bolusCalcLastBG.value, bolusUnitsUserInput, bolusCarbsGramsUserInput, bolusBgMgdlUserInput))?.conditions
            conditions?.forEach { condition ->
                LineInfoChip(
                    labelText = condition.msg,
                    fontSize = 10.sp
                )
            }
        }
    }

    val scrollState = rememberScalingLazyListState()

    Dialog(
        showDialog = showConfirmDialog,
        onDismissRequest = {
            showConfirmDialog = false
        },
        scrollState = scrollState
    ) {
        Alert(
            title = {
                Text(
                    text = "${bolusFinalParameters?.units?.let { twoDecimalPlaces(it) } ?: 0 }u Bolus",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Do not deliver bolus"
                    )
                }
            },
            positiveButton = {
                Button(
                    onClick = {
                        // TODO: deliver safely!
                        showConfirmDialog = false
                        showInProgressDialog = true
                    },
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Deliver bolus"
                    )
                }
            },
            scrollState = scrollState
        ) {
            Text(
                text = "Do you want to deliver the bolus? STUB",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }

    fun cancelBolus() {
        Timber.i("todo: bolus would be cancelled!")
    }

    Dialog(
        showDialog = showInProgressDialog,
        onDismissRequest = {
            cancelBolus()
            showInProgressDialog = false
        },
        scrollState = scrollState
    ) {
        Alert(
            title = {
                Text(
                    text = "${bolusCalculatorBuilder}u Bolus",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
                )
            },
            negativeButton = {
                Button(
                    onClick = {
                        showInProgressDialog = false
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()

                ) {
                    Text("Cancel")
                }
            },
            positiveButton = {},
            scrollState = scrollState
        ) {
            Text(
                text = "The bolus is being delivered. STUB",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }

}
