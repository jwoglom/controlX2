package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.ZoneId

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
    val dateString: String,  // ISO 8601 format

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
            val epochMilli = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return NightscoutEntry(
                sgv = sgv,
                direction = direction,
                date = epochMilli,
                dateString = timestamp.toString(),
                identifier = seqId.toString()
            )
        }
    }
}
