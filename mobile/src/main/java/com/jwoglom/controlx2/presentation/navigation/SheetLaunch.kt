package com.jwoglom.controlx2.presentation.navigation

import android.os.SystemClock

data class SheetLaunchRequest(
    val target: SheetLaunchTarget,
    val requestId: Long = SystemClock.elapsedRealtimeNanos()
)

sealed class SheetLaunchTarget {
    data class Bolus(
        val prefill: BolusInputPrefill? = null
    ) : SheetLaunchTarget()

    data class TempRate(
        val prefill: TempRateInputPrefill? = null
    ) : SheetLaunchTarget()
}

data class BolusInputPrefill(
    val unitsRawValue: String? = null,
    val carbsRawValue: String? = null,
    val glucoseRawValue: String? = null
)

data class TempRateInputPrefill(
    val percentRawValue: String? = null,
    val hoursRawValue: String? = null,
    val minutesRawValue: String? = null
)
