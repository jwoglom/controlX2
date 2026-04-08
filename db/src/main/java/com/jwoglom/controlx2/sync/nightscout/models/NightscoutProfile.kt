package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName

/**
 * Nightscout profile
 * API endpoint: POST /api/v1/profile
 *
 * Represents the pump's insulin delivery profile for Nightscout display.
 * Uses the time-indexed array format for basal rates and the simple format
 * for other settings.
 */
data class NightscoutProfile(
    @SerializedName("defaultProfile")
    val defaultProfile: String = "Default",

    @SerializedName("store")
    val store: Map<String, ProfileStore>,

    @SerializedName("startDate")
    val startDate: String,

    @SerializedName("created_at")
    val createdAt: String? = null
)

data class ProfileStore(
    @SerializedName("dia")
    val dia: Double,  // Duration of Insulin Action in hours

    @SerializedName("timezone")
    val timezone: String,

    @SerializedName("units")
    val units: String = "mg/dl",

    @SerializedName("carbratio")
    val carbRatio: List<TimeValue>,  // Grams per unit of insulin

    @SerializedName("sens")
    val sensitivity: List<TimeValue>,  // mg/dL drop per unit of insulin

    @SerializedName("basal")
    val basal: List<TimeValue>,  // Basal rates in U/hr

    @SerializedName("target_low")
    val targetLow: List<TimeValue>,

    @SerializedName("target_high")
    val targetHigh: List<TimeValue>
)

data class TimeValue(
    @SerializedName("time")
    val time: String,  // "HH:MM" format

    @SerializedName("value")
    val value: Double,

    @SerializedName("timeAsSeconds")
    val timeAsSeconds: Int
)
