package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.worker.AutoBackupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackupAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "BackupAlarmReceiver triggered: action=${intent?.action}")

        val isBoot = intent?.action == Intent.ACTION_BOOT_COMPLETED
        val isPackageReplaced = intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (isBoot || isPackageReplaced) {
            // Re-schedule daily alarm after reboot/update and check if backup was missed
            AutoBackupManager.scheduleNextDailyBackup(context)
            AutoBackupWorker.enqueue(context, force = false)

            // Restore floating bubble service if enabled
            val app = context.applicationContext as? com.tirup.app.TirupApplication
            app?.let { application ->
                val pendingResult = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val settings = application.settingsRepository.getSettings().first()
                        if (settings.isFloatingBubbleEnabled && android.provider.Settings.canDrawOverlays(context)) {
                            com.tirup.app.presentation.overlay.FloatingBubbleService.start(context)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore floating bubble after boot/update: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        } else {
            // Triggered by daily 23:59:59 alarm: enqueue worker and reschedule for tomorrow
            Log.i(TAG, "Enqueuing exact daily auto-backup via WorkManager...")
            AutoBackupWorker.enqueue(context, force = true)
            AutoBackupManager.scheduleNextDailyBackup(context)
        }
    }

    companion object {
        private const val TAG = "BackupAlarmReceiver"
    }
}
