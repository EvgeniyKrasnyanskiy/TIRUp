package com.tirup.app.data.network

import android.content.Context
import android.util.Log
import com.tirup.app.domain.model.NightscoutSettings
import com.tirup.app.domain.model.NightscoutStatus
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
import kotlinx.coroutines.flow.firstOrNull
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
    private var pollJob: Job? = null
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
        settingsRepository: SettingsRepository
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository

        scope.launch {
            settingsRepository.getSettings().collect { settings ->
                mutex.withLock {
                    val ns = settings.nightscoutSettings
                    if (ns.isEnabled && ns.isValidUrl) {
                        _statusFlow.value = _statusFlow.value.copy(isEnabled = true)
                        if (pollJob?.isActive != true) {
                            startPolling(ns)
                        }
                    } else {
                        stopPolling()
                        _statusFlow.value = NightscoutStatus(isEnabled = false)
                    }
                }
            }
        }
    }

    private fun startPolling(settings: NightscoutSettings) {
        pollJob?.cancel()
        Log.i(TAG, "Starting Nightscout telemetry polling for ${settings.serverUrl}")
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
        Log.i(TAG, "Stopped Nightscout telemetry polling")
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

        // 1. Try /api/v1/devicestatus.json?count=1
        val dsResult = fetchDeviceStatus(cleanUrl, settings.apiSecret)
        if (dsResult.isSuccess) {
            val status = dsResult.getOrNull()!!
            if (status.masterBattery != null) {
                updateSuccess(status)
                return@withContext
            }
        }

        // 2. Fallback to /pebble
        val pebbleResult = fetchPebbleStatus(cleanUrl, settings.apiSecret)
        if (pebbleResult.isSuccess) {
            val status = pebbleResult.getOrNull()!!
            updateSuccess(status)
        } else {
            val err = dsResult.exceptionOrNull()?.message ?: pebbleResult.exceptionOrNull()?.message
            _statusFlow.value = _statusFlow.value.copy(
                isConnected = false,
                errorMessage = err,
                lastCheckTimestamp = System.currentTimeMillis()
            )
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

    private fun fetchPebbleStatus(cleanUrl: String, apiSecret: String): Result<NightscoutStatus> {
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
                if (pebble.battery != null) {
                    return Result.success(
                        NightscoutStatus(
                            masterBattery = pebble.battery,
                            lastBatteryTimestamp = pebble.timestamp ?: System.currentTimeMillis(),
                            lastSuccessTimestamp = System.currentTimeMillis()
                        )
                    )
                }
                return Result.success(NightscoutStatus(lastSuccessTimestamp = System.currentTimeMillis()))
            } else {
                return Result.failure(IllegalStateException("HTTP $code from pebble"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
