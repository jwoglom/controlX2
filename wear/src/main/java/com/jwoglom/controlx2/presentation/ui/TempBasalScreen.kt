package com.jwoglom.controlx2.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.ScalingLazyListState
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.ui.components.tempbasal.TempBasalConfirmPhase
import com.jwoglom.controlx2.presentation.ui.components.tempbasal.TempBasalInputPhase
import com.jwoglom.controlx2.presentation.ui.components.tempbasal.TempBasalRateMode
import com.jwoglom.controlx2.presentation.ui.components.tempbasal.TempBasalSendingPhase
import com.jwoglom.controlx2.shared.presentation.LifecycleStateObserver
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TempBasalScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    tempBasalPercentUserInput: Int?,
    tempBasalUnitsPerHrUserInput: Double?,
    tempBasalDurationHoursUserInput: Int?,
    tempBasalDurationMinutesUserInput: Int?,
    rateMode: TempBasalRateMode,
    onToggleRateMode: () -> Unit,
    onClickPercent: () -> Unit,
    onClickUnitsPerHr: () -> Unit,
    onClickDurationHours: () -> Unit,
    onClickDurationMinutes: () -> Unit,
    onClickLanding: () -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSendingDialog by remember { mutableStateOf(false) }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val dataStore = LocalDataStore.current

    val commands = listOf(TempRateRequest())

    val baseFields = listOf(
        dataStore.tempRateActive,
        dataStore.tempRateDetails
    )

    @Synchronized
    fun waitForLoaded() = refreshScope.launch {
        var sinceLastFetchTime = 0
        while (true) {
            val nullBaseFields = baseFields.filter { field -> field.value == null }.toSet()
            if (nullBaseFields.isEmpty()) {
                break
            }

            Timber.i("TempBasalScreen loading: remaining ${nullBaseFields.size}: ${baseFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("TempBasalScreen loading re-fetching")
                sendPumpCommands(SendType.STANDARD, commands)
                sinceLastFetchTime = 0
            }

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("TempBasalScreen base loading done: ${baseFields.map { it.value }}")
        if (sinceLastFetchTime == 0) {
            delay(250)
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
    }) {
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    // Parse current basal rate from string like "0.85u" to Double
    val basalRateString = dataStore.basalRate.observeAsState()
    val currentBasalRate = basalRateString.value?.replace("u", "")?.toDoubleOrNull()

    fun calculatePercent(): Int {
        return when (rateMode) {
            TempBasalRateMode.PERCENT -> tempBasalPercentUserInput ?: 100
            TempBasalRateMode.UNITS_PER_HR -> {
                if (tempBasalUnitsPerHrUserInput != null && currentBasalRate != null && currentBasalRate > 0) {
                    (tempBasalUnitsPerHrUserInput / currentBasalRate * 100).toInt()
                } else {
                    100
                }
            }
        }
    }

    fun calculateDurationMinutes(): Int {
        return (tempBasalDurationHoursUserInput ?: 0) * 60 + (tempBasalDurationMinutesUserInput ?: 0)
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

        TempBasalInputPhase(
            modifier = modifier,
            scalingLazyListState = scalingLazyListState,
            focusRequester = focusRequester,
            rateMode = rateMode,
            percentValue = tempBasalPercentUserInput,
            unitsPerHrValue = tempBasalUnitsPerHrUserInput,
            currentBasalRate = currentBasalRate,
            durationHours = tempBasalDurationHoursUserInput,
            durationMinutes = tempBasalDurationMinutesUserInput,
            onClickRate = {
                when (rateMode) {
                    TempBasalRateMode.PERCENT -> onClickPercent()
                    TempBasalRateMode.UNITS_PER_HR -> onClickUnitsPerHr()
                }
            },
            onToggleRateMode = onToggleRateMode,
            onClickHours = onClickDurationHours,
            onClickMinutes = onClickDurationMinutes,
            onContinue = {
                showConfirmDialog = true
            },
        )

        TempBasalConfirmPhase(
            showDialog = showConfirmDialog,
            onDismiss = { showConfirmDialog = false },
            onConfirm = {
                showConfirmDialog = false
                showSendingDialog = true

                val percent = calculatePercent()
                val durationMinutes = calculateDurationMinutes()
                Timber.i("TempBasalScreen: sending SetTempRateRequest percent=$percent duration=$durationMinutes")

                sendPumpCommands(SendType.BUST_CACHE, listOf(
                    SetTempRateRequest(durationMinutes, percent)
                ))

                // Follow up with a status check after a delay, then navigate back
                refreshScope.launch {
                    delay(500)
                    sendPumpCommands(SendType.BUST_CACHE, listOf(TempRateRequest()))
                    delay(500)
                    showSendingDialog = false
                    onClickLanding()
                }
            },
            onReject = { showConfirmDialog = false },
            percent = calculatePercent(),
            durationMinutes = calculateDurationMinutes(),
        )

        TempBasalSendingPhase(
            showDialog = showSendingDialog,
            onDismiss = {
                showSendingDialog = false
                onClickLanding()
            },
        )
    }
}

fun resetTempBasalDataStoreState(dataStore: DataStore) {
    Timber.d("resetTempBasalDataStoreState")
    dataStore.tempRateActive.value = null
    dataStore.tempRateDetails.value = null
}
