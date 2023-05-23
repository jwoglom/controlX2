package com.jwoglom.controlx2.util

import com.google.common.primitives.UnsignedInts.toLong
import org.junit.Assert
import org.junit.Test

class HistoryLogFetcherTest {
    fun rng(x: IntRange): LongRange {
        return LongRange(toLong(x.first), toLong(x.last))
    }
    @Test
    fun getMissingIds_allFull() {
        Assert.assertEquals(listOf<LongRange>(), getMissingIds(listOf(100), 100, 100))
        Assert.assertEquals(listOf<LongRange>(), getMissingIds(listOf(100,101,102), 100, 102))
    }

    @Test
    fun getMissingIds_singleMissing() {
        Assert.assertEquals(listOf(rng(101..101)), getMissingIds(listOf(100,102), 100, 102))
        Assert.assertEquals(listOf(rng(103..103)), getMissingIds(listOf(100,101,102,104,105), 100, 105))
    }

    @Test
    fun getMissingIds_missingAtStart() {
        Assert.assertEquals(listOf(rng(100..100)), getMissingIds(listOf(101,102,103), 100, 103))
        Assert.assertEquals(listOf(rng(100..101)), getMissingIds(listOf(102,103), 100, 103))
    }

    @Test
    fun getMissingIds_missingAtEnd() {
        Assert.assertEquals(listOf(rng(103..103)), getMissingIds(listOf(100,101,102), 100, 103))
        Assert.assertEquals(listOf(rng(103..104)), getMissingIds(listOf(100,101,102), 100, 104))
    }

    @Test
    fun getMissingIds_missingStartAndEnd() {
        Assert.assertEquals(listOf(rng(100..100), rng(105..105)), getMissingIds(listOf(101,102,103,104), 100, 105))
        Assert.assertEquals(listOf(rng(100..101), rng(105..106)), getMissingIds(listOf(102,103,104), 100, 106))
    }
    @Test
    fun getMissingIds_missingStartEndAndMiddle() {
        Assert.assertEquals(listOf(rng(100..100), rng(103..103), rng(105..105)), getMissingIds(listOf(101,102,104), 100, 105))
        Assert.assertEquals(listOf(rng(100..101), rng(103..103), rng(105..106)), getMissingIds(listOf(102,104), 100, 106))
        Assert.assertEquals(listOf(rng(100..110), rng(115..120), rng(125..130)), getMissingIds(listOf(111,112,113,114,121,122,123,124), 100, 130))
        Assert.assertEquals(listOf(rng(100..110), rng(115..120), rng(125..130), rng(135..140)), getMissingIds(listOf(111,112,113,114,121,122,123,124,131,132,133,134), 100, 140))
    }
}