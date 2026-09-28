package com.tirup.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.local.AppDatabase
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
    private var treatmentsPollCounter = 0

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

    private fun startPollingInternal(settings: XdripLanSettings, userSettings: UserSettings) {
        if (pollJob?.isActive == true) return

        Log.i(TAG, "Starting xDrip LAN follower polling for host ${settings.masterHost}:${settings.port}")
        _statusFlow.value = _statusFlow.value.copy(
            state = LanConnectionState.CONNECTING,
            masterIp = settings.masterHost,
            errorMessage = null
        )

        pollJob = scope.launch {
            val intervalMs = (settings.pollIntervalSeconds.coerceIn(15, 300) * 1000L)
            while (true) {
                try {
                    val ctx = appContext ?: break
                    val isWifi = isWifiOrHotspotConnected(ctx)
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

                delay(intervalMs)
            }
        }
    }

    private fun stopPollingInternal() {
        pollJob?.cancel()
        pollJob = null
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
        val pebbleResult = XdripLanClient.fetchPebble(settings).getOrNull()
        val masterBattery = pebbleResult?.battery
        val pebbleIob = pebbleResult?.iob
        val pebbleCob = pebbleResult?.cob

        // 2. Fetch SGV
        val sgvResult = XdripLanClient.fetchSgv(settings, count = 10)
        if (sgvResult.isSuccess) {
            val readings = sgvResult.getOrNull() ?: emptyList()
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
                val now = System.currentTimeMillis()

                // Update widgets and evaluate alerts if reading is fresh (< 15 min)
                if (now - latest.timestamp < 15 * 60_000L) {
                    TirupWidgetUpdater.updateAllWidgets(ctx)
                    val recent = glucoseRepo.getRecentReadings(30).firstOrNull() ?: listOf(latest)
                    GlucoseAlertManager.checkAndAlert(
                        context = ctx,
                        recentReadings = recent,
                        settings = userSettings
                    )
                }

                _statusFlow.value = _statusFlow.value.copy(
                    state = LanConnectionState.CONNECTED,
                    masterIp = settings.masterHost,
                    masterBattery = masterBattery ?: _statusFlow.value.masterBattery,
                    lastSuccessTimestamp = now,
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
                val treatmentsResult = XdripLanClient.fetchTreatments(settings, count = 25)
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
    }
}
