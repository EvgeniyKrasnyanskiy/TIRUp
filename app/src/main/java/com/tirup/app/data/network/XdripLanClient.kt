package com.tirup.app.data.network

import android.util.Log
import com.tirup.app.data.receiver.DexdripBroadcastReceiver
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.model.XdripLanSettings
import com.tirup.app.domain.model.XdripLanTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SubnetInfo(
    val prefix: String,
    val gatewayIp: String?,
    val localIp: String
)

data class XdripPebbleResult(
    val iob: Double? = null,
    val cob: Double? = null,
    val battery: Int? = null,
    val glucoseMmol: Double? = null,
    val trendArrow: String? = null,
    val timestamp: Long? = null
)

object XdripLanClient {

    private const val TAG = "XdripLanClient"
    private const val CONNECT_TIMEOUT_MS = 3500
    private const val READ_TIMEOUT_MS = 3500

    fun getLocalSubnetInfo(context: Context): SubnetInfo? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val ipInt = dhcp.ipAddress
                val localIp = String.format(java.util.Locale.US, "%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
                val gwInt = dhcp.gateway
                val gatewayIp = if (gwInt != 0) String.format(java.util.Locale.US, "%d.%d.%d.%d", gwInt and 0xff, gwInt shr 8 and 0xff, gwInt shr 16 and 0xff, gwInt shr 24 and 0xff) else null
                val prefix = localIp.substringBeforeLast(".") + "."
                return SubnetInfo(prefix, gatewayIp, localIp)
            }
        } catch (_: Exception) {}

        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            val prefix = host.substringBeforeLast(".") + "."
                            return SubnetInfo(prefix, "${prefix}1", host)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun probeSocket(host: String, port: Int, timeoutMs: Int = 800): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyXdripEndpoint(host: String, port: Int, apiSecret: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val settings = XdripLanSettings(isEnabled = true, masterHost = host, port = port, apiSecret = apiSecret)
            val url = URL("${settings.baseUrl}/pebble")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1200
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (settings.hashedSecret != null) {
                    setRequestProperty("api-secret", settings.hashedSecret!!)
                }
            }
            val code = conn.responseCode
            code in 200..299 || code == 401 || code == 403
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun discoverMaster(context: Context, port: Int = 17580, apiSecret: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val subnetInfo = getLocalSubnetInfo(context) ?: return@withContext Result.failure(
            IllegalStateException("Устройство не подключено к сети Wi-Fi или Hotspot")
        )

        val foundChannel = kotlinx.coroutines.channels.Channel<String>(1)

        // 1. First probe Gateway (master is almost always at Gateway in Hotspot mode e.g. 192.168.43.1)
        val gw = subnetInfo.gatewayIp
        if (!gw.isNullOrBlank() && gw != subnetInfo.localIp) {
            if (probeSocket(gw, port, 600) && verifyXdripEndpoint(gw, port, apiSecret)) {
                return@withContext Result.success(gw)
            }
        }

        // 2. Parallel sweep of 1..254 with 2.5s overall timeout
        val scanScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val jobs = mutableListOf<Job>()

        for (i in 1..254) {
            val candidateIp = "${subnetInfo.prefix}$i"
            if (candidateIp == subnetInfo.localIp || candidateIp == gw) continue

            val job = scanScope.launch {
                if (probeSocket(candidateIp, port, 800)) {
                    if (verifyXdripEndpoint(candidateIp, port, apiSecret)) {
                        foundChannel.trySend(candidateIp)
                    }
                }
            }
            jobs.add(job)
        }

        var discoveredIp: String? = null
        try {
            withTimeoutOrNull(2500L) {
                discoveredIp = foundChannel.receiveCatching().getOrNull()
            }
        } catch (_: Exception) {}

        jobs.forEach { it.cancel() }

        if (discoveredIp != null) {
            Result.success(discoveredIp!!)
        } else {
            Result.failure(IllegalStateException("Мастер xDrip+ не найден в подсети ${subnetInfo.prefix}0/24 на порту $port"))
        }
    }

    private fun openConnection(endpointUrl: String, settings: XdripLanSettings): HttpURLConnection {
        val hasSecret = settings.apiSecret.isNotBlank()
        val finalUrl = if (hasSecret && settings.hashedSecret != null) {
            val delimiter = if (endpointUrl.contains("?")) "&" else "?"
            "$endpointUrl${delimiter}token=${settings.hashedSecret}"
        } else {
            endpointUrl
        }

        val url = URL(finalUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            if (hasSecret) {
                settings.hashedSecret?.let { setRequestProperty("api-secret", it) }
            }
        }
        return conn
    }

    suspend fun testConnection(settings: XdripLanSettings): Result<XdripLanTestResult> = withContext(Dispatchers.IO) {
        if (!settings.isValidHost) {
            return@withContext Result.failure(IllegalArgumentException("Не указан IP-адрес мастера или неверный порт"))
        }

        val startTime = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            // First, try /pebble to get battery and current status
            val pebbleUrl = "${settings.baseUrl}/pebble"
            conn = openConnection(pebbleUrl, settings)
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime

            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val pebbleResult = parsePebbleResponse(body)
                val msg = buildString {
                    append("Связь установлена (${latency} мс). ")
                    if (pebbleResult.glucoseMmol != null) {
                        append("Сахар: ${String.format(java.util.Locale.US, "%.1f", pebbleResult.glucoseMmol)} ммоль/л ${pebbleResult.trendArrow ?: ""}. ")
                    }
                    if (pebbleResult.battery != null) {
                        append("Батарея мастера: ${pebbleResult.battery}%. ")
                    }
                    if (pebbleResult.iob != null && pebbleResult.iob > 0.0) {
                        append("IoB: ${pebbleResult.iob} ед. ")
                    }
                }.trim()

                return@withContext Result.success(
                    XdripLanTestResult(
                        isSuccess = true,
                        responseTimeMs = latency,
                        masterBattery = pebbleResult.battery,
                        glucoseMmol = pebbleResult.glucoseMmol,
                        trendArrow = pebbleResult.trendArrow,
                        iob = pebbleResult.iob,
                        cob = pebbleResult.cob,
                        message = msg
                    )
                )
            } else if (code == 401 || code == 403) {
                return@withContext Result.failure(IllegalStateException("Ошибка авторизации (HTTP $code). Проверьте API Secret."))
            } else {
                // Fallback to /sgv.json?count=1
                conn.disconnect()
                val sgvResult = fetchSgv(settings, count = 1)
                val sgvList = sgvResult.getOrNull()
                if (!sgvList.isNullOrEmpty()) {
                    val latest = sgvList.first()
                    return@withContext Result.success(
                        XdripLanTestResult(
                            isSuccess = true,
                            responseTimeMs = System.currentTimeMillis() - startTime,
                            glucoseMmol = latest.valueMmol,
                            trendArrow = latest.trendArrow,
                            message = "Связь установлена. Сахар: ${String.format(java.util.Locale.US, "%.1f", latest.valueMmol)} ммоль/л"
                        )
                    )
                }
                return@withContext Result.failure(IllegalStateException("Сервер ответил кодом HTTP $code"))
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Не удалось подключиться к ${settings.masterHost}:${settings.port}"
            return@withContext Result.failure(Exception("Ошибка соединения: $msg", e))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchSgv(settings: XdripLanSettings, count: Int = 10): Result<List<GlucoseReading>> = withContext(Dispatchers.IO) {
        if (!settings.isValidHost) {
            return@withContext Result.failure(IllegalArgumentException("Неверный адрес мастера"))
        }

        var conn: HttpURLConnection? = null
        try {
            val urlStr = "${settings.baseUrl}/sgv.json?count=$count"
            conn = openConnection(urlStr, settings)

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code from sgv.json"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val readings = parseSgvJson(body)
            Result.success(readings)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchPebble(settings: XdripLanSettings): Result<XdripPebbleResult> = withContext(Dispatchers.IO) {
        if (!settings.isValidHost) {
            return@withContext Result.failure(IllegalArgumentException("Неверный адрес мастера"))
        }

        var conn: HttpURLConnection? = null
        try {
            val urlStr = "${settings.baseUrl}/pebble"
            conn = openConnection(urlStr, settings)

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code from pebble"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val result = parsePebbleResponse(body)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchTreatments(settings: XdripLanSettings, count: Int = 30): Result<List<Treatment>> = withContext(Dispatchers.IO) {
        if (!settings.isValidHost) {
            return@withContext Result.failure(IllegalArgumentException("Неверный адрес мастера"))
        }

        var conn: HttpURLConnection? = null
        try {
            // First attempt: /api/v1/treatments.json
            val primaryUrl = "${settings.baseUrl}/api/v1/treatments.json?count=$count"
            conn = openConnection(primaryUrl, settings)
            var code = conn.responseCode

            if (code == 404 || code == 400) {
                conn.disconnect()
                // Fallback attempt: /treatments.json
                val fallbackUrl = "${settings.baseUrl}/treatments.json?count=$count"
                conn = openConnection(fallbackUrl, settings)
                code = conn.responseCode
            }

            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code from treatments.json"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val list = DexdripBroadcastReceiver.Companion.parseTreatmentsJson(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    fun parseSgvJson(jsonStr: String): List<GlucoseReading> {
        val result = mutableListOf<GlucoseReading>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue

                val rawSgv = DexdripBroadcastReceiver.Companion.parseJsonDouble(obj, "sgv") ?: continue
                if (rawSgv <= 0.0) continue

                // xDrip SGV is in mg/dL (> 35.0) or mmol/L (<= 35.0)
                val valueMmol = if (rawSgv > 35.0) rawSgv / 18.0182 else rawSgv

                val ts = when {
                    obj.has("date") -> obj.optLong("date")
                    obj.has("mills") -> obj.optLong("mills")
                    obj.has("timestamp") -> {
                        val raw = obj.optLong("timestamp")
                        if (raw in 1..99_999_999_999L) raw * 1000L else raw
                    }
                    else -> 0L
                }
                if (ts <= 0L) continue

                val direction = obj.optString("direction").ifBlank { obj.optString("trend_name") }
                val arrow = DexdripBroadcastReceiver.Companion.slopeToArrow(direction)

                result.add(
                    GlucoseReading(
                        timestamp = ts,
                        valueMmol = valueMmol,
                        trendArrow = arrow
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SGV JSON: ${e.message}")
        }
        return result
    }

    fun parsePebbleResponse(jsonStr: String): XdripPebbleResult {
        var iob: Double? = null
        var cob: Double? = null
        var battery: Int? = null
        var glucoseMmol: Double? = null
        var trendArrow: String? = null
        var timestamp: Long? = null

        try {
            val root = JSONObject(jsonStr)

            // Top-level iob / cob
            val rootIob = DexdripBroadcastReceiver.Companion.parseJsonDouble(root, "iob")
            if (rootIob != null && rootIob >= 0.0) iob = rootIob

            val rootCob = DexdripBroadcastReceiver.Companion.parseJsonDouble(root, "cob")
            if (rootCob != null && rootCob >= 0.0) cob = rootCob

            // Top-level or status array
            var bgsArray: JSONArray? = null
            if (root.has("bgs")) {
                bgsArray = root.optJSONArray("bgs")
            } else if (root.has("status")) {
                val statusArr = root.optJSONArray("status")
                if (statusArr != null && statusArr.length() > 0) {
                    val firstStatus = statusArr.optJSONObject(0)
                    if (firstStatus != null && firstStatus.has("bgs")) {
                        bgsArray = firstStatus.optJSONArray("bgs")
                    }
                }
            }

            if (bgsArray != null && bgsArray.length() > 0) {
                val firstBg = bgsArray.optJSONObject(0)
                if (firstBg != null) {
                    if (iob == null) {
                        val v = DexdripBroadcastReceiver.Companion.parseJsonDouble(firstBg, "iob")
                        if (v != null && v >= 0.0) iob = v
                    }
                    if (cob == null) {
                        val v = DexdripBroadcastReceiver.Companion.parseJsonDouble(firstBg, "cob")
                        if (v != null && v >= 0.0) cob = v
                    }

                    // Battery
                    if (firstBg.has("battery")) {
                        val rawBattery = firstBg.opt("battery")
                        battery = when (rawBattery) {
                            is Number -> rawBattery.toInt().coerceIn(0, 100)
                            is String -> rawBattery.trim().toIntOrNull()?.coerceIn(0, 100)
                            else -> null
                        }
                    }

                    // SGV
                    val rawSgv = DexdripBroadcastReceiver.Companion.parseJsonDouble(firstBg, "sgv")
                    if (rawSgv != null && rawSgv > 0.0) {
                        glucoseMmol = if (rawSgv > 35.0) rawSgv / 18.0182 else rawSgv
                    }

                    // Direction
                    val dir = firstBg.optString("direction")
                    if (dir.isNotBlank()) {
                        trendArrow = DexdripBroadcastReceiver.Companion.slopeToArrow(dir)
                    }

                    // Timestamp
                    if (firstBg.has("datetime")) {
                        timestamp = firstBg.optLong("datetime")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse pebble response: ${e.message}")
        }

        return XdripPebbleResult(
            iob = iob,
            cob = cob,
            battery = battery,
            glucoseMmol = glucoseMmol,
            trendArrow = trendArrow,
            timestamp = timestamp
        )
    }
}
