@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.MinsTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileActions(
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

    var showProfileDetails by remember { mutableStateOf<IDPManager.Profile?>(null) }

    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type,
            ds.idpManager.value?.nextMessages() ?: IDPManager().nextMessages()
        )
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        var attempts = 0
        var round = 0
        val messagesSent = mutableSetOf<Message>()
        while (true) {
            if (ds.idpManager.value?.isComplete == true) {
                break
            }

            val nextMessages = ds.idpManager.value?.nextMessages() ?: IDPManager().nextMessages()
            if (messagesSent.containsAll(nextMessages)) {
                Timber.i("profileActions round${round} loading: remaining ${nextMessages?.size} sent ${messagesSent.size}")
                if (sinceLastFetchTime >= 2500) {
                    Timber.i("profileActions round${round} loading re-fetching with bust_cache")
                    sendPumpCommands(SendType.BUST_CACHE, nextMessages)
                    sinceLastFetchTime = 0
                    attempts++
                }
            } else {
                // there are new messages to send
                round++
                sinceLastFetchTime = 0
                attempts = 0
                Timber.i("profileActions round${round} sent: remaining ${nextMessages?.size} sent ${messagesSent.size}")
                sendPumpCommands(SendType.BUST_CACHE, nextMessages)
                messagesSent.addAll(nextMessages)
            }


            withContext(Dispatchers.IO) {
                Thread.sleep(100)
            }
            sinceLastFetchTime += 100
        }
        Timber.i("profileActions loading done: ${ds.idpManager}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading profileActions with force")
        refreshing = true

        profileActionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading profileActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading profileActions from interval")
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
        val idpManager = ds.idpManager.observeAsState()
        PullRefreshIndicator(
            refreshing || idpManager.value?.isComplete() == false,
            state,
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
                    HeaderLine("Profile Actions")
                    Divider()

                    val model = determinePumpModel(deviceName.value ?: "")
                    if (model == KnownDeviceModel.TSLIM_X2) {
                        Line("Profile control is not supported on this device model (${model}). Profiles can only be viewed.")
                        Line("")
                    }
                }

                if (idpManager.value?.isComplete == true) {
                    idpManager.value?.profiles?.forEachIndexed { profileIndex, profile ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(Alignment.TopStart)
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            profile.idpSettingsResponse.name
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            buildAnnotatedString {
                                                append("IDP ID ${profile.idpSettingsResponse.idpId} in slot ${profileIndex}")
                                                if (idpManager.value?.activeProfile?.idpId == profile.idpId) {
                                                    append("\nActive Profile")
                                                }
                                            }
                                        )
                                    },
                                    leadingContent = {
                                        if (idpManager.value?.activeProfile?.idpId == profile.idpId) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                        } else {
                                            Icon(Icons.Filled.Menu, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        refreshScope.launch {
                                            showProfileDetails = profile
                                        }
                                    }
                                )

                                DropdownMenu(
                                    expanded = showProfileDetails?.idpId == profile.idpId,
                                    onDismissRequest = { },
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                ) {
                                    AlertDialog(
                                        onDismissRequest = {
                                            showProfileDetails = null
                                        },
                                        dismissButton = {
                                            TextButton(
                                                onClick = {
                                                    showProfileDetails = null
                                                },
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                Text("Back")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                                        properties = DialogProperties(
                                            usePlatformDefaultWidth = false
                                        ),
                                        confirmButton = {},
                                        title = {
                                            Text("Profile '${profile.idpSettingsResponse.name}'")
                                        },
                                        text = {
                                            LazyColumn(
                                                contentPadding = innerPadding,
                                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 0.dp),
                                                content = {
                                                    profile.segments?.forEachIndexed { segmentIndex, segment ->
                                                        item {
                                                            ListItem(
                                                                headlineContent = {
                                                                    Text(
                                                                        buildAnnotatedString {
                                                                            append("${segmentIndex}. ")
                                                                            withStyle(
                                                                                style = SpanStyle(
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                            ) {
                                                                                append("${MinsTime(segment.profileStartTime)}")
                                                                            }
                                                                            append(": ")
                                                                            append("${InsulinUnit.from1000To1(segment.profileBasalRate.toLong())} u/hr")
                                                                        }
                                                                    )
                                                                },
                                                                supportingContent = {
                                                                    Text(buildAnnotatedString {
                                                                        append("Carb ratio: ${segment.profileCarbRatio}")
                                                                        append("\n")
                                                                        append("ISF: ${segment.profileISF}")
                                                                        append("\n")
                                                                        append("Target BG: ${segment.profileTargetBG}")
                                                                        append("\n")
                                                                    })
                                                                }
                                                            )
                                                        }

                                                    }

                                                    item {
                                                        Divider()
                                                    }

                                                    item {
                                                        ListItem(
                                                            headlineContent = {
                                                                Text("Delete Profile")
                                                            },
                                                            leadingContent = {
                                                                Icon(
                                                                    Icons.Filled.Delete,
                                                                    contentDescription = null,
                                                                )
                                                            },
                                                            modifier = Modifier.clickable {
                                                                refreshScope.launch {
                                                                    sendPumpCommands(
                                                                        SendType.BUST_CACHE, listOf(
                                                                            profile.deleteProfileMessage()
                                                                        )
                                                                    )
                                                                    showProfileDetails = null
                                                                    refresh()
                                                                }
                                                            }
                                                        )
                                                    }

                                                    item {
                                                        ListItem(
                                                            headlineContent = {
                                                                Text("Set Active Profile")
                                                            },
                                                            supportingContent = {
                                                                if (idpManager.value?.activeProfile?.idpId == profile.idpId) {
                                                                    Text("This profile is already active")
                                                                }
                                                            },
                                                            leadingContent = {
                                                                Icon(
                                                                    Icons.Filled.CheckCircle,
                                                                    contentDescription = null,
                                                                )
                                                            },
                                                            modifier = Modifier.clickable {
                                                                if (idpManager.value?.activeProfile?.idpId != profile.idpId) {
                                                                    refreshScope.launch {
                                                                        sendPumpCommands(
                                                                            SendType.BUST_CACHE,
                                                                            listOf(
                                                                                profile.setActiveProfileMessage()
                                                                            )
                                                                        )
                                                                        showProfileDetails = null
                                                                        refresh()
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    )

                                }

                            }
                        }
                    }
                }
                else {
                    item {
                        LoadSpinner("Loading profiles...")
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

// On initial load and reloads, we only need to load the base profile data (aka empty-state IDPManager)
// so we don't use the global IDPManager in dataStore
val profileActionsCommands = listOf(
    *IDPManager().nextMessages().toTypedArray()
)

val profileActionsFields = listOf(
    dataStore.idpManager
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
            ProfileActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
