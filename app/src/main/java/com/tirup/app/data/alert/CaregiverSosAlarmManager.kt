package com.tirup.app.data.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tirup.app.R
import com.tirup.app.data.receiver.AlertActionReceiver
import com.tirup.app.domain.alert.SosAlertData
import com.tirup.app.presentation.alert.CaregiverSosActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CaregiverSosAlarmManager {

    private const val TAG = "CaregiverSosAlarmManager"
    const val NOTIFICATION_ID_CAREGIVER_SOS = 9999
    const val CHANNEL_ID_CAREGIVER_SOS = "channel_caregiver_sos_v2"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var strobeJob: Job? = null
    private var vibratorJob: Job? = null

    private val _isSosAlarmActive = MutableStateFlow(false)
    val isSosAlarmActive: StateFlow<Boolean> = _isSosAlarmActive.asStateFlow()

    private val _currentSosData = MutableStateFlow<SosAlertData?>(null)
    val currentSosData: StateFlow<SosAlertData?> = _currentSosData.asStateFlow()

    /**
     * Triggers the full emergency wakeup alarm on caregiver's device.
     */
    fun triggerCaregiverSos(context: Context, data: SosAlertData) {
        val appContext = context.applicationContext
        Log.i(TAG, "Triggering Caregiver SOS Wakeup Alarm for patient '${data.patientName}' (${data.glucoseDisplay})")

        _currentSosData.value = data
        _isSosAlarmActive.value = true

        // 1. Acquire WakeLock to turn on / keep CPU awake
        try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock?.release()
            @Suppress("DEPRECATION")
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TIRUp:CaregiverSosWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(30_000L) // 30 sec max hold
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wakeLock: ${e.message}")
        }

        // 2. Play 24-second siren on USAGE_ALARM at 100% volume
        MedicalSoundPlayer.playCaregiverSosAlarm(cycles = 16)

        // 3. Strobe camera flashlight for ~24 seconds
        startFlashlightStrobe(appContext)

        // 4. Start urgent vibration pattern
        startVibration(appContext)

        // 5. Post high-priority full-screen notification
        showFullScreenNotification(appContext, data)

        // 6. Direct launch Activity over lockscreen
        try {
            val activityIntent = Intent(appContext, CaregiverSosActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CaregiverSosActivity.EXTRA_PATIENT_NAME, data.patientName)
                putExtra(CaregiverSosActivity.EXTRA_GLUCOSE, data.glucoseDisplay)
                putExtra(CaregiverSosActivity.EXTRA_TREND, data.trendArrow)
                putExtra(CaregiverSosActivity.EXTRA_MINUTES, data.delayMinutes)
                putExtra(CaregiverSosActivity.EXTRA_SENDER_PHONE, data.senderPhone)
                putExtra(CaregiverSosActivity.EXTRA_MAPS_URL, data.mapsUrl)
                putExtra(CaregiverSosActivity.EXTRA_IS_TEST, data.isTest)
            }
            appContext.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch CaregiverSosActivity directly: ${e.message}")
        }
    }

    /**
     * Silences and dismisses the active Caregiver SOS alarm.
     */
    fun dismissSosAlarm(context: Context) {
        val appContext = context.applicationContext
        Log.i(TAG, "Dismissing Caregiver SOS Alarm")

        _isSosAlarmActive.value = false
        _currentSosData.value = null

        // Stop sound and restore previous alarm volume
        MedicalSoundPlayer.stopAll()

        // Stop strobe
        strobeJob?.cancel()
        strobeJob = null
        stopFlashlight(appContext)

        // Stop vibration
        vibratorJob?.cancel()
        vibratorJob = null
        try {
            getVibrator(appContext)?.cancel()
        } catch (_: Exception) {}

        // Cancel notification
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID_CAREGIVER_SOS)

        // Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun showFullScreenNotification(context: Context, data: SosAlertData) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        initChannel(nm)

        val fullScreenIntent = Intent(context, CaregiverSosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CaregiverSosActivity.EXTRA_PATIENT_NAME, data.patientName)
            putExtra(CaregiverSosActivity.EXTRA_GLUCOSE, data.glucoseDisplay)
            putExtra(CaregiverSosActivity.EXTRA_TREND, data.trendArrow)
            putExtra(CaregiverSosActivity.EXTRA_MINUTES, data.delayMinutes)
            putExtra(CaregiverSosActivity.EXTRA_SENDER_PHONE, data.senderPhone)
            putExtra(CaregiverSosActivity.EXTRA_MAPS_URL, data.mapsUrl)
            putExtra(CaregiverSosActivity.EXTRA_IS_TEST, data.isTest)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_DISMISS_CAREGIVER_SOS
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            102,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (data.isTest) "🚨 ТЕСТ SOS: ${data.patientName}" else "🚨 SOS! КРИТИЧЕСКАЯ ГИПО: ${data.patientName}"
        val text = "${data.glucoseDisplay} (${data.trendArrow}) • Сирена ${data.delayMinutes}м без ответа"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CAREGIVER_SOS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nСрочно свяжитесь с пациентом!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.RED)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .addAction(0, "Отключить тревогу", dismissPending)

        nm.notify(NOTIFICATION_ID_CAREGIVER_SOS, builder.build())
    }

    fun initChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CAREGIVER_SOS,
                "0. Экстренный SOS фоловера (Caregiver Wakeup)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Громкая сирена при получении SMS о критической гипогликемии у близкого"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null) // Sound is handled explicitly by MedicalSoundPlayer on USAGE_ALARM
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startFlashlightStrobe(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        strobeJob?.cancel()
        strobeJob = scope.launch {
            var activeCameraId: String? = null
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@launch
                activeCameraId = cameraId

                // 24 seconds = 48 pulses (160ms on, 340ms off = 500ms cycle)
                for (i in 0 until 48) {
                    if (!_isSosAlarmActive.value) break
                    cameraManager.setTorchMode(cameraId, true)
                    delay(160)
                    cameraManager.setTorchMode(cameraId, false)
                    delay(340)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Strobe failed: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    try {
                        activeCameraId?.let { id -> cameraManager.setTorchMode(id, false) }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopFlashlight(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(cameraId, false)
        } catch (_: Exception) {}
    }

    private fun startVibration(context: Context) {
        val vibrator = getVibrator(context) ?: return
        vibratorJob?.cancel()
        vibratorJob = scope.launch {
            try {
                val pattern = longArrayOf(0, 800, 300, 800, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vibration failed: ${e.message}")
            }
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
}
