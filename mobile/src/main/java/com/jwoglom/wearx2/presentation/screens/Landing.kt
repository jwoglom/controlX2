@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.sections.BolusWindow
import com.jwoglom.wearx2.presentation.screens.sections.Dashboard
import com.jwoglom.wearx2.presentation.screens.sections.Debug
import com.jwoglom.wearx2.presentation.screens.sections.Settings
import com.jwoglom.wearx2.presentation.screens.sections.dashboardCommands
import com.jwoglom.wearx2.presentation.screens.sections.dashboardFields
import com.jwoglom.wearx2.presentation.theme.Colors
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import com.jwoglom.wearx2.shared.util.SendType
import kotlinx.coroutines.launch

@Composable
fun Landing(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sectionState: LandingSection = LandingSection.DASHBOARD,
    bolusSheetState: BottomSheetValue = BottomSheetValue.Collapsed,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()

    var selectedItem by remember { mutableStateOf(sectionState) }
    val displayBolusWindow = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetState(bolusSheetState)
    )

    fun showBolusWindow(): Boolean {
        return displayBolusWindow.bottomSheetState.isExpanded ||
            displayBolusWindow.bottomSheetState.isAnimationRunning ||
            bolusSheetState == BottomSheetValue.Expanded
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (pumpConnected.value != true) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Disconnected",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            when {
                                Prefs(context).serviceEnabled() -> "Disconnected, reconnecting..."
                                else -> "Service disabled"
                             },
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.pump),
                            tint = Color.Unspecified,
                            contentDescription = "Pump icon",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "${deviceName.value}",
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        dashboardFields.forEach { it.value = null }
                        sendPumpCommands(SendType.BUST_CACHE, dashboardCommands)
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
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.7F),
                        content = {
                            item {
                                // Fix for Android Studio preview which renders the scaffold at the
                                // top of the screen instead of half-way.
                                if (bolusSheetState == BottomSheetValue.Expanded) {
                                    Spacer(Modifier.size(100.dp))
                                }
                                if (showBolusWindow()) {
                                    BolusWindow()
                                }
                            }
                            item {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                )
                            }
                        }
                    )
                },
                sheetPeekHeight = 0.dp,
                backgroundColor = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxHeight()) {
                    when (selectedItem) {
                        LandingSection.DASHBOARD -> {
                            Dashboard(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                            )
                        }
                        LandingSection.DEBUG -> {
                            Debug(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                            )
                        }
                        LandingSection.SETTINGS -> {
                            Settings(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                            )
                        }
                    }
                }
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
                                if (!showBolusWindow()) {
                                    Image(
                                        if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon)
                                        else painterResource(R.drawable.bolus_icon_secondary),
                                        "Bolus icon",
                                        Modifier.size(24.dp)
                                    )
                                } else {
                                    Image(
                                        if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_x)
                                        else painterResource(R.drawable.bolus_x_secondary),
                                        "Cancel bolus icon",
                                        Modifier.size(24.dp)
                                    )
                                }
                            },
                            text = {
                                Text(
                                    if (!showBolusWindow()) "Bolus" else "Cancel",
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

fun setUpPreviewState(ds: DataStore) {
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
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BolusPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                bolusSheetState = BottomSheetValue.Expanded,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sectionState = LandingSection.DEBUG,
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
                sendPumpCommands = { _, _ -> },
                sectionState = LandingSection.SETTINGS,
            )
        }
    }
}