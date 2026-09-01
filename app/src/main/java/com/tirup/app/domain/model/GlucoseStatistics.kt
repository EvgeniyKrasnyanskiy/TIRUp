package com.tirup.app.domain.model

data class ClinicalMetricStatus(
    val title: String,
    val valueStr: String,
    val targetStr: String,
    val isMet: Boolean,
    val isWarning: Boolean,
    val symbol: String = if (isMet) "✔" else if (isWarning) "⚠️" else "✘"
)

data class ClinicalSummary(
    val overallStatus: String = "Все цели достигнуты. Отличный контроль!",
    val isAllTargetsMet: Boolean = true,
    val evaluatedMetrics: List<ClinicalMetricStatus> = emptyList(),
    val hyperIssues: List<String> = emptyList(),
    val hypoIssues: List<String> = emptyList(),
    val variabilityIssues: List<String> = emptyList(),
    val rangeIssues: List<String> = emptyList(),
    val recommendation: String = "Поддерживайте текущий режим питания и терапии."
)

data class GlucoseStatistics(
    val meanMmol: Double = 0.0,
    val medianMmol: Double = 0.0,
    val sdMmol: Double = 0.0,
    val cvPercent: Double = 0.0,
    val gmiPercent: Double = 0.0,
    val hba1cMmolMol: Int = 0,
    val tirPercent: Double = 0.0,
    val tingPercent: Double = 0.0,
    val tbrVeryLowPercent: Double = 0.0,
    val tbrLowPercent: Double = 0.0,
    val tarHighPercent: Double = 0.0,
    val tarVeryHighPercent: Double = 0.0,
    val gvi: Double = 1.0,
    val pgs: Double = 0.0,
    val gri: Double = 0.0,
    val griZone: Int = 1,
    val griLabel: String = "Очень низкий риск",
    val activeTimePercent: Double = 100.0,
    val totalCount: Int = 0,
    val daysCount: Int = 0,
    val nightStability: NightStability = NightStability(),
    val clinicalSummary: ClinicalSummary = ClinicalSummary()
)

data class NightStability(
    val meanMmol: Double = 0.0,
    val sdMmol: Double = 0.0,
    val cvPercent: Double = 0.0,
    val tirPercent: Double = 0.0,
    val tbrPercent: Double = 0.0,
    val tarPercent: Double = 0.0,
    val isStable: Boolean = true,
    val nightReadingsCount: Int = 0
)

enum class CompensatorStatus {
    REACHABLE,
    EXCEEDING,
    UNREALISTIC
}

data class CompensatorGoal(
    val targetMode: TargetMode = TargetMode.TIR,
    val targetGoalPercent: Double = 70.0,
    val totalDays: Int = 7,
    val pastDays: Int = 4,
    val remainingDays: Int = 3,
    val pastAveragePercent: Double = 65.0,
    val neededRemainingPercent: Double = 76.7,
    val status: CompensatorStatus = CompensatorStatus.REACHABLE
)

data class AGPPercentileBin(
    val binIndex: Int,          // 0..47 for 30-min bins, or 0..95 for 15-min bins
    val minuteOfDay: Int,       // 0..1439
    val formattedTime: String,  // "04:30"
    val p10: Double,
    val p25: Double,
    val p50: Double,            // Median
    val p75: Double,
    val p90: Double,
    val readingsCount: Int
)

data class HeatmapCell(
    val dayIndex: Int,          // Day 0..N
    val dayFormatted: String,   // "12 Oct"
    val hourOfDay: Int,         // 0..23
    val meanMmol: Double?,
    val rangeCategory: GlucoseRangeCategory?
)
