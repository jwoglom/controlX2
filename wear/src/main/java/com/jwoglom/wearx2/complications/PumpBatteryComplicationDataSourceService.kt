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
    val OldDataThresholdSeconds = 1200 // 20 minutes

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
            pumpBattery = Pair("50", Instant.now())
        )

    private fun getComplicationData(
        tapAction: PendingIntent?,
        pumpBattery: Pair<String, Instant>?,
    ): ComplicationData {
        Log.i(tag, "getComplicationData($pumpBattery)")

        val text: ComplicationText?
        val monochromaticImage: MonochromaticImage?
        val title: ComplicationText?

        val duration = Duration.between(Instant.now(), pumpBattery?.second)
        val percentLabel = "${pumpBattery?.first}%"
        val percentValue = pumpBattery?.first?.toFloatOrNull() ?: 0f

        var case: Case

        when {
            pumpBattery == null || pumpBattery.first == "" -> {
                case = Case.TEXT_ONLY
                text = PlainComplicationText.Builder(
                    text = "?"
                ).build()
                monochromaticImage = null
                title = null
            }
            duration.seconds >= OldDataThresholdSeconds -> {
                case = Case.TEXT_WITH_TITLE
                text = PlainComplicationText.Builder(
                    text = percentLabel
                ).build()
                monochromaticImage = null
                title = TimeDifferenceComplicationText.Builder(
                    style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
                    countUpTimeReference = CountUpTimeReference(pumpBattery.second),
                ).build()
            }
            else -> {
                case = Case.TEXT_WITH_ICON
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

        val caseContentDescription = "Pump Battery ($case)"

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

    private enum class Case(
        val minValue: Float,
        val maxValue: Float
    ) {
        TEXT_ONLY(0f, 100f),
        TEXT_WITH_ICON(-20f, 20f),
        TEXT_WITH_TITLE(57.5f, 824.2f),
        ICON_ONLY(10_045f, 100_000f);

        init {
            require(minValue < maxValue) { "Minimum value was greater than maximum value!" }
        }
    }
}