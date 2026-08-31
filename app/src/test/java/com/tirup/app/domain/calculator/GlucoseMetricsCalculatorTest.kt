package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GlucoseMetricsCalculatorTest {

    @Test
    fun testGmiCalculation() {
        // GMI = 3.31 + 0.431 * Mean
        // If Mean = 10.0 mmol/L -> GMI = 3.31 + 4.31 = 7.62%
        val mean = 10.0
        val gmi = GlucoseMetricsCalculator.calculateGmi(mean)
        assertEquals(7.62, gmi, 0.001)

        // If Mean = 7.0 mmol/L -> GMI = 3.31 + (0.431 * 7.0) = 3.31 + 3.017 = 6.327%
        val gmi2 = GlucoseMetricsCalculator.calculateGmi(7.0)
        assertEquals(6.327, gmi2, 0.001)
    }

    @Test
    fun testMeanSdAndCvCalculation() {
        // Values: [6.0, 8.0, 10.0]
        // Mean = 8.0
        // Variance = ((6-8)^2 + (8-8)^2 + (10-8)^2) / 2 = (4 + 0 + 4) / 2 = 4.0
        // SD = sqrt(4.0) = 2.0
        // %CV = (2.0 / 8.0) * 100% = 25.0%
        val readings = listOf(
            GlucoseReading(timestamp = 1000L, valueMmol = 6.0),
            GlucoseReading(timestamp = 2000L, valueMmol = 8.0),
            GlucoseReading(timestamp = 3000L, valueMmol = 10.0)
        )

        val stats = GlucoseMetricsCalculator.calculateStatistics(readings)

        assertEquals(8.0, stats.meanMmol, 0.001)
        assertEquals(2.0, stats.sdMmol, 0.001)
        assertEquals(25.0, stats.cvPercent, 0.001)
        assertEquals(GlucoseMetricsCalculator.calculateGmi(8.0), stats.gmiPercent, 0.001)
        assertEquals(3, stats.totalCount)
    }

    @Test
    fun testTargetRangesAndBreakdown() {
        val targets = TargetRanges(
            tirLowMmol = 3.9,
            tirHighMmol = 10.0,
            tingHighMmol = 7.8,
            veryLowThresholdMmol = 3.0,
            veryHighThresholdMmol = 13.9
        )

        // Categorize checks
        assertEquals(GlucoseRangeCategory.VERY_LOW, targets.categorize(2.5))
        assertEquals(GlucoseRangeCategory.LOW, targets.categorize(3.5))
        assertEquals(GlucoseRangeCategory.TIGHT, targets.categorize(5.5))
        assertEquals(GlucoseRangeCategory.TARGET, targets.categorize(8.5))
        assertEquals(GlucoseRangeCategory.HIGH, targets.categorize(11.0))
        assertEquals(GlucoseRangeCategory.VERY_HIGH, targets.categorize(15.0))

        val readings = listOf(
            GlucoseReading(timestamp = 1000L, valueMmol = 2.5),  // Very Low (1/6 = 16.67%)
            GlucoseReading(timestamp = 2000L, valueMmol = 3.5),  // Low (1/6 = 16.67%)
            GlucoseReading(timestamp = 3000L, valueMmol = 5.0),  // Tight & TIR (1/6)
            GlucoseReading(timestamp = 4000L, valueMmol = 6.0),  // Tight & TIR (1/6)
            GlucoseReading(timestamp = 5000L, valueMmol = 9.0),  // Target & TIR (1/6)
            GlucoseReading(timestamp = 6000L, valueMmol = 12.0)  // High (1/6)
        )

        val stats = GlucoseMetricsCalculator.calculateStatistics(readings, targets)

        // TIR: 3 out of 6 (50.0%)
        assertEquals(50.0, stats.tirPercent, 0.001)
        // TING: 2 out of 6 (33.333%)
        assertEquals(33.333, stats.tingPercent, 0.01)
        // TBR Very Low: 1 out of 6 (16.67%)
        assertEquals(16.666, stats.tbrVeryLowPercent, 0.01)
        // TBR Low: 1 out of 6 (16.67%)
        assertEquals(16.666, stats.tbrLowPercent, 0.01)
        // TAR High: 1 out of 6 (16.67%)
        assertEquals(16.666, stats.tarHighPercent, 0.01)
        // TAR Very High: 0 out of 6 (0%)
        assertEquals(0.0, stats.tarVeryHighPercent, 0.001)
    }

    @Test
    fun testNightStabilityDetection() {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(Calendar.HOUR_OF_DAY, 3) // 03:00 AM (night)
        val nightTime = calendar.timeInMillis

        // Stable night (values in 5.5..6.5, CV is low, TIR is 100%)
        val stableNightReadings = listOf(
            GlucoseReading(timestamp = nightTime, valueMmol = 5.5),
            GlucoseReading(timestamp = nightTime + 300000, valueMmol = 5.8),
            GlucoseReading(timestamp = nightTime + 600000, valueMmol = 6.0)
        )

        val stats = GlucoseMetricsCalculator.calculateStatistics(stableNightReadings)
        assertTrue(stats.nightStability.isStable)
        assertEquals(100.0, stats.nightStability.tirPercent, 0.001)
        assertTrue(stats.nightStability.cvPercent < 36.0)
    }
}
