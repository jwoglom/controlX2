package com.jwoglom.controlx2.sync.xdrip

/**
 * Selectable xDrip payload groups that can be enabled/disabled in UI and sender logic.
 */
enum class XdripPayloadGroup(val displayName: String) {
    CGM("CGM SGV"),
    PUMP_DEVICE_STATUS("Pump Device Status"),
    TREATMENTS("Treatments"),
    STATUS_LINE("Status Line");

    companion object {
        fun fromName(name: String): XdripPayloadGroup? {
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        fun all(): Set<XdripPayloadGroup> = values().toSet()
    }
}
