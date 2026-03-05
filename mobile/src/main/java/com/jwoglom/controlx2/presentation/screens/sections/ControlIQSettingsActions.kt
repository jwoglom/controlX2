@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeControlIQSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQSleepScheduleResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun ControlIQSettingsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val controlIQEnabled = ds.controlIQEnabled.observeAsState()
    val controlIQWeight = ds.controlIQWeight.observeAsState()
    val controlIQWeightUnit = ds.controlIQWeightUnit.observeAsState()
    val controlIQTotalDailyInsulin = ds.controlIQTotalDailyInsulin.observeAsState()
    val sleepSchedule = ds.controlIQSleepScheduleResponse.observeAsState()

    val controlIQSummaryText = remember(
        controlIQEnabled.value,
        controlIQWeight.value,
        controlIQWeightUnit.value,
        controlIQTotalDailyInsulin.value,
    ) {
        if (controlIQEnabled.value == null) {
            "Loading..."
        } else {
            "Status: ${if (controlIQEnabled.value == true) "Enabled" else "Disabled"}\n" +
                "Weight: ${controlIQWeight.value ?: "?"} ${controlIQWeightUnit.value ?: ""}\n" +
                "Total Daily Insulin: ${controlIQTotalDailyInsulin.value ?: "?"} units"
        }
    }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, controlIQSettingsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = controlIQSettingsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("ControlIQSettingsActions loading: remaining ${nullFields.size}: ${controlIQSettingsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("ControlIQSettingsActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("ControlIQSettingsActions loading done: ${controlIQSettingsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading ControlIQSettingsActions with force")
        refreshing = true

        controlIQSettingsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading ControlIQSettingsActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading ControlIQSettingsActions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    // Control-IQ settings state
    var showChangeControlIQDialog by remember { mutableStateOf(false) }
    var ciqEnabled by remember { mutableStateOf(false) }
    var weightText by remember { mutableStateOf("") }
    var tdiText by remember { mutableStateOf("") }

    // Sleep schedule edit state
    var showEditSleepScheduleDialog by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf(0) }
    var sleepEnabled by remember { mutableStateOf(false) }
    var sleepStartHour by remember { mutableStateOf("") }
    var sleepStartMin by remember { mutableStateOf("") }
    var sleepEndHour by remember { mutableStateOf("") }
    var sleepEndMin by remember { mutableStateOf("") }
    var sleepDays by remember { mutableStateOf("") }

    // Populate from current values
    LaunchedEffect(controlIQEnabled.value, controlIQWeight.value, controlIQTotalDailyInsulin.value) {
        controlIQEnabled.value?.let { ciqEnabled = it }
        controlIQWeight.value?.let { weightText = it.toString() }
        controlIQTotalDailyInsulin.value?.let { tdiText = it.toString() }
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
                        modifier = Modifier.clickable { navigateBack() },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                    )
                    HeaderLine("Control-IQ Settings")
                    Divider()
                }

                if (refreshing) {
                    item {
                        LoadSpinner("Loading Control-IQ settings...")
                    }
                }

                // Control-IQ enabled status display
                item {
                    ListItem(
                        headlineContent = { Text("Control-IQ") },
                        supportingContent = {
                            Text(controlIQSummaryText)
                        },
                        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showChangeControlIQDialog = true
                        }
                    )
                    Divider()
                }

                // Sleep Schedule section header
                item {
                    Line("\n")
                    HeaderLine("Sleep Schedule")
                    Divider()
                }

                // Sleep schedule slots
                val schedule = sleepSchedule.value
                if (schedule != null) {
                    val schedules = listOf(
                        schedule.schedule0,
                        schedule.schedule1,
                        schedule.schedule2,
                        schedule.schedule3
                    )
                    schedules.forEachIndexed { index, slot ->
                        item {
                            val enabled = slot.enabled != 0
                            ListItem(
                                headlineContent = { Text("Schedule ${index + 1}") },
                                supportingContent = {
                                    if (enabled) {
                                        val days = slot.activeDays().joinToString(", ") { it.name.take(3) }
                                        Text(
                                            "Enabled\n" +
                                            "${String.format("%02d:%02d", slot.startTime().hour(), slot.startTime().min())} - " +
                                            "${String.format("%02d:%02d", slot.endTime().hour(), slot.endTime().min())}\n" +
                                            "Days: $days"
                                        )
                                    } else {
                                        Text("Disabled")
                                    }
                                },
                                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    editingSlot = index
                                    sleepEnabled = enabled
                                    if (enabled) {
                                        sleepStartHour = slot.startTime().hour().toString()
                                        sleepStartMin = slot.startTime().min().toString()
                                        sleepEndHour = slot.endTime().hour().toString()
                                        sleepEndMin = slot.endTime().min().toString()
                                        sleepDays = slot.activeDays().sumOf { it.id() }.toString()
                                    } else {
                                        sleepStartHour = "22"
                                        sleepStartMin = "0"
                                        sleepEndHour = "6"
                                        sleepEndMin = "0"
                                        sleepDays = "127"
                                    }
                                    showEditSleepScheduleDialog = true
                                }
                            )
                            Divider()
                        }
                    }
                } else if (!refreshing) {
                    item {
                        Line("Sleep schedule data not available.")
                    }
                }

                item {
                    TextButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to ${LandingSection.ACTIONS.label}")
                    }
                }
            }
        )
    }

    // Change Control-IQ Settings Dialog
    if (showChangeControlIQDialog) {
        AlertDialog(
            onDismissRequest = { showChangeControlIQDialog = false },
            title = { Text("Change Control-IQ Settings") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = ciqEnabled,
                            onCheckedChange = { ciqEnabled = it }
                        )
                    }
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Weight (lbs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tdiText,
                        onValueChange = { tdiText = it },
                        label = { Text("Total Daily Insulin (units)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val weight = weightText.toIntOrNull()
                    val tdi = tdiText.toIntOrNull()
                    if (weight != null && tdi != null) {
                        sendPumpCommands(
                            SendType.STANDARD,
                            listOf(ChangeControlIQSettingsRequest(ciqEnabled, weight, tdi))
                        )
                        showChangeControlIQDialog = false
                        refreshScope.launch {
                            delay(500)
                            refresh()
                        }
                    } else {
                        Toast.makeText(context, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeControlIQDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Sleep Schedule Dialog
    if (showEditSleepScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showEditSleepScheduleDialog = false },
            title = { Text("Sleep Schedule ${editingSlot + 1}") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = sleepEnabled,
                            onCheckedChange = { sleepEnabled = it }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = sleepStartHour,
                            onValueChange = { sleepStartHour = it },
                            label = { Text("Start Hour") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sleepStartMin,
                            onValueChange = { sleepStartMin = it },
                            label = { Text("Start Min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = sleepEndHour,
                            onValueChange = { sleepEndHour = it },
                            label = { Text("End Hour") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sleepEndMin,
                            onValueChange = { sleepEndMin = it },
                            label = { Text("End Min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = sleepDays,
                        onValueChange = { sleepDays = it },
                        label = { Text("Active days bitmask (Mon=1,Tue=2,...Sun=64, all=127)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val startH = sleepStartHour.toIntOrNull() ?: 0
                    val startM = sleepStartMin.toIntOrNull() ?: 0
                    val endH = sleepEndHour.toIntOrNull() ?: 0
                    val endM = sleepEndMin.toIntOrNull() ?: 0
                    val daysBitmask = sleepDays.toIntOrNull() ?: 0

                    val startTime = startH * 60 + startM
                    val endTime = endH * 60 + endM

                    val sleepScheduleObj = ControlIQSleepScheduleResponse.SleepSchedule(
                        if (sleepEnabled) 1 else 0,
                        daysBitmask,
                        startTime,
                        endTime
                    )
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(SetSleepScheduleRequest(editingSlot, sleepScheduleObj, 0))
                    )
                    showEditSleepScheduleDialog = false
                    refreshScope.launch {
                        delay(500)
                        refresh()
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSleepScheduleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

val controlIQSettingsCommands = listOf(
    ControlIQInfoRequestBuilder.create(apiVersion()),
    ControlIQSleepScheduleRequest(),
)

val controlIQSettingsFields = listOf(
    dataStore.controlIQEnabled,
    dataStore.controlIQSleepScheduleResponse,
)

@Preview(showBackground = true)
@Composable
internal fun ControlIQSettingsActionsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            ControlIQSettingsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
