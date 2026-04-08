package com.jwoglom.controlx2.sync.nightscout

import java.net.URI

fun normalizeNightscoutUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) {
        return ""
    }

    val withScheme = if (trimmed.contains("://")) {
        trimmed
    } else {
        "https://$trimmed"
    }

    return try {
        val uri = URI(withScheme)
        if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) {
            null
        } else {
            uri.toString().trimEnd('/')
        }
    } catch (_: Exception) {
        null
    }
}
