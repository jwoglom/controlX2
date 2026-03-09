@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.CartridgeActionsMenuScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.ChangeCartridgeWorkflowScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillCannulaWorkflowScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillTubingWorkflowScreen
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

private enum class CartridgeSubScreen {
    MENU,
    CHANGE_CARTRIDGE,
    FILL_TUBING,
    FILL_CANNULA,
}

@Composable
fun CartridgeActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    _changeCartridgeMenuState: Boolean = false,
    _fillTubingMenuState: Boolean = false,
    _fillCannulaMenuState: Boolean = false,
    navigateBack: () -> Unit
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var notificationsRefreshing by remember { mutableStateOf(false) }
    var tubingExitRequested by remember { mutableStateOf(false) }
    var cannulaPrimeAmount by remember { mutableStateOf(0.3) }

    var subScreen by remember {
        mutableStateOf(
            when {
                _changeCartridgeMenuState -> CartridgeSubScreen.CHANGE_CARTRIDGE
                _fillTubingMenuState -> CartridgeSubScreen.FILL_TUBING
                _fillCannulaMenuState -> CartridgeSubScreen.FILL_CANNULA
                else -> CartridgeSubScreen.MENU
            }
        )
    }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cartridgeActionsCommands)
    }

    fun refreshNotifications(type: SendType = SendType.BUST_CACHE) = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        notificationsRefreshing = true
        sendPumpCommands(type, cartridgeNotificationCommands)
        delay(500)
        notificationsRefreshing = false
    }

    fun requestHomeMirrorBurst() = refreshScope.launch {
        repeat(5) {
            delay(1000)
            sendPumpCommands(SendType.BUST_CACHE, listOf(HomeScreenMirrorRequest()))
        }
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = cartridgeActionsFields.filter { field -> field.value == null }.toSet()
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
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading CartridgeActions with force")
        refreshing = true
        cartridgeActionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = { refreshScope.cancel() }) {
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(subScreen, intervalOf(15)) {
        if (subScreen == CartridgeSubScreen.CHANGE_CARTRIDGE || subScreen == CartridgeSubScreen.FILL_TUBING) {
            refreshNotifications(SendType.STANDARD)
        }
    }

    LaunchedEffect(refreshing) { waitForLoaded() }

    val basalStatus = ds.basalStatus.observeAsState()
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
    val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()
    val inFillTubingMode = ds.inFillTubingMode.observeAsState()
    val fillTubingState = ds.fillTubingState.observeAsState()
    val exitFillTubingState = ds.exitFillTubingState.observeAsState()
    val fillCannulaState = ds.fillCannulaState.observeAsState()
    val notificationBundle = ds.notificationBundle.observeAsState()
    val activeNotifications = notificationBundle.value?.get() ?: emptyList()

    LaunchedEffect(exitFillTubingState.value?.state) {
        if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
            tubingExitRequested = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(state)) {
        PullRefreshIndicator(refreshing, state, Modifier.align(Alignment.TopCenter).zIndex(10f))

        when (subScreen) {
            CartridgeSubScreen.MENU -> CartridgeActionsMenuScreen(
                innerPadding = innerPadding,
                deviceName = deviceName.value ?: "",
                onChangeCartridge = {
                    refreshScope.launch {
                        ds.enterChangeCartridgeState.value = null
                        ds.detectingCartridgeState.value = null
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        refreshNotifications()
                        subScreen = CartridgeSubScreen.CHANGE_CARTRIDGE
                    }
                },
                onFillTubing = {
                    refreshScope.launch {
                        ds.fillTubingState.value = null
                        ds.exitFillTubingState.value = null
                        ds.inFillTubingMode.value = false
                        tubingExitRequested = false
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        refreshNotifications()
                        subScreen = CartridgeSubScreen.FILL_TUBING
                    }
                },
                onFillCannula = {
                    refreshScope.launch {
                        ds.fillCannulaState.value = null
                        cannulaPrimeAmount = 0.3
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        subScreen = CartridgeSubScreen.FILL_CANNULA
                    }
                },
                onBack = navigateBack,
            )

            CartridgeSubScreen.CHANGE_CARTRIDGE -> ChangeCartridgeWorkflowScreen(
                innerPadding = innerPadding,
                basalStatus = basalStatus.value,
                inChangeCartridgeMode = inChangeCartridgeMode.value == true,
                enterChangeCartridgeState = enterChangeCartridgeState.value,
                detectingCartridgeState = detectingCartridgeState.value,
                activeNotifications = activeNotifications,
                notificationsRefreshing = notificationsRefreshing,
                sendPumpCommands = sendPumpCommands,
                refreshNotifications = { refreshNotifications() },
                onDismiss = { subScreen = CartridgeSubScreen.MENU },
                onSuspend = {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(SuspendPumpingRequest()))
                    requestHomeMirrorBurst()
                },
                onEnter = { sendPumpCommands(SendType.BUST_CACHE, listOf(EnterChangeCartridgeModeRequest())) },
                onExit = { sendPumpCommands(SendType.BUST_CACHE, listOf(ExitChangeCartridgeModeRequest())) },
                onDone = { subScreen = CartridgeSubScreen.MENU },
                onCancelInProgress = { sendPumpCommands(SendType.BUST_CACHE, listOf(ExitChangeCartridgeModeRequest())) },
            )

            CartridgeSubScreen.FILL_TUBING -> FillTubingWorkflowScreen(
                innerPadding = innerPadding,
                basalStatus = basalStatus.value,
                inFillTubingMode = inFillTubingMode.value == true,
                fillTubingButtonDown = fillTubingState.value?.buttonDown,
                exitFillTubingState = exitFillTubingState.value,
                exitRequested = tubingExitRequested,
                activeNotifications = activeNotifications,
                notificationsRefreshing = notificationsRefreshing,
                sendPumpCommands = sendPumpCommands,
                refreshNotifications = { refreshNotifications() },
                onDismiss = { subScreen = CartridgeSubScreen.MENU },
                onSuspend = {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(SuspendPumpingRequest()))
                    requestHomeMirrorBurst()
                },
                onBeginFillTubing = {
                    tubingExitRequested = false
                    sendPumpCommands(SendType.BUST_CACHE, listOf(EnterFillTubingModeRequest()))
                },
                onFinishFillTubing = {
                    tubingExitRequested = true
                    sendPumpCommands(SendType.BUST_CACHE, listOf(ExitFillTubingModeRequest()))
                },
                onDone = { subScreen = CartridgeSubScreen.MENU },
                onCancelInProgress = {
                    tubingExitRequested = true
                    sendPumpCommands(SendType.BUST_CACHE, listOf(ExitFillTubingModeRequest()))
                },
            )

            CartridgeSubScreen.FILL_CANNULA -> FillCannulaWorkflowScreen(
                innerPadding = innerPadding,
                basalStatus = basalStatus.value,
                fillCannulaState = fillCannulaState.value,
                primeAmount = cannulaPrimeAmount,
                onPrimeAmountChange = { cannulaPrimeAmount = it },
                onDismiss = { subScreen = CartridgeSubScreen.MENU },
                onSuspend = {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(SuspendPumpingRequest()))
                    requestHomeMirrorBurst()
                },
                onResume = {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(ResumePumpingRequest()))
                    requestHomeMirrorBurst()
                },
                onDone = { subScreen = CartridgeSubScreen.MENU },
                onSendFillRequest = {
                    sendPumpCommands(
                        SendType.BUST_CACHE,
                        listOf(FillCannulaRequest(InsulinUnit.from1To1000(cannulaPrimeAmount).toInt()))
                    )
                }
            )
        }
    }
}


val cartridgeActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    CGMStatusRequest(),
    TimeSinceResetRequest(),
    LoadStatusRequest()
)

val cartridgeNotificationCommands = listOf(
    HomeScreenMirrorRequest(),
    *NotificationBundle.allRequests().toTypedArray(),
)

val cartridgeActionsFields = listOf(
    dataStore.cgmSessionState
)

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            CartridgeActions(sendMessage = { _, _ -> }, sendPumpCommands = { _, _ -> }, navigateBack = {})
        }
    }
}


@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_RESUMED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _changeCartridgeMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _changeCartridgeMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewFillTubing_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _fillTubingMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewFillCannula_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _fillCannulaMenuState = true,
                navigateBack = {},
            )
        }
    }
}
