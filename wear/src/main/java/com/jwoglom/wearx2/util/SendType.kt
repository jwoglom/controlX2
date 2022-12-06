package com.jwoglom.wearx2.util

enum class SendType(val slug: String) {
    STANDARD("commands"),
    BUST_CACHE("commands-bust-cache"),
    CACHED("cached-commands"),
}