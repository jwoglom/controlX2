package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nightscout device status
 * API endpoint: /api/v1/devicestatus
 */
data class NightscoutDeviceStatus(
    @SerializedName("created_at")
    val createdAt: String,  // ISO 8601 format

    @SerializedName("device")
    val device: String = "ControlX2",

    @SerializedName("pump")
    val pump: PumpStatus? = null,

    @SerializedName("uploaderBattery")
    val uploaderBattery: Int? = null,  // Phone battery percentage

    @SerializedName("_id")
    val id: String? = null  // Nightscout-assigned ID (on read)
)

data class PumpStatus(
    @SerializedName("battery")
    val battery: Battery? = null,

    @SerializedName("reservoir")
    val reservoir: Double? = null,  // Remaining insulin in units

    @SerializedName("iob")
    val iob: IOB? = null,

    @SerializedName("status")
    val status: PumpStatusInfo? = null,

    @SerializedName("clock")
    val clock: String? = null  // ISO 8601 pump time
)

data class Battery(
    @SerializedName("percent")
    val percent: Int? = null
)

data class IOB(
    @SerializedName("bolusiob")
    val bolusIob: Double? = null,

    @SerializedName("iob")
    val iob: Double? = null,  // Total IOB

    @SerializedName("timestamp")
    val timestamp: String? = null
)

data class PumpStatusInfo(
    @SerializedName("status")
    val status: String? = null,  // "normal", "suspended", etc.

    @SerializedName("bolusing")
    val bolusing: Boolean? = null,

    @SerializedName("suspended")
    val suspended: Boolean? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null
)

fun createDeviceStatus(
    timestamp: LocalDateTime,
    batteryPercent: Int? = null,
    reservoirUnits: Double? = null,
    iob: Double? = null,
    pumpStatus: String? = null,
    uploaderBattery: Int? = null
): NightscoutDeviceStatus {
    return NightscoutDeviceStatus(
        createdAt = timestamp.toString(),
        pump = PumpStatus(
            battery = batteryPercent?.let { Battery(it) },
            reservoir = reservoirUnits,
            iob = iob?.let { IOB(iob = it, timestamp = timestamp.toString()) },
            status = pumpStatus?.let { PumpStatusInfo(status = it, timestamp = timestamp.toString()) },
            clock = timestamp.toString()
        ),
        uploaderBattery = uploaderBattery
    )
}
