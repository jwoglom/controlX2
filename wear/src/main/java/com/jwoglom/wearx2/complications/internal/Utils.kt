package com.jwoglom.wearx2.complications.internal

import android.content.Context
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable
import com.jwoglom.wearx2.util.DataClientState
import timber.log.Timber
import java.util.concurrent.TimeUnit

fun initGoogleApi(context: Context): Boolean {
    val mApiClient = GoogleApiClient.Builder(context)
        .addApi(Wearable.API)
        .build()

    Timber.d("create: mApiClient: $mApiClient")
    mApiClient.connect()

    var remaining = 1000
    while (!mApiClient.isConnected && remaining >= 0) {
        Timber.d("waiting for mApiClient (remaining: ${remaining}ms)")
        Thread.sleep(100)
        remaining -= 100
    }

    Timber.d("mApiClient connected: ${mApiClient.isConnected}")

    return mApiClient.isConnected
}