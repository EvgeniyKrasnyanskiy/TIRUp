package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.DailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Query("SELECT * FROM daily_summaries WHERE date_timestamp BETWEEN :startDate AND :endDate ORDER BY date_timestamp ASC")
    fun getSummariesBetween(startDate: Long, endDate: Long): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE date_timestamp BETWEEN :startDate AND :endDate ORDER BY date_timestamp ASC")
    suspend fun getSummariesBetweenSync(startDate: Long, endDate: Long): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summaries ORDER BY date_timestamp DESC")
    fun getAllSummaries(): Flow<List<DailySummaryEntity>>

    @Query("SELECT COUNT(*) FROM daily_summaries")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(summary: DailySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(summaries: List<DailySummaryEntity>)

    @Query("DELETE FROM daily_summaries")
    suspend fun clearAll()
}
