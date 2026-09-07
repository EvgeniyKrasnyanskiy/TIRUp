package com.tirup.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.model.daysRemaining
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DeviceStatusReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing DeviceStatusReminderWorker...")
        return try {
            val settingsRepo = SettingsRepositoryImpl(context)
            val settings = settingsRepo.getSettings().first()

            if (!settings.isDeviceRemindersEnabled) {
                Log.d(TAG, "Device reminders disabled, skipping.")
                return Result.success()
            }

            val isRu = settings.language.equals("RU", ignoreCase = true)
            val sensor = settings.sensorStatus
            val pumpSet = settings.pumpSetStatus

            // Sensor check: notify if <=2 days remaining OR expired
            if (sensor.installedAt > 0L) {
                val sensorDays = sensor.daysRemaining
                if (sensorDays <= 2) {
                    GlucoseAlertManager.showDeviceReminderNotification(
                        context = context,
                        isSensor = true,
                        daysRemaining = sensorDays,
                        isRu = isRu
                    )
                }
            }

            // Pump set check: notify if <=1 day remaining OR expired
            val isPump = settings.patientProfile.therapyType in listOf("Инсулиновая помпа", "Insulin Pump")
            if (isPump && pumpSet.installedAt > 0L) {
                val pumpDays = pumpSet.daysRemaining
                if (pumpDays <= 1) {
                    GlucoseAlertManager.showDeviceReminderNotification(
                        context = context,
                        isSensor = false,
                        daysRemaining = pumpDays,
                        isRu = isRu
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in DeviceStatusReminderWorker", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "DeviceStatusReminderWorker"
        const val WORK_NAME = "tirup_device_status_reminder"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<DeviceStatusReminderWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
