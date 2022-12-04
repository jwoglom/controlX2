package com.jwoglom.wearx2.presentation

import androidx.lifecycle.MutableLiveData
import timber.log.Timber

class DataStore {
    val batteryPercent = MutableLiveData<Int>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()

    init {
        batteryPercent.observeForever { t -> Timber.i("DataStore.batteryPercent=$t") }
        iobUnits.observeForever { t -> Timber.i("DataStore.iobUnits=$t") }
        cartridgeRemainingUnits.observeForever { t -> Timber.i("DataStore.cartridgeRemainingUnits=$t") }
    }
}