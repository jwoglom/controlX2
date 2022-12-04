package com.jwoglom.wearx2.presentation.navigation
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

// Used as a Navigation Argument for the WatchDetail Screen.
const val WATCH_ID_NAV_ARGUMENT = "watchId"

// Navigation Argument for Screens with scrollable types:
// 1. WatchList -> ScalingLazyColumn
// 2. WatchDetail -> Column (with scaling enabled)
const val SCROLL_TYPE_NAV_ARGUMENT = "scrollType"

/**
 * Represent all Screens (Composables) in the app.
 */
sealed class Screen(
    val route: String
) {
    object WaitingForPhone : Screen("Waiting for phone")
    object WaitingToFindPump : Screen("Waiting to find pump")
    object ConnectingToPump : Screen("Waiting for pump connection")
    object PumpDisconnectedReconnecting : Screen("Waiting for pump connection")
    object Landing : Screen("landing")
    object WatchList : Screen("watchList")
    object WatchDetail : Screen("watchDetail")
    object UserInputComponents : Screen("userInputComponents")
    object Stepper : Screen("stepper")
    object Slider : Screen("slider")
    object Map : Screen("map")
    object DatePicker : Screen("date")
    object Time12hPicker : Screen("time12h")
    object Time24hPicker : Screen("time24h")
    object Dialogs : Screen("dialogs")
    object ProgressIndicators : Screen("progressIndicators")
    object IndeterminateProgressIndicator : Screen("indeterminateProgressIndicator")
    object FullScreenProgressIndicator : Screen("fullScreenProgressIndicator")
    object Theme : Screen("theme")
}
