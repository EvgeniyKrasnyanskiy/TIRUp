package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.ClinicalMetricStatus
import com.tirup.app.domain.model.ClinicalSummary
import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.NightStability
import com.tirup.app.domain.model.TargetRanges
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object GlucoseMetricsCalculator {

    private const val MGDL_FACTOR = 18.0182
    private const val CUTOFF_MGDL = 38.0 // xDrip & DiaKiaBot CUTOFF (~2.1 mmol/L)
    private const val CUTOFF_MMOL = CUTOFF_MGDL / MGDL_FACTOR // ~2.109 mmol/L

    /**
     * Resamples raw readings into 5-minute intervals (matching xDrip & DiaKiaBot),
     * filtering out noise/calibration artifacts below CUTOFF.
     */
    fun resampleTo5Minutes(readings: List<GlucoseReading>): List<GlucoseReading> {
        if (readings.isEmpty()) return emptyList()

        val fiveMinMs = 5 * 60 * 1000L
        val validReadings = readings.filter { it.valueMmol >= CUTOFF_MMOL }
        if (validReadings.isEmpty()) return emptyList()

        return validReadings
            .groupBy { it.timestamp / fiveMinMs }
            .map { (binKey, binItems) ->
                val avgMmol = binItems.sumOf { it.valueMmol } / binItems.size
                GlucoseReading(
                    timestamp = binKey * fiveMinMs,
                    valueMmol = avgMmol,
                    trendArrow = binItems.last().trendArrow
                )
            }
            .sortedBy { it.timestamp }
    }

    /**
     * Calculates complete clinical statistics from a list of glucose readings,
     * matching xDrip+ and DiaKiaBot algorithms:
     * Mean, Median, SD, %CV, ADAG eA1c, GMI, IFCC HbA1c, TIR, TING, TBR, TAR, GVI, PGS, GRI (Klonoff 2022),
     * Night Stability, and Automated Doctor's Clinical Summary with language support.
     */
    fun calculateStatistics(
        readings: List<GlucoseReading>,
        targetRanges: TargetRanges = TargetRanges(),
        nightStartHour: Int = 0,
        nightEndHour: Int = 6,
        language: String = "RU"
    ): GlucoseStatistics {
        if (readings.isEmpty()) {
            return GlucoseStatistics()
        }

        // 1. Resample to standard 5-minute bins (xDrip / DiaKiaBot standard)
        val clean5MinReadings = resampleTo5Minutes(readings)
        if (clean5MinReadings.isEmpty()) {
            return GlucoseStatistics()
        }

        val totalCount = clean5MinReadings.size
        val sumMmol = clean5MinReadings.sumOf { it.valueMmol }
        val mean = sumMmol / totalCount

        // Median
        val sortedValues = clean5MinReadings.map { it.valueMmol }.sorted()
        val median = if (totalCount % 2 == 0) {
            (sortedValues[totalCount / 2 - 1] + sortedValues[totalCount / 2]) / 2.0
        } else {
            sortedValues[totalCount / 2]
        }

        // Standard Deviation
        val variance = if (totalCount > 1) {
            clean5MinReadings.sumOf { (it.valueMmol - mean) * (it.valueMmol - mean) } / (totalCount - 1)
        } else {
            0.0
        }
        val sd = sqrt(variance)

        // %CV = (SD / Mean) * 100%
        val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0

        // eA1c: ADAG Clinical formula as in DiaKiaBot: (Mean + 2.59) / 1.59
        val ea1cAdag = (mean + 2.59) / 1.59
        // IFCC mmol/mol: (eA1c% - 2.15) * 10.929
        val hba1cMmolMol = ((ea1cAdag - 2.15) * 10.929).roundToInt().coerceAtLeast(0)

        // 2. Ranges counting in mg/dL (xDrip exact thresholds: Low 70.27 mg/dL, High 180.0 mg/dL, Tight High 140.0 mg/dL)
        val lowMgdl = 3.9 * MGDL_FACTOR      // ~70.27
        val highMgdl = 180.0                 // ~10.0 mmol/L
        val tightHighMgdl = 140.0            // ~7.8 mmol/L
        val tbr30Mgdl = 54.0                 // ~3.0 mmol/L
        val tar139Mgdl = 250.0               // ~13.9 mmol/L

        var belowCount = 0
        var below30Count = 0
        var inRangeCount = 0
        var tightCount = 0
        var aboveCount = 0
        var above139Count = 0

        val nightReadings = mutableListOf<GlucoseReading>()
        val calendar = Calendar.getInstance(TimeZone.getDefault())

        clean5MinReadings.forEach { reading ->
            val mgdl = reading.valueMmol * MGDL_FACTOR

            if (mgdl < tbr30Mgdl) below30Count++
            if (mgdl < lowMgdl) belowCount++
            if (mgdl >= lowMgdl && mgdl < highMgdl) inRangeCount++
            if (mgdl >= lowMgdl && mgdl < tightHighMgdl) tightCount++
            if (mgdl >= highMgdl) aboveCount++
            if (mgdl >= tar139Mgdl) above139Count++

            calendar.timeInMillis = reading.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (isNightHour(hour, nightStartHour, nightEndHour)) {
                nightReadings.add(reading)
            }
        }

        val tirPercent = (inRangeCount.toDouble() / totalCount) * 100.0
        val tingPercent = (tightCount.toDouble() / totalCount) * 100.0
        val tbrLowPercent = ((belowCount - below30Count).coerceAtLeast(0).toDouble() / totalCount) * 100.0
        val tbrVeryLowPercent = (below30Count.toDouble() / totalCount) * 100.0
        val tarHighPercent = ((aboveCount - above139Count).coerceAtLeast(0).toDouble() / totalCount) * 100.0
        val tarVeryHighPercent = (above139Count.toDouble() / totalCount) * 100.0

        val tbrTotalPercent = (belowCount.toDouble() / totalCount) * 100.0
        val tarTotalPercent = (aboveCount.toDouble() / totalCount) * 100.0

        // Distinct active days
        val distinctDays = clean5MinReadings.map {
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }.distinct().size

        val nightStability = calculateNightStability(nightReadings, targetRanges)

        // 3. GVI calculation (xDrip step-by-step algorithm in mg/dL on 5-min intervals)
        val valuesMgdl = clean5MinReadings.map { it.valueMmol * MGDL_FACTOR }
        val gvi = computeGviXdripStyle(valuesMgdl)

        // 4. PGS calculation: GVI * floor(mean_mgdl) * (1 - floor(TIR)/100)
        val meanMgdlFloored = floor(mean * MGDL_FACTOR)
        val tirPercentInt = (inRangeCount * 100) / totalCount
        val pgsRaw = gvi * meanMgdlFloored * (1.0 - (tirPercentInt / 100.0))
        val pgsTruncated = (pgsRaw * 10.0).toInt() / 10.0

        // 5. GRI calculation (Glycemia Risk Index - Klonoff et al. 2022)
        val griLowCount = clean5MinReadings.count { (it.valueMmol * MGDL_FACTOR) in tbr30Mgdl..(3.8 * MGDL_FACTOR) }
        val griLowPct = (griLowCount.toDouble() / totalCount) * 100.0
        val griHighCount = clean5MinReadings.count { (it.valueMmol * MGDL_FACTOR) in (10.1 * MGDL_FACTOR)..tar139Mgdl }
        val griHighPct = (griHighCount.toDouble() / totalCount) * 100.0

        val hypoComponent = tbrVeryLowPercent + (0.8 * griLowPct)
        val hyperComponent = tarVeryHighPercent + (0.5 * griHighPct)
        val rawGri = (3.0 * hypoComponent) + (1.6 * hyperComponent)
        val gri = min((rawGri * 10.0).roundToInt() / 10.0, 100.0)

        val isRu = language.equals("RU", ignoreCase = true)
        val (griZone, griLabel) = when {
            gri <= 20.0 -> Pair(1, if (isRu) "Очень низкий риск" else "Very Low Risk")
            gri <= 40.0 -> Pair(2, if (isRu) "Низкий риск" else "Low Risk")
            gri <= 60.0 -> Pair(3, if (isRu) "Средний риск" else "Moderate Risk")
            gri <= 80.0 -> Pair(4, if (isRu) "Высокий риск" else "High Risk")
            else -> Pair(5, if (isRu) "Очень высокий риск" else "Very High Risk")
        }

        // 6. Active Time (CGM coverage)
        val totalDurationMs = if (clean5MinReadings.size > 1) {
            clean5MinReadings.last().timestamp - clean5MinReadings.first().timestamp
        } else 0L

        var totalGapDurationMs = 0L
        val gapThresholdMs = 20 * 60 * 1000L // 20 min
        for (i in 1 until clean5MinReadings.size) {
            val diff = clean5MinReadings[i].timestamp - clean5MinReadings[i - 1].timestamp
            if (diff > gapThresholdMs) {
                totalGapDurationMs += diff
            }
        }

        val activeTimePercent = if (totalDurationMs > 0L) {
            val activeMs = (totalDurationMs - totalGapDurationMs).coerceAtLeast(0L)
            val pct = (activeMs.toDouble() / totalDurationMs.toDouble()) * 100.0
            (pct * 10.0).roundToInt() / 10.0
        } else {
            100.0
        }

        // 7. Clinical Summary with exact ordered metrics & language support
        val clinicalSummary = generateClinicalSummary(
            meanMmol = mean,
            ea1c = ea1cAdag,
            tirPercent = tirPercent,
            tingPercent = tingPercent,
            tbrTotalPercent = tbrTotalPercent,
            tbrVeryLowPercent = tbrVeryLowPercent,
            tarTotalPercent = tarTotalPercent,
            tarVeryHighPercent = tarVeryHighPercent,
            gvi = gvi,
            pgs = pgsTruncated,
            cvPercent = cv,
            sdMmol = sd,
            gri = gri,
            griLabel = griLabel,
            nightStability = nightStability,
            language = language
        )

        return GlucoseStatistics(
            meanMmol = mean,
            medianMmol = median,
            sdMmol = sd,
            cvPercent = cv,
            gmiPercent = ea1cAdag, // Display ADAG eA1c
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
     * Exact xDrip & DiaKiaBot GVI calculation.
     */
    private fun computeGviXdripStyle(valuesMgdl: List<Double>): Double {
        if (valuesMgdl.size < 2) return 1.0

        val glucoseFirst = valuesMgdl.first()
        var glucoseLast = glucoseFirst
        var gviTotal = 0.0
        var usedRecords = 1

        for (i in 1 until valuesMgdl.size) {
            val curr = valuesMgdl[i]
            val delta = curr - glucoseLast
            gviTotal += sqrt(25.0 + (delta * delta))
            usedRecords++
            glucoseLast = curr
        }

        val gviDelta = abs(glucoseLast - glucoseFirst)
        val timeComponent = (usedRecords * 5).toDouble()
        val gviIdeal = sqrt((timeComponent * timeComponent) + (gviDelta * gviDelta))

        if (gviIdeal == 0.0) return 1.0

        val rawGvi = gviTotal / gviIdeal
        // Truncate to 2 decimals (emulates Java's (int)(val*100)/100.0)
        return ((rawGvi * 100.0).toInt()) / 100.0
    }

    /**
     * Orders clinical evaluation items matching DiaKiaBot order with full bilingual support:
     */
    private fun generateClinicalSummary(
        meanMmol: Double,
        ea1c: Double,
        tirPercent: Double,
        tingPercent: Double,
        tbrTotalPercent: Double,
        tbrVeryLowPercent: Double,
        tarTotalPercent: Double,
        tarVeryHighPercent: Double,
        gvi: Double,
        pgs: Double,
        cvPercent: Double,
        sdMmol: Double,
        gri: Double,
        griLabel: String,
        nightStability: NightStability,
        language: String
    ): ClinicalSummary {
        val isRu = language.equals("RU", ignoreCase = true)
        val evalItems = mutableListOf<ClinicalMetricStatus>()
        val issuesList = mutableListOf<String>()

        // 1. Mean BG (Target ≤ 7.8 mmol/L)
        val meanMet = meanMmol <= 7.8
        val meanWarn = meanMmol in 7.9..8.5
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "Mean BG (средний сахар)" else "Mean BG (average glucose)",
                valueStr = String.format(Locale.US, "%.1f %s", meanMmol, if (isRu) "ммоль/л" else "mmol/L"),
                targetStr = if (isRu) "цель ≤7.8" else "target ≤7.8",
                isMet = meanMet,
                isWarning = meanWarn
            )
        )
        if (!meanMet) issuesList.add(if (isRu) "Mean BG" else "Mean BG")

        // 2. eA1c (Target ≤ 7.0%)
        val a1cMet = ea1c <= 7.0
        val a1cWarn = ea1c in 7.1..7.5
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "eA1c (расчёт ГГ)" else "eA1c (estimated A1c)",
                valueStr = String.format(Locale.US, "%.1f%%", ea1c),
                targetStr = if (isRu) "цель ≤7.0%" else "target ≤7.0%",
                isMet = a1cMet,
                isWarning = a1cWarn
            )
        )
        if (!a1cMet) issuesList.add(if (isRu) "eA1c" else "eA1c")

        // 3. TIR (3.9–10.0, Target ≥ 70%)
        val tirMet = tirPercent >= 70.0
        val tirWarn = tirPercent in 60.0..69.9
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TIR (3.9–10.0 ммоль/л)" else "TIR (3.9–10.0 mmol/L)",
                valueStr = String.format(Locale.US, "%.0f%%", tirPercent),
                targetStr = if (isRu) "цель ≥70%" else "target ≥70%",
                isMet = tirMet,
                isWarning = tirWarn
            )
        )
        if (!tirMet) issuesList.add("TIR")

        // 4. TING (3.9–7.8, Target ≥ 50%)
        val tingMet = tingPercent >= 50.0
        val tingWarn = tingPercent in 40.0..49.9
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TING (3.9–7.8 ммоль/л)" else "TING (3.9–7.8 mmol/L)",
                valueStr = String.format(Locale.US, "%.0f%%", tingPercent),
                targetStr = if (isRu) "цель ≥50%" else "target ≥50%",
                isMet = tingMet,
                isWarning = tingWarn
            )
        )
        if (!tingMet) issuesList.add("TING")

        // 5. TBR < 3.9 (Target < 4%)
        val tbrMet = tbrTotalPercent <= 4.0
        val tbrWarn = tbrTotalPercent in 4.1..6.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TBR < 3.9 ммоль/л" else "TBR < 3.9 mmol/L",
                valueStr = String.format(Locale.US, "%.1f%%", tbrTotalPercent),
                targetStr = if (isRu) "цель <4%" else "target <4%",
                isMet = tbrMet,
                isWarning = tbrWarn
            )
        )
        if (!tbrMet) issuesList.add(if (isRu) "TBR <3.9" else "TBR <3.9")

        // 6. TBR < 3.0 (Target < 1%)
        val tbr30Met = tbrVeryLowPercent <= 1.0
        val tbr30Warn = tbrVeryLowPercent in 1.1..2.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TBR < 3.0 ммоль/л" else "TBR < 3.0 mmol/L",
                valueStr = String.format(Locale.US, "%.1f%%", tbrVeryLowPercent),
                targetStr = if (isRu) "цель <1%" else "target <1%",
                isMet = tbr30Met,
                isWarning = tbr30Warn
            )
        )
        if (!tbr30Met) issuesList.add(if (isRu) "TBR <3.0" else "TBR <3.0")

        // 7. TAR > 10.0 (Target < 25%)
        val tarMet = tarTotalPercent <= 25.0
        val tarWarn = tarTotalPercent in 25.1..35.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TAR > 10.0 ммоль/л" else "TAR > 10.0 mmol/L",
                valueStr = String.format(Locale.US, "%.0f%%", tarTotalPercent),
                targetStr = if (isRu) "цель <25%" else "target <25%",
                isMet = tarMet,
                isWarning = tarWarn
            )
        )
        if (!tarMet) issuesList.add(if (isRu) "TAR >10.0" else "TAR >10.0")

        // 8. TAR > 13.9 (Target < 5%)
        val tar139Met = tarVeryHighPercent <= 5.0
        val tar139Warn = tarVeryHighPercent in 5.1..8.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "TAR > 13.9 ммоль/л" else "TAR > 13.9 mmol/L",
                valueStr = String.format(Locale.US, "%.1f%%", tarVeryHighPercent),
                targetStr = if (isRu) "цель <5%" else "target <5%",
                isMet = tar139Met,
                isWarning = tar139Warn
            )
        )
        if (!tar139Met) issuesList.add(if (isRu) "TAR >13.9" else "TAR >13.9")

        // 9. GVI (Target ≤ 1.20)
        val gviMet = gvi <= 1.20
        val gviWarn = gvi in 1.21..1.40
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "GVI (лабильность)" else "GVI (variability index)",
                valueStr = String.format(Locale.US, "%.2f", gvi),
                targetStr = if (isRu) "цель ≤1.20" else "target ≤1.20",
                isMet = gviMet,
                isWarning = gviWarn
            )
        )
        if (!gviMet) issuesList.add("GVI")

        // 10. PGS (Target ≤ 35.0)
        val pgsMet = pgs <= 35.0
        val pgsWarn = pgs in 35.1..45.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "PGS (гликемический статус)" else "PGS (patient status)",
                valueStr = String.format(Locale.US, "%.1f", pgs),
                targetStr = if (isRu) "цель ≤35.0" else "target ≤35.0",
                isMet = pgsMet,
                isWarning = pgsWarn
            )
        )
        if (!pgsMet) issuesList.add("PGS")

        // 11. %CV (Target ≤ 36.0%)
        val cvMet = cvPercent <= 36.0
        val cvWarn = cvPercent in 36.1..40.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "Вариабельность (%CV)" else "Variability (%CV)",
                valueStr = String.format(Locale.US, "%.1f%%", cvPercent),
                targetStr = if (isRu) "цель ≤36.0%" else "target ≤36.0%",
                isMet = cvMet,
                isWarning = cvWarn
            )
        )
        if (!cvMet) issuesList.add("%CV")

        // 12. SD
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "SD (стандартное отклонение)" else "SD (standard deviation)",
                valueStr = String.format(Locale.US, "%.2f %s", sdMmol, if (isRu) "ммоль/л" else "mmol/L"),
                targetStr = if (isRu) "норма ≤2.5" else "normal ≤2.5",
                isMet = sdMmol <= 2.5,
                isWarning = sdMmol in 2.51..3.0
            )
        )

        // 13. GRI (Target ≤ 40.0)
        val griMet = gri <= 40.0
        val griWarn = gri in 40.1..60.0
        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "GRI (индекс риска)" else "GRI (glycemia risk)",
                valueStr = String.format(Locale.US, "%.1f (%s)", gri, griLabel),
                targetStr = if (isRu) "цель ≤40.0" else "target ≤40.0",
                isMet = griMet,
                isWarning = griWarn
            )
        )
        if (!griMet) issuesList.add("GRI")

        // 14. Night Profile Stability
        val hasNightData = nightStability.nightReadingsCount >= 6
        val nightMet = hasNightData && nightStability.isStable
        val nightValRu = when {
            !hasNightData -> "Недостаточно данных (<30 мин)"
            nightStability.isStable -> "Стабильный (TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)})"
            else -> "Обнаружены колебания (TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)})"
        }
        val nightValEn = when {
            !hasNightData -> "Insufficient night data (<30m)"
            nightStability.isStable -> "Stable (TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)})"
            else -> "Fluctuations detected (TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)})"
        }

        evalItems.add(
            ClinicalMetricStatus(
                title = if (isRu) "Ночной профиль сна" else "Night Sleep Profile",
                valueStr = if (isRu) nightValRu else nightValEn,
                targetStr = if (isRu) "TIR ≥70% и SD ≤1.5" else "TIR ≥70% & SD ≤1.5",
                isMet = nightMet || !hasNightData,
                isWarning = hasNightData && !nightStability.isStable
            )
        )
        if (hasNightData && !nightStability.isStable) issuesList.add(if (isRu) "Ночной сон" else "Night sleep")

        val (status, isAllMet, rec) = when {
            issuesList.isEmpty() -> Triple(
                if (isRu) "Все цели достигнуты. Отличный контроль!" else "All clinical targets achieved. Excellent glycemic control!",
                true,
                if (isRu) "Поддерживайте текущий режим питания и терапии." else "Maintain current nutrition and insulin therapy regimen."
            )
            issuesList.size <= 2 -> {
                val names = issuesList.joinToString(", ")
                Triple(
                    if (isRu) "Есть небольшие отклонения по параметрам: $names." else "Minor deviations noted in: $names.",
                    false,
                    if (isRu) "Рекомендуется обратить внимание на отмеченные параметры и обсудить с лечащим врачом." else "Recommended to review highlighted parameters with your healthcare provider."
                )
            }
            else -> {
                val names = issuesList.take(3).joinToString(", ")
                Triple(
                    if (isRu) "Имеются отклонения по параметрам: $names и др." else "Deviations detected in: $names and others.",
                    false,
                    if (isRu) "Рекомендуется консультация эндокринолога для возможной корректировки доз или схемы терапии." else "Consultation with an endocrinologist is advised to evaluate therapy adjustments."
                )
            }
        }

        return ClinicalSummary(
            overallStatus = status,
            isAllTargetsMet = isAllMet,
            evaluatedMetrics = evalItems,
            hyperIssues = emptyList(),
            hypoIssues = emptyList(),
            variabilityIssues = emptyList(),
            rangeIssues = emptyList(),
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
