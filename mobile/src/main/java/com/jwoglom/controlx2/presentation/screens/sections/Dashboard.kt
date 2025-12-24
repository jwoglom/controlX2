@file:OptIn(ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.HistoryLogSyncProgressBar
import com.jwoglom.controlx2.presentation.components.LastConnectionUpdatedTimestamp
import com.jwoglom.controlx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.controlx2.presentation.components.PumpSetupStageProgress
import com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCard
import com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBar
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.TherapyMetricsCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCard
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
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
    historyLogViewModel: HistoryLogViewModel? = null,
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
                .padding(horizontal = 8.dp),
            content = {
                item {
                    ServiceDisabledMessage(sendMessage = sendMessage)
                    PumpSetupStageProgress(initialSetup = false)
                    PumpSetupStageDescription(initialSetup = false)

                    LaunchedEffect(pumpLastConnectionTimestamp.value) {
                        Timber.d("pumpLastConnectionTimestamp effect: ${pumpLastConnectionTimestamp.value}")
                        sendPumpCommands(SendType.CACHED, dashboardCommands)
                    }
                }

                // Pump Status Bar with battery, connection, and cartridge
                item {
                    PumpStatusBar(middleContent = {
                        LastConnectionUpdatedTimestamp()
                    })
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        content = {
                            // Hero card showing current glucose reading
                            item {
                                val cgmReading = ds.cgmReading.observeAsState()
                                val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
                                GlucoseHeroCard(
                                    glucoseValue = cgmReading.value,
                                    deltaArrow = cgmDeltaArrow.value,
                                    modifier = Modifier.fillParentMaxWidth(0.5f)
                                )
                            }

                            // Therapy Metrics Card - IOB, COB
                            item {
                                TherapyMetricsCardFromDataStore(
                                    historyLogViewModel = historyLogViewModel,
                                    modifier = Modifier.fillParentMaxWidth(0.5f)
                                )
                            }
                        }
                    )
                }


                // CGM Chart with Vico - shows glucose, boluses, basal, carbs
                item {
                    VicoCgmChartCard(
                        historyLogViewModel = historyLogViewModel,
                        showLegend = true
                    )
                }

                // Active Therapy Card - Basal, Last Bolus, Mode
                item {
                    ActiveTherapyCardFromDataStore()
                }

                // Sensor Info Card - Sensor expiration, Transmitter status
                item {
                    SensorInfoCardFromDataStore()
                }

                // History Log Sync Progress
                historyLogViewModel?.let {
                    item {
                        HistoryLogSyncProgressBar(
                            historyLogViewModel = it,
                            replaceWithPaddingWhenComplete = true
                        )
                    }
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
    GlobalMaxBolusSettingsRequest(),
    // trigger HistoryLogFetcher
    HistoryLogStatusRequest(),
    // update notification badge
    *NotificationBundle.allRequests().toTypedArray()
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
    dataStore.notificationBundle
)

@Preview(showBackground = true)
@Composable
internal fun DashboardDefaultPreview() {
    ControlX2Theme() {
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