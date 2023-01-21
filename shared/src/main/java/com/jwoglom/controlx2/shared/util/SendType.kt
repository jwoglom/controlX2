package com.jwoglom.controlx2.shared.util

enum class SendType(val slug: String) {
    STANDARD("commands"),
    BUST_CACHE("commands-bust-cache"),
    CACHED("cached-commands"),
    DEBUG_PROMPT("commands"),
}