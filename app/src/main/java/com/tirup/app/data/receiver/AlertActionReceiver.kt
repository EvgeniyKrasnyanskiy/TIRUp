package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        Log.i(TAG, "AlertActionReceiver onReceive action=${intent.action}")

        when (intent.action) {
            ACTION_DISMISS_CRITICAL -> {
                GlucoseAlertManager.dismissCriticalAlarm(context, fromUser = true)
            }
        }
    }

    companion object {
        private const val TAG = "AlertActionReceiver"
        const val ACTION_DISMISS_CRITICAL = "com.tirup.app.ACTION_DISMISS_CRITICAL"
    }
}
