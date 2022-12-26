@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.presentation.components.Line
import com.jwoglom.wearx2.presentation.theme.Colors
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import kotlinx.coroutines.launch

@Composable
fun Landing(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    bolusSheetState: BottomSheetValue = BottomSheetValue.Collapsed,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()

    var selectedItem by remember { mutableStateOf(LandingSection.DASHBOARD) }
    val displayBolusWindow = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetState(bolusSheetState)
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${deviceName.value}${if (pumpConnected.value == true) "" else " (DISCONNECTED)"}")
                },
                colors = if (isSystemInDarkTheme()) TopAppBarDefaults.topAppBarColors()
                else TopAppBarDefaults.topAppBarColors(containerColor = Colors.primary)
            )
        },
        content = { innerPadding ->
            BottomSheetScaffold(
                scaffoldState = displayBolusWindow,
                sheetContent = {
                    LazyColumn(
                        contentPadding = PaddingValues(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        content = {
                            item {
                                Line("Bolus window")
                            }
                            item {
                                Spacer(Modifier.fillMaxWidth().height(64.dp))
                            }
                        }
                    )
                },
                sheetPeekHeight = 0.dp,
                backgroundColor = MaterialTheme.colorScheme.background,
            ) {
                LazyColumn(
                    contentPadding = innerPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    content = {
                        when (selectedItem) {
                            LandingSection.DASHBOARD -> {
                                item {
                                    if (pumpConnected.value == false) {
                                        Line("Connecting: ${setupStage.value}", bold = true)
                                    }

                                    Line("Dashboard")
                                }
                            }
                            LandingSection.DEBUG -> {
                                item {
                                    Line("Debug")
                                }
                            }
                            LandingSection.SETTINGS -> {
                                item {
                                    Line("Settings")
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

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            LocalDataStore.current.setupDeviceName.value = "tslim X2 ***789"
            LocalDataStore.current.setupDeviceModel.value = "X2"
            LocalDataStore.current.pumpConnected.value = true
            Landing(
                sendMessage = { _, _ -> },
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
            LocalDataStore.current.setupDeviceName.value = "tslim X2 ***789"
            LocalDataStore.current.setupDeviceModel.value = "X2"
            LocalDataStore.current.pumpConnected.value = true
            Landing(
                sendMessage = { _, _ -> },
                bolusSheetState = BottomSheetValue.Expanded,
            )
        }
    }
}