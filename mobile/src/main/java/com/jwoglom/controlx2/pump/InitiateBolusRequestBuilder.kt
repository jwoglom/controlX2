package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog.BolusType

/**
 * Assembles an [InitiateBolusRequest] for either a standard bolus or an
 * extended ("combo") bolus.
 *
 * Extended-bolus semantics, per the PumpX2 protocol (these are easy to get
 * wrong, so they are centralized here):
 *  - [InitiateBolusRequest]'s `totalVolume` argument is the amount delivered
 *    **now**, NOT the grand total. The grand total delivered over the whole
 *    extended period is `totalVolume + extendedVolume`.
 *  - `extendedVolume` is the portion delivered gradually over the extended
 *    window; `extendedSeconds` is that window's length (the pump/history log
 *    express it in minutes, so callers pass minutes and we convert here).
 *  - the third extended field is unknown to the protocol and is always 0.
 *  - the [BolusType.EXTENDED] bit must be set for an extended bolus.
 *  - the pump requires the grand total to be at least
 *    [MIN_EXTENDED_BOLUS_MILLIUNITS]; below that an extended bolus is rejected.
 */
object InitiateBolusRequestBuilder {
    /** 0.40u: the pump-enforced minimum grand total (now + extended) for an extended bolus. */
    const val MIN_EXTENDED_BOLUS_MILLIUNITS = 400L

    /** Number of seconds in a minute; extended duration is collected in minutes but sent in seconds. */
    private const val SECONDS_PER_MINUTE = 60L

    data class ExtendedConfig(
        /** Percentage of the grand total delivered immediately (coerced to 0..100). */
        val nowPercent: Int,
        /** Length of the extended portion, in minutes (must be > 0 to take effect). */
        val durationMinutes: Int,
    )

    /**
     * Builds the request. When [extended] is null (or has a non-positive
     * duration) a standard bolus is produced, identical to the previous
     * 8-argument call site.
     *
     * @param totalMilliunits the **grand total** dose (now + extended), in milliunits.
     * @param baseBolusTypes FOOD2 plus optionally FOOD1/CORRECTION; [BolusType.EXTENDED] is added here when needed.
     */
    @JvmStatic
    fun create(
        bolusId: Int,
        totalMilliunits: Long,
        baseBolusTypes: List<BolusType>,
        foodVolume: Long,
        correctionVolume: Long,
        carbs: Int,
        bgMgdl: Int,
        iob: Long,
        extended: ExtendedConfig?,
    ): InitiateBolusRequest {
        if (extended == null || extended.durationMinutes <= 0) {
            return InitiateBolusRequest(
                totalMilliunits,
                bolusId,
                BolusType.toBitmask(*baseBolusTypes.toTypedArray()),
                foodVolume,
                correctionVolume,
                carbs,
                bgMgdl,
                iob,
            )
        }

        val nowPercent = extended.nowPercent.coerceIn(0, 100)
        val nowVolume = Math.round(totalMilliunits * (nowPercent / 100.0))
        val extendedVolume = totalMilliunits - nowVolume
        val types = baseBolusTypes + BolusType.EXTENDED

        return InitiateBolusRequest(
            nowVolume,
            bolusId,
            BolusType.toBitmask(*types.toTypedArray()),
            foodVolume,
            correctionVolume,
            carbs,
            bgMgdl,
            iob,
            extendedVolume,
            extended.durationMinutes * SECONDS_PER_MINUTE,
            0L,
        )
    }

    /** Whether [totalMilliunits] meets the pump's minimum grand total for an extended bolus. */
    @JvmStatic
    fun isValidExtendedTotal(totalMilliunits: Long): Boolean =
        totalMilliunits >= MIN_EXTENDED_BOLUS_MILLIUNITS
}
