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
import android.os.PowerManager
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tirup.app.TirupApplication

object BleBroadcaster {

    private const val TAG = "BleBroadcaster"
    const val BURST_1MIN_MS = 5_000L   // 5 seconds for 1-minute CGM sensors (Libre 3, Dexcom G7)
    const val BURST_5MIN_MS = 10_000L  // 10 seconds for 5-minute CGM sensors (Libre 1/2, Dexcom G6)
    const val HEARTBEAT_BURST_MS = 12_000L // 12 seconds for 5-minute fallback heartbeat timer for max reliability
    const val TEST_PING_BURST_MS = 30_000L // 30 seconds for manual diagnostic test ping

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private var appContext: Context? = null

    private var currentAdvertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null
    private var stopBurstJob: Job? = null
    private var countdownJob: Job? = null
    private var heartbeatTickerJob: Job? = null
    private var nextHeartbeatTargetMs: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastReadingCache: GlucoseReading? = null
    private var lastRateCache: Double = 0.0
    private var lastSettingsCache: BleBridgeSettings? = null

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _broadcastRemainingSec = MutableStateFlow(0)
    val broadcastRemainingSec: StateFlow<Int> = _broadcastRemainingSec.asStateFlow()

    private val _nextHeartbeatRemainingSec = MutableStateFlow(300)
    val nextHeartbeatRemainingSec: StateFlow<Int> = _nextHeartbeatRemainingSec.asStateFlow()

    /**
     * Broadcasts a telemetry packet over BLE advertising if BLE Bridge is in BROADCASTER mode.
     * Advertising runs for an adaptive pulse burst (5s or 10s) and then shuts off to preserve battery.
     */
    fun broadcastReading(
        context: Context,
        reading: GlucoseReading,
        rateOfChange: Double,
        iob: Double,
        settings: BleBridgeSettings,
        burstDurationMs: Long = BURST_5MIN_MS,
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

        appContext = context.applicationContext
        lastReadingCache = reading
        lastRateCache = rateOfChange
        lastSettingsCache = settings

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
            mutex.withLock {
                try {
                    // Stop any previous active burst without wiping the heartbeat target
                    stopAdvertisingInternal(cancelHeartbeat = false)

                    // Acquire partial WakeLock to prevent CPU sleep during burst
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    try {
                        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleBroadcasterWakeLock")?.apply {
                            setReferenceCounted(false)
                            acquire(burstDurationMs + 5_000L)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
                    }

                    val durationSec = (burstDurationMs / 1000L).toInt()
                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            Log.i(TAG, "BLE broadcast started successfully for reading ts=${reading.timestamp}")
                            _isBroadcasting.value = true
                            onStatus?.invoke(true, "Радиоимпульс запущен ($durationSec сек)")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            Log.w(TAG, "BLE broadcast start failed with code: $errorCode")
                            scope.launch {
                                mutex.withLock { stopAdvertisingInternal(cancelHeartbeat = false) }
                            }
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

                    _broadcastRemainingSec.value = durationSec

                    countdownJob?.cancel()
                    countdownJob = launch {
                        while (_broadcastRemainingSec.value > 0) {
                            delay(1000L)
                            _broadcastRemainingSec.value = (_broadcastRemainingSec.value - 1).coerceAtLeast(0)
                        }
                    }

                    stopBurstJob?.cancel()
                    stopBurstJob = launch {
                        delay(burstDurationMs)
                        mutex.withLock {
                            stopAdvertisingInternal(cancelHeartbeat = false)
                            Log.d(TAG, "BLE broadcast pulse burst completed ($burstDurationMs ms)")
                            // Reset 5-minute countdown immediately after burst completion
                            scheduleHeartbeat(300_000L)
                        }
                    }

                    // Reset 5-minute target timestamp right away (so UI shows 5:00 while broadcasting)
                    scheduleHeartbeat(300_000L + burstDurationMs)

                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException starting BLE advertising: ${e.message}")
                    stopAdvertisingInternal(cancelHeartbeat = false)
                    onStatus?.invoke(false, "Ошибка безопасности: нет Bluetooth-доступа")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start BLE advertising: ${e.message}")
                    stopAdvertisingInternal(cancelHeartbeat = false)
                    onStatus?.invoke(false, "Сбой запуска: ${e.message}")
                }
            }
        }
    }

    private fun scheduleHeartbeat(delayMs: Long) {
        nextHeartbeatTargetMs = System.currentTimeMillis() + delayMs
        _nextHeartbeatRemainingSec.value = (delayMs / 1000L).toInt()
        ensureTickerRunning()
    }

    private fun ensureTickerRunning() {
        if (heartbeatTickerJob?.isActive == true) return
        heartbeatTickerJob = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (nextHeartbeatTargetMs > 0L) {
                    val remSec = ((nextHeartbeatTargetMs - now) / 1000L).toInt().coerceAtLeast(0)
                    _nextHeartbeatRemainingSec.value = remSec
                    if (remSec == 0 && !_isBroadcasting.value) {
                        triggerFallbackHeartbeat()
                    }
                }
                delay(500L)
            }
        }
    }

    private suspend fun triggerFallbackHeartbeat() {
        // Postpone next trigger to prevent repeated executions
        scheduleHeartbeat(300_000L)
        val ctx = appContext ?: return
        val cachedSettings = lastSettingsCache
        if (cachedSettings?.role != BleBridgeRole.BROADCASTER) return

        var reading = lastReadingCache
        if (reading == null) {
            val app = ctx as? TirupApplication
            val recent = app?.database?.glucoseReadingDao()?.getRecentReadingsSync(1)?.firstOrNull()?.toDomain()
            reading = recent
        }

        if (reading != null) {
            Log.i(TAG, "Triggering 5-minute fallback BLE heartbeat broadcast (12s burst)")
            broadcastReading(
                context = ctx,
                reading = reading,
                rateOfChange = lastRateCache,
                iob = reading.iob ?: 0.0,
                settings = cachedSettings,
                burstDurationMs = HEARTBEAT_BURST_MS
            )
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
            burstDurationMs = TEST_PING_BURST_MS,
            onStatus = onStatus
        )
    }

    fun stopAdvertising() {
        scope.launch {
            mutex.withLock {
                stopAdvertisingInternal(cancelHeartbeat = true)
            }
        }
    }

    private fun stopAdvertisingInternal(cancelHeartbeat: Boolean = true) {
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
        countdownJob = null
        stopBurstJob?.cancel()
        stopBurstJob = null
        if (cancelHeartbeat) {
            heartbeatTickerJob?.cancel()
            heartbeatTickerJob = null
            nextHeartbeatTargetMs = 0L
            _nextHeartbeatRemainingSec.value = 300
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
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
