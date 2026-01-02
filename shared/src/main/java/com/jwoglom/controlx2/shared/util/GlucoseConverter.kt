package com.jwoglom.controlx2.shared.util

import com.jwoglom.controlx2.shared.enums.GlucoseUnit

object GlucoseConverter {
    const val MGDL_TO_MMOL_FACTOR = 0.0555

    fun convert(value: Double, from: GlucoseUnit, to: GlucoseUnit): Double {
        if (from == to) return value
        return when (to) {
            GlucoseUnit.MMOL -> value * MGDL_TO_MMOL_FACTOR
            GlucoseUnit.MGDL -> value / MGDL_TO_MMOL_FACTOR
        }
    }

    fun format(value: Int, unit: GlucoseUnit): String {
        return when (unit) {
            GlucoseUnit.MGDL -> value.toString()
            GlucoseUnit.MMOL -> String.format("%.1f", value * MGDL_TO_MMOL_FACTOR)
        }
    }
}
