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
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.wearx2.MainActivity
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.shared.util.oneDecimalPlace
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces
import com.jwoglom.wearx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.wearx2.util.StatePrefs
import com.jwoglom.wearx2.util.StateWearableApi
import com.jwoglom.wearx2.util.connectGoogleApi
import com.jwoglom.wearx2.util.getStateWearableApi
import java.time.Duration
import java.time.Instant

/**
 * A complication provider that supports only [ComplicationType.RANGED_VALUE] and cycles
 * through the possible configurations on tap. The value is randomised on each update.
 *
 * Note: This subclasses [SuspendingComplicationDataSourceService] instead of [ComplicationDataSourceService] to support
 * coroutines, so data operations (specifically, calls to [DataStore]) can be supported directly in the
 * [onComplicationRequest].
 *
 * If you don't perform any suspending operations to update your complications, you can subclass
 * [ComplicationDataSourceService] and override [onComplicationRequest] directly.
 * (see [NoDataDataSourceService] for an example)
 */
@SuppressLint("LogNotTimber")
class PumpIOBComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:PumpIOB"
    val OldDataThresholdSeconds = 600 // shows icon instead of recency
    val MaxIob = 20.0f
    val TwoDecimalPlaces = false

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(tag, "onComplicationRequest(${request.complicationType}, ${request.complicationInstanceId}, ${request.immediateResponseRequired})")

        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pumpIOB = getStateWearableApi(this).pumpIOB

        return getComplicationDataForType(
            request.complicationType,
            buildDataFields(pumpIOB = pumpIOB),
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
            pumpIOB = Pair("15.00", Instant.now())
        )
        return getComplicationDataForType(type, data, null)
    }

    data class DataFields(
        val iobValue: Float,
        val contentDescription: ComplicationText,
        val text: ComplicationText,
        val monochromaticImage: MonochromaticImage?,
        val title: ComplicationText?
    )

    private fun buildDataFields(
        pumpIOB: Pair<String, Instant>?,
    ): DataFields {
        Log.i(tag, "buildDataFields($pumpIOB)")

        val text: ComplicationText
        val monochromaticImage: MonochromaticImage?
        val title: ComplicationText?

        val duration = pumpIOB?.second?.let {
            Duration.between(it, Instant.now())
        } ?: Duration.ZERO
        val iobNum = pumpIOB?.first?.toDoubleOrNull() ?: 0.0
        val iobLabel = if (TwoDecimalPlaces) "${twoDecimalPlaces(iobNum)}u"
                       else "${oneDecimalPlace(iobNum)}u"
        var displayType = ""

        when {
            pumpIOB == null || pumpIOB.first == "" || duration == Duration.ZERO -> {
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
                    text = iobLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.bolus_x)
                ).build()
                title = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
                    countUpTimeReference = CountUpTimeReference(pumpIOB.second),
                ).build()
            }
            else -> {
                displayType = "normal"
                text = PlainComplicationText.Builder(
                    text = iobLabel
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.bolus_icon)
                ).build()
                title = null
            }
        }

        Log.i(
            tag,
            "complicationData: displayType=$displayType pumpIOB=$pumpIOB duration=${duration.seconds}s iobLabel=$iobLabel iobNum=$iobNum"
        )

        val caseContentDescription = "Pump IOB ($displayType)"

        // Create a content description that includes the value information
        val contentDescription = PlainComplicationText.Builder(
            text = "${caseContentDescription}: $iobLabel"
        ).build()

        return DataFields(
            iobValue = iobNum.toFloat(),
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
            value = data.iobValue,
            min = 0f,
            max = MaxIob,
            contentDescription = data.contentDescription
        )
            .setColorRamp(ColorRamp(
                colors = arrayOf(
                    Color.White.value.toInt(), // 0..5
                    Color.Blue.value.toInt(),  // 5..10
                    Color.Blue.value.toInt(),  // 10..15
                    Color.Red.value.toInt(),   // 15..20
                ).toIntArray(),
                interpolated = false,
            ))
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