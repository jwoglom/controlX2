package com.jwoglom.controlx2.presentation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.accompanist.flowlayout.FlowRow
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.controlx2.BuildConfig
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
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.components.FirstRowChip
import com.jwoglom.controlx2.shared.presentation.LifecycleStateObserver
import com.jwoglom.controlx2.presentation.components.LineInfoChip
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.presentation.defaultTheme
import com.jwoglom.controlx2.presentation.greenTheme
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.redTheme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.util.SendType
import hu.supercluster.paperwork.Paperwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class, ExperimentalWearMaterialApi::class)
@Composable
fun LandingScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendPhoneCommand: (String) -> Unit,
    sendPhoneOpenActivity: () -> Unit,
    resetSavedBolusEnteredState: () -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }


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
        dataStore.cgmDeltaArrow,
    )

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, commands)
    }

    fun waitForLoaded() = refreshScope.launch {
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = fields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("LandingPage loading: remaining ${nullFields.size}: ${fields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("LandingPage loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("LandingPage loading done: ${fields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        Timber.i("reloading LandingPage with force")
        refreshing = true

        fields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading LandingPage from onStart lifecyclestate")
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading LandingPage from interval")
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    LaunchedEffect (refreshing) {
        waitForLoaded()
    }

    Box(modifier = modifier
        .fillMaxSize()
        .pullRefresh(state)) {
        PullRefreshIndicator(refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f))

        // Places both Chips (button and toggle) in the middle of the screen.
        ScalingLazyColumn(
            modifier = Modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {

            item {
                // Signify we have drawn the content of the first screen
                ReportFullyDrawn()

                val dataStore = LocalDataStore.current

                FlowRow {
                    val batteryPercent = dataStore.batteryPercent.observeAsState()
                    val iobUnits = dataStore.iobUnits.observeAsState()
                    val cartridgeRemainingUnits = dataStore.cartridgeRemainingUnits.observeAsState()
                    val cartridgeRemainingEstimate = dataStore.cartridgeRemainingEstimate.observeAsState()

                    FirstRowChip(
                        labelText = when(batteryPercent.value) {
                            null -> "?"
                            else -> "${batteryPercent.value}%"
                        },
                        secondaryLabelText = "Battery",
                        theme = when {
                            batteryPercent.value == null -> defaultTheme
                            batteryPercent.value!! > 50 -> greenTheme
                            batteryPercent.value!! > 25 -> defaultTheme
                            else -> redTheme
                        },
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(iobUnits.value) {
                            null -> "?"
                            else -> "${String.format("%.1f", iobUnits.value)}u"
                        },
                        secondaryLabelText = "IOB",
                        theme = defaultTheme,
                        numItems = 3,
                    )

                    FirstRowChip(
                        labelText = when(cartridgeRemainingUnits.value) {
                            null -> "?"
                            else -> "${cartridgeRemainingUnits.value}u${if (cartridgeRemainingEstimate.value == true) "+" else ""}"
                        },
                        secondaryLabelText = "Cartridge",
                        theme = when {
                            cartridgeRemainingUnits.value == null -> defaultTheme
                            cartridgeRemainingUnits.value!! > 75 -> greenTheme
                            cartridgeRemainingUnits.value!! > 35 -> defaultTheme
                            else -> redTheme
                        },
                        numItems = 3,
                    )
                }
            }

            item {
                val lastBolusStatus = dataStore.lastBolusStatus.observeAsState()

                Chip(
                    onClick = {
                        resetSavedBolusEnteredState()
                        resetBolusDataStoreState(dataStore)
                        navController.navigate(Screen.Bolus.route)
                    },
                    label = {
                        Text(
                            "Bolus",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            when (lastBolusStatus.value) {
                                null -> ""
                                else -> "Last: ${lastBolusStatus.value}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val basalRate = dataStore.basalRate.observeAsState()
                val basalStatus = dataStore.basalStatus.observeAsState()
                val landingBasalDisplayedText = dataStore.landingBasalDisplayedText.observeAsState()

                LaunchedEffect (basalRate.value, basalStatus.value) {
                    dataStore.landingBasalDisplayedText.value = when (basalStatus.value) {
                        BasalStatus.ON, BasalStatus.ZERO, BasalStatus.CONTROLIQ_INCREASED, BasalStatus.CONTROLIQ_REDUCED, BasalStatus.UNKNOWN, null -> when (basalRate.value) {
                            null -> null
                            else -> "${basalRate.value}"
                        }
                        BasalStatus.PUMP_SUSPENDED, BasalStatus.BASALIQ_SUSPENDED ->
                            "${basalStatus.value?.str}"
                        else -> when (basalRate.value) {
                            null -> "${basalStatus.value?.str}"
                            else -> "${basalStatus.value?.str} (${basalRate.value})"
                        }
                    }
                }

                LineInfoChip(
                    "Basal",
                    when (landingBasalDisplayedText.value) {
                        null -> "?"
                        else -> "${landingBasalDisplayedText.value}"
                    },
                )
            }


            item {
                val controlIQMode = dataStore.controlIQMode.observeAsState()

                LazyRow {
                    item {
                        Chip(
                            onClick = {
                                navController.navigate(Screen.ExerciseModeSet.route)
                            },
                            label = {
                                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Exercise")
                            },
                            secondaryLabel = {
                                Text(
                                    when (controlIQMode.value) {
                                        UserMode.EXERCISE -> "ON"
                                        else -> "OFF"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Spacer(Modifier.width(12.dp))
                    }
                    item {
                        Chip(
                            onClick = {
                                navController.navigate(Screen.SleepModeSet.route)
                            },
                            label = {
                                Icon(Icons.Filled.KingBed, contentDescription = "Sleep")
                            },
                            secondaryLabel = {
                                Text(
                                    when (controlIQMode.value) {
                                        UserMode.SLEEP -> "ON"
                                        else -> "OFF"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Spacer(Modifier.width(12.dp))
                    }
                    item {
                        Chip(
                            onClick = {
                                // swipeDismissableNavController.navigate(Screen.Bolus.route)
                            },
                            label = {
                                Icon(
                                    painterResource(R.drawable.pump),
                                    tint = Color.Unspecified,
                                    contentDescription = "Pump icon",
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    " ",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                val controlIQStatus = LocalDataStore.current.controlIQStatus.observeAsState()
                val controlIQMode = LocalDataStore.current.controlIQMode.observeAsState()
                val landingControlIQDisplayedText = dataStore.landingControlIQDisplayedText.observeAsState()

                LaunchedEffect (controlIQStatus.value, controlIQMode.value) {
                    dataStore.landingControlIQDisplayedText.value = when (controlIQMode.value) {
                        UserMode.SLEEP, UserMode.EXERCISE -> "${controlIQMode.value}"
                        else -> when (controlIQStatus.value) {
                            null -> "?"
                            else -> "${controlIQStatus.value}"
                        }
                    }
                }

                LineInfoChip(
                    "Control-IQ",
                    when(landingControlIQDisplayedText.value) {
                        null -> "?"
                        else -> "${landingControlIQDisplayedText.value}"
                    }
                )
            }

            item {
                var showExact by remember { mutableStateOf(false) }
                val cgmSessionState = LocalDataStore.current.cgmSessionState.observeAsState()
                val cgmSessionExpireRelative = LocalDataStore.current.cgmSessionExpireRelative.observeAsState()
                val cgmSessionExpireExact = LocalDataStore.current.cgmSessionExpireExact.observeAsState()
                LineInfoChip(
                    "CGM Sensor",
                    when (cgmSessionState.value) {
                        null -> "?"
                        "Active" -> when (showExact) {
                            true -> "${cgmSessionExpireExact.value}"
                            false -> "${cgmSessionExpireRelative.value}"
                        }
                        else -> "${cgmSessionState.value}"
                    },
                    onClick = {
                        showExact = !showExact
                    }
                )
            }

            item {
                val cgmTransmitterStatus = LocalDataStore.current.cgmTransmitterStatus.observeAsState()
                LineInfoChip(
                    "CGM Battery",
                    when(cgmTransmitterStatus.value) {
                        null -> "?"
                        else -> "${cgmTransmitterStatus.value}"
                    }
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.padding(top = 25.dp),
                ) {
                    Chip(
                        onClick = {
                            sendPhoneCommand("force-reload")
                        },
                        label = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Force reload app"
                            )
                        },
                    )
                    Spacer(Modifier.width(16.dp))
                    Chip(
                        onClick = {
                            sendPhoneOpenActivity()
                        },
                        label = {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = "Open on phone"
                            )
                        },
                    )
                }
            }

            item {
                val context = LocalContext.current
                val p = Paperwork(context)
                Text(buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)) {
                        append("ControlX2 ")
                        append(BuildConfig.VERSION_NAME)
                    }

                    append("\n")
                    append("with PumpX2 ")
                    append(com.jwoglom.pumpx2.BuildConfig.PUMPX2_VERSION)
                    append("\n")

                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Build: ")
                    }
                    append(p.get("build_version"))
                    append("\n")

                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Build time: ")
                    }
                    append(p.get("build_time"))
                    append("\n")
                }, color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(start = 16.dp))
            }

//            item {
//                Button(
//                    onClick = {
//                        sendPhoneOpenTconnect()
//                    }
//                ) {
//
//                    Text("Open t:connect on phone")
//                }
//            }
//
//            item {
//                Button(
//                    onClick = {
//                        sendPhoneOpenActivity()
//                    }
//                ) {
//
//                    Text("Open WearX2 on phone")
//                }
//            }
        }
    }
}


@Preview(
    apiLevel = 28,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    device = Devices.WEAR_OS_RECT,
    heightDp = 500,
    showBackground = true
)
@Composable
fun DefaultLandingScreenPreviewFull() {
    LandingScreen(
        scalingLazyListState = ScalingLazyListState(1, 0),
        focusRequester = FocusRequester(),
        sendPumpCommands = { _, _ -> },
        sendPhoneOpenActivity = {},
        sendPhoneCommand = {},
        resetSavedBolusEnteredState = {},
        navController = rememberSwipeDismissableNavController()
    )
}


@Preview(
    apiLevel = 28,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    device = Devices.WEAR_OS_LARGE_ROUND,
    showBackground = true
)
@Composable
fun DefaultLandingScreenPreviewCropped() {
    LandingScreen(
        scalingLazyListState = ScalingLazyListState(1, 0),
        focusRequester = FocusRequester(),
        sendPumpCommands = { _, _ -> },
        sendPhoneOpenActivity = {},
        sendPhoneCommand = {},
        resetSavedBolusEnteredState = {},
        navController = rememberSwipeDismissableNavController()
    )
}