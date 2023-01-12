package com.jwoglom.wearx2.complications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.wearx2.MainActivity
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.util.DataClientState
import java.time.Duration
import java.time.Instant

@SuppressLint("LogNotTimber")
class CGMReadingComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:CGMReading"
    val OldDataThresholdSeconds = 600 // shows icon instead of recency
    val MaxMgdl = 300

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(tag, "onComplicationRequest(${request.complicationType}, ${request.complicationInstanceId}, ${request.immediateResponseRequired})")

        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pumpIOB = DataClientState(this).cgmReading

        return getComplicationDataForType(
            request.complicationType,
            buildDataFields(readingPair = pumpIOB),
            tapIntent,
        )
    }

    private fun getComplicationDataForType(
        type: ComplicationType,
        data: DataFields,
        tapAction: PendingIntent?,
    ): ComplicationData? {
        Log.i(tag, "getComplicationDataForType($type, $data, $tapAction)")
        return when (type) {
            ComplicationType.RANGED_VALUE -> rangedValueComplication(data, tapAction)
            ComplicationType.SHORT_TEXT -> shortTextComplication(data, tapAction)
            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val data = buildDataFields(
            readingPair = Pair("15.00", Instant.now())
        )
        return getComplicationDataForType(type, data, null)
    }

    data class DataFields(
        val cgmValue: Float,
        val contentDescription: ComplicationText,
        val text: ComplicationText,
        val monochromaticImage: MonochromaticImage?,
        val title: ComplicationText?
    )

    private fun buildDataFields(
        readingPair: Pair<String, Instant>?,
    ): DataFields {
        Log.i(tag, "buildDataFields($readingPair)")

        val text: ComplicationText
        val monochromaticImage: MonochromaticImage?
        val title: ComplicationText?

        val duration = readingPair?.second?.let {
            Duration.between(it, Instant.now())
        } ?: Duration.ZERO
        val cgmNum = readingPair?.first?.toIntOrNull() ?: 0
        val cgmLabel = "$cgmNum"
        var displayType = ""

        when {
            readingPair == null || readingPair.first == "" || duration == Duration.ZERO -> {
                displayType = "empty"
                text = PlainComplicationText.Builder(
                    text = "?"
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.bolus_x)
                ).build()
                title = null
            }
            duration.seconds >= OldDataThresholdSeconds -> {
                displayType = "oldData"
                text = PlainComplicationText.Builder(
                    text = cgmLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.bolus_x)
                ).build()
                title = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
                    countUpTimeReference = CountUpTimeReference(readingPair.second),
                ).build()
            }
            else -> {
                displayType = "normal"
                text = PlainComplicationText.Builder(
                    text = cgmLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.pump)
                ).build()
                title = null
            }
        }

        Log.i(
            tag,
            "complicationData: displayType=$displayType readingPair=$readingPair duration=${duration.seconds}s cgmLabel=$cgmLabel cgmNum=$cgmNum"
        )

        val caseContentDescription = "CGM Reading ($displayType)"

        // Create a content description that includes the value information
        val contentDescription = PlainComplicationText.Builder(
            text = "${caseContentDescription}: $cgmLabel"
        ).build()

        return DataFields(
            cgmValue = cgmNum.toFloat(),
            contentDescription = contentDescription,
            text = text,
            monochromaticImage = monochromaticImage,
            title = title,
        )
    }

    private fun rangedValueComplication(
        data: DataFields,
        tapAction: PendingIntent?
    ): ComplicationData {
        return RangedValueComplicationData.Builder(
            value = data.cgmValue,
            min = 0f,
            max = MaxMgdl.toFloat(),
            contentDescription = data.contentDescription
        )
//            .setColorRamp(ColorRamp(
//                colors = arrayOf(
//                    Color.White.value.toInt(), // 0..5
//                    Color.Blue.value.toInt(),  // 5..10
//                    Color.Blue.value.toInt(),  // 10..15
//                    Color.Red.value.toInt(),   // 15..20
//                ).toIntArray(),
//                interpolated = false,
//            ))
            .setText(data.text)
            .setMonochromaticImage(data.monochromaticImage)
            .setTitle(data.title)
            .setTapAction(tapAction)
            .build()
    }

    private fun shortTextComplication(
        data: DataFields,
        tapAction: PendingIntent?
    ): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = data.text,
            contentDescription = data.contentDescription,
        )
            .setMonochromaticImage(data.monochromaticImage)
            .setTitle(data.title)
            .setTapAction(tapAction)
            .build()
    }
}