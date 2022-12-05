package com.jwoglom.wearx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import timber.log.Timber

class DataStore {
    val batteryPercent = MutableLiveData<Int>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val lastBolusStatus = MutableLiveData<String>()
    val controlIQStatus = MutableLiveData<String>()
    val basalStatus = MutableLiveData<String>()
    val cgmSessionState = MutableLiveData<String>()
    val cgmTransmitterStatus = MutableLiveData<String>()
    val cgmReading = MutableLiveData<Int>()
    val cgmDelta = MutableLiveData<Int>()
    val cgmStatusText = MutableLiveData<String>()
    val cgmHighLowState = MutableLiveData<String>()
    val cgmDeltaArrow = MutableLiveData<String>()
    val bolusCalcDataSnapshot = MutableLiveData<BolusCalcDataSnapshotResponse>()
    val bolusCalcLastBG = MutableLiveData<LastBGResponse>()

    init {
        batteryPercent.observeForever { t -> Timber.i("DataStore.batteryPercent=$t") }
        iobUnits.observeForever { t -> Timber.i("DataStore.iobUnits=$t") }
        cartridgeRemainingUnits.observeForever { t -> Timber.i("DataStore.cartridgeRemainingUnits=$t") }
        lastBolusStatus.observeForever { t -> Timber.i("DataStore.lastBolusStatus=$t") }
        controlIQStatus.observeForever { t -> Timber.i("DataStore.controlIQStatus=$t") }
        basalStatus.observeForever { t -> Timber.i("DataStore.basalStatus=$t") }
        cgmSessionState.observeForever { t -> Timber.i("DataStore.cgmSessionState=$t") }
        cgmTransmitterStatus.observeForever { t -> Timber.i("DataStore.cgmTransmitterStatus=$t") }
        cgmReading.observeForever { t -> Timber.i("DataStore.cgmLastReading=$t") }
        cgmDelta.observeForever { t -> Timber.i("DataStore.cgmDelta=$t") }
        cgmStatusText.observeForever { t -> Timber.i("DataStore.cgmStatusText=$t") }
        cgmHighLowState.observeForever { t -> Timber.i("DataStore.cgmHighLowState=$t") }
        cgmDeltaArrow.observeForever { t -> Timber.i("DataStore.cgmDeltaArrow=$t") }
        bolusCalcDataSnapshot.observeForever { t -> Timber.i("DataStore.bolusCalcDataSnapshot=$t") }
        bolusCalcLastBG.observeForever { t -> Timber.i("DataStore.bolusCalcLastBG=$t") }
    }
}