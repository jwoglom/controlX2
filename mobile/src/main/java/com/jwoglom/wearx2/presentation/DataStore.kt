package com.jwoglom.wearx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.wearx2.presentation.screens.PumpSetupStage
import timber.log.Timber
import java.time.Instant

class DataStore {
    val pumpConnected = MutableLiveData<Boolean>()
    val pumpLastConnectionTimestamp = MutableLiveData<Instant>()
    val pumpLastMessageTimestamp = MutableLiveData<Instant>()
    val watchConnected = MutableLiveData<Boolean>()

    val pumpSetupStage = MutableLiveData<PumpSetupStage>(PumpSetupStage.WAITING_PUMPX2_INIT)
    val setupDeviceName = MutableLiveData<String>()
    val setupDeviceModel = MutableLiveData<String>()
    val pumpCriticalError = MutableLiveData<Pair<String, Instant>>()

    val batteryPercent = MutableLiveData<Int>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
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
    val maxBolusAmount = MutableLiveData<Int>()

    val landingBasalDisplayedText = MutableLiveData<String>()
    val landingControlIQDisplayedText = MutableLiveData<String>()

    val historyLogStatus = MutableLiveData<HistoryLogStatusResponse>()

    val historyLogCache = MutableLiveData<MutableMap<Long, HistoryLog>>(mutableMapOf())
    val debugMessageCache = MutableLiveData<List<Pair<Message, Instant>>>()
    val debugPromptAwaitingResponses = MutableLiveData<MutableSet<String>>()

    init {
        pumpConnected.observeForever { t -> Timber.i("DataStore.pumpConnected=$t") }
        pumpLastConnectionTimestamp.observeForever { t -> Timber.i("DataStore.pumpLastConnectionTimestamp=$t") }
        pumpLastMessageTimestamp.observeForever { t -> Timber.i("DataStore.pumpLastMessageTimestamp=$t") }
        watchConnected.observeForever { t -> Timber.i("DataStore.watchConnected=$t") }

        pumpSetupStage.observeForever { t -> Timber.i("DataStore.setupStage=$t") }
        setupDeviceName.observeForever { t -> Timber.i("DataStore.setupDeviceName=$t") }
        setupDeviceModel.observeForever { t -> Timber.i("DataStore.setupDeviceModel=$t") }
        pumpCriticalError.observeForever { t -> Timber.i("DataStore.pumpCriticalError=$t") }

        batteryPercent.observeForever { t -> Timber.i("DataStore.batteryPercent=$t") }
        iobUnits.observeForever { t -> Timber.i("DataStore.iobUnits=$t") }
        cartridgeRemainingUnits.observeForever { t -> Timber.i("DataStore.cartridgeRemainingUnits=$t") }
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

        historyLogStatus.observeForever { t -> Timber.i("DataStore.historyLogStatus=$t") }

        historyLogCache.observeForever { t -> Timber.i("DataStore.historyLogCache=$t") }
        debugMessageCache.observeForever { t -> Timber.i("DataStore.debugMessageCache=$t") }
        debugPromptAwaitingResponses.observeForever { t -> Timber.i("DataStore.debugPromptAwaitingResponses=$t") }
    }


}