package com.jwoglom.wearx2.util

import android.content.Context
import com.google.android.gms.common.data.AbstractDataBuffer
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import timber.log.Timber
import java.time.Instant

class DataClientState(private val context: Context) {
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

    var pumpCartridgeUnits: Pair<String, Instant>?
        get() = get("pumpCartridgeUnits")
        set(value) {
            set("pumpCartridgeUnits", value)
        }

    var pumpCurrentBasal: Pair<String, Instant>?
        get() = get("pumpCurrentBasal")
        set(value) {
            set("pumpCurrentBasal", value)
        }

    private fun get(key: String): Pair<String, Instant>? {
        Timber.d("DataClientState fields() = ${fields()}")
        val parts = fields()[key]
        Timber.d("DataClientState get $key=$parts")
        return parts
    }

    private fun set(key: String, pair: Pair<String, Instant>?) {
        Timber.d("DataClientState set $key=$pair")
        client().putDataItem(
            PutDataRequest.create("/state/$key")
                .setData("${pair?.first};;${pair?.second?.toEpochMilli()}".toByteArray())
                .setUrgent()
        )
    }

    private fun fields(): Map<String, Pair<String, Instant>> {
        val m = mutableMapOf<String, Pair<String, Instant>>()

        val dataItems = client().dataItems
        while (!dataItems.isComplete) Thread.sleep(50);
        dataItems
            .result.forEach { dataItem ->
            dataItem.data?.let {
                val parts = String(it).split(";;", limit = 2)
                if (parts.size == 2) {
                    val pair = Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
                    dataItem.uri.path?.let { path ->
                        if (path.removePrefix("/").startsWith("state/")) {
                            m[path.removePrefix("/state/")] = pair
                        }
                    }
                }
            }
        }
        dataItems.result.release()

        return m
    }

    private fun client(): DataClient {
        return Wearable.getDataClient(context)
    }
}