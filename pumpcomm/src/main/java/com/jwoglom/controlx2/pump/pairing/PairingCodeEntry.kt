package com.jwoglom.controlx2.pump.pairing

import android.content.Context
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.pumpx2.pump.PumpState
import timber.log.Timber

/**
 * Shared post-`TO_SERVER_SET_PAIRING_CODE` dispatch logic used by both the
 * mobile and watch MainActivity handlers.
 *
 * Caller is expected to already hold the current [PumpSetupStage]-equivalent
 * value as a string (enum `.name`) — passing the string avoids a cross-module
 * enum dependency since mobile and watch maintain independent enums.
 */
object PairingCodeEntry {
    /**
     * Persists the pairing code via [PumpState.setPairingCode] and dispatches
     * the next step in the pairing flow based on [currentStageName].
     *
     * - `WAITING_PUMP_FINDER_CLEANUP`: fires `/to-server/stop-pump-finder` with
     *   `"init_comm"` payload so the comm service swaps from PumpFinder to the
     *   real PumpComm handler. The caller should also have flipped the
     *   pump-finder-enabled pref to `false` prior to invoking this.
     * - `PUMPX2_WAITING_FOR_PAIRING_CODE`: fires `/to-pump/pair` so the service
     *   re-sends the pairing code to the pump (e.g. after an invalid code).
     */
    fun apply(
        context: Context,
        code: String,
        currentStageName: String?,
        sendMessage: (String, ByteArray) -> Unit,
    ) {
        PumpState.setPairingCode(context, code)
        when (currentStageName) {
            WAITING_PUMP_FINDER_CLEANUP -> {
                sendMessage(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, INIT_COMM_PAYLOAD.toByteArray())
            }
            PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                sendMessage(MessagePaths.TO_PUMP_PAIR, ByteArray(0))
            }
            else -> {
                Timber.w("PairingCodeEntry.apply: ignoring for stage=%s", currentStageName)
            }
        }
    }

    const val WAITING_PUMP_FINDER_CLEANUP = "WAITING_PUMP_FINDER_CLEANUP"
    const val PUMPX2_WAITING_FOR_PAIRING_CODE = "PUMPX2_WAITING_FOR_PAIRING_CODE"
    private const val INIT_COMM_PAYLOAD = "init_comm"
}
