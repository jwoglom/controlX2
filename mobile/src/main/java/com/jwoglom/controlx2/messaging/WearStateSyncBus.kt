package com.jwoglom.controlx2.messaging

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
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

    init {
        Timber.d("WearStateSyncBus initialized with Wearable Data Layer")
    }

    override suspend fun putState(key: String, value: Pair<String, Instant>?) {
        Timber.d("WearStateSyncBus.putState: $key = $value")

        try {
            if (value == null) {
                // Delete the data item
                val uri = PutDataRequest.create("/state/$key").uri
                dataClient.deleteDataItems(uri).await()
            } else {
                // Put the data item
                val data = "${value.first};;${value.second.toEpochMilli()}".toByteArray()
                val request = PutDataRequest.create("/state/$key")
                    .setData(data)
                    .setUrgent()

                dataClient.putDataItem(request).await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error putting state: $key")
        }
    }

    override suspend fun getState(key: String): Pair<String, Instant>? {
        return try {
            val dataItems = dataClient.dataItems.await()
            dataItems.use { buffer ->
                for (i in 0 until buffer.count) {
                    val dataItem = buffer.get(i)
                    val path = dataItem.uri.path

                    if (path == "/state/$key") {
                        dataItem.data?.let { data ->
                            val parts = String(data).split(";;", limit = 2)
                            if (parts.size == 2) {
                                return Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting state: $key")
            null
        }
    }

    override suspend fun getAllStates(): Map<String, Pair<String, Instant>> {
        val result = mutableMapOf<String, Pair<String, Instant>>()

        try {
            val dataItems = dataClient.dataItems.await()
            dataItems.use { buffer ->
                for (i in 0 until buffer.count) {
                    val dataItem = buffer.get(i)
                    val path = dataItem.uri.path

                    if (path?.startsWith("/state/") == true) {
                        val key = path.removePrefix("/state/")
                        dataItem.data?.let { data ->
                            val parts = String(data).split(";;", limit = 2)
                            if (parts.size == 2) {
                                result[key] = Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong()))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting all states")
        }

        Timber.d("WearStateSyncBus.getAllStates: ${result.size} states")
        return result
    }

    override fun observeState(key: String): Flow<Pair<String, Instant>?> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            dataEvents.forEach { event ->
                if (event.dataItem.uri.path == "/state/$key") {
                    event.dataItem.data?.let { data ->
                        val parts = String(data).split(";;", limit = 2)
                        if (parts.size == 2) {
                            trySend(Pair(parts[0], Instant.ofEpochMilli(parts[1].toLong())))
                        }
                    }
                }
            }
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
        Timber.d("WearStateSyncBus.clearAllStates")

        try {
            val dataItems = dataClient.dataItems.await()
            dataItems.use { buffer ->
                for (i in 0 until buffer.count) {
                    val dataItem = buffer.get(i)
                    val path = dataItem.uri.path

                    if (path?.startsWith("/state/") == true) {
                        dataClient.deleteDataItems(dataItem.uri).await()
                    }
                }
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
