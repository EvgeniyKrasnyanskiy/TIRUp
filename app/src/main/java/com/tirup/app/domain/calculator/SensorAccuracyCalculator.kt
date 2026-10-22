package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import kotlin.math.abs

enum class SensorAccuracyGrade(val labelRu: String, val labelEn: String) {
    EXCELLENT("Отличная", "Excellent"), // MARD < 10.0% (ISO/FDA benchmark standard)
    GOOD("Хорошая", "Good"),            // 10.0% <= MARD < 15.0% (Clinically acceptable)
    MODERATE("Умеренная", "Moderate"),  // 15.0% <= MARD < 20.0% (Fair accuracy / Caution on bolusing)
    POOR("Неточный", "Inaccurate")      // MARD >= 20.0% (Sensor malfunction / Recalibration necessary)
}

data class MeterReading(
    val timestamp: Long,
    val valueMmol: Double
)

data class PairedReading(
    val meterTimestamp: Long,
    val meterValueMmol: Double,
    val cgmTimestamp: Long,
    val cgmValueMmol: Double,
    val ardPercent: Double,
    val biasMmol: Double,
    val isIsoCompliant: Boolean
)

data class SensorVerificationResult(
    val totalMeterReadings: Int,
    val matchedPairsCount: Int,
    val mardPercent: Double,
    val meanBiasMmol: Double,
    val iso15197Percent: Double,
    val accuracyGrade: SensorAccuracyGrade,
    val pairedReadings: List<PairedReading> = emptyList()
) {
    val pairedPointsCount: Int get() = matchedPairsCount
    val isoPassPercent: Double get() = iso15197Percent
}

object SensorAccuracyCalculator {
    private const val MAX_PAIRING_WINDOW_MS = 7 * 60 * 1000L // +/- 7 minutes matching window
    private const val MAX_ROC_THRESHOLD = 0.12 // mmol/L/min - suppress points in rapid trend to avoid lag penalty

    fun calculateVerification(
        sensorReadings: List<GlucoseReading>,
        meterReadings: List<MeterReading>,
        filterRapidTrends: Boolean = true
    ): SensorVerificationResult? {
        if (sensorReadings.isEmpty() || meterReadings.isEmpty()) return null

        val sortedCgm = sensorReadings.sortedBy { it.timestamp }
        val sortedMeter = meterReadings.sortedBy { it.timestamp }

        val pairedList = mutableListOf<PairedReading>()

        for (meter in sortedMeter) {
            val meterTs = meter.timestamp
            val meterVal = meter.valueMmol
            if (meterVal <= 0.5 || meterVal > 35.0) continue

            // Find closest CGM point within +/- 7 minutes
            var closestCgm: GlucoseReading? = null
            var minDeltaTime = Long.MAX_VALUE

            for (cgm in sortedCgm) {
                val dt = abs(cgm.timestamp - meterTs)
                if (dt <= MAX_PAIRING_WINDOW_MS && dt < minDeltaTime) {
                    minDeltaTime = dt
                    closestCgm = cgm
                }
            }

            if (closestCgm != null) {
                // Rate of change check: look at neighboring CGM reading to estimate velocity
                if (filterRapidTrends) {
                    val cgmIdx = sortedCgm.indexOf(closestCgm)
                    var isRateTooHigh = false
                    if (cgmIdx > 0) {
                        val prev = sortedCgm[cgmIdx - 1]
                        val dtMin = (closestCgm.timestamp - prev.timestamp) / 60000.0
                        if (dtMin in 0.5..15.0) {
                            val roc = abs(closestCgm.valueMmol - prev.valueMmol) / dtMin
                            if (roc > MAX_ROC_THRESHOLD) {
                                isRateTooHigh = true
                            }
                        }
                    }
                    if (isRateTooHigh) {
                        continue // Skip rapid change points to prevent penalizing sensor for genuine lag
                    }
                }

                val cgmVal = closestCgm.valueMmol
                val ard = (abs(cgmVal - meterVal) / meterVal) * 100.0
                val bias = cgmVal - meterVal

                // ISO 15197:2013 criterion:
                // For BG < 5.55 mmol/L (100 mg/dL): within +/- 0.83 mmol/L (15 mg/dL)
                // For BG >= 5.55 mmol/L (100 mg/dL): within +/- 15%
                val isIso = if (meterVal < 5.55) {
                    abs(bias) <= 0.83
                } else {
                    ard <= 15.0
                }

                pairedList.add(
                    PairedReading(
                        meterTimestamp = meterTs,
                        meterValueMmol = meterVal,
                        cgmTimestamp = closestCgm.timestamp,
                        cgmValueMmol = cgmVal,
                        ardPercent = ard,
                        biasMmol = bias,
                        isIsoCompliant = isIso
                    )
                )
            }
        }

        if (pairedList.isEmpty()) return null

        val mard = pairedList.map { it.ardPercent }.average()
        val meanBias = pairedList.map { it.biasMmol }.average()
        val isoCompliantCount = pairedList.count { it.isIsoCompliant }
        val isoPercent = (isoCompliantCount.toDouble() / pairedList.size.toDouble()) * 100.0

        val grade = when {
            mard < 10.0 -> SensorAccuracyGrade.EXCELLENT
            mard < 15.0 -> SensorAccuracyGrade.GOOD
            mard < 20.0 -> SensorAccuracyGrade.MODERATE
            else -> SensorAccuracyGrade.POOR
        }

        return SensorVerificationResult(
            totalMeterReadings = sortedMeter.size,
            matchedPairsCount = pairedList.size,
            mardPercent = mard,
            meanBiasMmol = meanBias,
            iso15197Percent = isoPercent,
            accuracyGrade = grade,
            pairedReadings = pairedList
        )
    }
}
