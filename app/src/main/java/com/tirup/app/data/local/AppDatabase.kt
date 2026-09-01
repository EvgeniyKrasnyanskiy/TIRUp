package com.tirup.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tirup.app.data.local.dao.DailySummaryDao
import com.tirup.app.data.local.dao.GlucoseReadingDao
import com.tirup.app.data.local.dao.HistoricalReadingDao
import com.tirup.app.data.local.entity.DailySummaryEntity
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.data.local.entity.HistoricalReadingEntity

@Database(
    entities = [
        GlucoseReadingEntity::class,
        DailySummaryEntity::class,
        HistoricalReadingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun historicalReadingDao(): HistoricalReadingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tirup_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
