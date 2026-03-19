package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName

/**
 * Nightscout announcement payload from /api/v1/announcements.
 */
data class NightscoutAnnouncement(
    @SerializedName("_id")
    val id: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null,

    @SerializedName("level")
    val level: String? = null,

    @SerializedName("plugin")
    val plugin: String? = null,

    @SerializedName("isActive")
    val isActive: Boolean? = null
)
