package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyDigestCalculatorTest {

    @Test
    fun testCountHypoEpisodes() {
        val now = 1700000000000L
        val fiveMin = 5 * 60 * 1000L

        // Episode 1: 3 readings below 3.9
        // Normal: 5 readings at 5.5
        // Episode 2: 2 readings below 3.9
        val readings = listOf(
            GlucoseReading(timestamp = now, valueMmol = 3.5),
            GlucoseReading(timestamp = now + fiveMin, valueMmol = 3.4),
            GlucoseReading(timestamp = now + 2 * fiveMin, valueMmol = 3.6),
            // Recovered:
            GlucoseReading(timestamp = now + 3 * fiveMin, valueMmol = 5.5),
            GlucoseReading(timestamp = now + 8 * fiveMin, valueMmol = 6.0), // > 20 min passed
            // Second hypo episode:
            GlucoseReading(timestamp = now + 9 * fiveMin, valueMmol = 3.2),
            GlucoseReading(timestamp = now + 10 * fiveMin, valueMmol = 3.7)
        )

        val count = WeeklyDigestCalculator.countHypoEpisodes(readings, 3.9)
        assertEquals(2, count)
    }

    @Test
    fun testInsufficientData() {
        // Less than 200 points
        val fewReadings = (0 until 50).map { i ->
            GlucoseReading(
                timestamp = 1700000000000L + i * 5 * 60 * 1000L,
                valueMmol = 5.5
            )
        }

        val digest = WeeklyDigestCalculator.calculateDigest(
            currentWeekReadings = fewReadings,
            previousWeekReadings = emptyList(),
            currentStart = 1700000000000L,
            currentEnd = 1700000000000L + 7 * 86400 * 1000L,
            prevStart = 1700000000000L - 7 * 86400 * 1000L,
            prevEnd = 1700000000000L,
            settings = UserSettings(language = "RU")
        )

        assertFalse(digest.hasSufficientData)
        assertTrue(digest.headline.contains("накапливаются"))
        assertTrue(digest.keyInsights.isNotEmpty())
    }

    @Test
    fun testProgressDigestPositiveDynamics() {
        val weekMs = 7L * 86400 * 1000L
        val fiveMin = 5 * 60 * 1000L
        val totalPoints = 1500 // ~5.2 days of dense points (>70%)

        val now = 1700000000000L
        val currentStart = now - weekMs
        val prevStart = currentStart - weekMs

        // Previous week: TIR ~65%, some high readings
        val prevReadings = (0 until totalPoints).map { i ->
            val bg = if (i % 3 == 0) 11.5 else 6.0
            GlucoseReading(timestamp = prevStart + i * fiveMin, valueMmol = bg)
        }

        // Current week: TIR ~90%, stable readings
        val currReadings = (0 until totalPoints).map { i ->
            val bg = if (i % 10 == 0) 10.5 else 5.8
            GlucoseReading(timestamp = currentStart + i * fiveMin, valueMmol = bg)
        }

        val digest = WeeklyDigestCalculator.calculateDigest(
            currentWeekReadings = currReadings,
            previousWeekReadings = prevReadings,
            currentStart = currentStart,
            currentEnd = now,
            prevStart = prevStart,
            prevEnd = currentStart,
            settings = UserSettings(language = "RU")
        )

        assertTrue(digest.hasSufficientData)
        assertTrue("TIR delta should be positive", digest.tirDelta > 0.0)
        assertEquals(0, digest.hypoCountCurrent)
        assertTrue(digest.headline.contains("Прогресс") || digest.headline.contains("Отличная"))
        assertTrue(digest.keyInsights.any { it.contains("TIR вырос") })
    }

    @Test
    fun testHypoWarningDigest() {
        val weekMs = 7L * 86400 * 1000L
        val fiveMin = 5 * 60 * 1000L
        val totalPoints = 1500

        val now = 1700000000000L
        val currentStart = now - weekMs

        // Current week with frequent hypos (>5% TBR)
        val currReadings = (0 until totalPoints).map { i ->
            val bg = if (i % 10 == 0) 3.1 else 6.0
            GlucoseReading(timestamp = currentStart + i * fiveMin, valueMmol = bg)
        }

        val digest = WeeklyDigestCalculator.calculateDigest(
            currentWeekReadings = currReadings,
            previousWeekReadings = emptyList(),
            currentStart = currentStart,
            currentEnd = now,
            prevStart = currentStart - weekMs,
            prevEnd = currentStart,
            settings = UserSettings(language = "RU")
        )

        assertTrue(digest.hasSufficientData)
        assertTrue(digest.totalTbrCurrent > 4.0)
        assertTrue(digest.hypoCountCurrent > 0)
        assertTrue(digest.headline.contains("гипо") || digest.headline.contains("Внимание"))
        assertTrue(digest.recommendation.contains("купировать") || digest.recommendation.contains("базал"))
    }

    @Test
    fun testEnglishLocalization() {
        val weekMs = 7L * 86400 * 1000L
        val fiveMin = 5 * 60 * 1000L
        val totalPoints = 1500

        val now = 1700000000000L
        val currentStart = now - weekMs

        val currReadings = (0 until totalPoints).map { i ->
            GlucoseReading(timestamp = currentStart + i * fiveMin, valueMmol = 6.2)
        }

        val digest = WeeklyDigestCalculator.calculateDigest(
            currentWeekReadings = currReadings,
            previousWeekReadings = emptyList(),
            currentStart = currentStart,
            currentEnd = now,
            prevStart = currentStart - weekMs,
            prevEnd = currentStart,
            settings = UserSettings(language = "EN")
        )

        assertTrue(digest.hasSufficientData)
        assertTrue(digest.headline.contains("Weekly Digest") || digest.headline.contains("Great week"))
        assertTrue(digest.keyInsights.any { it.contains("Time in Range") || it.contains("TIR") })
    }
}
