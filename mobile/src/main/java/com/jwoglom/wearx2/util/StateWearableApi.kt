package com.jwoglom.wearx2.util

import android.net.Uri
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import timber.log.Timber
import java.time.Instant

// todo: move to shared
class StateWearableApi(val apiClient: GoogleApiClient) {
    var connected: Pair<String, Instant>?
        get() = get("connected")
        set(value) {
            set("connected", value)
        }

    var pumpBattery: Pair<String, Instant>?
        get() = get("pumpBattery")
        set(value) {
            set("pumpBattery", value)
        }

    var pumpIOB: Pair<String, Instant>?
        get() = get("pumpIOB")
        set(value) {
            set("pumpIOB", value)
        }

    private fun get(key: String): Pair<String, Instant>? {
        return null // todo
//        val res = Wearable.DataApi.getDataItem(apiClient, Uri.parse("wear://StateWearableApi/${key}")).await()
//        if (!res.status.isSuccess) {
//            Timber.e("failed to get StateWearableApi: $res ${res.status}")
//            return null
//        }
//        val s = res.dataItem.data.toString()
//        val parts = s.split(";;", limit = 2)
//        if (parts.size != 2) {
//            return null
//        }
//        Timber.d("StateWearableApi get $key=$parts")
//        return Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
    }

    private fun set(key: String, pair: Pair<String, Instant>?) {
        pair?.let {
            Timber.d("StateWearableApi set $key=$pair")
            Wearable.DataApi.putDataItem(apiClient, PutDataRequest.create("/${key}").setData("${pair.first};;${pair.second.toEpochMilli()}".toByteArray()))
        }
    }
}