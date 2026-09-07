package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.TreatmentEntity
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.Treatment
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

        // 0. Extract potential treatment event (bolus insulin or carbs)
        val treatment = extractTreatment(extras, System.currentTimeMillis())

        // 1. Extract glucose value and its source key
        val extracted = extractGlucoseValue(extras)
        if (extracted == null || extracted.first <= 0.0) {
            if (treatment != null) {
                // Standalone treatment broadcast (e.g. from xDrip+ / AndroidAPS)
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val app = context.applicationContext as? TirupApplication
                        val db = app?.database
                        if (db != null) {
                            saveTreatmentIfNew(db, treatment)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save treatment: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
            Log.w(TAG, "Unable to extract valid glucose or treatment from extras: ${extrasSummary(extras)}")
            return
        }

        val (glucoseVal, matchedKey) = extracted

        // Convert mg/dL to mmol/L safely based on key, action, metadata, and numerical range.
        // SAFETY (IEC 62304): Avoid blind value thresholds (>35) alone because 10-35 mg/dL is severe hypo
        // and must never be treated as mmol/L (severe hyper).
        val isMgdl = when {
            // Explicit mmol indicators from keys or broadcast extras
            matchedKey.contains("mmol", ignoreCase = true) ||
                extras.containsKey("glucoseLevelMmol") ||
                extras.containsKey("com.eveningoutpost.dexdrip.Extras.BgEstimateMmol") ||
                extras.getString("units")?.contains("mmol", ignoreCase = true) == true ||
                extras.getString("glucodata.Minute.Unit")?.contains("mmol", ignoreCase = true) == true ||
                extras.getString("unit")?.contains("mmol", ignoreCase = true) == true -> false

            // Explicit mg/dL key indicators (xDrip, GlucoDataHandler, Nightscout)
            matchedKey.contains("mgdl", ignoreCase = true) ||
                matchedKey.equals("sgv", ignoreCase = true) ||
                matchedKey.contains("bgestimate", ignoreCase = true) ||
                matchedKey.contains("Extras.BgEstimate", ignoreCase = true) -> true

            // Known mg/dL broadcast actions (xDrip BgEstimate and Nightscout SGV always send mg/dL)
            action.contains("BgEstimate", ignoreCase = true) ||
                action.contains("NEW_SGV", ignoreCase = true) ||
                action.contains("dexcom", ignoreCase = true) -> true

            // Explicit mg/dL string flags
            extras.getString("units")?.contains("mg", ignoreCase = true) == true ||
                extras.getString("glucodata.Minute.Unit")?.contains("mg", ignoreCase = true) == true -> true

            // Values > 35 are unambiguously mg/dL (35 mmol/L = 630 mg/dL, impossibly high)
            glucoseVal > 35.0 -> true

            else -> false
        }

        val valueMmol = if (isMgdl) glucoseVal / 18.01559 else glucoseVal

        // 2. Extract timestamp
        val timestamp = extractTimestamp(extras) ?: System.currentTimeMillis()

        // Deduplication: drop duplicate echo broadcasts from multiple apps (xDrip + GDH) within 4 seconds
        val now = System.currentTimeMillis()
        if (Math.abs(now - lastProcessedWallClock) < 4000L &&
            Math.abs(valueMmol - lastProcessedValue) < 0.01 &&
            Math.abs(timestamp - lastProcessedTimestamp) < 3000L) {
            Log.d(TAG, "Skipping duplicate broadcast (ts=$timestamp, val=$valueMmol) received within 4s")
            return
        }
        lastProcessedWallClock = now
        lastProcessedTimestamp = timestamp
        lastProcessedValue = valueMmol

        // 3. Extract slope / arrow
        val slopeName = extractSlopeName(extras)
        val trendArrow = slopeToArrow(slopeName)

        // 4. Extract IoB (Insulin on Board) & CoB (Carbs on Board) with 30-min cache
        var iob = extractIob(extras)
        var cob = extractCob(extras)

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

        val pendingResult = goAsync()
        scope.launch {
            try {
                Log.i(TAG, "Saving glucose immediately: $valueMmol mmol/L at $timestamp (trend: $trendArrow, iob: $iob, cob: $cob)")

                val app = context.applicationContext as? TirupApplication
                val repository = app?.glucoseRepository
                if (repository != null) {
                    if (treatment != null) {
                        saveTreatmentIfNew(app.database, treatment)
                    }
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

                    // BLE Bridge Broadcaster: pulse advertising if role is BROADCASTER
                    if (userSettings.bleBridgeSettings.role == com.tirup.app.domain.model.BleBridgeRole.BROADCASTER) {
                        val latest = todayDomain.lastOrNull() ?: recentDomain.firstOrNull()
                        if (latest != null) {
                            val prev = recentDomain.getOrNull(1)
                            val rate = if (prev != null && latest.timestamp > prev.timestamp) {
                                val dtMin = (latest.timestamp - prev.timestamp) / 60000.0
                                if (dtMin in 1.0..15.0) (latest.valueMmol - prev.valueMmol) / dtMin else 0.0
                            } else 0.0

                            // Adaptive burst duration (Variant B):
                            // 5s for 1-minute cadence (dt <= 2.5 min), 10s for standard 5-minute cadence
                            val burstDuration = if (prev != null && (latest.timestamp - prev.timestamp) <= 150_000L) {
                                com.tirup.app.data.ble.BleBroadcaster.BURST_1MIN_MS
                            } else {
                                com.tirup.app.data.ble.BleBroadcaster.BURST_5MIN_MS
                            }

                            com.tirup.app.data.ble.BleBroadcaster.broadcastReading(
                                context = context.applicationContext,
                                reading = latest,
                                rateOfChange = rate,
                                iob = latest.iob ?: 0.0,
                                settings = userSettings.bleBridgeSettings,
                                burstDurationMs = burstDuration
                            )
                        }
                    }

                    com.tirup.app.data.backup.AutoBackupManager.maybeTriggerAutoBackup(
                        context = context.applicationContext,
                        database = app.database,
                        settingsRepository = app.settingsRepository
                    )
                    com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context.applicationContext)

                    // Asynchronously fetch IoB / CoB from local port 17580 if missing, without delaying the primary glucose display
                    if (iob == null || cob == null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val pebbleData = fetchIobCobFromLocalPebbleService()
                                if (pebbleData != null && (pebbleData.first != null || pebbleData.second != null)) {
                                    var newIob = iob
                                    var newCob = cob
                                    if (pebbleData.first != null && pebbleData.first!! > 0.05) {
                                        newIob = pebbleData.first
                                        cachedIob = newIob
                                        cachedIobTimestamp = System.currentTimeMillis()
                                    }
                                    if (pebbleData.second != null && pebbleData.second!! > 0.5) {
                                        newCob = pebbleData.second
                                        cachedCob = newCob
                                        cachedCobTimestamp = System.currentTimeMillis()
                                    }
                                    if (newIob != iob || newCob != cob) {
                                        repository.insertReading(
                                            GlucoseReading(
                                                timestamp = timestamp,
                                                valueMmol = valueMmol,
                                                trendArrow = trendArrow,
                                                iob = newIob,
                                                cob = newCob
                                            )
                                        )
                                        com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context.applicationContext)
                                        Log.i(TAG, "Asynchronously updated reading with Pebble IoB/CoB: iob=$newIob, cob=$newCob")
                                    }
                                }
                            } catch (pe: Exception) {
                                Log.d(TAG, "Background Pebble IoB/CoB fetch skipped: ${pe.message}")
                            }
                        }
                    }
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

    private fun extractGlucoseValue(extras: Bundle): Pair<Double, String>? {
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
                if (num != null && num > 0.0) return Pair(num, key)
            }
        }

        // Fallback: check all bundle keys for anything containing 'bg' or 'glucose' or 'estimate'
        for (key in extras.keySet()) {
            val kLower = key.lowercase()
            if (kLower.contains("estimate") || kLower.contains("glucose") || kLower.contains("sgv") || kLower == "bg" || kLower.contains("mgdl")) {
                val num = getDoubleFromBundle(extras, key)
                if (num != null && num > 0.0) return Pair(num, key)
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
        if (trimmed == "↑↑" || trimmed == "⇈") return "⇈"
        if (trimmed == "↓↓" || trimmed == "⇊") return "⇊"
        if (trimmed in listOf("↑", "↗", "→", "↘", "↓")) {
            return trimmed
        }
        return when (trimmed.lowercase()) {
            "doubleup", "double_up", "tripleup", "triple_up" -> "⇈"
            "singleup", "single_up", "up", "rapidly increasing" -> "↑"
            "fortyfiveup", "forty_five_up", "up45", "increasing" -> "↗"
            "flat", "constant", "not changing" -> "→"
            "fortyfivedown", "forty_five_down", "down45", "decreasing" -> "↘"
            "singledown", "single_down", "down", "rapidly decreasing" -> "↓"
            "doubledown", "double_down", "tripledown", "triple_down" -> "⇊"
            else -> "→"
        }
    }

    private fun fetchIobCobFromLocalPebbleService(): Pair<Double?, Double?>? {
        // 1. Try /pebble endpoint
        val pebbleResult = queryLocalEndpoint("pebble")?.let { parsePebbleJson(it) }
        if (pebbleResult?.first != null && pebbleResult.second != null) {
            return pebbleResult
        }

        // 2. Fallback to /status.json endpoint
        val statusResult = queryLocalEndpoint("status.json")?.let { parseStatusJson(it) }
        val mergedIob = pebbleResult?.first ?: statusResult?.first
        val mergedCob = pebbleResult?.second ?: statusResult?.second

        if (mergedIob != null || mergedCob != null) {
            return Pair(mergedIob, mergedCob)
        }
        return null
    }

    private fun queryLocalEndpoint(endpoint: String): String? {
        var connection: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL("http://127.0.0.1:17580/$endpoint")
            connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseStatusJson(jsonStr: String): Pair<Double?, Double?>? {
        try {
            val root = org.json.JSONObject(jsonStr)
            var iob: Double? = null
            var cob: Double? = null

            val statusArr = root.optJSONArray("status")
            val statusObj = if (statusArr != null && statusArr.length() > 0) statusArr.optJSONObject(0) else root

            if (statusObj != null) {
                val iobObj = statusObj.optJSONObject("iob")
                if (iobObj != null && iobObj.has("iob")) {
                    val v = iobObj.optDouble("iob")
                    if (!v.isNaN() && v >= 0.0) iob = v
                } else if (statusObj.has("iob")) {
                    val v = statusObj.optDouble("iob")
                    if (!v.isNaN() && v >= 0.0) iob = v
                }

                val cobObj = statusObj.optJSONObject("cob")
                if (cobObj != null && cobObj.has("cob")) {
                    val v = cobObj.optDouble("cob")
                    if (!v.isNaN() && v >= 0.0) cob = v
                } else if (statusObj.has("cob")) {
                    val v = statusObj.optDouble("cob")
                    if (!v.isNaN() && v >= 0.0) cob = v
                }
            }
            if (iob != null || cob != null) {
                return Pair(iob, cob)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse status.json: ${e.message}")
        }
        return null
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

    private fun extractTreatment(extras: Bundle, defaultTimestamp: Long): Treatment? {
        val insulin = getDoubleFromBundle(extras, "treatment.insulin")
            ?: getDoubleFromBundle(extras, "insulin")
            ?: getDoubleFromBundle(extras, "bolus")
            ?: getDoubleFromBundle(extras, "insulin_units")

        val carbs = getDoubleFromBundle(extras, "treatment.carbs")
            ?: getDoubleFromBundle(extras, "carbs")
            ?: getDoubleFromBundle(extras, "carbs_grams")

        val notes = extras.getString("treatment.notes")
            ?: extras.getString("notes")
            ?: extras.getString("notes_text")

        val ts = getLongFromBundle(extras, "treatment.timeStamp")
            ?: getLongFromBundle(extras, "treatment.timestamp")
            ?: getLongFromBundle(extras, "created_at")
            ?: getLongFromBundle(extras, "timestamp")
            ?: defaultTimestamp

        val hasInsulin = insulin != null && insulin > 0.0
        val hasCarbs = carbs != null && carbs > 0.0

        if (!hasInsulin && !hasCarbs) return null

        return Treatment(
            timestamp = ts,
            insulinUnits = if (hasInsulin) insulin else null,
            carbsGrams = if (hasCarbs) carbs else null,
            notes = notes,
            source = "XDRIP"
        )
    }

    private suspend fun saveTreatmentIfNew(database: AppDatabase, treatment: Treatment) {
        val dao = database.treatmentDao()
        val minTime = treatment.timestamp - 60_000L
        val maxTime = treatment.timestamp + 60_000L
        val count = dao.countSimilar(minTime, maxTime, treatment.insulinUnits, treatment.carbsGrams)
        if (count == 0) {
            dao.insert(TreatmentEntity.fromDomain(treatment))
            Log.i(TAG, "Persisted new treatment: insulin=${treatment.insulinUnits} U, carbs=${treatment.carbsGrams} g at ${treatment.timestamp}")
        } else {
            Log.d(TAG, "Skipped duplicate treatment within 60s window: insulin=${treatment.insulinUnits}, carbs=${treatment.carbsGrams}")
        }
    }

    companion object {
        private const val TAG = "DexdripReceiver"
        private const val IOB_COB_EXPIRY_MS = 30 * 60 * 1000L // 30 min cache like GDH

        @Volatile
        private var lastProcessedWallClock: Long = 0L
        @Volatile
        private var lastProcessedTimestamp: Long = 0L
        @Volatile
        private var lastProcessedValue: Double = 0.0

        @Volatile
        private var cachedIob: Double? = null
        @Volatile
        private var cachedIobTimestamp: Long = 0L

        @Volatile
        private var cachedCob: Double? = null
        @Volatile
        private var cachedCobTimestamp: Long = 0L

        fun sendXdripBroadcastServiceHandshake(context: Context) {
            try {
                val actions = listOf(
                    "com.eveningoutpost.dexdrip.services.broadcastservice.BROADCAST_SERVICE_INIT",
                    "com.eveningoutpost.dexdrip.services.broadcastservice.PING",
                    "com.eveningoutpost.dexdrip.services.broadcastservice.CONNECT"
                )
                for (act in actions) {
                    val intent = Intent(act).apply {
                        setPackage("com.eveningoutpost.dexdrip")
                        putExtra("package", context.packageName)
                        putExtra("app_name", "TIRUp")
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send xDrip handshake: ${e.message}")
            }
        }
    }
}
