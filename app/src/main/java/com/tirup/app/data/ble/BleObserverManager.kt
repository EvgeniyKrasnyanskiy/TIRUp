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
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import com.tirup.app.presentation.widget.TirupWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

            if (ble.role == BleBridgeRole.OBSERVER) {
                startScanning(context, ble.familyPin, settingsRepository, glucoseRepository)
            } else {
                stopScanning()
            }
        }
    }

    private suspend fun startScanning(
        context: Context,
        familyPin: String,
        settingsRepository: SettingsRepository,
        glucoseRepository: GlucoseRepository
    ) = mutex.withLock {
        if (isScanning) return@withLock

        if (!hasScanPermission(context)) {
            Log.w(TAG, "Cannot start BLE scanner: scan permission not granted")
            return@withLock
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return@withLock
        val adapter = bm.adapter ?: return@withLock
        if (!adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled, cannot start BLE Observer")
            return@withLock
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return@withLock
        }

        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(
                BlePacketCodec.MANUFACTURER_ID,
                byteArrayOf(0x54, 0x55), // Match 'TU' prefix
                byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            )
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // Energy-conserving hardware filter
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
            }
        }

        try {
            leScanner.startScan(listOf(scanFilter), scanSettings, callback)
            scanner = leScanner
            activeCallback = callback
            isScanning = true
            Log.i(TAG, "BLE Observer started scanning in low power mode")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}")
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
                    iob = if (packet.iob > 0.0) packet.iob else null
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

    suspend fun stopScanning() = mutex.withLock {
        if (!isScanning) return@withLock
        try {
            activeCallback?.let { cb ->
                scanner?.stopScan(cb)
            }
        } catch (_: Exception) {}
        activeCallback = null
        scanner = null
        isScanning = false
        Log.i(TAG, "BLE Observer stopped scanning")
    }

    fun hasScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
