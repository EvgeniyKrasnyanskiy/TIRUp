package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.ClinicalSummary
import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.NightStability
import com.tirup.app.domain.model.TargetRanges
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object GlucoseMetricsCalculator {

    private const val MGDL_FACTOR = 18.0182

    /**
     * Calculates complete clinical statistics from a list of glucose readings,
     * matching xDrip+ and DiaKiaBot algorithms:
     * Mean, Median, SD, %CV, GMI, IFCC HbA1c, TIR, TING, TBR, TAR, GVI, PGS, GRI (Klonoff 2022),
     * Night Stability, and Automated Doctor's Clinical Summary.
     */
    fun calculateStatistics(
        readings: List<GlucoseReading>,
        targetRanges: TargetRanges = TargetRanges(),
        nightStartHour: Int = 0,
        nightEndHour: Int = 6
    ): GlucoseStatistics {
        if (readings.isEmpty()) {
            return GlucoseStatistics()
        }

        val sortedReadings = readings.sortedBy { it.timestamp }
        val totalCount = sortedReadings.size
        val sum = sortedReadings.sumOf { it.valueMmol }
        val mean = sum / totalCount

        // Median
        val sortedValues = sortedReadings.map { it.valueMmol }.sorted()
        val median = if (totalCount % 2 == 0) {
            (sortedValues[totalCount / 2 - 1] + sortedValues[totalCount / 2]) / 2.0
        } else {
            sortedValues[totalCount / 2]
        }

        val variance = if (totalCount > 1) {
            sortedReadings.sumOf { (it.valueMmol - mean) * (it.valueMmol - mean) } / (totalCount - 1)
        } else {
            0.0
        }
        val sd = sqrt(variance)

        // %CV = (SD / Mean) * 100%
        val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0

        // GMI = 3.31 + (0.431 * Mean Glucose in mmol/L)
        val gmi = calculateGmi(mean)
        // IFCC mmol/mol: (HbA1c% - 2.15) * 10.929
        val hba1cMmolMol = ((gmi - 2.15) * 10.929).roundToInt().coerceAtLeast(0)

        var veryLowCount = 0
        var lowCount = 0
        var tirCount = 0
        var tingCount = 0
        var highCount = 0
        var veryHighCount = 0

        val nightReadings = mutableListOf<GlucoseReading>()
        val calendar = Calendar.getInstance(TimeZone.getDefault())

        sortedReadings.forEach { reading ->
            val v = reading.valueMmol
            when (targetRanges.categorize(v)) {
                GlucoseRangeCategory.VERY_LOW -> veryLowCount++
                GlucoseRangeCategory.LOW -> lowCount++
                GlucoseRangeCategory.TIGHT -> {
                    tingCount++
                    tirCount++
                }
                GlucoseRangeCategory.TARGET -> tirCount++
                GlucoseRangeCategory.HIGH -> highCount++
                GlucoseRangeCategory.VERY_HIGH -> veryHighCount++
            }

            calendar.timeInMillis = reading.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (isNightHour(hour, nightStartHour, nightEndHour)) {
                nightReadings.add(reading)
            }
        }

        val tbrVeryLowPercent = (veryLowCount.toDouble() / totalCount) * 100.0
        val tbrLowPercent = (lowCount.toDouble() / totalCount) * 100.0
        val tirPercent = (tirCount.toDouble() / totalCount) * 100.0
        val tingPercent = (tingCount.toDouble() / totalCount) * 100.0
        val tarHighPercent = (highCount.toDouble() / totalCount) * 100.0
        val tarVeryHighPercent = (veryHighCount.toDouble() / totalCount) * 100.0

        // Distinct days
        val distinctDays = sortedReadings.map {
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }.distinct().size

        val nightStability = calculateNightStability(nightReadings, targetRanges)

        // GVI calculation (xDrip step-by-step algorithm in mg/dL)
        val valuesMgdl = sortedReadings.map { it.valueMmol * MGDL_FACTOR }
        val gvi = computeGvi(valuesMgdl)

        // PGS calculation: GVI * floor(mean_mgdl) * (1 - TIR/100)
        val meanMgdl = mean * MGDL_FACTOR
        val pgs = gvi * floor(meanMgdl) * (1.0 - (tirPercent / 100.0))
        val pgsTruncated = (pgs * 10.0).roundToInt() / 10.0

        // GRI calculation (Glycemia Risk Index - Klonoff et al. 2022)
        val hypoComponent = tbrVeryLowPercent + (0.8 * tbrLowPercent)
        val hyperComponent = tarVeryHighPercent + (0.5 * tarHighPercent)
        val rawGri = (3.0 * hypoComponent) + (1.6 * hyperComponent)
        val gri = min((rawGri * 10.0).roundToInt() / 10.0, 100.0)

        val (griZone, griLabel) = when {
            gri <= 20.0 -> Pair(1, "Очень низкий риск")
            gri <= 40.0 -> Pair(2, "Низкий риск")
            gri <= 60.0 -> Pair(3, "Средний риск")
            gri <= 80.0 -> Pair(4, "Высокий риск")
            else -> Pair(5, "Очень высокий риск")
        }

        // Active Time (Total duration minus gaps > 20 minutes)
        var totalGapDurationMs = 0L
        val gapThresholdMs = 20 * 60 * 1000L // 20 min
        for (i in 1 until sortedReadings.size) {
            val diff = sortedReadings[i].timestamp - sortedReadings[i - 1].timestamp
            if (diff > gapThresholdMs) {
                totalGapDurationMs += diff
            }
        }
        val totalDurationMs = if (sortedReadings.size > 1) {
            sortedReadings.last().timestamp - sortedReadings.first().timestamp
        } else 0L

        val activeTimePercent = if (totalDurationMs > 0L) {
            val activeMs = (totalDurationMs - totalGapDurationMs).coerceAtLeast(0L)
            val pct = (activeMs.toDouble() / totalDurationMs.toDouble()) * 100.0
            (pct * 10.0).roundToInt() / 10.0
        } else {
            100.0
        }

        // Automated Doctor's Clinical Summary
        val clinicalSummary = generateClinicalSummary(
            meanMmol = mean,
            gmi = gmi,
            tirPercent = tirPercent,
            tingPercent = tingPercent,
            tbrLowTotal = tbrLowPercent + tbrVeryLowPercent,
            tbrVeryLow = tbrVeryLowPercent,
            tarVeryHigh = tarVeryHighPercent,
            cvPercent = cv,
            gvi = gvi,
            pgs = pgsTruncated,
            gri = gri
        )

        return GlucoseStatistics(
            meanMmol = mean,
            medianMmol = median,
            sdMmol = sd,
            cvPercent = cv,
            gmiPercent = gmi,
            hba1cMmolMol = hba1cMmolMol,
            tirPercent = tirPercent,
            tingPercent = tingPercent,
            tbrVeryLowPercent = tbrVeryLowPercent,
            tbrLowPercent = tbrLowPercent,
            tarHighPercent = tarHighPercent,
            tarVeryHighPercent = tarVeryHighPercent,
            gvi = gvi,
            pgs = pgsTruncated,
            gri = gri,
            griZone = griZone,
            griLabel = griLabel,
            activeTimePercent = activeTimePercent,
            totalCount = totalCount,
            daysCount = distinctDays.coerceAtLeast(1),
            nightStability = nightStability,
            clinicalSummary = clinicalSummary
        )
    }

    /**
     * xDrip exact GVI algorithm.
     */
    private fun computeGvi(valuesMgdl: List<Double>): Double {
        if (valuesMgdl.size < 2) return 1.0

        val first = valuesMgdl.first()
        var last = first
        var totalLength = 0.0
        var count = 1

        for (i in 1 until valuesMgdl.size) {
            val curr = valuesMgdl[i]
            val delta = curr - last
            totalLength += sqrt(25.0 + (delta * delta))
            last = curr
            count++
        }

        val totalDelta = abs(last - first)
        val timeComponent = (count * 5).toDouble()
        val idealLength = sqrt((timeComponent * timeComponent) + (totalDelta * totalDelta))

        if (idealLength == 0.0) return 1.0

        val ratio = totalLength / idealLength
        return ((ratio * 100.0).roundToInt()) / 100.0
    }

    private fun generateClinicalSummary(
        meanMmol: Double,
        gmi: Double,
        tirPercent: Double,
        tingPercent: Double,
        tbrLowTotal: Double,
        tbrVeryLow: Double,
        tarVeryHigh: Double,
        cvPercent: Double,
        gvi: Double,
        pgs: Double,
        gri: Double
    ): ClinicalSummary {
        val hyperIssues = mutableListOf<String>()
        val hypoIssues = mutableListOf<String>()
        val varIssues = mutableListOf<String>()
        val rangeIssues = mutableListOf<String>()

        if (meanMmol > 7.8) hyperIssues.add("Средний сахар повышен: ${String.format("%.1f", meanMmol)} ммоль/л (цель ≤7.8)")
        if (gmi > 7.0) hyperIssues.add("Расчётный eA1c: ${String.format("%.1f%%", gmi)} (цель ≤7.0%)")
        if (tarVeryHigh > 5.0) hyperIssues.add("Эпизоды выраженной гипергликемии (≥14.0 ммоль/л): ${String.format("%.1f%%", tarVeryHigh)}")

        if (tbrVeryLow > 1.0) hypoIssues.add("Опасные тяжёлые гипогликемии (<3.0 ммоль/л): ${String.format("%.1f%%", tbrVeryLow)} (норма <1%)")
        if (tbrLowTotal > 4.0) hypoIssues.add("Повышенный риск гипогликемий (<3.9 ммоль/л): ${String.format("%.1f%%", tbrLowTotal)} (норма <4%)")

        if (cvPercent >= 36.0) varIssues.add("Вариабельность глюкозы (%CV): ${String.format("%.1f%%", cvPercent)} (цель ≤36.0%)")
        if (gvi > 1.20) varIssues.add("Индекс лабильности GVI: ${String.format("%.2f", gvi)} (цель ≤1.20)")

        if (tirPercent < 70.0) rangeIssues.add("TIR в диапазоне: ${String.format("%.1f%%", tirPercent)} (цель ≥70%)")
        if (tingPercent < 50.0) rangeIssues.add("TING в узком диапазоне: ${String.format("%.1f%%", tingPercent)} (цель ≥50%)")

        val totalIssuesCount = hyperIssues.size + hypoIssues.size + varIssues.size + rangeIssues.size

        val (status, isAllMet, rec) = when {
            totalIssuesCount == 0 -> Triple(
                "Все цели достигнуты. Отличный контроль гликемии!",
                true,
                "Поддерживайте текущий режим питания и терапии."
            )
            totalIssuesCount <= 2 -> Triple(
                "Есть небольшие отклонения от целевых диапазонов.",
                false,
                "Рекомендуется обратить внимание на отмеченные параметры и обсудить с лечащим врачом."
            )
            totalIssuesCount <= 4 -> Triple(
                "Контроль гликемии нестабильный. Требуется внимание.",
                false,
                "Рекомендуется консультация с врачом для возможной корректировки коэффициентов или доз."
            )
            else -> Triple(
                "Имеются выраженные отклонения гликемического профиля.",
                false,
                "Рекомендуется очная консультация эндокринолога для пересмотра схемы инсулинотерапии!"
            )
        }

        return ClinicalSummary(
            overallStatus = status,
            isAllTargetsMet = isAllMet,
            hyperIssues = hyperIssues,
            hypoIssues = hypoIssues,
            variabilityIssues = varIssues,
            rangeIssues = rangeIssues,
            recommendation = rec
        )
    }

    private fun isNightHour(hour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }

    /**
     * Glucose Management Indicator formula: GMI = 3.31 + (0.431 * Mean Glucose in mmol/L)
     */
    fun calculateGmi(meanMmol: Double): Double {
        if (meanMmol <= 0.0) return 0.0
        return 3.31 + (0.431 * meanMmol)
    }

    /**
     * Night stability metrics.
     */
    private fun calculateNightStability(
        nightReadings: List<GlucoseReading>,
        targetRanges: TargetRanges
    ): NightStability {
        if (nightReadings.isEmpty()) {
            return NightStability()
        }

        val count = nightReadings.size
        val sum = nightReadings.sumOf { it.valueMmol }
        val mean = sum / count
        val variance = if (count > 1) {
            nightReadings.sumOf { (it.valueMmol - mean) * (it.valueMmol - mean) } / (count - 1)
        } else {
            0.0
        }
        val sd = sqrt(variance)
        val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0

        var inRangeCount = 0
        nightReadings.forEach { r ->
            val cat = targetRanges.categorize(r.valueMmol)
            if (cat == GlucoseRangeCategory.TARGET || cat == GlucoseRangeCategory.TIGHT) {
                inRangeCount++
            }
        }

        val tir = (inRangeCount.toDouble() / count) * 100.0
        val isStable = sd <= 1.5 && tir >= 70.0

        return NightStability(
            isStable = isStable,
            meanMmol = mean,
            sdMmol = sd,
            cvPercent = cv,
            tirPercent = tir,
            nightReadingsCount = count
        )
    }
}
