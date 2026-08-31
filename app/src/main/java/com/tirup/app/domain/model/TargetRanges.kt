package com.tirup.app.domain.model

data class TargetRanges(
    val tirLowMmol: Double = 3.9,
    val tirHighMmol: Double = 10.0,
    val tingHighMmol: Double = 7.8,
    val veryLowThresholdMmol: Double = 3.0,
    val veryHighThresholdMmol: Double = 13.9,
    val tirGoalPercent: Int = 70,
    val tingGoalPercent: Int = 50
) {
    fun categorize(valueMmol: Double): GlucoseRangeCategory {
        return when {
            valueMmol < veryLowThresholdMmol -> GlucoseRangeCategory.VERY_LOW
            valueMmol < tirLowMmol -> GlucoseRangeCategory.LOW
            valueMmol <= tingHighMmol -> GlucoseRangeCategory.TIGHT
            valueMmol <= tirHighMmol -> GlucoseRangeCategory.TARGET
            valueMmol <= veryHighThresholdMmol -> GlucoseRangeCategory.HIGH
            else -> GlucoseRangeCategory.VERY_HIGH
        }
    }

    fun isInTir(valueMmol: Double): Boolean {
        return valueMmol in tirLowMmol..tirHighMmol
    }

    fun isInTing(valueMmol: Double): Boolean {
        return valueMmol in tirLowMmol..tingHighMmol
    }
}
