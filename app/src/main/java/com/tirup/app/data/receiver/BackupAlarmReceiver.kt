package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.data.backup.AutoBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BackupAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "BackupAlarmReceiver triggered: action=${intent?.action}")

        val app = context.applicationContext as? TirupApplication ?: return

        val pendingResult = goAsync()
        scope.launch {
            try {
                // If triggered by BOOT_COMPLETED, only re-schedule the alarm
                if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                    AutoBackupManager.scheduleNextDailyBackup(context)
                    // Check if yesterday's backup was missed while device was powered off
                    AutoBackupManager.maybeTriggerAutoBackup(
                        context = context,
                        database = app.database,
                        settingsRepository = app.settingsRepository,
                        force = false
                    )
                } else {
                    // Triggered by exact 23:59:59 alarm
                    Log.i(TAG, "Executing exact 23:59:59 daily auto-backup...")
                    AutoBackupManager.maybeTriggerAutoBackup(
                        context = context,
                        database = app.database,
                        settingsRepository = app.settingsRepository,
                        force = true
                    )
                    // Reschedule for next day's 23:59:59
                    AutoBackupManager.scheduleNextDailyBackup(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in BackupAlarmReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BackupAlarmReceiver"
    }
}
