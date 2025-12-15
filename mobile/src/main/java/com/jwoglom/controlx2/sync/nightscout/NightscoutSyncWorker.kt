package com.jwoglom.controlx2.sync.nightscout

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDatabase
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

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
                return
            }

            // Create coordinator with dependencies
            val historyLogRepo = HistoryLogRepo(
                HistoryLogDatabase.getDatabase(context).historyLogDao()
            )
            val nightscoutClient = NightscoutClient(
                config.getSanitizedUrl(),
                config.apiSecret
            )
            val syncStateDb = NightscoutSyncStateDatabase.getDatabase(context)
            val syncStateDao = syncStateDb.nightscoutSyncStateDao()
            val processorStateDao = syncStateDb.nightscoutProcessorStateDao()

            val coordinator = NightscoutSyncCoordinator(
                historyLogRepo,
                nightscoutClient,
                syncStateDao,
                processorStateDao,
                config,
                pumpSid,
                context
            )

            // Perform sync
            when (val result = coordinator.syncAll()) {
                is SyncResult.Success -> {
                    Timber.i(
                        "Nightscout sync completed: " +
                        "processed ${result.processedCount}, " +
                        "uploaded ${result.uploadedCount}, " +
                        "seqId ${result.seqIdRange.first}..${result.seqIdRange.second}"
                    )
                }
                is SyncResult.NoData -> {
                    Timber.d("No new data to sync")
                }
                is SyncResult.Disabled -> {
                    Timber.d("Nightscout sync is disabled")
                }
                is SyncResult.InvalidConfig -> {
                    Timber.e("Nightscout configuration is invalid")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during Nightscout sync")
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
