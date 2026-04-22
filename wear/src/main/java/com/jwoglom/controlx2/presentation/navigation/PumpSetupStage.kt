package com.jwoglom.controlx2.presentation.navigation

/**
 * Watch-local pairing-flow states. A subset of mobile's
 * `com.jwoglom.controlx2.presentation.screens.PumpSetupStage` containing only
 * the values the watch UI needs. TODO(phase6): share the enum across platforms.
 *
 * The string names of these values are matched against
 * [com.jwoglom.controlx2.pump.pairing.PairingCodeEntry.WAITING_PUMP_FINDER_CLEANUP]
 * and [com.jwoglom.controlx2.pump.pairing.PairingCodeEntry.PUMPX2_WAITING_FOR_PAIRING_CODE]
 * when dispatching post-set-pairing-code actions. Keep the names aligned.
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
