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
        val windowMs = 60_000L
        val existing = readingDao.getReadingsBetweenSync(
            reading.timestamp - windowMs,
            reading.timestamp + windowMs
        )
        if (existing.isNotEmpty()) {
            val closest = existing.minByOrNull { kotlin.math.abs(it.timestamp - reading.timestamp) }!!
            // If already exists within 60s, enrich it if the incoming reading has extra information (e.g. IoB/trend)
            val shouldUpdate = (reading.iob != null && closest.iob == null) ||
                               (reading.cob != null && closest.cob == null) ||
                               (!reading.trendArrow.isNullOrBlank() && closest.trendArrow.isNullOrBlank())
            if (shouldUpdate) {
                val merged = closest.copy(
                    valueMmol = if (reading.valueMmol > 0.0) reading.valueMmol else closest.valueMmol,
                    trendArrow = reading.trendArrow ?: closest.trendArrow,
                    iob = reading.iob ?: closest.iob,
                    cob = reading.cob ?: closest.cob
                )
                readingDao.insert(merged)
            }
            return@withContext
        }

        readingDao.insert(GlucoseReadingEntity.fromDomain(reading))

        // Recalculate summary for today
        val startOfDay = getStartOfDay(reading.timestamp)
        val endOfDay = startOfDay + 86400000L - 1
        recalculateDailySummaries(startOfDay, endOfDay)
    }

    override suspend fun insertReadingsBatch(readings: List<GlucoseReading>) = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext
        val windowMs = 60_000L
        val filtered = mutableListOf<GlucoseReading>()
        
        for (r in readings) {
            val existing = readingDao.getReadingsBetweenSync(r.timestamp - windowMs, r.timestamp + windowMs)
            val alreadyInBatch = filtered.any { kotlin.math.abs(it.timestamp - r.timestamp) < windowMs }
            if (existing.isEmpty() && !alreadyInBatch) {
                filtered.add(r)
            }
        }
        
        if (filtered.isNotEmpty()) {
            val entities = filtered.map { GlucoseReadingEntity.fromDomain(it) }
            readingDao.insertBatch(entities)

            val minTs = filtered.minOf { it.timestamp }
            val maxTs = filtered.maxOf { it.timestamp }
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

    override suspend fun purgeDuplicateReadings(): Int = withContext(Dispatchers.IO) {
        val totalCount = readingDao.getTotalCount()
        if (totalCount <= 1) return@withContext 0

        var offset = 0
        val batchSize = 1000
        var deletedCount = 0
        var lastKeptEntity: GlucoseReadingEntity? = null
        var earliestModifiedTs: Long? = null
        var latestModifiedTs: Long? = null

        while (true) {
            val page = readingDao.getReadingsPaginated(batchSize, offset)
            if (page.isEmpty()) break

            for (current in page) {
                val prev = lastKeptEntity
                if (prev != null && kotlin.math.abs(current.timestamp - prev.timestamp) < 60_000L) {
                    val prevScore = (if (prev.iob != null) 2 else 0) + (if (!prev.trendArrow.isNullOrBlank()) 1 else 0)
                    val currScore = (if (current.iob != null) 2 else 0) + (if (!current.trendArrow.isNullOrBlank()) 1 else 0)

                    val toDeleteId = if (currScore > prevScore) {
                        lastKeptEntity = current
                        prev.id
                    } else {
                        current.id
                    }
                    readingDao.deleteById(toDeleteId)
                    deletedCount++

                    if (earliestModifiedTs == null || current.timestamp < earliestModifiedTs) earliestModifiedTs = current.timestamp
                    if (latestModifiedTs == null || current.timestamp > latestModifiedTs) latestModifiedTs = current.timestamp
                } else {
                    lastKeptEntity = current
                }
            }

            offset += batchSize
        }

        if (deletedCount > 0 && earliestModifiedTs != null && latestModifiedTs != null) {
            recalculateDailySummaries(earliestModifiedTs, latestModifiedTs)
        }

        deletedCount
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
