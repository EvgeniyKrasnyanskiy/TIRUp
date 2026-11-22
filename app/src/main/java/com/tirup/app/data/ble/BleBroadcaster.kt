package com.tirup.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tirup.app.TirupApplication

@OptIn(ExperimentalCoroutinesApi::class)
object BleBroadcaster {

    private const val TAG = "BleBroadcaster"
    const val BURST_1MIN_MS = 10_000L  // 10 seconds for 1-minute CGM sensors (covers two ~5.1s ECO/OEM scan windows; hardware-timed)
    const val BURST_5MIN_MS = 15_000L  // 15 seconds for 5-minute CGM sensors (Libre 1/2, Dexcom G6)
    const val HEARTBEAT_BURST_MS = 15_000L // 15 seconds for 5-minute fallback heartbeat timer for max reliability
    const val TEST_PING_BURST_MS = 30_000L // 30 seconds for manual diagnostic test ping
    const val ALARM_ACK_BURST_MS = 8_000L  // 8 seconds hardware-timed alarm ACK burst (reaches both LOW_LATENCY and ECO scanners)
    const val WAKE_LOCK_START_TIMEOUT_MS = 2_500L // Short safety cap; released as soon as BT controller confirms start

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val wakeLockSync = Any()
    private var appContext: Context? = null

    private var currentAdvertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null
    private var activeSetCallback: AdvertisingSetCallback? = null
    private var cleanupBurstJob: Job? = null
    private var heartbeatJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarmAckAdvertiser: BluetoothLeAdvertiser? = null
    private var alarmAckCallback: AdvertiseCallback? = null
    private var alarmAckCleanupJob: Job? = null
    private val alarmAckMutex = Mutex()

    private var lastReadingCache: GlucoseReading? = null
    private var lastRateCache: Double = 0.0
    private var lastSettingsCache: BleBridgeSettings? = null

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _broadcastEndTargetMs = MutableStateFlow(0L)
    private val _nextHeartbeatTargetMs = MutableStateFlow(0L)

    /**
     * Lazy UI countdowns: tick only while a lifecycle-aware screen actively observes them.
     * Zero background CPU wakeups when the app is stopped or the screen is off.
     */
    val broadcastRemainingSec: StateFlow<Int> = _broadcastEndTargetMs
        .flatMapLatest { endMs ->
            flow {
                if (endMs <= 0L) {
                    emit(0)
                } else {
                    while (true) {
                        val rem = ((endMs - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
                        emit(rem)
                        if (rem <= 0) break
                        delay(1000L)
                    }
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(0L), 0)

    val nextHeartbeatRemainingSec: StateFlow<Int> = _nextHeartbeatTargetMs
        .flatMapLatest { targetMs ->
            flow {
                if (targetMs <= 0L) {
                    emit(300)
                } else {
                    while (true) {
                        val rem = ((targetMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                        emit(rem)
                        if (rem <= 0) break
                        delay(1000L)
                    }
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(0L), 300)

    internal fun computeLegacyTimeoutMs(burstDurationMs: Long): Int =
        burstDurationMs.coerceIn(1_000L, 180_000L).toInt()

    internal fun computeAdvertisingSetDurationTenMs(burstDurationMs: Long): Int =
        ((burstDurationMs + 9L) / 10L).coerceIn(100L, 65_535L).toInt()

    private fun acquireShortStartWakeLock(context: Context) {
        synchronized(wakeLockSync) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleBroadcasterWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_START_TIMEOUT_MS)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire short WakeLock: ${e.message}")
            }
        }
    }

    private fun releaseStartWakeLock() {
        synchronized(wakeLockSync) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}
            wakeLock = null
        }
    }

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
        isRu: Boolean = true,
        cob: Double = reading.cob ?: 0.0,
        onStatus: ((Boolean, String) -> Unit)? = null
    ) {
        if (settings.role != BleBridgeRole.BROADCASTER) {
            onStatus?.invoke(false, if (isRu) "Роль «Вещатель» не включена" else "Broadcaster role is not enabled")
            return
        }

        if (!hasAdvertisePermission(context)) {
            Log.w(TAG, "Cannot advertise: BLUETOOTH_ADVERTISE permission not granted")
            onStatus?.invoke(false, if (isRu) "Требуется разрешение на поиск устройств поблизости" else "Nearby devices permission required")
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth adapter is disabled, skipping BLE broadcast")
            onStatus?.invoke(false, if (isRu) "Bluetooth выключен на смартфоне" else "Bluetooth is disabled on device")
            return
        }

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "Device does not support BLE Peripheral advertising")
            onStatus?.invoke(false, if (isRu) "Устройство не поддерживает режим вещания Bluetooth" else "Device does not support Bluetooth broadcasting")
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
            pin = settings.familyPin,
            cob = cob
        )

        val legacyTimeoutMs = computeLegacyTimeoutMs(burstDurationMs)
        val durationTenMs = computeAdvertisingSetDurationTenMs(burstDurationMs)

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // 100ms advertising interval for reliable capture
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(legacyTimeoutMs) // hardware controller timeout; AP CPU can sleep immediately
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

                    // Hold WakeLock ONLY until the BT controller confirms advertising start (~50-150ms),
                    // then release immediately so CPU enters deep sleep while controller runs the timed burst.
                    acquireShortStartWakeLock(context)

                    val durationSec = (burstDurationMs / 1000L).toInt()
                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            releaseStartWakeLock()
                            Log.i(TAG, "BLE broadcast started (${durationSec}s hardware timeout) for reading ts=${reading.timestamp}")
                            _isBroadcasting.value = true
                            _broadcastEndTargetMs.value = System.currentTimeMillis() + burstDurationMs
                            onStatus?.invoke(true, if (isRu) "Передача запущена ($durationSec сек)" else "Broadcast started ($durationSec s)")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            releaseStartWakeLock()
                            Log.w(TAG, "BLE broadcast start failed with code: $errorCode")
                            scope.launch {
                                mutex.withLock { stopAdvertisingInternal(cancelHeartbeat = false) }
                            }
                            val errDesc = when (errorCode) {
                                ADVERTISE_FAILED_DATA_TOO_LARGE -> if (isRu) "Пакет слишком велик" else "Data too large"
                                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> if (isRu) "Слишком много вещателей BLE" else "Too many advertisers"
                                ADVERTISE_FAILED_ALREADY_STARTED -> if (isRu) "Вещание уже запущено" else "Already advertising"
                                ADVERTISE_FAILED_INTERNAL_ERROR -> if (isRu) "Внутренняя ошибка Bluetooth" else "Internal Bluetooth error"
                                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> if (isRu) "BLE-вещание не поддерживается" else "BLE advertising not supported"
                                else -> if (isRu) "Код ошибки $errorCode" else "Error code $errorCode"
                            }
                            onStatus?.invoke(false, if (isRu) "Ошибка BLE: $errDesc" else "BLE error: $errDesc")
                        }
                    }

                    val canUseLongRange = settings.useLongRange && isLongRangeSupported(adapter)
                    if (canUseLongRange) {
                        val setCallback = object : AdvertisingSetCallback() {
                            override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                                    releaseStartWakeLock()
                                    Log.i(TAG, "BLE Long Range (Coded PHY) broadcast started (${durationSec}s hw timeout) for reading ts=${reading.timestamp}")
                                    _isBroadcasting.value = true
                                    _broadcastEndTargetMs.value = System.currentTimeMillis() + burstDurationMs
                                    onStatus?.invoke(true, if (isRu) "Long Range запущен ($durationSec сек)" else "Long Range started ($durationSec s)")
                                } else {
                                    Log.w(TAG, "BLE Long Range advertising set failed with status: $status. Falling back to Legacy 1M...")
                                    scope.launch {
                                        mutex.withLock {
                                            stopAdvertisingInternal(cancelHeartbeat = false)
                                            acquireShortStartWakeLock(context)
                                            startLegacyAdvertising(advertiser, advertiseSettings, advertiseData, callback, isRu, onStatus)
                                        }
                                    }
                                }
                            }

                            override fun onAdvertisingEnabled(advertisingSet: AdvertisingSet?, enable: Boolean, status: Int) {
                                if (!enable) {
                                    Log.d(TAG, "BLE Long Range hardware burst timer completed")
                                    val completedCallback = this
                                    scope.launch {
                                        mutex.withLock {
                                            if (activeSetCallback === completedCallback) {
                                                stopAdvertisingInternal(cancelHeartbeat = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val parameters = AdvertisingSetParameters.Builder()
                            .setLegacyMode(false)
                            .setConnectable(false)
                            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                            .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)
                            .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
                            .build()

                        activeSetCallback = setCallback
                        currentAdvertiser = advertiser
                        try {
                            advertiser.startAdvertisingSet(parameters, advertiseData, null, null, null, durationTenMs, 0, setCallback)
                        } catch (e: Exception) {
                            Log.w(TAG, "Exception starting AdvertisingSet, falling back to legacy: ${e.message}")
                            startLegacyAdvertising(advertiser, advertiseSettings, advertiseData, callback, isRu, onStatus)
                        }
                    } else {
                        startLegacyAdvertising(advertiser, advertiseSettings, advertiseData, callback, isRu, onStatus)
                    }

                    _broadcastEndTargetMs.value = System.currentTimeMillis() + burstDurationMs

                    // Lightweight state cleanup without WakeLock (hardware timer stops radio even during deep sleep)
                    cleanupBurstJob?.cancel()
                    cleanupBurstJob = launch {
                        delay(burstDurationMs + 300L)
                        mutex.withLock {
                            if (_isBroadcasting.value || activeCallback != null || activeSetCallback != null) {
                                stopAdvertisingInternal(cancelHeartbeat = false)
                                Log.d(TAG, "BLE broadcast state cleaned up after ${burstDurationMs}ms burst")
                            }
                        }
                    }

                    // Reset 5-minute fallback heartbeat without a 500ms polling ticker
                    scheduleHeartbeat(300_000L)

                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException starting BLE advertising: ${e.message}")
                    stopAdvertisingInternal(cancelHeartbeat = false)
                    onStatus?.invoke(false, if (isRu) "Ошибка безопасности: нет Bluetooth-доступа" else "Security error: Bluetooth access denied")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start BLE advertising: ${e.message}")
                    stopAdvertisingInternal(cancelHeartbeat = false)
                    onStatus?.invoke(false, if (isRu) "Сбой запуска: ${e.message}" else "Launch error: ${e.message}")
                }
            }
        }
    }

    private fun scheduleHeartbeat(delayMs: Long) {
        _nextHeartbeatTargetMs.value = System.currentTimeMillis() + delayMs
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay(delayMs)
            if (!_isBroadcasting.value) {
                triggerFallbackHeartbeat()
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
        isRu: Boolean = true,
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
            isRu = isRu,
            onStatus = onStatus
        )
    }

    /**
     * Sends a 10-second test ping burst specifically for open-field range testing.
     */
    fun broadcastRangeTestPing(
        context: Context,
        reading: GlucoseReading?,
        settings: BleBridgeSettings,
        durationSec: Int = 10,
        isRu: Boolean = true,
        onStatus: (Boolean, String) -> Unit
    ) {
        val targetReading = reading ?: GlucoseReading(
            timestamp = 0L,
            valueMmol = 0.0,
            trendArrow = "",
            iob = null,
            cob = null
        )
        broadcastReading(
            context = context,
            reading = targetReading,
            rateOfChange = 0.0,
            iob = targetReading.iob ?: 0.0,
            settings = settings,
            burstDurationMs = durationSec * 1000L,
            isRu = isRu,
            onStatus = onStatus
        )
    }

    /**
     * Broadcasts a compact 14-byte Alarm ACK control packet over BLE Legacy 1M PHY so BOTH
     * Legacy and Extended (PHY_LE_ALL_SUPPORTED) scanners catch it reliably on Master and Followers.
     * Works regardless of BleBridgeRole (BROADCASTER or OBSERVER) and uses a dedicated hardware-timed slot
     * so it never disrupts glucose telemetry heartbeats.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun broadcastAlarmAck(
        context: Context,
        payload: ByteArray,
        burstDurationMs: Long = ALARM_ACK_BURST_MS
    ) {
        if (!hasAdvertisePermission(context)) {
            Log.d(TAG, "Cannot broadcast alarm ACK: BLUETOOTH_ADVERTISE permission not granted")
            return
        }
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val timeoutMs = computeLegacyTimeoutMs(burstDurationMs)
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(timeoutMs)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(BlePacketCodec.MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        scope.launch {
            alarmAckMutex.withLock {
                stopAlarmAckInternal()
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val ackWakeLock = try {
                    pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleAlarmAckWakeLock")?.apply {
                        setReferenceCounted(false)
                        acquire(WAKE_LOCK_START_TIMEOUT_MS)
                    }
                } catch (_: Exception) { null }

                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        try { if (ackWakeLock?.isHeld == true) ackWakeLock.release() } catch (_: Exception) {}
                        Log.i(TAG, "BLE alarm ACK broadcast started (${timeoutMs}ms hardware timeout)")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        try { if (ackWakeLock?.isHeld == true) ackWakeLock.release() } catch (_: Exception) {}
                        Log.w(TAG, "BLE alarm ACK broadcast failed with code=$errorCode")
                        scope.launch { alarmAckMutex.withLock { stopAlarmAckInternal() } }
                    }
                }

                try {
                    alarmAckAdvertiser = advertiser
                    alarmAckCallback = cb
                    advertiser.startAdvertising(advertiseSettings, advertiseData, cb)
                    alarmAckCleanupJob = launch {
                        delay(burstDurationMs + 300L)
                        alarmAckMutex.withLock { stopAlarmAckInternal() }
                    }
                } catch (e: Exception) {
                    try { if (ackWakeLock?.isHeld == true) ackWakeLock.release() } catch (_: Exception) {}
                    Log.w(TAG, "Failed to start BLE alarm ACK broadcast: ${e.message}")
                    stopAlarmAckInternal()
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopAlarmAckInternal() {
        try {
            alarmAckCallback?.let { cb -> alarmAckAdvertiser?.stopAdvertising(cb) }
        } catch (_: Exception) {}
        alarmAckCallback = null
        alarmAckAdvertiser = null
        alarmAckCleanupJob?.cancel()
        alarmAckCleanupJob = null
    }

    fun stopAdvertising() {
        scope.launch {
            mutex.withLock {
                stopAdvertisingInternal(cancelHeartbeat = true)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startLegacyAdvertising(
        advertiser: BluetoothLeAdvertiser,
        advertiseSettings: AdvertiseSettings,
        advertiseData: AdvertiseData,
        callback: AdvertiseCallback,
        isRu: Boolean,
        onStatus: ((Boolean, String) -> Unit)?
    ) {
        try {
            activeCallback = callback
            currentAdvertiser = advertiser
            advertiser.startAdvertising(advertiseSettings, advertiseData, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start legacy advertising: ${e.message}")
            stopAdvertisingInternal(cancelHeartbeat = false)
            onStatus?.invoke(false, if (isRu) "Ошибка запуска BLE: ${e.message}" else "BLE start error: ${e.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopAdvertisingInternal(cancelHeartbeat: Boolean = true) {
        try {
            activeCallback?.let { cb ->
                currentAdvertiser?.stopAdvertising(cb)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        activeCallback = null

        try {
            activeSetCallback?.let { cb ->
                currentAdvertiser?.stopAdvertisingSet(cb)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        activeSetCallback = null
        currentAdvertiser = null
        _isBroadcasting.value = false
        _broadcastEndTargetMs.value = 0L
        cleanupBurstJob?.cancel()
        cleanupBurstJob = null
        if (cancelHeartbeat) {
            heartbeatJob?.cancel()
            heartbeatJob = null
            _nextHeartbeatTargetMs.value = 0L
        }
        releaseStartWakeLock()
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return bm.adapter?.isEnabled == true
    }

    fun isLongRangeSupported(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bm.adapter ?: return false
        return isLongRangeSupported(adapter)
    }

    fun isLongRangeSupported(adapter: BluetoothAdapter): Boolean {
        return try {
            adapter.isLeCodedPhySupported && adapter.isLeExtendedAdvertisingSupported
        } catch (_: Exception) {
            false
        }
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
