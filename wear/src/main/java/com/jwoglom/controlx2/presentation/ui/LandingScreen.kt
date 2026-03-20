package com.jwoglom.controlx2.presentation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.ui.components.LandingBasalRow
import com.jwoglom.controlx2.presentation.ui.components.LandingBolusChip
import com.jwoglom.controlx2.presentation.ui.components.LandingBuildInfo
import com.jwoglom.controlx2.presentation.ui.components.LandingCgmBatteryRow
import com.jwoglom.controlx2.presentation.ui.components.LandingCgmSensorRow
import com.jwoglom.controlx2.presentation.ui.components.LandingControlIQRow
import com.jwoglom.controlx2.presentation.ui.components.LandingFooterActions
import com.jwoglom.controlx2.presentation.ui.components.LandingModeActionsRow
import com.jwoglom.controlx2.presentation.ui.components.LandingTopRow
import com.jwoglom.controlx2.shared.presentation.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBasalStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class, ExperimentalWearMaterialApi::class)
@Composable
fun LandingScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendPhoneCommand: (String) -> Unit,
    sendPhoneOpenActivity: () -> Unit,
    resetSavedBolusEnteredState: () -> Unit,
    resetSavedTempBasalEnteredState: () -> Unit = {},
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun apiVersion(): ApiVersion = PumpState.getPumpAPIVersion() ?: KnownApiVersion.API_V2_5.get()

    val commands = listOf(
        CurrentBatteryRequestBuilder.create(apiVersion()),
        ControlIQIOBRequest(),
        InsulinStatusRequest(),
        LastBolusStatusRequestBuilder.create(apiVersion()),
        HomeScreenMirrorRequest(),
        ControlIQInfoRequestBuilder.create(apiVersion()),
        CurrentBasalStatusRequest(),
        CGMStatusRequest(),
        CurrentEGVGuiDataRequest(),
        GlobalMaxBolusSettingsRequest()
    )

    val fields = listOf(
        dataStore.batteryPercent,
        dataStore.iobUnits,
        dataStore.cartridgeRemainingUnits,
        dataStore.lastBolusStatus,
        dataStore.controlIQStatus,
        dataStore.controlIQMode,
        dataStore.basalRate,
        dataStore.cgmSessionState,
        dataStore.cgmTransmitterStatus,
        dataStore.cgmReading,
        dataStore.cgmDeltaArrow,
    )

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, commands)
    }

    fun waitForLoaded() = refreshScope.launch {
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = fields.filter { it.value == null }.toSet()
            if (nullFields.isEmpty()) break

            if (sinceLastFetchTime >= 2500) {
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            delay(250)
            sinceLastFetchTime += 250
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        Timber.i("reloading LandingPage with force")
        refreshing = true
        fields.forEach { it.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    LaunchedEffect(intervalOf(60)) {
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing,
            state,
            Modifier.align(Alignment.TopCenter).zIndex(10f)
        )

        ScalingLazyColumn(
            modifier = Modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {
            item {
                ReportFullyDrawn()
                LandingTopRow()
            }

            item {
                LandingBolusChip(
                    onClick = {
                        resetSavedBolusEnteredState()
                        resetBolusDataStoreState(dataStore)
                        navController.navigate(Screen.Bolus.route)
                    }
                )
            }

            item {
                LandingBasalRow(
                    onClick = {
                        resetSavedTempBasalEnteredState()
                        resetTempBasalDataStoreState(dataStore)
                        navController.navigate(Screen.TempBasal.route)
                    }
                )
            }

            item {
                LandingModeActionsRow(
                    onExerciseClick = { navController.navigate(Screen.ExerciseModeSet.route) },
                    onSleepClick = { navController.navigate(Screen.SleepModeSet.route) }
                )
            }

            item { LandingControlIQRow() }
            item { LandingCgmSensorRow() }
            item { LandingCgmBatteryRow() }

            item {
                LandingFooterActions(
                    onForceReload = { sendPhoneCommand("force-reload") },
                    onOpenPhone = sendPhoneOpenActivity,
                )
            }

            item { LandingBuildInfo() }
        }
    }
}

@Preview(apiLevel = 28, uiMode = Configuration.UI_MODE_TYPE_WATCH, device = Devices.WEAR_OS_RECT, heightDp = 500, showBackground = true)
@Composable
fun DefaultLandingScreenPreviewFull() {
    LandingScreen(
        scalingLazyListState = ScalingLazyListState(1, 0),
        focusRequester = FocusRequester(),
        sendPumpCommands = { _, _ -> },
        sendPhoneOpenActivity = {},
        sendPhoneCommand = {},
        resetSavedBolusEnteredState = {},
        navController = rememberSwipeDismissableNavController()
    )
}

@Preview(apiLevel = 28, uiMode = Configuration.UI_MODE_TYPE_WATCH, device = Devices.WEAR_OS_LARGE_ROUND, showBackground = true)
@Composable
fun DefaultLandingScreenPreviewCropped() {
    LandingScreen(
        scalingLazyListState = ScalingLazyListState(1, 0),
        focusRequester = FocusRequester(),
        sendPumpCommands = { _, _ -> },
        sendPhoneOpenActivity = {},
        sendPhoneCommand = {},
        resetSavedBolusEnteredState = {},
        navController = rememberSwipeDismissableNavController()
    )
}
