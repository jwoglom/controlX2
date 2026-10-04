package com.jwoglom.controlx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.pump.ErrorPresentation
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQSleepScheduleResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import timber.log.Timber
import java.time.Instant

/**
 * Resolved extended-bolus split used to build an [com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest].
 *
 * Volumes are pre-resolved (in milliunits) from a single calculation pass so the request
 * builder performs no further math: [nowMilliUnits] maps to the request's totalVolume
 * (delivered immediately) and [extendedMilliUnits] to extendedVolume (delivered linearly
 * over [durationSeconds]). The total bolus delivered is nowMilliUnits + extendedMilliUnits.
 *
 * @param nowMilliUnits       portion delivered immediately, in milliunits.
 * @param extendedMilliUnits  portion delivered over the extended window, in milliunits.
 * @param durationSeconds     length of the extended window, in seconds (pumpx2 expects seconds).
 */
data class BolusExtendedParameters(
    val nowMilliUnits: Long,
    val extendedMilliUnits: Long,
    val durationSeconds: Long,
)

class DataStore {
    val pumpConnected = MutableLiveData<Boolean>()
    val pumpLastConnectionTimestamp = MutableLiveData<Instant>()
    val pumpLastMessageTimestamp = MutableLiveData<Instant>()
    val watchConnected = MutableLiveData<Boolean>()

    val pumpSetupStage = MutableLiveData<PumpSetupStage>(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
    val pumpFinderPumps = MutableLiveData<List<Pair<String, String>>>() // pump name, pump MAC
    val setupDeviceName = MutableLiveData<String>()
    val pumpReadyState = MutableLiveData<PumpReadyState>()
    val setupPairingCodeType = MutableLiveData<PairingCodeType>()
    val pumpSid = MutableLiveData<Int>()
    val setupDeviceModel = MutableLiveData<String>()
    val pumpCriticalError = MutableLiveData<PumpCriticalErrorState?>()

    val notificationBundle = MutableLiveData<NotificationBundle>(NotificationBundle())
    val batteryPercent = MutableLiveData<Int>()
    val batteryCharging = MutableLiveData<Boolean>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val cartridgeRemainingEstimate = MutableLiveData<Boolean>()
    val lastBolusStatus = MutableLiveData<String>()
    val controlIQStatus = MutableLiveData<String>()
    val controlIQMode = MutableLiveData<UserMode>()
    val controlIQEnabled = MutableLiveData<Boolean>()
    // Whether the pump's Control-IQ firmware allows extended boluses while closed-loop is on
    // (PumpFeaturesV2Response CONTROL_IQ_FEATURES → EXTENDED_BOLUS_ENABLED). Null = unknown/not
    // fetched (e.g. pre-V2.5 pump); the extended-bolus UI fails open when this is null.
    val controlIQExtendedBolusEnabled = MutableLiveData<Boolean?>()
    val controlIQWeight = MutableLiveData<Int>()
    val controlIQWeightUnit = MutableLiveData<String>()
    val controlIQTotalDailyInsulin = MutableLiveData<Int>()
    val basalRate = MutableLiveData<String>()
    var basalStatus = MutableLiveData<BasalStatus>()
    var tempRateActive = MutableLiveData<Boolean>()
    var tempRateDetails = MutableLiveData<TempRateResponse>()
    val pumpGlobalsResponse = MutableLiveData<PumpGlobalsResponse>()
    val controlIQInfoResponse = MutableLiveData<ControlIQInfoAbstractResponse>()
    val controlIQSleepScheduleResponse = MutableLiveData<ControlIQSleepScheduleResponse>()
    val globalMaxBolusSettingsResponse = MutableLiveData<GlobalMaxBolusSettingsResponse>()
    val basalLimitSettingsResponse = MutableLiveData<BasalLimitSettingsResponse>()
    val cgmSessionState = MutableLiveData<CGMSessionState>()
    val cgmSessionExpireRelative = MutableLiveData<String>()
    val cgmSessionExpireExact = MutableLiveData<String>()
    val cgmTransmitterStatus = MutableLiveData<String>()
    val cgmReading = MutableLiveData<Int>()
    val cgmDelta = MutableLiveData<Int>()
    val cgmStatusText = MutableLiveData<String>()
    val cgmHighLowState = MutableLiveData<String>()
    val cgmDeltaArrow = MutableLiveData<String>()
    val cgmSetupG6TxId = MutableLiveData<String>()
    val cgmSetupG6SensorCode = MutableLiveData<String>()
    val cgmSetupG7SensorCode = MutableLiveData<String>()
    val savedG7PairingCode = MutableLiveData<Int>()
    val idpManager = MutableLiveData<IDPManager>(IDPManager())

    val bolusCalcDataSnapshot = MutableLiveData<BolusCalcDataSnapshotResponse>()
    val bolusCalcLastBG = MutableLiveData<LastBGResponse>()
    val maxBolusAmount = MutableLiveData<Int>()

    val landingBasalDisplayedText = MutableLiveData<String>()
    val landingControlIQDisplayedText = MutableLiveData<String>()

    val bolusCalculatorBuilder = MutableLiveData<BolusCalculatorBuilder>()
    val bolusCurrentParameters = MutableLiveData<BolusParameters>()
    val bolusCurrentConditions = MutableLiveData<List<BolusCalcCondition>>()
    val bolusConditionsPrompt = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsPromptAcknowledged = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsExcluded = MutableLiveData<MutableSet<BolusCalcCondition>>()
    val bolusFinalParameters = MutableLiveData<BolusParameters>()
    val bolusFinalCalcUnits = MutableLiveData<BolusCalcUnits>()
    val bolusFinalConditions = MutableLiveData<Set<BolusCalcCondition>>()

    val bolusUnitsRawValue = MutableLiveData<String?>()
    val bolusCarbsRawValue = MutableLiveData<String?>()
    val bolusGlucoseRawValue = MutableLiveData<String?>()

    // Extended ("square wave") bolus: deliver bolusExtendedNowPercent of the total
    // bolus immediately and the remainder linearly over the configured duration.
    val bolusExtendedEnabled = MutableLiveData<Boolean>()
    val bolusExtendedNowPercentRawValue = MutableLiveData<String?>()
    val bolusExtendedNowUnitsRawValue = MutableLiveData<String?>()
    val bolusExtendedHoursRawValue = MutableLiveData<String?>()
    val bolusExtendedMinutesRawValue = MutableLiveData<String?>()
    // Resolved split (computed from the raw inputs + calculated total) used to build
    // the InitiateBolusRequest. Null when extended bolus is disabled or not yet valid.
    val bolusCurrentExtendedParameters = MutableLiveData<BolusExtendedParameters?>()
    val bolusFinalExtendedParameters = MutableLiveData<BolusExtendedParameters?>()

    val tempRatePercentRawValue = MutableLiveData<String?>()
    val tempRateMinutesRawValue = MutableLiveData<String?>()
    val tempRateHoursRawValue = MutableLiveData<String?>()

    val timeSinceResetResponse = MutableLiveData<TimeSinceResetResponse>()
    val loadStatusResponse = MutableLiveData<LoadStatusResponse>()
    val bolusPermissionResponse = MutableLiveData<BolusPermissionResponse>()
    val bolusCarbEntryResponse = MutableLiveData<RemoteCarbEntryResponse>()
    val bolusInitiateResponse = MutableLiveData<InitiateBolusResponse>()
    val bolusCancelResponse = MutableLiveData<CancelBolusResponse>()
    val lastBolusStatusResponse = MutableLiveData<LastBolusStatusAbstractResponse>()
    val bolusCurrentResponse = MutableLiveData<CurrentBolusStatusResponse>()

    val inChangeCartridgeMode = MutableLiveData<Boolean>()
    val inFillTubingMode = MutableLiveData<Boolean>()
    val enterChangeCartridgeState = MutableLiveData<EnterChangeCartridgeModeStateStreamResponse>()
    val detectingCartridgeState = MutableLiveData<DetectingCartridgeStateStreamResponse>()
    val fillTubingState = MutableLiveData<FillTubingStateStreamResponse>()
    val exitFillTubingState = MutableLiveData<ExitFillTubingModeStateStreamResponse>()
    val fillCannulaState = MutableLiveData<FillCannulaStateStreamResponse>()
    val pumpingState = MutableLiveData<PumpingStateStreamResponse>()

    val historyLogStatus = MutableLiveData<HistoryLogStatusResponse>()

    val historyLogCache = MutableLiveData<MutableMap<Long, HistoryLog>>(mutableMapOf())
    val debugMessageCache = MutableLiveData<List<Pair<Message, Instant>>>()
    val debugPromptAwaitingResponses = MutableLiveData<MutableSet<String>>()

    val glucoseUnitPreference = MutableLiveData<GlucoseUnit>()

    private fun <T> MutableLiveData<T>.logOnChange(fieldName: String) {
        var initialized = false
        var previous: Any? = null
        observeForever { current ->
            if (!initialized || previous != current) {
                Timber.d("DataStore.$fieldName=$current")
                previous = current
                initialized = true
            }
        }
    }

    init {
        pumpConnected.logOnChange("pumpConnected")
        pumpLastConnectionTimestamp.logOnChange("pumpLastConnectionTimestamp")
        pumpLastMessageTimestamp.logOnChange("pumpLastMessageTimestamp")
        watchConnected.logOnChange("watchConnected")

        pumpSetupStage.logOnChange("setupStage")
        pumpFinderPumps.logOnChange("pumpFinderPumps")
        setupDeviceName.logOnChange("setupDeviceName")
        setupPairingCodeType.logOnChange("setupPairingCodeType")
        pumpSid.logOnChange("pumpSid")
        setupDeviceModel.logOnChange("setupDeviceModel")
        pumpCriticalError.logOnChange("pumpCriticalError")

        notificationBundle.logOnChange("notificationBundle")
        batteryPercent.logOnChange("batteryPercent")
        batteryCharging.logOnChange("batteryCharging")
        iobUnits.logOnChange("iobUnits")
        cartridgeRemainingUnits.logOnChange("cartridgeRemainingUnits")
        cartridgeRemainingEstimate.logOnChange("cartridgeRemainingEstimate")
        lastBolusStatus.logOnChange("lastBolusStatus")
        controlIQStatus.logOnChange("controlIQStatus")
        controlIQMode.logOnChange("controlIQMode")
        controlIQEnabled.logOnChange("controlIQEnabled")
        controlIQExtendedBolusEnabled.logOnChange("controlIQExtendedBolusEnabled")
        controlIQWeight.logOnChange("controlIQWeight")
        controlIQWeightUnit.logOnChange("controlIQWeightUnit")
        controlIQTotalDailyInsulin.logOnChange("controlIQTotalDailyInsulin")
        basalRate.logOnChange("basalRate")
        basalStatus.logOnChange("basalStatus")
        tempRateActive.logOnChange("tempRateActive")
        tempRateDetails.logOnChange("tempRateDetails")
        pumpGlobalsResponse.logOnChange("pumpGlobalsResponse")
        controlIQInfoResponse.logOnChange("controlIQInfoResponse")
        controlIQSleepScheduleResponse.logOnChange("controlIQSleepScheduleResponse")
        globalMaxBolusSettingsResponse.logOnChange("globalMaxBolusSettingsResponse")
        basalLimitSettingsResponse.logOnChange("basalLimitSettingsResponse")
        cgmSessionState.logOnChange("cgmSessionState")
        cgmSessionExpireRelative.logOnChange("cgmSessionExpireRelative")
        cgmSessionExpireExact.logOnChange("cgmSessionExpireExact")
        cgmTransmitterStatus.logOnChange("cgmTransmitterStatus")
        cgmReading.logOnChange("cgmReading")
        cgmDelta.logOnChange("cgmDelta")
        cgmStatusText.logOnChange("cgmStatusText")
        cgmHighLowState.logOnChange("cgmHighLowState")
        cgmDeltaArrow.logOnChange("cgmDeltaArrow")
        cgmSetupG6TxId.logOnChange("cgmSetupG6TxId")
        cgmSetupG6SensorCode.logOnChange("cgmSetupG6SensorId")
        cgmSetupG7SensorCode.logOnChange("cgmSetupG7SensorId")
        savedG7PairingCode.logOnChange("savedG7PairingCode")
        idpManager.logOnChange("idpManager")


        bolusCalcDataSnapshot.logOnChange("bolusCalcDataSnapshot")
        bolusCalcLastBG.logOnChange("bolusCalcLastBG")
        maxBolusAmount.logOnChange("maxBolusAmount")

        landingBasalDisplayedText.logOnChange("landingBasalDisplayedText")
        landingControlIQDisplayedText.logOnChange("landingControlIQDisplayedText")

        bolusCalculatorBuilder.logOnChange("bolusCalculatorBuilder")
        bolusCurrentParameters.logOnChange("bolusCurrentParameters")
        bolusCurrentConditions.logOnChange("bolusCurrentConditions")
        bolusConditionsPrompt.logOnChange("bolusConditionsPrompt")
        bolusConditionsPromptAcknowledged.logOnChange("bolusConditionsPromptAcknowledged")
        bolusConditionsExcluded.logOnChange("bolusConditionsExcluded")
        bolusFinalParameters.logOnChange("bolusFinalParameters")
        bolusFinalCalcUnits.logOnChange("bolusFinalCalcUnits")
        bolusFinalConditions.logOnChange("bolusFinalConditions")

        bolusUnitsRawValue.logOnChange("bolusUnitsRawValue")
        bolusCarbsRawValue.logOnChange("bolusCarbsRawValue")
        bolusGlucoseRawValue.logOnChange("bolusGlucoseRawValue")

        bolusExtendedEnabled.logOnChange("bolusExtendedEnabled")
        bolusExtendedNowPercentRawValue.logOnChange("bolusExtendedNowPercentRawValue")
        bolusExtendedNowUnitsRawValue.logOnChange("bolusExtendedNowUnitsRawValue")
        bolusExtendedHoursRawValue.logOnChange("bolusExtendedHoursRawValue")
        bolusExtendedMinutesRawValue.logOnChange("bolusExtendedMinutesRawValue")
        bolusCurrentExtendedParameters.logOnChange("bolusCurrentExtendedParameters")
        bolusFinalExtendedParameters.logOnChange("bolusFinalExtendedParameters")

        tempRatePercentRawValue.logOnChange("tempRatePercentRawValue")
        tempRateMinutesRawValue.logOnChange("tempRateMinutesRawValue")
        tempRateHoursRawValue.logOnChange("tempRateHoursRawValue")

        timeSinceResetResponse.logOnChange("timeSinceResetResponse")
        loadStatusResponse.logOnChange("loadStatusResponse")
        bolusPermissionResponse.logOnChange("bolusPermissionResponse")
        bolusCarbEntryResponse.logOnChange("bolusCarbEntryResponse")
        bolusInitiateResponse.logOnChange("bolusInitiateResponse")
        bolusCancelResponse.logOnChange("bolusCancelResponse")
        bolusCurrentResponse.logOnChange("bolusCurrentResponse")

        inChangeCartridgeMode.logOnChange("inChangeCartridgeMode")
        inFillTubingMode.logOnChange("inFillTubingMode")
        enterChangeCartridgeState.logOnChange("enterChangeCartridgeState")
        detectingCartridgeState.logOnChange("detectingCartridgeState")
        fillTubingState.logOnChange("fillTubingState")
        exitFillTubingState.logOnChange("exitFillTubingState")
        fillCannulaState.logOnChange("fillCannulaState")
        pumpingState.logOnChange("pumpingState")

        historyLogStatus.logOnChange("historyLogStatus")

        historyLogCache.logOnChange("historyLogCache")
        debugMessageCache.logOnChange("debugMessageCache")
        debugPromptAwaitingResponses.logOnChange("debugPromptAwaitingResponses")

        glucoseUnitPreference.logOnChange("glucoseUnitPreference")
    }


}

/**
 * Coalesced pump-critical-error state held by [DataStore.pumpCriticalError].
 * Same `presentation.name` arriving repeatedly bumps `occurrences`/`lastSeenAt`
 * rather than replacing — see MainActivity's FROM_PUMP_PUMP_CRITICAL_ERROR handler.
 */
data class PumpCriticalErrorState(
    val presentation: ErrorPresentation,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val occurrences: Int,
    val errorStage: PumpSetupStage?,
)
