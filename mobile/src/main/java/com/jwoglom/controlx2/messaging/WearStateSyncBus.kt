package com.jwoglom.controlx2.messaging

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.jwoglom.controlx2.shared.messaging.StateSyncBus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant

/**
 * Wear OS implementation of StateSyncBus using Google Wearable Data Layer API.
 * Wraps DataClient for persistent state synchronization between phone and watch.
 */
class WearStateSyncBus(context: Context) : StateSyncBus {
    private val dataClient: DataClient = Wearable.getDataClient(context)

    public val isWearableApiSupported = GoogleApiAvailability.getInstance().checkApiAvailability(dataClient).isSuccessful

    init {
        Timber.d("WearStateSyncBus initialized with Wearable Data Layer")
    }

    override suspend fun putState(key: String, value: Pair<String, Instant>?) {
        if (!isWearableApiSupported) {
            return
        }
        Timber.d("WearStateSyncBus.putState: $key = $value")

        try {
            if (value == null) {
                // Delete the data item
                val uri = PutDataMapRequest.create("/state/$key").uri
                dataClient.deleteDataItems(uri).await()
            } else {
                // Put the data item using DataMap
                val putDataMapReq = PutDataMapRequest.create("/state/$key")
                putDataMapReq.dataMap.putString("value", value.first)
                putDataMapReq.dataMap.putLong("timestamp", value.second.toEpochMilli())
                putDataMapReq.setUrgent()

                val putDataReq = putDataMapReq.asPutDataRequest()
                dataClient.putDataItem(putDataReq).await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error putting state: $key")
        }
    }

    override suspend fun getState(key: String): Pair<String, Instant>? {
        return try {
            val dataItems = dataClient.getDataItems(android.net.Uri.parse("wear://*/state/$key")).await()
            try {
                if (dataItems.count > 0) {
                    val dataItem = dataItems.get(0)
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val valueStr = dataMap.getString("value")
                    val timestamp = dataMap.getLong("timestamp")

                    if (valueStr != null) {
                        Pair(valueStr, Instant.ofEpochMilli(timestamp))
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                dataItems.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting state: $key")
            null
        }
    }

    override suspend fun getAllStates(): Map<String, Pair<String, Instant>> {
        val result = mutableMapOf<String, Pair<String, Instant>>()

        try {
            val dataItems = dataClient.getDataItems(android.net.Uri.parse("wear://*/state/*")).await()
            try {
                for (i in 0 until dataItems.count) {
                    val dataItem = dataItems.get(i)
                    val path = dataItem.uri.path

                    if (path?.startsWith("/state/") == true) {
                        val key = path.removePrefix("/state/")
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val valueStr = dataMap.getString("value")
                        val timestamp = dataMap.getLong("timestamp")

                        if (valueStr != null) {
                            result[key] = Pair(valueStr, Instant.ofEpochMilli(timestamp))
                        }
                    }
                }
            } finally {
                dataItems.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting all states")
        }

        Timber.d("WearStateSyncBus.getAllStates: ${result.size} states")
        return result
    }

    override fun observeState(key: String): Flow<Pair<String, Instant>?> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { dataEvents: DataEventBuffer ->
            dataEvents.forEach { event: DataEvent ->
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == "/state/$key") {
                    try {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val valueStr = dataMap.getString("value")
                        val timestamp = dataMap.getLong("timestamp")

                        if (valueStr != null) {
                            trySend(Pair(valueStr, Instant.ofEpochMilli(timestamp)))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error in observeState listener")
                    }
                }
            }
            dataEvents.release()
        }

        dataClient.addListener(listener)

        // Send current value
        val currentValue = getState(key)
        trySend(currentValue)

        awaitClose {
            dataClient.removeListener(listener)
        }
    }

    override suspend fun clearAllStates() {
        if (!isWearableApiSupported) {
            return
        }
        Timber.d("WearStateSyncBus.clearAllStates")

        try {
            val dataItems = dataClient.getDataItems(android.net.Uri.parse("wear://*/state/*")).await()
            try {
                for (i in 0 until dataItems.count) {
                    val dataItem = dataItems.get(i)
                    dataClient.deleteDataItems(dataItem.uri).await()
                }
            } finally {
                dataItems.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing all states")
        }
    }

    override fun close() {
        Timber.d("WearStateSyncBus.close()")
        // DataClient doesn't need explicit cleanup
    }
}
