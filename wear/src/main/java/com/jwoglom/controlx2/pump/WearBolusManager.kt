package com.jwoglom.controlx2.pump

import android.content.Context
import android.content.SharedPreferences
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.shared.Hex
import timber.log.Timber
import java.time.Instant

/**
 * Watch-side bolus manager. Handles bolus request confirmation when the watch
 * is the pump-host. Simplified compared to mobile BolusManager: no Android
 * notification-based confirmation flow. Bolus requests from the phone client
 * are forwarded to the pump directly (the phone handles its own confirmation UI).
 */
class WearBolusManager(
    private val context: Context,
    private val sendMessage: (String, ByteArray) -> Unit,
) {
    enum class BolusRequestSource(val id: String) {
        PHONE("phone"),
        WEAR("wear")
    }

    fun confirmBolusRequest(request: InitiateBolusRequest, source: BolusRequestSource) {
        val units = twoDecimalPlaces(InsulinUnit.from1000To1(request.totalVolume))
        Timber.i("WearBolusManager confirmBolusRequest $units: $request source=$source")

        // Store bolus state in prefs
        prefs()?.edit()
            ?.putString("initiateBolusRequest", Hex.encodeHexString(PumpMessageSerializer.toBytes(request)))
            ?.putString("initiateBolusSecret", Hex.encodeHexString(Bytes.getSecureRandom10Bytes()))
            ?.putString("initiateBolusSource", source.id)
            ?.putLong("initiateBolusTime", Instant.now().toEpochMilli())
            ?.commit()

        // Send bolus confirmation dialog to client (phone) for user confirmation
        sendMessage(
            MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG,
            "${Hex.encodeHexString(PumpMessageSerializer.toBytes(request))}|${source.id}|0".toByteArray()
        )
    }

    fun resetBolusPrefs() {
        prefs()?.edit()
            ?.remove("initiateBolusRequest")
            ?.remove("initiateBolusTime")
            ?.apply()
    }

    private fun prefs(): SharedPreferences? {
        return context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
    }
}
