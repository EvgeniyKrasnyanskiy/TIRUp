package com.tirup.app.data.alert

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tirup.app.R
import com.tirup.app.data.receiver.AlertActionReceiver
import com.tirup.app.domain.calculator.DetectedPattern
import com.tirup.app.domain.calculator.GlucoseTrendPredictor
import com.tirup.app.domain.calculator.PatternSeverity
import com.tirup.app.domain.calculator.PredictedEvent
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AlertTier {
    PREDICTIVE,  // Tier 1: Soft / Упреждающий за 15 мин
    MAIN,        // Tier 2: Confirmed 5 points / Основной (тройной сигнал)
    CRITICAL,    // Tier 3: Prolonged / Критический «кричащий» (сирена 12 сек)
    SIGNAL_LOSS  // Tier 4: Signal Loss / Потеря связи (>20 мин)
}

object GlucoseAlertManager {

    private const val TAG = "GlucoseAlertManager"

    const val CHANNEL_PREDICTIVE = "tirup_alert_predictive_v2"
    const val CHANNEL_MAIN = "tirup_alert_main_v2"
    const val CHANNEL_CRITICAL = "tirup_alert_critical_v2"
    const val CHANNEL_SIGNAL_LOSS = "tirup_alert_signal_loss_v2"
    const val CHANNEL_PATTERNS = "tirup_patterns_v1"

    private const val NOTIFICATION_ID_PREDICTIVE = 1001
    private const val NOTIFICATION_ID_MAIN = 1002
    private const val NOTIFICATION_ID_CRITICAL = 1003
    private const val NOTIFICATION_ID_SIGNAL_LOSS = 1004

    // Timestamps for Smart Snooze / Anti-spam
    @Volatile
    private var lastHypoAlertTimestamp: Long = 0L

    @Volatile
    private var lastHyperAlertTimestamp: Long = 0L

    @Volatile
    private var lastPredictiveAlertTimestamp: Long = 0L

    @Volatile
    private var lastSignalLossAlertTimestamp: Long = 0L

    @Volatile
    private var currentSignalLossIntervalMs: Long = 20 * 60 * 1000L

    // Adaptive Snooze tracking
    @Volatile
    private var userAcknowledgedHypoTimestamp: Long = 0L

    @Volatile
    private var userAcknowledgedHyperTimestamp: Long = 0L

    @Volatile
    var isCriticalAlarmActive: Boolean = false
        private set

    private var flashJob: Job? = null

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
            "1. Предиктивные тревоги (за 15 мин)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Мягкие упреждающие сигналы о скором выходе за целевой диапазон"
            setSound(null, null)
            enableVibration(false)
        }

        // Tier 2: Main (Confirmed 5 points) - sound handled purely by MedicalSoundPlayer
        val mainChannel = NotificationChannel(
            CHANNEL_MAIN,
            "2. Основные тревоги (5 точек)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уверенные сигналы при подтверждённом выходе сахара за целевой диапазон"
            setSound(null, null)
            enableVibration(false)
        }

        // Tier 3: Critical (Prolonged or extreme) - sound handled purely by MedicalSoundPlayer on USAGE_ALARM
        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL,
            "3. Критические тревоги («кричащие»)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Громкие настойчивые тревоги при затяжной гипо/гипергликемии или экстремальных значениях"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }

        // Tier 4: Signal Loss (No readings >20 min)
        val signalLossChannel = NotificationChannel(
            CHANNEL_SIGNAL_LOSS,
            "4. Потеря сигнала сенсора (>20 мин)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Оповещения при отсутствии свежих данных от трансмиттера/сенсора"
            setSound(null, null)
            enableVibration(false)
        }

        nm.createNotificationChannel(predictiveChannel)
        nm.createNotificationChannel(mainChannel)
        nm.createNotificationChannel(criticalChannel)
        nm.createNotificationChannel(signalLossChannel)
    }

    /**
     * Dismisses the screaming critical alarm immediately (stops siren, vibration, torch, cancels notification).
     * If [fromUser] is true, records user reaction for adaptive snooze.
     */
    fun dismissCriticalAlarm(context: Context, fromUser: Boolean) {
        if (!isCriticalAlarmActive && !fromUser) return
        Log.i(TAG, "dismissCriticalAlarm fromUser=$fromUser")

        isCriticalAlarmActive = false
        MedicalSoundPlayer.stopAll()

        try {
            flashJob?.cancel()
            flashJob = null
            turnOffFlashlight(context)
        } catch (_: Exception) {}

        try {
            val vibrator = getVibrator(context)
            vibrator?.cancel()
        } catch (_: Exception) {}

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_CRITICAL)
        } catch (_: Exception) {}

        if (fromUser) {
            val now = System.currentTimeMillis()
            userAcknowledgedHypoTimestamp = now
            userAcknowledgedHyperTimestamp = now
        }
    }

    /**
     * Checks if user is currently interacting with the phone (screen ON and device UNLOCKED).
     */
    /**
     * Instantly silences any actively playing sound, vibration, and torch (e.g. on hardware volume/power key press)
     * WITHOUT modifying user acknowledged timestamps or snooze intervals.
     */
    fun silenceCurrentSoundOnly() {
        Log.i(TAG, "silenceCurrentSoundOnly")
        MedicalSoundPlayer.stopAll()
        try {
            flashJob?.cancel()
            flashJob = null
        } catch (_: Exception) {}
    }

    /**
     * Checks if user is currently interacting with the phone (screen ON and device UNLOCKED).
     */
    fun isPhoneInActiveUse(context: Context): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isScreenOn = pm?.isInteractive == true
            val isUnlocked = km?.isDeviceLocked == false
            isScreenOn && isUnlocked
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Test trigger directly from SettingsScreen buttons.
     */
    fun sendTestAlert(context: Context, tier: AlertTier, isRu: Boolean) {
        initChannels(context)

        val channelId = when (tier) {
            AlertTier.PREDICTIVE -> CHANNEL_PREDICTIVE
            AlertTier.MAIN -> CHANNEL_MAIN
            AlertTier.CRITICAL -> CHANNEL_CRITICAL
            AlertTier.SIGNAL_LOSS -> CHANNEL_SIGNAL_LOSS
        }
        val notificationId = when (tier) {
            AlertTier.PREDICTIVE -> NOTIFICATION_ID_PREDICTIVE
            AlertTier.MAIN -> NOTIFICATION_ID_MAIN
            AlertTier.CRITICAL -> NOTIFICATION_ID_CRITICAL
            AlertTier.SIGNAL_LOSS -> NOTIFICATION_ID_SIGNAL_LOSS
        }

        val title = when (tier) {
            AlertTier.PREDICTIVE -> if (isRu) "🔔 Тест: Предиктивная тревога" else "🔔 Test: Predictive Alert"
            AlertTier.MAIN -> if (isRu) "⚠️ Тест: Основная тревога (3 бипа)" else "⚠️ Test: Main Alert (3 beeps)"
            AlertTier.CRITICAL -> if (isRu) "🚨 Тест: Критическая сирена" else "🚨 Test: Critical Alarm"
            AlertTier.SIGNAL_LOSS -> if (isRu) "📡 Тест: Потеря сигнала" else "📡 Test: Signal Loss"
        }
        val text = when (tier) {
            AlertTier.PREDICTIVE -> if (isRu) "Мягкий сигнал прогноза падения/роста." else "Soft warning before crossing range."
            AlertTier.MAIN -> if (isRu) "Тройной сигнал с интервалом 1.5 сек при 5 точках вне нормы." else "Triple beep (1.5s pause) on confirmed out-of-range."
            AlertTier.CRITICAL -> if (isRu) "Серия громкой сирены ~12 сек. Нажмите «Принято» для глушения." else "Loud siren series ~12s. Tap 'Dismiss' to silence."
            AlertTier.SIGNAL_LOSS -> if (isRu) "Двухтональный сигнал при отсутствии данных более 20 минут." else "Two-tone alert on missing data for >20 minutes."
        }

        sendNotification(
            context = context,
            channelId = channelId,
            notificationId = notificationId,
            title = title,
            text = text,
            tier = tier,
            vibrate = true,
            flash = tier == AlertTier.CRITICAL
        )
    }

    /**
     * Posts a notification to the Android shade when a significant clinical pattern is discovered.
     * Guaranteed rate-limited to at most 1 notification per day per pattern ID.
     */
    fun notifyPatternIfNew(context: Context, pattern: DetectedPattern, isRu: Boolean) {
        if (pattern.severity != PatternSeverity.ALERT && pattern.severity != PatternSeverity.WARNING) return

        val prefs = context.getSharedPreferences("tirup_patterns_notif", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val key = "last_notif_${pattern.id}"
        val lastDate = prefs.getString(key, null)
        if (lastDate == todayStr) {
            return // Already notified today
        }

        prefs.edit().putString(key, todayStr).apply()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PATTERNS,
                "Клинические паттерны",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления об обнаруженных трендах и скрытых закономерностях гликемии"
            }
            nm.createNotificationChannel(channel)
        }

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            pattern.id.hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isRu) "🔍 Паттерн: ${pattern.titleRu}" else "🔍 Pattern: ${pattern.titleEn}"
        val text = if (isRu) pattern.descriptionRu else pattern.descriptionEn

        val notification = NotificationCompat.Builder(context, CHANNEL_PATTERNS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(5000 + kotlin.math.abs(pattern.id.hashCode() % 100), notification)
    }

    /**
     * Inspects recent readings against settings and triggers the appropriate alert tier.
     * Uses Smart Adaptive Snooze for Hypo and Hyper, and Exponential Backoff for Signal Loss.
     */
    fun checkAndAlert(
        context: Context,
        recentReadings: List<GlucoseReading>,
        settings: UserSettings
    ) {
        if (recentReadings.isEmpty()) return
        val alerts = settings.alertSettings
        if (!alerts.isAlertsMasterEnabled) return

        // Auto-dismiss if phone is actively used
        if (isCriticalAlarmActive && isPhoneInActiveUse(context)) {
            dismissCriticalAlarm(context, fromUser = true)
        }

        initChannels(context)

        val targetRanges = settings.targetRanges
        val sorted = recentReadings.sortedBy { it.timestamp }
        val latest = sorted.last()
        val now = System.currentTimeMillis()
        val isRu = settings.language.equals("RU", ignoreCase = true)

        // ----------------------------------------------------
        // TIER 4: SIGNAL LOSS CHECK (Exponential Backoff: 20m -> 40m -> 80m -> 160m)
        // ----------------------------------------------------
        val elapsedSinceLatest = now - latest.timestamp
        if (elapsedSinceLatest > alerts.signalLossMinutes * 60 * 1000L) {
            if (alerts.isSignalLossEnabled) {
                if (now - lastSignalLossAlertTimestamp >= currentSignalLossIntervalMs) {
                    lastSignalLossAlertTimestamp = now
                    currentSignalLossIntervalMs = (currentSignalLossIntervalMs * 2).coerceAtMost(160 * 60 * 1000L)
                    val elapsedMin = (elapsedSinceLatest / 60000L).toInt()
                    val title = if (isRu) "📡 Потеря связи с сенсором ($elapsedMin мин)" else "📡 Sensor Signal Lost ($elapsedMin min)"
                    val text = if (isRu) "Нет данных от xDrip+ более $elapsedMin мин. Проверьте Bluetooth и трансмиттер."
                    else "No CGM readings for $elapsedMin min. Check Bluetooth and transmitter."
                    sendNotification(context, CHANNEL_SIGNAL_LOSS, NOTIFICATION_ID_SIGNAL_LOSS, title, text, AlertTier.SIGNAL_LOSS, alerts.isSignalLossVibrate, flash = false)
                }
            }
            return
        } else {
            // Signal is active! Reset backoff interval and dismiss signal loss notification
            currentSignalLossIntervalMs = alerts.signalLossMinutes * 60 * 1000L
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_SIGNAL_LOSS)
        }

        val tirLow = targetRanges.tirLowMmol
        val tirHigh = if (settings.targetMode.name == "TING") targetRanges.tingHighMmol else targetRanges.tirHighMmol

        // Auto-dismiss stale out-of-range notifications when back in normal range
        val isInNormalRange = latest.valueMmol in tirLow..tirHigh
        if (isInNormalRange) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_PREDICTIVE)
            nm?.cancel(NOTIFICATION_ID_MAIN)
        }

        // ----------------------------------------------------
        // TIER 3: CRITICAL / PROLONGED
        // ----------------------------------------------------
        // Hypo protection is always on unless temporarily paused (<2h) or permanently disabled with explicit consent
        val isHypoProtectionActive = alerts.isCriticalEnabled ||
                (!alerts.isCriticalHypoPermanentDisabled && now >= alerts.criticalHypoPauseUntilTimestamp)

        if (isHypoProtectionActive) {
            // Extreme Low (< 3.0) or Prolonged Low (< 3.9 for >= criticalHypoMinutes)
            val isExtremeLow = latest.valueMmol < targetRanges.veryLowThresholdMmol
            val isProlongedLow = checkProlongedOutOfRange(sorted, isLow = true, threshold = tirLow, minutes = alerts.criticalHypoMinutes)

            if (isExtremeLow || isProlongedLow) {
                val timeSinceAck = now - userAcknowledgedHypoTimestamp
                val isUnderSnooze = userAcknowledgedHypoTimestamp > 0 && timeSinceAck < alerts.snoozeHypoMinutes * 60000L

                // Safety override: if under snooze, but sugar dropped critically (< 2.8 or drop rate <= -0.3 mmol/L)
                val isDroppingDangerously = (latest.valueMmol < 2.8) ||
                        (sorted.size >= 2 && (latest.valueMmol - sorted[sorted.size - 2].valueMmol) <= -0.3)

                val shouldTriggerHypo = if (isUnderSnooze) {
                    isDroppingDangerously // break snooze early if plummeting!
                } else if (userAcknowledgedHypoTimestamp > 0) {
                    true // 15-min snooze expired!
                } else {
                    // No reaction from user yet: repeat every 5 minutes!
                    now - lastHypoAlertTimestamp >= 5 * 60000L
                }

                if (shouldTriggerHypo) {
                    lastHypoAlertTimestamp = now
                    userAcknowledgedHypoTimestamp = 0L // reset so repeats in 5m if no reaction

                    val title = if (isExtremeLow) {
                        if (isRu) "🚨 ЭКСТРЕМАЛЬНО НИЗКИЙ САХАР!" else "🚨 EXTREMELY LOW GLUCOSE!"
                    } else {
                        if (isRu) "🚨 ЗАТЯЖНАЯ ГИПОГЛИКЕМИЯ (${alerts.criticalHypoMinutes}+ мин)!" else "🚨 PROLONGED HYPO (${alerts.criticalHypoMinutes}+ min)!"
                    }
                    val iobText = if (latest.iob != null && latest.iob > 0.0) {
                        String.format(Locale.US, if (isRu) " (IoB: %.1f Ед)" else " (IoB: %.1f U)", latest.iob)
                    } else ""
                    val text = String.format(
                        Locale.US,
                        if (isRu) "Текущий сахар: %.1f ммоль/л%s. Срочно примите быстрые углеводы!"
                        else "Current glucose: %.1f mmol/L%s. Take fast-acting carbs now!",
                        latest.valueMmol,
                        iobText
                    )
                    sendNotification(context, CHANNEL_CRITICAL, NOTIFICATION_ID_CRITICAL, title, text, AlertTier.CRITICAL, alerts.isCriticalVibrate, alerts.isCriticalFlash)
                    return
                }
            }
        }

        if (alerts.isCriticalEnabled) {
            // Extreme High (> 13.9) or Prolonged High (> tirHigh for >= criticalHyperMinutes)
            val isExtremeHigh = latest.valueMmol > targetRanges.veryHighThresholdMmol
            val isProlongedHigh = checkProlongedOutOfRange(sorted, isLow = false, threshold = tirHigh, minutes = alerts.criticalHyperMinutes)

            if (isExtremeHigh || isProlongedHigh) {
                val timeSinceAck = now - userAcknowledgedHyperTimestamp
                val isUnderInitialSnooze = userAcknowledgedHyperTimestamp > 0 && timeSinceAck < 30 * 60000L
                val isBetween30And45 = userAcknowledgedHyperTimestamp > 0 && timeSinceAck in (30 * 60000L)..(45 * 60000L)

                // Check trend & active bolus: is glucose falling or is there active bolus IoB >= 0.5?
                val hasActiveBolus = latest.iob != null && latest.iob >= 0.5
                val isFalling = sorted.size >= 3 && (latest.valueMmol - sorted[sorted.size - 3].valueMmol) <= -0.5

                val shouldTriggerHyper = when {
                    isUnderInitialSnooze -> false // give insulin 30 min minimum
                    (isBetween30And45 || (hasActiveBolus && timeSinceAck < 60 * 60000L)) && (isFalling || hasActiveBolus) -> false // active bolus working, extend snooze
                    userAcknowledgedHyperTimestamp > 0 && timeSinceAck >= (if (hasActiveBolus) 60 * 60000L else 45 * 60000L) -> true // insulin expired or not falling!
                    else -> now - lastHyperAlertTimestamp >= 15 * 60000L // no reaction yet: repeat every 15 min
                }

                if (shouldTriggerHyper) {
                    lastHyperAlertTimestamp = now
                    userAcknowledgedHyperTimestamp = 0L

                    val title = if (isExtremeHigh) {
                        if (isRu) "⚠️ ЭКСТРЕМАЛЬНО ВЫСОКИЙ САХАР!" else "⚠️ EXTREMELY HIGH GLUCOSE!"
                    } else {
                        if (isRu) "⚠️ ЗАТЯЖНАЯ ГИПЕРГЛИКЕМИЯ (${alerts.criticalHyperMinutes}+ мин)!" else "⚠️ PROLONGED HIGH (${alerts.criticalHyperMinutes}+ min)!"
                    }
                    val iobText = if (latest.iob != null && latest.iob > 0.0) {
                        String.format(Locale.US, if (isRu) " (IoB: %.1f Ед)" else " (IoB: %.1f U)", latest.iob)
                    } else ""
                    val text = String.format(
                        Locale.US,
                        if (isRu) "Текущий сахар: %.1f ммоль/л%s. Проверьте помпу/подколку и кетоны."
                        else "Current glucose: %.1f mmol/L%s. Check insulin delivery and ketones.",
                        latest.valueMmol,
                        iobText
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
                    if (isRu) "Глюкоза: %.1f ммоль/л ниже порога %.1f."
                    else "Glucose: %.1f mmol/L below threshold %.1f.",
                    latest.valueMmol,
                    tirLow
                )
                sendNotification(context, CHANNEL_MAIN, NOTIFICATION_ID_MAIN, title, text, AlertTier.MAIN, alerts.isMainVibrate, alerts.isMainFlash)
                return
            } else if (allHigh && now - lastHyperAlertTimestamp >= alerts.snoozeHyperMinutes * 60000L) {
                lastHyperAlertTimestamp = now
                val title = if (isRu) "🔺 Высокий сахар (подтверждено 5 точек)" else "🔺 High Glucose (5 points confirmed)"
                val text = String.format(
                    Locale.US,
                    if (isRu) "Глюкоза: %.1f ммоль/л выше нормы %.1f."
                    else "Glucose: %.1f mmol/L above threshold %.1f.",
                    latest.valueMmol,
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

            val isPredictedLow = prediction.event == PredictedEvent.PREDICTED_LOW
            val hasHighIobRisk = (latest.iob != null && latest.iob >= 1.0 && latest.valueMmol <= 6.5 &&
                    prediction.rateOfChangeMmolPerMin < -0.01)

            if ((isPredictedLow || hasHighIobRisk) && now - lastPredictiveAlertTimestamp >= 20 * 60000L) {
                lastPredictiveAlertTimestamp = now
                val eventTime = now + (prediction.minutesUntilCrossing ?: 15) * 60000L
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(eventTime))
                val title = if (isRu) "📉 Скоро гипогликемия (в $timeStr)" else "📉 Predicted Low (at $timeStr)"

                // Derive trend arrow strictly from rate of change to prevent conflicting arrows
                val arrow = when {
                    prediction.rateOfChangeMmolPerMin <= -0.15 -> "↓↓"
                    prediction.rateOfChangeMmolPerMin <= -0.06 -> "↓"
                    else -> "↘"
                }

                val iobNotice = if (latest.iob != null && latest.iob > 0.0) {
                    String.format(Locale.US, if (isRu) " (IoB: %.1f Ед)" else " (IoB: %.1f U)", latest.iob)
                } else ""

                val text = String.format(
                    Locale.US,
                    if (isRu) "Сахар %.1f ммоль/л %s%s падает со скоростью %.2f ммоль/л/мин."
                    else "Glucose %.1f mmol/L %s%s dropping at %.2f mmol/L/min.",
                    latest.valueMmol,
                    arrow,
                    iobNotice,
                    kotlin.math.abs(prediction.rateOfChangeMmolPerMin)
                )
                sendNotification(context, CHANNEL_PREDICTIVE, NOTIFICATION_ID_PREDICTIVE, title, text, AlertTier.PREDICTIVE, alerts.isPredictiveVibrate, alerts.isPredictiveFlash)
            } else if (prediction.event == PredictedEvent.PREDICTED_HIGH && now - lastPredictiveAlertTimestamp >= 30 * 60000L) {
                lastPredictiveAlertTimestamp = now
                val eventTime = now + (prediction.minutesUntilCrossing ?: 15) * 60000L
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(eventTime))
                val title = if (isRu) "📈 Скоро гипергликемия (в $timeStr)" else "📈 Predicted High (at $timeStr)"

                val arrow = when {
                    prediction.rateOfChangeMmolPerMin >= 0.15 -> "↑↑"
                    prediction.rateOfChangeMmolPerMin >= 0.06 -> "↑"
                    else -> "↗"
                }

                val text = String.format(
                    Locale.US,
                    if (isRu) "Сахар %.1f ммоль/л %s растёт со скоростью %.2f ммоль/л/мин."
                    else "Glucose %.1f mmol/L %s rising at %.2f mmol/L/min.",
                    latest.valueMmol,
                    arrow,
                    prediction.rateOfChangeMmolPerMin
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
        val now = readings.last().timestamp
        val windowReadings = readings.filter { now - it.timestamp <= windowMs }

        if (windowReadings.size < 3) return false
        val spanMs = windowReadings.last().timestamp - windowReadings.first().timestamp
        if (spanMs < (minutes - 3) * 60 * 1000L) return false

        return if (isLow) {
            windowReadings.all { it.valueMmol < threshold }
        } else {
            windowReadings.all { it.valueMmol > threshold }
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

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (tier) {
            AlertTier.CRITICAL -> NotificationCompat.PRIORITY_MAX
            AlertTier.MAIN -> NotificationCompat.PRIORITY_HIGH
            AlertTier.SIGNAL_LOSS -> NotificationCompat.PRIORITY_HIGH
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

        if (tier != AlertTier.CRITICAL) {
            // Automatically clear unread predictive/main/signal loss alerts after 60 minutes
            builder.setTimeoutAfter(60 * 60 * 1000L)
        }

        if (tier == AlertTier.CRITICAL) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            isCriticalAlarmActive = true

            // Add direct "✓ Принято" action button on notification
            val dismissIntent = Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_DISMISS_CRITICAL
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dismissTitle = if (context.resources.configuration.locales[0].language.equals("ru", true)) "✓ Принято (Снять тревогу)" else "✓ Dismiss Alarm"
            builder.addAction(R.mipmap.ic_launcher, dismissTitle, dismissPendingIntent)
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

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun triggerVibration(context: Context, tier: AlertTier) {
        try {
            val vibrator = getVibrator(context) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = when (tier) {
                    AlertTier.PREDICTIVE -> longArrayOf(0, 150, 100, 150)
                    AlertTier.SIGNAL_LOSS -> longArrayOf(0, 250, 150, 250)
                    // Tier 2: 3 pulses synchronized with 1.5s pauses
                    AlertTier.MAIN -> longArrayOf(0, 320, 1500, 320, 1500, 320)
                    // Tier 3: High urgency repeat pattern for ~12 seconds
                    AlertTier.CRITICAL -> longArrayOf(
                        0, 400, 200, 400, 200, 600, 350,
                        400, 200, 400, 200, 600, 350,
                        400, 200, 400, 200, 600, 350,
                        400, 200, 400, 200, 600
                    )
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

        flashJob?.cancel()
        flashJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@launch

                when (tier) {
                    AlertTier.PREDICTIVE -> {
                        cameraManager.setTorchMode(cameraId, true)
                        delay(140)
                        cameraManager.setTorchMode(cameraId, false)
                    }
                    AlertTier.MAIN -> {
                        // 3 pulses synchronized with 1.5s pauses
                        for (i in 0 until 3) {
                            cameraManager.setTorchMode(cameraId, true)
                            delay(160)
                            cameraManager.setTorchMode(cameraId, false)
                            if (i < 2) delay(1500)
                        }
                    }
                    AlertTier.CRITICAL -> {
                        // Strobe series for up to 12 seconds, stops if cancelled
                        for (i in 0 until 24) {
                            if (!isCriticalAlarmActive) break
                            cameraManager.setTorchMode(cameraId, true)
                            delay(160)
                            cameraManager.setTorchMode(cameraId, false)
                            delay(340)
                        }
                    }
                    AlertTier.SIGNAL_LOSS -> {
                        // Signal loss does not pulse flashlight
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Flashlight pulse failed: ${e.message}")
            }
        }
    }

    private fun turnOffFlashlight(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(cameraId, false)
        } catch (e: Exception) {}
    }
}
