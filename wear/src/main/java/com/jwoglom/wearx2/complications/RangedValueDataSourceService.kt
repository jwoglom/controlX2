package com.jwoglom.wearx2.complications
/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.PendingIntent
import android.content.ComponentName
import android.graphics.drawable.Icon
import androidx.datastore.core.DataStore
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jwoglom.wearx2.R
import com.jwoglom.wearx2.complications.internal.Complication
import com.jwoglom.wearx2.complications.internal.ComplicationToggleArgs
import com.jwoglom.wearx2.complications.internal.ComplicationToggleReceiver
import com.jwoglom.wearx2.complications.internal.getPumpBatteryState
import com.jwoglom.wearx2.shared.util.setupTimber
import timber.log.Timber

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
class RangedValueDataSourceService : SuspendingComplicationDataSourceService() {

    override fun onCreate() {
        setupTimber("RVDS")
        super.onCreate()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Timber.i("RangedValueDataSourceService.onComplicationRequest($request)")
        if (request.complicationType != ComplicationType.RANGED_VALUE) {
            return null
        }
        val args = ComplicationToggleArgs(
            providerComponent = ComponentName(this, javaClass),
            complication = Complication.RANGED_VALUE,
            complicationInstanceId = request.complicationInstanceId
        )
        val complicationTogglePendingIntent =
            ComplicationToggleReceiver.getComplicationToggleIntent(
                context = this,
                args = args
            )
        // Suspending function to retrieve the complication's state
        var state = args.getPumpBatteryState(this)
        if (state == null) {
            state = 0
        }
        // val case = Case.values()[state.mod(Case.values().size)]
        val case = Case.TEXT_WITH_ICON

        return getComplicationData(
            tapAction = complicationTogglePendingIntent,
            case = case,
            percentValue = state.toDouble(),
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        getComplicationData(
            tapAction = null,
            case = Case.TEXT_WITH_ICON,
            percentValue = 75.0,
        )

    private fun getComplicationData(
        tapAction: PendingIntent?,
        case: Case,
        percentValue: Double,
    ): ComplicationData {
        Timber.i("RangedValueDataSourceService.getComplicationData($case, $percentValue)")

        val text: ComplicationText?
        val monochromaticImage: MonochromaticImage?
        val title: ComplicationText?
        val caseContentDescription: String

        val minValue = case.minValue
        val maxValue = case.maxValue

        when (case) {
            Case.TEXT_ONLY -> {
                text = PlainComplicationText.Builder(
                    text = "$percentValue% (1)"
                ).build()
                monochromaticImage = null
                title = null
                caseContentDescription = "$percentValue% (2)"
            }
            Case.TEXT_WITH_ICON -> {
                text = PlainComplicationText.Builder(
                    text = "$percentValue% (3)"
                ).build()
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.ic_battery)
                )
                    .setAmbientImage(
                        ambientImage = Icon.createWithResource(this, R.drawable.ic_battery_burn_protect)
                    )
                    .build()
                title = null
                caseContentDescription = "$percentValue% (4)"
            }
            Case.TEXT_WITH_TITLE -> {
                text = PlainComplicationText.Builder(
                    text = "$percentValue% (5)"
                ).build()
                monochromaticImage = null
                title = PlainComplicationText.Builder(
                    text = "$percentValue% (6)"
                ).build()

                caseContentDescription = "$percentValue% (7)"
            }
            Case.ICON_ONLY -> {
                text = null
                monochromaticImage = MonochromaticImage.Builder(
                    image = Icon.createWithResource(this, R.drawable.pump)
                ).build()
                title = null
                caseContentDescription = "$percentValue% (8)"
            }
        }

        // Create a content description that includes the value information
        val contentDescription = PlainComplicationText.Builder(
            text = String.format(
                "ranged_value_content_description: %s %s %s %s",
                caseContentDescription,
                percentValue,
                minValue,
                maxValue
            )
        )
            .build()

        return RangedValueComplicationData.Builder(
            value = percentValue.toFloat(),
            min = 0f,
            max = 100f,
            contentDescription = contentDescription
        )
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