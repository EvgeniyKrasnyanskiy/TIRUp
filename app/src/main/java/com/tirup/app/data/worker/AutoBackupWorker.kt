package com.tirup.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tirup.app.TirupApplication
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.SettingsRepositoryImpl

/**
 * Executes daily auto-backup reliably in the background via WorkManager,
 * bypassing BroadcastReceiver 10-second execution timeouts and preventing ANRs.
 */
class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing AutoBackupWorker...")
        return try {
            val force = inputData.getBoolean(KEY_FORCE, false)
            val app = context.applicationContext as? TirupApplication
            val database = app?.database ?: AppDatabase.getInstance(context)
            val settingsRepo = app?.settingsRepository ?: SettingsRepositoryImpl(context)

            val result = AutoBackupManager.maybeTriggerAutoBackup(
                context = context,
                database = database,
                settingsRepository = settingsRepo,
                force = force
            )
            if (result.isSuccess) {
                Log.i(TAG, "AutoBackupWorker completed successfully.")
                Result.success()
            } else {
                Log.w(TAG, "AutoBackupWorker completed with non-fatal issue: ${result.exceptionOrNull()?.message}")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AutoBackupWorker error: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val KEY_FORCE = "force_backup"
        private const val UNIQUE_WORK_NAME = "tirup_daily_auto_backup"

        fun enqueue(context: Context, force: Boolean) {
            val data = workDataOf(KEY_FORCE to force)
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "Enqueued AutoBackupWorker (force=$force)")
        }
    }
}
