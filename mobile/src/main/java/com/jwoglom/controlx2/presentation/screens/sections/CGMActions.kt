@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.sections.components.DexcomG6SensorCode
import com.jwoglom.controlx2.presentation.screens.sections.components.DexcomG6TransmitterCode
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.control.SetDexcomG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetG6TransmitterIdRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StartDexcomG6SensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopDexcomCGMSensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GetSavedG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GetSavedG7PairingCodeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun CGMActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    _startG6CgmSessionMenuState: Boolean = false,
    _stopG6CgmSessionMenuState: Boolean = false,
    _startG7CgmSessionMenuState: Boolean = false,
    _stopG7CgmSessionMenuState: Boolean = false,
    navigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var showStartG6CgmSessionMenu by remember { mutableStateOf(_startG6CgmSessionMenuState) }
    var startG6CgmSessionInProgressTxId by remember { mutableStateOf<String?>(null) }
    var showStopG6CgmSessionMenu by remember { mutableStateOf(_stopG6CgmSessionMenuState) }

    var showStartG7CgmSessionMenu by remember { mutableStateOf(_startG7CgmSessionMenuState) }
    var showStopG7CgmSessionMenu by remember { mutableStateOf(_stopG7CgmSessionMenuState) }

    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cgmActionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = cgmActionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("CGMActions loading: remaining ${nullFields.size}: ${cgmActionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("CGMActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("Actions loading done: ${cgmActionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading CGMActions with force")
        refreshing = true

        cgmActionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading CGMActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading CGMActions from interval")
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
                .padding(horizontal = 0.dp),
            content = {
                item {
                    HeaderLine("CGM Actions")
                    Divider()

                    val model = determinePumpModel(deviceName.value ?: "")
                    if (model == KnownDeviceModel.TSLIM_X2) {
                        Line("CGM control is not supported on this device model (${model}).")
                        Line("")
                    }
                }

                item {
                    val cgmSessionState = ds.cgmSessionState.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> "Dexcom G6: Stop CGM Sensor"
                                    CGMSessionState.STOPPED -> "Dexcom G6: Start CGM Sensor"
                                    else -> "Dexcom G6 CGM Sensor State: ${cgmSessionState.value?.str}"
                                }
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> Icon(Icons.Filled.Close, contentDescription = null)
                                    CGMSessionState.STOPPED -> Icon(Icons.Filled.Settings, contentDescription = null)
                                    else -> Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable {
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> { showStopG6CgmSessionMenu = true }
                                    CGMSessionState.STOPPED -> { showStartG6CgmSessionMenu = true }
                                    else -> {}
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showStartG6CgmSessionMenu,
                            onDismissRequest = { showStartG6CgmSessionMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val cgmSetupG6TxId = ds.cgmSetupG6TxId.observeAsState()
                            val cgmSetupG6SensorCode = ds.cgmSetupG6SensorCode.observeAsState()

                            AlertDialog(
                                onDismissRequest = {showStartG6CgmSessionMenu = false},
                                title = {
                                    Text("Start G6 CGM Session")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = innerPadding,
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            if (startG6CgmSessionInProgressTxId == null) {
                                                item {
                                                    Text("To start the CGM session, confirm the transmitter ID and sensor code:")
                                                    Text("\n")
                                                }

                                                item {
                                                    DexcomG6TransmitterCode(
                                                        title = "Transmitter ID",
                                                        value = cgmSetupG6TxId.value,
                                                        onValueChange = { it ->
                                                            ds.cgmSetupG6TxId.value = it
                                                        }
                                                    )
                                                }

                                                item {
                                                    Text("\n")
                                                    Text("To connect to an existing G6 CGM session or if no code is available, use '0000'")
                                                    Text("\n")
                                                }

                                                item {
                                                    DexcomG6SensorCode(
                                                        title = "Sensor Code",
                                                        value = cgmSetupG6SensorCode.value,
                                                        onValueChange = { it ->
                                                            ds.cgmSetupG6SensorCode.value = it
                                                        }
                                                    )
                                                }
                                            } else {
                                                item {
                                                    Text("Setting Transmitter ID to ${startG6CgmSessionInProgressTxId}...")
                                                }
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStartG6CgmSessionMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            refreshScope.launch {
                                                startG6CgmSessionInProgressTxId = cgmSetupG6TxId.value
                                                ds.cgmSetupG6SensorCode.value = null
                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    SetG6TransmitterIdRequest(startG6CgmSessionInProgressTxId)
                                                ))

                                                run repeatBlock@{
                                                    repeat(3) {
                                                        withContext(Dispatchers.IO) {
                                                            Thread.sleep(250)
                                                        }
                                                        if (cgmSetupG6SensorCode.value == startG6CgmSessionInProgressTxId) {
                                                            return@repeatBlock
                                                        }
                                                    }
                                                }

                                                val sensorCode = cgmSetupG6SensorCode.value?.toIntOrNull() ?: 0

                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    StartDexcomG6SensorSessionRequest(sensorCode)
                                                ))

                                                showStartG6CgmSessionMenu = false
                                                withContext(Dispatchers.IO) {
                                                    Thread.sleep(250)
                                                }
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(CGMStatusRequest())
                                                )
                                            }
                                        },
                                        enabled = startG6CgmSessionInProgressTxId == null,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Start Sensor")
                                    }
                                }
                            )

                        }

                        DropdownMenu(
                            expanded = showStopG6CgmSessionMenu,
                            onDismissRequest = { showStopG6CgmSessionMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {showStopG6CgmSessionMenu = false},
                                title = {
                                    Text("Stop G6 CGM Session")
                                },
                                text = {
                                    Text("The Dexcom G6 sensor will be stopped.")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStopG6CgmSessionMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            refreshScope.launch {
                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    StopDexcomCGMSensorSessionRequest()
                                                ))

                                                showStopG6CgmSessionMenu = false
                                                repeat(3) {
                                                    Thread.sleep(250)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(CGMStatusRequest())
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Stop Sensor")
                                    }
                                }
                            )

                        }
                    }
                }

                item {
                    Line("\n")
                }


                item {
                    val cgmSessionState = ds.cgmSessionState.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> "Dexcom G7: Stop CGM Sensor"
                                    CGMSessionState.STOPPED -> "Dexcom G7: Start CGM Sensor"
                                    else -> "Dexcom G7 CGM Sensor State: ${cgmSessionState.value?.str}"
                                }
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> Icon(Icons.Filled.Close, contentDescription = null)
                                    CGMSessionState.STOPPED -> Icon(Icons.Filled.Settings, contentDescription = null)
                                    else -> Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable {
                                when (cgmSessionState.value) {
                                    CGMSessionState.ACTIVE -> { showStopG7CgmSessionMenu = true }
                                    CGMSessionState.STOPPED -> { showStartG7CgmSessionMenu = true }
                                    else -> {}
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showStartG7CgmSessionMenu,
                            onDismissRequest = { showStartG7CgmSessionMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val cgmSetupG7SensorCode = ds.cgmSetupG7SensorCode.observeAsState()

                            AlertDialog(
                                onDismissRequest = {showStartG7CgmSessionMenu = false},
                                title = {
                                    Text("Start G7 CGM Session")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = innerPadding,
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            item {
                                                Text("To start the CGM session, enter the sensor code:")
                                                Text("\n")
                                            }

                                            item {
                                                DexcomG6SensorCode(
                                                    title = "Sensor Code",
                                                    value = cgmSetupG7SensorCode.value,
                                                    onValueChange = { it ->
                                                        ds.cgmSetupG7SensorCode.value = it
                                                    }
                                                )
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStartG7CgmSessionMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            refreshScope.launch {
                                                cgmSetupG7SensorCode.value?.let {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE, listOf(
                                                            SetDexcomG7PairingCodeRequest(it.toInt())
                                                        )
                                                    )
                                                }

                                                run repeatBlock@{
                                                    repeat(3) {
                                                        withContext(Dispatchers.IO) {
                                                            Thread.sleep(250)
                                                        }
                                                        sendPumpCommands(
                                                            SendType.BUST_CACHE, listOf(
                                                                GetSavedG7PairingCodeRequest()
                                                            )
                                                        )
                                                        if (ds.savedG7PairingCode.value.toString() == startG6CgmSessionInProgressTxId) {
                                                            return@repeatBlock
                                                        }
                                                    }
                                                }

                                                showStartG6CgmSessionMenu = false
                                            }
                                        },
                                        enabled = cgmSetupG7SensorCode.value != null,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Start Sensor")
                                    }
                                }
                            )
                        }
                    }
                }


                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Back"
                                )
                            },
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.ArrowBack, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateBack()
                            }
                        )
                    }
                }
            }
        )
    }
}
val cgmActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    CGMStatusRequest()
)

val cgmActionsFields = listOf(
    dataStore.cgmSessionState
)

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            CGMActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewCgmStart() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.cgmSetupG6TxId.value = "ABC123"
            LocalDataStore.current.cgmSetupG6SensorCode.value = "1234"
            CGMActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _startG6CgmSessionMenuState = true,
                navigateBack = {},
            )
        }
    }
}