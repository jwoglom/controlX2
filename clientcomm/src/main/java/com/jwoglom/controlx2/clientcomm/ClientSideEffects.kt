package com.jwoglom.controlx2.clientcomm

/**
 * Callbacks for platform-specific side effects triggered by client message handling.
 * The wear implementation updates notifications and complications.
 * A future phone implementation (Phase 4) would update its own UI.
 */
interface ClientSideEffects {
    fun onConnectionStateChanged(state: ClientConnectionState)
    fun onPumpDataUpdated(key: String)
    fun onGlucoseUnitUpdated()
    fun onOpenActivityRequested()
    fun onBolusBlockedSignature()
    fun onBolusNotEnabled()
}
