package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName

/**
 * Nightscout announcement payload represented as a treatment entry
 * (`eventType = "Announcement"`).
 */
data class NightscoutAnnouncement(
    @SerializedName("_id")
    val id: String? = null,

    @SerializedName("eventType")
    val eventType: String? = null,

    @SerializedName(value = "notes", alternate = ["message"])
    val message: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("enteredBy")
    val enteredBy: String? = null,

    @SerializedName("date")
    val date: Long? = null
)
