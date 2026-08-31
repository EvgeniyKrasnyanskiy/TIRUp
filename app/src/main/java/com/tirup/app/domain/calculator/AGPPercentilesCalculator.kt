package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.HeatmapCell
import com.tirup.app.domain.model.TargetRanges
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AGPPercentilesCalculator {

    private const val BINS_COUNT = 48 // 30-minute intervals (48 * 30 = 1440 min)
    private const val MINUTES_PER_BIN = 30

    /**
     * Calculates Ambulatory Glucose Profile (AGP) 10th, 25th, 50th, 75th, and 90th percentiles
     * across 24-hour modal bins.
     */
    fun calculatePercentiles(
        readings: List<GlucoseReading>,
        binsCount: Int = BINS_COUNT
    ): List<AGPPercentileBin> {
        val minutesPerBin = 1440 / binsCount
        val binBuckets = Array(binsCount) { mutableListOf<Double>() }
        val calendar = Calendar.getInstance(TimeZone.getDefault())

        readings.forEach { reading ->
            calendar.timeInMillis = reading.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val totalMinutes = (hour * 60 + minute) % 1440
            val binIndex = (totalMinutes / minutesPerBin).coerceIn(0, binsCount - 1)
            binBuckets[binIndex].add(reading.valueMmol)
        }

        return (0 until binsCount).map { i ->
            val values = binBuckets[i].sorted()
            val startMinute = i * minutesPerBin
            val hour = startMinute / 60
            val min = startMinute % 60
            val formatted = String.format(Locale.US, "%02d:%02d", hour, min)

            if (values.isEmpty()) {
                AGPPercentileBin(
                    binIndex = i,
                    minuteOfDay = startMinute,
                    formattedTime = formatted,
                    p10 = 0.0,
                    p25 = 0.0,
                    p50 = 0.0,
                    p75 = 0.0,
                    p90 = 0.0,
                    readingsCount = 0
                )
            } else {
                AGPPercentileBin(
                    binIndex = i,
                    minuteOfDay = startMinute,
                    formattedTime = formatted,
                    p10 = calculatePercentile(values, 10.0),
                    p25 = calculatePercentile(values, 25.0),
                    p50 = calculatePercentile(values, 50.0),
                    p75 = calculatePercentile(values, 75.0),
                    p90 = calculatePercentile(values, 90.0),
                    readingsCount = values.size
                )
            }
        }
    }

    /**
     * Standard Linear Interpolation Percentile estimation (Percentile Rank P in [0..100]).
     */
    fun calculatePercentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        if (sortedValues.size == 1) return sortedValues[0]

        val rank = (percentile / 100.0) * (sortedValues.size - 1)
        val lowerIndex = rank.toInt().coerceIn(0, sortedValues.size - 1)
        val upperIndex = (lowerIndex + 1).coerceIn(0, sortedValues.size - 1)
        val weight = rank - lowerIndex

        return sortedValues[lowerIndex] + weight * (sortedValues[upperIndex] - sortedValues[lowerIndex])
    }

    /**
     * Calculates 24-hour heatmap matrix (Day x Hour).
     */
    fun calculateHeatmap(
        readings: List<GlucoseReading>,
        targetRanges: TargetRanges = TargetRanges(),
        maxDays: Int = 14
    ): List<List<HeatmapCell>> {
        if (readings.isEmpty()) return emptyList()

        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        // Group by Day (normalized to start of day)
        val groupedByDay = readings.groupBy { reading ->
            calendar.timeInMillis = reading.timestamp
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.toSortedMap()

        val recentDays = groupedByDay.keys.toList().takeLast(maxDays)

        return recentDays.mapIndexed { dayIdx, dayTimestamp ->
            val dayReadings = groupedByDay[dayTimestamp] ?: emptyList()
            val dayLabel = dateFormat.format(Date(dayTimestamp))

            val hourBuckets = Array(24) { mutableListOf<Double>() }
            dayReadings.forEach { r ->
                calendar.timeInMillis = r.timestamp
                val hour = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                hourBuckets[hour].add(r.valueMmol)
            }

            (0..23).map { hour ->
                val vals = hourBuckets[hour]
                val mean = if (vals.isNotEmpty()) vals.average() else null
                val category = mean?.let { targetRanges.categorize(it) }
                HeatmapCell(
                    dayIndex = dayIdx,
                    dayFormatted = dayLabel,
                    hourOfDay = hour,
                    meanMmol = mean,
                    rangeCategory = category
                )
            }
        }
    }
}
