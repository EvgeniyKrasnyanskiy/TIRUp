package com.tirup.app.data.network

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.receiver.AlertActionReceiver
import com.tirup.app.data.receiver.DexdripBroadcastReceiver
import com.tirup.app.domain.model.DataSourcePriority
import com.tirup.app.domain.model.LanConnectionState
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.XdripLanSettings
import com.tirup.app.domain.model.XdripLanStatus
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object XdripLanManager {

    private const val TAG = "XdripLanManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var cachedSettingsRepo: SettingsRepository? = null
    private var cachedGlucoseRepo: GlucoseRepository? = null
    private var cachedDb: AppDatabase? = null

    private var pollJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var treatmentsPollCounter = 0
    private var lastPollExecutionTime = 0L
    private var lastReadingTimestamp = 0L

    private val _statusFlow = MutableStateFlow(XdripLanStatus())
    val statusFlow: StateFlow<XdripLanStatus> = _statusFlow.asStateFlow()

    private val _isDiscoveringFlow = MutableStateFlow(false)
    val isDiscoveringFlow: StateFlow<Boolean> = _isDiscoveringFlow.asStateFlow()

    fun syncWithSettings(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        database: AppDatabase
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        cachedGlucoseRepo = glucoseRepository
        cachedDb = database

        scope.launch {
            settingsRepository.getSettings().collect { settings ->
                mutex.withLock {
                    val lan = settings.xdripLanSettings
                    if (lan.isEnabled && lan.isConfigured) {
                        if (pollJob?.isActive == true) {
                            stopPollingInternal()
                        }
                        startPollingInternal(lan, settings)
                    } else {
                        stopPollingInternal()
                    }
                }
            }
        }
    }

    private fun isWifiOrHotspotConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun scheduleNextPollAlarm(context: Context, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_POLL_XDRIP_LAN
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AlertActionReceiver.REQUEST_CODE_POLL_LAN,
            intent,
            flags
        )
        val triggerAtMillis = System.currentTimeMillis() + delayMs.coerceAtLeast(5_000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next LAN poll alarm in ${delayMs / 1000}s (at $triggerAtMillis)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule next LAN poll alarm: ${e.message}")
        }
    }

    fun cancelPollAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_POLL_XDRIP_LAN
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AlertActionReceiver.REQUEST_CODE_POLL_LAN,
            intent,
            flags
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled pending LAN poll alarm")
        }
    }

    fun calculateNextPollIntervalMs(latestTimestamp: Long, configuredIntervalSec: Int): Long {
        val configuredMs = (configuredIntervalSec.coerceIn(15, 300) * 1000L)
        if (latestTimestamp <= 0L) {
            return configuredMs
        }
        val now = System.currentTimeMillis()
        val elapsedMs = now - latestTimestamp

        return when {
            // Very fresh: point just arrived (< 20 seconds ago).
            // Sensor won't send new reading for at least 40-60s (1-min sensor) or ~5m (5-min sensor).
            // Sleep for 35s to save CPU and battery.
            elapsedMs < 20_000L -> 35_000L

            // Approaching 1-minute mark: (20s .. 75s).
            // Poll rapidly every 15s to catch 1-minute sensors (Libre 3 / Dexcom G7 / Aidex) on time.
            elapsedMs in 20_000L..75_000L -> 15_000L

            // Between 1.25 min and 4.2 min: (75s .. 250s).
            // Likely a 5-minute sensor (Dexcom G6 / Libre 2 / MiaoMiao / Bubble).
            // Sleep 60s between checks.
            elapsedMs in 75_000L..250_000L -> 60_000L

            // Approaching 5-minute mark: (250s .. 330s).
            // Poll rapidly every 15s to catch the new 5-minute reading.
            elapsedMs in 250_000L..330_000L -> 15_000L

            // Sensor signal loss or warmup: (> 10 minutes without data).
            // Avoid burning battery polling every 15s when sensor is lost/warming up; poll every 60s.
            elapsedMs > 10 * 60_000L -> 60_000L

            // Default fallback
            else -> configuredMs
        }
    }

    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Wi-Fi network became available. Triggering immediate poll and resuming cadence.")
                _statusFlow.value = _statusFlow.value.copy(isWifiConnected = true)
                scope.launch {
                    val settings = cachedSettingsRepo?.getSettings()?.firstOrNull() ?: return@launch
                    if (settings.xdripLanSettings.isEnabled && settings.xdripLanSettings.isConfigured) {
                        executePollCycle(settings.xdripLanSettings, settings)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Wi-Fi network lost.")
                val isStillConnected = isWifiOrHotspotConnected(context)
                _statusFlow.value = _statusFlow.value.copy(isWifiConnected = isStillConnected)
                if (!isStillConnected) {
                    cancelPollAlarm(context)
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            Log.d(TAG, "NetworkCallback registered for Wi-Fi")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback(context: Context) {
        val callback = networkCallback ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
            Log.d(TAG, "NetworkCallback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister NetworkCallback: ${e.message}")
        } finally {
            networkCallback = null
        }
    }

    private fun startPollingInternal(settings: XdripLanSettings, userSettings: UserSettings) {
        val ctx = appContext ?: return
        registerNetworkCallback(ctx)

        if (pollJob?.isActive == true) return

        Log.i(TAG, "Starting xDrip LAN follower polling for host ${settings.masterHost}:${settings.port}")
        _statusFlow.value = _statusFlow.value.copy(
            state = LanConnectionState.CONNECTING,
            masterIp = settings.masterHost,
            errorMessage = null
        )

        pollJob = scope.launch {
            while (true) {
                try {
                    val currentCtx = appContext ?: break
                    val isWifi = isWifiOrHotspotConnected(currentCtx)
                    _statusFlow.value = _statusFlow.value.copy(isWifiConnected = isWifi)

                    if (!isWifi) {
                        Log.d(TAG, "Device not connected to Wi-Fi. Skipping LAN polling to preserve battery.")
                        _statusFlow.value = _statusFlow.value.copy(
                            state = LanConnectionState.DISCONNECTED,
                            errorMessage = "Ожидание Wi-Fi подключения"
                        )
                        delay(15_000L)
                        continue
                    }

                    executePollCycle(settings, userSettings)
                } catch (e: Exception) {
                    Log.w(TAG, "Error in LAN polling loop: ${e.message}")
                    _statusFlow.value = _statusFlow.value.copy(
                        state = LanConnectionState.ERROR,
                        errorMessage = e.message
                    )
                }

                val currentInterval = calculateNextPollIntervalMs(
                    lastReadingTimestamp,
                    settings.pollIntervalSeconds
                )
                delay(currentInterval)
            }
        }
    }

    private fun stopPollingInternal() {
        pollJob?.cancel()
        pollJob = null
        val ctx = appContext
        if (ctx != null) {
            cancelPollAlarm(ctx)
            unregisterNetworkCallback(ctx)
        }
        _statusFlow.value = _statusFlow.value.copy(
            state = LanConnectionState.DISABLED,
            errorMessage = null
        )
        Log.i(TAG, "Stopped xDrip LAN follower polling")
    }

    suspend fun pollNow() = withContext(Dispatchers.IO) {
        val settings = cachedSettingsRepo?.getSettings()?.firstOrNull() ?: return@withContext
        val lan = settings.xdripLanSettings
        if (!lan.isEnabled || !lan.isConfigured) return@withContext
        val ctx = appContext ?: return@withContext
        if (!isWifiOrHotspotConnected(ctx)) return@withContext
        executePollCycle(lan, settings)
    }

    suspend fun discoverMaster(): Result<String> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext Result.failure(IllegalStateException("Контекст недоступен"))
        val repo = cachedSettingsRepo ?: return@withContext Result.failure(IllegalStateException("Репозиторий недоступен"))
        val settings = repo.getSettings().firstOrNull() ?: return@withContext Result.failure(IllegalStateException("Настройки недоступны"))
        val lan = settings.xdripLanSettings

        _isDiscoveringFlow.value = true
        _statusFlow.value = _statusFlow.value.copy(
            state = LanConnectionState.CONNECTING,
            errorMessage = "Поиск мастера в подсети..."
        )

        try {
            val res = XdripLanClient.discoverMaster(ctx, lan.port, lan.apiSecret)
            if (res.isSuccess) {
                val foundIp = res.getOrNull()!!
                Log.i(TAG, "xDrip+ master discovered at $foundIp")
                val updated = settings.copy(
                    xdripLanSettings = lan.copy(masterHost = foundIp)
                )
                repo.updateSettings(updated)
                _statusFlow.value = _statusFlow.value.copy(
                    masterIp = foundIp,
                    errorMessage = null
                )
                executePollCycle(updated.xdripLanSettings, updated)
                Result.success(foundIp)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Мастер не найден"
                _statusFlow.value = _statusFlow.value.copy(
                    state = LanConnectionState.ERROR,
                    errorMessage = err
                )
                Result.failure(Exception(err))
            }
        } finally {
            _isDiscoveringFlow.value = false
        }
    }

    private suspend fun executePollCycle(settings: XdripLanSettings, userSettings: UserSettings) {
        val ctx = appContext ?: return
        val glucoseRepo = cachedGlucoseRepo ?: return
        val db = cachedDb ?: return

        val now = System.currentTimeMillis()
        if (now - lastPollExecutionTime < 6_000L) {
            Log.d(TAG, "Skipping poll cycle, executed ${now - lastPollExecutionTime}ms ago (debounced)")
            return
        }
        lastPollExecutionTime = now

        try {
            var activeLan = settings
            if (activeLan.isAutoDiscovery && activeLan.masterHost.isBlank()) {
                val disc = XdripLanClient.discoverMaster(ctx, activeLan.port, activeLan.apiSecret)
                if (disc.isSuccess) {
                    val foundIp = disc.getOrNull()!!
                    activeLan = activeLan.copy(masterHost = foundIp)
                    cachedSettingsRepo?.updateSettings(userSettings.copy(xdripLanSettings = activeLan))
                    _statusFlow.value = _statusFlow.value.copy(masterIp = foundIp)
                } else {
                    _statusFlow.value = _statusFlow.value.copy(
                        state = LanConnectionState.ERROR,
                        errorMessage = "Мастер не найден в подсети"
                    )
                    return
                }
            }

            // 1. Fetch Pebble (battery, iob, cob)
            val pebbleResult = XdripLanClient.fetchPebble(activeLan).getOrNull()
            val masterBattery = pebbleResult?.battery
            val pebbleIob = pebbleResult?.iob
            val pebbleCob = pebbleResult?.cob

            // 2. Fetch SGV
            val sgvResult = XdripLanClient.fetchSgv(activeLan, count = 10)
            if (sgvResult.isSuccess) {
                val rawReadings = sgvResult.getOrNull() ?: emptyList()
                val readings = rawReadings.sortedByDescending { it.timestamp }
                if (readings.isNotEmpty()) {
                    // Enrich latest reading with IoB/CoB from pebble if available
                    val enrichedReadings = readings.mapIndexed { index, r ->
                        if (index == 0 && (pebbleIob != null || pebbleCob != null)) {
                            r.copy(
                                iob = pebbleIob ?: r.iob,
                                cob = pebbleCob ?: r.cob
                            )
                        } else r
                    }

                    glucoseRepo.insertReadingsBatchFromSource(
                        enrichedReadings,
                        DataSourcePriority.WIFI_LAN
                    )

                    val latest = enrichedReadings.first()
                    lastReadingTimestamp = latest.timestamp
                    val readingNow = System.currentTimeMillis()

                    // Update widgets, lockscreen notification and evaluate alerts if reading is fresh (< 15 min)
                    if (readingNow - latest.timestamp < 15 * 60_000L) {
                        GlucoseAlertManager.refreshLockscreenNotificationAndWidgets(ctx, userSettings, latest)
                        val recent = glucoseRepo.getRecentReadings(30).firstOrNull() ?: listOf(latest)
                        GlucoseAlertManager.checkAndAlert(
                            context = ctx,
                            recentReadings = recent,
                            settings = userSettings
                        )
                    }

                    _statusFlow.value = _statusFlow.value.copy(
                        state = LanConnectionState.CONNECTED,
                        masterIp = activeLan.masterHost,
                        masterBattery = masterBattery ?: _statusFlow.value.masterBattery,
                        lastSuccessTimestamp = readingNow,
                        lastGlucoseMmol = latest.valueMmol,
                        lastIob = pebbleIob ?: latest.iob,
                        lastCob = pebbleCob ?: latest.cob,
                        lastTrendArrow = latest.trendArrow,
                        errorMessage = null,
                        isWifiConnected = true
                    )
                }
            } else {
                val err = sgvResult.exceptionOrNull()?.message ?: "Ошибка получения SGV"
                _statusFlow.value = _statusFlow.value.copy(
                    state = LanConnectionState.ERROR,
                    errorMessage = err
                )

                // Auto-reconnect / rediscovery if host unreachable in auto-discovery mode
                if (activeLan.isAutoDiscovery) {
                    try {
                        val disc = XdripLanClient.discoverMaster(ctx, activeLan.port, activeLan.apiSecret)
                        if (disc.isSuccess && disc.getOrNull() != activeLan.masterHost) {
                            val newIp = disc.getOrNull()!!
                            Log.i(TAG, "Master IP changed to $newIp, updating...")
                            val updatedLan = activeLan.copy(masterHost = newIp)
                            cachedSettingsRepo?.updateSettings(userSettings.copy(xdripLanSettings = updatedLan))
                            _statusFlow.value = _statusFlow.value.copy(masterIp = newIp)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Periodic treatments fetch (every 3 cycles)
            treatmentsPollCounter++
            if (treatmentsPollCounter >= 3) {
                treatmentsPollCounter = 0
                try {
                    val treatmentsResult = XdripLanClient.fetchTreatments(activeLan, count = 25)
                    val treatments = treatmentsResult.getOrNull()
                    if (!treatments.isNullOrEmpty()) {
                        for (t in treatments) {
                            DexdripBroadcastReceiver.saveTreatmentIfNew(db, t)
                        }
                        DexdripBroadcastReceiver.purgeDuplicateTreatments(db)
                        DexdripBroadcastReceiver.processDeviceRenewals(ctx, treatments)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Treatments sync skipped: ${e.message}")
                }
            }
        } finally {
            val nextDelay = calculateNextPollIntervalMs(lastReadingTimestamp, settings.pollIntervalSeconds)
            scheduleNextPollAlarm(ctx, nextDelay)
        }
    }
}
