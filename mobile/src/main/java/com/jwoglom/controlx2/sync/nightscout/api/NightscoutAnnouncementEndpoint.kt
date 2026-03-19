package com.jwoglom.controlx2.sync.nightscout.api

import java.net.URLEncoder

internal object NightscoutAnnouncementEndpoint {
    fun latest(count: Int): String {
        return "/api/v1/announcements?count=${count.coerceAtLeast(1)}"
    }

    fun since(sinceTimestamp: Long?, sinceId: String?, count: Int): String {
        val params = mutableListOf("count=${count.coerceAtLeast(1)}")
        sinceTimestamp?.let { params.add("find[created_at][\$gte]=$it") }
        sinceId?.takeIf { it.isNotBlank() }?.let {
            params.add("find[_id][\$gt]=${URLEncoder.encode(it, Charsets.UTF_8.name())}")
        }
        return "/api/v1/announcements?${params.joinToString("&")}"
    }
}
