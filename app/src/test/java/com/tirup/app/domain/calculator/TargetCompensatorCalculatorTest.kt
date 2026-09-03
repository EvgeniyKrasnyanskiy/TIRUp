package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetCompensatorCalculatorTest {

    @Test
    fun testCompensatorFormulaStandardScenario() {
        // Spec Example:
        // T_target = 70%, D_total = 7, D_past = 4, T_past = 65%
        // D_rem = 3
        // T_needed = (70 * 7 - 65 * 4) / 3 = (490 - 260) / 3 = 230 / 3 = 76.666...%
        val goal = TargetCompensatorCalculator.calculateCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            totalDays = 7,
            pastDays = 4,
            pastAveragePercent = 65.0
        )

        assertEquals(CompensatorStatus.REACHABLE, goal.status)
        assertEquals(3, goal.remainingDays)
        assertEquals(76.666, goal.neededRemainingPercent, 0.01)
    }

    @Test
    fun testCompensatorExceedingTarget() {
        // Target = 70%, Past 4 days = 85%
        // T_needed = (70 * 7 - 85 * 4) / 3 = (490 - 340) / 3 = 150 / 3 = 50.0%
        // Since past is 85% (>70%) and needed is 50%, user is exceeding the goal!
        val goal = TargetCompensatorCalculator.calculateCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            totalDays = 7,
            pastDays = 4,
            pastAveragePercent = 85.0
        )

        assertEquals(CompensatorStatus.EXCEEDING, goal.status)
        assertEquals(50.0, goal.neededRemainingPercent, 0.01)
    }

    @Test
    fun testCompensatorUnrealisticScenario() {
        // Target = 80%, Past 6 days = 40%, Remaining = 1 day
        // T_needed = (80 * 7 - 40 * 6) / 1 = 560 - 240 = 320% -> Impossible (>100%)
        val goal = TargetCompensatorCalculator.calculateCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 80.0,
            totalDays = 7,
            pastDays = 6,
            pastAveragePercent = 40.0
        )

        assertEquals(CompensatorStatus.UNREALISTIC, goal.status)
        assertEquals(320.0, goal.neededRemainingPercent, 0.01)
    }

    @Test
    fun testCompensatorStartOfPeriod() {
        // Day 0 of 7 -> needed is exactly the target (70%)
        val goal = TargetCompensatorCalculator.calculateCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            totalDays = 7,
            pastDays = 0,
            pastAveragePercent = 0.0
        )

        assertEquals(CompensatorStatus.REACHABLE, goal.status)
        assertEquals(7, goal.remainingDays)
        assertEquals(70.0, goal.neededRemainingPercent, 0.01)
    }

    @Test
    fun testFormatHoursMins() {
        assertEquals("3 ч 40 мин", TargetCompensatorCalculator.formatHoursMins(220, true))
        assertEquals("3h 40m", TargetCompensatorCalculator.formatHoursMins(220, false))
        assertEquals("2 ч", TargetCompensatorCalculator.formatHoursMins(120, true))
        assertEquals("45 мин", TargetCompensatorCalculator.formatHoursMins(45, true))
    }

    @Test
    fun testDailyCompensatorOutOfRange() {
        val ranges = com.tirup.app.domain.model.TargetRanges()
        val refTime = 1700000000000L // arbitrary fixed time
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = refTime
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
        }
        val noonTime = calendar.timeInMillis

        // Readings around noon: some in range, latest out of range (high)
        val latest = com.tirup.app.domain.model.GlucoseReading(
            timestamp = noonTime,
            valueMmol = 11.5,
            trendArrow = "↑"
        )
        val readings = listOf(
            com.tirup.app.domain.model.GlucoseReading(timestamp = noonTime - 3600000L, valueMmol = 6.0),
            com.tirup.app.domain.model.GlucoseReading(timestamp = noonTime - 1800000L, valueMmol = 7.0),
            latest
        )

        val goal = TargetCompensatorCalculator.calculateDailyCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            latestReading = latest,
            recentReadings = readings,
            targetRanges = ranges,
            language = "RU",
            referenceTimestamp = noonTime
        )

        org.junit.Assert.assertFalse(goal.isCurrentlyInRange)
        org.junit.Assert.assertTrue(goal.recommendationRu.contains("Сахар вне диапазона"))
        org.junit.Assert.assertTrue(goal.recommendationRu.contains("Вернитесь в норму"))
    }

    @org.junit.Test
    fun testDailyCompensatorFewPointsDoesNotTriggerExceeding() {
        val ranges = com.tirup.app.domain.model.TargetRanges()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
        }
        val eveningTime = calendar.timeInMillis

        // 26 points over 26 minutes (1-min intervals, 100% in range)
        val readings = (0 until 26).map { i ->
            com.tirup.app.domain.model.GlucoseReading(
                timestamp = eveningTime - (25 - i) * 60_000L,
                valueMmol = 5.5
            )
        }
        val latest = readings.last()

        val goal = TargetCompensatorCalculator.calculateDailyCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            latestReading = latest,
            recentReadings = readings,
            targetRanges = ranges,
            language = "RU",
            referenceTimestamp = eveningTime
        )

        // Must NOT declare goal completed (EXCEEDING) with only 26 points
        org.junit.Assert.assertNotEquals(com.tirup.app.domain.model.CompensatorStatus.EXCEEDING, goal.status)
        org.junit.Assert.assertEquals(com.tirup.app.domain.model.CompensatorStatus.INSUFFICIENT_DATA, goal.status)
        org.junit.Assert.assertEquals(26, goal.observedPointsCount)
        org.junit.Assert.assertTrue(goal.recommendationRu.contains("Сбор данных за сегодня"))
        org.junit.Assert.assertTrue(goal.recommendationRu.contains("требуется ≥6 ч"))
    }

    @org.junit.Test
    fun testDailyCompensatorLastChanceSlack() {
        val ranges = com.tirup.app.domain.model.TargetRanges()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 19)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val refTime = calendar.timeInMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        val startOfDay = calendar.timeInMillis

        // Readings from 00:00 to 19:00 every 5 minutes (228 readings)
        // 154 points in range (~67% of elapsed day), last points out of range (11.5)
        val readings = (0..228).map { i ->
            val t = startOfDay + i * 5 * 60_000L
            com.tirup.app.domain.model.GlucoseReading(
                timestamp = t,
                valueMmol = if (i < 154) 5.5 else 11.5
            )
        }
        val latest = readings.last()

        val goal = TargetCompensatorCalculator.calculateDailyCompensator(
            targetMode = TargetMode.TIR,
            targetPercent = 70.0,
            latestReading = latest,
            recentReadings = readings,
            targetRanges = ranges,
            language = "RU",
            referenceTimestamp = refTime
        )

        org.junit.Assert.assertFalse(goal.isCurrentlyInRange)
        org.junit.Assert.assertTrue(goal.neededMinutesToday > 0)
        org.junit.Assert.assertTrue(goal.neededMinutesToday <= goal.remainingMinutesToday)
        val slack = goal.remainingMinutesToday - goal.neededMinutesToday
        org.junit.Assert.assertTrue("Slack should be non-negative", slack >= 0)
        org.junit.Assert.assertTrue("Slack should be critical <= 65 min", slack <= 65)
    }
}
