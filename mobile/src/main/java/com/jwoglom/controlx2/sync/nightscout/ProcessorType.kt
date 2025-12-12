package com.jwoglom.controlx2.sync.nightscout

/**
 * Enum of Nightscout processor types
 * Each processor handles a specific type of pump data and uploads it to Nightscout
 */
enum class ProcessorType(val displayName: String) {
    CGM_READING("CGM Readings"),
    BOLUS("Bolus"),
    BASAL("Basal"),
    BASAL_SUSPENSION("Basal Suspension"),
    BASAL_RESUME("Basal Resume"),
    ALARM("Alarms"),
    CGM_ALERT("CGM Alerts"),
    USER_MODE("User Mode"),
    CARTRIDGE("Cartridge"),
    CARB("Carbs"),
    PROFILE("Profile"),
    DEVICE_STATUS("Device Status");

    companion object {
        /**
         * Parse processor type from string name (case-insensitive)
         */
        fun fromName(name: String): ProcessorType? {
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        /**
         * Get all processor types as a set (useful for default config)
         */
        fun all(): Set<ProcessorType> = values().toSet()
    }
}
