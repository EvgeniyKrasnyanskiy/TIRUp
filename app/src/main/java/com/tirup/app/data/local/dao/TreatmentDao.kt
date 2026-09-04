package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {

    @Query("SELECT * FROM treatments WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getTreatmentsBetween(startTime: Long, endTime: Long): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getTreatmentsBetweenSync(startTime: Long, endTime: Long): List<TreatmentEntity>

    @Query("SELECT * FROM treatments ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTreatmentsSync(limit: Int): List<TreatmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(treatment: TreatmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(treatments: List<TreatmentEntity>)

    @Query("SELECT COUNT(*) FROM treatments WHERE timestamp BETWEEN :minTime AND :maxTime AND ((:insulin IS NULL AND insulin_units IS NULL) OR ABS(insulin_units - :insulin) < 0.05) AND ((:carbs IS NULL AND carbs_grams IS NULL) OR ABS(carbs_grams - :carbs) < 0.5)")
    suspend fun countSimilar(minTime: Long, maxTime: Long, insulin: Double?, carbs: Double?): Int

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM treatments")
    suspend fun clearAll()
}
