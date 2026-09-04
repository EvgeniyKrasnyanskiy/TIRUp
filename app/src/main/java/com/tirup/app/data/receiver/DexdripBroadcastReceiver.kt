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

        // Handle xDrip BroadcastService lifecycle handshake
        val function = extras.getString("FUNCTION")
        if (function.equals("start", ignoreCase = true)) {
            Log.i(TAG, "Received CMD_START from xDrip. Re-sending registration handshake.")
            registerWithXdripBroadcastService(context)
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

        // 4. Extract IoB (Insulin on Board) & CoB (Carbs on Board) with 30-min cache
        var iob = extractIob(extras)
        var cob = extractCob(extras)

        val pendingResult = goAsync()
        scope.launch {
            try {
                // If IoB or CoB not found in broadcast extras, query local xDrip/Nightscout web service (port 17580)
                if (iob == null || cob == null) {
                    val pebbleData = fetchIobCobFromLocalPebbleService()
                    if (pebbleData != null) {
                        if (pebbleData.first != null) {
                            val pIob = pebbleData.first!!
                            if (pIob <= 0.05) {
                                iob = null
                                cachedIob = null
                                cachedIobTimestamp = 0L
                            } else if (iob == null) {
                                iob = pIob
                                cachedIob = iob
                                cachedIobTimestamp = System.currentTimeMillis()
                                Log.i(TAG, "Fetched active IoB from local 17580 web service: $iob U")
                            }
                        }
                        if (pebbleData.second != null) {
                            val pCob = pebbleData.second!!
                            if (pCob <= 0.5) {
                                cob = null
                                cachedCob = null
                                cachedCobTimestamp = 0L
                            } else if (cob == null) {
                                cob = pCob
                                cachedCob = cob
                                cachedCobTimestamp = System.currentTimeMillis()
                                Log.i(TAG, "Fetched active CoB from local 17580 web service: $cob g")
                            }
                        }
                    }
                }

                val curIob = iob
                if (curIob != null && curIob <= 0.05) {
                    iob = null
                    cachedIob = null
                    cachedIobTimestamp = 0L
                }
                val curCob = cob
                if (curCob != null && curCob <= 0.5) {
                    cob = null
                    cachedCob = null
                    cachedCobTimestamp = 0L
                }

                Log.i(TAG, "Saving glucose: $valueMmol mmol/L at $timestamp (trend: $trendArrow, iob: $iob, cob: $cob)")

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

                    if (userSettings.alertSettings.isLastChanceAlertEnabled && todayDomain.isNotEmpty()) {
                        com.tirup.app.data.alert.GlucoseAlertManager.checkLastChanceAlert(
                            context = context.applicationContext,
                            todayReadings = todayDomain,
                            latestReading = todayDomain.last(),
                            settings = userSettings
                        )
                    }

                    // Update Lockscreen status notification if enabled
                    com.tirup.app.data.alert.GlucoseAlertManager.updateLockscreenNotification(
                        context = context.applicationContext,
                        latestReading = todayDomain.lastOrNull() ?: recentDomain.lastOrNull(),
                        todayReadings = todayDomain,
                        settings = userSettings
                    )

                    if (userSettings.isFloatingBubbleEnabled && android.provider.Settings.canDrawOverlays(context)) {
                        com.tirup.app.presentation.overlay.FloatingBubbleService.start(context.applicationContext)
                    }

                    com.tirup.app.data.backup.AutoBackupManager.maybeTriggerAutoBackup(
                        context = context.applicationContext,
                        database = app.database,
                        settingsRepository = app.settingsRepository
                    )
                    com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context.applicationContext)
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
            "glucoseMgdl",
            "glucodata.Minute.mgdl",
            "glucodata.Minute.glucose",
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
            if (kLower.contains("estimate") || kLower.contains("glucose") || kLower.contains("sgv") || kLower == "bg" || kLower.contains("mgdl")) {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num > 0.0) return num
            }
        }

        return null
    }

    private fun extractTimestamp(extras: Bundle): Long? {
        val candidateKeys = listOf(
            "bg.timeStamp",
            "glucoseTimeStamp",
            "glucodata.Minute.Time",
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
            "slopeArrow",
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
            "glucodata.Minute.IOB",
            "de.michelinside.glucodatahandler.iob"
        )
        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num >= 0.0) {
                    if (num <= 0.05) {
                        cachedIob = null
                        cachedIobTimestamp = 0L
                        return null
                    }
                    cachedIob = num
                    cachedIobTimestamp = System.currentTimeMillis()
                    return num
                }
            }
        }

        // Parse external.statusLine from xDrip+ / AndroidAPS (e.g., "2,07IE 19g", "1.50 U")
        val statusLine = extras.getString("external.statusLine")
        if (!statusLine.isNullOrBlank()) {
            val iobMatch = Regex("""(\d+[.,]\d+)\s*(?:IE|U|ЕД)""", RegexOption.IGNORE_CASE).find(statusLine)
            val parsedIob = iobMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            if (parsedIob != null && parsedIob >= 0.0) {
                if (parsedIob <= 0.05) {
                    cachedIob = null
                    cachedIobTimestamp = 0L
                    return null
                }
                cachedIob = parsedIob
                cachedIobTimestamp = System.currentTimeMillis()
                return parsedIob
            }
        }

        // Cache fallback: return last valid IoB if within 30 minutes
        if (cachedIob != null && (System.currentTimeMillis() - cachedIobTimestamp) <= IOB_COB_EXPIRY_MS) {
            return cachedIob
        }

        return null
    }

    private fun extractCob(extras: Bundle): Double? {
        val candidateKeys = listOf(
            "predict.COB",
            "predict.cob",
            "predict.BWP",
            "treatment.carbs",
            "com.eveningoutpost.dexdrip.Extras.Cob",
            "cob",
            "COB",
            "current_cob",
            "carbs_on_board",
            "glucodata.Minute.COB",
            "de.michelinside.glucodatahandler.cob"
        )
        for (key in candidateKeys) {
            if (extras.containsKey(key)) {
                val obj = extras.get(key)
                if (obj is String && obj.contains("Carbs:", ignoreCase = true)) {
                    val extracted = Regex("""\d+([.,]\d+)?""").find(obj)?.value?.replace(',', '.')?.toDoubleOrNull()
                    if (extracted != null && extracted >= 0.0) {
                        if (extracted <= 0.5) {
                            cachedCob = null
                            cachedCobTimestamp = 0L
                            return null
                        }
                        cachedCob = extracted
                        cachedCobTimestamp = System.currentTimeMillis()
                        return extracted
                    }
                }
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num >= 0.0) {
                    if (num <= 0.5) {
                        cachedCob = null
                        cachedCobTimestamp = 0L
                        return null
                    }
                    cachedCob = num
                    cachedCobTimestamp = System.currentTimeMillis()
                    return num
                }
            }
        }

        // Parse external.statusLine from xDrip+ / AndroidAPS (e.g., "2,07IE 19g", "Loop aktiv 15g")
        val statusLine = extras.getString("external.statusLine")
        if (!statusLine.isNullOrBlank()) {
            val cobMatch = Regex("""(?:IE|U|ЕД|\||\s|^)(\d+)\s*(?:g|г)\b""", RegexOption.IGNORE_CASE).find(statusLine)
            val parsedCob = cobMatch?.groupValues?.get(1)?.toDoubleOrNull()
            if (parsedCob != null && parsedCob >= 0.0) {
                if (parsedCob <= 0.5) {
                    cachedCob = null
                    cachedCobTimestamp = 0L
                    return null
                }
                cachedCob = parsedCob
                cachedCobTimestamp = System.currentTimeMillis()
                return parsedCob
            }
        }

        // Cache fallback: return last valid CoB if within 30 minutes
        if (cachedCob != null && (System.currentTimeMillis() - cachedCobTimestamp) <= IOB_COB_EXPIRY_MS) {
            return cachedCob
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
        val trimmed = slopeName?.trim() ?: return "→"
        if (trimmed in listOf("↑", "↗", "→", "↘", "↓", "↑↑", "↓↓")) {
            return trimmed
        }
        return when (trimmed.lowercase()) {
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

    private fun fetchIobCobFromLocalPebbleService(): Pair<Double?, Double?>? {
        var connection: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL("http://127.0.0.1:17580/pebble")
            connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parsePebbleJson(response)
            } else {
                null
            }
        } catch (_: Exception) {
            // Local server not running or port 17580 disabled - expected when xDrip web service is off
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parsePebbleJson(jsonStr: String): Pair<Double?, Double?>? {
        try {
            val root = org.json.JSONObject(jsonStr)
            var iob: Double? = null
            var cob: Double? = null

            // 1. Root level iob / cob
            if (root.has("iob") && !root.isNull("iob")) {
                val v = root.optDouble("iob")
                if (!v.isNaN() && v >= 0.0) iob = v
            }
            if (root.has("cob") && !root.isNull("cob")) {
                val v = root.optDouble("cob")
                if (!v.isNaN() && v >= 0.0) cob = v
            }

            // 2. Nested in bgs array (Pebble format)
            if ((iob == null || cob == null) && root.has("bgs")) {
                val bgs = root.optJSONArray("bgs")
                if (bgs != null && bgs.length() > 0) {
                    val first = bgs.optJSONObject(0)
                    if (first != null) {
                        if (iob == null && first.has("iob") && !first.isNull("iob")) {
                            val v = first.optDouble("iob")
                            if (!v.isNaN() && v >= 0.0) iob = v
                        }
                        if (cob == null && first.has("cob") && !first.isNull("cob")) {
                            val v = first.optDouble("cob")
                            if (!v.isNaN() && v >= 0.0) cob = v
                        }
                    }
                }
            }
            if (iob != null || cob != null) {
                return Pair(iob, cob)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse local pebble JSON: ${e.message}")
        }
        return null
    }

    companion object {
        private const val TAG = "DexdripReceiver"
        private const val IOB_COB_EXPIRY_MS = 30 * 60 * 1000L // 30 min cache like GDH

        @Volatile
        private var cachedIob: Double? = null
        @Volatile
        private var cachedIobTimestamp: Long = 0L

        @Volatile
        private var cachedCob: Double? = null
        @Volatile
        private var cachedCobTimestamp: Long = 0L

        /**
         * Send registration handshake to xDrip+ BroadcastService with required Settings Parcelable.
         * Calling this prompts xDrip+ to register TIRUp in its subscriber table and stream full BG, graph, IoB and CoB data.
         */
        fun registerWithXdripBroadcastService(context: Context) {
            try {
                val intent = Intent("com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_RECEIVER").apply {
                    putExtra("FUNCTION", "update_bg_force")
                    putExtra("PACKAGE", context.packageName)
                    putExtra("SETTINGS", com.eveningoutpost.dexdrip.services.broadcastservice.models.Settings(context.packageName, 4 * 60 * 60 * 1000L))
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                Log.i(TAG, "Dispatched registration handshake to xDrip BroadcastService with Settings (PACKAGE=${context.packageName})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send registration handshake to xDrip BroadcastService", e)
            }
        }
    }
}
