package com.jwoglom.controlx2.sync.nightscout

import java.security.MessageDigest

const val NightscoutApiSecretHeader = "api-secret"

fun hashNightscoutApiSecret(apiSecret: String): String {
    return MessageDigest.getInstance("SHA-1")
        .digest(apiSecret.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
