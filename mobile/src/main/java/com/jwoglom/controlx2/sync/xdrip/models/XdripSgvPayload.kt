package com.jwoglom.controlx2.sync.xdrip.models

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class XdripSgvPayload(
    val sgv: Int,
    val date: Long,
    val dateString: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("sgv", sgv)
            put("date", date)
            put("dateString", dateString)
        }
    }

    fun toJsonArrayString(): String = JSONArray().put(toJsonObject()).toString()

    companion object {
        const val EXTRA_KEY = "sgvs"

        fun fromResponse(response: CurrentEGVGuiDataResponse, receivedAt: Instant): XdripSgvPayload {
            return XdripSgvPayload(
                sgv = response.cgmReading,
                date = receivedAt.toEpochMilli(),
                dateString = receivedAt.toString()
            )
        }

        fun fromValue(mgdl: Int, receivedAt: Instant): XdripSgvPayload {
            return XdripSgvPayload(
                sgv = mgdl,
                date = receivedAt.toEpochMilli(),
                dateString = receivedAt.toString()
            )
        }
    }
}
