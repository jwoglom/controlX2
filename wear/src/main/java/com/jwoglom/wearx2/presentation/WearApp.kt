package com.jwoglom.wearx2.presentation

/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.compose.layout.fadeAway
import com.google.android.horologist.compose.layout.fadeAwayScalingLazyList
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.presentation.components.BottomText
import com.jwoglom.wearx2.presentation.components.DecimalNumberPicker
import com.jwoglom.wearx2.presentation.components.SingleNumberPicker
import com.jwoglom.wearx2.presentation.components.TopText
import com.jwoglom.wearx2.presentation.navigation.DestinationScrollType
import com.jwoglom.wearx2.presentation.navigation.SCROLL_TYPE_NAV_ARGUMENT
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.ui.BolusScreen
import com.jwoglom.wearx2.presentation.ui.FullScreenText
import com.jwoglom.wearx2.presentation.ui.IndeterminateProgressIndicator
import com.jwoglom.wearx2.presentation.ui.LandingScreen
import com.jwoglom.wearx2.presentation.ui.ScalingLazyListStateViewModel
import com.jwoglom.wearx2.presentation.ui.ScrollStateViewModel
import com.jwoglom.wearx2.shared.util.SendType
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun WearApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberSwipeDismissableNavController(),
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendPhoneBolusRequest: (Int, BolusParameters, BolusCalcUnits, Double) -> Unit,
    sendPhoneBolusCancel: () -> Unit,
    sendPhoneConnectionCheck: () -> Unit,
    sendPhoneCommand: (String) -> Unit,
) {
    var themeColors by remember { mutableStateOf(defaultTheme.colors) }
    WearAppTheme(colors = themeColors) {
        // Allows user to disable the text before the time.
        var showProceedingTextBeforeTime by rememberSaveable { mutableStateOf(false) }

        // Allows user to show/hide the vignette on appropriate screens.
        // IMPORTANT NOTE: Usually you want to show the vignette all the time on screens with
        // scrolling content, a rolling side button, or a rotating bezel. This preference is just
        // to visually demonstrate the vignette for the developer to see it on and off.
        var vignetteVisiblePreference by rememberSaveable { mutableStateOf(true) }

        // Observes the current back stack entry to pull information and determine if the screen
        // is scrollable and the scrollable state.
        //
        // The main reason the state for any scrollable screen is hoisted to this level is so the
        // Scaffold can properly place the position indicator (also known as the scroll indicator).
        //
        // We save the above scrollable states in the SavedStateHandle and retrieve them
        // when needed from custom view models (see ScrollingViewModels class).
        //
        // Screens with scrollable content:
        //  1. The watch list screen uses ScalingLazyColumn (backed by ScalingLazyListState)
        //  2. The watch detail screens uses Column with scrolling enabled (backed by ScrollState).
        //
        // We also use these scrolling states for various other things (like hiding the time
        // when the user is scrolling and only showing the vignette when the screen is
        // scrollable).
        //
        // Remember, mobile guidelines specify that if you back navigate out of a screen and then
        // later navigate into it again, it should be in its initial scroll state (not the last
        // scroll location it was in before you backed out).
        val currentBackStackEntry by navController.currentBackStackEntryAsState()

        val scrollType =
            currentBackStackEntry?.arguments?.getSerializable(SCROLL_TYPE_NAV_ARGUMENT)
                ?: DestinationScrollType.NONE

        // TODO: consider moving to ViewModel
        // Display value is passed down to various user input screens, for the slider and stepper
        // components specifically, to demonstrate how they work.
        var bolusUnitsUserInput by remember { mutableStateOf<Double?>(null) }
        var bolusCarbsGramsUserInput by remember { mutableStateOf<Int?>(null) }
        var bolusBgMgdlUserInput by remember { mutableStateOf<Int?>(null) }

        val resetSavedBolusEnteredState: () -> Unit = {
            bolusUnitsUserInput = null
            bolusCarbsGramsUserInput = null
            bolusBgMgdlUserInput = null
        }

        LaunchedEffect (Unit) {
            navController.navigate(Screen.WaitingForPhone.route)
        }

        LaunchedEffect (navController.currentDestination) {
            sendPhoneConnectionCheck()
        }

        Scaffold(
            modifier = modifier,
            timeText = {
                // Scaffold places time at top of screen to follow Material Design guidelines.
                // (Time is hidden while scrolling.)
                val timeTextModifier =
                    when (scrollType) {
                        DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING -> {
                            val scrollViewModel: ScalingLazyListStateViewModel =
                                viewModel(currentBackStackEntry!!)
                            Modifier.fadeAwayScalingLazyList {
                                scrollViewModel.scrollState
                            }
                        }
                        DestinationScrollType.COLUMN_SCROLLING -> {
                            val viewModel: ScrollStateViewModel =
                                viewModel(currentBackStackEntry!!)
                            Modifier.fadeAway {
                                viewModel.scrollState
                            }
                        }
                        DestinationScrollType.TIME_TEXT_ONLY -> {
                            Modifier
                        }
                        else -> {
                            null
                        }
                    }

                key (currentBackStackEntry?.destination?.route) {
                    TopText(
                        modifier = timeTextModifier ?: Modifier,
                        visible = timeTextModifier != null,
                    )
                }
            },
            vignette = {
                // Only show vignette for screens with scrollable content.
                if (scrollType == DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING ||
                    scrollType == DestinationScrollType.COLUMN_SCROLLING
                ) {
                    if (vignetteVisiblePreference) {
                        Vignette(vignettePosition = VignettePosition.TopAndBottom)
                    }
                }
            },
            positionIndicator = {
                // Only displays the position indicator for scrollable content.
                when (scrollType) {
                    DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING -> {
                        // Get or create the ViewModel associated with the current back stack entry
                        val scrollViewModel: ScalingLazyListStateViewModel =
                            viewModel(currentBackStackEntry!!)
                        PositionIndicator(scalingLazyListState = scrollViewModel.scrollState)
                    }
                    DestinationScrollType.COLUMN_SCROLLING -> {
                        // Get or create the ViewModel associated with the current back stack entry
                        val viewModel: ScrollStateViewModel = viewModel(currentBackStackEntry!!)
                        PositionIndicator(scrollState = viewModel.scrollState)
                    }
                }
            }
        ) {
            /*
             * Wear OS's version of NavHost supports swipe-to-dismiss (similar to back
             * gesture on mobile). Otherwise, the code looks very similar.
             */
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = Screen.WaitingForPhone.route,
                modifier = Modifier.background(MaterialTheme.colors.background)
            ) {

                // WaitingForPhone
                composable(Screen.WaitingForPhone.route) {
                    IndeterminateProgressIndicator(text = "Waiting for phone")
                }

                composable(Screen.WaitingToFindPump.route) {
                    IndeterminateProgressIndicator(text = "Waiting to find pump")
                }

                composable(Screen.ConnectingToPump.route) {
                    IndeterminateProgressIndicator(text = "Connecting to pump")
                }

                composable(Screen.PairingToPump.route) {
                    IndeterminateProgressIndicator(text = "Pairing to pump")
                }

                composable(Screen.MissingPairingCode.route) {
                    IndeterminateProgressIndicator(text = "Pump pairing needed")
                }

                composable(Screen.PumpDisconnectedReconnecting.route) {
                    IndeterminateProgressIndicator(text = "Reconnecting")
                }
                // Main Window
                composable(
                    route = Screen.Landing.route,
                    arguments = listOf(
                        // In this case, the argument isn't part of the route, it's just attached
                        // as information for the destination.
                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
                            type = NavType.EnumType(DestinationScrollType::class.java)
                            defaultValue = DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
                        }
                    )
                ) {
                    val scalingLazyListState = scalingLazyListState(it)

                    val focusRequester = remember { FocusRequester() }

                    LandingScreen(
                        scalingLazyListState = scalingLazyListState,
                        focusRequester = focusRequester,
                        swipeDismissableNavController = navController,
                        sendPumpCommands = sendPumpCommands,
                        sendPhoneCommand = sendPhoneCommand,
                        resetSavedBolusEnteredState = resetSavedBolusEnteredState,
                    )

                    RequestFocusOnResume(focusRequester)
                    BottomText()
                }

                composable(
                    route = Screen.Bolus.route,
                    arguments = listOf(
                        // In this case, the argument isn't part of the route, it's just attached
                        // as information for the destination.
                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
                            type = NavType.EnumType(DestinationScrollType::class.java)
                            defaultValue = DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
                        }
                    )
                ) {
                    val scalingLazyListState = scalingLazyListState(it)

                    val focusRequester = remember { FocusRequester() }

                    BolusScreen(
                        scalingLazyListState = scalingLazyListState,
                        focusRequester = focusRequester,
                        bolusUnitsUserInput = bolusUnitsUserInput,
                        bolusCarbsGramsUserInput = bolusCarbsGramsUserInput,
                        bolusBgMgdlUserInput = bolusBgMgdlUserInput,
                        onClickUnits = {
                            navController.navigate(Screen.BolusSelectUnitsScreen.route)
                        },
                        onClickCarbs = {
                            navController.navigate(Screen.BolusSelectCarbsScreen.route)
                        },
                        onClickBG = {
                            navController.navigate(Screen.BolusSelectBGScreen.route)
                        },
                        onClickLanding = {
                            navController.navigate(Screen.Landing.route)
                        },
                        sendPumpCommands = sendPumpCommands,
                        sendPhoneBolusRequest = sendPhoneBolusRequest,
                        sendPhoneBolusCancel = sendPhoneBolusCancel,
                        resetSavedBolusEnteredState = resetSavedBolusEnteredState,
                    )

                    RequestFocusOnResume(focusRequester)
                    BottomText()
                }

                composable(Screen.BolusSelectUnitsScreen.route) {
                    val currentUnits = LocalDataStore.current.bolusCalculatorBuilder.value?.insulinUnits?.total
                    val maxBolusAmount = LocalDataStore.current.maxBolusAmount.observeAsState()
                    LaunchedEffect(Unit) {
                        sendPumpCommands(SendType.CACHED, listOf(GlobalMaxBolusSettingsRequest()))
                    }

                    DecimalNumberPicker(
                        label = "Units",
                        onNumberConfirm = {
                            navController.popBackStack()
                            bolusUnitsUserInput = it
                        },
                        labelColors = defaultTheme.colors,
                        rotaryScrollCalc = rotaryExponentialScroll(1.3f),
                        maxNumber = when (maxBolusAmount.value) {
                            null -> 30
                            else -> maxBolusAmount.value!!
                        },
                        defaultNumber = when {
                            currentUnits != null -> currentUnits
                            else -> 0.0
                         },
                    )
                    BottomText()
                }

                composable(Screen.BolusSelectCarbsScreen.route) {
                    val currentCarbs = LocalDataStore.current.bolusCalculatorBuilder.value?.carbsValueGrams?.orElse(null)
                    SingleNumberPicker(
                        label = "Carbs",
                        maxNumber = 100,
                        defaultNumber = when {
                            currentCarbs != null -> currentCarbs
                            bolusCarbsGramsUserInput != null -> bolusCarbsGramsUserInput!!
                            else -> 0
                        },
                        rotaryScrollCalc = rotaryExponentialScroll(1.3f),
                        onNumberConfirm = {
                            navController.popBackStack()
                            bolusCarbsGramsUserInput = it
                        }
                    )
                    BottomText()
                }

                composable(Screen.BolusSelectBGScreen.route) {
                    val currentBG = LocalDataStore.current.bolusCalculatorBuilder.value?.glucoseMgdl?.orElse(null)

                    SingleNumberPicker(
                        label = "BG",
                        minNumber = 40,
                        maxNumber = 400,
                        defaultNumber = when {
                            currentBG != null -> currentBG
                            else -> 120
                        },
                        rotaryScrollCalc = rotaryExponentialScroll(1.5f),
                        onNumberConfirm = {
                            navController.popBackStack()
                            bolusBgMgdlUserInput = it
                        }
                    )
                    BottomText()
                }

                composable(Screen.BolusBlocked.route) {
                    FullScreenText("A bolus was blocked which didn't match the units requested. This is either a bug in WearX2 or another actor is attempting to bolus via your phone and/or watch unsuccessfully.")
                    BottomText()
                }
                composable(Screen.BolusNotEnabled.route) {
                    FullScreenText("A bolus was requested, but actions affecting insulin delivery are not enabled in the phone app settings.")
                    BottomText()
                }
                composable(Screen.BolusRejectedOnPhone.route) {
                    Alert(
                        title = {
                            Text(
                                text = "Bolus Rejected on Phone",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onBackground
                            )
                        },
                        negativeButton = {
                            Button(
                                onClick = {
                                    navController.navigate(Screen.Landing.route)
                                },
                                colors = ButtonDefaults.secondaryButtonColors(),
                                modifier = Modifier.fillMaxWidth()

                            ) {
                                Text("Cancel")
                            }
                        },
                        positiveButton = {},
                        icon = {
                            Image(
                                painterResource(R.drawable.bolus_icon),
                                "Bolus icon",
                                Modifier.size(24.dp)
                            )
                        },
                    ) {
                        Text(
                            text = "The bolus request was rejected by the connected phone.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onBackground
                        )
                    }
                    BottomText()
                }
            }
        }
    }
}

private fun rotaryExponentialScroll(factor: Float): (Float) -> Float {
    return { weight -> when {
        weight < 0f -> -1f * abs(weight).pow(factor)
        else -> weight.pow(factor)
    }}
}

@Composable
private fun menuNameAndCallback(
    navController: NavHostController,
    menuName: String,
    screen: Screen
) = MenuItem(menuName) { navController.navigate(screen.route) }

@Composable
private fun scrollState(it: NavBackStackEntry): ScrollState {
    val passedScrollType = it.arguments?.getSerializable(SCROLL_TYPE_NAV_ARGUMENT)

    check(passedScrollType == DestinationScrollType.COLUMN_SCROLLING) {
        "Scroll type must be DestinationScrollType.COLUMN_SCROLLING"
    }

    val scrollViewModel: ScrollStateViewModel = viewModel(it)
    return scrollViewModel.scrollState
}

@Composable
private fun scalingLazyListState(it: NavBackStackEntry): ScalingLazyListState {
    val passedScrollType = it.arguments?.getSerializable(SCROLL_TYPE_NAV_ARGUMENT)

    check(
        passedScrollType == DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
    ) {
        "Scroll type must be DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING"
    }

    val scrollViewModel: ScalingLazyListStateViewModel = viewModel(it)

    return scrollViewModel.scrollState
}

@Composable
private fun RequestFocusOnResume(focusRequester: FocusRequester) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(state = Lifecycle.State.RESUMED) {
            focusRequester.requestFocus()
        }
    }
}

data class MenuItem(val name: String, val clickHander: () -> Unit)
