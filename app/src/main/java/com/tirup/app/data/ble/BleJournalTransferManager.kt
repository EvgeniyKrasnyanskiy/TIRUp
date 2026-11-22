package com.tirup.app.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.domain.model.LabHba1cRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

sealed class JournalTransferState {
    object Idle : JournalTransferState()
    data class SendingWaiting(val remainingSec: Int, val recordCount: Int) : JournalTransferState()
    data class SendingProgress(val percent: Int) : JournalTransferState()
    data class ReceivingScanning(val remainingSec: Int) : JournalTransferState()
    data class ReceivingProgress(val percent: Int) : JournalTransferState()
    data class Success(val messageRu: String, val messageEn: String) : JournalTransferState()
    data class Error(val messageRu: String, val messageEn: String) : JournalTransferState()
}

/**
 * Ephemeral, pairing-free BLE GATT peer-to-peer transfer manager for HbA1c lab journal records.
 *
 * Uses a dedicated 128-bit GATT Service UUID so it never interferes with CGM sensors or the
 * continuous BLE Bridge broadcaster/observer. Automatically shuts down on completion, error,
 * cancellation, or 60-second timeout.
 */
object BleJournalTransferManager {

    private const val TAG = "BleJournalTransfer"
    private const val TRANSFER_TIMEOUT_MS = 60_000L
    private const val ACK_COMPLETE: Int = -1
    private const val MAX_CHUNK_BYTES = 240

    val SERVICE_UUID: UUID = UUID.fromString("54550001-4862-4131-6353-796e63544952")
    private val CHAR_CHUNK_READ_UUID: UUID = UUID.fromString("54550002-4862-4131-6353-796e63544952")
    private val CHAR_CONTROL_WRITE_UUID: UUID = UUID.fromString("54550003-4862-4131-6353-796e63544952")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<JournalTransferState>(JournalTransferState.Idle)
    val state: StateFlow<JournalTransferState> = _state.asStateFlow()

    private var timeoutJob: Job? = null
    private var mtuFallbackJob: Job? = null

    // Sender resources
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var outgoingFrame: ByteArray = ByteArray(0)
    private var outgoingRecordCount: Int = 0
    private val requestedOffsets = ConcurrentHashMap<String, Int>()
    private val serverDeviceMtu = ConcurrentHashMap<String, Int>()

    // Receiver resources
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var clientGatt: BluetoothGatt? = null
    private val hasFoundSender = AtomicBoolean(false)
    private val servicesDiscovered = AtomicBoolean(false)
    private var clientMtu: Int = 23
    private var receiveBuffer = ByteArrayOutputStream()
    private var expectedTotalBytes: Int = -1
    private var clientReadChar: BluetoothGattCharacteristic? = null
    private var clientControlChar: BluetoothGattCharacteristic? = null
    private var pendingAckSent: Boolean = false
    private var onRecordsReceivedCallback: ((List<LabHba1cRecord>) -> Hba1cMergeResult)? = null
    private var gattRetryUsed: Boolean = false

    fun requiredSenderPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    fun requiredReceiverPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasPermissions(context: Context, perms: Array<String>): Boolean {
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun resetState() {
        cleanupInternal()
        _state.value = JournalTransferState.Idle
    }

    fun cancel() {
        cleanupInternal()
        _state.value = JournalTransferState.Idle
    }

    @SuppressLint("MissingPermission")
    fun startSending(context: Context, records: List<LabHba1cRecord>) {
        cleanupInternal()

        if (records.isEmpty()) {
            _state.value = JournalTransferState.Error(
                messageRu = "Журнал HbA1c пуст — нечего отправлять",
                messageEn = "HbA1c journal is empty — nothing to send"
            )
            return
        }

        if (!hasPermissions(context, requiredSenderPermissions())) {
            _state.value = JournalTransferState.Error(
                messageRu = "Нет разрешений Bluetooth для отправки",
                messageEn = "Missing Bluetooth permissions to send"
            )
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = JournalTransferState.Error(
                messageRu = "Включите Bluetooth на телефоне",
                messageEn = "Please turn on Bluetooth"
            )
            return
        }

        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            _state.value = JournalTransferState.Error(
                messageRu = "Устройство не поддерживает BLE-вещание",
                messageEn = "Device does not support BLE advertising"
            )
            return
        }

        outgoingFrame = Hba1cJournalCodec.encodeFrame(records)
        outgoingRecordCount = records.size
        requestedOffsets.clear()
        serverDeviceMtu.clear()

        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                if (device == null) return
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Receiver connected: ${device.address}")
                    requestedOffsets[device.address] = 0
                    serverDeviceMtu[device.address] = 23
                    _state.value = JournalTransferState.SendingProgress(5)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    requestedOffsets.remove(device.address)
                    serverDeviceMtu.remove(device.address)
                }
            }

            override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
                if (device != null && mtu >= 23) {
                    serverDeviceMtu[device.address] = mtu
                    Log.d(TAG, "Sender negotiated MTU=$mtu with ${device.address}")
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                val srv = gattServer ?: return
                if (device == null || characteristic?.uuid != CHAR_CONTROL_WRITE_UUID || value == null || value.size < 4) {
                    if (responseNeeded && device != null) {
                        srv.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, 0, null)
                    }
                    return
                }

                val requested = ByteBuffer.wrap(value, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                if (responseNeeded) {
                    srv.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                if (requested == ACK_COMPLETE) {
                    Log.i(TAG, "Receiver confirmed full HbA1c journal ($outgoingRecordCount records)")
                    val sentCount = outgoingRecordCount
                    _state.value = JournalTransferState.Success(
                        messageRu = "Журнал отправлен ($sentCount зап.)",
                        messageEn = "Journal sent ($sentCount records)"
                    )
                    scope.launch {
                        delay(400L)
                        cleanupInternal()
                    }
                } else {
                    val safeOffset = requested.coerceIn(0, outgoingFrame.size)
                    requestedOffsets[device.address] = safeOffset
                    val pct = if (outgoingFrame.isNotEmpty()) {
                        ((safeOffset * 100) / outgoingFrame.size).coerceIn(5, 99)
                    } else 50
                    _state.value = JournalTransferState.SendingProgress(pct)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                val srv = gattServer ?: return
                if (device == null || characteristic?.uuid != CHAR_CHUNK_READ_UUID) {
                    if (device != null) {
                        srv.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null)
                    }
                    return
                }

                val basePos = requestedOffsets[device.address] ?: 0
                val pos = basePos + offset
                val mtu = serverDeviceMtu[device.address] ?: 23
                val chunkSize = (mtu - 3).coerceIn(20, MAX_CHUNK_BYTES)
                val chunk = if (pos in 0 until outgoingFrame.size) {
                    outgoingFrame.copyOfRange(pos, minOf(pos + chunkSize, outgoingFrame.size))
                } else {
                    ByteArray(0)
                }
                srv.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, chunk)
            }
        }

        try {
            val server = bm.openGattServer(context.applicationContext, serverCallback)
            if (server == null) {
                _state.value = JournalTransferState.Error(
                    messageRu = "Не удалось открыть Bluetooth GATT-сервер",
                    messageEn = "Failed to open Bluetooth GATT server"
                )
                return
            }

            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val readChar = BluetoothGattCharacteristic(
                CHAR_CHUNK_READ_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            val controlChar = BluetoothGattCharacteristic(
                CHAR_CONTROL_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(readChar)
            service.addCharacteristic(controlChar)
            server.addService(service)
            gattServer = server

            val advSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(TRANSFER_TIMEOUT_MS.toInt())
                .build()

            val advData = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

            val cb = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "HbA1c GATT sender advertising started (${outgoingFrame.size} bytes)")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "HbA1c GATT advertise failed: $errorCode")
                    cleanupInternal()
                    _state.value = JournalTransferState.Error(
                        messageRu = "Ошибка запуска BLE-отправки (код $errorCode)",
                        messageEn = "Failed to start BLE send (code $errorCode)"
                    )
                }
            }

            advertiser = leAdvertiser
            advertiseCallback = cb
            leAdvertiser.startAdvertising(advSettings, advData, cb)

            startCountdownTimeout(isSender = true, recordCount = records.size)
        } catch (e: Exception) {
            Log.e(TAG, "startSending error: ${e.message}", e)
            cleanupInternal()
            _state.value = JournalTransferState.Error(
                messageRu = "Ошибка Bluetooth: ${e.message}",
                messageEn = "Bluetooth error: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startReceiving(
        context: Context,
        onRecordsReceived: (List<LabHba1cRecord>) -> Hba1cMergeResult
    ) {
        cleanupInternal()

        if (!hasPermissions(context, requiredReceiverPermissions())) {
            _state.value = JournalTransferState.Error(
                messageRu = "Нет разрешений Bluetooth для приёма",
                messageEn = "Missing Bluetooth permissions to receive"
            )
            return
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = JournalTransferState.Error(
                messageRu = "Включите Bluetooth на телефоне",
                messageEn = "Please turn on Bluetooth"
            )
            return
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            _state.value = JournalTransferState.Error(
                messageRu = "BLE-сканер недоступен",
                messageEn = "BLE scanner unavailable"
            )
            return
        }

        onRecordsReceivedCallback = onRecordsReceived
        hasFoundSender.set(false)
        servicesDiscovered.set(false)
        gattRetryUsed = false
        clientMtu = 23
        receiveBuffer = ByteArrayOutputStream()
        expectedTotalBytes = -1
        pendingAckSent = false

        val appCtx = context.applicationContext
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                if (!hasFoundSender.compareAndSet(false, true)) return

                Log.i(TAG, "Found HbA1c sender device: ${device.address} (rssi=${result.rssi})")
                try {
                    leScanner.stopScan(this)
                } catch (_: Exception) {}
                scanCallback = null

                _state.value = JournalTransferState.ReceivingProgress(5)
                scope.launch {
                    delay(250L) // Brief pause after stopScan avoids Android GATT 133 error
                    connectToSender(appCtx, device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "HbA1c scan failed: $errorCode")
                cleanupInternal()
                _state.value = JournalTransferState.Error(
                    messageRu = "Ошибка поиска Bluetooth (код $errorCode)",
                    messageEn = "Bluetooth scan failed (code $errorCode)"
                )
            }
        }

        try {
            scanner = leScanner
            scanCallback = cb
            leScanner.startScan(listOf(filter), scanSettings, cb)
            startCountdownTimeout(isSender = false, recordCount = 0)
        } catch (e: Exception) {
            Log.e(TAG, "startReceiving error: ${e.message}", e)
            cleanupInternal()
            _state.value = JournalTransferState.Error(
                messageRu = "Ошибка запуска приёма: ${e.message}",
                messageEn = "Failed to start receive: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToSender(appContext: Context, device: BluetoothDevice) {
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Connected to HbA1c sender GATT, requesting MTU 256")
                    servicesDiscovered.set(false)
                    val mtuRequested = gatt.requestMtu(256)
                    if (!mtuRequested) {
                        gatt.discoverServices()
                    } else {
                        // Fallback if device never invokes onMtuChanged
                        mtuFallbackJob?.cancel()
                        mtuFallbackJob = scope.launch {
                            delay(2500L)
                            if (servicesDiscovered.compareAndSet(false, true)) {
                                try { gatt.discoverServices() } catch (_: Exception) {}
                            }
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (pendingAckSent) return // Normal disconnect after completion
                    if (status == 133 && !gattRetryUsed) {
                        gattRetryUsed = true
                        Log.w(TAG, "GATT 133 on connect, retrying once...")
                        try { gatt.close() } catch (_: Exception) {}
                        clientGatt = null
                        scope.launch {
                            delay(400L)
                            connectToSender(appContext, device)
                        }
                    } else if (_state.value !is JournalTransferState.Success && _state.value !is JournalTransferState.Idle) {
                        cleanupInternal()
                        _state.value = JournalTransferState.Error(
                            messageRu = "Соединение Bluetooth прервано (код $status)",
                            messageEn = "Bluetooth connection lost (status $status)"
                        )
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                mtuFallbackJob?.cancel()
                if (status == BluetoothGatt.GATT_SUCCESS && mtu >= 23) {
                    clientMtu = mtu
                }
                if (servicesDiscovered.compareAndSet(false, true)) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    cleanupInternal()
                    _state.value = JournalTransferState.Error(
                        messageRu = "Не удалось найти сервис журнала (код $status)",
                        messageEn = "Failed to discover journal service (status $status)"
                    )
                    return
                }
                val svc = gatt.getService(SERVICE_UUID)
                val readChar = svc?.getCharacteristic(CHAR_CHUNK_READ_UUID)
                val ctrlChar = svc?.getCharacteristic(CHAR_CONTROL_WRITE_UUID)
                if (readChar == null || ctrlChar == null) {
                    cleanupInternal()
                    _state.value = JournalTransferState.Error(
                        messageRu = "Сервис обмена журналом не найден",
                        messageEn = "Journal transfer service not found"
                    )
                    return
                }
                clientReadChar = readChar
                clientControlChar = ctrlChar
                receiveBuffer.reset()
                expectedTotalBytes = -1
                pendingAckSent = false
                writeControlOffset(gatt, ctrlChar, 0)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (pendingAckSent) {
                    // Transfer finished and ACK acknowledged
                    scope.launch {
                        delay(200L)
                        cleanupInternal()
                    }
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    cleanupInternal()
                    _state.value = JournalTransferState.Error(
                        messageRu = "Ошибка синхронизации смещения (код $status)",
                        messageEn = "Offset sync error (status $status)"
                    )
                    return
                }
                val readChar = clientReadChar ?: return
                gatt.readCharacteristic(readChar)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                handleChunkRead(gatt, characteristic.value, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                handleChunkRead(gatt, value, status)
            }
        }

        clientGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun handleChunkRead(gatt: BluetoothGatt, chunk: ByteArray?, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS || chunk == null) {
            cleanupInternal()
            _state.value = JournalTransferState.Error(
                messageRu = "Ошибка чтения данных (код $status)",
                messageEn = "Read error (status $status)"
            )
            return
        }

        if (chunk.isNotEmpty()) {
            receiveBuffer.write(chunk)
        }

        val currentBytes = receiveBuffer.toByteArray()
        if (expectedTotalBytes < 0 && currentBytes.size >= Hba1cJournalCodec.HEADER_SIZE) {
            val header = Hba1cJournalCodec.parseHeader(currentBytes)
            if (header == null) {
                cleanupInternal()
                _state.value = JournalTransferState.Error(
                    messageRu = "Повреждённый заголовок пакета",
                    messageEn = "Corrupted transfer header"
                )
                return
            }
            expectedTotalBytes = Hba1cJournalCodec.HEADER_SIZE + header.first
        }

        if (expectedTotalBytes > 0 && currentBytes.size >= expectedTotalBytes) {
            // Complete frame received!
            val decoded = Hba1cJournalCodec.decodeFrame(currentBytes)
            decoded.fold(
                onSuccess = { records ->
                    val mergeResult = onRecordsReceivedCallback?.invoke(records)
                    val added = mergeResult?.addedCount ?: records.size
                    val skipped = mergeResult?.skippedDuplicates ?: 0
                    val msgRu = if (skipped > 0) {
                        "Принято: добавлено $added (дублей: $skipped)"
                    } else {
                        "Принято записей HbA1c: $added"
                    }
                    val msgEn = if (skipped > 0) {
                        "Received: $added added ($skipped duplicates)"
                    } else {
                        "Received $added HbA1c record(s)"
                    }
                    _state.value = JournalTransferState.Success(msgRu, msgEn)
                    pendingAckSent = true
                    val ctrlChar = clientControlChar
                    if (ctrlChar != null) {
                        writeControlOffset(gatt, ctrlChar, ACK_COMPLETE)
                    } else {
                        cleanupInternal()
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "Decode failed: ${err.message}")
                    cleanupInternal()
                    _state.value = JournalTransferState.Error(
                        messageRu = "Ошибка проверки данных (CRC)",
                        messageEn = "Data verification failed (CRC)"
                    )
                }
            )
            return
        }

        if (chunk.isEmpty()) {
            cleanupInternal()
            _state.value = JournalTransferState.Error(
                messageRu = "Передача оборвалась раньше времени",
                messageEn = "Transfer ended unexpectedly"
            )
            return
        }

        val pct = if (expectedTotalBytes > 0) {
            ((currentBytes.size * 100) / expectedTotalBytes).coerceIn(10, 99)
        } else 15
        _state.value = JournalTransferState.ReceivingProgress(pct)

        val ctrlChar = clientControlChar ?: return
        writeControlOffset(gatt, ctrlChar, currentBytes.size)
    }

    @SuppressLint("MissingPermission")
    private fun writeControlOffset(
        gatt: BluetoothGatt,
        controlChar: BluetoothGattCharacteristic,
        offset: Int
    ) {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(offset).array()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(controlChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            controlChar.value = bytes
            controlChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(controlChar)
        }
    }

    private fun startCountdownTimeout(isSender: Boolean, recordCount: Int) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            val totalSec = (TRANSFER_TIMEOUT_MS / 1000L).toInt()
            for (rem in totalSec downTo 1) {
                val cur = _state.value
                if (cur is JournalTransferState.Success || cur is JournalTransferState.Error) return@launch
                if (isSender && cur !is JournalTransferState.SendingProgress) {
                    _state.value = JournalTransferState.SendingWaiting(rem, recordCount)
                } else if (!isSender && cur !is JournalTransferState.ReceivingProgress) {
                    _state.value = JournalTransferState.ReceivingScanning(rem)
                }
                delay(1000L)
            }
            val cur = _state.value
            if (cur !is JournalTransferState.Success && cur !is JournalTransferState.Error && cur !is JournalTransferState.Idle) {
                cleanupInternal()
                _state.value = if (isSender) {
                    JournalTransferState.Error(
                        messageRu = "Время ожидания вышло — получатель не подключился",
                        messageEn = "Timed out — no receiver connected"
                    )
                } else {
                    JournalTransferState.Error(
                        messageRu = "Отправитель не найден. Нажмите «Отправить» на втором телефоне",
                        messageEn = "Sender not found. Tap Send on the other phone"
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanupInternal() {
        timeoutJob?.cancel()
        timeoutJob = null
        mtuFallbackJob?.cancel()
        mtuFallbackJob = null

        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advertiseCallback = null
        advertiser = null

        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null

        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null
        scanner = null

        try {
            clientGatt?.disconnect()
            clientGatt?.close()
        } catch (_: Exception) {}
        clientGatt = null
        clientReadChar = null
        clientControlChar = null
        requestedOffsets.clear()
        serverDeviceMtu.clear()
    }
}
