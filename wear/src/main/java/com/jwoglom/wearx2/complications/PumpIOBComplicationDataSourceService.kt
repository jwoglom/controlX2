package com.jwoglom.wearx2.complications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.wearx2.MainActivity
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.util.StatePrefs
import java.time.Duration
import java.time.Instant

@SuppressLint("LogNotTimber")
class PumpIOBComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:PumpIOB"
    val OldDataThresholdSeconds = 600 // shows icon instead of recency

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(tag, "onComplicationRequest(${request.complicationType}, ${request.complicationInstanceId}, ${request.immediateResponseRequired})")
        if (request.complicationType != ComplicationType.RANGED_VALUE) {
            return null
        }

        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // Suspending function to retrieve the complication's state
        val pumpBattery = StatePrefs(this).pumpBattery

        return getComplicationData(
            tapAction = tapIntent,
            pumpBattery = pumpBattery,
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        getComplicationData(
            tapAction = null,
            pumpBattery = Pair("50", Instant.now().minusSeconds(601))
        )

    private fun getComplicationData(
        tapAction: PendingIntent?,
        pumpBattery: Pair<String, Instant>?,
    ): ComplicationData {
        Log.i(tag, "getComplicationData($pumpBattery)")

        val text: ComplicationText?
        val monochromaticImage: MonochromaticImage?
        val title: ComplicationText?

        val duration = Duration.between(pumpBattery?.second, Instant.now())
        val percentLabel = "${pumpBattery?.first}%"
        val percentValue = pumpBattery?.first?.toFloatOrNull() ?: 0f
        var displayType = ""

        when {
            pumpBattery == null || pumpBattery.first == "" -> {
                displayType = "empty"
                text = PlainComplicationText.Builder(
                    text = "?"
                ).build()
                monochromaticImage = null
                title = null
            }
            duration.seconds >= OldDataThresholdSeconds -> {
                displayType = "oldData"
                text = PlainComplicationText.Builder(
                    text = percentLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.ic_battery)
                ).setAmbientImage(
                    ambientImage = Icon.createWithResource(this, R.drawable.ic_battery_burn_protect)
                ).build()
                title = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
                    countUpTimeReference = CountUpTimeReference(pumpBattery.second),
                ).build()
            }
            else -> {
                displayType = "normal"
                text = PlainComplicationText.Builder(
                    text = percentLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.ic_battery)
                ).setAmbientImage(
                    ambientImage = Icon.createWithResource(this, R.drawable.ic_battery_burn_protect)
                ).build()
                title = null
            }
        }

        Log.i(tag, "complicationData: $displayType $pumpBattery $duration $percentLabel $percentValue")

        val caseContentDescription = "Pump Battery ($displayType)"

        // Create a content description that includes the value information
        val contentDescription = PlainComplicationText.Builder(
            text = "${caseContentDescription}: $percentLabel"
        ).build()

        return RangedValueComplicationData.Builder(
            value = percentValue,
            min = 0f,
            max = 100f,
            contentDescription = contentDescription
        )
            .setColorRamp(ColorRamp(
                colors = arrayOf(
                    Color.Red.value.toInt(),
                    Color.Yellow.value.toInt(),
                    Color.Green.value.toInt(),
                    Color.Green.value.toInt(),
                ).toIntArray(),
                interpolated = false,
            ))
            .setText(text)
            .setMonochromaticImage(monochromaticImage)
            .setTitle(title)
            .setTapAction(tapAction)
            .build()
    }
}