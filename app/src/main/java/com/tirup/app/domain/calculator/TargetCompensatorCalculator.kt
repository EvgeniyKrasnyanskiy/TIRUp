package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.CompensatorGoal
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object TargetCompensatorCalculator {

    fun formatHoursMins(minutes: Int, isRu: Boolean): String {
        val safeMin = minutes.coerceAtLeast(0)
        val h = safeMin / 60
        val m = safeMin % 60
        return when {
            h > 0 && m > 0 -> if (isRu) "${h} ч ${m} мин" else "${h}h ${m}m"
            h > 0 -> if (isRu) "${h} ч" else "${h}h"
            else -> if (isRu) "${m} мин" else "${m}m"
        }
    }

    /**
     * Calculates the real-time daily 24-hour goal compensator.
     * Evaluates in-range and out-of-range minutes today, remaining day window,
     * whether current glucose is in range, and generates accurate clinical recommendations.
     */
    fun calculateDailyCompensator(
        targetMode: TargetMode,
        targetPercent: Double,
        latestReading: GlucoseReading?,
        recentReadings: List<GlucoseReading>,
        targetRanges: TargetRanges,
        language: String = "RU",
        referenceTimestamp: Long = System.currentTimeMillis()
    ): CompensatorGoal {
        val isRu = language.equals("RU", ignoreCase = true)
        val targetName = targetMode.name

        // 1. Calendar Day Elapsed & Remaining Minutes
        val calendar = Calendar.getInstance().apply {
            timeInMillis = referenceTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val elapsedTodayMillis = (referenceTimestamp - startOfDay).coerceAtLeast(0L)
        val elapsedMinutesToday = (elapsedTodayMillis / 60000L).toInt().coerceIn(1, 1440)
        val remainingMinutesToday = (1440 - elapsedMinutesToday).coerceIn(0, 1440)

        // 2. Select readings for today (or fallback to recent 24h if early morning)
        val todayReadings = recentReadings.filter { it.timestamp >= startOfDay }
        val effectiveReadings = if (todayReadings.size >= 6) todayReadings else recentReadings

        // 3. Determine in-range proportion
        val inRangeCheck: (GlucoseReading) -> Boolean = { reading ->
            if (targetMode == TargetMode.TIR) {
                targetRanges.isInTir(reading.valueMmol)
            } else {
                targetRanges.isInTing(reading.valueMmol)
            }
        }

        val inCount = effectiveReadings.count(inRangeCheck)
        val totalCount = effectiveReadings.size.coerceAtLeast(1)
        val currentTirFraction = inCount.toDouble() / totalCount.toDouble()
        val currentScore = currentTirFraction * 100.0

        // In-range & Out-of-range minutes today
        val inRangeMinutes = (currentTirFraction * elapsedMinutesToday).roundToInt().coerceIn(0, elapsedMinutesToday)
        val outOfRangeMinutes = elapsedMinutesToday - inRangeMinutes

        // Target thresholds for the full 24 hours (1440 minutes)
        val targetGoalMinutes = ((targetPercent / 100.0) * 1440.0).roundToInt()
        val allowedOutMinutes = 1440 - targetGoalMinutes

        // Needed in-range minutes to reach the daily target
        val neededMinutesToday = (targetGoalMinutes - inRangeMinutes).coerceAtLeast(0)

        // Max possible TIR if in-range 100% of remaining time
        val maxPossibleTir = ((inRangeMinutes + remainingMinutesToday).toDouble() / 1440.0 * 100.0).coerceIn(0.0, 100.0)

        // Current status of latest reading
        val isCurrentlyInRange = latestReading?.let(inRangeCheck) ?: true

        val status: CompensatorStatus
        val recRu: String
        val recEn: String

        when {
            // Scenario 3: Target already guaranteed / exceeded (> targetGoalMinutes)
            inRangeMinutes >= targetGoalMinutes || neededMinutesToday == 0 -> {
                status = CompensatorStatus.EXCEEDING
                val inStr = formatHoursMins(inRangeMinutes, true)
                val targetStr = formatHoursMins(targetGoalMinutes, true)
                recRu = "Суточная цель выполнена! В норме уже $inStr (норма ≥$targetStr). Отличная компенсация!"

                val inStrEn = formatHoursMins(inRangeMinutes, false)
                val targetStrEn = formatHoursMins(targetGoalMinutes, false)
                recEn = "Daily target achieved! Already $inStrEn in range (goal ≥$targetStrEn). Great control!"
            }

            // Scenario 2: Unrealistic to reach targetPercent today (out-of-range time limit exceeded)
            neededMinutesToday > remainingMinutesToday -> {
                status = CompensatorStatus.UNREALISTIC
                val maxTirStr = String.format(Locale.US, "%.0f%%", maxPossibleTir)
                recRu = "Лимит времени вне нормы исчерпан. Постарайтесь вернуться в диапазон сейчас, чтобы завершить сутки с максимальным $targetName (до $maxTirStr)."
                recEn = "Time out of range exceeded limit. Return to range now to finish the day with highest $targetName (up to $maxTirStr)."
            }

            // Scenario 1A: Out of range right now -> urgent actionable recommendation
            !isCurrentlyInRange -> {
                status = CompensatorStatus.REACHABLE
                val needStrRu = formatHoursMins(neededMinutesToday, true)
                val targetPctInt = targetPercent.toInt()
                recRu = "Сахар вне диапазона. Вернитесь в норму как можно скорее и удерживайте не менее $needStrRu до конца суток для цели ≥$targetPctInt%."

                val needStrEn = formatHoursMins(neededMinutesToday, false)
                recEn = "Glucose is out of range. Return to range ASAP and maintain for at least $needStrEn until end of day for ≥$targetPctInt% goal."
            }

            // Scenario 1B: In range right now -> encouraging pace recommendation
            else -> {
                status = if (currentScore >= targetPercent) CompensatorStatus.EXCEEDING else CompensatorStatus.REACHABLE
                val needStrRu = formatHoursMins(neededMinutesToday, true)
                val currentScoreStr = String.format(Locale.US, "%.0f%%", currentScore)
                val targetPctInt = targetPercent.toInt()
                recRu = "Отличный темп ($targetName $currentScoreStr)! Удерживайте сахар в диапазоне ещё не менее $needStrRu, чтобы зафиксировать цель ≥$targetPctInt%."

                val needStrEn = formatHoursMins(neededMinutesToday, false)
                recEn = "Great pace ($targetName $currentScoreStr)! Keep glucose in range for at least $needStrEn to secure ≥$targetPctInt% daily goal."
            }
        }

        return CompensatorGoal(
            targetMode = targetMode,
            targetGoalPercent = targetPercent,
            totalDays = 1,
            pastDays = 1,
            remainingDays = 0,
            pastAveragePercent = currentScore,
            neededRemainingPercent = targetPercent,
            status = status,
            currentScore = currentScore,
            inRangeMinutes = inRangeMinutes,
            outOfRangeMinutes = outOfRangeMinutes,
            targetGoalMinutes = targetGoalMinutes,
            allowedOutMinutes = allowedOutMinutes,
            remainingMinutesToday = remainingMinutesToday,
            neededMinutesToday = neededMinutesToday,
            maxPossibleTir = maxPossibleTir,
            isCurrentlyInRange = isCurrentlyInRange,
            recommendationRu = recRu,
            recommendationEn = recEn
        )
    }

    /**
     * Legacy multi-day compensator calculation (preserved for backward compatibility and tests).
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
