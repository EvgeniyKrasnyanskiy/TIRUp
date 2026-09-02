package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseReadingDao {

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReading(): Flow<GlucoseReadingEntity?>

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentReadingsSync(limit: Int): List<GlucoseReadingEntity>

    @Query("SELECT * FROM glucose_readings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getReadingsBetween(startTime: Long, endTime: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getReadingsBetweenSync(startTime: Long, endTime: Long): List<GlucoseReadingEntity>

    @Query("SELECT COUNT(*) FROM glucose_readings")
    suspend fun getTotalCount(): Long

    @Query("SELECT MIN(timestamp) FROM glucose_readings")
    suspend fun getEarliestTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM glucose_readings")
    suspend fun getLatestTimestamp(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: GlucoseReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(readings: List<GlucoseReadingEntity>)

    @Query("DELETE FROM glucose_readings")
    suspend fun clearAll()
}
