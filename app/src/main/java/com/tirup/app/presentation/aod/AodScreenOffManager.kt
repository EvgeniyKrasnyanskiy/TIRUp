package com.tirup.app.presentation.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.TirupApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Handles launching AOD mode during night hours (22:00 - 06:00) when the device screen turns off,
 * guarded by proximity sensor detection to avoid turning on inside pockets or face-down.
 */
object AodScreenOffManager {
    private const val TAG = "AodScreenOffManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent?.action != Intent.ACTION_SCREEN_OFF) return
            handleScreenOff(context)
        }
    }

    fun init(context: Context) {
        if (isRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(
                context.applicationContext,
                screenOffReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isRegistered = true
            Log.d(TAG, "AodScreenOffManager initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenOffReceiver: ${e.message}")
        }
    }

    private fun handleScreenOff(context: Context) {
        val app = context.applicationContext as? TirupApplication ?: return
        scope.launch {
            try {
                val settings = app.settingsRepository.getSettings().first()
                val aod = settings.aodSettings
                if (!aod.launchOnScreenOff) {
                    return@launch
                }

                // Night hours check: 22:00 - 06:00
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val isNightHour = (hour >= 22 || hour < 6)
                if (!isNightHour) {
                    Log.d(TAG, "Screen off ignored: outside night hours ($hour:00)")
                    return@launch
                }

                // Check proximity sensor before waking screen (avoid pocket / face-down turns on)
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

                if (sensorManager != null && proximitySensor != null) {
                    val maxRange = proximitySensor.maximumRange
                    var isNear = false
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event != null && event.values.isNotEmpty()) {
                                val distance = event.values[0]
                                isNear = (distance < 1.0f || distance < maxRange)
                            }
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }

                    sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST)
                    delay(120L)
                    sensorManager.unregisterListener(listener)

                    if (isNear) {
                        Log.i(TAG, "Phone is near object (pocket / face down). Skipping AoD launch.")
                        return@launch
                    }
                }

                Log.i(TAG, "Night hour screen-off detected. Launching AoD activity over lock screen.")
                val aodIntent = Intent(context, AodActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(aodIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handleScreenOff: ${e.message}", e)
            }
        }
    }
}
