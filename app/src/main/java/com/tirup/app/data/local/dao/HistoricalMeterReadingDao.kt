package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.HistoricalMeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricalMeterReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(readings: List<HistoricalMeterReadingEntity>)

    @Query("SELECT * FROM historical_meter_readings ORDER BY timestamp ASC")
    fun getAllReadings(): Flow<List<HistoricalMeterReadingEntity>>

    @Query("SELECT * FROM historical_meter_readings ORDER BY timestamp ASC")
    suspend fun getAllReadingsSync(): List<HistoricalMeterReadingEntity>

    @Query("SELECT COUNT(*) FROM historical_meter_readings")
    suspend fun getCount(): Int

    @Query("DELETE FROM historical_meter_readings")
    suspend fun clearAll()
}
