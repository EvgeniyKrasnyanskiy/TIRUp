package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.domain.model.GlucoseReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DexdripBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i(TAG, "Broadcast received: action=$action")

        val extras = intent.extras
        if (extras == null) {
            Log.w(TAG, "Broadcast has no extras")
            return
        }

        // 1. Extract glucose value
        val glucoseVal = extractGlucoseValue(extras)
        if (glucoseVal == null || glucoseVal <= 0.0) {
            Log.w(TAG, "Unable to extract valid glucose value from extras: ${extrasSummary(extras)}")
            return
        }

        // Convert mg/dL to mmol/L if needed (values > 35 are in mg/dL)
        val valueMmol = if (glucoseVal > 35.0) {
            glucoseVal / 18.0182
        } else {
            glucoseVal
        }

        // 2. Extract timestamp
        val timestamp = extractTimestamp(extras) ?: System.currentTimeMillis()

        // 3. Extract slope / arrow
        val slopeName = extractSlopeName(extras)
        val trendArrow = slopeToArrow(slopeName)

        // 4. Extract IoB (Insulin on Board) & CoB (Carbs on Board)
        val iob = extractIob(extras)
        val cob = extractCob(extras)

        Log.i(TAG, "Saving glucose: $valueMmol mmol/L at $timestamp (trend: $trendArrow, iob: $iob, cob: $cob)")

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
                            trendArrow = trendArrow,
                            iob = iob,
                            cob = cob
                        )
                    )
                    Log.i(TAG, "Successfully persisted reading into database.")
                    val recentEntities = app.database.glucoseReadingDao().getRecentReadingsSync(25)
                    val recentDomain = recentEntities.map { it.toDomain() }
                    val userSettings = app.settingsRepository.getSettings().first()
                    com.tirup.app.data.alert.GlucoseAlertManager.checkAndAlert(
                        context = context.applicationContext,
                        recentReadings = recentDomain,
                        settings = userSettings
                    )
                    if (userSettings.alertSettings.isLastChanceAlertEnabled) {
                        val calendar = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val todayEntities = app.database.glucoseReadingDao().getReadingsBetweenSync(
                            calendar.timeInMillis,
                            System.currentTimeMillis() + 60_000L
                        )
                        val todayDomain = todayEntities.map { it.toDomain() }
                        if (todayDomain.isNotEmpty()) {
                            com.tirup.app.data.alert.GlucoseAlertManager.checkLastChanceAlert(
                                context = context.applicationContext,
                                todayReadings = todayDomain,
                                latestReading = todayDomain.last(),
                                settings = userSettings
                            )
                        }
                    }
                    com.tirup.app.data.backup.AutoBackupManager.maybeTriggerAutoBackup(
                        context = context.applicationContext,
                        database = app.database,
                        settingsRepository = app.settingsRepository
                    )
                } else {
                    Log.e(TAG, "TirupApplication or repository instance is null.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving broadcast glucose reading", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractGlucoseValue(extras: Bundle): Double? {
        val candidateKeys = listOf(
            "bg.valueMgdl",
            "bg.value",
            "com.eveningoutpost.dexdrip.Extras.BgEstimate",
            "com.eveningoutpost.dexdrip.Extras.Bg",
            "com.eveningoutpost.dexdrip.Extras.Value",
            "bgestimate",
            "bg_estimate",
            "bgEstimate",
            "glucose",
            "value",
            "bg",
            "sgv",
            "glucose_value",
            "calculated_value"
        )

        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num > 0.0) return num
            }
        }

        // Fallback: check all bundle keys for anything containing 'bg' or 'glucose' or 'estimate'
        for (key in extras.keySet()) {
            val kLower = key.lowercase()
            if (kLower.contains("estimate") || kLower.contains("glucose") || kLower.contains("sgv") || kLower == "bg") {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num > 0.0) return num
            }
        }

        return null
    }

    private fun extractTimestamp(extras: Bundle): Long? {
        val candidateKeys = listOf(
            "bg.timeStamp",
            "treatment.timeStamp",
            "com.eveningoutpost.dexdrip.Extras.RawTimestamp",
            "com.eveningoutpost.dexdrip.Extras.Time",
            "rawtimestamp",
            "raw_timestamp",
            "rawTimestamp",
            "timestamp",
            "time",
            "date"
        )

        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val ts = getLongFromBundle(extras, key)
                if (ts != null && ts > 0) {
                    return if (ts > 100_000_000_000L) ts else ts * 1000L
                }
            }
        }
        return null
    }

    private fun extractSlopeName(extras: Bundle): String? {
        val candidateKeys = listOf(
            "bg.deltaName",
            "com.eveningoutpost.dexdrip.Extras.BgSlopeName",
            "slope_name",
            "slopename",
            "slope",
            "trend",
            "direction",
            "trend_name"
        )

        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val str = extras.getString(key)
                if (!str.isNullOrBlank()) return str
            }
        }
        return null
    }

    private fun extractIob(extras: Bundle): Double? {
        val candidateKeys = listOf(
            "predict.IOB",
            "predict.iob",
            "treatment.insulin",
            "com.eveningoutpost.dexdrip.Extras.Iob",
            "iob",
            "IOB",
            "current_iob",
            "active_insulin",
            "de.michelinside.glucodatahandler.iob"
        )
        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num >= 0.0) return num
            }
        }
        return null
    }

    private fun extractCob(extras: Bundle): Double? {
        val candidateKeys = listOf(
            "predict.COB",
            "predict.cob",
            "predict.BWP",
            "com.eveningoutpost.dexdrip.Extras.Cob",
            "cob",
            "COB",
            "current_cob",
            "carbs_on_board",
            "de.michelinside.glucodatahandler.cob"
        )
        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val obj = extras.get(key)
                if (obj is String && obj.contains("Carbs:", ignoreCase = true)) {
                    val extracted = Regex("""\d+([.,]\d+)?""").find(obj)?.value?.replace(',', '.')?.toDoubleOrNull()
                    if (extracted != null && extracted >= 0.0) return extracted
                }
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num >= 0.0) return num
            }
        }
        return null
    }

    private fun getDoubleFromBundle(extras: Bundle, key: String): Double? {
        val obj = extras.get(key) ?: return null
        return when (obj) {
            is Number -> obj.toDouble()
            is String -> obj.replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    private fun getLongFromBundle(extras: Bundle, key: String): Long? {
        val obj = extras.get(key) ?: return null
        return when (obj) {
            is Number -> obj.toLong()
            is String -> obj.toLongOrNull()
            else -> null
        }
    }

    private fun extrasSummary(extras: Bundle): String {
        return extras.keySet().joinToString(", ") { key ->
            "$key=${extras.get(key)}"
        }
    }

    private fun slopeToArrow(slopeName: String?): String {
        return when (slopeName?.lowercase()?.trim()) {
            "doubleup", "double_up", "tripleup", "triple_up" -> "↑↑"
            "singleup", "single_up", "up", "rapidly increasing" -> "↑"
            "fortyfiveup", "forty_five_up", "up45", "increasing" -> "↗"
            "flat", "constant", "not changing" -> "→"
            "fortyfivedown", "forty_five_down", "down45", "decreasing" -> "↘"
            "singledown", "single_down", "down", "rapidly decreasing" -> "↓"
            "doubledown", "double_down", "tripledown", "triple_down" -> "↓↓"
            else -> "→"
        }
    }

    companion object {
        private const val TAG = "DexdripReceiver"
    }
}
