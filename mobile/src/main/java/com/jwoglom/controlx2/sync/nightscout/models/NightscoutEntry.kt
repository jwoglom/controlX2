package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import com.jwoglom.controlx2.sync.nightscout.NightscoutTimestampPolicy
import java.time.LocalDateTime

/**
 * Nightscout CGM entry (glucose reading)
 * API endpoint: /api/v1/entries
 */
data class NightscoutEntry(
    @SerializedName("type")
    val type: String = "sgv",  // "sgv" = sensor glucose value

    @SerializedName("sgv")
    val sgv: Int,  // Glucose value in mg/dL

    @SerializedName("direction")
    val direction: String? = null,  // Trend: "Flat", "FortyFiveUp", etc.

    @SerializedName("date")
    val date: Long,  // Unix timestamp in milliseconds

    @SerializedName("dateString")
    val dateString: String,  // RFC3339/ISO instant in UTC (Z)

    @SerializedName("device")
    val device: String = "ControlX2",

    @SerializedName("_id")
    val id: String? = null,  // Nightscout-assigned ID (on read)

    @SerializedName("identifier")
    val identifier: String? = null,  // Our unique identifier (seqId)
) {
    companion object {
        fun fromTimestamp(
            timestamp: LocalDateTime,
            sgv: Int,
            direction: String? = null,
            seqId: Long
        ): NightscoutEntry {
            // Keep date + dateString from the same instant for Nightscout consistency.
            val nightscoutTimestamp = NightscoutTimestampPolicy.fromPumpTime(timestamp, "entry")
            return NightscoutEntry(
                sgv = sgv,
                direction = direction,
                date = nightscoutTimestamp.epochMillis,
                dateString = nightscoutTimestamp.isoInstant,
                identifier = seqId.toString()
            )
        }
    }
}
