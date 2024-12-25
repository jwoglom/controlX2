@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.screens.TempRatePreview
import com.jwoglom.controlx2.presentation.screens.sections.components.IntegerOutlinedText
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun TempRateWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    closeWindow: () -> Unit,
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val context = LocalContext.current
    val dataStore = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val percentRawValue = dataStore.tempRatePercentRawValue.observeAsState()
    val hoursRawValue = dataStore.tempRateHoursRawValue.observeAsState()
    val minutesRawValue = dataStore.tempRateMinutesRawValue.observeAsState()

    var percentSubtitle by remember { mutableStateOf<String>("percent") }
    var hoursSubtitle by remember { mutableStateOf<String>("hours") }
    var minutesSubtitle by remember { mutableStateOf<String>("mins") }

    var percentHumanEntered by remember { mutableStateOf<Int?>(null) }
    var percentHumanFocus by remember { mutableStateOf(false) }
    var hoursHumanEntered by remember { mutableStateOf<Int?>(null) }
    var minutesHumanEntered by remember { mutableStateOf<Int?>(null) }

    var tempRateButtonEnabled by remember { mutableStateOf(false) }
    var tempRate by remember { mutableStateOf<SetTempRateRequest?>(null) }

    var showPermissionCheckDialog by remember { mutableStateOf(false) }

    val commands = listOf(
        TempRateRequest()
    )

    val baseFields = listOf(
        dataStore.tempRateActive,
        dataStore.tempRateDetails
    )

    @Synchronized
    fun waitForLoaded() = refreshScope.launch {
        if (!refreshing) return@launch;
        var sinceLastFetchTime = 0
        while (true) {
            val nullBaseFields = baseFields.filter { field -> field.value == null }.toSet()
            if (nullBaseFields.isEmpty()) {
                break
            }

            Timber.i("TempRateWindow loading: remaining ${nullBaseFields.size}: ${baseFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("TempRateWindow loading re-fetching")
                // for safety reasons, NEVER CACHE.
                sendPumpCommands(SendType.STANDARD, commands)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("TempRateWindow base loading done: ${baseFields.map { it.value }}")
        if (sinceLastFetchTime == 0) {
            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true

        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    LifecycleStateObserver(
        lifecycleOwner = LocalLifecycleOwner.current,
        onStop = {
            resetTempRateDataStoreState(dataStore)
        }
    ) {
        resetTempRateDataStoreState(dataStore)
        refresh()
    }

    LaunchedEffect (refreshing, Unit) {
        waitForLoaded()
    }

    fun recalculate() {
        percentHumanEntered = if (percentRawValue.value != null) rawToInt(percentRawValue.value) else null
        hoursHumanEntered = if (hoursRawValue.value != null) rawToInt(hoursRawValue.value) else null
        minutesHumanEntered = if (minutesRawValue.value != null) rawToInt(minutesRawValue.value) else null
    }

    fun validTempRate(rawPercent: Int?, rawHours: Int?, rawMinutes: Int?): SetTempRateRequest? {
        if (rawPercent == null || rawPercent < 0 || rawPercent > 250) {
            return null
        }

        if (rawHours == null && rawMinutes == null) {
            return null
        }

        var hours = 0
        var minutes = 0
        if (rawHours != null && rawMinutes != null) {
            hours = rawHours
            minutes = rawMinutes
        } else if (rawHours == null && rawMinutes != null) {
            hours = 0
            minutes = rawMinutes
        } else if (rawHours != null && rawMinutes == null) {
            hours = rawHours
            minutes = 0
        } else {
            return null
        }

        if (hours < 0 || hours > 72 || minutes < 0 || minutes >= 60) {
            return null
        }

        if (60*hours + minutes > 72*60) {
            return null
        }

        return SetTempRateRequest(
            60*hours + minutes,
            rawPercent

        )
    }

    LaunchedEffect (percentRawValue.value, hoursRawValue.value, minutesRawValue.value) {
        recalculate()
        val request = validTempRate(percentHumanEntered, hoursHumanEntered, minutesHumanEntered)
        tempRateButtonEnabled = request != null
    }

    HeaderLine("Temp Rate")

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.75f)) {}
        Column(Modifier.weight(1f)) {
            IntegerOutlinedText(
                title = percentSubtitle,
                value = percentRawValue.value,
                onValueChange = {
                    dataStore.tempRatePercentRawValue.value = it
                    percentHumanEntered = if (it == "") null else it.toIntOrNull()
                },
                modifier = Modifier.onFocusChanged {
                    percentHumanFocus = it.isFocused
                }
            )
        }
        Column(Modifier.weight(0.75f)) {}
    }
    
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.5f)) {}
        Column(
            Modifier
                .weight(0.75f)
                .padding(all = 8.dp)) {
            IntegerOutlinedText(
                title = hoursSubtitle,
                value = hoursRawValue.value,
                onValueChange = { dataStore.tempRateHoursRawValue.value = it }
            )
        }

        Column(
            Modifier
                .weight(0.75f)
                .padding(all = 8.dp)) {
            IntegerOutlinedText(
                title = minutesSubtitle,
                value = minutesRawValue.value,
                onValueChange = {
                    dataStore.tempRateMinutesRawValue.value = it
                    hoursHumanEntered = if (it == "") null else it.toIntOrNull()
                }
            )
        }
        Column(Modifier.weight(0.5f)) {}
    }


    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {

            Button(
                onClick = {
                    tempRate = validTempRate(percentHumanEntered, hoursHumanEntered, minutesHumanEntered)
                    if (tempRate != null) {
                        showPermissionCheckDialog = true
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = tempRateButtonEnabled,
                colors = if (isSystemInDarkTheme()) ButtonDefaults.filledTonalButtonColors(containerColor = Color.LightGray) else ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.align(Alignment.Center)

            ) {
                Image(
                    painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    "Set temp rate",
                    fontSize = 18.sp,
                    color = if (isSystemInDarkTheme()) Color.Black else Color.Unspecified
                )
            }
        }
    }

    if (showPermissionCheckDialog) {
        fun sendTempRateRequest() {
            if (tempRate == null) {
                Timber.w("sendTempRateRequest: null parameter")
                return
            }

            sendPumpCommands(SendType.BUST_CACHE, listOf(tempRate as Message))
        }

        fun prettyDuration(minutes: Int?): String {
            return "${minutes?.div(60)}h${minutes?.rem(60)}m"
        }

        AlertDialog(
            onDismissRequest = {
                showPermissionCheckDialog = false
            },
            title = {
                Text("Set ${tempRate?.percent}% temp rate for ${prettyDuration(tempRate?.minutes)}?")
            },
            icon = {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionCheckDialog = false
                        resetTempRateDataStoreState(dataStore)
                    },
                ) {
                    Text("Cancel")
                }

            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sendTempRateRequest()
                    },
                    enabled = (
                        tempRate != null
                    )
                ) {
                    Text("Deliver")
                }
            }
        )
    }
}

fun resetTempRateDataStoreState(dataStore: DataStore) {
    Timber.d("tempRate resetTempRateDataStoreState")
    dataStore.tempRatePercentRawValue.value = null
    dataStore.tempRateHoursRawValue.value = null
    dataStore.tempRateMinutesRawValue.value = null
}

@Preview
@Composable
fun DefaultTempRateWindow() {
    TempRatePreview()
}

@Preview
@Composable
fun DefaultTempRateWindow_Filled() {
    LocalDataStore.current.tempRatePercentRawValue.value = "150"
    LocalDataStore.current.tempRateHoursRawValue.value = "1"
    LocalDataStore.current.tempRateMinutesRawValue.value = "30"
    TempRatePreview()
}