package com.tirup.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BleBridgeSettings
import com.tirup.app.domain.model.GlucoseReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BleBroadcaster {

    private const val TAG = "BleBroadcaster"
    private const val ADVERTISE_BURST_MS = 30_000L // 30 seconds per reading to ensure reception

    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentAdvertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null
    private var stopBurstJob: Job? = null
    private var countdownJob: Job? = null

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _broadcastRemainingSec = MutableStateFlow(0)
    val broadcastRemainingSec: StateFlow<Int> = _broadcastRemainingSec.asStateFlow()

    /**
     * Broadcasts a telemetry packet over BLE advertising if BLE Bridge is in BROADCASTER mode.
     * Advertising runs for a 30-second pulse burst and then shuts off to preserve battery.
     */
    fun broadcastReading(
        context: Context,
        reading: GlucoseReading,
        rateOfChange: Double,
        iob: Double,
        settings: BleBridgeSettings,
        onStatus: ((Boolean, String) -> Unit)? = null
    ) {
        if (settings.role != BleBridgeRole.BROADCASTER) {
            onStatus?.invoke(false, "Роль «Мастер (Вещатель)» не включена")
            return
        }

        if (!hasAdvertisePermission(context)) {
            Log.w(TAG, "Cannot advertise: BLUETOOTH_ADVERTISE permission not granted")
            onStatus?.invoke(false, "Нет разрешения BLUETOOTH_ADVERTISE")
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth adapter is disabled, skipping BLE broadcast")
            onStatus?.invoke(false, "Bluetooth выключен на смартфоне")
            return
        }

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "Device does not support BLE Peripheral advertising")
            onStatus?.invoke(false, "Смартфон не поддерживает BLE-вещание (Peripheral mode)")
            return
        }

        val battery = if (settings.transmitBattery) getBatteryLevel(context) else -1
        val payload = BlePacketCodec.encodePacket(
            timestampMs = reading.timestamp,
            valueMmol = reading.valueMmol,
            trendArrow = reading.trendArrow ?: "→",
            rateOfChangeMmolPerMin = rateOfChange,
            iob = iob,
            batteryPercent = battery,
            pin = settings.familyPin
        )

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED) // ~250ms advertising interval
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0) // manual timeout control via coroutine
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(BlePacketCodec.MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        scope.launch {
            try {
                // Stop any previous active burst
                stopAdvertisingInternal()

                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        Log.i(TAG, "BLE broadcast started successfully for reading ts=${reading.timestamp}")
                        _isBroadcasting.value = true
                        onStatus?.invoke(true, "Радиоимпульс запущен (30 сек)")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.w(TAG, "BLE broadcast start failed with code: $errorCode")
                        _isBroadcasting.value = false
                        _broadcastRemainingSec.value = 0
                        val errDesc = when (errorCode) {
                            ADVERTISE_FAILED_DATA_TOO_LARGE -> "Пакет слишком велик"
                            ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Слишком много вещателей BLE"
                            ADVERTISE_FAILED_ALREADY_STARTED -> "Вещание уже запущено"
                            ADVERTISE_FAILED_INTERNAL_ERROR -> "Внутренняя ошибка Bluetooth стека"
                            ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE-вещание не поддерживается чипом"
                            else -> "Код ошибки $errorCode"
                        }
                        onStatus?.invoke(false, "Ошибка BLE: $errDesc")
                    }
                }

                activeCallback = callback
                currentAdvertiser = advertiser
                advertiser.startAdvertising(advertiseSettings, advertiseData, callback)

                // Run countdown and burst shutdown after ADVERTISE_BURST_MS
                stopBurstJob?.cancel()
                countdownJob?.cancel()

                _broadcastRemainingSec.value = (ADVERTISE_BURST_MS / 1000L).toInt()

                countdownJob = launch {
                    while (_broadcastRemainingSec.value > 0) {
                        delay(1000L)
                        _broadcastRemainingSec.value = (_broadcastRemainingSec.value - 1).coerceAtLeast(0)
                    }
                }

                stopBurstJob = launch {
                    delay(ADVERTISE_BURST_MS)
                    stopAdvertisingInternal()
                    Log.d(TAG, "BLE broadcast pulse burst completed")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException starting BLE advertising: ${e.message}")
                _isBroadcasting.value = false
                _broadcastRemainingSec.value = 0
                onStatus?.invoke(false, "Ошибка безопасности: нет Bluetooth-доступа")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE advertising: ${e.message}")
                _isBroadcasting.value = false
                _broadcastRemainingSec.value = 0
                onStatus?.invoke(false, "Сбой запуска: ${e.message}")
            }
        }
    }

    /**
     * Sends a manual test ping burst (30 seconds) for link diagnostics.
     */
    fun broadcastTestPing(
        context: Context,
        reading: GlucoseReading?,
        settings: BleBridgeSettings,
        onStatus: (Boolean, String) -> Unit
    ) {
        val targetReading = reading ?: GlucoseReading(
            timestamp = System.currentTimeMillis(),
            valueMmol = 6.0,
            trendArrow = "→",
            iob = null,
            cob = null
        )
        broadcastReading(
            context = context,
            reading = targetReading,
            rateOfChange = 0.0,
            iob = targetReading.iob ?: 0.0,
            settings = settings,
            onStatus = onStatus
        )
    }

    fun stopAdvertising() {
        scope.launch {
            stopAdvertisingInternal()
        }
    }

    private fun stopAdvertisingInternal() {
        try {
            activeCallback?.let { cb ->
                currentAdvertiser?.stopAdvertising(cb)
            }
        } catch (_: Exception) {}
        activeCallback = null
        currentAdvertiser = null
        _isBroadcasting.value = false
        _broadcastRemainingSec.value = 0
        countdownJob?.cancel()
        stopBurstJob?.cancel()
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return bm.adapter?.isEnabled == true
    }

    fun hasAdvertisePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Prior to Android 12, BLUETOOTH / BLUETOOTH_ADMIN in manifest suffice
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) {
            -1
        }
    }
}
