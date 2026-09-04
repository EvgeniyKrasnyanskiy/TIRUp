package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.worker.AutoBackupWorker

class BackupAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "BackupAlarmReceiver triggered: action=${intent?.action}")

        val isBoot = intent?.action == Intent.ACTION_BOOT_COMPLETED
        if (isBoot) {
            // Re-schedule daily alarm after reboot and check if yesterday was missed
            AutoBackupManager.scheduleNextDailyBackup(context)
            com.tirup.app.data.worker.AutoBackupWorker.enqueue(context, force = false)
        } else {
            // Triggered by daily 23:59:59 alarm: enqueue worker and reschedule for tomorrow
            Log.i(TAG, "Enqueuing exact daily auto-backup via WorkManager...")
            com.tirup.app.data.worker.AutoBackupWorker.enqueue(context, force = true)
            AutoBackupManager.scheduleNextDailyBackup(context)
        }
    }

    companion object {
        private const val TAG = "BackupAlarmReceiver"
    }
}
