package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseTrendPredictorTest {

    private val targetRanges = TargetRanges(
        tirLowMmol = 3.9,
        tirHighMmol = 10.0,
        tingHighMmol = 7.8
    )

    @Test
    fun testPredictiveLowDetectedWhenFalling() {
        val now = System.currentTimeMillis()
        // Glucose falling rapidly from 6.0 down to 4.2 in 10 readings (5-min intervals)
        // Rate: ~ -0.2 mmol/L per 5 min = -0.04 mmol/L/min
        val readings = (0 until 10).map { i ->
            val timestamp = now - (9 - i) * 5 * 60 * 1000L
            val value = 6.0 - (i * 0.2) // at i=9, value = 4.2
            GlucoseReading(id = i.toLong(), timestamp = timestamp, valueMmol = value)
        }

        val result = GlucoseTrendPredictor.predictTrend(readings, targetRanges, minutesAhead = 15)

        assertEquals(PredictedEvent.PREDICTED_LOW, result.event)
        assertTrue("Rate should be negative", result.rateOfChangeMmolPerMin < 0.0)
        assertTrue("Predicted value should be below 3.9", result.predictedValueMmol < 3.9)
        assertNotNull(result.minutesUntilCrossing)
        assertTrue("Should cross within 15 mins", result.minutesUntilCrossing!! in 1..15)
    }

    @Test
    fun testPredictiveHighDetectedWhenRising() {
        val now = System.currentTimeMillis()
        // Glucose rising rapidly from 7.0 up to 9.2 in 10 readings (5-min intervals)
        val readings = (0 until 10).map { i ->
            val timestamp = now - (9 - i) * 5 * 60 * 1000L
            val value = 7.0 + (i * 0.3) // at i=9, value = 9.7
            GlucoseReading(id = i.toLong(), timestamp = timestamp, valueMmol = value)
        }

        val result = GlucoseTrendPredictor.predictTrend(readings, targetRanges, minutesAhead = 15)

        assertEquals(PredictedEvent.PREDICTED_HIGH, result.event)
        assertTrue("Rate should be positive", result.rateOfChangeMmolPerMin > 0.0)
        assertTrue("Predicted value should be above 10.0", result.predictedValueMmol > 10.0)
        assertNotNull(result.minutesUntilCrossing)
        assertTrue("Should cross within 15 mins", result.minutesUntilCrossing!! in 1..15)
    }

    @Test
    fun testStableGlucoseDoesNotTriggerPrediction() {
        val now = System.currentTimeMillis()
        // Stable readings around 5.5
        val readings = (0 until 10).map { i ->
            val timestamp = now - (9 - i) * 5 * 60 * 1000L
            val value = 5.5 + (if (i % 2 == 0) 0.05 else -0.05)
            GlucoseReading(id = i.toLong(), timestamp = timestamp, valueMmol = value)
        }

        val result = GlucoseTrendPredictor.predictTrend(readings, targetRanges, minutesAhead = 15)

        assertEquals(PredictedEvent.NONE, result.event)
        assertEquals(null, result.minutesUntilCrossing)
    }
}
