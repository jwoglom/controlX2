package com.jwoglom.wearx2.complications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.wearx2.MainActivity
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.complications.internal.ButtonComplicationData

@SuppressLint("LogNotTimber")
class PumpButtonComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:PumpButton"

    private fun complicationData(): ButtonComplicationData {
        return ButtonComplicationData(
            tag,
            "Pump",
            Icon.createWithResource(this, R.drawable.pump)
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return complicationData().get(request.complicationType, tapIntent)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return complicationData().get(ComplicationType.SMALL_IMAGE, null)
    }
}