package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nightscout treatment (bolus, basal, carbs, etc.)
 * API endpoint: /api/v1/treatments
 */
data class NightscoutTreatment(
    @SerializedName("eventType")
    val eventType: String,  // "Bolus", "Temp Basal", "Site Change", etc.

    @SerializedName("created_at")
    val createdAt: String,  // ISO 8601 format

    @SerializedName("timestamp")
    val timestamp: Long,  // Unix timestamp in milliseconds

    @SerializedName("insulin")
    val insulin: Double? = null,  // Insulin in units

    @SerializedName("carbs")
    val carbs: Double? = null,  // Carbs in grams

    @SerializedName("duration")
    val duration: Int? = null,  // Duration in minutes (for temp basal)

    @SerializedName("rate")
    val rate: Double? = null,  // Basal rate in U/hr

    @SerializedName("absolute")
    val absolute: Double? = null,  // Absolute basal rate in U/hr

    @SerializedName("reason")
    val reason: String? = null,  // Reason for treatment

    @SerializedName("notes")
    val notes: String? = null,  // Additional notes

    @SerializedName("enteredBy")
    val enteredBy: String = "ControlX2",

    @SerializedName("_id")
    val id: String? = null,  // Nightscout-assigned ID (on read)

    @SerializedName("pumpId")
    val pumpId: String? = null,  // Our unique identifier (seqId) - used for deduplication

    @SerializedName("device")
    val device: String = "ControlX2"
) {
    companion object {
        fun fromTimestamp(
            eventType: String,
            timestamp: LocalDateTime,
            seqId: Long,
            insulin: Double? = null,
            carbs: Double? = null,
            duration: Int? = null,
            rate: Double? = null,
            absolute: Double? = null,
            reason: String? = null,
            notes: String? = null
        ): NightscoutTreatment {
            val epochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return NightscoutTreatment(
                eventType = eventType,
                createdAt = timestamp.toString(),
                timestamp = epochMilli,
                insulin = insulin,
                carbs = carbs,
                duration = duration,
                rate = rate,
                absolute = absolute,
                reason = reason,
                notes = notes,
                pumpId = seqId.toString()
            )
        }
    }
}
