package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlertActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        Log.i(TAG, "AlertActionReceiver onReceive action=${intent.action}")

        when (intent.action) {
            ACTION_DISMISS_CRITICAL -> {
                val notifId = intent.getIntExtra("notification_id", -1)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                if (notifId != -1) {
                    nm?.cancel(notifId)
                }
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_CRITICAL)
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_MAIN)
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_PREDICTIVE)
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_SIGNAL_LOSS)
                GlucoseAlertManager.silenceCurrentSoundOnly()
                GlucoseAlertManager.dismissCriticalAlarm(context, fromUser = true)
                GlucoseAlertManager.clearActiveAlertBanner()
            }
            ACTION_CHECK_SIGNAL_LOSS -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        GlucoseAlertManager.checkSignalLossDirectly(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed checkSignalLossDirectly: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_LAUNCH_DIANIGHT -> {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage("com.diaclock.nightstand")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Приложение DiaNight не установлено. Его можно скачать в Telegram-канале @diakia",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    try {
                        val tgIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/diakia")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(tgIntent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlertActionReceiver"
        const val ACTION_DISMISS_CRITICAL = "com.tirup.app.ACTION_DISMISS_CRITICAL"
        const val ACTION_LAUNCH_DIANIGHT = "com.tirup.app.ACTION_LAUNCH_DIANIGHT"
        const val ACTION_CHECK_SIGNAL_LOSS = "com.tirup.app.ACTION_CHECK_SIGNAL_LOSS"
    }
}
