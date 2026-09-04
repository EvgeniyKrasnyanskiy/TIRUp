package com.tirup.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.calculator.WeeklyDigestCalculator
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WeeklyDigestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing WeeklyDigestWorker...")
        return try {
            val settingsRepo = SettingsRepositoryImpl(context)
            val settings = settingsRepo.getSettings().first()

            if (!settings.isWeeklyDigestEnabled) {
                Log.d(TAG, "Weekly digest is disabled in user settings, skipping.")
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val weekMs = 7L * 24 * 60 * 60 * 1000L
            val prevStart = now - 2 * weekMs

            val database = AppDatabase.getInstance(context)
            val entities = database.glucoseReadingDao().getReadingsBetweenSync(prevStart, now)
            val readings = entities.map { it.toDomain() }

            if (readings.isEmpty()) {
                Log.d(TAG, "No readings available for weekly digest, skipping notification.")
                return Result.success()
            }

            val digest = WeeklyDigestCalculator.calculateForReferenceTimestamp(
                allReadings = readings,
                referenceTime = now,
                settings = settings
            )

            val isRu = settings.language.equals("RU", ignoreCase = true)
            GlucoseAlertManager.showWeeklyDigestNotification(context, digest, isRu)
            Log.d(TAG, "Weekly digest notification delivered successfully.")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing WeeklyDigestWorker", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "WeeklyDigestWorker"
        const val WORK_NAME = "tirup_weekly_digest_worker"
        const val WORK_NAME_ONCE = "tirup_weekly_digest_once"

        fun schedule(context: Context) {
            val initialDelayMs = calculateInitialDelayToSunday20()
            Log.d(TAG, "Scheduling weekly digest with initial delay: ${initialDelayMs / 1000 / 60} minutes")

            val workRequest = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun triggerImmediately(context: Context) {
            Log.d(TAG, "Triggering immediate one-time weekly digest calculation")
            val workRequest = OneTimeWorkRequestBuilder<WeeklyDigestWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun calculateInitialDelayToSunday20(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
