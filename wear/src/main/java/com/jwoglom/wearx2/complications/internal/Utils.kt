package com.jwoglom.wearx2.complications.internal

import android.content.Context
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.jwoglom.wearx2.util.DataClientState
import timber.log.Timber

fun initGoogleApi(context: Context) {
    val mApiClient = GoogleApiClient.Builder(context)
        .addApi(Wearable.API)
        .build()

    Timber.d("create: mApiClient: $mApiClient")
    mApiClient.connect()

    while (!mApiClient.isConnected) {
        Timber.d("waiting for mApiClient")
        Thread.sleep(100)
    }

    Timber.d("mApiClient connected")
}