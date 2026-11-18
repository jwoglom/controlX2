package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import android.content.Intent
import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

interface XdripBroadcaster {
    fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int? = null): Boolean
    fun sendDeviceStatus(deviceStatusJsonString: String, minimumIntervalSeconds: Int? = null): Boolean
    fun sendTreatments(
        treatmentsJsonString: String,
        minimumIntervalSeconds: Int? = null,
        alsoSendNewFood: Boolean = true
    ): Boolean

    fun sendExternalStatusline(statusline: String, minimumIntervalSeconds: Int? = null): Boolean
}

/**
 * Sends xDrip-compatible broadcast intents for glucose, treatments, and status updates.
 *
 * Glucose (SGV) is sent through two routes:
 *  - NSClient-style broadcast (info.nightscout.client.NEW_SGV), as before.
 *  - NS_EMULATOR broadcast (com.eveningoutpost.dexdrip.NS_EMULATOR) with extras
 *    "collection" and "data", using a minimal JSON (type, sgv, date, direction).
 */
class XdripBroadcastSender(
    private val sendBroadcastFn: (action: String, extraKey: String, payload: String) -> Unit,
    private val nowMillisFn: () -> Long = { System.currentTimeMillis() },
    private val sendNsEmulatorFn: (collection: String, data: String) -> Unit = { _, _ -> }
) : XdripBroadcaster {
    constructor(context: Context) : this(
        sendBroadcastFn = { action, extraKey, payload ->
            val intent = Intent(action).apply {
                `package` = "com.eveningoutpost.dexdrip"
                putExtra(extraKey, payload)
            }
            context.sendBroadcast(intent)
        },
        sendNsEmulatorFn = { collection, data ->
            val intent = Intent(ACTION_NS_EMULATOR).apply {
                `package` = "com.eveningoutpost.dexdrip"
                putExtra("collection", collection)
                putExtra("data", data)
            }
            context.sendBroadcast(intent)
        }
    )

    companion object {
        const val ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV"
        const val ACTION_NEW_DEVICE_STATUS = "info.nightscout.client.NEW_DEVICESTATUS"
        const val ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT"
        const val ACTION_NEW_FOOD = "info.nightscout.client.NEW_FOOD"
        const val ACTION_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline"
        const val ACTION_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"

        /** true = se envía la flecha calculada por ControlX2 (con las horas reales); false = "NONE". */
        private const val NS_EMULATOR_SEND_DIRECTION = true

        private const val EXTRA_SGVS = XdripSgvPayload.EXTRA_KEY
        private const val EXTRA_DEVICESTATUS = XdripDeviceStatusPayload.EXTRA_KEY
        private const val EXTRA_TREATMENTS = XdripTreatmentPayload.EXTRA_KEY
        private const val EXTRA_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline"
    }

    private data class LastSentState(
        var payload: String,
        var sentAtMillis: Long
    )

    private val cache: MutableMap<String, LastSentState> = mutableMapOf()

    override fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int?): Boolean {
        val sent = sendWithCache(
            cacheKey = "sgv",
            action = ACTION_NEW_SGV,
            extraKey = EXTRA_SGVS,
            payload = sgvsJsonArrayString,
            minimumIntervalSeconds = minimumIntervalSeconds
        )

        if (sent) {
            val nsPayload = toNsEmulatorPayload(sgvsJsonArrayString)
            Timber.i("NS_EMULATOR in=%s out=%s", sgvsJsonArrayString, nsPayload)
            if (nsPayload != "[]") {
                // Mismo payload (mismo valor y misma hora de lectura) ya lo filtra sendWithCache,
                // asi que aqui solo llegan lecturas nuevas.
                sendNsEmulatorFn("entries", nsPayload)
                Timber.i("Sent xDrip NS_EMULATOR entries broadcast")
            }
        }

        return sent
    }

    /**
     * Rebuilds the SGV array as the minimal JSON that xDrip+'s NS emulator receiver accepts:
     * [{"type":"sgv","sgv":<mg/dL int>,"date":<epoch ms>,"direction":"<name>"}]
     */
    private fun toNsEmulatorPayload(json: String): String {
        val out = JSONArray()
        try {
            val src = JSONArray(json)
            for (i in 0 until src.length()) {
                val o = src.getJSONObject(i)
                val sgv = if (o.has("sgv")) o.optDouble("sgv", Double.NaN) else o.optDouble("mgdl", Double.NaN)
                var date = if (o.has("date")) o.optLong("date", 0L) else o.optLong("mills", 0L)
                if (date in 1..99_999_999_999L) date *= 1000 // seconds -> milliseconds
                if (sgv.isNaN() || sgv <= 0 || date <= 0L) {
                    Timber.w("NS_EMULATOR: entrada descartada: %s", o)
                    continue
                }
                out.put(JSONObject().apply {
                    put("type", "sgv")
                    put("sgv", sgv.toInt())
                    put("date", date)
                    // xDrip+ calcula la tendencia con sus propias lecturas. Se envía "NONE"
                    // para que no use la flecha de ControlX2 (que sale siempre DoubleDown).
                    put(
                        "direction",
                        if (NS_EMULATOR_SEND_DIRECTION) o.optString("direction", "").ifBlank { "NONE" } else "NONE"
                    )
                })
            }
        } catch (e: Exception) {
            Timber.w(e, "NS_EMULATOR: JSON no válido: %s", json)
        }
        return out.toString()
    }

    override fun sendDeviceStatus(deviceStatusJsonString: String, minimumIntervalSeconds: Int?): Boolean {
        return sendWithCache(
            cacheKey = "device_status",
            action = ACTION_NEW_DEVICE_STATUS,
            extraKey = EXTRA_DEVICESTATUS,
            payload = deviceStatusJsonString,
            minimumIntervalSeconds = minimumIntervalSeconds
        )
    }

    override fun sendTreatments(
        treatmentsJsonString: String,
        minimumIntervalSeconds: Int?,
        alsoSendNewFood: Boolean
    ): Boolean {
        Timber.i("treatments=%s", treatmentsJsonString)
        val sentTreatment = sendWithCache(
            // Cache aparte para basal y bolus: antes un basal intercalado borraba el ultimo bolus
            // de la cache y el mismo bolus se reenviaba varias veces.
            cacheKey = when {
                treatmentsJsonString.contains("\"Temp Basal\"") -> "treatments_basal"
                treatmentsJsonString.contains("\"Carb Correction\"") -> "treatments_carbs"
                else -> "treatments_bolus"
            },
            action = ACTION_NEW_TREATMENT,
            extraKey = EXTRA_TREATMENTS,
            payload = treatmentsJsonString,
            minimumIntervalSeconds = minimumIntervalSeconds
        )

        if (alsoSendNewFood && sentTreatment) {
            sendBroadcast(ACTION_NEW_FOOD, EXTRA_TREATMENTS, treatmentsJsonString)
        }

        return sentTreatment
    }

    override fun sendExternalStatusline(statusline: String, minimumIntervalSeconds: Int?): Boolean {
        Timber.i("statusline=%s", statusline)
        return sendWithCache(
            cacheKey = "statusline",
            action = ACTION_EXTERNAL_STATUSLINE,
            extraKey = EXTRA_EXTERNAL_STATUSLINE,
            payload = statusline,
            minimumIntervalSeconds = minimumIntervalSeconds
        )
    }

    private fun sendWithCache(
        cacheKey: String,
        action: String,
        extraKey: String,
        payload: String,
        minimumIntervalSeconds: Int?
    ): Boolean {
        val now = nowMillisFn()
        val minIntervalMillis = (minimumIntervalSeconds ?: 0).coerceAtLeast(0) * 1000L
        val previous = cache[cacheKey]

        if (previous != null && previous.payload == payload) {
            Timber.d("xDrip broadcast suppressed (%s): duplicate payload", cacheKey)
            return false
        }

        if (previous != null && minIntervalMillis > 0 && (now - previous.sentAtMillis) < minIntervalMillis) {
            Timber.d("xDrip broadcast suppressed (%s): under min interval", cacheKey)
            return false
        }

        sendBroadcast(action, extraKey, payload)
        cache[cacheKey] = LastSentState(payload = payload, sentAtMillis = now)
        return true
    }

    private fun sendBroadcast(action: String, extraKey: String, payload: String) {
        sendBroadcastFn(action, extraKey, payload)
        Timber.i("Sent xDrip broadcast action=%s extra=%s", action, extraKey)
    }
}
