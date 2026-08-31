package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.domain.model.GlucoseReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DexdripBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == "com.eveningoutpost.dexdrip.BgEstimate") {
            val extras = intent.extras ?: return

            // Timestamp extraction
            val timestamp = when {
                extras.containsKey("com.eveningoutpost.dexdrip.Extras.RawTimestamp") ->
                    extras.getLong("com.eveningoutpost.dexdrip.Extras.RawTimestamp")
                extras.containsKey("timestamp") ->
                    extras.getLong("timestamp")
                extras.containsKey("time") ->
                    extras.getLong("time")
                else -> System.currentTimeMillis()
            }

            // Glucose value extraction
            var rawBg = when {
                extras.containsKey("com.eveningoutpost.dexdrip.Extras.BgEstimate") ->
                    extras.getDouble("com.eveningoutpost.dexdrip.Extras.BgEstimate")
                extras.containsKey("bg") ->
                    extras.getDouble("bg")
                extras.containsKey("value") ->
                    extras.getDouble("value")
                else -> 0.0
            }

            if (rawBg <= 0.0) {
                // Fallback for int values
                val rawInt = extras.getInt("com.eveningoutpost.dexdrip.Extras.BgEstimate", 0)
                if (rawInt > 0) {
                    rawBg = rawInt.toDouble()
                }
            }

            if (rawBg <= 0.0) return

            // Convert mg/dL to mmol/L if needed (values > 35 are mg/dL)
            val valueMmol = if (rawBg > 35.0) {
                rawBg / 18.0182
            } else {
                rawBg
            }

            val slopeName = extras.getString("com.eveningoutpost.dexdrip.Extras.BgSlopeName")
                ?: extras.getString("slope_name")

            val trendArrow = slopeToArrow(slopeName)

            Log.d(TAG, "Received BG broadcast: $valueMmol mmol/L at $timestamp (trend: $trendArrow)")

            val pendingResult = goAsync()
            scope.launch {
                try {
                    val app = context.applicationContext as? TirupApplication
                    val repository = app?.glucoseRepository
                    if (repository != null) {
                        repository.insertReading(
                            GlucoseReading(
                                timestamp = timestamp,
                                valueMmol = valueMmol,
                                trendArrow = trendArrow
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving broadcast glucose reading", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun slopeToArrow(slopeName: String?): String {
        return when (slopeName?.lowercase()) {
            "doubleup", "double_up" -> "↑↑"
            "singleup", "single_up", "up" -> "↑"
            "fortyfiveup", "forty_five_up", "up45" -> "↗"
            "flat" -> "→"
            "fortyfivedown", "forty_five_down", "down45" -> "↘"
            "singledown", "single_down", "down" -> "↓"
            "doubledown", "double_down" -> "↓↓"
            else -> "→"
        }
    }

    companion object {
        private const val TAG = "DexdripReceiver"
    }
}
