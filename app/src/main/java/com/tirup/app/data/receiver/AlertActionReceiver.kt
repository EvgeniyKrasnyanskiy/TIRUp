package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlertActionReceiver : BroadcastReceiver() {

    private val scope get() = receiverScope

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
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_LOW_BATTERY)
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
            ACTION_SKIP_HBA1C_QUARTER -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_HBA1C_REMINDER)
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val settingsRepo = com.tirup.app.data.repository.SettingsRepositoryImpl(context)
                        settingsRepo.skipHba1cQuarter()
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            val isRu = java.util.Locale.getDefault().language.lowercase() in listOf("ru", "be", "kk", "uk")
                            android.widget.Toast.makeText(
                                context,
                                if (isRu) "Квартальный контроль HbA1c отложен на 90 дней" else "Quarterly HbA1c checkup postponed for 90 days",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed skipHba1cQuarter: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DISMISS_CAREGIVER_SOS -> {
                com.tirup.app.data.alert.CaregiverSosAlarmManager.dismissSosAlarm(context)
            }
            ACTION_TRIGGER_EMERGENCY_SMS -> {
                val pendingResult = goAsync()
                scope.launch {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:EmergencySmsWakeLock")
                    wakeLock?.acquire(15000L)
                    try {
                        if (!GlucoseAlertManager.isCriticalAlarmActive) {
                            Log.i(TAG, "Critical alarm is no longer active when AlarmManager triggered. Emergency SMS aborted.")
                            return@launch
                        }

                        val app = context.applicationContext as? com.tirup.app.TirupApplication
                        val settingsRepo = app?.settingsRepository ?: com.tirup.app.data.repository.SettingsRepositoryImpl(context.applicationContext)
                        val glucoseRepo = app?.glucoseRepository ?: app?.database?.let { com.tirup.app.data.repository.GlucoseRepositoryImpl(it) }

                        val userSettings = settingsRepo.getSettings().first()
                        val alerts = userSettings.alertSettings
                        if (!alerts.isEmergencySmsEnabled || (alerts.emergencyContactPhone.isBlank() && alerts.secondaryEmergencyContactPhone.isBlank())) {
                            Log.i(TAG, "Emergency SMS disabled or no contact phone configured.")
                            return@launch
                        }

                        // Re-check freshest reading to avoid stale SOS if glucose already recovered
                        val latestReading = glucoseRepo?.getLatestReading()?.first()
                        val scheduledGlucose = intent.getDoubleExtra(EXTRA_SCHEDULED_GLUCOSE, 0.0)
                        val scheduledTrend = intent.getStringExtra(EXTRA_SCHEDULED_TREND) ?: "→"
                        val delayMinutes = intent.getIntExtra(EXTRA_DELAY_MINUTES, alerts.emergencySmsDelayMinutes.coerceAtLeast(1))

                        val actualGlucose = latestReading?.valueMmol ?: scheduledGlucose
                        val actualTrend = latestReading?.trendArrow ?: scheduledTrend

                        if (latestReading != null && latestReading.valueMmol >= alerts.criticalLowThresholdMmol) {
                            Log.i(TAG, "User glucose recovered to ${latestReading.valueMmol} mmol/L (>= threshold ${alerts.criticalLowThresholdMmol}). Emergency SMS aborted safely.")
                            return@launch
                        }

                        val isRu = userSettings.language.equals("RU", ignoreCase = true)
                        Log.w(TAG, "Critical hypo alarm timed out after $delayMinutes min without reaction! Dispatching emergency SMS (BG=$actualGlucose)...")
                        com.tirup.app.data.alert.EmergencySmsManager.sendEmergencyAlert(
                            context = context.applicationContext,
                            glucoseValue = actualGlucose,
                            trendArrow = actualTrend,
                            delayMinutes = delayMinutes,
                            settings = alerts,
                            patientProfile = userSettings.patientProfile,
                            isRu = isRu,
                            unit = userSettings.unit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing emergency SMS trigger: ${e.message}", e)
                    } finally {
                        try {
                            if (wakeLock?.isHeld == true) wakeLock.release()
                        } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            }
            ACTION_SMS_SENT -> {
                val phone = intent.getStringExtra("extra_phone") ?: "unknown"
                Log.i(TAG, "SMS dispatch event callback received for $phone")
            }
            ACTION_SMS_DELIVERED -> {
                val phone = intent.getStringExtra("extra_phone") ?: "unknown"
                Log.i(TAG, "SMS delivery receipt callback received for $phone")
            }
            ACTION_POLL_XDRIP_LAN -> {
                val pendingResult = goAsync()
                scope.launch {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:LanPollWakeLock")
                    wakeLock?.acquire(8000L)
                    try {
                        com.tirup.app.data.network.XdripLanManager.pollNow()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed ACTION_POLL_XDRIP_LAN: ${e.message}", e)
                    } finally {
                        try {
                            if (wakeLock?.isHeld == true) wakeLock.release()
                        } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlertActionReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val ACTION_DISMISS_CRITICAL = "com.tirup.app.ACTION_DISMISS_CRITICAL"
        const val ACTION_LAUNCH_DIANIGHT = "com.tirup.app.ACTION_LAUNCH_DIANIGHT"
        const val ACTION_CHECK_SIGNAL_LOSS = "com.tirup.app.ACTION_CHECK_SIGNAL_LOSS"
        const val ACTION_SKIP_HBA1C_QUARTER = "com.tirup.app.ACTION_SKIP_HBA1C_QUARTER"
        const val ACTION_DISMISS_CAREGIVER_SOS = "com.tirup.app.ACTION_DISMISS_CAREGIVER_SOS"
        const val ACTION_POLL_XDRIP_LAN = "com.tirup.app.ACTION_POLL_XDRIP_LAN"
        const val ACTION_TRIGGER_EMERGENCY_SMS = "com.tirup.app.ACTION_TRIGGER_EMERGENCY_SMS"
        const val ACTION_SMS_SENT = "com.tirup.app.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.tirup.app.ACTION_SMS_DELIVERED"
        const val EXTRA_SCHEDULED_GLUCOSE = "extra_scheduled_glucose"
        const val EXTRA_SCHEDULED_TREND = "extra_scheduled_trend"
        const val EXTRA_DELAY_MINUTES = "extra_delay_minutes"
        const val REQUEST_CODE_POLL_LAN = 1005
        const val REQUEST_CODE_EMERGENCY_SMS = 1006
    }
}
