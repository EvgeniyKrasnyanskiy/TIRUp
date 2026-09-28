package com.tirup.app.data.network

import android.util.Log
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.NightscoutSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class NightscoutStatusResult(
    val name: String,
    val version: String,
    val latencyMs: Long
)

object NightscoutUploadManager {
    private const val TAG = "NightscoutUploadManager"
    private const val TIMEOUT_MS = 6000

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatIso8601(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    suspend fun checkConnection(serverUrl: String, apiSecret: String): Result<NightscoutStatusResult> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("URL должен начинаться с http:// или https://"))
        }

        val targetUrl = "$cleanUrl/api/v1/status.json"
        var conn: HttpURLConnection? = null
        val startTime = System.currentTimeMillis()

        try {
            val url = URL(targetUrl)
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
            val latency = System.currentTimeMillis() - startTime

            if (code in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val json = try {
                    JSONObject(responseBody)
                } catch (_: Exception) {
                    JSONObject()
                }
                val name = json.optString("name", "Nightscout")
                val version = json.optString("version", "1.0.0")
                Result.success(NightscoutStatusResult(name = name, version = version, latencyMs = latency))
            } else if (code == 401) {
                Result.failure(IllegalStateException("Неверный API Secret (401 Unauthorized)"))
            } else {
                Result.failure(IllegalStateException("Сервер ответил HTTP $code"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection check failed: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun uploadTreatment(
        settings: NightscoutSettings,
        insulin: Double? = null,
        carbs: Double? = null,
        glucose: Double? = null,
        unit: GlucoseUnit = GlucoseUnit.MMOL_L,
        notes: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!settings.isEnabled || !settings.isValidUrl) {
            return@withContext Result.failure(IllegalStateException("Синхронизация с сервером выключена или URL не задан"))
        }

        val cleanUrl = settings.getCleanBaseUrl()
        val targetUrl = "$cleanUrl/api/v1/treatments"
        val itemUuid = UUID.randomUUID().toString()

        val eventType = when {
            insulin != null && insulin > 0.0 && carbs != null && carbs > 0.0 -> "Meal Bolus"
            insulin != null && insulin > 0.0 -> "Correction Bolus"
            carbs != null && carbs > 0.0 -> "Carb Intake"
            glucose != null && glucose > 0.0 -> "BG Check"
            else -> "Note"
        }

        val payload = JSONObject().apply {
            put("uuid", itemUuid)
            put("_id", itemUuid)
            put("eventType", eventType)
            put("insulin", insulin ?: 0.0)
            put("carbs", carbs ?: 0.0)
            put("notes", notes ?: "")
            put("created_at", formatIso8601(timestamp))
            put("date", timestamp)
            put("enteredBy", "TIRUp")

            if (glucose != null && glucose > 0.0) {
                put("glucose", glucose)
                put("units", if (unit == GlucoseUnit.MMOL_L) "mmol" else "mgdl")
                put("glucoseType", "Finger")
            }
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (settings.apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1(settings.apiSecret))
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "Treatment successfully posted: uuid=$itemUuid event=$eventType insulin=$insulin carbs=$carbs")
                Result.success(itemUuid)
            } else if (code == 401) {
                Result.failure(IllegalStateException("Неверный API Secret (401)"))
            } else {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                Log.w(TAG, "Treatment post failed with code $code: $errText")
                Result.failure(IllegalStateException("Ошибка сервера: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post treatment to $targetUrl: ${e.message}", e)
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun deleteTreatment(
        settings: NightscoutSettings,
        uuid: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isEnabled || !settings.isValidUrl) {
            return@withContext Result.failure(IllegalStateException("Синхронизация с сервером выключена или URL не задан"))
        }
        val cleanUuid = uuid.trim()
        if (cleanUuid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("UUID записи не может быть пустым"))
        }

        val cleanUrl = settings.getCleanBaseUrl()
        val tokenParam = if (settings.apiSecret.isNotBlank()) "?token=${java.net.URLEncoder.encode(settings.apiSecret, "UTF-8")}" else ""
        val targetUrl = "$cleanUrl/api/v1/treatments/$cleanUuid$tokenParam"

        var conn: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "DELETE"
                setRequestProperty("Accept", "application/json")
                if (settings.apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1(settings.apiSecret))
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "Treatment successfully voided/deleted on server: uuid=$cleanUuid (HTTP $code)")
                Result.success(Unit)
            } else if (code == 401) {
                Result.failure(IllegalStateException("Неверный API Secret (401)"))
            } else {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                Log.w(TAG, "Treatment delete failed with code $code: $errText")
                Result.failure(IllegalStateException("Ошибка сервера: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete treatment $cleanUuid from $targetUrl: ${e.message}", e)
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
