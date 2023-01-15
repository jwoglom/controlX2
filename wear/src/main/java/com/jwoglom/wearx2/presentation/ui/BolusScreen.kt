package com.jwoglom.wearx2.presentation.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.MutableLiveData
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
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
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBolusStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse.CancelStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse.CurrentBolusStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.dataStore
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.components.LineTextDescription
import com.jwoglom.wearx2.presentation.defaultTheme
import com.jwoglom.wearx2.shared.presentation.LifecycleStateObserver
import com.jwoglom.wearx2.shared.util.SendType
import com.jwoglom.wearx2.shared.util.firstLetterCapitalized
import com.jwoglom.wearx2.shared.util.oneDecimalPlace
import com.jwoglom.wearx2.shared.util.snakeCaseToSpace
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces1000Unit
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
    sendPhoneBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse) -> Unit,
    resetSavedBolusEnteredState: () -> Unit,
    sendPhoneBolusCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBolusConditionPrompt by remember { mutableStateOf(false) }
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

    fun bolusCalcDecision(
        bolusCalc: BolusCalculatorBuilder?,
        bolusConditionsExcluded: MutableSet<BolusCalcCondition>?
    ): BolusCalcDecision? {
        val excluded = (bolusConditionsExcluded ?: mutableSetOf()).stream().toArray { arrayOfNulls<BolusCalcCondition>(it) }
        val decision = bolusCalc?.build()?.parse(*excluded)
        Timber.i("bolusCalcDecision: OUTPUT units=${decision?.units} conditions=${decision?.conditions} excluded=$excluded")
        return decision
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

    val commands = listOf(
        BolusCalcDataSnapshotRequest(),
        LastBGRequest(),
        TimeSinceResetRequest(),
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
        val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()

        fun recalculate() {
            dataStore.bolusCalculatorBuilder.value = buildBolusCalculator(
                bolusCalcDataSnapshot.value,
                bolusCalcLastBG.value,
                bolusUnitsUserInput,
                bolusCarbsGramsUserInput,
                bolusBgMgdlUserInput
            )

            dataStore.bolusCurrentParameters.value =
                bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value).first

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
                } else {
                    dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>().let {
                        it.addAll(conditionsWithPrompt)
                        it
                    }
                    showBolusConditionPrompt = true
                }
            } else {
                dataStore.bolusConditionsPrompt.value = null
                showBolusConditionPrompt = false
            }

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

        LaunchedEffect(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            bolusConditionsExcluded.value,
            bolusUnitsUserInput,
            bolusCarbsGramsUserInput,
            bolusBgMgdlUserInput
        ) {
            recalculate()
        }

        LaunchedEffect(Unit) {
            scalingLazyListState.animateScrollToItem(0)
        }

        ScalingLazyColumn(
            modifier = modifier.scrollableColumn(focusRequester, scalingLazyListState),
            state = scalingLazyListState,
            autoCentering = AutoCenteringParams()
        ) {
            item {
                // Signify we have drawn the content of the first screen
                ReportFullyDrawn()

                val bolusUnitsDisplayedText = dataStore.bolusUnitsDisplayedText.observeAsState()
                val bolusConditionsPromptAcknowledged = dataStore.bolusConditionsPromptAcknowledged.observeAsState()

                if (bolusConditionsPromptAcknowledged.value != null && bolusConditionsPromptAcknowledged.value!!.size > 0) {
                    Spacer(modifier = Modifier.height(96.dp))
                    bolusConditionsPromptAcknowledged.value?.forEach {
                        LineTextDescription(
                            when {
                                bolusConditionsExcluded.value?.contains(it) == true -> "${it.prompt?.whenIgnoredNotice}"
                                else -> "${it.prompt?.whenAcceptedNotice}"
                            },
                            textColor = when {
                                bolusConditionsExcluded.value?.contains(it) == true -> Color.Red
                                else -> defaultTheme.colors.primary
                            },
                            fontSize = 12.sp,
                            align = Alignment.Center,
                            height = 48.dp,
                            bottomPadding = 8.dp,
                            onClick = {
                                Timber.i("bolusConditionsPromptAcknowledged click")
                                dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>().let {
                                    it.addAll(bolusConditionsPromptAcknowledged.value!!)
                                    it
                                }
                                dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf()
                                dataStore.bolusConditionsExcluded.value = mutableSetOf()
                                showBolusConditionPrompt = true
                            }
                        )
                    }
                }

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
                    modifier = Modifier
                        .fillMaxWidth()
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Button(
                    onClick = {
                        dataStore.bolusFinalConditions.value =
                            bolusCalcDecision(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value)?.conditions

                        val pair = bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, bolusConditionsExcluded.value)
                        dataStore.bolusFinalParameters.value = pair.first
                        dataStore.bolusFinalCalcUnits.value = pair.second
                        performPermissionCheck()
                    },
                    enabled = true
                ) {
                    Row() {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "continue",
                            modifier = Modifier
                                .size(ButtonDefaults.SmallIconSize)
                                .wrapContentSize(align = Alignment.Center),
                        )
                    }
                }
            }

            items(5) { index ->
                val conditions = dataStore.bolusCurrentConditions.observeAsState()

                if (conditions.value == null) {
                    Spacer(modifier = Modifier.height(0.dp))
                } else if (index < (conditions.value?.size ?: 0)) {
                    LineTextDescription(
                        labelText = firstLetterCapitalized(conditions.value?.get(index)?.msg ?: ""),
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }

        val scrollState = rememberScalingLazyListState()

        Dialog(
            showDialog = showBolusConditionPrompt,
            onDismissRequest = {
                showBolusConditionPrompt = false
            },
            scrollState = scrollState
        ) {
            val bolusConditionsPrompt = dataStore.bolusConditionsPrompt.observeAsState()

            Alert(
                title = {
                    Text(
                        text = when {
                            bolusConditionsPrompt.value != null -> "${bolusConditionsPrompt.value?.first()?.msg}"
                            else -> "Bolus Conditions Prompt"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground
                    )
                },
                negativeButton = {
                    Button(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value == null) {
                                    dataStore.bolusConditionsExcluded.value = mutableSetOf(it.first())
                                } else {
                                    dataStore.bolusConditionsExcluded.value?.add(it.first())
                                }

                                if (it.size == 1) {
                                    showBolusConditionPrompt = false
                                }
                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }

                        },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Do not apply"
                        )
                    }
                },
                positiveButton = {
                    Button(
                        onClick = {
                            bolusConditionsPrompt.value?.let {
                                if (dataStore.bolusConditionsPromptAcknowledged.value == null) {
                                    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(it.first())
                                } else {
                                    dataStore.bolusConditionsPromptAcknowledged.value!!.add(it.first())
                                }

                                if (dataStore.bolusConditionsExcluded.value != null) {
                                    dataStore.bolusConditionsExcluded.value?.remove(it.first())
                                }

                                if (it.size == 1) {
                                    showBolusConditionPrompt = false
                                }

                                dataStore.bolusConditionsPrompt.value?.drop(0)
                                recalculate()
                            }
                        },
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Apply"
                        )
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
                    text = when {
                        bolusConditionsPrompt.value != null -> "${bolusConditionsPrompt.value?.first()?.prompt?.promptMessage}"
                        else -> ""
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }

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

        fun sendBolusRequestToPhone(bolusParameters: BolusParameters?, unitBreakdown: BolusCalcUnits?, dataSnapshot: BolusCalcDataSnapshotResponse?, timeSinceReset: TimeSinceResetResponse?) {
            if (bolusParameters == null || dataStore.bolusPermissionResponse.value == null || dataStore.bolusCalcDataSnapshot.value == null || unitBreakdown == null || dataSnapshot == null || timeSinceReset == null) {
                Timber.w("sendBolusRequestToPhone: null parameters")
                return
            }

            val bolusId = dataStore.bolusPermissionResponse.value!!.bolusId

            Timber.i("sendBolusRequestToPhone: sending bolus request to phone: bolusId=$bolusId bolusParameters=$bolusParameters unitBreakdown=$unitBreakdown dataSnapshot=$dataSnapshot timeSinceReset=$timeSinceReset")
            sendPhoneBolusRequest(bolusId, bolusParameters, unitBreakdown, dataSnapshot, timeSinceReset)
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
                            if (permissionResponse.isPermissionGranted && finalParameters.units >= 0.05) {
                                Button(
                                    onClick = {
                                        if (permissionResponse.isPermissionGranted && finalParameters.units >= 0.05) {
                                            showConfirmDialog = false
                                            showInProgressDialog = true
                                            sendBolusRequestToPhone(
                                                dataStore.bolusFinalParameters.value,
                                                dataStore.bolusFinalCalcUnits.value,
                                                dataStore.bolusCalcDataSnapshot.value,
                                                dataStore.timeSinceResetResponse.value,
                                            )
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
                resetBolusDataStoreState(dataStore)
                onClickLanding()
            },
            scrollState = scrollState
        ) {
            val bolusCancelResponse = dataStore.bolusCancelResponse.observeAsState()
            val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
            val lastBolusStatusResponse = dataStore.lastBolusStatusResponse.observeAsState()

            LaunchedEffect (bolusCancelResponse.value, Unit) {
                sendPumpCommands(SendType.STANDARD, listOf(LastBolusStatusV2Request()))
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
                if (matchesBolusId() == false) {
                    refreshScope.launch {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(250)
                        }
                        sendPumpCommands(
                            SendType.STANDARD,
                            listOf(LastBolusStatusV2Request())
                        )
                    }
                }
            }

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
                content = {
                    Text(
                        text = when {
                            matchesBolusId() == true ->
                                lastBolusStatusResponse.value?.deliveredVolume?.let {
                                    if (it == 0L) "A bolus was started and no insulin was delivered." else "${twoDecimalPlaces1000Unit(it)}u was delivered."
                                } ?: ""
                            matchesBolusId() == false -> "No insulin was delivered."
                            else -> "Checking if any insulin was delivered..."
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
                }
            }
            showCancellingDialog = true
            refreshScope.launch {
                var time = 0
                while (dataStore.bolusCancelResponse.value == null) {
                    if (time >= 5000) {
                        performCancel()
                        time = 0
                    }
                    withContext(Dispatchers.IO) {
                        Thread.sleep(100)
                    }
                    time += 100
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
            val bolusMinNotifyThreshold = dataStore.bolusMinNotifyThreshold.observeAsState()

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
                    text = when {
                        bolusInitiateResponse.value != null -> "Bolus request received by pump"
                        bolusFinalParameters.value != null && bolusMinNotifyThreshold.value != null -> when {
                            bolusFinalParameters.value!!.units >= bolusMinNotifyThreshold.value!! -> "A notification was sent to approve the request."
                            else -> "Sending request to pump..."
                        }
                        else -> "Sending request to phone..."
                    },
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
                resetBolusDataStoreState(dataStore)
                resetSavedBolusEnteredState()
                onClickLanding()
            },
            scrollState = scrollState
        ) {
            val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
            val bolusInitiateResponse = dataStore.bolusInitiateResponse.observeAsState()
            val bolusCurrentResponse = dataStore.bolusCurrentResponse.observeAsState()

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
                LaunchedEffect(Unit) {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                    refreshScope.launch {
                        repeat(5) {
                            Thread.sleep(1000)
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                listOf(CurrentBolusStatusRequest())
                            )
                        }
                    }
                }

                // When bolusCurrentResponse is updated, re-request it
                LaunchedEffect(bolusCurrentResponse.value) {
                    Timber.i("bolusCurrentResponse: ${bolusCurrentResponse.value}")
                    // when a bolusId=0 is returned, the current bolus session has ended so the message
                    // no longer contains any useful data.
                    if (bolusCurrentResponse.value?.bolusId != 0) {
                        sendPumpCommands(SendType.BUST_CACHE, listOf(CurrentBolusStatusRequest()))
                        refreshScope.launch {
                            repeat(5) {
                                Thread.sleep(1000)
                                sendPumpCommands(
                                    SendType.BUST_CACHE,
                                    listOf(CurrentBolusStatusRequest())
                                )
                            }
                        }
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
                                        CurrentBolusStatus.REQUESTING -> "is being prepared."
                                        CurrentBolusStatus.DELIVERING -> "is being delivered."
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
}


@Preview(
    apiLevel = 26,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    device = Devices.WEAR_OS_LARGE_ROUND,
)
@Composable
fun EmptyPreview() {
    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf()
    BolusScreen(
        scalingLazyListState = ScalingLazyListState(0, 0),
        focusRequester = FocusRequester(),
        bolusUnitsUserInput = 0.0,
        bolusCarbsGramsUserInput = 0,
        bolusBgMgdlUserInput = 0,
        onClickUnits = {},
        onClickCarbs = { },
        onClickBG = { },
        onClickLanding = { },
        sendPumpCommands = {_, _ -> },
        sendPhoneBolusRequest = {_, _, _, _, _ -> },
        resetSavedBolusEnteredState = {},
        sendPhoneBolusCancel = {}
    )
}

@Preview(
    apiLevel = 26,
    uiMode = Configuration.UI_MODE_TYPE_WATCH,
    device = Devices.WEAR_OS_LARGE_ROUND,
)
@Composable
fun ConditionAcknowledgedPreview() {
    dataStore.bolusConditionsPromptAcknowledged.value = mutableListOf(BolusCalcCondition.POSITIVE_BG_CORRECTION)
    BolusScreen(
        scalingLazyListState = ScalingLazyListState(0, 0),
        focusRequester = FocusRequester(),
        bolusUnitsUserInput = 0.0,
        bolusCarbsGramsUserInput = 0,
        bolusBgMgdlUserInput = 0,
        onClickUnits = {},
        onClickCarbs = { },
        onClickBG = { },
        onClickLanding = { },
        sendPumpCommands = {_, _ -> },
        sendPhoneBolusRequest = {_, _, _, _, _ -> },
        resetSavedBolusEnteredState = {},
        sendPhoneBolusCancel = {}
    )
}