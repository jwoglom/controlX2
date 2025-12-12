package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nightscout Profile Structure
 * 
 * {
 *   "defaultProfile": "Default",
 *   "store": {
 *     "Default": {
 *       "dia": 3,
 *       "carbratio": [...],
 *       "carbs_hr": 20,
 *       "delay": 20,
 *       "sens": [...],
 *       "timezone": "UTC",
 *       "basal": [...],
 *       "target_low": [...],
 *       "target_high": [...],
 *       "units": "mg/dL"
 *     }
 *   }
 * }
 */
data class NightscoutProfile(
    @SerializedName("defaultProfile")
    val defaultProfile: String,
    
    @SerializedName("store")
    val store: Map<String, ProfileStore>,
    
    @SerializedName("created_at")
    val createdAt: String
)

data class ProfileStore(
    @SerializedName("dia")
    val dia: Double, // Duration of Insulin Action (hours)
    
    @SerializedName("carbratio")
    val carbRatio: List<ScheduleEntry>,
    
    @SerializedName("sens")
    val sensitivity: List<ScheduleEntry>,
    
    @SerializedName("basal")
    val basal: List<ScheduleEntry>,
    
    @SerializedName("target_low")
    val targetLow: List<ScheduleEntry>,
    
    @SerializedName("target_high")
    val targetHigh: List<ScheduleEntry>,
    
    @SerializedName("timezone")
    val timezone: String,
    
    @SerializedName("units")
    val units: String = "mg/dL"
)

data class ScheduleEntry(
    @SerializedName("time")
    val time: String, // "HH:mm"
    
    @SerializedName("value")
    val value: Double,
    
    @SerializedName("timeAsSeconds")
    val timeAsSeconds: Int // Seconds from midnight
)

fun createNightscoutProfile(
    profileName: String,
    dia: Double,
    carbRatios: List<Triple<Int, Int, Double>>, // hour, minute, value
    sensitivities: List<Triple<Int, Int, Double>>,
    basals: List<Triple<Int, Int, Double>>,
    targets: List<Triple<Int, Int, Double>>, // Assume single target for now or low/high same
    timezone: String,
    timestamp: LocalDateTime
): NightscoutProfile {
    
    fun toSchedule(list: List<Triple<Int, Int, Double>>): List<ScheduleEntry> {
        return list.map { (h, m, v) ->
            ScheduleEntry(
                time = "%02d:%02d".format(h, m),
                value = v,
                timeAsSeconds = h * 3600 + m * 60
            )
        }
    }

    val store = ProfileStore(
        dia = dia,
        carbRatio = toSchedule(carbRatios),
        sensitivity = toSchedule(sensitivities),
        basal = toSchedule(basals),
        targetLow = toSchedule(targets),
        targetHigh = toSchedule(targets),
        timezone = timezone
    )

    return NightscoutProfile(
        defaultProfile = profileName,
        store = mapOf(profileName to store),
        createdAt = timestamp.toString()
    )
}
