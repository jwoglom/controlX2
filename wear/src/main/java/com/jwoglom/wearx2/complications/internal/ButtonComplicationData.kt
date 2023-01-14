package com.jwoglom.wearx2.complications.internal

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import com.jwoglom.wearx2.R

@SuppressLint("LogNotTimber")
class ButtonComplicationData(
    val tag: String,
    val title: String,
    val icon: Icon,
) {
    fun get(
        type: ComplicationType,
        tapAction: PendingIntent?,
    ): ComplicationData? {
        Log.i(tag, "getComplicationDataForType($type, $tapAction)")
        return when (type) {
            ComplicationType.SHORT_TEXT -> shortTextComplication(tapAction)
            ComplicationType.RANGED_VALUE -> rangedValueComplication(tapAction)
            ComplicationType.SMALL_IMAGE -> smallImageComplication(tapAction)
            ComplicationType.MONOCHROMATIC_IMAGE -> monochromaticImageComplication(tapAction)
            else -> null
        }
    }

    private fun shortTextComplication(
        tapAction: PendingIntent?
    ): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(title).build(),
            contentDescription = ComplicationText.EMPTY
        )
            .setSmallImage(
                SmallImage.Builder(
                image = icon,
                type = SmallImageType.ICON
            ).build())
            .setTapAction(tapAction)
            .build()
    }

    private fun rangedValueComplication(
        tapAction: PendingIntent?
    ): ComplicationData {
        return RangedValueComplicationData.Builder(
            0f, 0f, 0f, ComplicationText.EMPTY
        )
            .setSmallImage(
                SmallImage.Builder(
                image = icon,
                type = SmallImageType.ICON
            ).build())
            .setTapAction(tapAction)
            .build()
    }

    private fun smallImageComplication(
        tapAction: PendingIntent?
    ): ComplicationData {
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = icon,
                type = SmallImageType.ICON
            ).build(),
            contentDescription = PlainComplicationText.Builder(title).build(),
        )
            .setTapAction(tapAction)
            .build()
    }

    private fun monochromaticImageComplication(
        tapAction: PendingIntent?
    ): ComplicationData {
        return MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(
                image = icon,
            ).build(),
            contentDescription = PlainComplicationText.Builder(title).build(),
        )
            .setTapAction(tapAction)
            .build()
    }
}