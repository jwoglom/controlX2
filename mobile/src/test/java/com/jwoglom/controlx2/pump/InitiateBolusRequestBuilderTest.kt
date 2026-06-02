package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog.BolusType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the standard-vs-extended bolus assembly. These exercise the
 * tricky PumpX2 extended-bolus semantics (totalVolume = now portion only, grand
 * total = now + extended, minutes -> seconds) without any Android dependencies.
 */
class InitiateBolusRequestBuilderTest {

    private val base = listOf(BolusType.FOOD2)

    @Test
    fun standardBolus_whenExtendedNull_hasNoExtendedFields() {
        val req = InitiateBolusRequestBuilder.create(
            bolusId = 1,
            totalMilliunits = 5000,
            baseBolusTypes = base,
            foodVolume = 5000,
            correctionVolume = 0,
            carbs = 0,
            bgMgdl = 0,
            iob = 0,
            extended = null,
        )

        assertEquals(5000L, req.totalVolume)
        assertEquals(0L, req.extendedVolume)
        assertEquals(0L, req.extendedSeconds)
        assertEquals(BolusType.toBitmask(BolusType.FOOD2), req.bolusTypeBitmask)
    }

    @Test
    fun extendedConfig_withZeroDuration_isTreatedAsStandard() {
        val req = InitiateBolusRequestBuilder.create(
            bolusId = 1,
            totalMilliunits = 5000,
            baseBolusTypes = base,
            foodVolume = 0,
            correctionVolume = 0,
            carbs = 0,
            bgMgdl = 0,
            iob = 0,
            extended = InitiateBolusRequestBuilder.ExtendedConfig(nowPercent = 50, durationMinutes = 0),
        )

        assertEquals(5000L, req.totalVolume)
        assertEquals(0L, req.extendedVolume)
        assertEquals(BolusType.toBitmask(BolusType.FOOD2), req.bolusTypeBitmask)
    }

    @Test
    fun extendedBolus_50_50_over120min_splitsVolumesAndSetsBit() {
        val req = InitiateBolusRequestBuilder.create(
            bolusId = 2,
            totalMilliunits = 10000,
            baseBolusTypes = base,
            foodVolume = 0,
            correctionVolume = 0,
            carbs = 0,
            bgMgdl = 0,
            iob = 0,
            extended = InitiateBolusRequestBuilder.ExtendedConfig(nowPercent = 50, durationMinutes = 120),
        )

        assertEquals(5000L, req.totalVolume)       // now portion
        assertEquals(5000L, req.extendedVolume)    // extended portion
        assertEquals(120L * 60, req.extendedSeconds)
        assertEquals(0L, req.extended3)
        assertEquals(BolusType.toBitmask(BolusType.FOOD2, BolusType.EXTENDED), req.bolusTypeBitmask)
        // The grand total must be preserved across the split.
        assertEquals(10000L, req.totalVolume + req.extendedVolume)
    }

    @Test
    fun extendedBolus_preservesGrandTotalDespiteRounding() {
        val total = 7771L // 7.771u — not evenly divisible by the 60% split
        val req = InitiateBolusRequestBuilder.create(
            bolusId = 3,
            totalMilliunits = total,
            baseBolusTypes = base,
            foodVolume = 0,
            correctionVolume = 0,
            carbs = 0,
            bgMgdl = 0,
            iob = 0,
            extended = InitiateBolusRequestBuilder.ExtendedConfig(nowPercent = 60, durationMinutes = 90),
        )

        assertEquals(Math.round(total * 0.60), req.totalVolume)
        // Whatever the rounding, no insulin is created or lost.
        assertEquals(total, req.totalVolume + req.extendedVolume)
        assertEquals(90L * 60, req.extendedSeconds)
    }

    @Test
    fun extendedBolus_nowPercentIsCoercedIntoRange() {
        val req = InitiateBolusRequestBuilder.create(
            bolusId = 4,
            totalMilliunits = 4000,
            baseBolusTypes = base,
            foodVolume = 0,
            correctionVolume = 0,
            carbs = 0,
            bgMgdl = 0,
            iob = 0,
            extended = InitiateBolusRequestBuilder.ExtendedConfig(nowPercent = 150, durationMinutes = 60),
        )

        // 150% is coerced to 100%: everything delivered now, nothing extended.
        assertEquals(4000L, req.totalVolume)
        assertEquals(0L, req.extendedVolume)
    }

    @Test
    fun isValidExtendedTotal_enforces040uMinimum() {
        assertFalse(InitiateBolusRequestBuilder.isValidExtendedTotal(399))
        assertTrue(InitiateBolusRequestBuilder.isValidExtendedTotal(400))
        assertTrue(InitiateBolusRequestBuilder.isValidExtendedTotal(10000))
    }
}
