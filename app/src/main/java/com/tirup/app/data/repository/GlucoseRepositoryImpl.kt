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
            var streak = 0
            // Traverse from newest to oldest
            for (summary in summaries) {
                // If TIR >= 70%, streak increments
                if (summary.tir >= 70.0 && summary.count >= 10) {
                    streak++
                } else {
                    break
                }
            }
            streak
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

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
