@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.PumpGlobalsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpSoundsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SoundSettingsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val pumpGlobals = ds.pumpGlobalsResponse.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, soundSettingsActionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = soundSettingsActionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("SoundSettingsActions loading: remaining ${nullFields.size}: ${soundSettingsActionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("SoundSettingsActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("SoundSettingsActions loading done: ${soundSettingsActionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading SoundSettingsActions with force")
        refreshing = true

        soundSettingsActionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading SoundSettingsActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading SoundSettingsActions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    var quickBolusText by remember { mutableStateOf("") }
    var generalText by remember { mutableStateOf("") }
    var reminderText by remember { mutableStateOf("") }
    var alertText by remember { mutableStateOf("") }
    var alarmText by remember { mutableStateOf("") }
    var cgmAText by remember { mutableStateOf("") }
    var cgmBText by remember { mutableStateOf("") }
    var changeBitmaskText by remember { mutableStateOf("") }

    LaunchedEffect(pumpGlobals.value) {
        pumpGlobals.value?.let { globals ->
            val cargo = globals.cargo
            if (cargo != null && cargo.size >= 14) {
                quickBolusText = cargo[8].toUByte().toInt().toString()
                generalText = cargo[9].toUByte().toInt().toString()
                reminderText = cargo[10].toUByte().toInt().toString()
                alertText = cargo[11].toUByte().toInt().toString()
                alarmText = cargo[12].toUByte().toInt().toString()
            }
            if (cgmAText.isBlank()) {
                cgmAText = "0"
            }
            if (cgmBText.isBlank()) {
                cgmBText = "0"
            }
            if (changeBitmaskText.isBlank()) {
                changeBitmaskText = SetPumpSoundsRequest.ChangeBitmask.toBitmask(*SetPumpSoundsRequest.ChangeBitmask.values()).toString()
            }
        }
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
                    ListItem(
                        headlineContent = { Text("Back") },
                        leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable {
                            navigateBack()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                    )
                    HeaderLine("Sound Settings")
                    Divider()
                }

                if (refreshing) {
                    item {
                        LoadSpinner("Loading sound settings..." )
                    }
                }

                item {
                    SoundSettingField(
                        label = "Quick bolus annunciation",
                        value = quickBolusText,
                        onValueChange = { quickBolusText = it },
                        enumValue = annunciationLabel(quickBolusText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "General annunciation",
                        value = generalText,
                        onValueChange = { generalText = it },
                        enumValue = annunciationLabel(generalText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "Reminder annunciation",
                        value = reminderText,
                        onValueChange = { reminderText = it },
                        enumValue = annunciationLabel(reminderText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "Alert annunciation",
                        value = alertText,
                        onValueChange = { alertText = it },
                        enumValue = annunciationLabel(alertText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "Alarm annunciation",
                        value = alarmText,
                        onValueChange = { alarmText = it },
                        enumValue = annunciationLabel(alarmText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "CGM alert annunciation A",
                        value = cgmAText,
                        onValueChange = { cgmAText = it },
                        enumValue = cgmAnnunciationLabel(cgmAText, cgmBText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "CGM alert annunciation B",
                        value = cgmBText,
                        onValueChange = { cgmBText = it },
                        enumValue = cgmAnnunciationLabel(cgmAText, cgmBText)
                    )
                }

                item {
                    SoundSettingField(
                        label = "Change bitmask",
                        value = changeBitmaskText,
                        onValueChange = { changeBitmaskText = it },
                        enumValue = ""
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text("Apply sound settings") },
                        supportingContent = { Text("Send SetPumpSoundsRequest with these values") },
                        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val message = SetPumpSoundsRequest(
                                quickBolusText.toIntOrNull() ?: 0,
                                generalText.toIntOrNull() ?: 0,
                                reminderText.toIntOrNull() ?: 0,
                                alertText.toIntOrNull() ?: 0,
                                alarmText.toIntOrNull() ?: 0,
                                cgmAText.toIntOrNull() ?: 0,
                                cgmBText.toIntOrNull() ?: 0,
                                changeBitmaskText.toIntOrNull() ?: 0,
                            )
                            sendPumpCommands(SendType.STANDARD, listOf(message))
                        }
                    )
                }

                item {
                    TextButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to ${LandingSection.ACTIONS.label}")
                    }
                }
            }
        )
    }
}

@Composable
private fun SoundSettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enumValue: String,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                if (enumValue.isNotBlank()) {
                    Text(
                        text = enumValue,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                }
            }
        }
    )
}

private fun annunciationLabel(value: String): String {
    val parsed = value.toIntOrNull()
    return if (parsed != null) PumpGlobalsResponse.AnnunciationEnum.fromId(parsed)?.name ?: "Unknown" else "Unknown"
}

private fun cgmAnnunciationLabel(valueA: String, valueB: String): String {
    val a = valueA.toIntOrNull()
    val b = valueB.toIntOrNull()
    return if (a != null && b != null) {
        SetPumpSoundsRequest.CgmAlertAnnunciationEnum.fromIds(a, b)?.name ?: "Unknown"
    } else {
        "Unknown"
    }
}

val soundSettingsActionsCommands = listOf(
    PumpGlobalsRequest(),
)

val soundSettingsActionsFields = listOf(
    dataStore.pumpGlobalsResponse,
)

@Preview(showBackground = true)
@Composable
internal fun SoundSettingsActionsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            SoundSettingsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
