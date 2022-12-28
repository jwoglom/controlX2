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
import com.jwoglom.wearx2.MainActivity
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.util.StatePrefs
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
class PumpBatteryComplicationDataSourceService : SuspendingComplicationDataSourceService() {
    val tag = "WearX2:Compl:PumpBattery"
    val OldDataThresholdSeconds = 600 // shows icon instead of recency

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.i(tag, "onComplicationRequest(${request.complicationType}, ${request.complicationInstanceId}, ${request.immediateResponseRequired})")

        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pumpBattery = StatePrefs(this).pumpBattery

        return getComplicationDataForType(
            request.complicationType,
            buildDataFields(pumpBattery = pumpBattery),
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
            pumpBattery = Pair("50", Instant.now().minusSeconds(601))
        )
        return getComplicationDataForType(type, data, null)
    }

    data class DataFields(
        val percentValue: Float,
        val contentDescription: ComplicationText,
        val text: ComplicationText,
        val monochromaticImage: MonochromaticImage?,
        val title: ComplicationText?
    )

    private fun buildDataFields(
        pumpBattery: Pair<String, Instant>?,
    ): DataFields {
        Log.i(tag, "buildDataFields($pumpBattery)")

        val text: ComplicationText
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

        Log.i(
            tag,
            "complicationData: $displayType $pumpBattery $duration $percentLabel $percentValue"
        )

        val caseContentDescription = "Pump Battery ($displayType)"

        // Create a content description that includes the value information
        val contentDescription = PlainComplicationText.Builder(
            text = "${caseContentDescription}: $percentLabel"
        ).build()

        return DataFields(
            percentValue = percentValue,
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
            value = data.percentValue,
            min = 0f,
            max = 100f,
            contentDescription = data.contentDescription
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