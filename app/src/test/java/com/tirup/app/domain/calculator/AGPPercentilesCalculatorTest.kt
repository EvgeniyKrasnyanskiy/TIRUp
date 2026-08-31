package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AGPPercentilesCalculatorTest {

    @Test
    fun testPercentileInterpolation() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

        // 50th percentile of [1, 2, 3, 4, 5] should be 3.0
        val p50 = AGPPercentilesCalculator.calculatePercentile(values, 50.0)
        assertEquals(3.0, p50, 0.001)

        // 0th percentile should be 1.0, 100th should be 5.0
        assertEquals(1.0, AGPPercentilesCalculator.calculatePercentile(values, 0.0), 0.001)
        assertEquals(5.0, AGPPercentilesCalculator.calculatePercentile(values, 100.0), 0.001)

        // 25th percentile should be 2.0
        assertEquals(2.0, AGPPercentilesCalculator.calculatePercentile(values, 25.0), 0.001)

        // 75th percentile should be 4.0
        assertEquals(4.0, AGPPercentilesCalculator.calculatePercentile(values, 75.0), 0.001)
    }

    @Test
    fun testModalBinsCalculation() {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 15) // Bin 24 (12:00 - 12:30)
        val t1 = calendar.timeInMillis

        val readings = listOf(
            GlucoseReading(timestamp = t1, valueMmol = 5.0),
            GlucoseReading(timestamp = t1 + 60000, valueMmol = 7.0),
            GlucoseReading(timestamp = t1 + 120000, valueMmol = 9.0)
        )

        val bins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)
        assertEquals(48, bins.size)

        val noonBin = bins[24]
        assertEquals("12:00", noonBin.formattedTime)
        assertEquals(3, noonBin.readingsCount)
        assertEquals(7.0, noonBin.p50, 0.001) // Median of 5, 7, 9
        assertTrue(noonBin.p10 in 5.0..7.0)
        assertTrue(noonBin.p90 in 7.0..9.0)
    }
}
