package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetRanges
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PredictedEvent {
    NONE,
    PREDICTED_LOW,
    PREDICTED_HIGH
}

data class PredictionResult(
    val event: PredictedEvent,
    val predictedValueMmol: Double,
    val minutesUntilCrossing: Int?,
    val rateOfChangeMmolPerMin: Double,
    val confidenceR2: Double,
    val currentReading: GlucoseReading?
)

object GlucoseTrendPredictor {

    private const val MIN_POINTS = 5
    private const val DEFAULT_LOOKBACK_POINTS = 12
    private const val MIN_CONFIDENCE_R2 = 0.70

    /**
     * Analyzes recent glucose readings and predicts whether glucose will cross
     * low (< tirLow) or high (> tirHigh / tingHigh) threshold within [minutesAhead] minutes.
     *
     * @param readings Chronologically ordered (or unordered) glucose readings.
     * @param targetRanges Clinical target ranges.
     * @param minutesAhead Prediction horizon (default 15 minutes).
     */
    fun predictTrend(
        readings: List<GlucoseReading>,
        targetRanges: TargetRanges,
        minutesAhead: Int = 15,
        useTingForHigh: Boolean = false
    ): PredictionResult {
        if (readings.size < MIN_POINTS) {
            val last = readings.maxByOrNull { it.timestamp }
            return PredictionResult(
                event = PredictedEvent.NONE,
                predictedValueMmol = last?.valueMmol ?: 0.0,
                minutesUntilCrossing = null,
                rateOfChangeMmolPerMin = 0.0,
                confidenceR2 = 0.0,
                currentReading = last
            )
        }

        // Take the latest 10-15 readings, sorted ascending by timestamp
        val sorted = readings.sortedBy { it.timestamp }
        val window = sorted.takeLast(DEFAULT_LOOKBACK_POINTS)
        val latest = window.last()

        // Check for time gaps: if latest reading is older than 20 minutes, don't predict
        val now = System.currentTimeMillis()
        if (now - latest.timestamp > 20 * 60 * 1000L) {
            return PredictionResult(
                event = PredictedEvent.NONE,
                predictedValueMmol = latest.valueMmol,
                minutesUntilCrossing = null,
                rateOfChangeMmolPerMin = 0.0,
                confidenceR2 = 0.0,
                currentReading = latest
            )
        }

        // Convert timestamps to elapsed minutes from the earliest point in window
        val t0 = window.first().timestamp
        val x = window.map { (it.timestamp - t0).toDouble() / 60000.0 }
        val y = window.map { it.valueMmol }
        val n = x.size

        // Linear regression: y = m * x + b
        val xMean = x.average()
        val yMean = y.average()

        var ssXx = 0.0
        var ssYy = 0.0
        var ssXy = 0.0

        for (i in 0 until n) {
            val dx = x[i] - xMean
            val dy = y[i] - yMean
            ssXx += dx * dx
            ssYy += dy * dy
            ssXy += dx * dy
        }

        if (ssXx == 0.0) {
            return PredictionResult(
                event = PredictedEvent.NONE,
                predictedValueMmol = latest.valueMmol,
                minutesUntilCrossing = null,
                rateOfChangeMmolPerMin = 0.0,
                confidenceR2 = 0.0,
                currentReading = latest
            )
        }

        val slope = ssXy / ssXx // mmol/L per minute
        val r2 = if (ssYy > 0.0) (ssXy * ssXy) / (ssXx * ssYy) else 1.0

        val predictedValue = latest.valueMmol + slope * minutesAhead.toDouble()

        val highThreshold = if (useTingForHigh) targetRanges.tingHighMmol else targetRanges.tirHighMmol
        val lowThreshold = targetRanges.tirLowMmol

        var event = PredictedEvent.NONE
        var minutesUntilCrossing: Int? = null

        // Predictive Low: currently above low, falling, and will cross below lowThreshold within minutesAhead
        if (latest.valueMmol >= lowThreshold && slope < -0.015) {
            val minutesToCross = (lowThreshold - latest.valueMmol) / slope
            if (minutesToCross in 0.0..minutesAhead.toDouble() && (r2 >= MIN_CONFIDENCE_R2 || predictedValue <= lowThreshold)) {
                event = PredictedEvent.PREDICTED_LOW
                minutesUntilCrossing = max(1, minutesToCross.roundToInt())
            }
        }
        // Predictive High: currently below high, rising, and will cross above highThreshold within minutesAhead
        else if (latest.valueMmol <= highThreshold && slope > 0.015) {
            val minutesToCross = (highThreshold - latest.valueMmol) / slope
            if (minutesToCross in 0.0..minutesAhead.toDouble() && (r2 >= MIN_CONFIDENCE_R2 || predictedValue >= highThreshold)) {
                event = PredictedEvent.PREDICTED_HIGH
                minutesUntilCrossing = max(1, minutesToCross.roundToInt())
            }
        }

        return PredictionResult(
            event = event,
            predictedValueMmol = (predictedValue * 10.0).roundToInt() / 10.0,
            minutesUntilCrossing = minutesUntilCrossing,
            rateOfChangeMmolPerMin = (slope * 1000.0).roundToInt() / 1000.0,
            confidenceR2 = (r2 * 100.0).roundToInt() / 100.0,
            currentReading = latest
        )
    }
}
