package com.tirup.app.data.ble

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver for keeping BLE Observer scanner alive across Android Doze mode
 * and resetting the AOSP 30-minute continuous scan demotion limit.
 */
class BleScanKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_BLE_KEEP_ALIVE) return

        Log.i(TAG, "Keep-alive alarm triggered, refreshing BLE scan to reset 30-min AOSP timeout")

        if (BleObserverManager.isServiceRunning) {
            // Re-arm for the next 20 minutes to maintain the chain
            scheduleKeepAlive(context)

            // Trigger single entry-point scan restart
            BleObserverManager.restartScan("alarm_keep_alive")
        } else {
            Log.d(TAG, "BleObserverService is not running, skipping reschedule")
        }
    }

    companion object {
        const val ACTION_BLE_KEEP_ALIVE = "com.tirup.app.ACTION_BLE_KEEP_ALIVE"
        private const val TAG = "BleKeepAliveReceiver"
        private const val REQUEST_CODE = 9924
        const val KEEP_ALIVE_INTERVAL_MS = 20 * 60 * 1000L // 20 minutes (safely below 30-min AOSP limit)

        fun scheduleKeepAlive(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, BleScanKeepAliveReceiver::class.java).apply {
                action = ACTION_BLE_KEEP_ALIVE
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            val triggerAtMillis = System.currentTimeMillis() + KEEP_ALIVE_INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        // Safe fallback without requiring special SCHEDULE_EXACT_ALARM permission
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.d(TAG, "Keep-alive alarm scheduled for +20 min")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule keep-alive alarm: ${e.message}")
            }
        }

        fun cancelKeepAlive(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, BleScanKeepAliveReceiver::class.java).apply {
                action = ACTION_BLE_KEEP_ALIVE
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Keep-alive alarm cancelled")
        }
    }
}
