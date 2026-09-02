package com.tirup.app.data.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tirup.app.R
import com.tirup.app.domain.calculator.GlucoseTrendPredictor
import com.tirup.app.domain.calculator.PredictedEvent
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class AlertTier {
    PREDICTIVE, // Tier 1: Soft / Упреждающий за 15 мин
    MAIN,       // Tier 2: Confirmed 5 points / Основной
    CRITICAL    // Tier 3: Prolonged / Критический «кричащий»
}

object GlucoseAlertManager {

    private const val TAG = "GlucoseAlertManager"

    const val CHANNEL_PREDICTIVE = "tirup_alert_predictive_v2"
    const val CHANNEL_MAIN = "tirup_alert_main_v2"
    const val CHANNEL_CRITICAL = "tirup_alert_critical_v2"

    private const val NOTIFICATION_ID_PREDICTIVE = 1001
    private const val NOTIFICATION_ID_MAIN = 1002
    private const val NOTIFICATION_ID_CRITICAL = 1003

    // Timestamps for Smart Snooze / Anti-spam
    @Volatile
    private var lastHypoAlertTimestamp: Long = 0L

    @Volatile
    private var lastHyperAlertTimestamp: Long = 0L

    @Volatile
    private var lastPredictiveAlertTimestamp: Long = 0L

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Delete old v1 channels that had system sound attached
        listOf("tirup_alert_predictive", "tirup_alert_main", "tirup_alert_critical").forEach { id ->
            try { nm.deleteNotificationChannel(id) } catch (_: Exception) {}
        }

        // Tier 1: Predictive (Soft) - sound handled purely by MedicalSoundPlayer
        val predictiveChannel = NotificationChannel(
            CHANNEL_PREDICTIVE,
            "1. Предиктивные предупреждения (за 15 мин)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Мягкие упреждающие сигналы о скором выходе за целевой диапазон"
            setSound(null, null)
            enableVibration(false) // vibration handled explicitly by triggerVibration
        }

        // Tier 2: Main (Confirmed 5 points) - sound handled purely by MedicalSoundPlayer
        val mainChannel = NotificationChannel(
            CHANNEL_MAIN,
            "2. Основные оповещения (5 точек вне нормы)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уверенные сигналы при подтверждённом выходе сахара за целевой диапазон"
            setSound(null, null)
            enableVibration(false)
        }

        // Tier 3: Critical (Prolonged or extreme) - sound handled purely by MedicalSoundPlayer on USAGE_ALARM
        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL,
            "3. Критические и затяжные тревоги",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Громкие настойчивые тревоги при затяжной гипо/гипергликемии или экстремальных значениях"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }

        nm.createNotificationChannel(predictiveChannel)
        nm.createNotificationChannel(mainChannel)
        nm.createNotificationChannel(criticalChannel)
    }

    /**
     * Dispatches an immediate test alert with sound, vibration, and flashlight.
     */
    fun sendTestAlert(context: Context, tier: AlertTier, isRu: Boolean = true) {
        initChannels(context)
        val title: String
        val text: String
        val channelId: String
        val notifId: Int

        when (tier) {
            AlertTier.PREDICTIVE -> {
                channelId = CHANNEL_PREDICTIVE
                notifId = NOTIFICATION_ID_PREDICTIVE
                title = if (isRu) "🔮 ТЕСТ: 1. Мягкое предиктивное оповещение" else "🔮 TEST: 1. Soft Predictive Alert"
                text = if (isRu) "Прогноз падения через ~14 минут. Проверка звука, мягкого вибро и вспышки."
                       else "Forecast low in ~14 minutes. Checking soft sound, vibration and flash."
            }
            AlertTier.MAIN -> {
                channelId = CHANNEL_MAIN
                notifId = NOTIFICATION_ID_MAIN
                title = if (isRu) "🔻 ТЕСТ: 2. Основное оповещение (5 точек)" else "🔻 TEST: 2. Main Alert (5 points)"
                text = if (isRu) "Подтверждено 5 замеров вне нормы. Проверка двойной вибрации и звука."
                       else "Confirmed 5 out-of-range readings. Checking double vibration and alert tone."
            }
            AlertTier.CRITICAL -> {
                channelId = CHANNEL_CRITICAL
                notifId = NOTIFICATION_ID_CRITICAL
                title = if (isRu) "🚨 ТЕСТ: 3. Критическая тревога («кричащая»)" else "🚨 TEST: 3. Critical Alarm (Loud)"
                text = if (isRu) "Затяжная гипогликемия. Проверка громкой сирены, SOS-вибрации и стробоскопа!"
                       else "Prolonged hypo alarm. Checking loud siren, urgent vibration and strobe flash!"
            }
        }

        sendNotification(
            context = context,
            channelId = channelId,
            notificationId = notifId,
            title = title,
            text = text,
            tier = tier,
            vibrate = true,
            flash = true
        )
    }

    /**
     * Inspects recent readings against settings and triggers the appropriate alert tier.
     */
    fun checkAndAlert(
        context: Context,
        recentReadings: List<GlucoseReading>,
        settings: UserSettings
    ) {
        if (recentReadings.isEmpty()) return
        val alerts = settings.alertSettings
        if (!alerts.isAlertsMasterEnabled) return

        initChannels(context)

        val targetRanges = settings.targetRanges
        val sorted = recentReadings.sortedBy { it.timestamp }
        val latest = sorted.last()
        val now = System.currentTimeMillis()

        // Don't alert on stale readings (> 20 min)
        if (now - latest.timestamp > 20 * 60 * 1000L) return

        val tirLow = targetRanges.tirLowMmol
        val tirHigh = if (settings.targetMode.name == "TING") targetRanges.tingHighMmol else targetRanges.tirHighMmol
        val isRu = settings.language.equals("RU", ignoreCase = true)

        // ----------------------------------------------------
        // TIER 3: CRITICAL / PROLONGED
        // ----------------------------------------------------
        if (alerts.isCriticalEnabled) {
            // Extreme Low (< 3.0) or Prolonged Low (< 3.9 for >= criticalHypoMinutes)
            val isExtremeLow = latest.valueMmol < targetRanges.veryLowThresholdMmol
            val isProlongedLow = checkProlongedOutOfRange(sorted, isLow = true, threshold = tirLow, minutes = alerts.criticalHypoMinutes)

            if (isExtremeLow || isProlongedLow) {
                if (now - lastHypoAlertTimestamp >= alerts.snoozeHypoMinutes * 60000L) {
                    lastHypoAlertTimestamp = now
                    val title = if (isExtremeLow) {
                        if (isRu) "🚨 ЭКСТРЕМАЛЬНО НИЗКИЙ САХАР!" else "🚨 EXTREMELY LOW GLUCOSE!"
                    } else {
                        if (isRu) "🚨 ЗАТЯЖНАЯ ГИПОГЛИКЕМИЯ (${alerts.criticalHypoMinutes}+ мин)!" else "🚨 PROLONGED HYPO (${alerts.criticalHypoMinutes}+ min)!"
                    }
                    val text = String.format(
                        Locale.US,
                        if (isRu) "Текущий сахар: %.1f ммоль/л %s. Срочно примите быстрые углеводы!"
                        else "Current glucose: %.1f mmol/L %s. Take fast-acting carbs now!",
                        latest.valueMmol,
                        latest.trendArrow ?: ""
                    )
                    sendNotification(context, CHANNEL_CRITICAL, NOTIFICATION_ID_CRITICAL, title, text, AlertTier.CRITICAL, alerts.isCriticalVibrate, alerts.isCriticalFlash)
                    return
                }
            }

            // Extreme High (> 13.9) or Prolonged High (> tirHigh for >= criticalHyperMinutes)
            val isExtremeHigh = latest.valueMmol > targetRanges.veryHighThresholdMmol
            val isProlongedHigh = checkProlongedOutOfRange(sorted, isLow = false, threshold = tirHigh, minutes = alerts.criticalHyperMinutes)

            if (isExtremeHigh || isProlongedHigh) {
                if (now - lastHyperAlertTimestamp >= alerts.snoozeHyperMinutes * 60000L) {
                    lastHyperAlertTimestamp = now
                    val title = if (isExtremeHigh) {
                        if (isRu) "⚠️ ЭКСТРЕМАЛЬНО ВЫСОКИЙ САХАР!" else "⚠️ EXTREMELY HIGH GLUCOSE!"
                    } else {
                        if (isRu) "⚠️ ЗАТЯЖНАЯ ГИПЕРГЛИКЕМИЯ (${alerts.criticalHyperMinutes}+ мин)!" else "⚠️ PROLONGED HIGH (${alerts.criticalHyperMinutes}+ min)!"
                    }
                    val text = String.format(
                        Locale.US,
                        if (isRu) "Текущий сахар: %.1f ммоль/л %s. Проверьте помпу/подколку и кетоны."
                        else "Current glucose: %.1f mmol/L %s. Check insulin delivery and ketones.",
                        latest.valueMmol,
                        latest.trendArrow ?: ""
                    )
                    sendNotification(context, CHANNEL_CRITICAL, NOTIFICATION_ID_CRITICAL, title, text, AlertTier.CRITICAL, alerts.isCriticalVibrate, alerts.isCriticalFlash)
                    return
                }
            }
        }

        // ----------------------------------------------------
        // TIER 2: MAIN (5 CONSECUTIVE POINTS OUT OF RANGE)
        // ----------------------------------------------------
        if (alerts.isMainEnabled && sorted.size >= alerts.mainConsecutivePoints) {
            val lastPoints = sorted.takeLast(alerts.mainConsecutivePoints)
            val allLow = lastPoints.all { it.valueMmol < tirLow }
            val allHigh = lastPoints.all { it.valueMmol > tirHigh }

            if (allLow && now - lastHypoAlertTimestamp >= alerts.snoozeHypoMinutes * 60000L) {
                lastHypoAlertTimestamp = now
                val title = if (isRu) "🔻 Низкий сахар (подтверждено 5 точек)" else "🔻 Low Glucose (5 points confirmed)"
                val text = String.format(
                    Locale.US,
                    if (isRu) "Глюкоза: %.1f ммоль/л %s ниже порога %.1f."
                    else "Glucose: %.1f mmol/L %s below threshold %.1f.",
                    latest.valueMmol,
                    latest.trendArrow ?: "",
                    tirLow
                )
                sendNotification(context, CHANNEL_MAIN, NOTIFICATION_ID_MAIN, title, text, AlertTier.MAIN, alerts.isMainVibrate, alerts.isMainFlash)
                return
            } else if (allHigh && now - lastHyperAlertTimestamp >= alerts.snoozeHyperMinutes * 60000L) {
                lastHyperAlertTimestamp = now
                val title = if (isRu) "🔺 Высокий сахар (подтверждено 5 точек)" else "🔺 High Glucose (5 points confirmed)"
                val text = String.format(
                    Locale.US,
                    if (isRu) "Глюкоза: %.1f ммоль/л %s выше нормы %.1f."
                    else "Glucose: %.1f mmol/L %s above threshold %.1f.",
                    latest.valueMmol,
                    latest.trendArrow ?: "",
                    tirHigh
                )
                sendNotification(context, CHANNEL_MAIN, NOTIFICATION_ID_MAIN, title, text, AlertTier.MAIN, alerts.isMainVibrate, alerts.isMainFlash)
                return
            }
        }

        // ----------------------------------------------------
        // TIER 1: PREDICTIVE (15-MIN FORECAST)
        // ----------------------------------------------------
        if (alerts.isPredictiveEnabled && sorted.size >= 5) {
            val prediction = GlucoseTrendPredictor.predictTrend(
                readings = sorted,
                targetRanges = targetRanges,
                minutesAhead = alerts.predictiveMinutesAhead,
                useTingForHigh = settings.targetMode.name == "TING"
            )

            if (prediction.event == PredictedEvent.PREDICTED_LOW && now - lastPredictiveAlertTimestamp >= 20 * 60000L) {
                lastPredictiveAlertTimestamp = now
                val mins = prediction.minutesUntilCrossing ?: alerts.predictiveMinutesAhead
                val title = if (isRu) "🔮 Ожидается падение сахара (~$mins мин)" else "🔮 Predicted Low Glucose (~$mins min)"
                val text = String.format(
                    Locale.US,
                    if (isRu) "Текущий: %.1f ммоль/л %s. Прогноз: %.1f ммоль/л. Подготовьте углеводы."
                    else "Current: %.1f mmol/L %s. Forecast: %.1f mmol/L. Prepare carbs.",
                    latest.valueMmol,
                    latest.trendArrow ?: "",
                    prediction.predictedValueMmol
                )
                sendNotification(context, CHANNEL_PREDICTIVE, NOTIFICATION_ID_PREDICTIVE, title, text, AlertTier.PREDICTIVE, alerts.isPredictiveVibrate, alerts.isPredictiveFlash)
            } else if (prediction.event == PredictedEvent.PREDICTED_HIGH && now - lastPredictiveAlertTimestamp >= 30 * 60000L) {
                lastPredictiveAlertTimestamp = now
                val mins = prediction.minutesUntilCrossing ?: alerts.predictiveMinutesAhead
                val title = if (isRu) "🔮 Ожидается рост сахара (~$mins мин)" else "🔮 Predicted High Glucose (~$mins min)"
                val text = String.format(
                    Locale.US,
                    if (isRu) "Текущий: %.1f ммоль/л %s. Прогноз: %.1f ммоль/л."
                    else "Current: %.1f mmol/L %s. Forecast: %.1f mmol/L.",
                    latest.valueMmol,
                    latest.trendArrow ?: "",
                    prediction.predictedValueMmol
                )
                sendNotification(context, CHANNEL_PREDICTIVE, NOTIFICATION_ID_PREDICTIVE, title, text, AlertTier.PREDICTIVE, alerts.isPredictiveVibrate, alerts.isPredictiveFlash)
            }
        }
    }

    private fun checkProlongedOutOfRange(
        readings: List<GlucoseReading>,
        isLow: Boolean,
        threshold: Double,
        minutes: Int
    ): Boolean {
        if (readings.isEmpty()) return false
        val windowMs = minutes * 60 * 1000L
        val latestTime = readings.last().timestamp
        val cutoffTime = latestTime - windowMs

        val pointsInWindow = readings.filter { it.timestamp >= cutoffTime }
        if (pointsInWindow.isEmpty()) return false

        // Check that the duration of points in window spans at least (minutes - 5)
        val spanMs = pointsInWindow.last().timestamp - pointsInWindow.first().timestamp
        if (spanMs < (minutes - 5) * 60 * 1000L) return false

        return if (isLow) {
            pointsInWindow.all { it.valueMmol < threshold }
        } else {
            pointsInWindow.all { it.valueMmol > threshold }
        }
    }

    private fun sendNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        tier: AlertTier,
        vibrate: Boolean,
        flash: Boolean
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (tier) {
            AlertTier.CRITICAL -> NotificationCompat.PRIORITY_MAX
            AlertTier.MAIN -> NotificationCompat.PRIORITY_HIGH
            AlertTier.PREDICTIVE -> NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setSound(null)
            .setAutoCancel(true)

        if (tier == AlertTier.CRITICAL) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        if (vibrate) {
            triggerVibration(context, tier)
        }

        if (flash) {
            triggerFlashlight(context, tier)
        }

        MedicalSoundPlayer.playSound(tier)

        try {
            nm.notify(notificationId, builder.build())
            Log.i(TAG, "Sent notification tier=$tier: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
        }
    }

    private fun triggerVibration(context: Context, tier: AlertTier) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = when (tier) {
                    AlertTier.PREDICTIVE -> longArrayOf(0, 150, 100, 150)
                    // Synchronized with the 3 beeps and 1.5s pauses of Tier 2
                    AlertTier.MAIN -> longArrayOf(0, 320, 1500, 320, 1500, 320)
                    AlertTier.CRITICAL -> longArrayOf(0, 500, 200, 500, 200, 800)
                }
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun triggerFlashlight(context: Context, tier: AlertTier) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@launch

                if (tier == AlertTier.MAIN) {
                    // Synchronized with the 3 beeps and 1.5s pauses of Tier 2
                    for (i in 0 until 3) {
                        cameraManager.setTorchMode(cameraId, true)
                        delay(160)
                        cameraManager.setTorchMode(cameraId, false)
                        if (i < 2) delay(1500)
                    }
                } else {
                    val pulses = if (tier == AlertTier.CRITICAL) 5 else 1
                    for (i in 0 until pulses) {
                        cameraManager.setTorchMode(cameraId, true)
                        delay(120)
                        cameraManager.setTorchMode(cameraId, false)
                        delay(120)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Flashlight pulse failed: ${e.message}")
            }
        }
    }
}
