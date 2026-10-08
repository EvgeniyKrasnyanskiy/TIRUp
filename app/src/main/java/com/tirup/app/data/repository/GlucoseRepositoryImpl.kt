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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
            if (summaries.size <= 2) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val total = readingDao.getTotalCount()
                        if (total > 10) {
                            val earliest = readingDao.getEarliestTimestamp()
                            val latest = readingDao.getLatestTimestamp()
                            if (earliest != null && latest != null) {
                                val expectedDays = ((latest - earliest) / 86400000L).toInt() + 1
                                if (summaryDao.getCount() < expectedDays) {
                                    recalculateDailySummaries(earliest, latest)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            calculateStreakDays(summaries)
        }
    }

    override suspend fun insertReading(reading: GlucoseReading) {
        insertReadingFromSource(reading, com.tirup.app.domain.model.DataSourcePriority.LOCAL_XDRIP)
    }

    override suspend fun insertReadingsBatch(readings: List<GlucoseReading>) {
        insertReadingsBatchFromSource(readings, com.tirup.app.domain.model.DataSourcePriority.LOCAL_XDRIP)
    }

    override suspend fun insertReadingFromSource(
        reading: GlucoseReading,
        priority: com.tirup.app.domain.model.DataSourcePriority
    ) = withContext(Dispatchers.IO) {
        val windowMs = 25_000L // 25s window: safe for 1-min CGM cadence, still absorbs duplicate echoes from xDrip broadcast
        val existing = readingDao.getReadingsBetweenSync(
            reading.timestamp - windowMs,
            reading.timestamp + windowMs
        )
        if (existing.isNotEmpty()) {
            val closest = existing.minByOrNull { kotlin.math.abs(it.timestamp - reading.timestamp) }!!
            // Check if we should enrich or update based on priority and missing fields
            val shouldEnrichIob = reading.iob != null && reading.iob != closest.iob
            val shouldEnrichCob = reading.cob != null && reading.cob != closest.cob
            val shouldEnrichArrow = !reading.trendArrow.isNullOrBlank() && closest.trendArrow.isNullOrBlank()
            val shouldUpdateValue = priority.rank >= com.tirup.app.domain.model.DataSourcePriority.WIFI_LAN.rank && closest.valueMmol <= 0.0

            if (shouldEnrichIob || shouldEnrichCob || shouldEnrichArrow || shouldUpdateValue) {
                val merged = closest.copy(
                    valueMmol = if (shouldUpdateValue && reading.valueMmol > 0.0) reading.valueMmol else closest.valueMmol,
                    trendArrow = if (!reading.trendArrow.isNullOrBlank()) reading.trendArrow else closest.trendArrow,
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

    override suspend fun insertReadingsBatchFromSource(
        readings: List<GlucoseReading>,
        priority: com.tirup.app.domain.model.DataSourcePriority
    ) = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext
        val windowMs = 25_000L // 25s window prevents false deduplication of 1-minute CGM readings while eliminating duplicate echoes
        val toInsert = mutableListOf<GlucoseReading>()
        
        for (r in readings) {
            val existing = readingDao.getReadingsBetweenSync(r.timestamp - windowMs, r.timestamp + windowMs)
            val alreadyInBatch = toInsert.any { kotlin.math.abs(it.timestamp - r.timestamp) < windowMs }
            if (existing.isEmpty() && !alreadyInBatch) {
                toInsert.add(r)
            } else if (existing.isNotEmpty()) {
                val closest = existing.minByOrNull { kotlin.math.abs(it.timestamp - r.timestamp) }!!
                val shouldEnrichIob = r.iob != null && r.iob != closest.iob
                val shouldEnrichCob = r.cob != null && r.cob != closest.cob
                val shouldEnrichArrow = !r.trendArrow.isNullOrBlank() && closest.trendArrow.isNullOrBlank()
                if (shouldEnrichIob || shouldEnrichCob || shouldEnrichArrow) {
                    val merged = closest.copy(
                        trendArrow = if (!r.trendArrow.isNullOrBlank()) r.trendArrow else closest.trendArrow,
                        iob = r.iob ?: closest.iob,
                        cob = r.cob ?: closest.cob
                    )
                    readingDao.insert(merged)
                }
            }
        }
        
        if (toInsert.isNotEmpty()) {
            val entities = toInsert.map { GlucoseReadingEntity.fromDomain(it) }
            readingDao.insertBatch(entities)

            val minTs = toInsert.minOf { it.timestamp }
            val maxTs = toInsert.maxOf { it.timestamp }
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

    override suspend fun ensureDailySummariesUpToDate() = withContext(Dispatchers.IO) {
        val totalReadings = readingDao.getTotalCount()
        if (totalReadings == 0L) return@withContext
        val earliest = readingDao.getEarliestTimestamp() ?: return@withContext
        val latest = readingDao.getLatestTimestamp() ?: return@withContext

        val expectedDays = ((latest - earliest) / 86400000L).toInt() + 1
        val summaryCount = summaryDao.getCount()
        if (summaryCount < expectedDays) {
            recalculateDailySummaries(earliest, latest)
        }
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

    override suspend fun getTreatmentById(id: Long): Treatment? = withContext(Dispatchers.IO) {
        treatmentDao.getById(id)?.toDomain()
    }

    override suspend fun deleteTreatmentById(id: Long) = withContext(Dispatchers.IO) {
        treatmentDao.deleteById(id)
    }

    override suspend fun clearTreatments() = withContext(Dispatchers.IO) {
        treatmentDao.clearAll()
    }

    override suspend fun clearAllData() = withContext(Dispatchers.IO) {
        readingDao.clearAll()
        summaryDao.clearAll()
        treatmentDao.clearAll()
    }

    override suspend fun getHistoricalBestStreak(): Int = withContext(Dispatchers.IO) {
        val summaries = try {
            summaryDao.getAllSummaries().first()
        } catch (_: Exception) {
            emptyList()
        }
        calculateMaxHistoricalStreak(summaries)
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

        fun calculateMaxHistoricalStreak(
            summaries: List<DailySummaryEntity>
        ): Int {
            if (summaries.isEmpty()) return 0
            val sorted = summaries
                .filter { it.tir >= 70.0 && it.count >= 10 }
                .sortedBy { it.dateTimestamp }

            if (sorted.isEmpty()) return 0

            var maxStreak = 1
            var currentStreak = 1
            val cal = Calendar.getInstance(TimeZone.getDefault())

            for (i in 1 until sorted.size) {
                val prevTs = sorted[i - 1].dateTimestamp
                val currTs = sorted[i].dateTimestamp
                cal.timeInMillis = prevTs
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val expectedNextDay = cal.timeInMillis

                val diff = kotlin.math.abs(currTs - expectedNextDay)
                if (diff < 3600000L) { // consecutive day (allowing DST 1h diff)
                    currentStreak++
                    if (currentStreak > maxStreak) {
                        maxStreak = currentStreak
                    }
                } else if (kotlin.math.abs(currTs - prevTs) < 3600000L) {
                    // Same day duplicate - ignore
                } else {
                    currentStreak = 1
                }
            }
            return maxStreak
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
