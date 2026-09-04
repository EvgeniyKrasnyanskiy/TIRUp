package com.tirup.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tirup.app.data.local.dao.DailySummaryDao
import com.tirup.app.data.local.dao.GlucoseReadingDao
import com.tirup.app.data.local.dao.HistoricalReadingDao
import com.tirup.app.data.local.dao.TreatmentDao
import com.tirup.app.data.local.entity.DailySummaryEntity
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.data.local.entity.HistoricalReadingEntity
import com.tirup.app.data.local.entity.TreatmentEntity

@Database(
    entities = [
        GlucoseReadingEntity::class,
        DailySummaryEntity::class,
        HistoricalReadingEntity::class,
        TreatmentEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun historicalReadingDao(): HistoricalReadingDao
    abstract fun treatmentDao(): TreatmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE glucose_readings ADD COLUMN iob REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE glucose_readings ADD COLUMN cob REAL DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `treatments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `insulin_units` REAL,
                        `carbs_grams` REAL,
                        `notes` TEXT,
                        `source` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_treatments_timestamp` ON `treatments` (`timestamp`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tirup_database.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
