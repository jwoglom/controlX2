@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import com.jwoglom.pumpx2.pump.messages.Message
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
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetModesRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun Actions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    resumeInsulinMenuState: Boolean = false,
    suspendInsulinMenuState: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()

    var showResumeInsulinMenu by remember { mutableStateOf(resumeInsulinMenuState) }
    var showSuspendInsulinMenu by remember { mutableStateOf(suspendInsulinMenuState) }

    val context = LocalContext.current
    val ds = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, actionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = actionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("Actions loading: remaining ${nullFields.size}: ${actionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("Actions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("Actions loading done: ${actionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading Actions with force")
        refreshing = true

        actionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading Actions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading Actions from interval")
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
                    HeaderLine("Actions")
                }
                item {
                    val basalStatus = ds.basalStatus.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineText = { Text(
                                when (basalStatus.value) {
                                    BasalStatus.UNKNOWN, null -> "Stop / Start Insulin"
                                    BasalStatus.PUMP_SUSPENDED -> "Start Insulin"
                                    else -> "Stop Insulin"
                                }
                            )},
                            supportingText = { Text(
                                when (basalStatus.value) {
                                    BasalStatus.UNKNOWN, null -> "Stop or resume insulin deliveries"
                                    BasalStatus.PUMP_SUSPENDED -> "Resume insulin deliveries"
                                    else -> "Stop insulin deliveries"
                                }
                            ) },
                            leadingContent = {
                                Icon(
                                    when (basalStatus.value) {
                                        BasalStatus.UNKNOWN, null -> Icons.Filled.Close
                                        BasalStatus.PUMP_SUSPENDED -> Icons.Filled.PlayArrow
                                        else -> Icons.Filled.Close
                                    },
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = when (basalStatus.value) {
                                    BasalStatus.UNKNOWN, null -> ListItemDefaults.containerColor
                                    BasalStatus.PUMP_SUSPENDED -> Color.Green.copy(alpha = 0.5F)
                                    else -> Color.Red.copy(alpha = 0.5F)
                                }
                            ),
                            modifier = Modifier.clickable {
                                when (basalStatus.value) {
                                    BasalStatus.UNKNOWN, null -> {}
                                    BasalStatus.PUMP_SUSPENDED -> {showResumeInsulinMenu = true}
                                    else -> {showSuspendInsulinMenu = true}
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showResumeInsulinMenu,
                            onDismissRequest = { showResumeInsulinMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("Resume insulin")
                                },
                                text = {
                                    Text("Resume all insulin deliveries?")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showResumeInsulinMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showResumeInsulinMenu = false
                                            sendPumpCommands(SendType.BUST_CACHE, listOf(ResumePumpingRequest()))
                                            refreshScope.launch {
                                                repeat(5) {
                                                    Thread.sleep(1000)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(HomeScreenMirrorRequest())
                                                    )
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = Color.Green.copy(alpha = 0.5F)
                                        ),
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Resume insulin")
                                    }
                                }
                            )

                        }

                        DropdownMenu(
                            expanded = showSuspendInsulinMenu,
                            onDismissRequest = { showSuspendInsulinMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("Stop insulin")
                                },
                                text = {
                                    Text("Suspend all insulin deliveries?")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showSuspendInsulinMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showSuspendInsulinMenu = false
                                            sendPumpCommands(SendType.BUST_CACHE, listOf(SuspendPumpingRequest()))
                                            refreshScope.launch {
                                                repeat(5) {
                                                    Thread.sleep(1000)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(HomeScreenMirrorRequest())
                                                    )
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = Color.Red.copy(alpha = 0.5F)
                                        ),
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Stop insulin")
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
                    val controlIQMode = ds.controlIQMode.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineText = { Text(
                                when (controlIQMode.value) {
                                    UserMode.EXERCISE -> "Disable Exercise Mode"
                                    else -> "Enable Exercise Mode"
                                }
                            )},
                            supportingText = {  },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                when (controlIQMode.value) {
                                    UserMode.EXERCISE -> {
                                        // Disable
                                        sendPumpCommands(SendType.BUST_CACHE, listOf(
                                            SetModesRequest(SetModesRequest.ModeCommand.EXERCISE_MODE_OFF)
                                        ))
                                    }
                                    UserMode.NONE -> {
                                        // Enable
                                        sendPumpCommands(SendType.BUST_CACHE, listOf(
                                            SetModesRequest(SetModesRequest.ModeCommand.EXERCISE_MODE_ON)
                                        ))
                                    }
                                    else -> {
                                        Toast.makeText(context, "Exercise mode cannot be enabled because another user mode is active", Toast.LENGTH_LONG).show()
                                    }
                                }

                                refreshScope.launch {
                                    repeat(5) {
                                        Thread.sleep(1000)
                                        sendPumpCommands(
                                            SendType.BUST_CACHE,
                                            listOf(ControlIQInfoRequestBuilder.create(apiVersion()))
                                        )
                                    }
                                }
                            }
                        )

                    }
                }

                item {
                    Divider()
                }


                item {
                    val controlIQMode = ds.controlIQMode.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineText = { Text(
                                when (controlIQMode.value) {
                                    UserMode.SLEEP -> "Disable Sleep Mode"
                                    else -> "Enable Sleep Mode"
                                }
                            )},
                            supportingText = {  },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Home,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                when (controlIQMode.value) {
                                    UserMode.SLEEP -> {
                                        // Disable
                                        sendPumpCommands(SendType.BUST_CACHE, listOf(
                                            SetModesRequest(SetModesRequest.ModeCommand.SLEEP_MODE_OFF)
                                        ))
                                    }
                                    UserMode.NONE -> {
                                        // Enable
                                        sendPumpCommands(SendType.BUST_CACHE, listOf(
                                            SetModesRequest(SetModesRequest.ModeCommand.SLEEP_MODE_ON)
                                        ))
                                    }
                                    else -> {
                                        Toast.makeText(context, "Exercise mode cannot be enabled because another user mode is active", Toast.LENGTH_LONG).show()
                                    }
                                }

                                refreshScope.launch {
                                    repeat(5) {
                                        Thread.sleep(1000)
                                        sendPumpCommands(
                                            SendType.BUST_CACHE,
                                            listOf(ControlIQInfoRequestBuilder.create(apiVersion()))
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )
    }
}
val actionsCommands = listOf(
    HomeScreenMirrorRequest(),
    ControlIQInfoRequestBuilder.create(apiVersion())
)

val actionsFields = listOf(
    dataStore.basalStatus,
    dataStore.controlIQMode
)

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinActive() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinActive_StopMenuOpen() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                suspendInsulinMenuState = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinSuspended() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinSuspended_ResumeMenuOpen() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                resumeInsulinMenuState = true
            )
        }
    }
}