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

                // Attempt to recover fresh data from active source (LAN Follower or local xDrip service)
                if (settings.xdripLanSettings.isEnabled && settings.xdripLanSettings.isConfigured) {
                    com.tirup.app.data.network.XdripLanManager.pollNow()
                } else {
                    DexdripBroadcastReceiver.syncFromLocalXdrip(context, force = true)
                }

                val latest = app.glucoseRepository.getLatestReading().firstOrNull() ?: return@launch

                // 1. Re-render lockscreen notification & widgets with current status
                GlucoseAlertManager.refreshLockscreenNotificationAndWidgets(context, settings, latest)

                // 2. Dismiss outdated predictive alert if reading is stale
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.cancel(GlucoseAlertManager.NOTIFICATION_ID_PREDICTIVE)

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
