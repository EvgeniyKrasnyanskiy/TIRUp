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
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class YearEndDigestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing YearEndDigestWorker...")
        return try {
            val settingsRepo = SettingsRepositoryImpl(context)
            val settings = settingsRepo.getSettings().first()
            val database = AppDatabase.getInstance(context)

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val currentYear = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) // Calendar.DECEMBER is 11, JANUARY is 0
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            // Determine if today is December 31 (evening >= 19:00) or January (any day in January if previous year missed)
            val isDec31Evening = (month == Calendar.DECEMBER && day == 31 && hour >= 19)
            val isJanCatchup = (month == Calendar.JANUARY && settings.lastYearEndDigestShownYear < currentYear - 1)

            val targetYear = if (isDec31Evening) currentYear else if (isJanCatchup) currentYear - 1 else null

            if (targetYear != null && settings.lastYearEndDigestShownYear < targetYear) {
                // 1. Seal and archive this completed year's readings
                AutoBackupManager.archiveYear(targetYear, context, database)

                // 2. Compute the target year's statistics
                val startOfYear = Calendar.getInstance().apply {
                    set(targetYear, Calendar.JANUARY, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfYear = Calendar.getInstance().apply {
                    set(targetYear, Calendar.DECEMBER, 31, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val readings = database.glucoseReadingDao().getReadingsBetweenSync(startOfYear, endOfYear)
                if (readings.isNotEmpty()) {
                    val inRangeCount = readings.count { it.valueMmol in 3.9..10.0 }
                    val tirPercent = (inRangeCount.toDouble() / readings.size.toDouble()) * 100.0
                    val meanMmol = readings.map { it.valueMmol }.average()
                    val gmi = 3.31 + 0.431 * meanMmol

                    val isRu = settings.language.equals("RU", ignoreCase = true)
                    GlucoseAlertManager.showYearEndDigestNotification(
                        context = context,
                        year = targetYear,
                        tirPercent = tirPercent,
                        meanMmol = meanMmol,
                        gmi = gmi,
                        isRu = isRu
                    )

                    settingsRepo.updateSettings(
                        settings.copy(lastYearEndDigestShownYear = targetYear)
                    )
                    Log.i(TAG, "Delivered Year-End Digest notification for year $targetYear: TIR=%.1f%%, GMI=%.1f%%".format(tirPercent, gmi))
                }
            }

            // Always ensure the next December 31 20:00 exact trigger is scheduled
            scheduleNextDec31Exact(context)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "YearEndDigestWorker failed: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "YearEndDigestWorker"
        const val PERIODIC_WORK_NAME = "tirup_year_end_periodic"
        const val EXACT_WORK_NAME = "tirup_year_end_exact"

        fun schedule(context: Context) {
            // 1. Periodic background check every 12 hours
            val periodicWork = PeriodicWorkRequestBuilder<YearEndDigestWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )

            // 2. Exact schedule for December 31 at 20:00
            scheduleNextDec31Exact(context)
        }

        fun scheduleNextDec31Exact(context: Context) {
            val now = System.currentTimeMillis()
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (targetCal.timeInMillis <= now) {
                targetCal.add(Calendar.YEAR, 1)
            }
            val delayMs = targetCal.timeInMillis - now
            if (delayMs > 0) {
                val oneTimeWork = OneTimeWorkRequestBuilder<YearEndDigestWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    EXACT_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWork
                )
                Log.d(TAG, "Scheduled next Dec 31 20:00 exact digest check (in ${delayMs / 3600000} hours)")
            }
        }
    }
}
