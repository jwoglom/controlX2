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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.wearx2.presentation.components.DecimalNumberPicker
import com.jwoglom.wearx2.presentation.components.SingleNumberPicker
import com.jwoglom.wearx2.presentation.components.TopCGMReadingText
import com.jwoglom.wearx2.presentation.navigation.DestinationScrollType
import com.jwoglom.wearx2.presentation.navigation.SCROLL_TYPE_NAV_ARGUMENT
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.ui.BolusScreen
import com.jwoglom.wearx2.presentation.ui.IndeterminateProgressIndicator
import com.jwoglom.wearx2.presentation.ui.LandingScreen
import com.jwoglom.wearx2.presentation.ui.ScalingLazyListStateViewModel
import com.jwoglom.wearx2.presentation.ui.ScrollStateViewModel

@Composable
fun WearApp(
    modifier: Modifier = Modifier,
    swipeDismissableNavController: NavHostController = rememberSwipeDismissableNavController(),
    sendPumpCommand: (Message) -> Unit
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
        val currentBackStackEntry by swipeDismissableNavController.currentBackStackEntryAsState()

        val scrollType =
            currentBackStackEntry?.arguments?.getSerializable(SCROLL_TYPE_NAV_ARGUMENT)
                ?: DestinationScrollType.NONE

        // TODO: consider moving to ViewModel
        // Display value is passed down to various user input screens, for the slider and stepper
        // components specifically, to demonstrate how they work.
        var displayValueForUserInput by remember { mutableStateOf(5) }
        var bolusUnitsUserInput by remember { mutableStateOf<Double?>(null) }
        var bolusCarbsGramsUserInput by remember { mutableStateOf<Int?>(null) }
        var bolusBgMgdlUserInput by remember { mutableStateOf<Int?>(null) }

        Scaffold(
            modifier = modifier,
            timeText = {
                // Scaffold places time at top of screen to follow Material Design guidelines.
                // (Time is hidden while scrolling.)

                //key() {
                key (currentBackStackEntry?.destination?.route) {
                    if (currentBackStackEntry?.destination?.route == Screen.Landing.route) {
                        TopCGMReadingText()
                    }
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
                navController = swipeDismissableNavController,
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

                    val menuItems = listOf(
                        menuNameAndCallback(
                            navController = swipeDismissableNavController,
                            menuName = "Bolus",
                            screen = Screen.Bolus
                        ),
                    )

                    LandingScreen(
                        scalingLazyListState = scalingLazyListState,
                        focusRequester = focusRequester,
                        swipeDismissableNavController = swipeDismissableNavController,
                        sendPumpCommand = sendPumpCommand,
                    )

                    RequestFocusOnResume(focusRequester)
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
                            swipeDismissableNavController.navigate(Screen.BolusSelectUnitsScreen.route)
                        },
                        onClickCarbs = {
                            swipeDismissableNavController.navigate(Screen.BolusSelectCarbsScreen.route)
                        },
                        onClickBG = {
                            swipeDismissableNavController.navigate(Screen.BolusSelectBGScreen.route)
                        },
                        sendPumpCommand = sendPumpCommand,
                    )

                    RequestFocusOnResume(focusRequester)
                }

                composable(Screen.BolusSelectUnitsScreen.route) {
                    DecimalNumberPicker(
                        label = "Units",
                        onNumberConfirm = {
                            swipeDismissableNavController.popBackStack()
                            bolusUnitsUserInput = it
                        }
                    )
                }

                composable(Screen.BolusSelectCarbsScreen.route) {
                    SingleNumberPicker(
                        label = "Carbs",
                        maxNumber = 100,
                        onNumberConfirm = {
                            swipeDismissableNavController.popBackStack()
                            bolusCarbsGramsUserInput = it
                        }
                    )
                }

                composable(Screen.BolusSelectBGScreen.route) {
                    SingleNumberPicker(
                        label = "BG",
                        minNumber = 40,
                        maxNumber = 400,
                        defaultNumber = 120,
                        onNumberConfirm = {
                            swipeDismissableNavController.popBackStack()
                            bolusBgMgdlUserInput = it
                        }
                    )
                }
//
//                composable(route = Screen.Stepper.route) {
//                    StepperScreen(
//                        displayValue = displayValueForUserInput,
//                        onValueChange = {
//                            displayValueForUserInput = it
//                        }
//                    )
//                }
//
//                composable(route = Screen.Slider.route) {
//                    SliderScreen(
//                        displayValue = displayValueForUserInput,
//                        onValueChange = {
//                            displayValueForUserInput = it
//                        }
//                    )
//                }
//
//                composable(
//                    route = Screen.WatchList.route,
//                    arguments = listOf(
//                        // In this case, the argument isn't part of the route, it's just attached
//                        // as information for the destination.
//                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
//                            type = NavType.EnumType(DestinationScrollType::class.java)
//                            defaultValue = DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
//                        }
//                    )
//                ) {
//                    val scalingLazyListState = scalingLazyListState(it)
//                    val focusRequester = remember { FocusRequester() }
//
//                    val viewModel: WatchListViewModel = viewModel(
//                        factory = WatchListViewModel.Factory
//                    )
//
//                    WatchListScreen(
//                        viewModel = viewModel,
//                        scalingLazyListState = scalingLazyListState,
//                        focusRequester = focusRequester,
//                        showVignette = vignetteVisiblePreference,
//                        onClickVignetteToggle = { showVignette ->
//                            vignetteVisiblePreference = showVignette
//                        },
//                        onClickWatch = { id ->
//                            swipeDismissableNavController.navigate(
//                                route = Screen.WatchDetail.route + "/" + id
//                            )
//                        }
//                    )
//
//                    RequestFocusOnResume(focusRequester)
//                }
//
//                composable(
//                    route = Screen.WatchDetail.route + "/{$WATCH_ID_NAV_ARGUMENT}",
//                    arguments = listOf(
//                        navArgument(WATCH_ID_NAV_ARGUMENT) {
//                            type = NavType.IntType
//                        },
//                        // In this case, the argument isn't part of the route, it's just attached
//                        // as information for the destination.
//                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
//                            type = NavType.EnumType(DestinationScrollType::class.java)
//                            defaultValue = DestinationScrollType.COLUMN_SCROLLING
//                        }
//                    )
//                ) {
//                    val watchId: Int = it.arguments!!.getInt(WATCH_ID_NAV_ARGUMENT)
//
//                    val viewModel: WatchDetailViewModel =
//                        viewModel(factory = WatchDetailViewModel.factory(watchId))
//
//                    val scrollState = scrollState(it)
//                    val focusRequester = remember { FocusRequester() }
//
//                    WatchDetailScreen(
//                        viewModel = viewModel,
//                        scrollState = scrollState,
//                        focusRequester = focusRequester
//                    )
//
//                    RequestFocusOnResume(focusRequester)
//                }
//
//                composable(Screen.DatePicker.route) {
//                    DatePicker(
//                        onDateConfirm = {
//                            swipeDismissableNavController.popBackStack()
//                            dateTimeForUserInput = it.atTime(dateTimeForUserInput.toLocalTime())
//                        },
//                        date = dateTimeForUserInput.toLocalDate()
//                    )
//                }
//
//                composable(Screen.Time24hPicker.route) {
//                    TimePicker(
//                        onTimeConfirm = {
//                            swipeDismissableNavController.popBackStack()
//                            dateTimeForUserInput = it.atDate(dateTimeForUserInput.toLocalDate())
//                        },
//                        time = dateTimeForUserInput.toLocalTime()
//                    )
//                }
//
//                composable(Screen.Time12hPicker.route) {
//                    TimePickerWith12HourClock(
//                        onTimeConfirm = {
//                            swipeDismissableNavController.popBackStack()
//                            dateTimeForUserInput = it.atDate(dateTimeForUserInput.toLocalDate())
//                        },
//                        time = dateTimeForUserInput.toLocalTime()
//                    )
//                }
//
//                composable(Screen.Dialogs.route) {
//                    Dialogs()
//                }
//
//                composable(
//                    route = Screen.ProgressIndicators.route,
//                    arguments = listOf(
//                        // In this case, the argument isn't part of the route, it's just attached
//                        // as information for the destination.
//                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
//                            type = NavType.EnumType(DestinationScrollType::class.java)
//                            defaultValue = DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
//                        }
//                    )
//                ) {
//                    val scalingLazyListState = scalingLazyListState(it)
//
//                    val focusRequester = remember { FocusRequester() }
//                    val menuItems = listOf(
//                        menuNameAndCallback(
//                            navController = swipeDismissableNavController,
//                            menuName = R.string.indeterminate_progress_indicator_label,
//                            screen = Screen.IndeterminateProgressIndicator
//                        ),
//                        menuNameAndCallback(
//                            navController = swipeDismissableNavController,
//                            menuName = R.string.full_screen_progress_indicator_label,
//                            screen = Screen.FullScreenProgressIndicator
//                        )
//                    )
//                    ProgressIndicatorsScreen(
//                        scalingLazyListState = scalingLazyListState,
//                        focusRequester = focusRequester,
//                        menuItems = menuItems
//                    )
//                    RequestFocusOnResume(focusRequester)
//                }
//
//                composable(Screen.IndeterminateProgressIndicator.route) {
//                    IndeterminateProgressIndicator()
//                }
//
//                composable(
//                    route = Screen.FullScreenProgressIndicator.route,
//                    arguments = listOf(
//                        // In this case, the argument isn't part of the route, it's just attached
//                        // as information for the destination.
//                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
//                            type = NavType.EnumType(DestinationScrollType::class.java)
//                            defaultValue = DestinationScrollType.TIME_TEXT_ONLY
//                        }
//                    )
//                ) {
//                    FullScreenProgressIndicator()
//                }
//
//                composable(
//                    route = Screen.Theme.route,
//                    arguments = listOf(
//                        // In this case, the argument isn't part of the route, it's just attached
//                        // as information for the destination.
//                        navArgument(SCROLL_TYPE_NAV_ARGUMENT) {
//                            type = NavType.EnumType(DestinationScrollType::class.java)
//                            defaultValue = DestinationScrollType.SCALING_LAZY_COLUMN_SCROLLING
//                        }
//                    )
//                ) { it ->
//                    val scalingLazyListState = scalingLazyListState(it)
//                    val focusRequester = remember { FocusRequester() }
//
//                    ThemeScreen(
//                        scalingLazyListState = scalingLazyListState,
//                        focusRequester = focusRequester,
//                        currentlySelectedColors = themeColors,
//                        availableThemes = themeValues
//                    ) { colors -> themeColors = colors }
//                    RequestFocusOnResume(focusRequester)
//                }
//
//                activity(
//                    route = Screen.Map.route
//                ) {
//                    this.activityClass = MapActivity::class
//                }
            }
        }
    }
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
