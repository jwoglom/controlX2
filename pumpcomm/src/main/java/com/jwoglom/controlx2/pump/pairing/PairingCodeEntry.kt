package com.jwoglom.controlx2.pump.pairing

import android.content.Context
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.pumpx2.pump.PumpState

/**
 * Shared post-`TO_SERVER_SET_PAIRING_CODE` dispatch logic used by both the
 * mobile and watch MainActivity handlers.
 *
 * The caller decides which of [applyForInitialPumpComm] or [applyForRePair]
 * to invoke based on their platform's `PumpSetupStage`-equivalent. This
 * avoids the earlier stringly-typed dispatch (where an unknown stage name
 * silently logged and no message was sent) in favour of two explicit
 * entry points whose intent is clear from the call site.
 */
object PairingCodeEntry {

    private const val INIT_COMM_PAYLOAD = "init_comm"

    /**
     * First-time pairing: fires `/to-server/stop-pump-finder` with the
     * `"init_comm"` payload so the comm service swaps from PumpFinder to the
     * real PumpComm handler. Caller should have flipped the
     * pump-finder-enabled pref to `false` prior to invoking this.
     *
     * Use when the current stage is equivalent to `WAITING_PUMP_FINDER_CLEANUP`.
     */
    fun applyForInitialPumpComm(
        context: Context,
        code: String,
        sendMessage: (String, ByteArray) -> Unit,
    ) {
        PumpState.setPairingCode(context, code)
        sendMessage(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, INIT_COMM_PAYLOAD.toByteArray())
    }

    /**
     * Retry after the pump rejected a prior code: fires `/to-pump/pair` so the
     * service re-sends the new pairing code to the pump.
     *
     * Use when the current stage is equivalent to `PUMPX2_WAITING_FOR_PAIRING_CODE`.
     */
    fun applyForRePair(
        context: Context,
        code: String,
        sendMessage: (String, ByteArray) -> Unit,
    ) {
        PumpState.setPairingCode(context, code)
        sendMessage(MessagePaths.TO_PUMP_PAIR, ByteArray(0))
    }
}
