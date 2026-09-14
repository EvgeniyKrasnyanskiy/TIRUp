package com.tirup.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class Hba1cReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing Hba1cReminderWorker...")
        return try {
            val settingsRepo = SettingsRepositoryImpl(context)
            val settings = settingsRepo.getSettings().first()

            if (!settings.isHba1cReminderEnabled) {
                Log.d(TAG, "HbA1c reminders disabled in settings, skipping.")
                return Result.success()
            }

            val database = AppDatabase.getInstance(context)
            val earliestReadingTimestamp = database.glucoseReadingDao().getEarliestTimestamp() ?: 0L
            val latestRecordTimestamp = settings.hba1cRecords.maxOfOrNull { it.timestamp } ?: 0L
            val skippedQuarterTimestamp = settings.hba1cSkippedQuarterTimestamp
            val baseTimestamp = maxOf(earliestReadingTimestamp, latestRecordTimestamp, skippedQuarterTimestamp)

            if (baseTimestamp == 0L) {
                Log.d(TAG, "No glucose readings or lab records found, skipping.")
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val elapsedDays = ((now - baseTimestamp) / 86_400_000L).toInt()

            if (elapsedDays < 90) {
                Log.d(TAG, "Milestone not reached: $elapsedDays/90 days elapsed, skipping.")
                return Result.success()
            }

            // Cap at maximum 2 reminders per quarterly milestone cycle
            if (settings.hba1cRemindersCountInCycle >= 2) {
                Log.d(TAG, "Already sent 2 reminders for this quarterly cycle, waiting for new lab record or skip.")
                return Result.success()
            }

            // If 1 reminder has already been sent, ensure at least 14 days interval before the 2nd
            if (settings.hba1cRemindersCountInCycle == 1) {
                val daysSinceLastReminder = ((now - settings.lastHba1cReminderTimestamp) / 86_400_000L).toInt()
                if (daysSinceLastReminder < 14) {
                    Log.d(TAG, "Only $daysSinceLastReminder days since 1st reminder (needs 14 days), skipping.")
                    return Result.success()
                }
            }

            // Calculate 90-day GMI from database readings
            val ninetyDaysAgo = now - 90L * 86_400_000L
            val readings = database.glucoseReadingDao().getReadingsBetweenSync(ninetyDaysAgo, now)
            val meanMmol = if (readings.isNotEmpty()) readings.map { it.valueMmol }.average() else null
            val gmi = if (meanMmol != null) 3.31 + 0.431 * meanMmol else null

            val isRepeat = settings.hba1cRemindersCountInCycle == 1
            val isRu = settings.language.equals("RU", ignoreCase = true)

            GlucoseAlertManager.showHba1cReminderNotification(
                context = context,
                elapsedDays = elapsedDays,
                gmi = gmi,
                meanMmol = meanMmol,
                isRepeat = isRepeat,
                isRu = isRu
            )

            val updatedCount = settings.hba1cRemindersCountInCycle + 1
            settingsRepo.updateSettings(
                settings.copy(
                    hba1cRemindersCountInCycle = updatedCount,
                    lastHba1cReminderTimestamp = now
                )
            )

            Log.d(TAG, "HbA1c quarterly reminder delivered (reminder #$updatedCount).")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Hba1cReminderWorker", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "Hba1cReminderWorker"
        const val WORK_NAME = "tirup_hba1c_reminder_worker"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<Hba1cReminderWorker>(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
