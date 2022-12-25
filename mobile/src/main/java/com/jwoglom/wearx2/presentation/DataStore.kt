package com.jwoglom.wearx2.presentation

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
import com.jwoglom.wearx2.presentation.screens.SetupStage
import timber.log.Timber

class DataStore {
    val setupStage = MutableLiveData<SetupStage>(SetupStage.WAITING_PUMPX2_INIT)
    val setupDeviceName = MutableLiveData<String>()

    init {
        setupStage.observeForever { t -> Timber.i("DataStore.setupStage=$t") }
        setupDeviceName.observeForever { t -> Timber.i("DataStore.setupDeviceName=$t") }
    }
}