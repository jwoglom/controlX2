package com.jwoglom.controlx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import timber.log.Timber

class DataStore {
    val connectionStatus = MutableLiveData<String>()

    val batteryPercent = MutableLiveData<Int>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val cartridgeRemainingEstimate = MutableLiveData<Boolean>()
    val lastBolusStatus = MutableLiveData<String>()
    val controlIQStatus = MutableLiveData<String>()
    val controlIQMode = MutableLiveData<String>()
    val basalRate = MutableLiveData<String>()
    var basalStatus = MutableLiveData<String>()
    val cgmSessionState = MutableLiveData<String>()
    val cgmSessionExpireRelative = MutableLiveData<String>()
    val cgmSessionExpireExact = MutableLiveData<String>()
    val cgmTransmitterStatus = MutableLiveData<String>()
    val cgmReading = MutableLiveData<Int>()
    val cgmDelta = MutableLiveData<Int>()
    val cgmStatusText = MutableLiveData<String>()
    val cgmHighLowState = MutableLiveData<String>()
    val cgmDeltaArrow = MutableLiveData<String>()
    val bolusCalcDataSnapshot = MutableLiveData<BolusCalcDataSnapshotResponse>()
    val bolusCalcLastBG = MutableLiveData<LastBGResponse>()
    val maxBolusAmount = MutableLiveData<Double>()
    val maxCarbAmount = MutableLiveData<Int>()

    val landingBasalDisplayedText = MutableLiveData<String>()
    val landingControlIQDisplayedText = MutableLiveData<String>()

    val bolusUnitsDisplayedText = MutableLiveData<String>()
    val bolusUnitsDisplayedSubtitle = MutableLiveData<String>()
    val bolusBGDisplayedText = MutableLiveData<String>()
    val bolusBGDisplayedSubtitle = MutableLiveData<String>()

    val bolusCalculatorBuilder = MutableLiveData<BolusCalculatorBuilder>()
    val bolusCurrentParameters = MutableLiveData<BolusParameters>()
    val bolusCurrentConditions = MutableLiveData<List<BolusCalcCondition>>()
    val bolusConditionsPrompt = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsPromptAcknowledged = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsExcluded = MutableLiveData<MutableSet<BolusCalcCondition>>()
    val bolusFinalParameters = MutableLiveData<BolusParameters>()
    val bolusFinalCalcUnits = MutableLiveData<BolusCalcUnits>()
    val bolusFinalConditions = MutableLiveData<Set<BolusCalcCondition>>()
    val bolusMinNotifyThreshold = MutableLiveData<Double>()

    val timeSinceResetResponse = MutableLiveData<TimeSinceResetResponse>()
    val bolusPermissionResponse = MutableLiveData<BolusPermissionResponse>()
    val bolusCarbEntryResponse = MutableLiveData<RemoteCarbEntryResponse>()
    val bolusInitiateResponse = MutableLiveData<InitiateBolusResponse>()
    val bolusCancelResponse = MutableLiveData<CancelBolusResponse>()
    val lastBolusStatusResponse = MutableLiveData<LastBolusStatusAbstractResponse>()
    val bolusCurrentResponse = MutableLiveData<CurrentBolusStatusResponse>()

    init {
        connectionStatus.observeForever { t -> Timber.i("DataStore.connectionStatus=$t") }

        batteryPercent.observeForever { t -> Timber.i("DataStore.batteryPercent=$t") }
        iobUnits.observeForever { t -> Timber.i("DataStore.iobUnits=$t") }
        cartridgeRemainingUnits.observeForever { t -> Timber.i("DataStore.cartridgeRemainingUnits=$t") }
        cartridgeRemainingEstimate.observeForever { t -> Timber.i("DataStore.cartridgeRemainingEstimate=$t") }
        lastBolusStatus.observeForever { t -> Timber.i("DataStore.lastBolusStatus=$t") }
        controlIQStatus.observeForever { t -> Timber.i("DataStore.controlIQStatus=$t") }
        controlIQMode.observeForever { t -> Timber.i("DataStore.controlIQMode=$t") }
        basalRate.observeForever { t -> Timber.i("DataStore.basalRate=$t") }
        basalStatus.observeForever { t -> Timber.i("DataStore.basalStatus=$t") }
        cgmSessionState.observeForever { t -> Timber.i("DataStore.cgmSessionState=$t") }
        cgmSessionExpireExact.observeForever { t -> Timber.i("DataStore.cgmSessionExpireExact=$t") }
        cgmSessionExpireRelative.observeForever { t -> Timber.i("DataStore.cgmSessionExpireRelative=$t") }
        cgmTransmitterStatus.observeForever { t -> Timber.i("DataStore.cgmTransmitterStatus=$t") }
        cgmReading.observeForever { t -> Timber.i("DataStore.cgmLastReading=$t") }
        cgmDelta.observeForever { t -> Timber.i("DataStore.cgmDelta=$t") }
        cgmStatusText.observeForever { t -> Timber.i("DataStore.cgmStatusText=$t") }
        cgmHighLowState.observeForever { t -> Timber.i("DataStore.cgmHighLowState=$t") }
        cgmDeltaArrow.observeForever { t -> Timber.i("DataStore.cgmDeltaArrow=$t") }
        bolusCalcDataSnapshot.observeForever { t -> Timber.i("DataStore.bolusCalcDataSnapshot=$t") }
        bolusCalcLastBG.observeForever { t -> Timber.i("DataStore.bolusCalcLastBG=$t") }
        maxBolusAmount.observeForever { t -> Timber.i("DataStore.maxBolusAmount=$t") }

        landingBasalDisplayedText.observeForever { t -> Timber.i("DataStore.landingBasalDisplayedText=$t") }
        landingControlIQDisplayedText.observeForever { t -> Timber.i("DataStore.landingControlIQDisplayedText=$t") }

        bolusUnitsDisplayedText.observeForever { t -> Timber.i("DataStore.bolusUnitsDisplayedText=$t") }
        bolusUnitsDisplayedSubtitle.observeForever { t -> Timber.i("DataStore.bolusUnitsDisplayedSubtitle=$t") }
        bolusBGDisplayedText.observeForever { t -> Timber.i("DataStore.bolusBGDisplayedText=$t") }
        bolusBGDisplayedSubtitle.observeForever { t -> Timber.i("DataStore.bolusBGDisplayedSubtitle=$t") }

        bolusCalculatorBuilder.observeForever { t -> Timber.i("DataStore.bolusCalculatorBuilder=$t") }
        bolusCurrentParameters.observeForever { t -> Timber.i("DataStore.bolusCurrentParameters=$t") }
        bolusCurrentConditions.observeForever { t -> Timber.i("DataStore.bolusCurrentConditions=$t") }
        bolusConditionsPrompt.observeForever { t -> Timber.i("DataStore.bolusConditionsPrompt=$t") }
        bolusConditionsPromptAcknowledged.observeForever { t -> Timber.i("DataStore.bolusConditionsPromptAcknowledged=$t") }
        bolusConditionsExcluded.observeForever { t -> Timber.i("DataStore.bolusConditionsExcluded=$t") }
        bolusFinalParameters.observeForever { t -> Timber.i("DataStore.bolusFinalParameters=$t") }
        bolusFinalCalcUnits.observeForever { t -> Timber.i("DataStore.bolusFinalCalcUnits=$t") }
        bolusFinalConditions.observeForever { t -> Timber.i("DataStore.bolusFinalConditions=$t") }
        bolusMinNotifyThreshold.observeForever { t -> Timber.i("DataStore.bolusMinNotifyThreshold=$t") }

        timeSinceResetResponse.observeForever { t -> Timber.i("DataStore.timeSinceResetResponse=$t") }
        bolusPermissionResponse.observeForever { t -> Timber.i("DataStore.bolusPermissionResponse=$t") }
        bolusCarbEntryResponse.observeForever { t -> Timber.i("DataStore.bolusCarbEntryResponse=$t") }
        bolusInitiateResponse.observeForever { t -> Timber.i("DataStore.bolusInitiateResponse=$t") }
        bolusCancelResponse.observeForever { t -> Timber.i("DataStore.bolusCancelResponse=$t") }
        bolusCurrentResponse.observeForever { t -> Timber.i("DataStore.bolusCurrentResponse=$t") }
    }
}