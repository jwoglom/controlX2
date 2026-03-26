package com.jwoglom.controlx2.sync.nightscout

import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.ProfileStore
import com.jwoglom.controlx2.sync.nightscout.models.TimeValue
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Converts IDPManager profile data from the pump into a Nightscout-compatible profile.
 */
object NightscoutProfileConverter {

    /**
     * Convert the active IDPManager profile to a NightscoutProfile.
     *
     * @param idpManager A complete IDPManager with profile data
     * @return NightscoutProfile ready for upload, or null if data is insufficient
     */
    fun convert(idpManager: IDPManager): NightscoutProfile? {
        if (!idpManager.isComplete) return null

        val activeProfile = idpManager.activeProfile ?: return null
        val segments = activeProfile.segments ?: return null
        if (segments.isEmpty()) return null

        val profileName = activeProfile.idpSettingsResponse?.name ?: "Default"
        val timezone = ZoneId.systemDefault().id

        val basalRates = mutableListOf<TimeValue>()
        val carbRatios = mutableListOf<TimeValue>()
        val sensitivities = mutableListOf<TimeValue>()
        val targetLows = mutableListOf<TimeValue>()
        val targetHighs = mutableListOf<TimeValue>()

        for (segment in segments) {
            val startMinutes = segment.profileStartTime
            val timeStr = formatMinutesAsTime(startMinutes)
            val timeAsSeconds = startMinutes * 60

            basalRates.add(TimeValue(
                time = timeStr,
                value = InsulinUnit.from1000To1(segment.profileBasalRate.toLong()),
                timeAsSeconds = timeAsSeconds
            ))

            carbRatios.add(TimeValue(
                time = timeStr,
                value = segment.profileCarbRatio.toDouble(),
                timeAsSeconds = timeAsSeconds
            ))

            sensitivities.add(TimeValue(
                time = timeStr,
                value = segment.profileISF.toDouble(),
                timeAsSeconds = timeAsSeconds
            ))

            targetLows.add(TimeValue(
                time = timeStr,
                value = segment.profileTargetBG.toDouble(),
                timeAsSeconds = timeAsSeconds
            ))

            targetHighs.add(TimeValue(
                time = timeStr,
                value = segment.profileTargetBG.toDouble(),
                timeAsSeconds = timeAsSeconds
            ))
        }

        val store = ProfileStore(
            dia = 5.0,  // Tandem pumps use 5h DIA for Control-IQ
            timezone = timezone,
            units = "mg/dl",
            carbRatio = carbRatios,
            sensitivity = sensitivities,
            basal = basalRates,
            targetLow = targetLows,
            targetHigh = targetHighs
        )

        val now = LocalDateTime.now().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_INSTANT)

        return NightscoutProfile(
            defaultProfile = profileName,
            store = mapOf(profileName to store),
            startDate = now,
            createdAt = now
        )
    }

    private fun formatMinutesAsTime(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "%02d:%02d".format(hours, minutes)
    }
}
