package com.jwoglom.controlx2.sync.nightscout

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDatabase
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Worker for periodic Nightscout sync
 *
 * Can be integrated into CommService or run standalone.
 * Handles background sync on a periodic interval.
 */
class NightscoutSyncWorker(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val pumpSid: Int
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null
    private var isRunning = false

    /**
     * Start periodic sync
     */
    fun start() {
        if (isRunning) {
            Timber.w("NightscoutSyncWorker already running")
            return
        }

        val config = NightscoutSyncConfig.load(prefs)
        if (!config.enabled) {
            Timber.i("Nightscout sync is disabled, not starting worker")
            return
        }

        isRunning = true
        Timber.i("Starting NightscoutSyncWorker with ${config.syncIntervalMinutes}min interval")

        scheduleNextSync(config.syncIntervalMinutes)
    }

    /**
     * Stop periodic sync
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        isRunning = false
        syncRunnable?.let { handler.removeCallbacks(it) }
        Timber.i("Stopped NightscoutSyncWorker")
    }

    /**
     * Trigger an immediate sync (does not affect periodic schedule)
     */
    fun syncNow() {
        Timber.i("Triggering immediate Nightscout sync")
        scope.launch {
            performSync()
        }
    }

    /**
     * Schedule the next sync
     */
    private fun scheduleNextSync(intervalMinutes: Int) {
        if (!isRunning) {
            return
        }

        val runnable = Runnable {
            scope.launch {
                performSync()
                scheduleNextSync(intervalMinutes)
            }
        }

        syncRunnable = runnable
        handler.postDelayed(runnable, intervalMinutes * 60 * 1000L)
    }

    /**
     * Perform a sync operation
     */
    private suspend fun performSync() {
        try {
            val config = NightscoutSyncConfig.load(prefs)

            if (!config.enabled) {
                Timber.d("Nightscout sync is disabled, skipping")
                return
            }

            if (!config.isValid()) {
                Timber.e("Nightscout configuration is invalid, skipping sync")
                NightscoutSyncStatusStore.recordFailure(prefs, "Nightscout configuration is invalid")
                return
            }

            val normalizedUrl = normalizeNightscoutUrl(config.nightscoutUrl)
            if (normalizedUrl == null) {
                Timber.e("Nightscout URL is invalid, skipping sync")
                NightscoutSyncStatusStore.recordFailure(prefs, "Nightscout URL is invalid")
                return
            }

            val connectivityCheck = checkNightscoutConnection(normalizedUrl, config.apiSecret)
            if (connectivityCheck.isFailure) {
                val message = connectivityCheck.exceptionOrNull()?.message ?: "Connection test failed"
                Timber.e("Nightscout connection check failed: $message")
                NightscoutSyncStatusStore.recordFailure(prefs, "Connection failed: $message")
                return
            }

            // Create coordinator with dependencies
            val historyLogRepo = HistoryLogRepo(
                HistoryLogDatabase.getDatabase(context).historyLogDao()
            )
            val nightscoutClient = NightscoutClient(
                normalizedUrl,
                config.apiSecret
            )
            val nsDb = NightscoutSyncStateDatabase.getDatabase(context)
            val syncStateDao = nsDb.nightscoutSyncStateDao()
            val processorStateDao = nsDb.nightscoutProcessorStateDao()

            val configWithModel = config.copy(
                pumpModelName = Prefs(context).pumpModelName() ?: "Tandem Pump"
            )

            val coordinator = NightscoutSyncCoordinator(
                historyLogRepo,
                nightscoutClient,
                syncStateDao,
                processorStateDao,
                configWithModel,
                pumpSid
            )

            // Perform sync
            when (val result = coordinator.syncAll()) {
                is SyncResult.Success -> {
                    NightscoutSyncStatusStore.recordSuccess(prefs)
                    Timber.i(
                        "Nightscout sync completed: " +
                        "processed ${result.processedCount}, " +
                        "uploaded ${result.uploadedCount}, " +
                        "seqId ${result.seqIdRange.first}..${result.seqIdRange.second}"
                    )
                }
                is SyncResult.NoData -> {
                    NightscoutSyncStatusStore.recordSuccess(prefs)
                    Timber.d("No new data to sync")
                }
                is SyncResult.Disabled -> {
                    Timber.d("Nightscout sync is disabled")
                }
                is SyncResult.InvalidConfig -> {
                    NightscoutSyncStatusStore.recordFailure(prefs, "Nightscout configuration is invalid")
                    Timber.e("Nightscout configuration is invalid")
                }
            }
        } catch (e: Exception) {
            NightscoutSyncStatusStore.recordFailure(
                prefs,
                e.message ?: "Unexpected Nightscout sync error"
            )
            Timber.e(e, "Error during Nightscout sync")
        }
    }

    private fun checkNightscoutConnection(baseUrl: String, apiSecret: String): Result<Unit> {
        return runCatching {
            val statusUrl = URL("$baseUrl/api/v1/verifyauth")
            val connection = (statusUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty(NightscoutApiSecretHeader, hashNightscoutApiSecret(apiSecret))
                connectTimeout = 10000
                readTimeout = 10000
                doInput = true
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = connection.errorStream?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                    throw IllegalStateException("HTTP $responseCode $errorBody".trim())
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private var instance: NightscoutSyncWorker? = null

        /**
         * Get or create singleton instance
         */
        fun getInstance(context: Context, prefs: SharedPreferences, pumpSid: Int): NightscoutSyncWorker {
            return instance ?: synchronized(this) {
                instance ?: NightscoutSyncWorker(
                    context.applicationContext,
                    prefs,
                    pumpSid
                ).also { instance = it }
            }
        }

        /**
         * Start Nightscout sync worker (if enabled)
         */
        fun startIfEnabled(context: Context, prefs: SharedPreferences, pumpSid: Int) {
            val config = NightscoutSyncConfig.load(prefs)
            if (config.enabled) {
                getInstance(context, prefs, pumpSid).start()
            }
        }

        /**
         * Stop Nightscout sync worker
         */
        fun stopIfRunning() {
            instance?.stop()
        }
    }
}
