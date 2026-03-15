package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import android.content.Intent
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
 */
class XdripBroadcastSender(
    private val sendBroadcastFn: (action: String, extraKey: String, payload: String) -> Unit,
    private val nowMillisFn: () -> Long = { System.currentTimeMillis() }
) : XdripBroadcaster {
    constructor(context: Context) : this(
        sendBroadcastFn = { action, extraKey, payload ->
            val intent = Intent(action).apply {
                `package` = "com.eveningoutpost.dexdrip"
                putExtra(extraKey, payload)
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

        private const val EXTRA_SGVS = "sgvs"
        private const val EXTRA_DEVICESTATUS = "devicestatus"
        private const val EXTRA_TREATMENTS = "treatments"
        private const val EXTRA_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline"
    }

    private data class LastSentState(
        var payload: String,
        var sentAtMillis: Long
    )

    private val cache: MutableMap<String, LastSentState> = mutableMapOf()

    override fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int?): Boolean {
        return sendWithCache(
            cacheKey = "sgv",
            action = ACTION_NEW_SGV,
            extraKey = EXTRA_SGVS,
            payload = sgvsJsonArrayString,
            minimumIntervalSeconds = minimumIntervalSeconds
        )
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
        val sentTreatment = sendWithCache(
            cacheKey = "treatments",
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
