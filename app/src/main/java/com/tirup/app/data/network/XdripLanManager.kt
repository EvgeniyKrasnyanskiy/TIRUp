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
                    if (lan.isEnabled && lan.isValidHost) {
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
        if (!lan.isEnabled || !lan.isValidHost) return@withContext
        executePollCycle(lan, settings)
    }

    private suspend fun executePollCycle(settings: XdripLanSettings, userSettings: UserSettings) {
        val ctx = appContext ?: return
        val glucoseRepo = cachedGlucoseRepo ?: return
        val db = cachedDb ?: return

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
