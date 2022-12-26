package com.jwoglom.wearx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.wearx2.presentation.screens.PumpSetupStage
import timber.log.Timber

class DataStore {
    val pumpConnected = MutableLiveData<Boolean>()
    val watchConnected = MutableLiveData<Boolean>()

    val pumpSetupStage = MutableLiveData<PumpSetupStage>(PumpSetupStage.WAITING_PUMPX2_INIT)
    val setupDeviceName = MutableLiveData<String>()
    val setupDeviceModel = MutableLiveData<String>()

    init {
        pumpConnected.observeForever { t -> Timber.i("DataStore.pumpConnected=$t") }
        watchConnected.observeForever { t -> Timber.i("DataStore.watchConnected=$t") }

        pumpSetupStage.observeForever { t -> Timber.i("DataStore.setupStage=$t") }
        setupDeviceName.observeForever { t -> Timber.i("DataStore.setupDeviceName=$t") }
        setupDeviceModel.observeForever { t -> Timber.i("DataStore.setupDeviceModel=$t") }
    }
}