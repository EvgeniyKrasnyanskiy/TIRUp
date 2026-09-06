package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class StalenessAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val pendingResult = goAsync()

        scope.launch {
            try {
                val app = context.applicationContext as? TirupApplication ?: return@launch
                val settings = app.settingsRepository.getSettings().firstOrNull() ?: return@launch
                val latest = app.glucoseRepository.getLatestReading().firstOrNull() ?: return@launch

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayEntities = app.database.glucoseReadingDao().getReadingsBetweenSync(
                    calendar.timeInMillis,
                    System.currentTimeMillis() + 60_000L
                )
                val todayDomain = todayEntities.map { it.toDomain() }

                // 1. Re-render lockscreen notification with current stale status
                GlucoseAlertManager.updateLockscreenNotification(
                    context = context,
                    latestReading = latest,
                    todayReadings = todayDomain,
                    settings = settings
                )

                // 2. Re-render all homescreen widgets with current stale status
                TirupWidgetUpdater.updateAllWidgets(context)

                // 3. Schedule next periodic tick while stale (to update elapsed minutes string)
                GlucoseAlertManager.scheduleNextStalenessCheck(context, latest.timestamp)
                Log.d(TAG, "Successfully refreshed stale notification and widgets for ts=${latest.timestamp}")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing stale views: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "StalenessAlarmReceiver"
        const val ACTION_REFRESH_STALENESS = "com.tirup.app.ACTION_REFRESH_STALENESS"
    }
}
