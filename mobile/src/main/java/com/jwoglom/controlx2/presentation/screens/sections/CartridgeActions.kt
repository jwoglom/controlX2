@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.UnknownMobiOpcode20Request
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    val coroutineScope = rememberCoroutineScope()

    var showChangeCartridgeMenu by remember { mutableStateOf(_changeCartridgeMenuState) }
    var showFillTubingMenu by remember { mutableStateOf(_fillTubingMenuState) }
    var showFillCannulaMenu by remember { mutableStateOf(_fillCannulaMenuState) }

    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cartridgeActionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = cartridgeActionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("CartridgeActions loading: remaining ${nullFields.size}: ${cartridgeActionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("CartridgeActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("CartridgeActions loading done: ${cartridgeActionsFields.map { it.value }}")
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

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading CartridgeActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading CartridgeActions from interval")
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
                    HeaderLine("Cartridge Actions")
                    Divider()

                    val model = determinePumpModel(deviceName.value ?: "")
                    if (model == KnownDeviceModel.TSLIM_X2) {
                        Line("Insulin control is not supported on this device model (${model}).")
                        Line("")
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineText = {
                                Text(
                                    "Change Cartridge"
                                )
                            },
                            supportingText = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                refreshScope.launch {
                                    ds.enterChangeCartridgeState.value = null
                                    ds.detectingCartridgeState.value = null
                                    sendPumpCommands(
                                        SendType.BUST_CACHE, listOf(
                                            TimeSinceResetRequest()
                                        )
                                    )
                                    showChangeCartridgeMenu = true
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showChangeCartridgeMenu,
                            onDismissRequest = {  },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        ) {
                            val basalStatus = ds.basalStatus.observeAsState()
                            val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
                            val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
                            val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()

                            AlertDialog(
                                onDismissRequest = {
                                    if (inChangeCartridgeMode.value == false) {
                                        showChangeCartridgeMenu = false
                                    }
                                },
                                title = {
                                    Text("Change Cartridge")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = innerPadding,
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            if (detectingCartridgeState.value != null) {
                                                if (detectingCartridgeState.value?.isComplete == true) {
                                                    item {
                                                        Text("Cartridge change complete.")
                                                        Text("\n")
                                                        Text("${detectingCartridgeState.value?.percentComplete}% complete")
                                                    }
                                                } else {
                                                    item {
                                                        Text("Detecting insulin in the cartridge...")
                                                        Text("\n")
                                                        Text("${detectingCartridgeState.value?.percentComplete}% complete")
                                                    }
                                                }
                                            } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                                                item {
                                                    Text("You can now remove the cartridge from the pump.")
                                                    Text("\n")
                                                    Text("When you've inserted a new cartridge, press Cartridge Inserted below.")
                                                }
                                            } else if (inChangeCartridgeMode.value == true) {
                                                item {
                                                    Text("Preparing to change cartridge...")
                                                    Text("\n")
                                                }
                                            } else if (basalStatus.value == BasalStatus.PUMP_SUSPENDED) {
                                                item {
                                                    Text("Disconnect your pump from your body/site and press Change Cartridge below.")
                                                    Text("\n")
                                                }
                                            } else {
                                                item {
                                                    Text("Before changing your cartridge, stop delivery of insulin.")
                                                    Text("\n")
                                                }
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    if (inChangeCartridgeMode.value == true) {
                                        /* */
                                    } else if (detectingCartridgeState.value?.isComplete == true) {
                                        /* */
                                    } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                                        /* */
                                    } else {
                                        TextButton(
                                            onClick = {
                                                showChangeCartridgeMenu = false
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                },
                                confirmButton = {
                                    if (detectingCartridgeState.value?.isComplete == true) {
                                        TextButton(
                                            onClick = {
                                                showChangeCartridgeMenu = false
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Done")
                                        }
                                    } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                                        TextButton(
                                            onClick = {
                                                refreshScope.launch {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE, listOf(
                                                            ExitChangeCartridgeModeRequest()
                                                        )
                                                    )

                                                }
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Cartridge Inserted")
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                refreshScope.launch {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE, listOf(
                                                            EnterChangeCartridgeModeRequest()
                                                        )
                                                    )

                                                }
                                            },
                                            enabled = basalStatus.value == BasalStatus.PUMP_SUSPENDED,
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Change Cartridge")
                                        }
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
                            headlineText = {
                                Text(
                                    "Fill Tubing"
                                )
                            },
                            supportingText = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                refreshScope.launch {
                                    ds.fillTubingState.value = null
                                    ds.exitFillTubingState.value = null
                                    ds.inFillTubingMode.value = false
                                    sendPumpCommands(
                                        SendType.BUST_CACHE, listOf(
                                            TimeSinceResetRequest()
                                        )
                                    )
                                    showFillTubingMenu = true
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showFillTubingMenu,
                            onDismissRequest = { showFillTubingMenu = false },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        ) {
                            val basalStatus = ds.basalStatus.observeAsState()
                            val inFillTubingMode = ds.inFillTubingMode.observeAsState()
                            val fillTubingState = ds.fillTubingState.observeAsState()
                            val exitFillTubingState = ds.exitFillTubingState.observeAsState()

                            AlertDialog(
                                onDismissRequest = {
                                    if (inFillTubingMode.value == false) {
                                        showFillTubingMenu = false
                                    }
                                },
                                title = {
                                    Text("Fill Tubing")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = innerPadding,
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            if (exitFillTubingState.value != null) {
                                                if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                                                    item {
                                                        Text("Successfully filled tubing. Press Done to exit.")
                                                    }
                                                } else {
                                                    item {
                                                        Text("Please wait, finalizing tubing fill...\n\n${exitFillTubingState.value?.state} (${exitFillTubingState.value?.stateId})")
                                                    }
                                                }
                                            } else if (inFillTubingMode.value == true) {
                                                item {
                                                    Text("Hold down the pump button to fill insulin through the tubing.\n\n")
                                                }
                                                if (fillTubingState.value == null) {
                                                    item {
                                                        Text("You haven't filled any insulin through the tubing yet.")
                                                    }
                                                } else if (fillTubingState.value?.buttonDown == true) {
                                                    item {
                                                        Text("FILLING... continue to hold down button until you see insulin drops at the cannula.")
                                                    }
                                                } else if (fillTubingState.value?.buttonDown == false) {
                                                    item {
                                                        Text("STOPPED FILLING... do you see insulin drops at the cannula?\n\n")
                                                        Text("If you're done filling, press Complete Fill below. Otherwise, hold down the pump button again.")
                                                    }
                                                }
                                            } else if (basalStatus.value == BasalStatus.PUMP_SUSPENDED) {
                                                item {
                                                    Text("Disconnect your pump from your body/site and press Begin Tubing Fill below.")
                                                    Text("\n")
                                                }
                                            } else {
                                                item {
                                                    Text("Before filling your tubing, stop delivery of insulin.")
                                                    Text("\n")
                                                }
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    if (inFillTubingMode.value == true) {
                                    } else {
                                        TextButton(
                                            onClick = {
                                                showFillTubingMenu = false
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                },
                                confirmButton = {
                                    if (exitFillTubingState.value != null) {
                                        if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                                            TextButton(
                                                onClick = {
                                                    showFillTubingMenu = false
                                                },
                                                enabled = basalStatus.value == BasalStatus.PUMP_SUSPENDED,
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                Text("Done")
                                            }
                                        }
                                    } else if (inFillTubingMode.value == true) {
                                        if (fillTubingState.value?.buttonDown == false) {
                                            TextButton(
                                                onClick = {
                                                    refreshScope.launch {
                                                        sendPumpCommands(
                                                            SendType.BUST_CACHE, listOf(
                                                                ExitFillTubingModeRequest()
                                                            )
                                                        )

                                                    }
                                                },
                                                enabled = basalStatus.value == BasalStatus.PUMP_SUSPENDED,
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                Text("Complete Fill")
                                            }
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                refreshScope.launch {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE, listOf(
                                                            EnterFillTubingModeRequest()
                                                        )
                                                    )

                                                }
                                            },
                                            enabled = basalStatus.value == BasalStatus.PUMP_SUSPENDED,
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Begin Tubing Fill")
                                        }
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.BottomStart)
                    ) {
                        ListItem(
                            headlineText = {
                                Text(
                                    "Back"
                                )
                            },
                            supportingText = {
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
val cartridgeActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    CGMStatusRequest(),
    TimeSinceResetRequest()
)

val cartridgeActionsFields = listOf(
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
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewChangeCartridge_InsulinNotStopped() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.ON
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
private fun DefaultPreviewChangeCartridge_InsulinStopped() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
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