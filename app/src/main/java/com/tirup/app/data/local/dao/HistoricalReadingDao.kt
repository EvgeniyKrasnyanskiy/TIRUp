package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.HistoricalReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricalReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(readings: List<HistoricalReadingEntity>)

    @Query("SELECT * FROM historical_readings ORDER BY timestamp ASC")
    fun getAllReadings(): Flow<List<HistoricalReadingEntity>>

    @Query("SELECT * FROM historical_readings ORDER BY timestamp ASC")
    suspend fun getAllReadingsSync(): List<HistoricalReadingEntity>

    @Query("SELECT COUNT(*) FROM historical_readings")
    fun getReadingsCount(): Flow<Int>

    @Query("SELECT MIN(timestamp) FROM historical_readings")
    suspend fun getMinTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM historical_readings")
    suspend fun getMaxTimestamp(): Long?

    @Query("DELETE FROM historical_readings")
    suspend fun clearAll()
}
