package com.tirup.app.data.repository

import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.DailySummaryEntity
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.data.local.entity.TreatmentEntity
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.DailySummary
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.repository.GlucoseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

class GlucoseRepositoryImpl(
    private val database: AppDatabase
) : GlucoseRepository {

    private val readingDao = database.glucoseReadingDao()
    private val summaryDao = database.dailySummaryDao()
    private val treatmentDao = database.treatmentDao()

    override fun getLatestReading(): Flow<GlucoseReading?> {
        return readingDao.getLatestReading().map { it?.toDomain() }
    }

    override fun getRecentReadings(limit: Int): Flow<List<GlucoseReading>> {
        return readingDao.getRecentReadings(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getReadingsBetween(startTime: Long, endTime: Long): Flow<List<GlucoseReading>> {
        return readingDao.getReadingsBetween(startTime, endTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTreatmentsBetween(startTime: Long, endTime: Long): Flow<List<Treatment>> {
        return treatmentDao.getTreatmentsBetween(startTime, endTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getDailySummariesBetween(startTime: Long, endTime: Long): Flow<List<DailySummary>> {
        return summaryDao.getSummariesBetween(startTime, endTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getStreakDays(): Flow<Int> {
        return summaryDao.getAllSummaries().map { summaries ->
            calculateStreakDays(summaries)
        }
    }

    override suspend fun insertReading(reading: GlucoseReading) = withContext(Dispatchers.IO) {
        readingDao.insert(GlucoseReadingEntity.fromDomain(reading))

        // Recalculate summary for today
        val startOfDay = getStartOfDay(reading.timestamp)
        val endOfDay = startOfDay + 86400000L - 1
        recalculateDailySummaries(startOfDay, endOfDay)
    }

    override suspend fun insertReadingsBatch(readings: List<GlucoseReading>) = withContext(Dispatchers.IO) {
        val entities = readings.map { GlucoseReadingEntity.fromDomain(it) }
        readingDao.insertBatch(entities)

        if (readings.isNotEmpty()) {
            val minTs = readings.minOf { it.timestamp }
            val maxTs = readings.maxOf { it.timestamp }
            recalculateDailySummaries(minTs, maxTs)
        }
    }

    override suspend fun recalculateDailySummaries(startDate: Long, endDate: Long) = withContext(Dispatchers.IO) {
        val startOfDay = getStartOfDay(startDate)
        val endOfDay = getStartOfDay(endDate) + 86400000L - 1

        val allReadings = readingDao.getReadingsBetweenSync(startOfDay, endOfDay)
        if (allReadings.isEmpty()) return@withContext

        val defaultTargets = TargetRanges()

        // Group by normalized day timestamp
        val groupedByDay = allReadings.groupBy { entity ->
            getStartOfDay(entity.timestamp)
        }

        val summaries = groupedByDay.map { (dayStart, dayEntities) ->
            val domainReadings = dayEntities.map { it.toDomain() }
            val stats = GlucoseMetricsCalculator.calculateStatistics(domainReadings, defaultTargets)

            DailySummaryEntity(
                dateTimestamp = dayStart,
                mean = stats.meanMmol,
                tir = stats.tirPercent,
                ting = stats.tingPercent,
                tbrVeryLow = stats.tbrVeryLowPercent,
                tbrLow = stats.tbrLowPercent,
                tarHigh = stats.tarHighPercent,
                tarVeryHigh = stats.tarVeryHighPercent,
                sd = stats.sdMmol,
                cv = stats.cvPercent,
                count = stats.totalCount
            )
        }

        summaryDao.insertBatch(summaries)
    }

    override suspend fun insertTreatment(treatment: Treatment): Long = withContext(Dispatchers.IO) {
        treatmentDao.insert(TreatmentEntity.fromDomain(treatment))
    }

    override suspend fun insertTreatmentsBatch(treatments: List<Treatment>) = withContext(Dispatchers.IO) {
        treatmentDao.insertBatch(treatments.map { TreatmentEntity.fromDomain(it) })
    }

    override suspend fun clearAllData() = withContext(Dispatchers.IO) {
        readingDao.clearAll()
        summaryDao.clearAll()
        treatmentDao.clearAll()
    }

    companion object {
        fun calculateStreakDays(
            summaries: List<DailySummaryEntity>,
            nowTimestamp: Long = System.currentTimeMillis()
        ): Int {
            val todayStart = getStartOfDay(nowTimestamp)
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.timeInMillis = todayStart
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStart = cal.timeInMillis

            // Separate current in-progress day from completed past days
            val todaySummary = summaries.firstOrNull { it.dateTimestamp == todayStart }
            val completedSummaries = summaries.filter { it.dateTimestamp < todayStart }

            var completedStreak = 0
            var expectedDay = yesterdayStart

            for (summary in completedSummaries) {
                // Must strictly match expected consecutive calendar day (no missing days)
                if (summary.dateTimestamp == expectedDay && summary.tir >= 70.0 && summary.count >= 10) {
                    completedStreak++
                    cal.timeInMillis = expectedDay
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    expectedDay = cal.timeInMillis
                } else {
                    break
                }
            }

            // If today is currently meeting the target (TIR >= 70% with at least 10 readings),
            // award today's bonus (+1) on top of completed streak
            val todayBonus = if (todaySummary != null && todaySummary.tir >= 70.0 && todaySummary.count >= 10) 1 else 0

            return completedStreak + todayBonus
        }

        fun getStartOfDay(timestamp: Long): Long {
            val calendar = Calendar.getInstance(TimeZone.getDefault())
            calendar.timeInMillis = timestamp
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}
