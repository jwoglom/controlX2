package com.jwoglom.controlx2.presentation.navigation

/**
 * Watch-local pairing-flow states. A subset of mobile's
 * `com.jwoglom.controlx2.presentation.screens.PumpSetupStage` containing only
 * the values the watch UI needs. TODO(phase6): share the enum across platforms.
 *
 * The two post-set-pairing-code values here are dispatched via
 * [com.jwoglom.controlx2.pump.pairing.PairingCodeEntry.applyForInitialPumpComm]
 * (for `WAITING_PUMP_FINDER_CLEANUP`) and
 * [com.jwoglom.controlx2.pump.pairing.PairingCodeEntry.applyForRePair] (for
 * `PUMPX2_WAITING_FOR_PAIRING_CODE`). See `MainActivity.submitPairingCode`.
 */
enum class PumpSetupStage {
    WAITING_PUMP_FINDER_INIT,
    PUMP_FINDER_SEARCHING_FOR_PUMPS,
    PUMP_FINDER_SELECT_PUMP,
    WAITING_PUMP_FINDER_CLEANUP,
    PUMPX2_SEARCHING_FOR_PUMP,
    PUMPX2_WAITING_FOR_PAIRING_CODE,
    PUMPX2_SENDING_PAIRING_CODE,
    PUMPX2_INVALID_PAIRING_CODE,
    PUMPX2_PUMP_CONNECTED,
    PAIRING_UNSUPPORTED,
}
