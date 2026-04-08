package com.jwoglom.controlx2.db.historylog

import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryLogItemTest {
    @Before
    fun clearHistoryLogCache() {
        itemLruCache.evictAll()
    }

    @Test
    fun parse_usesStoredTypeIdForBolusDeliveryHistoryLog() {
        val message = BolusDeliveryHistoryLog(
            1_000L,
            4_242L,
            123,
            1,
            setOf(BolusDeliveryHistoryLog.BolusType.FOOD1),
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            0,
            1_500,
            0,
            0,
            0,
            1_500
        )

        val parsed = HistoryLogItem(message).parse()

        assertTrue(parsed is BolusDeliveryHistoryLog)
        assertEquals(1_500, (parsed as BolusDeliveryHistoryLog).deliveredTotal)
    }

    @Test
    fun parse_usesStoredTypeIdForBolusCompletedHistoryLog() {
        val message = BolusCompletedHistoryLog(
            1_000L,
            4_243L,
            321,
            1,
            1.2f,
            1.5f,
            0.4f
        )

        val parsed = HistoryLogItem(message).parse()

        assertTrue(parsed is BolusCompletedHistoryLog)
    }
}
