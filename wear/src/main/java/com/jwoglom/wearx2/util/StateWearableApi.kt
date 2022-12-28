package com.jwoglom.wearx2.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import timber.log.Timber
import java.time.Instant

// todo until timber is set up for complications
const val tag = "WearX2:StateWearableApi"
@SuppressLint("LogNotTimber")
class StateWearableApi(val apiClient: GoogleApiClient, val nodeIds: List<String>) {
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
        var res: DataApi.DataItemResult? = null
        nodeIds.forEach {
            val r = Wearable.DataApi.getDataItem(apiClient, uriFor(key, it)).await()
            if (!r.status.isSuccess) {
                Log.d(tag, "failed to get StateWearableApi: $r ${r.status}")
            } else {
                Log.d(tag, "successful node: $it")
                res = r
            }
        }
        if (res == null) {
            Log.w(tag, "no StateWearableApi data found")
            return null
        }
        val s = res!!.dataItem.data.toString()
        val parts = s.split(";;", limit = 2)
        if (parts.size != 2) {
            return null
        }
        Timber.d("StateWearableApi get $key=$parts")
        return Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
    }

    private fun set(key: String, pair: Pair<String, Instant>?) {
        // todo
//        pair?.let {
//            Timber.d("StateWearableApi set $key=$pair")
//            Wearable.DataApi.putDataItem(apiClient, PutDataRequest.create("wear://StateWearableApi/${key}").setData("${pair.first};;${pair.second.toEpochMilli()}".toByteArray()))
//        }
    }

    private fun uriFor(key: String, nodeId: String): Uri {
        return Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path("/${key}").build()
    }
}

fun connectGoogleApi(context: Context): GoogleApiClient {
    val mApiClient = GoogleApiClient.Builder(context)
        .addApi(Wearable.API)
        .build()

    mApiClient.connect()
    while (!mApiClient.isConnected && mApiClient.hasConnectedApi(Wearable.API)) {
        Timber.d("StateWearableApi is waiting on mApiClient connection")
        Thread.sleep(50)
    }

    return mApiClient
}

fun getNodeIds(apiClient: GoogleApiClient): List<String> {
    val nodes = Wearable.NodeApi.getConnectedNodes(apiClient).await()
    return nodes.nodes.map { node -> node.id }
}

fun getStateWearableApi(context: Context): StateWearableApi {
    val api = connectGoogleApi(context)
    return StateWearableApi(api, getNodeIds(api))
}