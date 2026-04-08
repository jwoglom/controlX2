package com.jwoglom.controlx2.sync.xdrip.models

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class XdripSgvPayload(
    val mgdl: Int,
    val mills: Long,
    val direction: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("mgdl", mgdl)
            put("mills", mills)
            put("direction", direction)
        }
    }

    fun toJsonArrayString(): String = JSONArray().put(toJsonObject()).toString()

    companion object {
        const val EXTRA_KEY = "sgvs"

        /**
         * Map a numeric trend rate (mg/dL per minute) to an xDrip-compatible direction string.
         * Thresholds follow the standard Dexcom CGM trend arrow ranges.
         */
        fun directionFromTrendRate(trendRate: Int): String {
            return when {
                trendRate <= -3 -> "DoubleDown"
                trendRate <= -2 -> "SingleDown"
                trendRate <= -1 -> "FortyFiveDown"
                trendRate < 1 -> "Flat"
                trendRate < 2 -> "FortyFiveUp"
                trendRate < 3 -> "SingleUp"
                else -> "DoubleUp"
            }
        }

        fun fromValue(mgdl: Int, trendRate: Int, receivedAt: Instant): XdripSgvPayload {
            return XdripSgvPayload(
                mgdl = mgdl,
                mills = receivedAt.toEpochMilli(),
                direction = directionFromTrendRate(trendRate)
            )
        }
    }
}
