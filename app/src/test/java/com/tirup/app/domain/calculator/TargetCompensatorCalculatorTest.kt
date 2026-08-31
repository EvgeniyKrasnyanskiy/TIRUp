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
}
