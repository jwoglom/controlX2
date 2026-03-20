@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.navigation.TempRateInputPrefill
import com.jwoglom.controlx2.presentation.screens.TempRatePreview
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.screens.sections.components.IntegerOutlinedText
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.roundToInt

@Composable
fun TempRateWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    prefill: TempRateInputPrefill? = null,
    onPrefillConsumed: () -> Unit = {},
    closeWindow: () -> Unit,
) {
    val dataStore = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    val percentRawValue = dataStore.tempRatePercentRawValue.observeAsState()
    val hoursRawValue = dataStore.tempRateHoursRawValue.observeAsState()
    val minutesRawValue = dataStore.tempRateMinutesRawValue.observeAsState()
    val currentBasalRateRaw = dataStore.basalRate.observeAsState()

    val focusManager = LocalFocusManager.current
    val durationHoursFocusRequester = remember { FocusRequester() }
    val durationMinutesFocusRequester = remember { FocusRequester() }

    var percentSubtitle by remember { mutableStateOf<String>("percent") }
    var hoursSubtitle by remember { mutableStateOf<String>("hours") }
    var minutesSubtitle by remember { mutableStateOf<String>("mins") }

    var inputMode by remember { mutableStateOf(TempRateInputMode.PERCENT) }
    var percentHumanEntered by remember { mutableStateOf<Int?>(null) }
    var unitsRawValue by remember { mutableStateOf<String?>(null) }
    var unitsHumanEntered by remember { mutableStateOf<Double?>(null) }
    var hoursHumanEntered by remember { mutableStateOf<Int?>(null) }
    var minutesHumanEntered by remember { mutableStateOf<Int?>(null) }

    var tempRateButtonEnabled by remember { mutableStateOf(false) }
    var tempRate by remember { mutableStateOf<SetTempRateRequest?>(null) }
    var tempRateError by remember { mutableStateOf<String?>(null) }
    var effectiveBasalPreview by remember { mutableStateOf<String?>(null) }
    var effectivePercentPreview by remember { mutableStateOf<String?>(null) }
    var pendingPrefill by remember(prefill) { mutableStateOf(prefill) }

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

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("TempRateWindow base loading done: ${baseFields.map { it.value }}")
        if (sinceLastFetchTime == 0) {
            delay(250)
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true

        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    fun applyPrefill(prefillValues: TempRateInputPrefill) {
        dataStore.tempRatePercentRawValue.value = prefillValues.percentRawValue
        dataStore.tempRateHoursRawValue.value = prefillValues.hoursRawValue
        dataStore.tempRateMinutesRawValue.value = prefillValues.minutesRawValue
        percentHumanEntered = prefillValues.percentRawValue?.toIntOrNull()
        hoursHumanEntered = prefillValues.hoursRawValue?.toIntOrNull()
        minutesHumanEntered = prefillValues.minutesRawValue?.toIntOrNull()
    }

    LifecycleStateObserver(
        lifecycleOwner = LocalLifecycleOwner.current,
        onStop = {
            resetTempRateDataStoreState(dataStore)
        }
    ) {
        resetTempRateDataStoreState(dataStore)
        pendingPrefill?.let {
            applyPrefill(it)
            pendingPrefill = null
            onPrefillConsumed()
        }
        refresh()
    }

    LaunchedEffect (refreshing, Unit) {
        waitForLoaded()
    }

    fun recalculate() {
        percentHumanEntered = if (percentRawValue.value != null) rawToInt(percentRawValue.value) else null
        hoursHumanEntered = if (hoursRawValue.value != null) rawToInt(hoursRawValue.value) else null
        minutesHumanEntered = if (minutesRawValue.value != null) rawToInt(minutesRawValue.value) else null
        unitsHumanEntered = if (unitsRawValue != null) unitsRawValue?.toDoubleOrNull() else null
    }

    fun parseCurrentBasalRate(): Double? {
        val raw = currentBasalRateRaw.value ?: return null
        val numeric = raw.filter { it.isDigit() || it == '.' }
        return numeric.toDoubleOrNull()
    }

    fun buildTempRateValidationResult(
        mode: TempRateInputMode,
        rawPercent: Int?,
        rawUnits: Double?,
        rawHours: Int?,
        rawMinutes: Int?,
        currentBasalRate: Double?
    ): TempRateValidationResult {
        val derivedPercent = when (mode) {
            TempRateInputMode.PERCENT -> rawPercent
            TempRateInputMode.UNITS -> {
                if (rawUnits == null) {
                    null
                } else if (currentBasalRate == null || currentBasalRate <= 0.0) {
                    return TempRateValidationResult(error = "Current profile basal rate is unavailable.")
                } else {
                    ((rawUnits / currentBasalRate) * 100.0).roundToInt()
                }
            }
        }

        if (mode == TempRateInputMode.PERCENT) {
            if (rawPercent == null) {
                return TempRateValidationResult(error = "Enter a temp basal percent.")
            }
            if (rawPercent < 0 || rawPercent > 250) {
                return TempRateValidationResult(error = "Percent must be between 0 and 250.")
            }
        }

        if (mode == TempRateInputMode.UNITS) {
            if (rawUnits == null) {
                return TempRateValidationResult(error = "Enter a temp basal rate in U/hr.")
            }
            if (rawUnits < 0.0) {
                return TempRateValidationResult(error = "Temp basal units must be 0 or greater.")
            }
            if (rawUnits != 0.0 && rawUnits < 0.05) {
                return TempRateValidationResult(error = "Temp basal units must be 0 or at least 0.05 U/hr.")
            }
            if (derivedPercent == null || derivedPercent > 250) {
                return TempRateValidationResult(error = "Effective percent cannot exceed 250%.")
            }
        }

        val hours = rawHours ?: 0
        val minutes = rawMinutes ?: 0
        if (rawHours == null && rawMinutes == null) {
            return TempRateValidationResult(error = "Enter a temp basal duration.")
        }
        if (hours < 0 || hours > 72 || minutes < 0 || minutes >= 60) {
            return TempRateValidationResult(error = "Duration must be 0-72h and 0-59m.")
        }

        val totalMinutes = (60 * hours) + minutes
        if (totalMinutes < 15) {
            return TempRateValidationResult(error = "Duration must be at least 15 minutes.")
        }
        if (totalMinutes > 72 * 60) {
            return TempRateValidationResult(error = "Duration cannot exceed 72 hours.")
        }

        if (derivedPercent == null || derivedPercent < 0 || derivedPercent > 250) {
            return TempRateValidationResult(error = "Effective percent must be between 0 and 250.")
        }

        return try {
            TempRateValidationResult(
                request = SetTempRateRequest(totalMinutes, derivedPercent),
                effectivePercent = derivedPercent.toDouble(),
                effectiveBasalRate = currentBasalRate?.times(derivedPercent / 100.0)
            )
        } catch (e: IllegalArgumentException) {
            TempRateValidationResult(error = "Invalid temp basal request.")
        }
    }

    fun submitIfValid() {
        val result = buildTempRateValidationResult(
            mode = inputMode,
            rawPercent = percentHumanEntered,
            rawUnits = unitsHumanEntered,
            rawHours = hoursHumanEntered,
            rawMinutes = minutesHumanEntered,
            currentBasalRate = parseCurrentBasalRate()
        )
        tempRateError = result.error
        tempRate = result.request
        if (result.request != null) {
            showPermissionCheckDialog = true
            focusManager.clearFocus()
        }
    }

    LaunchedEffect (percentRawValue.value, unitsRawValue, hoursRawValue.value, minutesRawValue.value, inputMode, currentBasalRateRaw.value) {
        recalculate()
        val result = buildTempRateValidationResult(
            mode = inputMode,
            rawPercent = percentHumanEntered,
            rawUnits = unitsHumanEntered,
            rawHours = hoursHumanEntered,
            rawMinutes = minutesHumanEntered,
            currentBasalRate = parseCurrentBasalRate()
        )
        tempRateButtonEnabled = result.request != null
        tempRateError = result.error
        effectiveBasalPreview = result.effectiveBasalRate?.let { "Effective basal rate: ${"%.2f".format(it)} U/hr" }
        effectivePercentPreview = result.effectivePercent?.let { "Effective percent: ${"%.0f".format(it)}%" }
    }

    HeaderLine("Temp Rate")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            selected = inputMode == TempRateInputMode.PERCENT,
            onClick = { inputMode = TempRateInputMode.PERCENT },
            label = { Text("Percent") },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = inputMode == TempRateInputMode.UNITS,
            onClick = { inputMode = TempRateInputMode.UNITS },
            label = { Text("Units (U/hr)") }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.75f)) {}
        Column(Modifier.weight(1f)) {
            if (inputMode == TempRateInputMode.PERCENT) {
                IntegerOutlinedText(
                    title = percentSubtitle,
                    value = percentRawValue.value,
                    onValueChange = {
                        dataStore.tempRatePercentRawValue.value = it
                        percentHumanEntered = if (it == "") null else it.toIntOrNull()
                    },
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = {
                            durationHoursFocusRequester.requestFocus()
                        }
                    )
                )
            } else {
                DecimalOutlinedText(
                    title = "U/hr",
                    value = unitsRawValue,
                    onValueChange = {
                        unitsRawValue = it
                        unitsHumanEntered = if (it == "") null else it.toDoubleOrNull()
                    },
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = {
                            durationHoursFocusRequester.requestFocus()
                        }
                    )
                )
            }
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
                onValueChange = { dataStore.tempRateHoursRawValue.value = it },
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(
                    onNext = {
                        durationMinutesFocusRequester.requestFocus()
                    }
                ),
                modifier = Modifier.focusRequester(durationHoursFocusRequester)
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
                    minutesHumanEntered = if (it == "") null else it.toIntOrNull()
                },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = {
                        submitIfValid()
                    }
                ),
                modifier = Modifier.focusRequester(durationMinutesFocusRequester)
            )
        }
        Column(Modifier.weight(0.5f)) {}
    }

    tempRateError?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }

    val modePreviewText = when (inputMode) {
        TempRateInputMode.PERCENT -> effectiveBasalPreview
        TempRateInputMode.UNITS -> effectivePercentPreview
    }
    modePreviewText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
    }


    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (sending) {
            /* */
        } else {

            Button(
                onClick = {
                    submitIfValid()
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = tempRateButtonEnabled,
                colors = ButtonDefaults.filledTonalButtonColors(),
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
                    fontSize = 18.sp
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
                        refreshScope.launch {
                            showPermissionCheckDialog = false
                            sending = true
                            sendTempRateRequest()
                            closeWindow()
                        }
                        refreshScope.launch {
                            delay(250)
                        }
                        refreshScope.launch {
                            sendPumpCommands(
                                SendType.BUST_CACHE, listOf(
                                    TempRateRequest()
                                )
                            )
                        }
                    },
                    enabled = (
                        tempRate != null
                    )
                ) {
                    Text("Set temp rate")
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

private enum class TempRateInputMode {
    PERCENT,
    UNITS
}

private data class TempRateValidationResult(
    val request: SetTempRateRequest? = null,
    val error: String? = null,
    val effectiveBasalRate: Double? = null,
    val effectivePercent: Double? = null
)

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
