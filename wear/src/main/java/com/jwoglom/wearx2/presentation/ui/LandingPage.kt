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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.radialGradientBackground
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.curvedText
import com.google.accompanist.flowlayout.FlowRow
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.presentation.MenuItem
import com.jwoglom.wearx2.presentation.components.FirstRowChip
import com.jwoglom.wearx2.presentation.defaultTheme
import com.jwoglom.wearx2.presentation.greenTheme

@Composable
fun LandingScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    onClickWatchList: () -> Unit,
    menuItems: List<MenuItem>,
    proceedingTimeTextEnabled: Boolean,
    onClickProceedingTimeText: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sendPumpCommand: (Message) -> Unit
) {

    Box(modifier = modifier.fillMaxSize()) {
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
                var apiVersion = PumpState.getPumpAPIVersion()
                if (apiVersion == null) {
                    apiVersion = KnownApiVersion.API_V2_5.get()
                }
                sendPumpCommand(CurrentBatteryRequestBuilder.create(apiVersion))
                sendPumpCommand(ControlIQIOBRequest())
                sendPumpCommand(InsulinStatusRequest())

                FlowRow {
                    val batteryPercent = dataStore.batteryPercent.observeAsState()
                    val iobUnits = dataStore.iobUnits.observeAsState()
                    val cartridgeRemainingUnits = dataStore.cartridgeRemainingUnits.observeAsState()

                    FirstRowChip(
                        labelText = when(batteryPercent.value) {
                            null -> ""
                            else -> "${batteryPercent.value}%"
                        },
                        secondaryLabelText = "  Batt",
                        theme = greenTheme,
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(iobUnits.value) {
                            null -> ""
                            else -> "${String.format("%.1f", iobUnits.value)}u"
                        },
                        secondaryLabelText = "    IOB",
                        theme = defaultTheme,
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(cartridgeRemainingUnits.value) {
                            null -> ""
                            else -> "${cartridgeRemainingUnits.value}u"
                        },
                        secondaryLabelText = "    Cart",
                        theme = greenTheme,
                        numItems = 3,
                    )
                }
            }

            for (listItem in menuItems) {
                item {
                    Chip(
                        onClick = listItem.clickHander,
                        label = {
                            Text(
                                listItem.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
//            item {
//                ToggleChip(
//                    modifier = Modifier.fillMaxWidth(),
//                    checked = proceedingTimeTextEnabled,
//                    onCheckedChange = onClickProceedingTimeText,
//                    label = {
//                        Text(
//                            text = "Switch",
//                            maxLines = 1,
//                            overflow = TextOverflow.Ellipsis
//                        )
//                    },6g.
//                    toggleControl = {
//                        Icon(
//                            imageVector = ToggleChipDefaults.switchIcon(
//                                checked = proceedingTimeTextEnabled
//                            ),
//                            contentDescription = if (proceedingTimeTextEnabled) "On" else "Off"
//                        )
//                    }
//                )
//            }
        }

        // Places curved text at the bottom of round devices and straight text at the bottom of
        // non-round devices.
        if (LocalConfiguration.current.isScreenRound) {
            val watchShape = "Watch Shape"
            val primaryColor = MaterialTheme.colors.primary
            CurvedLayout(
                anchor = 90F,
                anchorType = AnchorType.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                curvedRow {
                    curvedText(
                        text = watchShape,
                        angularDirection = CurvedDirection.Angular.CounterClockwise,
                        style = CurvedTextStyle(
                            fontSize = 18.sp,
                            color = primaryColor
                        ),
                        modifier = CurvedModifier
                            .radialGradientBackground(
                                0f to Color.Transparent,
                                0.2f to Color.DarkGray.copy(alpha = 0.2f),
                                0.6f to Color.DarkGray.copy(alpha = 0.2f),
                                0.7f to Color.DarkGray.copy(alpha = 0.05f),
                                1f to Color.Transparent
                            )
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.3f to Color.DarkGray.copy(alpha = 0.05f),
                                0.4f to Color.DarkGray.copy(alpha = 0.2f),
                                0.8f to Color.DarkGray.copy(alpha = 0.2f),
                                1f to Color.Transparent
                            )
                        ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary,
                    text = "Watch Shape",
                    fontSize = 18.sp
                )
            }
        }
    }
}
