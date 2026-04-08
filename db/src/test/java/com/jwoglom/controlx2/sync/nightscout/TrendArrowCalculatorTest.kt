package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TrendArrowCalculatorTest {

    private val baseTime = LocalDateTime.of(2024, 12, 12, 10, 0, 0)

    @Test
    fun testFlatTrend() {
        val readings = listOf(
            baseTime to 120,
            baseTime.plusMinutes(5) to 121,
            baseTime.plusMinutes(10) to 120
        )
        assertEquals("Flat", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testFortyFiveUpTrend() {
        // ~1.5 mg/dL per minute over 10 minutes = +15
        val readings = listOf(
            baseTime to 100,
            baseTime.plusMinutes(5) to 108,
            baseTime.plusMinutes(10) to 115
        )
        assertEquals("FortyFiveUp", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testSingleUpTrend() {
        // ~2.5 mg/dL per minute over 10 minutes = +25
        val readings = listOf(
            baseTime to 100,
            baseTime.plusMinutes(5) to 113,
            baseTime.plusMinutes(10) to 125
        )
        assertEquals("SingleUp", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testDoubleUpTrend() {
        // ~4 mg/dL per minute over 10 minutes = +40
        val readings = listOf(
            baseTime to 100,
            baseTime.plusMinutes(5) to 120,
            baseTime.plusMinutes(10) to 140
        )
        assertEquals("DoubleUp", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testFortyFiveDownTrend() {
        // ~-1.5 mg/dL per minute
        val readings = listOf(
            baseTime to 130,
            baseTime.plusMinutes(5) to 122,
            baseTime.plusMinutes(10) to 115
        )
        assertEquals("FortyFiveDown", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testSingleDownTrend() {
        // ~-2.5 mg/dL per minute
        val readings = listOf(
            baseTime to 150,
            baseTime.plusMinutes(5) to 138,
            baseTime.plusMinutes(10) to 125
        )
        assertEquals("SingleDown", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testDoubleDownTrend() {
        // ~-4 mg/dL per minute
        val readings = listOf(
            baseTime to 180,
            baseTime.plusMinutes(5) to 160,
            baseTime.plusMinutes(10) to 140
        )
        assertEquals("DoubleDown", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testInsufficientReadings_returnsNull() {
        val readings = listOf(baseTime to 120)
        assertNull(TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testEmptyReadings_returnsNull() {
        assertNull(TrendArrowCalculator.calculateDirection(emptyList()))
    }

    @Test
    fun testReadingsTooFarApart_returnsNull() {
        val readings = listOf(
            baseTime to 120,
            baseTime.plusMinutes(30) to 130
        )
        assertNull(TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testReadingsTooClose_returnsNull() {
        val readings = listOf(
            baseTime to 120,
            baseTime.plusMinutes(2) to 121
        )
        assertNull(TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testTwoReadings_fiveMinutesApart() {
        // 2 readings 5 min apart, +10 = 2 mg/dL/min -> FortyFiveUp (< 2.0)
        val readings = listOf(
            baseTime to 100,
            baseTime.plusMinutes(5) to 108
        )
        assertEquals("FortyFiveUp", TrendArrowCalculator.calculateDirection(readings))
    }

    @Test
    fun testDirectionFromRate_boundaryValues() {
        assertEquals("DoubleDown", TrendArrowCalculator.directionFromRate(-3.0))
        assertEquals("SingleDown", TrendArrowCalculator.directionFromRate(-2.0))
        assertEquals("FortyFiveDown", TrendArrowCalculator.directionFromRate(-1.0))
        assertEquals("Flat", TrendArrowCalculator.directionFromRate(0.0))
        assertEquals("Flat", TrendArrowCalculator.directionFromRate(0.99))
        assertEquals("FortyFiveUp", TrendArrowCalculator.directionFromRate(1.0))
        assertEquals("SingleUp", TrendArrowCalculator.directionFromRate(2.0))
        assertEquals("DoubleUp", TrendArrowCalculator.directionFromRate(3.0))
    }
}
