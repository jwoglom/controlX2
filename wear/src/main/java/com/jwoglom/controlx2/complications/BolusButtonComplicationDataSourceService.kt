package com.jwoglom.controlx2.complications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.controlx2.BolusActivity
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.complications.internal.ButtonComplicationData

@SuppressLint("LogNotTimber")
class BolusButtonComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:BolusButton"

    private fun complicationData(): ButtonComplicationData {
        return ButtonComplicationData(
            tag,
            "Bolus",
            Icon.createWithResource(this, R.drawable.bolus_icon)
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(
            tag,
            "onComplicationRequest(${request.complicationType}, ${request.complicationInstanceId}, ${request.immediateResponseRequired})"
        )

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BolusActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return complicationData().get(request.complicationType, tapIntent)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return complicationData().get(ComplicationType.SMALL_IMAGE, null)
    }
}