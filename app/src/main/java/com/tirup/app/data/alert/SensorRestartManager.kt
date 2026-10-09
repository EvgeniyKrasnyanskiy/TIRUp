package com.tirup.app.data.alert

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tirup.app.R
import com.tirup.app.TirupApplication
import com.tirup.app.data.receiver.AlertActionReceiver
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SensorRestartProposal(
    val timestamp: Long,
    val durationDays: Int
)

object SensorRestartManager {

    private const val TAG = "SensorRestartManager"
    const val NOTIFICATION_ID_SENSOR_RESTART = 1018
    private const val PREFS_NAME = "tirup_sensor_restart_prefs"
    private const val KEY_LAST_HANDLED_TIMESTAMP = "last_handled_restart_ts"

    private val _proposalFlow = MutableStateFlow<SensorRestartProposal?>(null)
    val proposalFlow: StateFlow<SensorRestartProposal?> = _proposalFlow.asStateFlow()

    @Volatile
    private var cachedLastHandledTimestamp: Long? = null

    fun getLastHandledTimestamp(context: Context): Long {
        cachedLastHandledTimestamp?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_LAST_HANDLED_TIMESTAMP, 0L)
        cachedLastHandledTimestamp = ts
        return ts
    }

    private fun setLastHandledTimestamp(context: Context, timestamp: Long) {
        cachedLastHandledTimestamp = timestamp
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_HANDLED_TIMESTAMP, timestamp).apply()
    }

    /**
     * Triggered when DexdripBroadcastReceiver receives a treatment note containing "Started by xDrip".
     */
    fun handleXdripRestartDetected(
        context: Context,
        eventTimestamp: Long,
        currentSettings: UserSettings
    ) {
        val lastHandled = getLastHandledTimestamp(context)
        if (eventTimestamp <= lastHandled) {
            Log.d(TAG, "xDrip sensor restart at $eventTimestamp already handled or dismissed (lastHandled=$lastHandled)")
            return
        }

        // Avoid repeated popup if identical proposal is already active
        if (_proposalFlow.value?.timestamp == eventTimestamp) {
            return
        }

        val durationDays = if (currentSettings.sensorStatus.durationDays > 0) {
            currentSettings.sensorStatus.durationDays
        } else {
            14
        }

        val proposal = SensorRestartProposal(
            timestamp = eventTimestamp,
            durationDays = durationDays
        )
        _proposalFlow.value = proposal
        Log.i(TAG, "New xDrip sensor restart proposal created for ts=$eventTimestamp, duration=${durationDays}d")

        // Post Android notification so user can confirm/decline even with app closed
        postNotification(context, proposal, currentSettings.language.equals("RU", ignoreCase = true))
    }

    private fun postNotification(
        context: Context,
        proposal: SensorRestartProposal,
        isRu: Boolean
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val title = if (isRu) "Перезапуск сенсора в xDrip+" else "xDrip+ Sensor Restart Detected"
            val text = if (isRu) {
                "Обнаружен перезапуск сенсора в xDrip+. Обновить таймер сенсора на ${proposal.durationDays}д?"
            } else {
                "Sensor restart detected in xDrip+. Update sensor timer to ${proposal.durationDays}d?"
            }

            // Tap notification -> open app
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_SENSOR_RESTART,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action: Yes, update
            val confirmIntent = Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_CONFIRM_SENSOR_RESTART
                putExtra("timestamp", proposal.timestamp)
                putExtra("duration_days", proposal.durationDays)
            }
            val confirmPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_SENSOR_RESTART + 1,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Action: No
            val dismissIntent = Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_DISMISS_SENSOR_RESTART
                putExtra("timestamp", proposal.timestamp)
                putExtra("duration_days", proposal.durationDays)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_SENSOR_RESTART + 2,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, GlucoseAlertManager.CHANNEL_DEVICE_REMINDER)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentPendingIntent)
                .addAction(0, if (isRu) "Да, обновить" else "Yes, update", confirmPendingIntent)
                .addAction(0, if (isRu) "Нет" else "No", dismissPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(NOTIFICATION_ID_SENSOR_RESTART, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post sensor restart notification: ${e.message}", e)
        }
    }

    suspend fun confirmRestart(context: Context, proposal: SensorRestartProposal) {
        try {
            cancelNotification(context)
            _proposalFlow.value = null
            setLastHandledTimestamp(context, proposal.timestamp)

            val app = context.applicationContext as? TirupApplication ?: return
            val settingsRepo = app.settingsRepository
            val currentSettings = settingsRepo.getSettings().first()

            val updated = currentSettings.copy(
                sensorStatus = SensorStatus(
                    installedAt = proposal.timestamp,
                    durationDays = proposal.durationDays,
                    lastUsedDurationDays = proposal.durationDays
                )
            )
            settingsRepo.updateSettings(updated)
            TirupWidgetUpdater.updateAllWidgets(context)

            val isRu = currentSettings.language.equals("RU", ignoreCase = true)
            withContext(Dispatchers.Main) {
                val msg = if (isRu) {
                    "Таймер сенсора обновлён на ${proposal.durationDays}д"
                } else {
                    "Sensor timer updated to ${proposal.durationDays}d"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            Log.i(TAG, "Successfully confirmed xDrip sensor restart for ts=${proposal.timestamp}, duration=${proposal.durationDays}d")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm sensor restart: ${e.message}", e)
        }
    }

    fun dismissRestart(context: Context, proposal: SensorRestartProposal? = null) {
        cancelNotification(context)
        _proposalFlow.value = null
        if (proposal != null && proposal.timestamp > 0L) {
            setLastHandledTimestamp(context, proposal.timestamp)
        }
        Log.i(TAG, "Dismissed xDrip sensor restart proposal ts=${proposal?.timestamp}")
    }

    private fun cancelNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_SENSOR_RESTART)
        } catch (_: Exception) {}
    }
}
