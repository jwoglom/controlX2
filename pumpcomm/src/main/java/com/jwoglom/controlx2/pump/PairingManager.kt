package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType

/**
 * Dispatches pump-pairing commands onto the active [PumpCommHandler] looper. Shared
 * across mobile [com.jwoglom.controlx2.CommService] and wear `WearPumpCommService` so
 * the two send-paths stay in lockstep. The handler is read through a provider lambda
 * because both hosting services reassign their `pumpCommHandler` field when the user
 * exits the pump finder, so a captured reference would go stale.
 */
class PairingManager(
    private val pumpCommHandlerProvider: () -> PumpCommHandler?,
) {
    fun sendInitPumpComm(pairingCodeType: PairingCodeType, filterToBluetoothMac: String) {
        val handler = pumpCommHandlerProvider() ?: return
        handler.obtainMessage().also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_COMM.ordinal
            msg.obj = if (filterToBluetoothMac.isNotEmpty()) {
                "${pairingCodeType.label} $filterToBluetoothMac"
            } else {
                pairingCodeType.label
            }
            handler.sendMessage(msg)
        }
    }

    fun sendPumpPairingMessage() {
        val handler = pumpCommHandlerProvider() ?: return
        handler.obtainMessage().also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal
            handler.sendMessage(msg)
        }
    }

    companion object {
        fun resolveCodeType(label: String?): PairingCodeType =
            if (!label.isNullOrEmpty()) PairingCodeType.fromLabel(label)
            else PairingCodeType.SHORT_6CHAR
    }
}
