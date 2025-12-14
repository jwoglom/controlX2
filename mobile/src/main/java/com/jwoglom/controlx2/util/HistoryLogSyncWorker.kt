package com.jwoglom.controlx2.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Lightweight periodic worker that triggers HistoryLogStatus requests while the pump is connected.
 */
class HistoryLogSyncWorker(
    private val intervalMinutes: Int = 5,
    private val requestSync: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncRunnable: Runnable? = null
    private var isRunning = false

    fun start() {
        if (isRunning) {
            Timber.v("HistoryLogSyncWorker already running")
            return
        }
        isRunning = true
        scheduleNextSync()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        syncRunnable?.let { handler.removeCallbacks(it) }
        syncRunnable = null
    }

    fun triggerImmediateSync() {
        scope.launch {
            try {
                requestSync()
            } catch (t: Throwable) {
                Timber.w(t, "HistoryLogSyncWorker immediate sync failed")
            }
        }
    }

    private fun scheduleNextSync() {
        if (!isRunning) return
        val runnable = Runnable {
            scope.launch {
                try {
                    requestSync()
                } catch (t: Throwable) {
                    Timber.w(t, "HistoryLogSyncWorker periodic sync failed")
                } finally {
                    scheduleNextSync()
                }
            }
        }
        syncRunnable = runnable
        handler.postDelayed(runnable, intervalMinutes * 60 * 1000L)
    }
}
