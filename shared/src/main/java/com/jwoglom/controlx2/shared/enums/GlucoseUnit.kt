package com.jwoglom.controlx2.shared.enums

enum class GlucoseUnit(val displayName: String, val abbreviation: String) {
    MGDL("mg/dL", "mg/dL"),
    MMOL("mmol/L", "mmol/L");

    companion object {
        fun fromName(name: String?): GlucoseUnit? {
            if (name == null) return null
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
