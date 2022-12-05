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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import com.google.accompanist.flowlayout.FlowRow
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBasalStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.dataStore
import com.jwoglom.wearx2.presentation.MenuItem
import com.jwoglom.wearx2.presentation.components.FirstRowChip
import com.jwoglom.wearx2.presentation.components.LineInfoChip
import com.jwoglom.wearx2.presentation.defaultTheme
import com.jwoglom.wearx2.presentation.greenTheme
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.redTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LandingScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommand: (Message) -> Unit,
    swipeDismissableNavController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }


    fun apiVersion(): ApiVersion {
        var apiVersion = PumpState.getPumpAPIVersion()
        if (apiVersion == null) {
            apiVersion = KnownApiVersion.API_V2_5.get()
        }
        return apiVersion
    }

    fun fetchDataStoreFields() {
        sendPumpCommand(CurrentBatteryRequestBuilder.create(apiVersion()))
        sendPumpCommand(ControlIQIOBRequest())
        sendPumpCommand(InsulinStatusRequest())
        sendPumpCommand(LastBolusStatusRequestBuilder.create(apiVersion()))
        sendPumpCommand(HomeScreenMirrorRequest())
        sendPumpCommand(CurrentBasalStatusRequest())
        sendPumpCommand(CGMStatusRequest())
        sendPumpCommand(CurrentEGVGuiDataRequest())
    }

    fun refresh() = refreshScope.launch {
        refreshing = true
        val fields = listOf(
            dataStore.batteryPercent,
            dataStore.iobUnits,
            dataStore.cartridgeRemainingUnits,
            dataStore.lastBolusStatus,
            dataStore.controlIQStatus,
            dataStore.basalStatus,
            dataStore.cgmSessionState,
            dataStore.cgmTransmitterStatus,
            dataStore.cgmReading,
        )

        fields.forEach { field -> field.value = null }
        fetchDataStoreFields()

        while (true) {
            if (fields.map { field -> field.value == null }.any()) {
                break
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
        }

        refreshing = false
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    fetchDataStoreFields()

    Box(modifier = modifier
        .fillMaxSize()
        .pullRefresh(state)) {
        PullRefreshIndicator(refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f))

        // Places both Chips (button and toggle) in the middle of the screen.
        ScalingLazyColumn(
            modifier = Modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {

            item {
                // Signify we have drawn the content of the first screen
                ReportFullyDrawn()

                val dataStore = LocalDataStore.current

                FlowRow {
                    val batteryPercent = dataStore.batteryPercent.observeAsState()
                    val iobUnits = dataStore.iobUnits.observeAsState()
                    val cartridgeRemainingUnits = dataStore.cartridgeRemainingUnits.observeAsState()

                    FirstRowChip(
                        labelText = when(batteryPercent.value) {
                            null -> "?"
                            else -> "${batteryPercent.value}%"
                        },
                        secondaryLabelText = "Battery",
                        theme = when {
                            batteryPercent.value == null -> defaultTheme
                            batteryPercent.value!! > 50 -> greenTheme
                            batteryPercent.value!! > 25 -> defaultTheme
                            else -> redTheme
                        },
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(iobUnits.value) {
                            null -> "?"
                            else -> "${String.format("%.1f", iobUnits.value)}u"
                        },
                        secondaryLabelText = "IOB",
                        theme = defaultTheme,
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(cartridgeRemainingUnits.value) {
                            null -> "?"
                            else -> "${cartridgeRemainingUnits.value}u"
                        },
                        secondaryLabelText = "Cartridge",
                        theme = when {
                            cartridgeRemainingUnits.value == null -> defaultTheme
                            cartridgeRemainingUnits.value!! > 75 -> greenTheme
                            cartridgeRemainingUnits.value!! > 35 -> defaultTheme
                            else -> redTheme
                        },
                        numItems = 3,
                    )
                }
            }

            item {
                val lastBolusStatus = dataStore.lastBolusStatus.observeAsState()
                Chip(
                    onClick = {
                        swipeDismissableNavController.navigate(Screen.Bolus.route)
                    },
                    label = {
                        Text(
                            "Bolus",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            when (lastBolusStatus.value) {
                                null -> ""
                                else -> "Last: ${lastBolusStatus.value}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val basalStatus = LocalDataStore.current.basalStatus.observeAsState()
                LineInfoChip(
                    "Basal",
                    when(basalStatus.value) {
                        null -> "?"
                        else -> "${basalStatus.value}"
                    }
                )
            }
            item {
                val controlIQStatus = LocalDataStore.current.controlIQStatus.observeAsState()
                LineInfoChip(
                    "Control-IQ",
                    when(controlIQStatus.value) {
                        null -> "?"
                        else -> "${controlIQStatus.value}"
                    }
                )
            }
            item {
                val cgmSensorStatus = LocalDataStore.current.cgmSessionState.observeAsState()
                LineInfoChip(
                    "CGM Sensor",
                    when(cgmSensorStatus.value) {
                        null -> "?"
                        else -> "${cgmSensorStatus.value}"
                    }
                )
            }
            item {
                val cgmTransmitterStatus = LocalDataStore.current.cgmTransmitterStatus.observeAsState()
                LineInfoChip(
                    "CGM Battery",
                    when(cgmTransmitterStatus.value) {
                        null -> "?"
                        else -> "${cgmTransmitterStatus.value}"
                    }
                )
            }
        }
    }
}
