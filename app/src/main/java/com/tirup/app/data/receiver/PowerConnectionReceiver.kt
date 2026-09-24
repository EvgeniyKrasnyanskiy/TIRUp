package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.tirup.app.TirupApplication
import com.tirup.app.domain.model.AodSettings
import com.tirup.app.presentation.aod.AodActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_POWER_CONNECTED) {
            val app = context.applicationContext as? TirupApplication ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = app.settingsRepository.getSettings().first()
                    val aod = settings.aodSettings
                    if (aod.isEnabled && aod.autoChargeEnabled) {
                        if (isCurrentTimeInWindow(
                                startHour = aod.autoChargeStartHour,
                                startMin = aod.autoChargeStartMinute,
                                endHour = aod.autoChargeEndHour,
                                endMin = aod.autoChargeEndMinute
                            )
                        ) {
                            val aodIntent = Intent(context, AodActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(aodIntent)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun isCurrentTimeInWindow(
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int
    ): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMin
        val endMinutes = endHour * 60 + endMin

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Spans across midnight (e.g. 23:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }
}
