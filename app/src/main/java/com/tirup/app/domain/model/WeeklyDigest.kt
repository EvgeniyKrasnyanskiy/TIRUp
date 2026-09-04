package com.tirup.app.domain.model

data class WeeklyDigest(
    val currentWeekStart: Long,
    val currentWeekEnd: Long,
    val previousWeekStart: Long,
    val previousWeekEnd: Long,
    val currentStats: GlucoseStatistics,
    val previousStats: GlucoseStatistics,
    val hasSufficientData: Boolean,
    val tirDelta: Double,
    val tingDelta: Double,
    val tbrDelta: Double,
    val cvDelta: Double,
    val meanDeltaMmol: Double,
    val hypoCountCurrent: Int,
    val hypoCountPrevious: Int,
    val headline: String,
    val keyInsights: List<String>,
    val recommendation: String
) {
    val totalTbrCurrent: Double
        get() = currentStats.tbrLowPercent + currentStats.tbrVeryLowPercent

    val totalTbrPrevious: Double
        get() = previousStats.tbrLowPercent + previousStats.tbrVeryLowPercent

    val totalTarCurrent: Double
        get() = currentStats.tarHighPercent + currentStats.tarVeryHighPercent

    val totalTarPrevious: Double
        get() = previousStats.tarHighPercent + previousStats.tarVeryHighPercent
}
