@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.screens.sections.Actions
import com.jwoglom.controlx2.presentation.screens.sections.BolusWindow
import com.jwoglom.controlx2.presentation.screens.sections.CGMActions
import com.jwoglom.controlx2.presentation.screens.sections.CartridgeActions
import com.jwoglom.controlx2.presentation.screens.sections.Dashboard
import com.jwoglom.controlx2.presentation.screens.sections.Debug
import com.jwoglom.controlx2.presentation.screens.sections.Notifications
import com.jwoglom.controlx2.presentation.screens.sections.ProfileActions
import com.jwoglom.controlx2.presentation.screens.sections.Settings
import com.jwoglom.controlx2.presentation.screens.sections.TempRateWindow
import com.jwoglom.controlx2.presentation.screens.sections.SoundSettingsActions
import com.jwoglom.controlx2.presentation.screens.sections.dashboardCommands
import com.jwoglom.controlx2.presentation.screens.sections.dashboardFields
import com.jwoglom.controlx2.presentation.screens.sections.resetBolusDataStoreState
import com.jwoglom.controlx2.presentation.screens.sections.resetTempRateDataStoreState
import com.jwoglom.controlx2.presentation.theme.Colors
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.util.SendType
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun Landing(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse) -> Unit,
    sendServiceBolusCancel: () -> Unit,
    sectionState: LandingSection = LandingSection.DASHBOARD,
    bottomScaffoldDisplayState: BottomSheetValue = BottomSheetValue.Collapsed,
    _bottomScaffoldState: BottomScaffoldState = BottomScaffoldState.NONE,
    historyLogViewModel: HistoryLogViewModel? = null,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()
    val notificationBundle = ds.notificationBundle.observeAsState()


    var selectedItem by remember { mutableStateOf(sectionState) }
    val displayBottomScaffold = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetState(bottomScaffoldDisplayState, density=Density(context))
    )
    var bottomScaffoldState by remember { mutableStateOf(_bottomScaffoldState) }

    fun showBottomScaffold(): Boolean {
        return displayBottomScaffold.bottomSheetState.isExpanded ||
            _bottomScaffoldState != BottomScaffoldState.NONE
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
                scaffoldState = displayBottomScaffold,
                sheetContent = {
                    LazyColumn(
                        contentPadding = PaddingValues(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.7F)
                            .let {
                                if (isSystemInDarkTheme()) {
                                    it.background(MaterialTheme.colorScheme.onBackground)
                                }
                                it
                            },
                        content = {
                            item {
                                // Fix for Android Studio preview which renders the scaffold at the
                                // top of the screen instead of half-way.
                                if (bottomScaffoldDisplayState == BottomSheetValue.Expanded) {
                                    Spacer(Modifier.size(100.dp))
                                }
                                if (bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW) {
                                    BolusWindow(
                                        sendPumpCommands = sendPumpCommands,
                                        sendServiceBolusRequest = sendServiceBolusRequest,
                                        sendServiceBolusCancel = sendServiceBolusCancel,
                                        closeWindow = {
                                            coroutineScope.launch {
                                                displayBottomScaffold.bottomSheetState.collapse()
                                                resetBolusDataStoreState(dataStore)
                                                bottomScaffoldState = BottomScaffoldState.NONE
                                            }
                                        }
                                    )
                                } else {
                                    resetBolusDataStoreState(dataStore)
                                }
                                if (bottomScaffoldState == BottomScaffoldState.TEMP_RATE_WINDOW) {
                                    TempRateWindow(
                                        sendPumpCommands = sendPumpCommands,
                                        closeWindow = {
                                            coroutineScope.launch {
                                                displayBottomScaffold.bottomSheetState.collapse()
                                                bottomScaffoldState = BottomScaffoldState.NONE
                                                resetTempRateDataStoreState(dataStore)
                                            }
                                        }
                                    )
                                } else {
                                    resetTempRateDataStoreState(dataStore)
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
                                historyLogViewModel = historyLogViewModel,
                            )
                        }

                        LandingSection.NOTIFICATIONS -> {
                            Notifications(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                            )
                        }

                        LandingSection.ACTIONS -> {
                            Actions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                openTempRateWindow = {
                                    coroutineScope.launch {
                                        bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW
                                        displayBottomScaffold.bottomSheetState.expand()
                                    }
                                },
                                navigateToSection = { section -> selectedItem = section }
                            )
                        }

                        LandingSection.CGM_ACTIONS -> {
                            CGMActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }

                        LandingSection.CARTRIDGE_ACTIONS -> {
                            CartridgeActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }

                        LandingSection.PROFILE_ACTIONS -> {
                            ProfileActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }
                        LandingSection.SOUND_SETTINGS_ACTIONS -> {
                            SoundSettingsActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
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
                                navigateToDebugOptions = {
                                    selectedItem = LandingSection.DEBUG
                                }
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
                                    if (displayBottomScaffold.bottomSheetState.isCollapsed) {
                                        bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW
                                        displayBottomScaffold.bottomSheetState.expand()

                                    } else {
                                        displayBottomScaffold.bottomSheetState.collapse()
                                        bottomScaffoldState = BottomScaffoldState.NONE
                                    }
                                }
                            },
                            icon = {
                                if (!showBottomScaffold() || bottomScaffoldState != BottomScaffoldState.BOLUS_WINDOW) {
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
                                    if (!showBottomScaffold() || bottomScaffoldState != BottomScaffoldState.BOLUS_WINDOW) "Bolus" else "Cancel",
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
                LandingSection.values()
                    .filter { item -> item.showInNav }
                    .forEach { item ->
                        NavigationBarItem(
                            icon = {
                                BadgedBox(
                                    badge = {
                                        notificationBundle.value?.get()?.count()?.let { count ->
                                            if (item.label == LandingSection.NOTIFICATIONS.label && count > 0) {
                                                Badge(
                                                    containerColor = Color.Red,
                                                    contentColor = Color.White
                                                ) {
                                                    Text("$count")
                                                }
                                            }
                                        }
                                    },
                                    content = {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                )
                           },
                            label = { Text(item.label) },
                            selected = selectedItem.label == item.label,
                            onClick = { selectedItem = item }
                        )
                    }
            }
        }
    )
}

// HACK: subpages should have the same label as an item appearing in the nav
// so that item appears as selected when it is navigated to within the app
enum class LandingSection(val label: String, val icon: ImageVector, val showInNav: Boolean) {
    DASHBOARD("Dashboard", Icons.Filled.Info, true),

    NOTIFICATIONS("Notifications", Icons.Filled.Notifications, true),

    ACTIONS("Actions", Icons.Filled.Create, true),
    CGM_ACTIONS("Actions", Icons.Filled.Create, false),
    CARTRIDGE_ACTIONS("Actions", Icons.Filled.Create, false),
    PROFILE_ACTIONS("Profiles", Icons.Filled.Create, false),
    SOUND_SETTINGS_ACTIONS("Profiles", Icons.Filled.Create, false),

    SETTINGS("Settings", Icons.Filled.Settings, true),
    DEBUG("Settings", Icons.Filled.Settings, false),
    ;
}

enum class BottomScaffoldState {
    NONE,
    BOLUS_WINDOW,
    TEMP_RATE_WINDOW
}

fun setUpPreviewState(ds: DataStore) {
    ds.setupDeviceName.value = "tslim X2 ***789"
    ds.setupDeviceModel.value = "X2"
    ds.pumpConnected.value = true
    ds.pumpLastConnectionTimestamp.value = Instant.now().minusSeconds(120)
    ds.cgmReading.value = 123
    ds.cgmDeltaArrow.value = "⬈"
    ds.batteryPercent.value = 50
    ds.iobUnits.value = 0.5
    ds.cartridgeRemainingUnits.value = 100
    ds.basalStatus.value = BasalStatus.ON
    ds.controlIQMode.value = UserMode.EXERCISE
}

@Preview(showBackground = true)
@Composable
internal fun LandingDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _ -> },
                sendServiceBolusCancel = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BolusPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _ -> },
                sendServiceBolusCancel = {},
                bottomScaffoldDisplayState = BottomSheetValue.Expanded,
                _bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TempRatePreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _ -> },
                sendServiceBolusCancel = {},
                bottomScaffoldDisplayState = BottomSheetValue.Expanded,
                _bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW,
                sectionState = LandingSection.ACTIONS,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LandingDebugPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _ -> },
                sendServiceBolusCancel = {},
                sectionState = LandingSection.DEBUG,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LandingSettingsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _ -> },
                sendServiceBolusCancel = {},
                sectionState = LandingSection.SETTINGS,
            )
        }
    }
}