package com.tirup.app.data.network

import android.content.Context
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.domain.model.DataSourcePriority
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.NightscoutSettings
import com.tirup.app.domain.model.NightscoutStatus
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object NightscoutStatusManager {

    private const val TAG = "NightscoutStatusManager"
    private const val TIMEOUT_MS = 6000
    private const val POLL_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var cachedSettingsRepo: SettingsRepository? = null
    private var cachedGlucoseRepo: GlucoseRepository? = null
    private var syncJob: Job? = null
    private var pollJob: Job? = null
    private var activeNsSettings: NightscoutSettings? = null
    private var lastPollTime = 0L

    private val _statusFlow = MutableStateFlow(NightscoutStatus())
    val statusFlow: StateFlow<NightscoutStatus> = _statusFlow.asStateFlow()

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseIsoTimestamp(iso: String): Long {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(iso)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun extractBattery(obj: JSONObject): Int? {
        val raw = when {
            obj.has("uploader") -> obj.optJSONObject("uploader")?.opt("battery")
            obj.has("uploaderBattery") -> obj.opt("uploaderBattery")
            obj.has("battery") -> obj.opt("battery")
            else -> null
        }
        return when (raw) {
            is Number -> raw.toInt().coerceIn(0, 100)
            is String -> raw.trim().toDoubleOrNull()?.toInt()?.coerceIn(0, 100)
            else -> null
        }
    }

    private fun extractTimestamp(obj: JSONObject): Long {
        return when {
            obj.has("created_at") -> {
                val raw = obj.optString("created_at")
                if (raw.isNotBlank()) parseIsoTimestamp(raw) else 0L
            }
            obj.has("date") -> obj.optLong("date")
            obj.has("mills") -> obj.optLong("mills")
            obj.has("timestamp") -> {
                val raw = obj.optLong("timestamp")
                if (raw in 1..99_999_999_999L) raw * 1000L else raw
            }
            else -> 0L
        }
    }

    fun syncWithSettings(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository? = null
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        if (glucoseRepository != null) {
            cachedGlucoseRepo = glucoseRepository
        }

        syncJob?.cancel()
        syncJob = scope.launch {
            settingsRepository.getSettings()
                .map { it.nightscoutSettings }
                .distinctUntilChanged()
                .collect { ns ->
                    mutex.withLock {
                        if (ns.isEnabled && ns.isValidUrl) {
                            _statusFlow.value = _statusFlow.value.copy(isEnabled = true)
                            if (activeNsSettings != ns || pollJob?.isActive != true) {
                                stopPolling()
                                activeNsSettings = ns
                                startPolling(ns)
                            }
                        } else {
                            activeNsSettings = null
                            stopPolling()
                            _statusFlow.value = NightscoutStatus(isEnabled = false)
                        }
                    }
                }
        }
    }

    private fun startPolling(settings: NightscoutSettings) {
        pollJob?.cancel()
        Log.i(TAG, "Starting Nightscout telemetry & sync polling for ${settings.serverUrl}")
        pollJob = scope.launch {
            while (true) {
                try {
                    executeFetchCycle(settings)
                } catch (e: Exception) {
                    Log.w(TAG, "Error in Nightscout status poll loop: ${e.message}")
                    _statusFlow.value = _statusFlow.value.copy(
                        isConnected = false,
                        errorMessage = e.message
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        activeNsSettings = null
        Log.i(TAG, "Stopped Nightscout telemetry & sync polling")
    }

    suspend fun pollNow() = withContext(Dispatchers.IO) {
        val repo = cachedSettingsRepo ?: return@withContext
        val settings = repo.getSettings().firstOrNull()?.nightscoutSettings ?: return@withContext
        if (!settings.isEnabled || !settings.isValidUrl) return@withContext

        val now = System.currentTimeMillis()
        if (now - lastPollTime < 8_000L) {
            Log.d(TAG, "Skipping pollNow (debounced)")
            return@withContext
        }

        executeFetchCycle(settings)
    }

    private suspend fun executeFetchCycle(settings: NightscoutSettings) = withContext(Dispatchers.IO) {
        lastPollTime = System.currentTimeMillis()
        val cleanUrl = settings.getCleanBaseUrl()

        // 1. Telemetry: Try /api/v1/devicestatus.json?count=1
        val dsResult = fetchDeviceStatus(cleanUrl, settings.apiSecret)
        var batteryFound = false
        if (dsResult.isSuccess) {
            val status = dsResult.getOrNull()!!
            if (status.masterBattery != null) {
                updateSuccess(status)
                batteryFound = true
            }
        }

        // 2. Fetch /pebble (for battery fallback and live IoB/CoB/Glucose)
        var pebbleData: XdripPebbleResult? = null
        val pebbleResult = fetchPebbleStatus(cleanUrl, settings.apiSecret)
        if (pebbleResult.isSuccess) {
            val (status, pebble) = pebbleResult.getOrNull()!!
            pebbleData = pebble
            if (!batteryFound) {
                updateSuccess(status)
                batteryFound = true
            }
        }

        if (!batteryFound && !dsResult.isSuccess && !pebbleResult.isSuccess) {
            val err = dsResult.exceptionOrNull()?.message ?: pebbleResult.exceptionOrNull()?.message
            _statusFlow.value = _statusFlow.value.copy(
                isConnected = false,
                errorMessage = err,
                lastCheckTimestamp = System.currentTimeMillis()
            )
        }

        // 3. Process glucose readings (Cloud Follower) if enabled
        if (settings.downloadGlucose) {
            try {
                fetchAndProcessGlucose(cleanUrl, settings, pebbleData)
            } catch (e: Exception) {
                Log.w(TAG, "Error processing Nightscout glucose readings: ${e.message}")
            }
        }
    }

    private suspend fun updateSuccess(status: NightscoutStatus) {
        val previousBattery = _statusFlow.value.masterBattery
        _statusFlow.value = status.copy(
            isEnabled = true,
            isConnected = true,
            errorMessage = null,
            lastCheckTimestamp = System.currentTimeMillis()
        )

        // Trigger widget and notification refresh if battery changed or updated
        if (status.masterBattery != null && status.masterBattery != previousBattery) {
            appContext?.let { ctx ->
                TirupWidgetUpdater.updateAllWidgets(ctx)
            }
        }
    }

    private fun fetchDeviceStatus(cleanUrl: String, apiSecret: String): Result<NightscoutStatus> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$cleanUrl/api/v1/devicestatus.json?count=1")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1(apiSecret))
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val first = array.getJSONObject(0)
                    val battery = extractBattery(first)
                    val ts = extractTimestamp(first)
                    val device = first.optString("device").ifBlank { null }
                    return Result.success(
                        NightscoutStatus(
                            masterBattery = battery,
                            lastBatteryTimestamp = if (ts > 0L) ts else System.currentTimeMillis(),
                            lastSuccessTimestamp = System.currentTimeMillis(),
                            uploaderDevice = device
                        )
                    )
                }
                return Result.success(NightscoutStatus(lastSuccessTimestamp = System.currentTimeMillis()))
            } else {
                return Result.failure(IllegalStateException("HTTP $code from devicestatus.json"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchPebbleStatus(cleanUrl: String, apiSecret: String): Result<Pair<NightscoutStatus, XdripPebbleResult>> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$cleanUrl/pebble")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1(apiSecret))
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val pebble = XdripLanClient.parsePebbleResponse(body)
                val status = if (pebble.battery != null) {
                    NightscoutStatus(
                        masterBattery = pebble.battery,
                        lastBatteryTimestamp = pebble.timestamp ?: System.currentTimeMillis(),
                        lastSuccessTimestamp = System.currentTimeMillis()
                    )
                } else {
                    NightscoutStatus(lastSuccessTimestamp = System.currentTimeMillis())
                }
                return Result.success(Pair(status, pebble))
            } else {
                return Result.failure(IllegalStateException("HTTP $code from pebble"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchEntries(cleanUrl: String, apiSecret: String, count: Int = 12): Result<List<GlucoseReading>> {
        var conn: HttpURLConnection? = null
        try {
            // 1. Primary: /api/v1/entries/sgv.json?count=N
            var targetUrl = "$cleanUrl/api/v1/entries/sgv.json?count=$count"
            var connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1(apiSecret))
                }
            }
            conn = connection
            var code = connection.responseCode

            // 2. Fallback: /api/v1/entries.json?count=N
            if (code == 404 || code == 400) {
                conn.disconnect()
                targetUrl = "$cleanUrl/api/v1/entries.json?count=$count"
                connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    if (apiSecret.isNotBlank()) {
                        setRequestProperty("api-secret", sha1(apiSecret))
                    }
                }
                conn = connection
                code = connection.responseCode
            }

            // 3. Fallback: /sgv.json?count=N
            if (code == 404 || code == 400) {
                conn.disconnect()
                targetUrl = "$cleanUrl/sgv.json?count=$count"
                connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    if (apiSecret.isNotBlank()) {
                        setRequestProperty("api-secret", sha1(apiSecret))
                    }
                }
                conn = connection
                code = connection.responseCode
            }

            if (code in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val readings = XdripLanClient.parseSgvJson(body)
                return Result.success(readings)
            } else {
                return Result.failure(IllegalStateException("HTTP $code from entries endpoint"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun fetchAndProcessGlucose(
        cleanUrl: String,
        settings: NightscoutSettings,
        pebbleData: XdripPebbleResult?
    ) {
        val repo = cachedGlucoseRepo ?: return
        val now = System.currentTimeMillis()
        val latestInDb = repo.getLatestReading().firstOrNull()

        // If we already have fresh data (< 2.5 min old from higher-priority source), skip remote batch fetch
        val hasVeryFreshReading = latestInDb != null && (now - latestInDb.timestamp < 150_000L)

        val readings = mutableListOf<GlucoseReading>()

        if (!hasVeryFreshReading) {
            // Determine count: if large gap (> 30 min), fetch 24 readings (~2 hours), otherwise 12 readings (~1 hour)
            val gapMinutes = if (latestInDb != null) (now - latestInDb.timestamp) / 60_000L else 120L
            val fetchCount = if (gapMinutes > 30L) 24 else 12

            val entriesResult = fetchEntries(cleanUrl, settings.apiSecret, count = fetchCount)
            if (entriesResult.isSuccess) {
                val fetched = entriesResult.getOrNull() ?: emptyList()
                readings.addAll(fetched)
            }
        }

        // Fallback: If entries list was empty, but pebble has a fresh glucose reading (< 15 min), use pebble reading
        if (readings.isEmpty() && pebbleData?.glucoseMmol != null && pebbleData.timestamp != null) {
            val pebbleTs = pebbleData.timestamp
            if (now - pebbleTs < 15 * 60_000L) {
                readings.add(
                    GlucoseReading(
                        timestamp = pebbleTs,
                        valueMmol = pebbleData.glucoseMmol,
                        trendArrow = pebbleData.trendArrow,
                        iob = pebbleData.iob,
                        cob = pebbleData.cob
                    )
                )
            }
        }

        if (readings.isNotEmpty()) {
            val sorted = readings.sortedByDescending { it.timestamp }
            val enriched = sorted.mapIndexed { index, r ->
                if (index == 0 && (pebbleData?.iob != null || pebbleData?.cob != null)) {
                    r.copy(
                        iob = pebbleData.iob ?: r.iob,
                        cob = pebbleData.cob ?: r.cob
                    )
                } else r
            }

            // Insert batch with NIGHTSCOUT_CLOUD priority (rank 1: will never overwrite BLE/LAN/local xDrip)
            repo.insertReadingsBatchFromSource(enriched, DataSourcePriority.NIGHTSCOUT_CLOUD)

            val latest = enriched.firstOrNull()
            if (latest != null && (now - latest.timestamp < 15 * 60_000L)) {
                appContext?.let { ctx ->
                    val userSettings = cachedSettingsRepo?.getSettings()?.firstOrNull() ?: return@let
                    GlucoseAlertManager.refreshLockscreenNotificationAndWidgets(ctx, userSettings, latest)
                    val recent = repo.getRecentReadings(30).firstOrNull() ?: listOf(latest)
                    GlucoseAlertManager.checkAndAlert(
                        context = ctx,
                        recentReadings = recent,
                        settings = userSettings
                    )
                }
            }
        } else if (latestInDb != null && (pebbleData?.iob != null || pebbleData?.cob != null)) {
            // Even if no new readings to insert, enrich latest DB reading if it is missing iob/cob
            if ((latestInDb.iob == null && pebbleData.iob != null) || (latestInDb.cob == null && pebbleData.cob != null)) {
                val enrichedLatest = latestInDb.copy(
                    iob = pebbleData.iob ?: latestInDb.iob,
                    cob = pebbleData.cob ?: latestInDb.cob
                )
                repo.insertReadingFromSource(enrichedLatest, DataSourcePriority.NIGHTSCOUT_CLOUD)
            }
        }
    }
}
