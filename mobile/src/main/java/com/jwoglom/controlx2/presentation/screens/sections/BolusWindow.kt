@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.os.Handler
import android.os.Looper
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.navigation.BolusInputPrefill
import com.jwoglom.controlx2.presentation.screens.BolusPreview
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.ApprovedDialogRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusConditionPromptRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusDeliverActionRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusEntryFormRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusPermissionDialogRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.CancelledDialogRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.CancellingDialogRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.InProgressDialogRegion
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.function.Supplier

@Composable
fun BolusWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Long, Long) -> Unit,
    sendServiceBolusCancel: () -> Unit,
    prefill: BolusInputPrefill? = null,
    onPrefillConsumed: () -> Unit = {},
    closeWindow: () -> Unit,
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val context = LocalContext.current
    val dataStore = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val unitAbbrev = glucoseUnit.abbreviation

    val unitsRawValue = dataStore.bolusUnitsRawValue.observeAsState()
    val carbsRawValue = dataStore.bolusCarbsRawValue.observeAsState()
    val glucoseRawValue = dataStore.bolusGlucoseRawValue.observeAsState()

    var unitsSubtitle by remember { mutableStateOf<String>("Units") }
    var carbsSubtitle by remember { mutableStateOf<String>("Carbs (g)") }
    var glucoseSubtitle by remember { mutableStateOf<String>("BG ($unitAbbrev)") }

    var unitsHumanEntered by remember { mutableStateOf<Double?>(null) }
    var unitsHumanFocus by remember { mutableStateOf(false) }
    var glucoseHumanEntered by remember { mutableStateOf<Int?>(null) }

    var bolusButtonEnabled by remember { mutableStateOf(false) }

    // Intentionally observed at the top-level composable for refresh/recalculate flow coordination.
    val cgmReading = dataStore.cgmReading.observeAsState()
    val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
    val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
    val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()

    var showPermissionCheckDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by remember { mutableStateOf(false) }
    var showApprovedDialog by remember { mutableStateOf(false) }
    var showCancellingDialog by remember { mutableStateOf(false) }
    var showCancelledDialog by remember { mutableStateOf(false) }
    var pendingPrefill by remember(prefill) { mutableStateOf(prefill) }

    val commands = listOf(
        BolusCalcDataSnapshotRequest(),
        LastBGRequest(),
        TimeSinceResetRequest()
    )

    val baseFields = listOf(
        dataStore.bolusCalcDataSnapshot,
        dataStore.bolusCalcLastBG
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

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("BolusScreen base loading done: ${baseFields.map { it.value }}")
        if (sinceLastFetchTime == 0) {
            delay(250)
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        refreshing = true

        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    fun applyPrefill(prefillValues: BolusInputPrefill) {
        dataStore.bolusUnitsRawValue.value = prefillValues.unitsRawValue
        dataStore.bolusCarbsRawValue.value = prefillValues.carbsRawValue
        dataStore.bolusGlucoseRawValue.value = prefillValues.glucoseRawValue
        unitsHumanEntered = prefillValues.unitsRawValue?.toDoubleOrNull()
        glucoseHumanEntered = prefillValues.glucoseRawValue?.toIntOrNull()
    }

    LifecycleStateObserver(
        lifecycleOwner = LocalLifecycleOwner.current,
        onStop = {
            resetBolusDataStoreState(dataStore)
        }
    ) {
        resetBolusDataStoreState(dataStore)
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

    LaunchedEffect(Unit) {
        Timber.i(
            "BolusWindow: composed prefillApplied units=%s carbs=%s bg=%s",
            dataStore.bolusUnitsRawValue.value,
            dataStore.bolusCarbsRawValue.value,
            dataStore.bolusGlucoseRawValue.value
        )
    }

    fun recalculate() {
        // Convert glucose from display unit to mg/dL for the bolus calculator
        val glucoseDisplayValue = rawToInt(glucoseRawValue.value)
        val glucoseMgdlValue = when {
            glucoseDisplayValue == null -> null
            glucoseUnit == GlucoseUnit.MMOL -> {
                val displayDouble = glucoseRawValue.value?.toDoubleOrNull()
                displayDouble?.let { GlucoseConverter.convert(it, GlucoseUnit.MMOL, GlucoseUnit.MGDL).toInt() }
            }
            else -> glucoseDisplayValue
        }
        dataStore.bolusCalculatorBuilder.value = buildBolusCalculator(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            if (unitsHumanEntered != null) rawToDouble(unitsRawValue.value) else null,
            rawToInt(carbsRawValue.value),
            glucoseMgdlValue
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
            dataStore.bolusGlucoseRawValue.value = GlucoseConverter.format(autofilledBg, glucoseUnit)
        }
        glucoseSubtitle = when {
            glucoseHumanEntered != null -> "Entered ($unitAbbrev)"
            autofilledBg != null -> "CGM ($unitAbbrev)"
            else -> "BG ($unitAbbrev)"
        }
    }

    LaunchedEffect (cgmReading) {
        refresh()
    }

    LaunchedEffect (unitsRawValue.value, carbsRawValue.value, glucoseRawValue.value, bolusCalcDataSnapshot.value, bolusCalcLastBG.value) {
        recalculate()
    }

    HeaderLine("Bolus")

    BolusEntryFormRegion(
        unitsSubtitle = unitsSubtitle,
        carbsSubtitle = carbsSubtitle,
        glucoseSubtitle = glucoseSubtitle,
        onUnitsChanged = {
            dataStore.bolusUnitsRawValue.value = it
            unitsHumanEntered = if (it == "") null else it.toDoubleOrNull()
        },
        onUnitsFocusChanged = { isFocused ->
            unitsHumanFocus = isFocused
        },
        onCarbsChanged = {
            dataStore.bolusCarbsRawValue.value = it
        },
        onGlucoseChanged = {
            dataStore.bolusGlucoseRawValue.value = it
            glucoseHumanEntered = if (it == "") null else it.toIntOrNull()
        }
    )

    BolusConditionPromptRegion(
        recalculate = { recalculate() }
    )

    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()

    LaunchedEffect (bolusCurrentParameters.value) {
        bolusButtonEnabled = validBolus(
            params = bolusCurrentParameters.value,
            maxBolusAmount1000 = dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount?.toLong()
        )
    }

    BolusDeliverActionRegion(
        refreshing = refreshing,
        bolusButtonEnabled = bolusButtonEnabled,
        bolusUnits = bolusCurrentParameters.value?.units,
        onPerformPermissionCheck = {
            showPermissionCheckDialog = true
            sendPumpCommands(SendType.BUST_CACHE, listOf(BolusPermissionRequest()))
        },
        onPrepareFinalParameters = {
            dataStore.bolusFinalConditions.value =
                bolusCalcDecision(dataStore.bolusCalculatorBuilder.value, dataStore.bolusConditionsExcluded.value)?.conditions

            val pair = bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, dataStore.bolusConditionsExcluded.value)
            dataStore.bolusFinalParameters.value = pair.first
            dataStore.bolusFinalCalcUnits.value = pair.second
        },
        isValidBolus = {
            validBolus(
                params = bolusCurrentParameters.value,
                maxBolusAmount1000 = dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount?.toLong()
            )
        }
    )

    if (showPermissionCheckDialog) {
        BolusPermissionDialogRegion(
            showPermissionCheckDialog = showPermissionCheckDialog,
            onDismiss = {
                showPermissionCheckDialog = false
            },
            onShowInProgress = {
                showPermissionCheckDialog = false
                showInProgressDialog = true
            },
            sendServiceBolusRequest = sendServiceBolusRequest
        )
    }

    fun cancelBolus() {
        Timber.i("cancelBolus()")
        fun performCancel() {
            Timber.i("cancelBolus performCancel(): ${dataStore.bolusPermissionResponse.value?.bolusId}")
            dataStore.bolusPermissionResponse.value?.bolusId?.let { bolusId ->
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
        InProgressDialogRegion(
            onShowApproved = {
                showInProgressDialog = false
                showApprovedDialog = true
            },
            onShowCancelled = {
                showInProgressDialog = false
                showCancelledDialog = true
            },
            onCancel = {
                showInProgressDialog = false
                cancelBolus()
            },
            context = context
        )
    }

    if (showApprovedDialog) {
        ApprovedDialogRegion(
            sendPumpCommands = sendPumpCommands,
            refreshScopeLaunch = { block -> refreshScope.launch { block() } },
            onCancel = {
                showApprovedDialog = false
                cancelBolus()
            },
            onClose = {
                showCancellingDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            }
        )
    }

    if (showCancellingDialog) {
        CancellingDialogRegion(
            onShowCancelled = {
                showCancellingDialog = false
                showCancelledDialog = true
            },
            onDismiss = {
                showCancellingDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            }
        )
    }

    if (showCancelledDialog) {
        CancelledDialogRegion(
            mainHandler = mainHandler,
            sendPumpCommands = sendPumpCommands,
            onDismiss = {
                showCancelledDialog = false
                resetBolusDataStoreState(dataStore)
                closeWindow()
            }
        )
    }
}


private fun validBolus(params: BolusParameters?, maxBolusAmount1000: Long?): Boolean {
    if (params == null) {
        return false
    }

    if (params.units < 0.05) {
        return false
    }

    if (params.units >= InsulinUnit.from1000To1(maxBolusAmount1000 ?: 25000)) {
        return false
    }

    return true
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
    PumpStateSupplier.inProgressBolusId = Supplier { null }
    dataStore.bolusCalcDataSnapshot.value = null
    dataStore.bolusCalcLastBG.value = null
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
    dataStore.bolusExtendedEnabled.value = false
    dataStore.bolusExtendedDurationMinutes.value = null
    dataStore.bolusExtendedPercentNow.value = null
}

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}
