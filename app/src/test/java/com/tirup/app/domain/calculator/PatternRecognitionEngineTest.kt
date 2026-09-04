package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternRecognitionEngineTest {

    @Test
    fun testInsufficientDataReturnsInfo() {
        val patterns = PatternRecognitionEngine.analyze(
            bins = emptyList(),
            stats = GlucoseStatistics(totalCount = 10)
        )
        assertEquals(1, patterns.size)
        assertEquals(PatternSeverity.INFO, patterns[0].severity)
    }

    @Test
    fun testNightHypoglycemiaDetection() {
        val bins = listOf(
            AGPPercentileBin(
                binIndex = 4,
                minuteOfDay = 120, // 02:00
                formattedTime = "02:00",
                p10 = 3.4,
                p25 = 3.8,
                p50 = 4.5,
                p75 = 6.0,
                p90 = 7.0,
                readingsCount = 20
            )
        )
        val patterns = PatternRecognitionEngine.analyze(
            bins = bins,
            stats = GlucoseStatistics(totalCount = 100)
        )
        assertTrue(patterns.any { it.severity == PatternSeverity.ALERT && it.titleRu.contains("Ночные провалы") })
    }

    @Test
    fun testNightHypoglycemiaWithCustomHoursCrossingMidnight() {
        val bins = listOf(
            AGPPercentileBin(
                binIndex = 46,
                minuteOfDay = 1410, // 23:30
                formattedTime = "23:30",
                p10 = 3.3,
                p25 = 3.6,
                p50 = 4.5,
                p75 = 6.0,
                p90 = 7.0,
                readingsCount = 20
            )
        )
        val patterns = PatternRecognitionEngine.analyze(
            bins = bins,
            stats = GlucoseStatistics(totalCount = 100),
            nightStartHour = 23,
            nightEndHour = 7
        )
        assertTrue(patterns.any { it.severity == PatternSeverity.ALERT && it.titleRu == "Ночные провалы (23:00–07:00)" })
    }

    @Test
    fun testDawnPhenomenonDetection() {
        val bins = listOf(
            AGPPercentileBin(
                binIndex = 3,
                minuteOfDay = 180, // 03:00
                formattedTime = "03:00",
                p10 = 4.5,
                p25 = 5.0,
                p50 = 5.8,
                p75 = 6.5,
                p90 = 7.2,
                readingsCount = 30
            ),
            AGPPercentileBin(
                binIndex = 7,
                minuteOfDay = 420, // 07:00
                formattedTime = "07:00",
                p10 = 7.0,
                p25 = 8.2,
                p50 = 10.2, // High morning spike
                p75 = 11.5,
                p90 = 12.8,
                readingsCount = 30
            )
        )
        val patterns = PatternRecognitionEngine.analyze(
            bins = bins,
            stats = GlucoseStatistics(totalCount = 200)
        )
        assertTrue(patterns.any { it.titleRu.contains("Утренней зари") })
    }

    @Test
    fun testStableProfileDetection() {
        val bins = listOf(
            AGPPercentileBin(
                binIndex = 0,
                minuteOfDay = 60,
                formattedTime = "01:00",
                p10 = 4.5,
                p25 = 5.0,
                p50 = 5.5,
                p75 = 6.5,
                p90 = 7.0,
                readingsCount = 30
            ),
            AGPPercentileBin(
                binIndex = 1,
                minuteOfDay = 600,
                formattedTime = "10:00",
                p10 = 4.8,
                p25 = 5.5,
                p50 = 6.2,
                p75 = 7.2,
                p90 = 8.0,
                readingsCount = 30
            )
        )
        val patterns = PatternRecognitionEngine.analyze(
            bins = bins,
            stats = GlucoseStatistics(totalCount = 200, cvPercent = 25.0)
        )
        assertTrue(patterns.isEmpty())
    }
}
