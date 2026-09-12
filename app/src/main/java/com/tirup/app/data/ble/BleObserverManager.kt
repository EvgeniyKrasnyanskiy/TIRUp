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
import android.content.pm.PackageManager
import android.os.Build
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    private var scanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private var isScanning = false
    private var isBoostActive = false
    private var boostJob: Job? = null

    private val _isScanningFlow = MutableStateFlow(false)
    val isScanningFlow: StateFlow<Boolean> = _isScanningFlow.asStateFlow()

    private val _boostRemainingSec = MutableStateFlow(0)
    val boostRemainingSec: StateFlow<Int> = _boostRemainingSec.asStateFlow()

    private val _packetReceivedEvent = MutableSharedFlow<Pair<BleGlucosePacket, Int>>(extraBufferCapacity = 5)
    val packetReceivedEvent: SharedFlow<Pair<BleGlucosePacket, Int>> = _packetReceivedEvent.asSharedFlow()

    @Volatile
    private var lastHandledTimestamp: Long = 0L

    /**
     * Synchronizes the scanner state with the user settings.
     * Starts scanning if role == OBSERVER, stops otherwise.
     */
    fun syncWithSettings(
        context: Context,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) {
        scope.launch {
            val userSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
            val ble = userSettings.bleBridgeSettings

            if (ble.isEnabled && ble.role == BleBridgeRole.OBSERVER) {
                startScanningInternal(context, ble.familyPin, settingsRepository, glucoseRepository, boost = isBoostActive)
            } else {
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
                // Revert to BALANCED scan mode
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

    private suspend fun startScanningInternal(
        context: Context,
        familyPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository,
        boost: Boolean
    ) = mutex.withLock {
        if (isScanning) return@withLock

        if (!hasScanPermission(context)) {
            Log.w(TAG, "Cannot start BLE scanner: scan permission not granted")
            _isScanningFlow.value = false
            return@withLock
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return@withLock
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled, cannot start BLE Observer")
            _isScanningFlow.value = false
            return@withLock
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            _isScanningFlow.value = false
            return@withLock
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
                    setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                }
            }
            .build()

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
                Log.w(TAG, "BLE Scan failed with errorCode: $errorCode")
                _isScanningFlow.value = false
            }
        }

        try {
            leScanner.startScan(listOf(scanFilter), scanSettings, callback)
            scanner = leScanner
            activeCallback = callback
            isScanning = true
            _isScanningFlow.value = true
            Log.i(TAG, "BLE Observer started scanning (boost=$boost)")
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

        // Anti-duplicate protection: ignore if timestamp already processed or in past
        if (packet.timestamp <= lastHandledTimestamp) return
        lastHandledTimestamp = packet.timestamp

        val rssi = result.rssi
        Log.i(TAG, "Received valid BLE glucose packet: ts=${packet.timestamp}, bg=${packet.valueMmol}, rssi=$rssi, bat=${packet.batteryPercent}%")
        _packetReceivedEvent.tryEmit(Pair(packet, rssi))

        scope.launch {
            try {
                // Update diagnostic info in settings
                val currentSettings = settingsRepository.getSettings().firstOrNull() ?: return@launch
                val updatedBle = currentSettings.bleBridgeSettings.copy(
                    lastPacketTimestamp = packet.timestamp,
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

    private suspend fun stopScanningInternal() = mutex.withLock {
        if (!isScanning) return@withLock
        try {
            activeCallback?.let { cb ->
                scanner?.stopScan(cb)
            }
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
