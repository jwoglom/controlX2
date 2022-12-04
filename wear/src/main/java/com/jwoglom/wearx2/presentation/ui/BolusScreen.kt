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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import timber.log.Timber


@Composable
fun BolusScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    value: Int,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?,
    onClickUnits: () -> Unit,
    onClickCarbs: () -> Unit,
    onClickBG: () -> Unit,
    onClickNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }
    var bolusParameters: Double? = null

    fun computeBolusAmount(bolusUnitsUserInput: Double?, bolusCarbsGramsUserInput: Int?, bolusBgMgdlUserInput: Int?): Double {
        Timber.i("computeBolusAmount: units=$bolusUnitsUserInput carbs=$bolusCarbsGramsUserInput bg=$bolusBgMgdlUserInput")
        return bolusUnitsUserInput!!
    }

    ScalingLazyColumn(
        modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
        state = scalingLazyListState,
        autoCentering = AutoCenteringParams(itemIndex = 0, itemOffset = 0)
    ) {
        item {
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
                        text = String.format("%.1f", bolusUnitsUserInput)
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
                        text = "$bolusCarbsGramsUserInput"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
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
                        text = "$bolusBgMgdlUserInput"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            CompactButton(
                onClick = {
                    bolusParameters = computeBolusAmount(bolusUnitsUserInput, bolusCarbsGramsUserInput, bolusBgMgdlUserInput)
                    showConfirmDialog = true
                },
                enabled = true
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "continue",
                    modifier = Modifier
                        .size(ButtonDefaults.SmallIconSize).wrapContentSize(align = Alignment.Center),
                )
            }
        }
    }

    val scrollState = rememberScalingLazyListState()

    Dialog(
        showDialog = showConfirmDialog,
        onDismissRequest = {

        },
        scrollState = scrollState
    ) {
        Alert(
            title = {
                Text(
                    text = "${bolusParameters}u Bolus",
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
                    text = "${bolusParameters}u Bolus",
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
