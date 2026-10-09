package com.tirup.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.TirupApplication
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BleGlucosePacket
import com.tirup.app.domain.model.DataSourcePriority
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

data class BleRadioChannelMetrics(
    val pdrPercent: Int = 100,
    val avgRssi: Int = 0,
    val receivedCountLastHour: Int = 0,
    val expectedCountLastHour: Int = 60,
    val isEcoMode: Boolean = false
)

object BleObserverManager {

    private const val TAG = "BleObserverManager"
    const val ECO_SILENCE_TIMEOUT_MS = 3_600_000L // 1 hour (60 minutes without transmitter packet)
    private const val MIN_RESTART_COOLDOWN_MS = 60_000L // anti-spam cooldown (protects against max 5 starts / 30s AOSP limit)
    private const val SILENCE_TIMEOUT_MS = 6 * 60 * 1000L // 6 minutes without packet triggers reactive restart
    private const val PROACTIVE_RESET_INTERVAL_MS = 27 * 60 * 1000L // 27 minutes continuous scan triggers proactive AOSP 30-min limit reset

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var cachedSettingsRepo: SettingsRepository? = null
    private var cachedGlucoseRepo: GlucoseRepository? = null

    private var scanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private var isScanning = false
    @Volatile
    private var isBoostActive = false
    private var boostJob: Job? = null
    private var ecoMonitorJob: Job? = null

    @Volatile
    private var lastRestartTimestampMs: Long = 0L

    @Volatile
    private var lastPacketReceivedSystemMs: Long = 0L

    @Volatile
    private var scanStartTimestampMs: Long = 0L

    @Volatile
    var extendedScanDisabledByFallback: Boolean = false
        private set

    private val _isScanningFlow = MutableStateFlow(false)
    val isScanningFlow: StateFlow<Boolean> = _isScanningFlow.asStateFlow()

    private val _isLongRangeScanActive = MutableStateFlow(false)
    val isLongRangeScanActive: StateFlow<Boolean> = _isLongRangeScanActive.asStateFlow()

    private val _lastPacketWasLongRange = MutableStateFlow(false)
    val lastPacketWasLongRange: StateFlow<Boolean> = _lastPacketWasLongRange.asStateFlow()

    private val _boostRemainingSec = MutableStateFlow(0)
    val boostRemainingSec: StateFlow<Int> = _boostRemainingSec.asStateFlow()

    private val _isEcoModeFlow = MutableStateFlow(false)
    val isEcoModeFlow: StateFlow<Boolean> = _isEcoModeFlow.asStateFlow()

    private val _channelMetricsFlow = MutableStateFlow(BleRadioChannelMetrics())
    val channelMetricsFlow: StateFlow<BleRadioChannelMetrics> = _channelMetricsFlow.asStateFlow()

    data class ChannelPacketRecord(
        val arrivalMs: Long,
        val readingTimestampMs: Long,
        val rssi: Int
    )

    private val packetRecords1h = java.util.Collections.synchronizedList(mutableListOf<ChannelPacketRecord>())

    @Volatile
    var detectedCadenceIntervalMs: Long = 60_000L // default: 1-minute cadence (60 readings/hr)
        private set

    private val _packetReceivedEvent = MutableSharedFlow<Pair<BleGlucosePacket, Int>>(extraBufferCapacity = 5)
    val packetReceivedEvent: SharedFlow<Pair<BleGlucosePacket, Int>> = _packetReceivedEvent.asSharedFlow()

    @Volatile
    private var lastHandledTimestamp: Long = 0L

    @Volatile
    private var lastHeartbeatLogMs: Long = 0L

    @Volatile
    var isServiceRunning: Boolean = false

    /**
     * Synchronizes the scanner state with the user settings.
     * Starts scanning if role == OBSERVER, stops otherwise.
     */
    fun syncWithSettings(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        cachedGlucoseRepo = glucoseRepository
        extendedScanDisabledByFallback = false

        scope.launch {
            val userSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
            val ble = userSettings.bleBridgeSettings

            if (ble.isEnabled && ble.role == BleBridgeRole.OBSERVER) {
                if (!isServiceRunning) {
                    try {
                        val serviceIntent = Intent(context, BleObserverService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to start BleObserverService: ${e.message}")
                    }
                }
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = isBoostActive, useLongRange = ble.useLongRange)
            } else {
                if (isServiceRunning) {
                    try {
                        context.stopService(Intent(context, BleObserverService::class.java))
                    } catch (_: Exception) {}
                    isServiceRunning = false
                }
                stopScanningInternal()
            }
        }
    }

    /**
     * Called directly by BleObserverService onStartCommand to ensure scanning is active
     * without triggering startForegroundService recursion.
     */
    fun startScanningFromService(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        cachedGlucoseRepo = glucoseRepository

        scope.launch {
            val userSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
            val ble = userSettings.bleBridgeSettings
            if (ble.isEnabled && ble.role == BleBridgeRole.OBSERVER) {
                // Ensure any previous dead or stale scan registration is cleared before starting fresh
                stopScanningInternal()
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = isBoostActive, useLongRange = ble.useLongRange)
            } else {
                try {
                    context.stopService(Intent(context, BleObserverService::class.java))
                } catch (_: Exception) {}
                isServiceRunning = false
                stopScanningInternal()
            }
        }
    }

    /**
     * Boosts scanning to SCAN_MODE_LOW_LATENCY for 60 seconds to quickly detect the master.
     */
    fun boostScanFor60Sec(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) = boostScanForDuration(context, settingsRepository, glucoseRepository, 60)

    /** Legacy alias for backwards compatibility */
    fun boostScanFor30Sec(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) = boostScanForDuration(context, settingsRepository, glucoseRepository, 30)

    /**
     * Boosts scan for a custom duration (e.g. 10s range test).
     */
    fun boostScanForDuration(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        durationSec: Int = 10
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        cachedGlucoseRepo = glucoseRepository

        scope.launch {
            val userSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
            val ble = userSettings.bleBridgeSettings

            mutex.withLock {
                boostJob?.cancel()
                isBoostActive = true
                _boostRemainingSec.value = durationSec

                // Restart scanner in low latency mode
                stopScanningInternalLocked()
                startScanningInternalLocked(context, ble.familyPin, settingsRepository, glucoseRepository, boost = true, useLongRange = ble.useLongRange)

                boostJob = launch {
                    while (_boostRemainingSec.value > 0) {
                        delay(1000L)
                        _boostRemainingSec.value = (_boostRemainingSec.value - 1).coerceAtLeast(0)
                    }
                    mutex.withLock {
                        if (isBoostActive) {
                            isBoostActive = false
                            // Revert to normal scan mode if observer, else stop
                            stopScanningInternalLocked()
                            if (ble.role == BleBridgeRole.OBSERVER) {
                                startScanningInternalLocked(context, ble.familyPin, settingsRepository, glucoseRepository, boost = false, useLongRange = ble.useLongRange)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Immediately cancels active boost search upon master packet arrival or manual cancel.
     */
    fun cancelBoost() {
        if (!isBoostActive && _boostRemainingSec.value <= 0) return
        Log.i(TAG, "Cancelling active boost search immediately (packet arrived or reset)")
        boostJob?.cancel()
        boostJob = null
        isBoostActive = false
        _boostRemainingSec.value = 0
    }

    /**
     * Periodic hardware heartbeat invoked by BleScanKeepAliveReceiver (every 5 minutes via AlarmManager).
     * Single source of truth for both:
     * 1) Reactive silence detection (restarts scan if no packets received for >= 6 minutes).
     * 2) Proactive AOSP demotion avoidance (restarts scan if running continuously for >= 20 minutes).
     *
     * Executed inside BroadcastReceiver's goAsync() block, guaranteeing CPU WakeLock remains held
     * during the entire stop -> delay(350ms) -> startScan sequence.
     */
    suspend fun onKeepAliveTickSuspend() {
        if (!isServiceRunning && !isScanning) return

        val now = System.currentTimeMillis()
        if (!isScanning) {
            Log.w(TAG, "Keep-alive tick: Scanner is unexpectedly stopped. Restarting.")
            restartScanInternal("alarm_scanner_dead")
            return
        }

        val elapsedSincePacket = if (lastPacketReceivedSystemMs > 0L) now - lastPacketReceivedSystemMs else 0L
        val elapsedSinceStart = if (scanStartTimestampMs > 0L) now - scanStartTimestampMs else 0L

        val isPacketSilence = lastPacketReceivedSystemMs > 0L && elapsedSincePacket >= SILENCE_TIMEOUT_MS
        val isAospLimitApproaching = scanStartTimestampMs > 0L && elapsedSinceStart >= PROACTIVE_RESET_INTERVAL_MS

        if (isPacketSilence) {
            Log.w(TAG, "Silence watchdog triggered during keep-alive tick: ${elapsedSincePacket / 1000}s since last packet. Restarting scan directly in BALANCED mode.")
            restartScanInternal("alarm_silence_timeout", boost = false)
        } else if (isAospLimitApproaching) {
            Log.i(TAG, "Proactive reset triggered during keep-alive tick: scan age is ${elapsedSinceStart / 60000} min (AOSP limit 30 min). Refreshing scan directly in BALANCED mode.")
            restartScanInternal("alarm_proactive_reset", boost = false)
        } else {
            Log.i(TAG, "Keep-alive tick healthy: scanAge=${elapsedSinceStart / 60000}m, packetSilence=${elapsedSincePacket / 1000}s, lastPacketReceivedMs=$lastPacketReceivedSystemMs")
        }
    }

    /** Non-suspending convenience wrapper */
    fun onKeepAliveTick() {
        scope.launch {
            onKeepAliveTickSuspend()
        }
    }

    /**
     * Invoked when user turns on the screen (ACTION_SCREEN_ON).
     * If device was sleeping and no packets arrived for >= 75 seconds, activates a 60-second
     * low-latency scan boost to immediately clear any dormant Bluetooth controller state and
     * capture the next transmitter burst without delay.
     */
    fun onScreenTurnedOn(context: Context) {
        if (!isServiceRunning && !isScanning) return
        val now = System.currentTimeMillis()
        val silenceMs = if (lastPacketReceivedSystemMs > 0L) now - lastPacketReceivedSystemMs else 0L
        Log.d(TAG, "Screen turned on. Silence: ${silenceMs / 1000}s")
        if (silenceMs >= 75_000L) {
            val settingsRepo = cachedSettingsRepo ?: (context.applicationContext as? TirupApplication)?.settingsRepository ?: return
            val glucoseRepo = cachedGlucoseRepo ?: (context.applicationContext as? TirupApplication)?.glucoseRepository ?: return
            Log.i(TAG, "Screen unlocked after ${silenceMs / 1000}s silence. Activating 60s boost scan to catch next transmission immediately.")
            boostScanFor60Sec(context, settingsRepo, glucoseRepo)
        }
    }

    /**
     * Public unified entry point for scan restarts (called by BleScanKeepAliveReceiver or silence detector).
     * Protected by Mutex and anti-spam cooldown to guarantee race-free execution.
     */
    fun restartScan(reason: String, boost: Boolean = false) {
        scope.launch {
            restartScanInternal(reason, boost = boost)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun restartScanInternal(reason: String, boost: Boolean = false) = mutex.withLock {
        val now = System.currentTimeMillis()
        val elapsedSinceLastRestart = now - lastRestartTimestampMs
        val isUrgent = boost || reason.startsWith("self_healing") || reason.startsWith("eco_mode")
        if (!isUrgent && lastRestartTimestampMs > 0L && elapsedSinceLastRestart < MIN_RESTART_COOLDOWN_MS) {
            Log.d(TAG, "Restart suppressed by cooldown ($reason, elapsed=${elapsedSinceLastRestart}ms < ${MIN_RESTART_COOLDOWN_MS}ms)")
            return@withLock
        }
        if (!isServiceRunning && !isScanning) {
            Log.d(TAG, "Observer is not active, skipping restart ($reason)")
            return@withLock
        }

        val context = appContext ?: TirupApplication.instance
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val restartWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleRestartWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        try {
            lastRestartTimestampMs = now
            Log.i(TAG, "Executing clean BLE scan restart: reason='$reason', boost=$boost")

            // 1. Stop current scan and discard old callback object
            try {
                if (hasScanPermission(context)) {
                    activeCallback?.let { cb -> scanner?.stopScan(cb) }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException stopping scan during restart: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan during restart: ${e.message}")
            }
            activeCallback = null
            scanner = null
            isScanning = false

            // 2. Pause 800ms to allow system Bluetooth stack IPC to fully clear the client registration
            delay(800L)

            // 3. Resolve context and repositories
            val settingsRepo = cachedSettingsRepo ?: (context as? TirupApplication)?.settingsRepository ?: return@withLock
            val glucoseRepo = cachedGlucoseRepo ?: (context as? TirupApplication)?.glucoseRepository ?: return@withLock
            val userSettings = settingsRepo.getSettings().firstOrNull() ?: return@withLock
            val ble = userSettings.bleBridgeSettings
            if (!ble.isEnabled || ble.role != BleBridgeRole.OBSERVER) return@withLock

            // If boost is requested, run 60 seconds of LOW_LATENCY then gracefully revert to BALANCED
            if (boost) {
                boostJob?.cancel()
                isBoostActive = true
                _boostRemainingSec.value = 60
                boostJob = scope.launch {
                    while (_boostRemainingSec.value > 0) {
                        delay(1000L)
                        _boostRemainingSec.value = (_boostRemainingSec.value - 1).coerceAtLeast(0)
                    }
                    isBoostActive = false
                    val revertWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleBoostRevertWakeLock")?.apply {
                        setReferenceCounted(false)
                        acquire(15_000L)
                    }
                    try {
                        mutex.withLock {
                            if (isScanning && !isBoostActive) {
                                Log.i(TAG, "Reverting scan mode from boost (LOW_LATENCY) to BALANCED")
                                try {
                                    if (hasScanPermission(context)) {
                                        activeCallback?.let { cb -> scanner?.stopScan(cb) }
                                    }
                                } catch (_: SecurityException) {
                                } catch (_: Exception) {}
                                activeCallback = null
                                scanner = null
                                isScanning = false
                                delay(800L)
                                startScanningInternalLocked(context, ble.familyPin, settingsRepo, glucoseRepo, boost = false, useLongRange = ble.useLongRange)
                            }
                        }
                    } finally {
                        try {
                            if (revertWakeLock?.isHeld == true) {
                                revertWakeLock.release()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // 4. Start completely fresh scan with a NEW ScanCallback instance
            startScanningInternalLocked(context, ble.familyPin, settingsRepo, glucoseRepo, boost = isBoostActive || boost, useLongRange = ble.useLongRange)
        } finally {
            try {
                if (restartWakeLock?.isHeld == true) {
                    restartWakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun startScanningInternal(
        context: Context,
        familyPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        boost: Boolean,
        useLongRange: Boolean = false
    ) = mutex.withLock {
        startScanningInternalLocked(context, familyPin, settingsRepository, glucoseRepository, boost, useLongRange)
    }

    private fun startScanningInternalLocked(
        context: Context,
        familyPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        boost: Boolean,
        useLongRange: Boolean = false
    ) {
        if (isScanning) return

        if (!hasScanPermission(context)) {
            Log.w(TAG, "Cannot start BLE scanner: scan permission not granted")
            _isScanningFlow.value = false
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled, cannot start BLE Observer")
            _isScanningFlow.value = false
            return
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            _isScanningFlow.value = false
            return
        }

        // Manufacturer ScanFilter with exact 'TU' magic header ensures hardware filtering keeps scanning alive while screen is off (Android 8+)
        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(
                BlePacketCodec.MANUFACTURER_ID,
                byteArrayOf(0x54, 0x55), // magic "TU"
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // exact match mask
            )
            .build()

        val canAttemptExtended = useLongRange && !extendedScanDisabledByFallback && adapter.isLeExtendedAdvertisingSupported
        val scanMode = when {
            boost -> ScanSettings.SCAN_MODE_LOW_LATENCY
            _isEcoModeFlow.value -> ScanSettings.SCAN_MODE_LOW_POWER
            isServiceRunning -> ScanSettings.SCAN_MODE_LOW_LATENCY
            else -> ScanSettings.SCAN_MODE_BALANCED
        }
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0L)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                }
                if (canAttemptExtended) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLegacy(true)
                }
            }
            .build()

        // Guarantee a completely fresh instance of ScanCallback on each start
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                handleScanResult(context, result, familyPin, settingsRepository, glucoseRepository)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { res ->
                    handleScanResult(context, res, familyPin, settingsRepository, glucoseRepository)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorDesc = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED (1)"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (2)"
                    SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR (3)"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED (4)"
                    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES (5)"
                    else -> "SCAN_FAILED_UNKNOWN ($errorCode)"
                }
                Log.e(TAG, "BLE Scan failed: $errorDesc")
                _isScanningFlow.value = false
                _isLongRangeScanActive.value = false

                if (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED && !extendedScanDisabledByFallback) {
                    Log.w(TAG, "Extended scan unsupported by driver, falling back to legacy 1M")
                    extendedScanDisabledByFallback = true
                    scope.launch {
                        delay(1000L)
                        restartScanInternal("fallback_legacy_after_feature_unsupported", boost = false)
                    }
                    return
                }

                if (errorCode == SCAN_FAILED_ALREADY_STARTED ||
                    errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ||
                    errorCode == SCAN_FAILED_INTERNAL_ERROR
                ) {
                    scope.launch {
                        delay(2500L)
                        restartScanInternal("on_scan_failed_recovery", boost = false)
                    }
                }
            }
        }

        try {
            leScanner.startScan(listOf(scanFilter), scanSettings, callback)
            scanner = leScanner
            activeCallback = callback
            isScanning = true
            _isScanningFlow.value = true
            _isLongRangeScanActive.value = canAttemptExtended
            val now = System.currentTimeMillis()
            scanStartTimestampMs = now
            if (lastPacketReceivedSystemMs == 0L) {
                lastPacketReceivedSystemMs = now
            }
            startEcoMonitorLocked()
            Log.i(TAG, "BLE Observer started scanning successfully (mode=$scanMode, boost=$boost, eco=${_isEcoModeFlow.value})")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting scan: ${e.message}")
            _isScanningFlow.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}")
            _isScanningFlow.value = false
        }
    }

    private fun startEcoMonitorLocked() {
        ecoMonitorJob?.cancel()
        ecoMonitorJob = scope.launch {
            while (isActive) {
                delay(30_000L) // check every 30s
                if (!isScanning) continue
                val now = System.currentTimeMillis()
                val silenceMs = if (lastPacketReceivedSystemMs > 0L) now - lastPacketReceivedSystemMs else (now - scanStartTimestampMs)
                val currentSettings = cachedSettingsRepo?.getSettings()?.firstOrNull()
                val isEcoConfigured = currentSettings?.bleBridgeSettings?.enableEcoMode == true

                if (isEcoConfigured && silenceMs >= ECO_SILENCE_TIMEOUT_MS && !_isEcoModeFlow.value && !isBoostActive) {
                    Log.i(TAG, "Silence of ${silenceMs / 1000}s detected (>= 1 hour). Switching to ECO power-saving mode (SCAN_MODE_LOW_POWER).")
                    _isEcoModeFlow.value = true
                    _channelMetricsFlow.value = _channelMetricsFlow.value.copy(isEcoMode = true)
                    restartScanInternal("eco_mode_silence", boost = false)
                } else if (!isEcoConfigured && _isEcoModeFlow.value) {
                    Log.i(TAG, "ECO mode disabled in settings. Instantly restoring LOW_LATENCY.")
                    _isEcoModeFlow.value = false
                    _channelMetricsFlow.value = _channelMetricsFlow.value.copy(isEcoMode = false)
                    restartScanInternal("eco_mode_disabled_by_settings", boost = false)
                }
                // Recalculate metrics periodically so PDR degrades naturally during signal loss
                _channelMetricsFlow.value = calculateMetrics(now)
            }
        }
    }

    fun calculateMetrics(now: Long = System.currentTimeMillis()): BleRadioChannelMetrics {
        synchronized(packetRecords1h) {
            val cutoff = now - 3600_000L
            packetRecords1h.removeAll { it.arrivalMs < cutoff }

            if (packetRecords1h.isEmpty()) {
                val expected = calculateExpectedCount(now, detectedCadenceIntervalMs, 0)
                return BleRadioChannelMetrics(
                    pdrPercent = 100,
                    avgRssi = 0,
                    receivedCountLastHour = 0,
                    expectedCountLastHour = expected,
                    isEcoMode = _isEcoModeFlow.value
                )
            }

            // Extract unique valid sensor reading timestamps
            val validReadingTimestamps = packetRecords1h
                .filter { it.readingTimestampMs > 0L }
                .map { it.readingTimestampMs }
                .distinct()
                .sorted()

            // If we have at least 2 distinct readings, determine cadence dynamically
            if (validReadingTimestamps.size >= 2) {
                val deltas = mutableListOf<Long>()
                for (i in 1 until validReadingTimestamps.size) {
                    val dt = validReadingTimestamps[i] - validReadingTimestamps[i - 1]
                    if (dt in 30_000L..600_000L) {
                        deltas.add(dt)
                    }
                }
                if (deltas.isNotEmpty()) {
                    val minDelta = deltas.minOrNull() ?: 60_000L
                    val medianDelta = deltas.sorted()[deltas.size / 2]
                    detectedCadenceIntervalMs = when {
                        minDelta <= 90_000L || medianDelta <= 120_000L -> 60_000L // 1-minute sensor (Libre 2/3, Dexcom G7, Juggluco)
                        medianDelta in 120_001L..210_000L -> 180_000L             // 2-3 minute sensor
                        else -> 300_000L                                          // 5-minute sensor (Dexcom G6)
                    }
                }
            }

            val receivedCount = if (validReadingTimestamps.isNotEmpty()) {
                validReadingTimestamps.size
            } else {
                // For test pings or packets without unique timestamps, count 1-minute arrival buckets
                packetRecords1h.map { it.arrivalMs / 60_000L }.distinct().size
            }

            val expectedCount = calculateExpectedCount(now, detectedCadenceIntervalMs, receivedCount)
            val pdr = if (expectedCount > 0) {
                ((receivedCount.toDouble() / expectedCount.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
            } else 100

            val avgRssi = packetRecords1h.map { it.rssi }.average().toInt()

            return BleRadioChannelMetrics(
                pdrPercent = pdr,
                avgRssi = avgRssi,
                receivedCountLastHour = receivedCount,
                expectedCountLastHour = expectedCount,
                isEcoMode = _isEcoModeFlow.value
            )
        }
    }

    private fun calculateExpectedCount(now: Long, intervalMs: Long, receivedCount: Int): Int {
        val safeInterval = intervalMs.coerceAtLeast(30_000L)
        val maxPerHour = (3600_000L / safeInterval).toInt().coerceAtLeast(1)

        val activeDurationMs = if (scanStartTimestampMs > 0L) {
            (now - scanStartTimestampMs).coerceIn(0L, 3600_000L)
        } else {
            3600_000L
        }

        val expectedInSpan = (activeDurationMs / safeInterval).toInt().coerceAtLeast(1)
        val clampedExpected = expectedInSpan.coerceAtMost(maxPerHour)

        return maxOf(clampedExpected, receivedCount)
    }

    private fun recordChannelPacket(now: Long, readingTimestampMs: Long, rssi: Int) {
        synchronized(packetRecords1h) {
            packetRecords1h.add(ChannelPacketRecord(arrivalMs = now, readingTimestampMs = readingTimestampMs, rssi = rssi))
        }
        _channelMetricsFlow.value = calculateMetrics(now)
    }

    internal fun addPacketRecordForTest(arrivalMs: Long, readingTimestampMs: Long, rssi: Int) {
        synchronized(packetRecords1h) {
            packetRecords1h.add(ChannelPacketRecord(arrivalMs = arrivalMs, readingTimestampMs = readingTimestampMs, rssi = rssi))
        }
        _channelMetricsFlow.value = calculateMetrics(arrivalMs)
    }

    internal fun clearRecordsForTest(startTimestampMs: Long = 0L, cadenceMs: Long = 60_000L) {
        synchronized(packetRecords1h) {
            packetRecords1h.clear()
        }
        scanStartTimestampMs = startTimestampMs
        detectedCadenceIntervalMs = cadenceMs
        _channelMetricsFlow.value = calculateMetrics(if (startTimestampMs > 0L) startTimestampMs else System.currentTimeMillis())
    }

    private fun handleScanResult(
        context: Context,
        result: ScanResult,
        expectedPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) {
        val record = result.scanRecord ?: return
        val rawData = record.getManufacturerSpecificData(BlePacketCodec.MANUFACTURER_ID) ?: return

        val packet = BlePacketCodec.decodePacket(rawData, expectedPin) ?: return

        val isCodedPhy = result.primaryPhy == BluetoothDevice.PHY_LE_CODED
        _lastPacketWasLongRange.value = isCodedPhy

        val rssi = result.rssi
        val now = System.currentTimeMillis()
        lastPacketReceivedSystemMs = now

        recordChannelPacket(now, packet.timestamp, rssi)

        // Cancel active boost search immediately on master packet arrival
        cancelBoost()

        // Self-Healing: if we were in ECO power-saving mode, instantly restore LOW_LATENCY
        if (_isEcoModeFlow.value) {
            Log.i(TAG, "Self-Healing: Master packet detected while in ECO mode! Instantly restoring LOW_LATENCY.")
            _isEcoModeFlow.value = false
            _channelMetricsFlow.value = _channelMetricsFlow.value.copy(isEcoMode = false)
            scope.launch {
                restartScanInternal("self_healing_master_detected", boost = true)
            }
        }

        // Check if this is a repeat packet from the same burst or a fallback heartbeat with the same reading timestamp
        if (packet.timestamp == 0L || packet.valueMmol <= 0.1) {
            // Pure range test ping without real sensor reading: update radio contact and emit event without inserting into Room DB
            Log.i(TAG, "Test ping packet received: ts=${packet.timestamp}, rssi=$rssi (no sensor reading)")
            _packetReceivedEvent.tryEmit(Pair(packet, rssi))
            scope.launch {
                try {
                    val currentSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
                    val updatedBle = currentSettings.bleBridgeSettings.copy(
                        lastRadioContactMs = now,
                        lastRssi = rssi,
                        lastMasterBattery = if (packet.batteryPercent in 0..100) packet.batteryPercent else currentSettings.bleBridgeSettings.lastMasterBattery
                    )
                    settingsRepository.updateSettings(currentSettings.copy(bleBridgeSettings = updatedBle))
                } catch (_: Exception) {}
            }
            return
        }

        if (packet.timestamp <= lastHandledTimestamp) {
            // Heartbeat or repeat packet within burst: keeps watchdog alive without cluttering Room DB
            if (now - lastHeartbeatLogMs >= 3000L) {
                lastHeartbeatLogMs = now
                Log.i(TAG, "Heartbeat received: ts=${packet.timestamp}, bg=${packet.valueMmol}, rssi=$rssi (duplicate, connection alive)")
            }
            scope.launch {
                try {
                    val currentSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
                    val updatedBle = currentSettings.bleBridgeSettings.copy(
                        lastRadioContactMs = now,
                        lastRssi = rssi,
                        lastMasterBattery = if (packet.batteryPercent in 0..100) packet.batteryPercent else currentSettings.bleBridgeSettings.lastMasterBattery
                    )
                    settingsRepository.updateSettings(currentSettings.copy(bleBridgeSettings = updatedBle))
                } catch (_: Exception) {}
            }
            return
        }

        lastHandledTimestamp = packet.timestamp
        Log.i(TAG, "Received valid BLE glucose packet: ts=${packet.timestamp}, bg=${packet.valueMmol}, rssi=$rssi, bat=${packet.batteryPercent}%")
        _packetReceivedEvent.tryEmit(Pair(packet, rssi))

        scope.launch {
            try {
                // Update diagnostic info in settings
                val currentSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
                val updatedBle = currentSettings.bleBridgeSettings.copy(
                    lastPacketTimestamp = packet.timestamp,
                    lastRadioContactMs = now,
                    lastRssi = rssi,
                    lastMasterBattery = packet.batteryPercent
                )
                settingsRepository.updateSettings(currentSettings.copy(bleBridgeSettings = updatedBle))

                // Insert into Room DB with explicit BLE_BRIDGE priority for robust deduplication
                val newReading = GlucoseReading(
                    timestamp = packet.timestamp,
                    valueMmol = packet.valueMmol,
                    trendArrow = packet.trendArrow,
                    iob = if (packet.iob > 0.0) packet.iob else null,
                    cob = if (packet.cob > 0.0) packet.cob else null
                )
                glucoseRepository.insertReadingFromSource(newReading, DataSourcePriority.BLE_BRIDGE)

                // Update widgets, lockscreen notification and clinical evaluations
                GlucoseAlertManager.refreshLockscreenNotificationAndWidgets(context, currentSettings, newReading)

                val recent = glucoseRepository.getRecentReadings(30).firstOrNull() ?: listOf(newReading)
                if (recent.size >= 2) {
                    val dt = Math.abs(recent[0].timestamp - recent[1].timestamp)
                    if (dt in 30_000L..150_000L) {
                        detectedCadenceIntervalMs = 60_000L
                    } else if (dt > 150_000L && dt <= 360_000L) {
                        detectedCadenceIntervalMs = 300_000L
                    }
                }

                GlucoseAlertManager.checkAndAlert(
                    context = context,
                    recentReadings = recent,
                    settings = currentSettings
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process BLE packet: ${e.message}")
            }
        }
    }

    suspend fun stopScanning() = stopScanningInternal()

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun stopScanningInternal() = mutex.withLock {
        stopScanningInternalLocked()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopScanningInternalLocked() {
        if (!isScanning) return
        try {
            activeCallback?.let { cb ->
                scanner?.stopScan(cb)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        ecoMonitorJob?.cancel()
        ecoMonitorJob = null
        _isEcoModeFlow.value = false
        _channelMetricsFlow.value = _channelMetricsFlow.value.copy(isEcoMode = false)
        packetRecords1h.clear()
        scanStartTimestampMs = 0L
        activeCallback = null
        scanner = null
        isScanning = false
        _isScanningFlow.value = false
        _isLongRangeScanActive.value = false
        Log.i(TAG, "BLE Observer stopped scanning")
    }

    fun isLongRangeScanSupported(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bm.adapter ?: return false
        return isLongRangeScanSupported(adapter)
    }

    fun isLongRangeScanSupported(adapter: BluetoothAdapter): Boolean {
        return try {
            adapter.isLeExtendedAdvertisingSupported && !extendedScanDisabledByFallback
        } catch (_: Exception) {
            false
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return bm.adapter?.isEnabled == true
    }

    fun hasScanPermission(context: Context): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            hasScan && hasLocation
        } else {
            hasLocation
        }
    }
}
