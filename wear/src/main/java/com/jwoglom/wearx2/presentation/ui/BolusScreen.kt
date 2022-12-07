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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.Decision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.FailedPrecondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.WaitingOnPrecondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.NonActionDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.FailedSanityCheck
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.components.LineTextDescription
import com.jwoglom.wearx2.util.SendType
import com.jwoglom.wearx2.util.oneDecimalPlace
import com.jwoglom.wearx2.util.twoDecimalPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BolusScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?,
    onClickUnits: (Double?) -> Unit,
    onClickCarbs: () -> Unit,
    onClickBG: () -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val dataStore = LocalDataStore.current

    fun runBolusCalculator(
        dataSnapshot: BolusCalcDataSnapshotResponse?,
        lastBG: LastBGResponse?,
        bolusUnitsUserInput: Double?,
        bolusCarbsGramsUserInput: Int?,
        bolusBgMgdlUserInput: Int?
    ): BolusCalculatorBuilder {
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
        return BolusParameters(
            decision?.units?.total,
            bolusCalc?.carbsValueGrams?.orElse(null),
            bolusCalc?.glucoseMgdl?.orElse(null)
        )
    }

    val commands = listOf(
        BolusCalcDataSnapshotRequest(),
        LastBGRequest()
    )

    val baseFields = listOf(
        dataStore.bolusCalcDataSnapshot,
        dataStore.bolusCalcLastBG
    )

    val calculatedFields = listOf(
        dataStore.bolusCalculatorBuilder,
        dataStore.bolusCurrentParameters
    )

    @Synchronized
    fun waitForLoaded() = refreshScope.launch {
        var sinceLastFetchTime = 0
        while (true) {
            val nullBaseFields = baseFields.filter { field -> field.value == null }.toSet()
            if (nullBaseFields.isEmpty()) {
                break
            }

            Timber.i("BolusScreen loading: remaining ${nullBaseFields.size}: ${baseFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("BolusScreen loading re-fetching")
                // for safety reasons, NEVER CACHE.
                sendPumpCommands(SendType.STANDARD, commands)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("BolusScreen base loading done: ${baseFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true

        baseFields.forEach { field -> field.value = null }
        calculatedFields.forEach { field -> field.value = null }
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    sendPumpCommands(SendType.BUST_CACHE, commands)

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
        val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()

        LaunchedEffect(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            bolusUnitsUserInput,
            bolusCarbsGramsUserInput,
            bolusBgMgdlUserInput
        ) {
            dataStore.bolusCalculatorBuilder.value = runBolusCalculator(
                bolusCalcDataSnapshot.value,
                bolusCalcLastBG.value,
                bolusUnitsUserInput,
                bolusCarbsGramsUserInput,
                bolusBgMgdlUserInput
            )
            dataStore.bolusCurrentParameters.value =
                bolusCalcParameters(dataStore.bolusCalculatorBuilder.value)

            dataStore.bolusUnitsDisplayedText.value = when (bolusUnitsUserInput) {
                null -> when (dataStore.bolusCurrentParameters.value) {
                    null -> "None Entered"
                    else -> "Calculated: ${twoDecimalPlaces(dataStore.bolusCurrentParameters.value!!.units)}"
                }
                else -> "Entered: ${oneDecimalPlace(bolusUnitsUserInput)}"
            }

            val autofilledBg = dataStore.bolusCalculatorBuilder.value?.glucoseMgdl?.orElse(null)
            dataStore.bolusBGDisplayedText.value = when {
                bolusBgMgdlUserInput != null -> "$bolusBgMgdlUserInput"
                autofilledBg != null -> "From CGM: $autofilledBg"
                else -> "Not Entered"
            }
        }

        ScalingLazyColumn(
            modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {
            item {
                val bolusUnitsDisplayedText = dataStore.bolusUnitsDisplayedText.observeAsState()

                Chip(
                    onClick = {
                        if (dataStore.bolusCurrentParameters.value != null) {
                            onClickUnits(dataStore.bolusCurrentParameters.value!!.units)
                        } else {
                            onClickUnits(null)
                        }
                    },
                    label = {
                        Text(
                            "Units",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "${bolusUnitsDisplayedText.value}",
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
                val bolusBGDisplayedText = dataStore.bolusBGDisplayedText.observeAsState()

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
                            text = "${bolusBGDisplayedText.value}",
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                CompactButton(
                    onClick = {
                        dataStore.bolusFinalConditions.value =
                            bolusCalcDecision(dataStore.bolusCalculatorBuilder.value)?.conditions
                        dataStore.bolusFinalParameters.value =
                            bolusCalcParameters(dataStore.bolusCalculatorBuilder.value)
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

            fun sortConditions(set: Set<BolusCalcCondition>?): List<BolusCalcCondition> {
                if (set == null) {
                    return emptyList()
                }

                return set.sortedWith(
                    compareBy(
                        { it.javaClass == FailedSanityCheck::class.java },
                        { it.javaClass == FailedPrecondition::class.java },
                        { it.javaClass == WaitingOnPrecondition::class.java },
                        { it.javaClass == Decision::class.java },
                        { it.javaClass == NonActionDecision::class.java },
                    )
                )
            }

            items(10) { index ->
                val bolusCalculatorBuilder = dataStore.bolusCalculatorBuilder.observeAsState()
                val conditions = sortConditions(bolusCalculatorBuilder.value?.conditions)

                if (index < conditions.size) {
                    LineTextDescription(
                        labelText = conditions[index].msg,
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
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
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

            Alert(
                title = {
                    Text(
                        text = bolusFinalParameters.value?.units?.let { "${twoDecimalPlaces(it)}u Bolus" }
                            ?: "Invalid",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            dataStore.bolusFinalConditions.value = null
                            dataStore.bolusFinalParameters.value = null
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
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

            Alert(
                title = {
                    Text(
                        text = when (bolusFinalParameters.value) {
                            null -> ""
                            else -> "${bolusFinalParameters.value!!.units}u Bolus"
                        },
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
}
