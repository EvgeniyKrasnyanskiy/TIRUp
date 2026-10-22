package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorAccuracyCalculatorTest {

    @Test
    fun testEmptyInputReturnsNull() {
        assertNull(SensorAccuracyCalculator.calculateVerification(emptyList(), emptyList()))
        assertNull(SensorAccuracyCalculator.calculateVerification(listOf(createReading(1000L, 5.5)), emptyList()))
        assertNull(SensorAccuracyCalculator.calculateVerification(emptyList(), listOf(MeterReading(1000L, 5.5))))
    }

    @Test
    fun testPairingWithinTimeWindow() {
        val baseTime = 1_000_000_000L
        val readings = listOf(
            createReading(baseTime, 6.0),
            createReading(baseTime + 300_000L, 6.2) // +5 min
        )
        // Meter reading at baseTime + 120_000L (2 min later) -> matches within 7 min
        val meterReadings = listOf(
            MeterReading(baseTime + 120_000L, 6.1)
        )

        val result = SensorAccuracyCalculator.calculateVerification(readings, meterReadings)
        assertNotNull(result)
        assertEquals(1, result!!.pairedPointsCount)
        assertTrue(result.isoPassPercent == 100.0)
        assertEquals(SensorAccuracyGrade.EXCELLENT, result.accuracyGrade)
    }

    @Test
    fun testPairingOutsideTimeWindowIgnored() {
        val baseTime = 1_000_000_000L
        val readings = listOf(
            createReading(baseTime, 6.0)
        )
        // Meter reading at baseTime + 600_000L (10 min later) -> exceeds 7 min window
        val meterReadings = listOf(
            MeterReading(baseTime + 600_000L, 6.0)
        )

        val result = SensorAccuracyCalculator.calculateVerification(readings, meterReadings)
        assertNull(result)
    }

    @Test
    fun testRapidTrendFilteredOut() {
        val baseTime = 1_000_000_000L
        // Glucose changes from 5.0 to 7.0 in 5 minutes (rate = 2.0 / 5 = 0.4 mmol/L/min > 0.12)
        val readings = listOf(
            createReading(baseTime, 5.0),
            createReading(baseTime + 300_000L, 7.0)
        )
        val meterReadings = listOf(
            MeterReading(baseTime + 300_000L, 7.0)
        )

        val result = SensorAccuracyCalculator.calculateVerification(
            sensorReadings = readings,
            meterReadings = meterReadings,
            filterRapidTrends = true
        )
        // Point was filtered out because of rapid upward trajectory
        assertNull(result)

        // Without rapid trend filter, it pairs
        val resultNoFilter = SensorAccuracyCalculator.calculateVerification(
            sensorReadings = readings,
            meterReadings = meterReadings,
            filterRapidTrends = false
        )
        assertNotNull(resultNoFilter)
        assertEquals(1, resultNoFilter!!.pairedPointsCount)
    }

    @Test
    fun testIso15197TolerancesLowAndHighGlucose() {
        val baseTime = 1_000_000_000L
        // Case 1: Low glucose (< 5.55 mmol/L): boundary is ±0.83 mmol/L
        // Meter = 4.0, Sensor = 4.8 (diff = 0.80 <= 0.83) -> PASS
        // Case 2: High glucose (>= 5.55 mmol/L): boundary is ±15%
        // Meter = 10.0, Sensor = 11.4 (rel diff = 14.0% <= 15%) -> PASS
        // Case 3: High glucose fail: Meter = 10.0, Sensor = 12.0 (rel diff = 20% > 15%) -> FAIL
        val readings = listOf(
            createReading(baseTime, 4.8),
            createReading(baseTime + 600_000L, 11.4),
            createReading(baseTime + 1_200_000L, 12.0)
        )
        val meterReadings = listOf(
            MeterReading(baseTime, 4.0),
            MeterReading(baseTime + 600_000L, 10.0),
            MeterReading(baseTime + 1_200_000L, 10.0)
        )

        val result = SensorAccuracyCalculator.calculateVerification(
            sensorReadings = readings,
            meterReadings = meterReadings,
            filterRapidTrends = false
        )
        assertNotNull(result)
        assertEquals(3, result!!.pairedPointsCount)
        // 2 out of 3 passed ISO 15197: 66.7%
        assertEquals(66.7, result.isoPassPercent, 0.5)

        // Bias: ((4.8-4.0) + (11.4-10.0) + (12.0-10.0)) / 3 = (0.8 + 1.4 + 2.0) / 3 = 1.40 mmol/L
        assertEquals(1.40, result.meanBiasMmol, 0.05)
    }

    private fun createReading(timestamp: Long, valueMmol: Double): GlucoseReading {
        return GlucoseReading(
            timestamp = timestamp,
            valueMmol = valueMmol,
            trendArrow = "FLAT"
        )
    }
}
