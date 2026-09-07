package com.tirup.app.presentation.widget

import com.tirup.app.domain.model.GlucoseReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TirupWidgetUpdaterTest {

    @Test
    fun test1MinuteCadenceFinds5MinuteAgoReading() {
        val now = 10000000L
        // 10 readings, 1 minute apart (timestamps: now - 9m, now - 8m, ..., now)
        // Values: at t=now - 5m value is 5.0, at t=now value is 5.8
        // Neighbor at t=now - 1m value is 5.7
        val readings = (0..9).map { i ->
            val t = now - (9 - i) * 60_000L
            val v = when (i) {
                4 -> 5.0 // t = now - 5m (index 4 out of 0..9)
                8 -> 5.7 // t = now - 1m (adjacent point)
                9 -> 5.8 // t = now (latest)
                else -> 5.0 + i * 0.1
            }
            GlucoseReading(timestamp = t, valueMmol = v)
        }
        val latest = readings.last()

        val delta = TirupWidgetUpdater.calculate5MinDelta(latest, readings)

        // Delta should be latest (5.8) - 5min reading (5.0) = +0.8, NOT 5.8 - 5.7 (+0.1)
        assertEquals(0.8, delta ?: 0.0, 0.001)
    }

    @Test
    fun test5MinuteCadenceUsesPreviousReading() {
        val now = 10000000L
        val reading1 = GlucoseReading(timestamp = now - 5 * 60_000L, valueMmol = 6.2)
        val reading2 = GlucoseReading(timestamp = now, valueMmol = 6.6)
        val readings = listOf(reading1, reading2)

        val delta = TirupWidgetUpdater.calculate5MinDelta(reading2, readings)

        assertEquals(0.4, delta ?: 0.0, 0.001)
    }

    @Test
    fun testSingleReadingReturnsNullDelta() {
        val now = 10000000L
        val reading = GlucoseReading(timestamp = now, valueMmol = 5.5)

        val delta = TirupWidgetUpdater.calculate5MinDelta(reading, listOf(reading))

        assertNull(delta)
    }

    @Test
    fun testFormatCompactTrendArrowConvertsDoubleArrows() {
        assertEquals("⇈", TirupWidgetUpdater.formatCompactTrendArrow("↑↑"))
        assertEquals("⇈", TirupWidgetUpdater.formatCompactTrendArrow("⇈"))
        assertEquals("⇊", TirupWidgetUpdater.formatCompactTrendArrow("↓↓"))
        assertEquals("⇊", TirupWidgetUpdater.formatCompactTrendArrow("⇊"))
        assertEquals("↑", TirupWidgetUpdater.formatCompactTrendArrow("↑"))
        assertEquals("→", TirupWidgetUpdater.formatCompactTrendArrow("→"))
        assertEquals("", TirupWidgetUpdater.formatCompactTrendArrow(null))
    }

    @Test
    fun testCalculateDailyTimeBalanceSurplusAndDeficit() {
        val dummyGoal = com.tirup.app.domain.model.CompensatorGoal(
            targetMode = com.tirup.app.domain.model.TargetMode.TIR,
            targetGoalPercent = 70.0,
            totalDays = 1,
            pastDays = 1,
            remainingDays = 0,
            pastAveragePercent = 80.0,
            neededRemainingPercent = 70.0,
            status = com.tirup.app.domain.model.CompensatorStatus.EXCEEDING,
            currentScore = 80.0,
            inRangeMinutes = 574,
            outOfRangeMinutes = 146,
            targetGoalMinutes = 1008,
            allowedOutMinutes = 432,
            remainingMinutesToday = 720,
            neededMinutesToday = 434,
            maxPossibleTir = 90.0,
            isCurrentlyInRange = true,
            observedPointsCount = 144,
            activeMonitoringMinutes = 720,
            recommendationRu = "",
            recommendationEn = ""
        )

        // 720 min active, 70% expected = 504 min. Actual in-range = 574 min -> +70 min = +1ч 10м
        val (surplusText, surplusColor) = TirupWidgetUpdater.calculateDailyTimeBalance(dummyGoal, 70.0)
        assertEquals("+1ч 10м", surplusText)
        assertEquals(0xFF059669.toInt(), surplusColor) // surplus > 60m -> dark green

        // Deficit: actual 366 min -> 366 - 504 = -138 min = -2ч 18м
        val deficitGoal = dummyGoal.copy(inRangeMinutes = 366)
        val (deficitText, deficitColor) = TirupWidgetUpdater.calculateDailyTimeBalance(deficitGoal, 70.0)
        assertEquals("-2ч 18м", deficitText)
        assertEquals(0xFFEF4444.toInt(), deficitColor) // deficit > 60m -> dark red

        // Mild deficit: 490 min -> 490 - 504 = -14 min = -14м
        val mildDeficitGoal = dummyGoal.copy(inRangeMinutes = 490)
        val (mildDefText, mildDefColor) = TirupWidgetUpdater.calculateDailyTimeBalance(mildDeficitGoal, 70.0)
        assertEquals("-14м", mildDefText)
        assertEquals(0xFFF59E0B.toInt(), mildDefColor) // mild deficit <= 60m -> amber
    }

    @Test
    fun testPrioritizedBadgesWhenAllPresent() {
        val now = System.currentTimeMillis()
        val latest = GlucoseReading(
            timestamp = now - 2 * 60_000L,
            valueMmol = 6.0,
            iob = 1.5
        )
        val settings = com.tirup.app.domain.model.UserSettings(
            bleBridgeSettings = com.tirup.app.domain.model.BleBridgeSettings(
                role = com.tirup.app.domain.model.BleBridgeRole.OBSERVER,
                lastMasterBattery = 85,
                lastPacketTimestamp = now - 60_000L
            )
        )
        val streakDays = 5
        val today = listOf(
            GlucoseReading(timestamp = now - 10 * 60_000L, valueMmol = 5.5),
            latest
        )

        val badges = TirupWidgetUpdater.getPrioritizedBadges(
            latest = latest,
            recent = today,
            todayReadings = today,
            settings = settings,
            streakDays = streakDays
        )

        val types = badges.map { it.type }
        assertEquals("iob", types[0])
        assertEquals("battery", types[1])
        assertEquals("streak", types[2])
        assertEquals("💉 1.5 U", badges[0].text)
        assertEquals("🔋 85%", badges[1].text)
        assertEquals("🔥 5 д.", badges[2].text)
    }

    @Test
    fun testPrioritizedBadgesFallbackWhenOptionalAbsent() {
        val now = System.currentTimeMillis()
        // No IoB, no BLE battery, streak = 0
        val latest = GlucoseReading(
            timestamp = now - 3 * 60_000L,
            valueMmol = 6.0,
            iob = null
        )
        val settings = com.tirup.app.domain.model.UserSettings(
            bleBridgeSettings = com.tirup.app.domain.model.BleBridgeSettings(
                role = com.tirup.app.domain.model.BleBridgeRole.DISABLED
            )
        )
        val streakDays = 0
        val today = listOf(
            GlucoseReading(timestamp = now - 5 * 60_000L, valueMmol = 5.4),
            latest
        )

        val badges = TirupWidgetUpdater.getPrioritizedBadges(
            latest = latest,
            recent = today,
            todayReadings = today,
            settings = settings,
            streakDays = streakDays
        )

        val types = badges.map { it.type }
        // ioB, battery, and streak are absent, so compensator/delta/time_ago/mean populate the top
        org.junit.Assert.assertFalse(types.contains("iob"))
        org.junit.Assert.assertFalse(types.contains("battery"))
        org.junit.Assert.assertFalse(types.contains("streak"))
        org.junit.Assert.assertTrue(types.isNotEmpty())
        assertEquals("compensator", types[0])
    }

    @Test
    fun testPrioritizedBadgesExcludesSpecifiedTypes() {
        val now = System.currentTimeMillis()
        val latest = GlucoseReading(
            timestamp = now - 2 * 60_000L,
            valueMmol = 6.0,
            iob = 2.0
        )
        val settings = com.tirup.app.domain.model.UserSettings()
        val today = listOf(latest)

        val badges = TirupWidgetUpdater.getPrioritizedBadges(
            latest = latest,
            recent = today,
            todayReadings = today,
            settings = settings,
            streakDays = 0,
            excludeTypes = setOf("iob")
        )

        val types = badges.map { it.type }
        org.junit.Assert.assertFalse(types.contains("iob"))
    }
}
