package com.tirup.app.domain.model

enum class GlucoseUnit(val label: String, val factorToMmol: Double) {
    MMOL_L("mmol/L", 1.0),
    MG_DL("mg/dL", 1.0 / 18.0182);

    companion object {
        fun convertMmolToMgDl(mmol: Double): Double = mmol * 18.0182
        fun convertMgDlToMmol(mgdl: Double): Double = mgdl / 18.0182
    }
}

data class GlucoseReading(
    val id: Long = 0,
    val timestamp: Long,
    val valueMmol: Double,
    val trendArrow: String? = null
) {
    fun getValue(unit: GlucoseUnit): Double {
        return when (unit) {
            GlucoseUnit.MMOL_L -> valueMmol
            GlucoseUnit.MG_DL -> GlucoseUnit.convertMmolToMgDl(valueMmol)
        }
    }
}

enum class GlucoseRangeCategory {
    VERY_LOW,    // < 3.0 mmol/L (< 54 mg/dL)
    LOW,         // 3.0 - 3.8 mmol/L (54 - 69 mg/dL)
    TIGHT,       // 3.9 - 7.8 mmol/L (70 - 140 mg/dL)
    TARGET,      // 7.9 - 10.0 mmol/L (141 - 180 mg/dL)
    HIGH,        // 10.1 - 13.9 mmol/L (181 - 250 mg/dL)
    VERY_HIGH    // >= 14.0 mmol/L (>= 252 mg/dL)
}

enum class TargetMode {
    TIR,   // Standard (3.9 - 10.0 mmol/L)
    TING   // Tight / TITR (3.9 - 7.8 mmol/L)
}
