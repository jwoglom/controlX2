@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBolusStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.screens.BolusPreview
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.screens.sections.components.IntegerOutlinedText
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.snakeCaseToSpace
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun BolusWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse) -> Unit,
    sendServiceBolusCancel: () -> Unit,
    closeWindow: () -> Unit,
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val context = LocalContext.current
    val dataStore = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val unitsRawValue = dataStore.bolusUnitsRawValue.observeAsState()
    val carbsRawValue = dataStore.bolusCarbsRawValue.observeAsState()
    val glucoseRawValue = dataStore.bolusGlucoseRawValue.observeAsState()

    var unitsSubtitle by remember { mutableStateOf<String>("Units") }
    var carbsSubtitle by remember { mutableStateOf<String>("Carbs (g)") }
    var glucoseSubtitle by remember { mutableStateOf<String>("BG (mg/dL)") }

    var unitsHumanEntered by remember { mutableStateOf<Double?>(null) }
    var unitsHumanFocus by remember { mutableStateOf(false) }
    var glucoseHumanEntered by remember { mutableStateOf<Int?>(null) }

    var bolusButtonEnabled by remember { mutableStateOf(false) }

    val cgmReading = dataStore.cgmReading.observeAsState()
    val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
    val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
    val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()
    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()
    val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
    val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()

    val bolusConditionsPrompt = dataStore.bolusConditionsPrompt.observeAsState()

    var showPermissionCheckDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }
    var showApprovedDialog by remember { mutableStateOf(false) }
    var showCancellingDialog by remember { mutableStateOf(false) }
    var showCancelledDialog by remember { mutableStateOf(false) }

    val commands = listOf(
        BolusCalcDataSnapshotRequest(),
        LastBGRequest(),
        TimeSinceResetRequest()
    )

    val baseFields = listOf(
        dataStore.bolusCalcDataSnapshot,
        dataStore.bolusCalcLastBG
    )

    val calculatedFields = listOf(
        dataStore.bolusCalculatorBuilder,
        dataStore.bolusCurrentParameters
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

            Timber.i("BolusScreen loading: remaining ${nullBaseFields.size}: ${baseFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("BolusScreen loading re-fetching")
                // for safety reasons, NEVER CACHE.
                sendPumpCommands(SendType.STANDARD, commands)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        Timber.i("BolusScreen base loading done: ${baseFields.map { it.value }}")
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
            resetBolusDataStoreState(dataStore)
        }
    ) {
        resetBolusDataStoreState(dataStore)
        refresh()
    }

    LaunchedEffect (refreshing, Unit) {
        waitForLoaded()
    }

    fun recalculate() {
        dataStore.bolusCalculatorBuilder.value = buildBolusCalculator(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            if (unitsHumanEntered != null) rawToDouble(unitsRawValue.value) else null,
            rawToInt(carbsRawValue.value),
            rawToInt(glucoseRawValue.value)
        )

        dataStore.bolusCurrentParameters.value =
            bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value).first

        fun sortConditions(set: Set<BolusCalcCondition>?): List<BolusCalcCondition> {
            if (set == null) {
                return emptyList()
            }

            return set.sortedWith(
                compareBy(
                    { it.javaClass == BolusCalcCondition.FailedSanityCheck::class.java },
                    { it.javaClass == BolusCalcCondition.FailedPrecondition::class.java },
                    { it.javaClass == BolusCalcCondition.WaitingOnPrecondition::class.java },
                    { it.javaClass == BolusCalcCondition.Decision::class.java },
                    { it.javaClass == BolusCalcCondition.NonActionDecision::class.java },
                )
            )
        }
        val conditions = bolusCalcDecision(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value)?.conditions
        dataStore.bolusCurrentConditions.value = sortConditions(conditions)

        val conditionsWithPrompt = conditions?.filter {
            it.prompt != null
        }
        val conditionsAcknowledged = dataStore.bolusConditionsPromptAcknowledged.value
        Timber.d("bolusCalc conditionsWithPrompt: $conditionsWithPrompt conditionsAcknowledged: $conditionsAcknowledged conditions: $conditions")
        if (conditionsWithPrompt?.size == 1) {
            var alreadyAcked = false
            conditionsAcknowledged?.forEach {
                if (conditionsWithPrompt[0] == it) {
                    alreadyAcked = true
                }
            }
            if (alreadyAcked) {
                Timber.d("bolusCalc: Not displaying acknowledged prompt again: $conditionsAcknowledged")
                dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>()
            } else {
                dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>().let {
                    it.addAll(conditionsWithPrompt)
                    it
                }
                // showBolusConditionPrompt = true
            }
        } else {
            dataStore.bolusConditionsPrompt.value = null
            // showBolusConditionPrompt = false
        }

        if (dataStore.bolusCurrentParameters.value != null &&
            dataStore.bolusCurrentParameters.value!!.units >= 0.0 &&
            unitsHumanEntered == null &&
            !unitsHumanFocus
        ) {
            dataStore.bolusUnitsRawValue.value = twoDecimalPlaces(dataStore.bolusCurrentParameters.value!!.units)
        }

        // TODO: invalid logic for attributing override vs pre-filled units
        unitsSubtitle = when {
            unitsHumanEntered == null -> when {
                dataStore.bolusCurrentParameters.value == null -> "Units"
                dataStore.bolusCurrentParameters.value?.units == 0.0 -> "Units"
                else -> "Calculated"
            }
            else -> "Override"
        }

        val autofilledBg = dataStore.bolusCalculatorBuilder.value?.glucoseMgdl?.orElse(null)
        if (dataStore.bolusCurrentParameters.value != null &&
            dataStore.bolusCurrentParameters.value!!.glucoseMgdl > 0 &&
            autofilledBg != null
        ) {
            dataStore.bolusGlucoseRawValue.value = "$autofilledBg"
        }
        glucoseSubtitle = when {
            glucoseHumanEntered != null -> "Entered (mg/dL)"
            autofilledBg != null -> "CGM (mg/dL)"
            else -> "BG (mg/dL)"
        }
    }

    LaunchedEffect (cgmReading) {
        refresh()
    }

    LaunchedEffect (unitsRawValue.value, carbsRawValue.value, glucoseRawValue.value, bolusCalcDataSnapshot.value, bolusCalcLastBG.value) {
        recalculate()
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.75f)) {}
        Column(Modifier.weight(1f)) {
            DecimalOutlinedText(
                title = unitsSubtitle,
                value = unitsRawValue.value,
                onValueChange = {
                    dataStore.bolusUnitsRawValue.value = it
                    unitsHumanEntered = if (it == "") null else it.toDoubleOrNull()
                },
                modifier = Modifier.onFocusChanged {
                    unitsHumanFocus = it.isFocused
                }
            )
        }
        Column(Modifier.weight(0.75f)) {}
    }
    
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)) {
            IntegerOutlinedText(
                title = carbsSubtitle,
                value = carbsRawValue.value,
                onValueChange = { dataStore.bolusCarbsRawValue.value = it }
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)) {
            IntegerOutlinedText(
                title = glucoseSubtitle,
                value = glucoseRawValue.value,
                onValueChange = {
                    dataStore.bolusGlucoseRawValue.value = it
                    glucoseHumanEntered = if (it == "") null else it.toIntOrNull()
                }
            )
        }
    }

    fun validBolus(params: BolusParameters?): Boolean {
        if (params == null) {
            return false
        }

        if (params.units < 0.05) {
            return false
        }

        if (params.units >= (InsulinUnit.from1000To1(bolusCalcDataSnapshot.value?.maxBolusAmount?.toLong() ?: 25000))) {
            return false
        }

        return true
    }

    bolusConditionsPrompt.value?.forEach {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${it.msg}: ")
                    }
                    append(it.prompt?.promptMessage)
                }, Modifier.padding(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(Modifier.weight(3f)) {}

                Column(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value =
                                        mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value == null) {
                                    dataStore.bolusConditionsExcluded.value =
                                        mutableSetOf(it.first())
                                } else {
                                    dataStore.bolusConditionsExcluded.value?.add(it.first())
                                }

                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Reject"
                        )
                    }
                }


                Column(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value =
                                        mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value != null) {
                                    dataStore.bolusConditionsExcluded.value?.remove(it.first())
                                }

                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Apply"
                        )
                    }
                }
            }
        }
    }


    val bolusConditionsPromptAcknowledged = dataStore.bolusConditionsPromptAcknowledged.observeAsState()

    if (bolusConditionsPromptAcknowledged.value != null && bolusConditionsPromptAcknowledged.value!!.size > 0) {
        bolusConditionsPromptAcknowledged.value?.forEach {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        Timber.i("bolusConditionsPromptAcknowledged click")
                        dataStore.bolusConditionsPrompt.value =
                            mutableListOf<BolusCalcCondition>().let {
                                it.addAll(bolusConditionsPromptAcknowledged.value!!)
                                it
                            }
                        dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf()
                        dataStore.bolusConditionsExcluded.value = mutableSetOf()
                    }) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(when {
                            bolusConditionsExcluded.value?.contains(it) == true -> it.prompt?.whenIgnoredNotice ?: it.msg
                            else -> it.prompt?.whenAcceptedNotice ?: it.msg
                        })
                    }
                }, Modifier.padding(8.dp))
            }
        }
    }

    LaunchedEffect (bolusCurrentParameters.value) {
        bolusButtonEnabled = validBolus(bolusCurrentParameters.value)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            fun performPermissionCheck() {
                showPermissionCheckDialog = true
                sendPumpCommands(SendType.BUST_CACHE, listOf(BolusPermissionRequest()))
            }

            Button(
                onClick = {
                    if (validBolus(bolusCurrentParameters.value)) {
                        dataStore.bolusFinalConditions.value =
                            bolusCalcDecision(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value)?.conditions

                        val pair = bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value)
                        dataStore.bolusFinalParameters.value = pair.first
                        dataStore.bolusFinalCalcUnits.value = pair.second
                        performPermissionCheck()
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = bolusButtonEnabled,
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
                    "Deliver ${bolusCurrentParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus",
                    fontSize = 18.sp,
                    color = if (isSystemInDarkTheme()) Color.Black else Color.Unspecified
                )
            }
        }
    }

    if (showPermissionCheckDialog) {
        fun sendBolusRequest(bolusParameters: BolusParameters?, unitBreakdown: BolusCalcUnits?, dataSnapshot: BolusCalcDataSnapshotResponse?, timeSinceReset: TimeSinceResetResponse?) {
            if (bolusParameters == null || dataStore.bolusPermissionResponse.value == null || dataStore.bolusCalcDataSnapshot.value == null || unitBreakdown == null || dataSnapshot == null || timeSinceReset == null) {
                Timber.w("sendBolusRequest: null parameters")
                return
            }

            val bolusId = dataStore.bolusPermissionResponse.value!!.bolusId

            Timber.i("sendBolusRequest: sending bolus request to phone: bolusId=$bolusId bolusParameters=$bolusParameters unitBreakdown=$unitBreakdown dataSnapshot=$dataSnapshot timeSinceReset=$timeSinceReset")
            sendServiceBolusRequest(bolusId, bolusParameters, unitBreakdown, dataSnapshot, timeSinceReset)
        }

        AlertDialog(
            onDismissRequest = {
                showPermissionCheckDialog = false
            },
            title = {
                Text("Deliver ${bolusCurrentParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus?")
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
                    },
                ) {
                    Text("Cancel")
                }

            },
            confirmButton = {
                TextButton(
                    onClick = {
                        bolusFinalParameters.value?.let { finalParameters ->
                            bolusPermissionResponse.value?.let { permissionResponse ->
                                if (permissionResponse.isPermissionGranted && finalParameters.units >= 0.05) {
                                    showPermissionCheckDialog = false
                                    showInProgressDialog = true
                                    sendBolusRequest(
                                        dataStore.bolusFinalParameters.value,
                                        dataStore.bolusFinalCalcUnits.value,
                                        dataStore.bolusCalcDataSnapshot.value,
                                        dataStore.timeSinceResetResponse.value,
                                    )
                                }
                            }
                        }
                    },
                    enabled = (
                        bolusPermissionResponse.value?.isPermissionGranted == true &&
                        bolusFinalParameters.value?.takeIf { it.units >= 0.05 } != null
                    )
                ) {
                    Text("Deliver")
                }
            }
        )
    }

    fun cancelBolus() {
        Timber.i("cancelBolus()")
        fun performCancel() {
            Timber.i("cancelBolus performCancel(): ${bolusPermissionResponse.value?.bolusId}")
            bolusPermissionResponse.value?.bolusId?.let { bolusId ->
                sendPumpCommands(SendType.BUST_CACHE, listOf(CancelBolusRequest(bolusId)))
            }
        }
        showCancellingDialog = true
        var time = 0
        fun checkFunc() {
            if (dataStore.bolusCancelResponse.value == null) {
                if (time >= 5000) {
                    performCancel()
                    time = 0
                }
                time += 500
                mainHandler.postDelayed({ checkFunc() }, 500)
            } else {
                showCancellingDialog = false
                showCancelledDialog = true
                dataStore.bolusFinalConditions.value = null
                dataStore.bolusFinalParameters.value = null
                sendServiceBolusCancel()
            }
        }
        checkFunc()
    }

    if (showInProgressDialog) {
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()

        val bolusMinNotifyThreshold = Prefs(context).bolusConfirmationInsulinThreshold()

        LaunchedEffect(bolusInitiateResponse.value) {
            if (bolusInitiateResponse.value != null) {
                showInProgressDialog = false
                showApprovedDialog = true
            }
        }

        LaunchedEffect(bolusCancelResponse.value) {
            if (bolusCancelResponse.value != null) {
                showInProgressDialog = false
                showCancelledDialog = true
            }
        }

        AlertDialog(
            onDismissRequest = {},
            icon = {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
            },
            title = {
                Text("Requesting ${bolusCurrentParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus")
            },
            text = {
                Text(when {
                    bolusInitiateResponse.value != null -> "Bolus request received by pump, waiting for response..."
                    bolusFinalParameters.value != null -> when {
                        bolusFinalParameters.value!!.units >= bolusMinNotifyThreshold -> "A notification was sent to approve the request."
                        else -> "Sending request to pump..."
                    }
                    else -> "Sending request to pump..."
                })
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInProgressDialog = false
                        cancelBolus()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Cancel Bolus Delivery")
                }
            },
            confirmButton = {
            }
        )
    }

    if (showApprovedDialog) {
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val bolusCurrentResponse = dataStore.bolusCurrentResponse.observeAsState()

        AlertDialog(
            onDismissRequest = {
                showCancellingDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            },
            icon = {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
            },
            title = {
                Text(when {
                    bolusInitiateResponse.value != null -> when {
                        bolusInitiateResponse.value!!.wasBolusInitiated() -> "Bolus Initiated"
                        else -> "Bolus Rejected by Pump"
                    }
                    else -> "Fetching Bolus Status..."
                })
            },
            text = {
                LaunchedEffect(Unit) {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                }

                // When bolusCurrentResponse is updated, re-request it
                LaunchedEffect(bolusCurrentResponse.value) {
                    Timber.i("bolusCurrentResponse: ${bolusCurrentResponse.value}")
                    // when a bolusId=0 is returned, the current bolus session has ended so the message
                    // no longer contains any useful data.
                    if (bolusCurrentResponse.value?.bolusId != 0) {
                        mainHandler.postDelayed({
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                listOf(CurrentBolusStatusRequest())
                            )
                        }, 1000)
                    }
                }

                Text(
                    text = when {
                        bolusInitiateResponse.value != null -> when {
                            bolusInitiateResponse.value!!.wasBolusInitiated() -> "The ${
                                bolusFinalParameters.value?.let {
                                    twoDecimalPlaces(
                                        it.units
                                    )
                                }
                            }u bolus ${
                                when (bolusCurrentResponse.value) {
                                    null -> "was requested."
                                    else -> when (bolusCurrentResponse.value!!.status) {
                                        CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "is being prepared."
                                        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "is being delivered."
                                        else -> "was completed."
                                    }
                                }
                            }"
                            else -> "The bolus could not be delivered: ${
                                bolusInitiateResponse.value?.let {
                                    snakeCaseToSpace(
                                        it.statusType.toString()
                                    )
                                }
                            }"
                        }
                        else -> "The bolus status is unknown. Please check your pump to identify the status of the bolus."
                    }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showApprovedDialog = false
                        cancelBolus()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Cancel Bolus Delivery")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancellingDialog = false
                        resetBolusDataStoreState(dataStore)
                        closeWindow()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showCancellingDialog) {
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()

        LaunchedEffect(bolusCancelResponse.value) {
            if (bolusCancelResponse.value != null) {
                showCancellingDialog = false
                showCancelledDialog = true
            }
        }

        AlertDialog(
            onDismissRequest = {
                showCancellingDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            },
            icon = {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
            },
            title = {
                Text("Cancelling..")
            },
            text = {
                Text("The bolus is being cancelled...")
            },
            confirmButton = {}
        )
    }

    if (showCancelledDialog) {
        val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
        val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
        val lastBolusStatusResponse = dataStore.lastBolusStatusResponse.observeAsState()

        LaunchedEffect (bolusCancelResponse.value, Unit) {
            Timber.d("showCancelledDialog querying LastBolusStatus")
            sendPumpCommands(SendType.STANDARD, listOf(LastBolusStatusV2Request()))
            mainHandler.postDelayed({
                sendPumpCommands(SendType.STANDARD, listOf(LastBolusStatusV2Request()))
            }, 500)
        }

        fun matchesBolusId(): Boolean? {
            lastBolusStatusResponse.value?.let { last ->
                bolusInitiateResponse.value?.let { initiate ->
                    return (last.bolusId == initiate.bolusId)
                }
            }
            return null
        }

        LaunchedEffect (lastBolusStatusResponse.value) {
            Timber.d("showCancelledDialog lastBolusStatusResponse effect: ${matchesBolusId()}")
            if (matchesBolusId() == false) {
                mainHandler.postDelayed({
                    Timber.d("showCancelledDialog lastBolusStatus postDelayed")
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(LastBolusStatusV2Request())
                    )
                }, 500)
            }
        }

        AlertDialog(
            onDismissRequest = {
                showCancelledDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            },
            icon = {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
            },
            title = {
                Text(when (bolusCancelResponse.value?.status) {
                    CancelBolusResponse.CancelStatus.SUCCESS ->
                        "Bolus Cancelled"
                    CancelBolusResponse.CancelStatus.FAILED ->
                        when (bolusInitiateResponse.value) {
                            null -> "Bolus Cancelled"
                            else -> "Could Not Be Cancelled"
                        }
                    else -> "Bolus Status Unknown"
                })
            },
            text = {
                Text(
                    "${when (bolusCancelResponse.value?.status) {
                    CancelBolusResponse.CancelStatus.SUCCESS ->
                        "The bolus was cancelled."
                    CancelBolusResponse.CancelStatus.FAILED ->
                        when (bolusInitiateResponse.value) {
                            null -> "A bolus request was not sent to the pump, so there is nothing to cancel."
                            else -> "The bolus could not be cancelled: ${
                                snakeCaseToSpace(
                                    bolusCancelResponse.value?.reason.toString()
                                )
                            }"
                        }
                    else -> "Please check your pump to confirm whether the bolus was cancelled."
                    }}\n\n${when {
                        matchesBolusId() == true ->
                            lastBolusStatusResponse.value?.deliveredVolume?.let {
                                if (it == 0L) "A bolus was started and no insulin was delivered." else "${twoDecimalPlaces1000Unit(it)}u was delivered."
                            } ?: ""
                        matchesBolusId() == false -> "No insulin was delivered."
                        else -> "Checking if any insulin was delivered..."
                    }}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelledDialog = false
                        resetBolusDataStoreState(dataStore)
                        closeWindow()
                    },
                ) {
                    Text("OK")
                }
            }
        )
    }
}


fun buildBolusCalculator(
    dataSnapshot: BolusCalcDataSnapshotResponse?,
    lastBG: LastBGResponse?,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?
): BolusCalculatorBuilder {
    Timber.i("buildBolusCalculator: INPUT units=$bolusUnitsUserInput carbs=$bolusCarbsGramsUserInput bg=$bolusBgMgdlUserInput dataSnapshot=$dataSnapshot lastBG=$lastBG")
    val bolusCalc = BolusCalculatorBuilder()
    if (dataSnapshot != null) {
        bolusCalc.onBolusCalcDataSnapshotResponse(dataSnapshot)
    }
    if (lastBG != null) {
        bolusCalc.onLastBGResponse(lastBG)
    }

    bolusCalc.setInsulinUnits(bolusUnitsUserInput)
    bolusCalc.setCarbsValueGrams(bolusCarbsGramsUserInput)
    bolusCalc.setGlucoseMgdl(bolusBgMgdlUserInput)
    return bolusCalc
}
fun bolusCalcParameters(
    bolusCalc: BolusCalculatorBuilder?,
    bolusConditionsExcluded: MutableSet<BolusCalcCondition>?
): Pair<BolusParameters, BolusCalcUnits> {
    val decision = bolusCalcDecision(bolusCalc, bolusConditionsExcluded)
    return Pair(
        BolusParameters(
            decision?.units?.total,
            bolusCalc?.carbsValueGrams?.orElse(0),
            bolusCalc?.glucoseMgdl?.orElse(0)
        ),
        decision!!.units!!)
}

fun bolusCalcDecision(
    bolusCalc: BolusCalculatorBuilder?,
    bolusConditionsExcluded: MutableSet<BolusCalcCondition>?
): BolusCalcDecision? {
    val excluded = (bolusConditionsExcluded ?: mutableSetOf()).stream().toArray { arrayOfNulls<BolusCalcCondition>(it) }
    val decision = bolusCalc?.build()?.parse(*excluded)
    Timber.i("bolusCalcDecision: OUTPUT units=${decision?.units} conditions=${decision?.conditions} excluded=$excluded")
    return decision
}

fun rawToDouble(s: String?): Double? {
    return if (s == "") 0.0 else s?.toDoubleOrNull()
}

fun rawToInt(s: String?): Int? {
    return if (s == "") 0 else s?.toIntOrNull()
}

fun resetBolusDataStoreState(dataStore: DataStore) {
    Timber.d("bolusCalc resetBolusDataStoreState")
    dataStore.bolusPermissionResponse.value = null
    dataStore.bolusCancelResponse.value = null
    dataStore.bolusInitiateResponse.value = null
    dataStore.lastBolusStatusResponse.value = null
    dataStore.bolusCalculatorBuilder.value = null
    dataStore.bolusCurrentParameters.value = null
    dataStore.bolusCurrentConditions.value = null
    dataStore.bolusConditionsPrompt.value = null
    dataStore.bolusConditionsPromptAcknowledged.value = null
    dataStore.bolusConditionsExcluded.value = null
    dataStore.bolusFinalParameters.value = null
    dataStore.bolusFinalCalcUnits.value = null
    dataStore.bolusFinalConditions.value = null
    dataStore.bolusUnitsRawValue.value = null
    dataStore.bolusCarbsRawValue.value = null
    dataStore.bolusGlucoseRawValue.value = null
}

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}