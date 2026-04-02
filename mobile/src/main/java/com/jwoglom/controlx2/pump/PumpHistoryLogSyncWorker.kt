package com.jwoglom.controlx2.pump

/**
 * Interface for periodic history log sync, abstracting the concrete HistoryLogSyncWorker
 * so PumpCommHandler doesn't depend on mobile-app utility classes.
 */
interface PumpHistoryLogSyncWorker {
    fun start()
    fun stop()
    fun triggerImmediateSync()
}
