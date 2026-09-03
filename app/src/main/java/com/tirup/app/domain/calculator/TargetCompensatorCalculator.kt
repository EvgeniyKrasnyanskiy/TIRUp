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

        // 2. Select readings for today (or fallback to recent 24h only if no readings today yet)
        val todayReadings = recentReadings.filter { it.timestamp >= startOfDay }
        val effectiveReadings = if (todayReadings.isNotEmpty()) todayReadings else recentReadings

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
                recRu = "Суточная цель выполнена досрочно! В норме уже $inStr (норма ≥$targetStr). Отличный результат!"

                val inStrEn = formatHoursMins(inRangeMinutes, false)
                val targetStrEn = formatHoursMins(targetGoalMinutes, false)
                recEn = "Daily target achieved! Already $inStrEn in range (goal ≥$targetStrEn). Great result!"
            }

            // Scenario 2: Unrealistic to reach targetPercent today (out-of-range time limit exceeded)
            neededMinutesToday > remainingMinutesToday -> {
                status = CompensatorStatus.UNREALISTIC
                val maxTirStr = String.format(Locale.US, "%.0f%%", maxPossibleTir)
                recRu = "Лимит времени вне нормы исчерпан (макс. $targetName за сегодня: $maxTirStr). Удерживайте диапазон до полуночи, чтобы завершить день с лучшим счётом."
                recEn = "Out of range limit exceeded (max $targetName today: $maxTirStr). Keep in range until midnight to finish the day with highest score."
            }

            // Scenario 1A: Out of range right now -> urgent actionable recommendation
            !isCurrentlyInRange -> {
                status = CompensatorStatus.REACHABLE
                val needStrRu = formatHoursMins(neededMinutesToday, true)
                val remainStrRu = formatHoursMins(remainingMinutesToday, true)
                val targetPctInt = targetPercent.toInt()
                recRu = "Сахар вне диапазона. Вернитесь в норму: из оставшихся $remainStrRu суток удержите ещё не менее $needStrRu для цели ≥$targetPctInt%."

                val needStrEn = formatHoursMins(neededMinutesToday, false)
                val remainStrEn = formatHoursMins(remainingMinutesToday, false)
                recEn = "Glucose is out of range. Return to target: out of remaining $remainStrEn, keep at least $needStrEn for ≥$targetPctInt% goal."
            }

            // Scenario 1B: In range right now -> encouraging pace recommendation
            else -> {
                status = if (currentScore >= targetPercent) CompensatorStatus.EXCEEDING else CompensatorStatus.REACHABLE
                val needStrRu = formatHoursMins(neededMinutesToday, true)
                val remainStrRu = formatHoursMins(remainingMinutesToday, true)
                val targetPctInt = targetPercent.toInt()
                recRu = "В норме. Из оставшихся $remainStrRu суток удерживайте диапазон ещё не менее $needStrRu для выполнения цели ≥$targetPctInt%."

                val needStrEn = formatHoursMins(neededMinutesToday, false)
                val remainStrEn = formatHoursMins(remainingMinutesToday, false)
                recEn = "In range. Out of remaining $remainStrEn today, keep at least $needStrEn to secure ≥$targetPctInt% goal."
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
     * Calculates multi-day strategic compensator for Trends screen.
     * Evaluates successful days in period, hours balance (surplus/deficit), and recommended daily target for next period.
     */
    fun calculateStrategicCompensator(
        targetMode: TargetMode,
        targetGoalPercent: Double,
        readings: List<GlucoseReading>,
        periodDays: Int,
        targetRanges: TargetRanges,
        language: String = "RU"
    ): CompensatorGoal {
        val isRu = language.equals("RU", ignoreCase = true)
        val targetName = targetMode.name

        if (readings.isEmpty()) {
            return CompensatorGoal(
                targetMode = targetMode,
                targetGoalPercent = targetGoalPercent,
                recommendationRu = "Недостаточно данных за выбранный период для анализа тренда.",
                recommendationEn = "Insufficient data for selected period to analyze trend."
            )
        }

        val inRangeCheck: (GlucoseReading) -> Boolean = { reading ->
            if (targetMode == TargetMode.TIR) {
                targetRanges.isInTir(reading.valueMmol)
            } else {
                targetRanges.isInTing(reading.valueMmol)
            }
        }

        // 1. Group readings by calendar day
        val cal = Calendar.getInstance()
        val readingsByDay = readings.groupBy { reading ->
            cal.timeInMillis = reading.timestamp
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        var successfulDays = 0
        var evaluatedDays = 0
        readingsByDay.values.forEach { dayReadings ->
            if (dayReadings.size >= 12) { // At least 1 hour of readings
                evaluatedDays++
                val dayInCount = dayReadings.count(inRangeCheck)
                val dayTir = (dayInCount.toDouble() / dayReadings.size.toDouble()) * 100.0
                if (dayTir >= targetGoalPercent) {
                    successfulDays++
                }
            }
        }

        val totalDaysEvaluated = evaluatedDays.coerceAtLeast(1)
        val totalReadingsCount = readings.size.coerceAtLeast(1)
        val inCount = readings.count(inRangeCheck)
        val overallTir = (inCount.toDouble() / totalReadingsCount.toDouble()) * 100.0

        // 2. Time Balance in Hours (Surplus vs Deficit)
        val totalPeriodHours = totalDaysEvaluated * 24.0
        val targetHours = (targetGoalPercent / 100.0) * totalPeriodHours
        val actualHours = (overallTir / 100.0) * totalPeriodHours
        val balanceHours = actualHours - targetHours

        // 3. Recommended daily target for next period to compensate or maintain
        val neededDailyTirNextPeriod = if (overallTir >= targetGoalPercent) {
            targetGoalPercent
        } else {
            (targetGoalPercent * 2.0 - overallTir).coerceIn(targetGoalPercent, 95.0)
        }
        val neededDailyMinutes = ((neededDailyTirNextPeriod / 100.0) * 1440.0).roundToInt()
        val neededDailyHoursMins = formatHoursMins(neededDailyMinutes, isRu)

        val status: CompensatorStatus
        val recRu: String
        val recEn: String

        if (overallTir >= targetGoalPercent) {
            status = CompensatorStatus.EXCEEDING
            val surplusStr = String.format(Locale.US, "%.1f", balanceHours)
            val surplusPctStr = String.format(Locale.US, "%.1f", overallTir - targetGoalPercent)
            recRu = "Цель $targetName перевыполнена на +$surplusPctStr% (запас: +$surplusStr ч в норме). $successfulDays из $totalDaysEvaluated дн. закрыты успешно. Отличный темп!"
            recEn = "$targetName target exceeded by +$surplusPctStr% (surplus: +$surplusStr h in range). $successfulDays of $totalDaysEvaluated days met target. Excellent!"
        } else {
            val deficitHoursStr = String.format(Locale.US, "%.1f", -balanceHours)
            val neededPctStr = String.format(Locale.US, "%.0f%%", neededDailyTirNextPeriod)
            if (neededDailyTirNextPeriod <= 90.0) {
                status = CompensatorStatus.REACHABLE
                recRu = "Дефицит диапазона: -$deficitHoursStr ч за период ($successfulDays из $totalDaysEvaluated дн. в норме). Чтобы выйти на цель, ориентируйтесь на $targetName ≥$neededPctStr ($neededDailyHoursMins в день)."
                recEn = "Range deficit: -$deficitHoursStr h for period ($successfulDays of $totalDaysEvaluated days in target). Aim for daily $targetName ≥$neededPctStr ($neededDailyHoursMins/day)."
            } else {
                status = CompensatorStatus.UNREALISTIC
                recRu = "Дефицит: -$deficitHoursStr ч ($successfulDays из $totalDaysEvaluated дн. в норме). Потребуется постепенная стабилизация гликемии с врачом."
                recEn = "Deficit: -$deficitHoursStr h ($successfulDays of $totalDaysEvaluated days in target). Gradual stabilization with your clinician recommended."
            }
        }

        return CompensatorGoal(
            targetMode = targetMode,
            targetGoalPercent = targetGoalPercent,
            totalDays = if (periodDays > 0) periodDays else totalDaysEvaluated,
            pastDays = totalDaysEvaluated,
            remainingDays = 0,
            pastAveragePercent = overallTir,
            neededRemainingPercent = neededDailyTirNextPeriod,
            status = status,
            currentScore = overallTir,
            inRangeMinutes = (actualHours * 60.0).roundToInt(),
            outOfRangeMinutes = ((totalPeriodHours - actualHours) * 60.0).roundToInt(),
            targetGoalMinutes = (targetHours * 60.0).roundToInt(),
            allowedOutMinutes = ((totalPeriodHours - targetHours) * 60.0).roundToInt(),
            successfulDaysCount = successfulDays,
            totalDaysWithData = totalDaysEvaluated,
            balanceHours = balanceHours,
            neededDailyTirNextPeriod = neededDailyTirNextPeriod,
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
