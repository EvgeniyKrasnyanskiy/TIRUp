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
import com.tirup.app.domain.model.millisRemaining
import com.tirup.app.domain.model.isPumpTherapy
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
            val lancet = settings.lancetStatus

            // Sensor check: notify if <=2 days remaining OR expired
            if (settings.isSensorReminderEnabled && sensor.installedAt > 0L) {
                val sensorMillis = sensor.millisRemaining
                if (sensorMillis <= 2 * 86_400_000L) {
                    GlucoseAlertManager.showDeviceReminderNotification(
                        context = context,
                        deviceType = 0,
                        millisRemaining = sensorMillis,
                        isRu = isRu
                    )
                } else {
                    GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 0)
                }
            } else {
                GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 0)
            }

            // Pump set check: notify if <=1 day remaining OR expired
            if (settings.isPumpReminderEnabled && pumpSet.installedAt > 0L) {
                val pumpMillis = pumpSet.millisRemaining
                if (pumpMillis <= 86_400_000L) {
                    GlucoseAlertManager.showDeviceReminderNotification(
                        context = context,
                        deviceType = 1,
                        millisRemaining = pumpMillis,
                        isRu = isRu
                    )
                } else {
                    GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 1)
                }
            } else {
                GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 1)
            }

            // Lancet check: notify if <=1 day remaining OR expired
            if (settings.isLancetReminderEnabled && lancet.installedAt > 0L) {
                val lancetMillis = lancet.millisRemaining
                if (lancetMillis <= 86_400_000L) {
                    GlucoseAlertManager.showDeviceReminderNotification(
                        context = context,
                        deviceType = 2,
                        millisRemaining = lancetMillis,
                        isRu = isRu
                    )
                } else {
                    GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 2)
                }
            } else {
                GlucoseAlertManager.cancelDeviceReminderNotification(context, deviceType = 2)
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
