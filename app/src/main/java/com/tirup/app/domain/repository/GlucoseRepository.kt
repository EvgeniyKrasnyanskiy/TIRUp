package com.tirup.app.domain.repository

import com.tirup.app.domain.model.DailySummary
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface GlucoseRepository {
    fun getLatestReading(): Flow<GlucoseReading?>
    fun getRecentReadings(limit: Int): Flow<List<GlucoseReading>>
    fun getReadingsBetween(startTime: Long, endTime: Long): Flow<List<GlucoseReading>>
    fun getTreatmentsBetween(startTime: Long, endTime: Long): Flow<List<Treatment>>
    fun getDailySummariesBetween(startTime: Long, endTime: Long): Flow<List<DailySummary>>
    fun getStreakDays(): Flow<Int>
    suspend fun insertReading(reading: GlucoseReading)
    suspend fun insertReadingsBatch(readings: List<GlucoseReading>)
    suspend fun insertTreatment(treatment: Treatment): Long
    suspend fun insertTreatmentsBatch(treatments: List<Treatment>)
    suspend fun recalculateDailySummaries(startDate: Long, endDate: Long)
    suspend fun clearAllData()
}

interface SettingsRepository {
    fun getSettings(): Flow<UserSettings>
    suspend fun updateSettings(settings: UserSettings)
}
