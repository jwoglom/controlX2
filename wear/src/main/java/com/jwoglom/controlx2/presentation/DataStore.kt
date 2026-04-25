package com.jwoglom.controlx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.controlx2.presentation.navigation.PumpSetupStage
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
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
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import timber.log.Timber
import java.time.Instant

class DataStore {
    val connectionStatus = MutableLiveData<String>()
    val pumpConnected = MutableLiveData<Boolean>()
    val pumpLastConnectionTimestamp = MutableLiveData<Instant>()
    val pumpLastMessageTimestamp = MutableLiveData<Instant>()
    // Short pump serial id (last 3 digits of BT device name). Reactive mirror
    // of `WearPrefs.currentPumpSid()` so Compose screens can recompose when a
    // pump pairs for the first time without the user navigating away and back.
    // `-1` means "no pump has ever been bound on this install".
    val currentPumpSid = MutableLiveData<Int>(-1)

    val glucoseUnitPreference = MutableLiveData<GlucoseUnit>()
    val batteryPercent = MutableLiveData<Int>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val cartridgeRemainingEstimate = MutableLiveData<Boolean>()
    val lastBolusStatus = MutableLiveData<String>()
    val controlIQStatus = MutableLiveData<String>()
    val controlIQMode = MutableLiveData<UserMode>()
    val basalRate = MutableLiveData<String>()
    var basalStatus = MutableLiveData<BasalStatus>()
    // Temp-rate state, populated from TempRateResponse (refreshed on entry to
    // the watch TempBasalScreen + every poll after set/cancel). Mirrors mobile
    // DataStore.tempRateActive / tempRateDetails. `basalStatus` also exposes
    // TEMP_RATE / ZERO_TEMP_RATE, but tempRateActive gives us the boolean
    // directly and tempRateDetails carries the percent + duration for display.
    val tempRateActive = MutableLiveData<Boolean>()
    val tempRateDetails = MutableLiveData<TempRateResponse>()
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
    val idpManager = MutableLiveData<IDPManager>(IDPManager())
    // Active alerts / alarms / reminders / CGM alerts. Aggregated from
    // pumpx2 NotificationBundle.add(message), same shape mobile uses.
    val notificationBundle = MutableLiveData<NotificationBundle>(NotificationBundle())

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
    val wearAutoApproveTimeout = MutableLiveData<Int>()

    // Watch-as-pump-host pairing flow state (populated only when DeviceRole.PUMP_HOST).
    val pumpSetupStage = MutableLiveData<PumpSetupStage>()
    val pumpFinderPumps = MutableLiveData<List<Pair<String, String>>>()
    val setupDeviceName = MutableLiveData<String>()
    val setupPairingCodeType = MutableLiveData<PairingCodeType>()
    val pumpReadyState = MutableLiveData<PumpReadyState>()
    val pumpPairingError = MutableLiveData<String?>()

    val timeSinceResetResponse = MutableLiveData<TimeSinceResetResponse>()
    val bolusPermissionResponse = MutableLiveData<BolusPermissionResponse>()
    val bolusCarbEntryResponse = MutableLiveData<RemoteCarbEntryResponse>()
    val bolusInitiateResponse = MutableLiveData<InitiateBolusResponse>()
    val bolusCancelResponse = MutableLiveData<CancelBolusResponse>()
    val lastBolusStatusResponse = MutableLiveData<LastBolusStatusAbstractResponse>()
    val bolusCurrentResponse = MutableLiveData<CurrentBolusStatusResponse>()

    private fun <T> MutableLiveData<T>.logOnChange(fieldName: String) {
        var initialized = false
        var previous: Any? = null
        observeForever { current ->
            if (!initialized || previous != current) {
                Timber.i("DataStore.$fieldName=$current")
                previous = current
                initialized = true
            }
        }
    }

    init {
        connectionStatus.logOnChange("connectionStatus")
        pumpConnected.logOnChange("pumpConnected")
        pumpLastConnectionTimestamp.logOnChange("pumpLastConnectionTimestamp")
        pumpLastMessageTimestamp.logOnChange("pumpLastMessageTimestamp")
        currentPumpSid.logOnChange("currentPumpSid")

        batteryPercent.logOnChange("batteryPercent")
        iobUnits.logOnChange("iobUnits")
        cartridgeRemainingUnits.logOnChange("cartridgeRemainingUnits")
        cartridgeRemainingEstimate.logOnChange("cartridgeRemainingEstimate")
        lastBolusStatus.logOnChange("lastBolusStatus")
        controlIQStatus.logOnChange("controlIQStatus")
        controlIQMode.logOnChange("controlIQMode")
        basalRate.logOnChange("basalRate")
        basalStatus.logOnChange("basalStatus")
        tempRateActive.logOnChange("tempRateActive")
        tempRateDetails.logOnChange("tempRateDetails")
        cgmSessionState.logOnChange("cgmSessionState")
        cgmSessionExpireExact.logOnChange("cgmSessionExpireExact")
        cgmSessionExpireRelative.logOnChange("cgmSessionExpireRelative")
        cgmTransmitterStatus.logOnChange("cgmTransmitterStatus")
        cgmReading.logOnChange("cgmLastReading")
        cgmDelta.logOnChange("cgmDelta")
        cgmStatusText.logOnChange("cgmStatusText")
        cgmHighLowState.logOnChange("cgmHighLowState")
        cgmDeltaArrow.logOnChange("cgmDeltaArrow")
        bolusCalcDataSnapshot.logOnChange("bolusCalcDataSnapshot")
        bolusCalcLastBG.logOnChange("bolusCalcLastBG")
        maxBolusAmount.logOnChange("maxBolusAmount")
        maxCarbAmount.logOnChange("maxCarbAmount")
        idpManager.logOnChange("idpManager")
        notificationBundle.logOnChange("notificationBundle")

        landingBasalDisplayedText.logOnChange("landingBasalDisplayedText")
        landingControlIQDisplayedText.logOnChange("landingControlIQDisplayedText")

        bolusUnitsDisplayedText.logOnChange("bolusUnitsDisplayedText")
        bolusUnitsDisplayedSubtitle.logOnChange("bolusUnitsDisplayedSubtitle")
        bolusBGDisplayedText.logOnChange("bolusBGDisplayedText")
        bolusBGDisplayedSubtitle.logOnChange("bolusBGDisplayedSubtitle")

        bolusCalculatorBuilder.logOnChange("bolusCalculatorBuilder")
        bolusCurrentParameters.logOnChange("bolusCurrentParameters")
        bolusCurrentConditions.logOnChange("bolusCurrentConditions")
        bolusConditionsPrompt.logOnChange("bolusConditionsPrompt")
        bolusConditionsPromptAcknowledged.logOnChange("bolusConditionsPromptAcknowledged")
        bolusConditionsExcluded.logOnChange("bolusConditionsExcluded")
        bolusFinalParameters.logOnChange("bolusFinalParameters")
        bolusFinalCalcUnits.logOnChange("bolusFinalCalcUnits")
        bolusFinalConditions.logOnChange("bolusFinalConditions")
        bolusMinNotifyThreshold.logOnChange("bolusMinNotifyThreshold")
        wearAutoApproveTimeout.logOnChange("wearAutoApproveTimeout")

        pumpSetupStage.logOnChange("pumpSetupStage")
        pumpFinderPumps.logOnChange("pumpFinderPumps")
        setupDeviceName.logOnChange("setupDeviceName")
        setupPairingCodeType.logOnChange("setupPairingCodeType")
        pumpReadyState.logOnChange("pumpReadyState")
        pumpPairingError.logOnChange("pumpPairingError")

        timeSinceResetResponse.logOnChange("timeSinceResetResponse")
        bolusPermissionResponse.logOnChange("bolusPermissionResponse")
        bolusCarbEntryResponse.logOnChange("bolusCarbEntryResponse")
        bolusInitiateResponse.logOnChange("bolusInitiateResponse")
        bolusCancelResponse.logOnChange("bolusCancelResponse")
        bolusCurrentResponse.logOnChange("bolusCurrentResponse")
    }
}
