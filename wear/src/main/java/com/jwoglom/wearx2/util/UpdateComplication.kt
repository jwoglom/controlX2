package com.jwoglom.wearx2.util

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.jwoglom.wearx2.complications.CGMReadingComplicationDataSourceService
import com.jwoglom.wearx2.complications.PumpBatteryComplicationDataSourceService
import com.jwoglom.wearx2.complications.PumpIOBComplicationDataSourceService
import timber.log.Timber
import kotlin.reflect.KClass

enum class WearX2Complication(val cls: Class<out ComplicationDataSourceService>) {
    PUMP_BATTERY(PumpBatteryComplicationDataSourceService::class.java),
    PUMP_IOB(PumpIOBComplicationDataSourceService::class.java),
    CGM_READING(CGMReadingComplicationDataSourceService::class.java),
}

fun UpdateComplication(context: Context, complication: WearX2Complication) {
    val request = ComplicationDataSourceUpdateRequester.create(
        context, ComponentName(
            context, complication.cls
        )
    )
    Timber.d("UpdateComplication($complication)")
    request.requestUpdateAll()
}