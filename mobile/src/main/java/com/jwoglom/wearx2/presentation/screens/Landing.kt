@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.dataStore
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.theme.Colors
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.SendType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Landing(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sectionState: LandingSection = LandingSection.DASHBOARD,
    bolusSheetState: BottomSheetValue = BottomSheetValue.Collapsed,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()

    var selectedItem by remember { mutableStateOf(sectionState) }
    val displayBolusWindow = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetState(bolusSheetState)
    )

    fun apiVersion(): ApiVersion {
        var apiVersion = PumpState.getPumpAPIVersion()
        if (apiVersion == null) {
            apiVersion = KnownApiVersion.API_V2_5.get()
        }
        return apiVersion
    }

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
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (pumpConnected.value != true) {
                        Icon(imageVector = Icons.Filled.Warning, contentDescription = "Disconnected")
                        Text("Disconnected, reconnecting...", modifier = Modifier.padding(start = 32.dp))
                    } else {
                        //Icon(painterResource(R.drawable.pump), "Pump icon", modifier = Modifier.width(32.dp))
                        Text("${deviceName.value}")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        fields.forEach { it.value = null }
                        sendPumpCommands(SendType.BUST_CACHE, commands)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = if (isSystemInDarkTheme()) TopAppBarDefaults.topAppBarColors()
                else TopAppBarDefaults.topAppBarColors(containerColor = Colors.primary),
            )
        },
        content = { innerPadding ->
            BottomSheetScaffold(
                scaffoldState = displayBolusWindow,
                sheetContent = {
                    LazyColumn(
                        contentPadding = PaddingValues(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().fillMaxHeight(0.5F),
                        content = {
                            item {
                                Line("Bolus window")
                            }
                            item {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(64.dp))
                            }
                        }
                    )
                },
                sheetPeekHeight = 0.dp,
                backgroundColor = MaterialTheme.colorScheme.background,
            ) {
                LazyColumn(
                    contentPadding = innerPadding,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    content = {
                        when (selectedItem) {
                            LandingSection.DASHBOARD -> {
                                item {
                                    if (pumpConnected.value == false) {
                                        Line("Connecting: ${setupStage.value}", bold = true)
                                    }

                                    LaunchedEffect(pumpConnected.value) {
                                        sendPumpCommands(SendType.CACHED, commands)
                                    }
                                }

                                item {
                                    val cgmReading = ds.cgmReading.observeAsState()
                                    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
                                    Line(
                                        "${cgmReading.value ?: ""} ${cgmDeltaArrow.value ?: ""}",
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
                                    val landingBasalDisplayedText = dataStore.landingBasalDisplayedText.observeAsState()

                                    LaunchedEffect (basalRate.value, basalStatus.value) {
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
                                    val landingControlIQDisplayedText = dataStore.landingControlIQDisplayedText.observeAsState()

                                    LaunchedEffect (controlIQStatus.value, controlIQMode.value) {
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
                            LandingSection.DEBUG -> {
                                item {
                                    Line("Debug")
                                }
                            }
                            LandingSection.SETTINGS -> {
                                item {
                                    Button(
                                        onClick = {
                                            sendMessage("/to-phone/force-reload", "".toByteArray())
                                        }
                                    ) {
                                        Text("Force service reload")
                                    }
                                }

                                item {
                                    Button(
                                        onClick = {
                                            Prefs(context).setPumpSetupComplete(false)
                                            coroutineScope.launch {
                                                sendMessage(
                                                    "/to-phone/app-reload",
                                                    "".toByteArray()
                                                )
                                            }
                                            navController?.navigate(Screen.PumpSetup.route)
                                        }
                                    ) {
                                        Text("Reconfigure pump")
                                    }
                                }

                                item {
                                    Button(
                                        onClick = {
                                            Prefs(context).setAppSetupComplete(false)
                                            navController?.navigate(Screen.AppSetup.route)
                                        }
                                    ) {
                                        Text("Reconfigure app")
                                    }
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            when (selectedItem) {
                LandingSection.DASHBOARD ->
                    ExtendedFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                if (displayBolusWindow.bottomSheetState.isCollapsed) {
                                    displayBolusWindow.bottomSheetState.expand()
                                } else {
                                    displayBolusWindow.bottomSheetState.collapse()
                                }
                            }
                        },
                        icon = {
                            Image(
                                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon)
                                else painterResource(R.drawable.bolus_icon_secondary),
                                "Bolus icon",
                                Modifier.size(24.dp)
                            )
                        },
                        text = {
                            Text(
                                "Bolus",
                                color = if (isSystemInDarkTheme()) Colors.primary
                                else Colors.onPrimary
                            )
                        }
                    )
                else -> {}
            }
        },
        bottomBar = {
            NavigationBar {
                LandingSection.values().forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedItem == item,
                        onClick = { selectedItem = item }
                    )
                }
            }
        }
    )
}

enum class LandingSection(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Info),
    DEBUG("Debug", Icons.Filled.Build),
    SETTINGS("Settings", Icons.Filled.Settings),
    ;
}

private fun setUpPreviewState(ds: DataStore) {
    ds.setupDeviceName.value = "tslim X2 ***789"
    ds.setupDeviceModel.value = "X2"
    ds.pumpConnected.value = true
    ds.cgmReading.value = 123
    ds.cgmDeltaArrow.value = "⬈"
    ds.batteryPercent.value = 50
    ds.iobUnits.value = 0.5
    ds.cartridgeRemainingUnits.value = 100
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BolusPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> },
                bolusSheetState = BottomSheetValue.Expanded,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> },
                sectionState = LandingSection.SETTINGS,
            )
        }
    }
}