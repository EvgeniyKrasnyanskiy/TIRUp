package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.NightStability
import com.tirup.app.domain.model.TargetRanges
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.sqrt

object GlucoseMetricsCalculator {

    /**
     * Calculates clinical statistics from a list of glucose readings.
     * Mean, SD, %CV (<=36% target), GMI (3.31 + 0.431 * Mean), TIR, TING, TBR, TAR, and Night Stability.
     */
    fun calculateStatistics(
        readings: List<GlucoseReading>,
        targetRanges: TargetRanges = TargetRanges()
    ): GlucoseStatistics {
        if (readings.isEmpty()) {
            return GlucoseStatistics()
        }

        val totalCount = readings.size
        val sum = readings.sumOf { it.valueMmol }
        val mean = sum / totalCount

        val variance = if (totalCount > 1) {
            readings.sumOf { (it.valueMmol - mean) * (it.valueMmol - mean) } / (totalCount - 1)
        } else {
            0.0
        }
        val sd = sqrt(variance)

        // %CV = (SD / Mean) * 100%
        val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0

        // GMI = 3.31 + (0.431 * Mean Glucose in mmol/L)
        val gmi = calculateGmi(mean)

        var veryLowCount = 0
        var lowCount = 0
        var tirCount = 0
        var tingCount = 0
        var highCount = 0
        var veryHighCount = 0

        // Night readings (00:00 - 05:59)
        val nightReadings = mutableListOf<GlucoseReading>()
        val calendar = Calendar.getInstance(TimeZone.getDefault())

        readings.forEach { reading ->
            val v = reading.valueMmol
            when (targetRanges.categorize(v)) {
                GlucoseRangeCategory.VERY_LOW -> {
                    veryLowCount++
                }
                GlucoseRangeCategory.LOW -> {
                    lowCount++
                }
                GlucoseRangeCategory.TIGHT -> {
                    tingCount++
                    tirCount++
                }
                GlucoseRangeCategory.TARGET -> {
                    tirCount++
                }
                GlucoseRangeCategory.HIGH -> {
                    highCount++
                }
                GlucoseRangeCategory.VERY_HIGH -> {
                    veryHighCount++
                }
            }

            calendar.timeInMillis = reading.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..5) {
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
        val distinctDays = readings.map {
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }.distinct().size

        val nightStability = calculateNightStability(nightReadings, targetRanges)

        return GlucoseStatistics(
            meanMmol = mean,
            sdMmol = sd,
            cvPercent = cv,
            gmiPercent = gmi,
            tirPercent = tirPercent,
            tingPercent = tingPercent,
            tbrVeryLowPercent = tbrVeryLowPercent,
            tbrLowPercent = tbrLowPercent,
            tarHighPercent = tarHighPercent,
            tarVeryHighPercent = tarVeryHighPercent,
            totalCount = totalCount,
            daysCount = distinctDays.coerceAtLeast(1),
            nightStability = nightStability
        )
    }

    /**
     * Glucose Management Indicator formula: GMI = 3.31 + (0.431 * Mean Glucose in mmol/L)
     */
    fun calculateGmi(meanMmol: Double): Double {
        if (meanMmol <= 0.0) return 0.0
        return 3.31 + (0.431 * meanMmol)
    }

    /**
     * Night stability metrics (00:00 - 06:00).
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
        } else 0.0
        val sd = sqrt(variance)
        val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0

        val inTirCount = nightReadings.count { targetRanges.isInTir(it.valueMmol) }
        val tirPercent = (inTirCount.toDouble() / count) * 100.0

        // Stable if CV <= 36% and Night TIR >= 70%
        val isStable = cv <= 36.0 && tirPercent >= 70.0

        return NightStability(
            meanMmol = mean,
            sdMmol = sd,
            cvPercent = cv,
            tirPercent = tirPercent,
            isStable = isStable,
            nightReadingsCount = count
        )
    }
}
