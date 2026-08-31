package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.CompensatorGoal
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.TargetMode

object TargetCompensatorCalculator {

    /**
     * Calculates the required TIR/TING percentage needed in the remaining days to hit the overall period target.
     * Formula: T_needed = (T_target * D_total - T_past * D_past) / D_rem
     */
    fun calculateCompensator(
        targetMode: TargetMode,
        targetPercent: Double,
        totalDays: Int,
        pastDays: Int,
        pastAveragePercent: Double
    ): CompensatorGoal {
        val safeTotalDays = totalDays.coerceAtLeast(1)
        val safePastDays = pastDays.coerceIn(0, safeTotalDays)
        val remainingDays = (safeTotalDays - safePastDays).coerceAtLeast(0)

        if (remainingDays == 0) {
            val status = if (pastAveragePercent >= targetPercent) {
                CompensatorStatus.EXCEEDING
            } else {
                CompensatorStatus.UNREALISTIC
            }
            return CompensatorGoal(
                targetMode = targetMode,
                targetGoalPercent = targetPercent,
                totalDays = safeTotalDays,
                pastDays = safePastDays,
                remainingDays = 0,
                pastAveragePercent = pastAveragePercent,
                neededRemainingPercent = pastAveragePercent,
                status = status
            )
        }

        if (safePastDays == 0) {
            return CompensatorGoal(
                targetMode = targetMode,
                targetGoalPercent = targetPercent,
                totalDays = safeTotalDays,
                pastDays = 0,
                remainingDays = safeTotalDays,
                pastAveragePercent = 0.0,
                neededRemainingPercent = targetPercent,
                status = CompensatorStatus.REACHABLE
            )
        }

        // T_needed = (T_target * D_total - T_past * D_past) / D_rem
        val needed = ((targetPercent * safeTotalDays) - (pastAveragePercent * safePastDays)) / remainingDays

        val status = when {
            needed > 100.0 -> CompensatorStatus.UNREALISTIC
            pastAveragePercent >= targetPercent && needed <= targetPercent -> CompensatorStatus.EXCEEDING
            needed < 0.0 -> CompensatorStatus.EXCEEDING
            else -> CompensatorStatus.REACHABLE
        }

        return CompensatorGoal(
            targetMode = targetMode,
            targetGoalPercent = targetPercent,
            totalDays = safeTotalDays,
            pastDays = safePastDays,
            remainingDays = remainingDays,
            pastAveragePercent = pastAveragePercent,
            neededRemainingPercent = needed.coerceAtLeast(0.0),
            status = status
        )
    }
}
