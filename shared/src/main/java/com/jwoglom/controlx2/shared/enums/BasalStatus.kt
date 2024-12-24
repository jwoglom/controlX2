package com.jwoglom.controlx2.shared.enums

enum class BasalStatus(val str: String) {
    ON("On"),
    ZERO("Zero"),
    TEMP_RATE("Temp Rate"),
    ZERO_TEMP_RATE("Zero Temp Rate"),
    PUMP_SUSPENDED("Pump Suspended"),
    BASALIQ_SUSPENDED("BasalIQ Suspended"),
    CONTROLIQ_INCREASED("ControlIQ Increased"),
    CONTROLIQ_REDUCED("ControlIQ Reduced"),
    UNKNOWN("Unknown")
}