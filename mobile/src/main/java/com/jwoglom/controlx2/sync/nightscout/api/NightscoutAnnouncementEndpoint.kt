package com.jwoglom.controlx2.sync.nightscout.api

import java.net.URLEncoder

internal object NightscoutAnnouncementEndpoint {
    private const val AnnouncementEventType = "Announcement"

    fun latest(count: Int): String {
        val encodedEventType = URLEncoder.encode(AnnouncementEventType, Charsets.UTF_8.name())
        return "/api/v1/treatments?find[eventType]=$encodedEventType&count=${count.coerceAtLeast(1)}"
    }

    fun since(sinceTimestamp: Long?, sinceId: String?, count: Int): String {
        val encodedEventType = URLEncoder.encode(AnnouncementEventType, Charsets.UTF_8.name())
        val params = mutableListOf(
            "find[eventType]=$encodedEventType",
            "count=${count.coerceAtLeast(1)}"
        )
        sinceTimestamp?.let { params.add("find[created_at][\$gte]=$it") }
        sinceId?.takeIf { it.isNotBlank() }?.let {
            params.add("find[_id][\$gt]=${URLEncoder.encode(it, Charsets.UTF_8.name())}")
        }
        return "/api/v1/treatments?${params.joinToString("&")}"
    }
}
