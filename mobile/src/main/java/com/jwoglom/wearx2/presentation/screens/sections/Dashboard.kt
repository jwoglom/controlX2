@file:OptIn(ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
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
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.dataStore
import com.jwoglom.wearx2.presentation.components.LastConnectionUpdatedTimestamp
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.wearx2.presentation.components.PumpSetupStageProgress
import com.jwoglom.wearx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.wearx2.presentation.screens.setUpPreviewState
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.presentation.LifecycleStateObserver
import com.jwoglom.wearx2.shared.presentation.intervalOf
import com.jwoglom.wearx2.shared.util.SendType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun Dashboard(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, dashboardCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = dashboardFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("Dashboard loading: remaining ${nullFields.size}: ${dashboardFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("Dashboard loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("Dashboard loading done: ${dashboardFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading Dashboard with force")
        refreshing = true

        dashboardFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading Dashboard from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading Dashboard from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            content = {
                item {
                    ServiceDisabledMessage()
                    LastConnectionUpdatedTimestamp()
                    PumpSetupStageProgress(initialSetup = false)
                    PumpSetupStageDescription(initialSetup = false)

                    LaunchedEffect(pumpLastConnectionTimestamp.value) {
                        Timber.d("pumpLastConnectionTimestamp effect: ${pumpLastConnectionTimestamp.value}")
                        sendPumpCommands(SendType.CACHED, dashboardCommands)
                    }
                }

                item {
                    val cgmReading = ds.cgmReading.observeAsState()
                    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
                    Line(
                        "${
                            when (cgmReading.value) {
                                0 -> "CGM Value Unknown"
                                null -> ""
                                else -> cgmReading.value
                            }
                        } ${cgmDeltaArrow.value ?: ""}",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }

                item {
                    val batteryPercent = ds.batteryPercent.observeAsState()
                    Line(batteryPercent.value?.let {
                        "Battery: ${batteryPercent.value}%"
                    } ?: "")
                }

                item {
                    val iobUnits = ds.iobUnits.observeAsState()
                    Line(iobUnits.value?.let {
                        "IOB: ${iobUnits.value}"
                    } ?: "")
                }

                item {
                    val cartridgeRemainingUnits =
                        ds.cartridgeRemainingUnits.observeAsState()
                    Line(cartridgeRemainingUnits.value?.let {
                        "Cartridge: ${cartridgeRemainingUnits.value}"
                    } ?: "")
                }

                item {
                    val lastBolusStatus = ds.lastBolusStatus.observeAsState()
                    Line(lastBolusStatus.value?.let {
                        "Last Bolus: ${lastBolusStatus.value}"
                    } ?: "")
                }

                item {
                    val basalRate = dataStore.basalRate.observeAsState()
                    val basalStatus = dataStore.basalStatus.observeAsState()
                    val landingBasalDisplayedText =
                        dataStore.landingBasalDisplayedText.observeAsState()

                    LaunchedEffect(basalRate.value, basalStatus.value) {
                        dataStore.landingBasalDisplayedText.value = when (basalStatus.value) {
                            "On", "Zero", "Increased", "Reduced", null -> when (basalRate.value) {
                                null -> null
                                else -> "${basalRate.value}"
                            }
                            else -> when (basalRate.value) {
                                null -> "${basalStatus.value}"
                                else -> "${basalStatus.value} (${basalRate.value})"
                            }
                        }
                    }
                    Line(landingBasalDisplayedText.value?.let {
                        "Basal: ${landingBasalDisplayedText.value}"
                    } ?: "")
                }

                item {

                    val controlIQStatus = LocalDataStore.current.controlIQStatus.observeAsState()
                    val controlIQMode = LocalDataStore.current.controlIQMode.observeAsState()
                    val landingControlIQDisplayedText =
                        dataStore.landingControlIQDisplayedText.observeAsState()

                    LaunchedEffect(controlIQStatus.value, controlIQMode.value) {
                        dataStore.landingControlIQDisplayedText.value = when (controlIQMode.value) {
                            "Sleep", "Exercise" -> "${controlIQMode.value}"
                            else -> when (controlIQStatus.value) {
                                null -> null
                                else -> "${controlIQStatus.value}"
                            }
                        }
                    }

                    Line(landingControlIQDisplayedText.value?.let {
                        "Control-IQ: ${landingControlIQDisplayedText.value}"
                    } ?: "")
                }

                item {
                    val cgmSessionExpireRelative =
                        ds.cgmSessionExpireRelative.observeAsState()
                    val cgmSessionExpireExact =
                        ds.cgmSessionExpireExact.observeAsState()
                    Line(cgmSessionExpireRelative.value?.let {
                        "CGM Sensor: ${cgmSessionExpireRelative.value} (${cgmSessionExpireExact.value})"
                    } ?: "")
                }

                item {
                    val cgmTransmitterStatus = ds.cgmTransmitterStatus.observeAsState()
                    Line(cgmTransmitterStatus.value?.let {
                        "CGM Transmitter Battery: ${cgmTransmitterStatus.value}"
                    } ?: "")
                }
            }
        )
    }
}

fun apiVersion(): ApiVersion {
    var apiVersion = PumpState.getPumpAPIVersion()
    if (apiVersion == null) {
        apiVersion = KnownApiVersion.API_V2_5.get()
    }
    return apiVersion
}

val dashboardCommands = listOf(
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

val dashboardFields = listOf(
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

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Dashboard(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}