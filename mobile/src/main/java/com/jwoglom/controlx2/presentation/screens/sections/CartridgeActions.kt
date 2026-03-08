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
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
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
    var willRestartTubingFill by remember { mutableStateOf(false) }
    var cannulaFillAmountStr by remember { mutableStateOf<String?>(null) }
    var cannulaFillAmount by remember { mutableStateOf<Double?>(null) }

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

    fun allowedCannulaFillAmount(units: Double?): Boolean = units != null && units > 0 && units <= 3.0

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cartridgeActionsCommands)
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

    LaunchedEffect(refreshing) { waitForLoaded() }

    val basalStatus = ds.basalStatus.observeAsState()
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
    val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()
    val inFillTubingMode = ds.inFillTubingMode.observeAsState()
    val fillTubingState = ds.fillTubingState.observeAsState()
    val exitFillTubingState = ds.exitFillTubingState.observeAsState()
    val fillCannulaState = ds.fillCannulaState.observeAsState()

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
                        subScreen = CartridgeSubScreen.CHANGE_CARTRIDGE
                    }
                },
                onFillTubing = {
                    refreshScope.launch {
                        ds.fillTubingState.value = null
                        ds.exitFillTubingState.value = null
                        ds.inFillTubingMode.value = false
                        willRestartTubingFill = false
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        subScreen = CartridgeSubScreen.FILL_TUBING
                    }
                },
                onFillCannula = {
                    refreshScope.launch {
                        ds.fillCannulaState.value = null
                        cannulaFillAmount = null
                        cannulaFillAmountStr = null
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        subScreen = CartridgeSubScreen.FILL_CANNULA
                    }
                },
                onBack = navigateBack,
            )

            CartridgeSubScreen.CHANGE_CARTRIDGE -> ChangeCartridgeWorkflowScreen(
                basalStatus = basalStatus.value,
                inChangeCartridgeMode = inChangeCartridgeMode.value == true,
                enterChangeCartridgeState = enterChangeCartridgeState.value,
                detectingCartridgeState = detectingCartridgeState.value,
                onBack = { subScreen = CartridgeSubScreen.MENU },
                onEnter = { sendPumpCommands(SendType.BUST_CACHE, listOf(EnterChangeCartridgeModeRequest())) },
                onExit = { sendPumpCommands(SendType.BUST_CACHE, listOf(ExitChangeCartridgeModeRequest())) },
                onDone = { subScreen = CartridgeSubScreen.MENU },
            )

            CartridgeSubScreen.FILL_TUBING -> FillTubingWorkflowScreen(
                basalStatus = basalStatus.value,
                inFillTubingMode = inFillTubingMode.value == true,
                fillTubingButtonDown = fillTubingState.value?.buttonDown,
                exitFillTubingState = exitFillTubingState.value,
                willRestartTubingFill = willRestartTubingFill,
                onBack = { subScreen = CartridgeSubScreen.MENU },
                onBeginFillTubing = { sendPumpCommands(SendType.BUST_CACHE, listOf(EnterFillTubingModeRequest())) },
                onFinishFillTubing = { restart ->
                    willRestartTubingFill = restart
                    sendPumpCommands(SendType.BUST_CACHE, listOf(ExitFillTubingModeRequest()))
                },
                onRestartFill = {
                    ds.exitFillTubingState.value = null
                    willRestartTubingFill = false
                    sendPumpCommands(SendType.BUST_CACHE, listOf(EnterFillTubingModeRequest()))
                },
                onDone = { subScreen = CartridgeSubScreen.MENU },
            )

            CartridgeSubScreen.FILL_CANNULA -> FillCannulaWorkflowScreen(
                basalStatus = basalStatus.value,
                fillCannulaState = fillCannulaState.value,
                cannulaFillAmount = cannulaFillAmount,
                cannulaFillAmountStr = cannulaFillAmountStr,
                allowedCannulaFillAmount = ::allowedCannulaFillAmount,
                onBack = { subScreen = CartridgeSubScreen.MENU },
                onDone = { subScreen = CartridgeSubScreen.MENU },
                onCannulaAmountChange = { str, amount ->
                    cannulaFillAmountStr = str
                    cannulaFillAmount = amount
                },
                onSendFillRequest = {
                    cannulaFillAmount?.let {
                        if (allowedCannulaFillAmount(it)) {
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                listOf(FillCannulaRequest(InsulinUnit.from1To1000(it).toInt()))
                            )
                        }
                    }
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
