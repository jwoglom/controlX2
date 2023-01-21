@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.wearx2.presentation.screens.sections

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
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
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBGRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.wearx2.LocalDataStore
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.presentation.screens.BolusPreview
import com.jwoglom.wearx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.wearx2.presentation.screens.sections.components.IntegerOutlinedText
import com.jwoglom.wearx2.presentation.util.LifecycleStateObserver
import com.jwoglom.wearx2.shared.util.SendType
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun BolusWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit
) {
    val dataStore = LocalDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    var unitsRawValue by remember { mutableStateOf<String?>(null) }
    var carbsRawValue by remember { mutableStateOf<String?>(null) }
    var glucoseRawValue by remember { mutableStateOf<String?>(null) }

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

        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    LifecycleStateObserver(
        lifecycleOwner = LocalLifecycleOwner.current,
        onStop = {}
    ) {
        sendPumpCommands(SendType.BUST_CACHE, commands)
    }

    LaunchedEffect (refreshing) {
        waitForLoaded()
    }

    fun recalculate() {
        dataStore.bolusCalculatorBuilder.value = buildBolusCalculator(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            if (unitsHumanEntered != null) rawToDouble(unitsRawValue) else null,
            rawToInt(carbsRawValue),
            rawToInt(glucoseRawValue)
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
            unitsRawValue = twoDecimalPlaces(dataStore.bolusCurrentParameters.value!!.units)
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
            glucoseRawValue = "$autofilledBg"
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

    LaunchedEffect (unitsRawValue, carbsRawValue, glucoseRawValue, bolusCalcDataSnapshot.value, bolusCalcLastBG.value) {
        recalculate()
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(0.75f)) {}
        Column(Modifier.weight(1f)) {
            DecimalOutlinedText(
                title = unitsSubtitle,
                value = unitsRawValue,
                onValueChange = {
                    unitsRawValue = it
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
                value = carbsRawValue,
                onValueChange = { carbsRawValue = it }
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(all = 8.dp)) {
            IntegerOutlinedText(
                title = glucoseSubtitle,
                value = glucoseRawValue,
                onValueChange = {
                    glucoseRawValue = it
                    glucoseHumanEntered = rawToInt(it)
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
            Button(
                onClick = {

                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = bolusButtonEnabled,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.align(Alignment.Center)

            ) {
                Image(
                    if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                    else painterResource(R.drawable.bolus_icon),
                    "Bolus icon",
                    Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Deliver ${bolusCurrentParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus", fontSize = 18.sp)
            }
        }
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

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}