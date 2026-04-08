package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import com.jwoglom.controlx2.sync.nightscout.NightscoutTimestampPolicy
import java.time.LocalDateTime

/**
 * Nightscout treatment (bolus, basal, carbs, etc.)
 * API endpoint: /api/v1/treatments
 */
data class NightscoutTreatment(
    @SerializedName("eventType")
    val eventType: String,  // "Bolus", "Temp Basal", "Site Change", etc.

    @SerializedName("created_at")
    val createdAt: String,  // RFC3339/ISO instant in UTC (Z)

    @SerializedName("timestamp")
    val timestamp: Long,  // Unix timestamp in milliseconds

    @SerializedName("utcOffset")
    val utcOffset: Int,  // Minutes offset for pump-local wall time (e.g. -420)

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
    val device: String = "ControlX2",

    // Combo/extended bolus fields (per Nightscout "Combo Bolus" eventType)
    @SerializedName("enteredinsulin")
    val enteredInsulin: Double? = null,  // Total insulin (immediate + extended)

    @SerializedName("splitNow")
    val splitNow: Int? = null,  // Percentage delivered immediately (0-100)

    @SerializedName("splitExt")
    val splitExt: Int? = null,  // Percentage delivered over extended period (0-100)

    @SerializedName("relative")
    val relative: Double? = null,  // Extended portion rate in U/hr

    @SerializedName("percent")
    val percent: Int? = null  // Temp basal percent change from profile (-50 = half, 0 = normal, 100 = double)
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
            notes: String? = null,
            enteredInsulin: Double? = null,
            splitNow: Int? = null,
            splitExt: Int? = null,
            relative: Double? = null,
            percent: Int? = null
        ): NightscoutTreatment {
            // Nightscout expects timezone-aware timestamps and matching epoch/offset from one instant.
            val nightscoutTimestamp = NightscoutTimestampPolicy.fromPumpTime(timestamp, "treatment")
            return NightscoutTreatment(
                eventType = eventType,
                createdAt = nightscoutTimestamp.isoInstant,
                timestamp = nightscoutTimestamp.epochMillis,
                utcOffset = nightscoutTimestamp.utcOffsetMinutes,
                insulin = insulin,
                carbs = carbs,
                duration = duration,
                rate = rate,
                absolute = absolute,
                reason = reason,
                notes = notes,
                pumpId = seqId.toString(),
                enteredInsulin = enteredInsulin,
                splitNow = splitNow,
                splitExt = splitExt,
                relative = relative,
                percent = percent
            )
        }
    }
}
