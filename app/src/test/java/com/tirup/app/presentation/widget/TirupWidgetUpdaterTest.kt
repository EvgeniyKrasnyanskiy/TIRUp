package com.tirup.app.presentation.widget

import com.tirup.app.domain.model.GlucoseReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TirupWidgetUpdaterTest {

    @Test
    fun test1MinuteCadenceFinds5MinuteAgoReading() {
        val now = 10000000L
        // 10 readings, 1 minute apart (timestamps: now - 9m, now - 8m, ..., now)
        // Values: at t=now - 5m value is 5.0, at t=now value is 5.8
        // Neighbor at t=now - 1m value is 5.7
        val readings = (0..9).map { i ->
            val t = now - (9 - i) * 60_000L
            val v = when (i) {
                4 -> 5.0 // t = now - 5m (index 4 out of 0..9)
                8 -> 5.7 // t = now - 1m (adjacent point)
                9 -> 5.8 // t = now (latest)
                else -> 5.0 + i * 0.1
            }
            GlucoseReading(timestamp = t, valueMmol = v)
        }
        val latest = readings.last()

        val delta = TirupWidgetUpdater.calculate5MinDelta(latest, readings)

        // Delta should be latest (5.8) - 5min reading (5.0) = +0.8, NOT 5.8 - 5.7 (+0.1)
        assertEquals(0.8, delta ?: 0.0, 0.001)
    }

    @Test
    fun test5MinuteCadenceUsesPreviousReading() {
        val now = 10000000L
        val reading1 = GlucoseReading(timestamp = now - 5 * 60_000L, valueMmol = 6.2)
        val reading2 = GlucoseReading(timestamp = now, valueMmol = 6.6)
        val readings = listOf(reading1, reading2)

        val delta = TirupWidgetUpdater.calculate5MinDelta(reading2, readings)

        assertEquals(0.4, delta ?: 0.0, 0.001)
    }

    @Test
    fun testSingleReadingReturnsNullDelta() {
        val now = 10000000L
        val reading = GlucoseReading(timestamp = now, valueMmol = 5.5)

        val delta = TirupWidgetUpdater.calculate5MinDelta(reading, listOf(reading))

        assertNull(delta)
    }
}
