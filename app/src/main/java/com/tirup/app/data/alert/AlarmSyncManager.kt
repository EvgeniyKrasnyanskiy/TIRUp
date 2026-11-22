package com.tirup.app.data.alert

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.TirupApplication
import com.tirup.app.data.ble.BleBroadcaster
import com.tirup.app.data.ble.BlePacketCodec
import com.tirup.app.data.network.NightscoutUploadManager
import com.tirup.app.data.network.XdripLanClient
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

enum class AlarmSyncTransport {
    BLE,
    WIFI_LAN_UDP,
    WIFI_LAN_TREATMENT,
    NIGHTSCOUT
}

/**
 * Multi-transport coordinator for universal bidirectional alarm snooze/dismissal
 * (Master <-> Followers <-> Followers) across:
 * 1. BLE Bridge (instant offline 1-2s; event-driven temporary Master scan window ONLY while an alarm is active = 0% standby drain)
 * 2. Local Wi-Fi LAN UDP Broadcast on port 17585 (instant <50ms across whole house Wi-Fi; active ONLY during alarms = 0% standby drain)
 * 3. Nightscout REST API Announcement treatments (remote over internet, no server changes required)
 */
object AlarmSyncManager {

    private const val TAG = "AlarmSyncManager"
    private const val PREFS_NAME = "tirup_alarm_sync_prefs"
    private const val KEY_SENDER_ID = "device_sender_id"

    private const val ACTIVE_ALARM_LISTEN_WINDOW_MS = 10 * 60_000L // 10 min max active listener window
    private const val NIGHTSCOUT_ACTIVE_POLL_INTERVAL_MS = 15_000L // 15s fast poll ONLY while alarm is active
    private const val DEDUP_RETENTION_MS = 15 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val seenAcksLock = Any()
    private val seenAcks = LinkedHashMap<String, Long>()

    private val activeAlarmSinceMs = AtomicLong(0L)

    // Temporary event-driven listeners (active ONLY during an active alarm -> 0% standby battery cost)
    private var masterBleScanJob: Job? = null
    private var masterBleScanner: BluetoothLeScanner? = null
    private var masterBleScanCallback: ScanCallback? = null

    private var lanUdpListenJob: Job? = null
    private var lanUdpSocket: DatagramSocket? = null

    private var nightscoutFastPollJob: Job? = null

    /** Callback invoked when a verified remote ACK should silence/snooze local alerts. */
    @Volatile
    var remoteAckHandler: ((Context, AlarmAckPacket, AlarmSyncTransport) -> Unit)? = null

    @Volatile
    private var cachedSenderId: Int = 0

    fun getLocalSenderId(context: Context): Int {
        if (cachedSenderId in 1..255) return cachedSenderId
        synchronized(this) {
            if (cachedSenderId in 1..255) return cachedSenderId
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var id = prefs.getInt(KEY_SENDER_ID, 0)
            if (id !in 1..255) {
                id = (1..255).random()
                prefs.edit().putInt(KEY_SENDER_ID, id).apply()
            }
            cachedSenderId = id
            return id
        }
    }

    /**
     * Called when ANY local glucose/signal alarm triggers.
     * Opens lightweight event-driven receive windows (BLE scan on Master, UDP 17585 on Wi-Fi,
     * fast Nightscout ACK poll) for up to 10 minutes; all windows shut down immediately on dismissal.
     */
    fun onLocalAlarmTriggered(context: Context, scopeMask: Int, isCriticalHypo: Boolean) {
        val appCtx = context.applicationContext
        activeAlarmSinceMs.set(System.currentTimeMillis())

        scope.launch {
            val settings = loadCurrentSettings(appCtx) ?: return@launch
            if (!settings.alertSettings.isAlarmSyncEnabled) return@launch

            Log.i(TAG, "Opening temporary alarm-sync listeners (scope=$scopeMask, critHypo=$isCriticalHypo)")

            // 1. BLE temporary scan window on Master (OBSERVER devices are already scanning continuously)
            val ble = settings.bleBridgeSettings
            if (ble.isEnabled && ble.role == BleBridgeRole.BROADCASTER && ble.familyPin.length == 3) {
                startTemporaryMasterBleScan(appCtx, ble.familyPin)
            }

            // 2. Local Wi-Fi LAN temporary UDP listener (port 17585)
            if (isWifiConnected(appCtx)) {
                startTemporaryLanUdpListener(appCtx, settings)
            }

            // 3. Nightscout fast ACK poller while alarm is actively ringing
            val ns = settings.nightscoutSettings
            if (ns.isEnabled && ns.isValidUrl) {
                startTemporaryNightscoutAckPoller(appCtx, settings)
            }
        }
    }

    /**
     * Called when the user locally dismisses or snoozes an alarm on THIS device.
     * Immediately stops temporary receive windows and broadcasts the ACK over all available transports.
     */
    fun onLocalAlarmDismissed(
        context: Context,
        scopeMask: Int = AlarmSyncScope.ALL,
        snoozeMinutes: Int = 0,
        isCriticalHypo: Boolean = false
    ) {
        val appCtx = context.applicationContext
        stopActiveAlarmListeners()

        scope.launch {
            val settings = loadCurrentSettings(appCtx) ?: return@launch
            if (!settings.alertSettings.isAlarmSyncEnabled) return@launch

            val ble = settings.bleBridgeSettings
            val isMaster = ble.role == BleBridgeRole.BROADCASTER ||
                (ble.role != BleBridgeRole.OBSERVER && !settings.alertSettings.isCaregiverRole)

            val packet = AlarmAckPacket(
                scopeMask = scopeMask,
                ackTimestampSec = System.currentTimeMillis() / 1000L,
                snoozeMinutes = snoozeMinutes.coerceIn(0, 240),
                isFromMaster = isMaster,
                isRelayed = false,
                isCriticalHypo = isCriticalHypo,
                senderId = getLocalSenderId(appCtx)
            )
            recordSeenAck(packet.dedupKey)

            Log.i(
                TAG,
                "Broadcasting universal alarm ACK: scope=$scopeMask snooze=${packet.snoozeMinutes}m fromMaster=$isMaster sender=${packet.senderId}"
            )

            // Channel 1: BLE Bridge (works from BOTH Master and Follower!)
            if (ble.isEnabled && ble.familyPin.length == 3) {
                val blePayload = AlarmSyncProtocol.encodeBleAckPacket(packet, ble.familyPin)
                BleBroadcaster.broadcastAlarmAck(appCtx, blePayload)
            }

            // Channel 2: Local Wi-Fi LAN UDP Broadcast (instant across whole Wi-Fi subnet, no server needed)
            if (isWifiConnected(appCtx)) {
                launch {
                    sendLanUdpBurst(appCtx, packet, settings)
                }
            }

            // Channel 3: Nightscout Cloud Announcement (remote followers outside BLE/Wi-Fi range)
            val ns = settings.nightscoutSettings
            if (ns.isEnabled && ns.isValidUrl && ns.apiSecret.isNotBlank()) {
                launch {
                    val secret = resolvePrimarySharedSecret(settings)
                    val note = AlarmSyncProtocol.encodeTextAck(packet, secret)
                    val res = NightscoutUploadManager.uploadTreatment(
                        settings = ns,
                        notes = note,
                        timestamp = packet.ackTimestampMs,
                        customEventType = AlarmSyncProtocol.NIGHTSCOUT_EVENT_TYPE
                    )
                    if (res.isSuccess) {
                        Log.i(TAG, "Published alarm ACK announcement to Nightscout")
                    } else {
                        Log.w(TAG, "Nightscout alarm ACK upload skipped/failed: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }

    /**
     * Stops all temporary alarm listening windows (called on dismissal or timeout).
     */
    fun stopActiveAlarmListeners() {
        activeAlarmSinceMs.set(0L)
        stopTemporaryMasterBleScan()
        stopTemporaryLanUdpListener()
        nightscoutFastPollJob?.cancel()
        nightscoutFastPollJob = null
    }

    /**
     * Handles a verified incoming [AlarmAckPacket] from any transport channel.
     * Deduplicates, ignores self-echoes, applies local silencing, and performs a 1-hop BLE relay on Master.
     */
    fun onIncomingAck(context: Context, packet: AlarmAckPacket, transport: AlarmSyncTransport) {
        val appCtx = context.applicationContext
        val nowSec = System.currentTimeMillis() / 1000L

        if (!AlarmSyncProtocol.isAckFresh(packet, nowSec)) {
            Log.d(TAG, "Ignoring stale alarm ACK from $transport (ts=${packet.ackTimestampSec}, now=$nowSec)")
            return
        }

        if (packet.senderId == getLocalSenderId(appCtx)) {
            return // Ignore own echoed packet
        }

        if (!recordSeenAck(packet.dedupKey)) {
            return // Already processed via another channel or repeated burst
        }

        scope.launch {
            val settings = loadCurrentSettings(appCtx) ?: return@launch
            if (!settings.alertSettings.isAlarmSyncEnabled) return@launch

            Log.i(
                TAG,
                "Applying remote alarm ACK via $transport: scope=${packet.scopeMask}, snooze=${packet.snoozeMinutes}m, fromMaster=${packet.isFromMaster}, sender=${packet.senderId}"
            )

            remoteAckHandler?.invoke(appCtx, packet, transport)

            // 1-hop BLE relay on Master: if a Follower dismissed the alarm and Master received it
            // (over BLE, Wi-Fi LAN, or Nightscout), Master re-broadcasts once over BLE so other Followers
            // out of direct reach of Follower A are also silenced immediately.
            val ble = settings.bleBridgeSettings
            if (ble.isEnabled && ble.role == BleBridgeRole.BROADCASTER && ble.familyPin.length == 3 &&
                !packet.isFromMaster && !packet.isRelayed
            ) {
                val relayed = packet.copy(isRelayed = true)
                val payload = AlarmSyncProtocol.encodeBleAckPacket(relayed, ble.familyPin)
                Log.i(TAG, "Master relaying follower alarm ACK over BLE (1-hop)")
                BleBroadcaster.broadcastAlarmAck(appCtx, payload)
            }
        }
    }

    /**
     * Parses treatment notes fetched by NightscoutStatusManager or XdripLanManager for remote alarm ACKs.
     */
    fun processTreatmentNote(context: Context, note: String?, settings: UserSettings, transport: AlarmSyncTransport) {
        if (!settings.alertSettings.isAlarmSyncEnabled) return
        if (note.isNullOrBlank() || !note.contains(AlarmSyncProtocol.NOTE_PREFIX)) return

        val secrets = collectExpectedSecrets(settings)
        val packet = AlarmSyncProtocol.decodeTextAck(
            text = note,
            expectedSecrets = secrets,
            allowUnauthenticatedFallback = secrets.isEmpty()
        ) ?: return

        onIncomingAck(context, packet, transport)
    }

    private fun resolvePrimarySharedSecret(settings: UserSettings): String {
        return settings.bleBridgeSettings.familyPin.takeIf { it.length == 3 }
            ?: settings.xdripLanSettings.apiSecret.takeIf { it.isNotBlank() }
            ?: settings.nightscoutSettings.apiSecret.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun collectExpectedSecrets(settings: UserSettings): List<String> {
        return listOf(
            settings.bleBridgeSettings.familyPin.takeIf { it.length == 3 } ?: "",
            settings.xdripLanSettings.apiSecret,
            settings.nightscoutSettings.apiSecret
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun recordSeenAck(dedupKey: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(seenAcksLock) {
            val iter = seenAcks.entries.iterator()
            while (iter.hasNext()) {
                if (now - iter.next().value > DEDUP_RETENTION_MS) iter.remove()
            }
            if (seenAcks.containsKey(dedupKey)) return false
            seenAcks[dedupKey] = now
            return true
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startTemporaryMasterBleScan(context: Context, familyPin: String) {
        if (!hasBleScanPermission(context)) {
            Log.d(TAG, "Skipping temporary Master BLE scan: BLUETOOTH_SCAN permission not granted")
            return
        }
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) return
        val leScanner = adapter.bluetoothLeScanner ?: return

        stopTemporaryMasterBleScan()

        val filter = ScanFilter.Builder()
            .setManufacturerData(BlePacketCodec.MANUFACTURER_ID, byteArrayOf(0x54, 0x55))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val raw = result?.scanRecord?.getManufacturerSpecificData(BlePacketCodec.MANUFACTURER_ID) ?: return
                val ack = AlarmSyncProtocol.decodeBleAckPacket(raw, familyPin) ?: return
                onIncomingAck(context, ack, AlarmSyncTransport.BLE)
            }
        }

        try {
            masterBleScanner = leScanner
            masterBleScanCallback = cb
            leScanner.startScan(listOf(filter), scanSettings, cb)
            Log.i(TAG, "Temporary Master BLE alarm-ACK scan opened (max ${ACTIVE_ALARM_LISTEN_WINDOW_MS / 60_000L} min)")
            masterBleScanJob = scope.launch {
                delay(ACTIVE_ALARM_LISTEN_WINDOW_MS)
                stopTemporaryMasterBleScan()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start temporary Master BLE scan: ${e.message}")
            stopTemporaryMasterBleScan()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun stopTemporaryMasterBleScan() {
        masterBleScanJob?.cancel()
        masterBleScanJob = null
        try {
            masterBleScanCallback?.let { cb -> masterBleScanner?.stopScan(cb) }
        } catch (_: Exception) {}
        masterBleScanCallback = null
        masterBleScanner = null
    }

    private fun startTemporaryLanUdpListener(context: Context, settings: UserSettings) {
        stopTemporaryLanUdpListener()
        val secrets = collectExpectedSecrets(settings)
        lanUdpListenJob = scope.launch {
            val deadline = System.currentTimeMillis() + ACTIVE_ALARM_LISTEN_WINDOW_MS
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 3000
                    bind(InetSocketAddress(AlarmSyncProtocol.LAN_UDP_PORT))
                }
                lanUdpSocket = socket
                Log.i(TAG, "Temporary Wi-Fi LAN UDP alarm-ACK listener opened on :${AlarmSyncProtocol.LAN_UDP_PORT}")
                val buf = ByteArray(512)
                while (isActive && System.currentTimeMillis() < deadline) {
                    val dp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(dp)
                        val ack = AlarmSyncProtocol.decodeLanUdpAck(dp.data, dp.length, secrets)
                        if (ack != null) {
                            onIncomingAck(context, ack, AlarmSyncTransport.WIFI_LAN_UDP)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue until active window expires or cancelled
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "LAN UDP listener stopped: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                if (lanUdpSocket === socket) lanUdpSocket = null
            }
        }
    }

    private fun stopTemporaryLanUdpListener() {
        lanUdpListenJob?.cancel()
        lanUdpListenJob = null
        try { lanUdpSocket?.close() } catch (_: Exception) {}
        lanUdpSocket = null
    }

    private suspend fun sendLanUdpBurst(context: Context, packet: AlarmAckPacket, settings: UserSettings) {
        val secret = resolvePrimarySharedSecret(settings)
        val bytes = AlarmSyncProtocol.encodeLanUdpAck(packet, secret)
        val targets = linkedSetOf<String>()

        val subnet = XdripLanClient.getLocalSubnetInfo(context)
        if (subnet != null) {
            targets.add("${subnet.prefix}255")
        }
        targets.add("255.255.255.255")
        val masterHost = settings.xdripLanSettings.masterHost.trim()
        if (masterHost.isNotEmpty()) {
            targets.add(masterHost)
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
            val sendDelays = listOf(0L, 250L, 600L)
            for (waitMs in sendDelays) {
                if (waitMs > 0L) delay(waitMs)
                for (host in targets) {
                    try {
                        val addr = InetAddress.getByName(host)
                        socket.send(DatagramPacket(bytes, bytes.size, addr, AlarmSyncProtocol.LAN_UDP_PORT))
                    } catch (_: Exception) {}
                }
            }
            Log.i(TAG, "Sent 3x Wi-Fi LAN UDP alarm-ACK burst to $targets")
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending LAN UDP alarm ACK: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun startTemporaryNightscoutAckPoller(context: Context, settings: UserSettings) {
        nightscoutFastPollJob?.cancel()
        nightscoutFastPollJob = scope.launch {
            val deadline = System.currentTimeMillis() + ACTIVE_ALARM_LISTEN_WINDOW_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(NIGHTSCOUT_ACTIVE_POLL_INTERVAL_MS)
                val currentSettings = loadCurrentSettings(context) ?: settings
                if (!currentSettings.alertSettings.isAlarmSyncEnabled) break
                val res = NightscoutUploadManager.fetchRecentAckNotes(currentSettings.nightscoutSettings, count = 8)
                res.getOrNull()?.forEach { note ->
                    processTreatmentNote(context, note, currentSettings, AlarmSyncTransport.NIGHTSCOUT)
                }
            }
        }
    }

    private suspend fun loadCurrentSettings(context: Context): UserSettings? {
        return try {
            (context.applicationContext as? TirupApplication)?.settingsRepository?.getSettings()?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun hasBleScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
