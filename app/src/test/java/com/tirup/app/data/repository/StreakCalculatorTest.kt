package com.tirup.app.data.repository

import com.tirup.app.data.local.entity.DailySummaryEntity
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.withUpdatedBestStreak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreakCalculatorTest {

    private fun makeDaySummary(dayOffsetFromToday: Int, tir: Double, count: Int = 288, baseNow: Long): DailySummaryEntity {
        val todayStart = GlucoseRepositoryImpl.getStartOfDay(baseNow)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = todayStart
        cal.add(Calendar.DAY_OF_YEAR, dayOffsetFromToday)
        val dayTimestamp = cal.timeInMillis

        return DailySummaryEntity(
            dateTimestamp = dayTimestamp,
            mean = 6.5,
            tir = tir,
            ting = 50.0,
            tbrVeryLow = 0.0,
            tbrLow = 1.0,
            tarHigh = 10.0,
            tarVeryHigh = 0.0,
            sd = 1.2,
            cv = 25.0,
            count = count
        )
    }

    @Test
    fun testTodayNightDropDoesNotBreakYesterdayStreak() {
        // Current time: 02:00 AM
        val now = 1757200800000L

        // Yesterday was good (TIR 80%), Day -2 was good (TIR 85%)
        val dayMinus2 = makeDaySummary(-2, tir = 85.0, baseNow = now)
        val yesterday = makeDaySummary(-1, tir = 80.0, baseNow = now)
        // Today at 02:00 AM has high sugar (TIR 45%, 24 points)
        val today = makeDaySummary(0, tir = 45.0, count = 24, baseNow = now)

        val summaries = listOf(today, yesterday, dayMinus2)

        val streak = GlucoseRepositoryImpl.calculateStreakDays(summaries, nowTimestamp = now)

        // Streak must preserve completed 2 days and NOT reset to 0!
        assertEquals(2, streak)
    }

    @Test
    fun testYesterdayFailedResetsCompletedStreak() {
        val now = 1757200800000L

        // Day -2 was good (85%), but yesterday evening spiked (TIR 58%)
        val dayMinus2 = makeDaySummary(-2, tir = 85.0, baseNow = now)
        val yesterday = makeDaySummary(-1, tir = 58.0, baseNow = now)
        val today = makeDaySummary(0, tir = 45.0, count = 24, baseNow = now)

        val summaries = listOf(today, yesterday, dayMinus2)

        val streak = GlucoseRepositoryImpl.calculateStreakDays(summaries, nowTimestamp = now)

        // Yesterday failed, today also out of range -> streak is 0
        assertEquals(0, streak)
    }

    @Test
    fun testTodayGoodAddsBonusOnTopOfCompletedStreak() {
        val now = 1757200800000L

        val yesterday = makeDaySummary(-1, tir = 78.0, baseNow = now)
        val today = makeDaySummary(0, tir = 82.0, count = 100, baseNow = now)

        val summaries = listOf(today, yesterday)

        val streak = GlucoseRepositoryImpl.calculateStreakDays(summaries, nowTimestamp = now)

        // 1 completed day + 1 today bonus = 2 days
        assertEquals(2, streak)
    }

    @Test
    fun testEarlyMorningUnder10PointsPreservesStreak() {
        val now = 1757200800000L

        val yesterday = makeDaySummary(-1, tir = 75.0, baseNow = now)
        // Only 4 points since midnight, e.g. at 00:20
        val today = makeDaySummary(0, tir = 100.0, count = 4, baseNow = now)

        val summaries = listOf(today, yesterday)

        val streak = GlucoseRepositoryImpl.calculateStreakDays(summaries, nowTimestamp = now)

        // Does not drop to 0 during early morning
        assertEquals(1, streak)
    }

    @Test
    fun testMissingDayBreaksStreak() {
        val now = 1757200800000L

        // Day -2 was good, but Day -1 (yesterday) was completely missing
        val dayMinus2 = makeDaySummary(-2, tir = 85.0, baseNow = now)
        val today = makeDaySummary(0, tir = 45.0, count = 24, baseNow = now)

        val summaries = listOf(today, dayMinus2)

        val streak = GlucoseRepositoryImpl.calculateStreakDays(summaries, nowTimestamp = now)

        // Missing yesterday breaks the streak
        assertEquals(0, streak)
    }

    @Test
    fun testBestStreakUpdatesOnlyOnImprovement() {
        val baseline = UserSettings(bestStreakDays = 3)

        val improved = baseline.withUpdatedBestStreak(5)
        val unchanged = baseline.withUpdatedBestStreak(2)

        assertEquals(5, improved.bestStreakDays)
        assertEquals(3, unchanged.bestStreakDays)
    }

    @Test
    fun testCalculateMaxHistoricalStreakFindsLongestChain() {
        val now = 1757200800000L

        // Chain 1: Days -10, -9, -8, -7 (4 days)
        val d10 = makeDaySummary(-10, tir = 75.0, baseNow = now)
        val d9 = makeDaySummary(-9, tir = 80.0, baseNow = now)
        val d8 = makeDaySummary(-8, tir = 85.0, baseNow = now)
        val d7 = makeDaySummary(-7, tir = 90.0, baseNow = now)

        // Day -6 was bad (60%)
        val d6 = makeDaySummary(-6, tir = 60.0, baseNow = now)

        // Chain 2: Days -5, -4, -3, -2, -1, 0 (6 days)
        val d5 = makeDaySummary(-5, tir = 72.0, baseNow = now)
        val d4 = makeDaySummary(-4, tir = 78.0, baseNow = now)
        val d3 = makeDaySummary(-3, tir = 82.0, baseNow = now)
        val d2 = makeDaySummary(-2, tir = 88.0, baseNow = now)
        val d1 = makeDaySummary(-1, tir = 91.0, baseNow = now)
        val d0 = makeDaySummary(0, tir = 74.0, baseNow = now)

        val list = listOf(d10, d9, d8, d7, d6, d5, d4, d3, d2, d1, d0)
        val maxStreak = GlucoseRepositoryImpl.calculateMaxHistoricalStreak(list)

        assertEquals(6, maxStreak)
    }

    @Test
    fun testCalculateMaxHistoricalStreakIgnoresDaysUnder70OrUnder10Readings() {
        val now = 1757200800000L

        val d3 = makeDaySummary(-3, tir = 80.0, count = 288, baseNow = now)
        val d2 = makeDaySummary(-2, tir = 68.0, count = 288, baseNow = now) // Near target, but <70%
        val d1 = makeDaySummary(-1, tir = 85.0, count = 5, baseNow = now)   // <10 readings
        val d0 = makeDaySummary(0, tir = 85.0, count = 288, baseNow = now)

        val list = listOf(d3, d2, d1, d0)
        val maxStreak = GlucoseRepositoryImpl.calculateMaxHistoricalStreak(list)

        assertEquals(1, maxStreak)
    }
}
