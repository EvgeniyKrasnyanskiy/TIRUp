package com.tirup.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
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
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

object BleObserverManager {

    private const val TAG = "BleObserverManager"
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
    private var isBoostActive = false
    private var boostJob: Job? = null

    @Volatile
    private var lastRestartTimestampMs: Long = 0L

    @Volatile
    private var lastPacketReceivedSystemMs: Long = 0L

    @Volatile
    private var scanStartTimestampMs: Long = 0L

    private val _isScanningFlow = MutableStateFlow(false)
    val isScanningFlow: StateFlow<Boolean> = _isScanningFlow.asStateFlow()

    private val _boostRemainingSec = MutableStateFlow(0)
    val boostRemainingSec: StateFlow<Int> = _boostRemainingSec.asStateFlow()

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
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = isBoostActive)
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
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = isBoostActive)
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
    ) {
        appContext = context.applicationContext
        cachedSettingsRepo = settingsRepository
        cachedGlucoseRepo = glucoseRepository

        scope.launch {
            val userSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
            val ble = userSettings.bleBridgeSettings
            if (ble.role != BleBridgeRole.OBSERVER) return@launch

            boostJob?.cancel()
            isBoostActive = true
            _boostRemainingSec.value = 60

            // Restart scanner in low latency mode
            stopScanningInternal()
            startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = true)

            boostJob = launch {
                while (_boostRemainingSec.value > 0) {
                    delay(1000L)
                    _boostRemainingSec.value = (_boostRemainingSec.value - 1).coerceAtLeast(0)
                }
                isBoostActive = false
                // Revert to normal scan mode
                stopScanningInternal()
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = false)
            }
        }
    }

    /** Legacy alias for backwards compatibility */
    fun boostScanFor30Sec(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) = boostScanFor60Sec(context, settingsRepository, glucoseRepository)

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
            Log.w(TAG, "Silence watchdog triggered during keep-alive tick: ${elapsedSincePacket / 1000}s since last packet. Restarting scan with boost.")
            restartScanInternal("alarm_silence_timeout", boost = true)
        } else if (isAospLimitApproaching) {
            Log.i(TAG, "Proactive reset triggered during keep-alive tick: scan age is ${elapsedSinceStart / 60000} min (AOSP limit 30 min). Refreshing scan with boost.")
            restartScanInternal("alarm_proactive_reset", boost = true)
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
     * Public unified entry point for scan restarts (called by BleScanKeepAliveReceiver or silence detector).
     * Protected by Mutex and anti-spam cooldown to guarantee race-free execution.
     */
    fun restartScan(reason: String, boost: Boolean = false) {
        scope.launch {
            restartScanInternal(reason, boost = boost)
        }
    }

    private suspend fun restartScanInternal(reason: String, boost: Boolean = false) = mutex.withLock {
        val now = System.currentTimeMillis()
        val elapsedSinceLastRestart = now - lastRestartTimestampMs
        if (lastRestartTimestampMs > 0L && elapsedSinceLastRestart < MIN_RESTART_COOLDOWN_MS) {
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
                activeCallback?.let { cb -> scanner?.stopScan(cb) }
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
                    mutex.withLock {
                        if (isScanning && !isBoostActive) {
                            Log.i(TAG, "Reverting scan mode from boost (LOW_LATENCY) to BALANCED")
                            try {
                                activeCallback?.let { cb -> scanner?.stopScan(cb) }
                            } catch (_: Exception) {}
                            activeCallback = null
                            scanner = null
                            isScanning = false
                            delay(800L)
                            startScanningInternalLocked(context, ble.familyPin, settingsRepo, glucoseRepo, boost = false)
                        }
                    }
                }
            }

            // 4. Start completely fresh scan with a NEW ScanCallback instance
            startScanningInternalLocked(context, ble.familyPin, settingsRepo, glucoseRepo, boost = isBoostActive || boost)
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
        boost: Boolean
    ) = mutex.withLock {
        startScanningInternalLocked(context, familyPin, settingsRepository, glucoseRepository, boost)
    }

    private fun startScanningInternalLocked(
        context: Context,
        familyPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        boost: Boolean
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

        val scanSettings = ScanSettings.Builder()
            .setScanMode(if (boost) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0L)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
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

                if (errorCode == SCAN_FAILED_ALREADY_STARTED ||
                    errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ||
                    errorCode == SCAN_FAILED_INTERNAL_ERROR
                ) {
                    scope.launch {
                        delay(2500L)
                        restartScanInternal("on_scan_failed_recovery", boost = true)
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
            val now = System.currentTimeMillis()
            scanStartTimestampMs = now
            if (lastPacketReceivedSystemMs == 0L) {
                lastPacketReceivedSystemMs = now
            }
            Log.i(TAG, "BLE Observer started scanning successfully (boost=$boost)")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting scan: ${e.message}")
            _isScanningFlow.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}")
            _isScanningFlow.value = false
        }
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

        val rssi = result.rssi
        val now = System.currentTimeMillis()
        lastPacketReceivedSystemMs = now

        // Check if this is a repeat packet from the same burst or a fallback heartbeat with the same reading timestamp
        if (packet.timestamp <= lastHandledTimestamp) {
            // Heartbeat or repeat packet within burst: keeps watchdog alive without cluttering Room DB
            if (now - lastHeartbeatLogMs >= 3000L) {
                lastHeartbeatLogMs = now
                Log.i(TAG, "Heartbeat received: ts=${packet.timestamp}, bg=${packet.valueMmol}, rssi=$rssi (duplicate, connection alive)")
                _packetReceivedEvent.tryEmit(Pair(packet, rssi))
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

                // Insert into Room DB (ignoring conflicts if already present)
                val newReading = GlucoseReading(
                    timestamp = packet.timestamp,
                    valueMmol = packet.valueMmol,
                    trendArrow = packet.trendArrow,
                    iob = if (packet.iob > 0.0) packet.iob else null,
                    cob = if (packet.cob > 0.0) packet.cob else null
                )
                glucoseRepository.insertReading(newReading)

                // Update widgets and clinical evaluations
                TirupWidgetUpdater.updateAllWidgets(context)

                val recent = glucoseRepository.getRecentReadings(30).firstOrNull() ?: listOf(newReading)

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
        if (!isScanning) return@withLock
        try {
            activeCallback?.let { cb ->
                scanner?.stopScan(cb)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        activeCallback = null
        scanner = null
        isScanning = false
        _isScanningFlow.value = false
        Log.i(TAG, "BLE Observer stopped scanning")
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
