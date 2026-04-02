package com.jwoglom.controlx2.sync.xdrip.models

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class XdripTreatmentPayload(
    val eventType: String,
    val createdAt: String,
    val mills: Long,
    val insulin: Double? = null,
    val duration: Int? = null,
    val rate: Double? = null,
    val absolute: Double? = null,
    val notes: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("eventType", eventType)
            put("created_at", createdAt)
            put("mills", mills)
            insulin?.let { put("insulin", it) }
            duration?.let { put("duration", it) }
            rate?.let { put("rate", it) }
            absolute?.let { put("absolute", it) }
            put("notes", notes)
        }
    }

    fun toJsonArrayString(): String = JSONArray().put(toJsonObject()).toString()

    companion object {
        const val EXTRA_KEY = "treatments"

        fun fromInitiateBolusResponse(response: InitiateBolusResponse, receivedAt: Instant): XdripTreatmentPayload {
            return XdripTreatmentPayload(
                eventType = "Bolus",
                createdAt = receivedAt.toString(),
                mills = receivedAt.toEpochMilli(),
                notes = "ControlX2 bolus initiated bolusId=${response.bolusId} status=${response.statusType}"
            )
        }

        fun fromCurrentBolusStatusResponse(response: CurrentBolusStatusResponse): XdripTreatmentPayload {
            return XdripTreatmentPayload(
                eventType = "Bolus",
                createdAt = response.timestampInstant.toString(),
                mills = response.timestampInstant.toEpochMilli(),
                insulin = InsulinUnit.from1000To1(response.requestedVolume),
                notes = "ControlX2 bolus status bolusId=${response.bolusId} status=${response.status}"
            )
        }

        fun fromStatus(
            bolusId: Int,
            requestedVolumeMilli: Long,
            status: String,
            timestamp: Instant
        ): XdripTreatmentPayload {
            return XdripTreatmentPayload(
                eventType = "Bolus",
                createdAt = timestamp.toString(),
                mills = timestamp.toEpochMilli(),
                insulin = InsulinUnit.from1000To1(requestedVolumeMilli),
                notes = "ControlX2 bolus status bolusId=$bolusId status=$status"
            )
        }

        fun forBasalRate(
            unitsPerHour: Double,
            durationMinutes: Int,
            timestamp: Instant
        ): XdripTreatmentPayload {
            return XdripTreatmentPayload(
                eventType = "Temp Basal",
                createdAt = timestamp.toString(),
                mills = timestamp.toEpochMilli(),
                duration = durationMinutes,
                rate = unitsPerHour,
                absolute = unitsPerHour,
                notes = "ControlX2 basal rate ${unitsPerHour}U/h"
            )
        }
    }
}
