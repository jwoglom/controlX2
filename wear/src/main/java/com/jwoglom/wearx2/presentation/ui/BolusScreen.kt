package com.jwoglom.wearx2.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.rememberScalingLazyListState
import com.google.android.horologist.compose.navscaffold.scrollableColumn
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.*
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse.CancelStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.components.LifecycleStateObserver
import com.jwoglom.wearx2.presentation.components.LineTextDescription
import com.jwoglom.wearx2.shared.util.firstLetterCapitalized
import com.jwoglom.wearx2.shared.util.snakeCaseToSpace
import com.jwoglom.wearx2.shared.util.oneDecimalPlace
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import com.jwoglom.wearx2.util.SendType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BolusScreen(
    scalingLazyListState: ScalingLazyListState,
    focusRequester: FocusRequester,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?,
    onClickUnits: (Double?) -> Unit,
    onClickCarbs: () -> Unit,
    onClickBG: () -> Unit,
    onClickLanding: () -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendPhoneBolusRequest: (Int, BolusParameters) -> Unit,
    resetSavedBolusEnteredState: () -> Unit,
    sendPhoneBolusCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPermissionCheckDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }
    var showCancelledDialog by remember { mutableStateOf(false) }
    var showCancellingDialog by remember { mutableStateOf(false) }
    var showApprovedDialog by remember { mutableStateOf(false) }

    fun resetDialogs() {
        showPermissionCheckDialog = false
        showConfirmDialog = false
        showInProgressDialog = false
        showCancelledDialog = false
        showCancellingDialog = false
        showApprovedDialog = false
    }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val dataStore = LocalDataStore.current

    fun runBolusCalculator(
        dataSnapshot: BolusCalcDataSnapshotResponse?,
        lastBG: LastBGResponse?,
        bolusUnitsUserInput: Double?,
        bolusCarbsGramsUserInput: Int?,
        bolusBgMgdlUserInput: Int?
    ): BolusCalculatorBuilder {
        Timber.i("runBolusCalculator: INPUT units=$bolusUnitsUserInput carbs=$bolusCarbsGramsUserInput bg=$bolusBgMgdlUserInput dataSnapshot=$dataSnapshot lastBG=$lastBG")
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

    fun bolusCalcDecision(bolusCalc: BolusCalculatorBuilder?): BolusCalcDecision? {
        val decision = bolusCalc?.build()?.parse()
        Timber.i("bolusCalcDecision: OUTPUT units=${decision?.units} conditions=${decision?.conditions}")
        return decision
    }

    fun bolusCalcParameters(bolusCalc: BolusCalculatorBuilder?): BolusParameters {
        val decision = bolusCalc?.build()?.parse()
        return BolusParameters(
            decision?.units?.total,
            bolusCalc?.carbsValueGrams?.orElse(0),
            bolusCalc?.glucoseMgdl?.orElse(0)
        )
    }

    val commands = listOf(
        BolusCalcDataSnapshotRequest(),
        LastBGRequest()
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
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true

        baseFields.forEach { field -> field.value = null }
        calculatedFields.forEach { field -> field.value = null }
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
    }) {
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
        val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()

        LaunchedEffect(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            bolusUnitsUserInput,
            bolusCarbsGramsUserInput,
            bolusBgMgdlUserInput
        ) {
            dataStore.bolusCalculatorBuilder.value = runBolusCalculator(
                bolusCalcDataSnapshot.value,
                bolusCalcLastBG.value,
                bolusUnitsUserInput,
                bolusCarbsGramsUserInput,
                bolusBgMgdlUserInput
            )
            dataStore.bolusCurrentParameters.value =
                bolusCalcParameters(dataStore.bolusCalculatorBuilder.value)

            dataStore.bolusUnitsDisplayedText.value = when (bolusUnitsUserInput) {
                null -> when (dataStore.bolusCurrentParameters.value) {
                    null -> "None Entered"
                    else -> "Calculated: ${twoDecimalPlaces(dataStore.bolusCurrentParameters.value!!.units)}"
                }
                else -> "Entered: ${oneDecimalPlace(bolusUnitsUserInput)}"
            }

            val autofilledBg = dataStore.bolusCalculatorBuilder.value?.glucoseMgdl?.orElse(null)
            dataStore.bolusBGDisplayedText.value = when {
                bolusBgMgdlUserInput != null -> "Entered: $bolusBgMgdlUserInput"
                autofilledBg != null -> "From CGM: $autofilledBg"
                else -> "Not Entered"
            }
        }

        LaunchedEffect (Unit) {
            scalingLazyListState.animateScrollToItem(0)
        }

        ScalingLazyColumn(
            modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {
            item {
                val bolusUnitsDisplayedText = dataStore.bolusUnitsDisplayedText.observeAsState()

                // Signify we have drawn the content of the first screen
                ReportFullyDrawn()

                Chip(
                    onClick = {
                        if (dataStore.bolusCurrentParameters.value != null) {
                            onClickUnits(dataStore.bolusCurrentParameters.value!!.units)
                        } else {
                            onClickUnits(null)
                        }
                    },
                    label = {
                        Text(
                            "Units",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "${bolusUnitsDisplayedText.value}",
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 35.dp)
                )
            }

            item {
                Chip(
                    onClick = onClickCarbs,
                    label = {
                        Text(
                            "Carbs (g)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = when (bolusCarbsGramsUserInput) {
                                null -> "Not Entered"
                                else -> "$bolusCarbsGramsUserInput"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val bolusBGDisplayedText = dataStore.bolusBGDisplayedText.observeAsState()

                Chip(
                    onClick = onClickBG,
                    label = {
                        Text(
                            "BG (mg/dL)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "${bolusBGDisplayedText.value}",
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            fun performPermissionCheck() {
                showPermissionCheckDialog = true
                sendPumpCommands(SendType.BUST_CACHE, listOf(BolusPermissionRequest()))
            }

            item {
                CompactButton(
                    onClick = {
                        dataStore.bolusFinalConditions.value =
                            bolusCalcDecision(dataStore.bolusCalculatorBuilder.value)?.conditions
                        dataStore.bolusFinalParameters.value =
                            bolusCalcParameters(dataStore.bolusCalculatorBuilder.value)
                        performPermissionCheck()
                    },
                    enabled = true
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "continue",
                        modifier = Modifier
                            .size(ButtonDefaults.SmallIconSize)
                            .wrapContentSize(align = Alignment.Center),
                    )
                }
            }

            fun sortConditions(set: Set<BolusCalcCondition>?): List<BolusCalcCondition> {
                if (set == null) {
                    return emptyList()
                }

                return set.sortedWith(
                    compareBy(
                        { it.javaClass == FailedSanityCheck::class.java },
                        { it.javaClass == FailedPrecondition::class.java },
                        { it.javaClass == WaitingOnPrecondition::class.java },
                        { it.javaClass == Decision::class.java },
                        { it.javaClass == NonActionDecision::class.java },
                    )
                )
            }

            items(5) { index ->
                val bolusCalculatorBuilder = dataStore.bolusCalculatorBuilder.observeAsState()
                val conditions = sortConditions(bolusCalculatorBuilder.value?.conditions)

                if (index < conditions.size) {
                    LineTextDescription(
                        labelText = firstLetterCapitalized(conditions[index].msg),
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }

        val scrollState = rememberScalingLazyListState()

        Dialog(
            showDialog = showPermissionCheckDialog,
            onDismissRequest = {
                showPermissionCheckDialog = false
            },
            scrollState = scrollState
        ) {
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

            IndeterminateProgressIndicator(text = bolusFinalParameters.value?.units?.let { "Requesting permission" }
                ?: "Invalid request!")
        }

        val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()

        LaunchedEffect(bolusPermissionResponse.value) {
            if (bolusPermissionResponse.value != null && showPermissionCheckDialog) {
                showConfirmDialog = true
                showPermissionCheckDialog = false
            }
        }

        fun sendBolusRequestToPhone(bolusParameters: BolusParameters?) {
            if (bolusParameters == null || dataStore.bolusPermissionResponse.value == null) {
                return
            }

            val bolusId = dataStore.bolusPermissionResponse.value!!.bolusId

            Timber.i("sending bolus request to phone: $bolusId $bolusParameters")
            sendPhoneBolusRequest(bolusId, bolusParameters)
        }

        Dialog(
            showDialog = showConfirmDialog,
            onDismissRequest = {
                showConfirmDialog = false
            },
            scrollState = scrollState
        ) {
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()

            Alert(
                title = {
                    Text(
                        text = bolusFinalParameters.value?.units?.let { "${twoDecimalPlaces(it)}u Bolus" }
                            ?: "",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            dataStore.bolusFinalConditions.value = null
                            dataStore.bolusFinalParameters.value = null
                        },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Do not deliver bolus"
                        )
                    }
                },
                positiveButton = {
                    bolusFinalParameters.value?.let { finalParameters ->
                        bolusPermissionResponse.value?.let { permissionResponse ->
                            if (permissionResponse.status == 0 && permissionResponse.nackReason == BolusPermissionResponse.NackReason.PERMISSION_GRANTED && finalParameters.units >= 0.05) {
                                Button(
                                    onClick = {
                                        if (permissionResponse.status == 0 && permissionResponse.nackReason == BolusPermissionResponse.NackReason.PERMISSION_GRANTED && finalParameters.units >= 0.05) {
                                            showConfirmDialog = false
                                            showInProgressDialog = true
                                            sendBolusRequestToPhone(dataStore.bolusFinalParameters.value)
                                        }
                                    },
                                    colors = ButtonDefaults.primaryButtonColors()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Deliver bolus"
                                    )
                                }
                            }
                        }
                    }
                },
                icon = {
                    Image(
                        painterResource(R.drawable.bolus_icon),
                        "Bolus icon",
                        Modifier.size(24.dp)
                    )
                },
                scrollState = scrollState,
            ) {
                Text(
                    text = bolusPermissionResponse.value?.let {
                        when {
                            bolusFinalParameters.value == null || bolusFinalParameters.value?.units == null -> ""
                            bolusFinalParameters.value?.units!! < 0.05 -> "Insulin amount too small."
                            it.status == 0 -> "Do you want to deliver the bolus?"
                            else -> "Cannot deliver bolus: ${it.nackReason}"
                        }
                    } ?: "",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }

        Dialog(
            showDialog = showCancellingDialog,
            onDismissRequest = {
                showCancellingDialog = false
            },
            scrollState = scrollState
        ) {
            val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()

            LaunchedEffect(bolusCancelResponse.value) {
                if (bolusCancelResponse.value != null) {
                    showCancelledDialog = true
                }
            }

            IndeterminateProgressIndicator(text = "The bolus is being cancelled..")
        }

        Dialog(
            showDialog = showCancelledDialog,
            onDismissRequest = {
                showCancelledDialog = false
                onClickLanding()
                resetBolusDataStoreState(dataStore)
            },
            scrollState = scrollState
        ) {
            val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
            val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()

            Alert(
                title = {
                    Text(
                        text = when (bolusCancelResponse.value?.status) {
                            CancelStatus.SUCCESS ->
                                "The bolus was cancelled."
                            CancelStatus.FAILED ->
                                when (bolusInitiateResponse.value) {
                                    null -> "A bolus request was not sent to the pump, so there is nothing to cancel."
                                    else -> "The bolus could not be cancelled: ${
                                        snakeCaseToSpace(
                                            bolusCancelResponse.value?.reason.toString()
                                        )
                                    }"
                                }
                            else -> "Please check your pump to confirm whether the bolus was cancelled."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            onClickLanding()
                            resetBolusDataStoreState(dataStore)
                        },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.fillMaxWidth()

                    ) {
                        Text("OK")
                    }
                },
                positiveButton = {},
                icon = {
                    Image(
                        painterResource(R.drawable.bolus_icon),
                        "Bolus icon",
                        Modifier.size(24.dp)
                    )
                },
                scrollState = scrollState,
            )
        }

        fun cancelBolus() {
            fun performCancel() {
                bolusPermissionResponse.value?.bolusId?.let { bolusId ->
                    sendPumpCommands(SendType.BUST_CACHE, listOf(CancelBolusRequest(bolusId)))
                    showCancellingDialog = true
                }
            }
            performCancel()
            refreshScope.launch {
                while (dataStore.bolusCancelResponse.value == null) {
                    withContext(Dispatchers.IO) {
                        Thread.sleep(250)
                    }
                    performCancel()
                }
                showCancellingDialog = false
                showCancelledDialog = true
                dataStore.bolusFinalConditions.value = null
                dataStore.bolusFinalParameters.value = null
                resetSavedBolusEnteredState()
                sendPhoneBolusCancel()
            }
        }

        Dialog(
            showDialog = showInProgressDialog,
            onDismissRequest = {
                showInProgressDialog = false
            },
            scrollState = scrollState
        ) {
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
            val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
            val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()

            LaunchedEffect(bolusInitiateResponse.value) {
                if (bolusInitiateResponse.value != null) {
                    showApprovedDialog = true
                }
            }

            LaunchedEffect(bolusCancelResponse.value) {
                if (bolusCancelResponse.value != null) {
                    showCancelledDialog = true
                }
            }

            Alert(
                title = {
                    Text(
                        text = when (bolusFinalParameters.value) {
                            null -> ""
                            else -> "${bolusFinalParameters.value?.units}u Bolus"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            cancelBolus()
                        },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.fillMaxWidth()

                    ) {
                        Text("Cancel")
                    }
                },
                positiveButton = {},
                scrollState = scrollState,
                icon = {
                    Image(
                        painterResource(R.drawable.bolus_icon),
                        "Bolus icon",
                        Modifier.size(24.dp)
                    )
                }
            ) {
                Text(
                    text = "A notification was sent to acknowledge the request.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }

        Dialog(
            showDialog = showApprovedDialog,
            onDismissRequest = {
                showApprovedDialog = false
                onClickLanding()
                resetBolusDataStoreState(dataStore)
                resetSavedBolusEnteredState()
            },
            scrollState = scrollState
        ) {
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
            val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()

            Alert(
                title = {
                    Text(
                        text = when {
                            bolusInitiateResponse.value != null -> when {
                                bolusInitiateResponse.value!!.wasBolusInitiated() -> "Bolus Initiated"
                                else -> "Bolus Rejected by Pump"
                            }
                            else -> "Bolus Status Unknown"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            cancelBolus()
                        },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.fillMaxWidth()

                    ) {
                        Text("Cancel")
                    }
                },
                positiveButton = {},
                icon = {
                    Image(
                        painterResource(R.drawable.bolus_icon),
                        "Bolus icon",
                        Modifier.size(24.dp)
                    )
                },
                scrollState = scrollState,
            ) {
                Text(
                    text = when {
                        bolusInitiateResponse.value != null -> when {
                            bolusInitiateResponse.value!!.wasBolusInitiated() -> "The ${
                                bolusFinalParameters.value?.let {
                                    twoDecimalPlaces(
                                        it.units
                                    )
                                }
                            }u bolus was initiated."
                            else -> "The bolus could not be delivered: ${
                                bolusInitiateResponse.value?.let {
                                    snakeCaseToSpace(
                                        it.statusType.toString()
                                    )
                                }
                            }"
                        }
                        else -> "The bolus status is unknown. Please check your pump to identify the status of the bolus."
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    }
}

fun resetBolusDataStoreState(dataStore: DataStore) {
    dataStore.bolusPermissionResponse.value = null
    dataStore.bolusCancelResponse.value = null
    dataStore.bolusInitiateResponse.value = null
    dataStore.bolusCalculatorBuilder.value = null
    dataStore.bolusCurrentParameters.value = null
    dataStore.bolusFinalParameters.value = null
    dataStore.bolusFinalConditions.value = null
}