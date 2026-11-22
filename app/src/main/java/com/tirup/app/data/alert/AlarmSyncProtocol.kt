package com.tirup.app.data.alert

import com.tirup.app.data.ble.BlePacketCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Bitmask identifying which alert categories are acknowledged/snoozed by a synchronized command.
 */
object AlarmSyncScope {
    const val HYPO = 1 shl 0
    const val HYPER = 1 shl 1
    const val PREDICTIVE = 1 shl 2
    const val RAPID_CHANGE = 1 shl 3
    const val SIGNAL_LOSS = 1 shl 4
    const val LOW_BATTERY = 1 shl 5
    const val ALL = HYPO or HYPER or PREDICTIVE or RAPID_CHANGE or SIGNAL_LOSS or LOW_BATTERY
}

data class AlarmAckPacket(
    val scopeMask: Int,
    val ackTimestampSec: Long,
    val snoozeMinutes: Int = 0, // 0 = use receiver's configured / adaptive snooze; 1..240 = explicit minutes
    val isFromMaster: Boolean = false,
    val isRelayed: Boolean = false,
    val isCriticalHypo: Boolean = false,
    val senderId: Int = 1
) {
    val ackTimestampMs: Long get() = ackTimestampSec * 1000L

    /** Unique deduplication key independent of relay hop or transport channel. */
    val dedupKey: String get() = "$ackTimestampSec:$senderId:$scopeMask:$snoozeMinutes"
}

/**
 * Pure protocol codec for universal bidirectional alarm snooze/dismissal synchronization
 * across BLE Bridge, local Wi-Fi LAN UDP broadcast, and Nightscout/xDrip treatments.
 */
object AlarmSyncProtocol {

    const val BLE_ACK_PACKET_SIZE = 14
    const val LAN_UDP_PORT = 17585
    const val NIGHTSCOUT_EVENT_TYPE = "Announcement"
    const val NOTE_PREFIX = "TIRUP_ACK|v1|"
    const val MAX_ACK_AGE_SEC = 600L // 10 minutes max validity window
    const val MAX_FUTURE_SKEW_SEC = 120L // tolerate up to 2 min clock skew

    private const val MAGIC_BYTE_1: Byte = 0x54 // 'T'
    private const val MAGIC_BYTE_2: Byte = 0x55 // 'U'
    private const val CONTROL_PIN_FLAG = 0x8000 // bit 15 = 1 marks control packet (valid PINs are 0..17575 < 0x8000)
    private const val COMMAND_ALARM_ACK: Byte = 0x01

    private const val FLAG_FROM_MASTER = 1 shl 0
    private const val FLAG_RELAYED = 1 shl 1
    private const val FLAG_CRITICAL_HYPO = 1 shl 2

    private const val FALLBACK_SHARED_SECRET = "TIRUP_LOCAL_SYNC"

    /**
     * Encodes an [AlarmAckPacket] into a compact 14-byte BLE Manufacturer Data payload
     * under [BlePacketCodec.MANUFACTURER_ID] (0x5455) with magic "TU".
     *
     * Backward compatibility with TIRUp <= v1.0.7:
     * - Keeps magic "TU" so existing Android hardware ScanFilters wake up even with screen off.
     * - Sets bit 15 of the PIN Short AND uses 14 bytes (< 16 bytes), so legacy BlePacketCodec.decodePacket()
     *   safely ignores it without corrupting glucose readings or telemetry.
     */
    fun encodeBleAckPacket(packet: AlarmAckPacket, pin: String): ByteArray {
        val buffer = ByteBuffer.allocate(BLE_ACK_PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)

        // 0..1: Magic ("TU")
        buffer.put(MAGIC_BYTE_1)
        buffer.put(MAGIC_BYTE_2)

        // 2..3: Control flag (bit 15 = 1) + Family PIN (0..17575 in bits 0..14)
        val pinCode = BlePacketCodec.encodePinToShort(pin).toInt() and 0x7FFF
        buffer.putShort((CONTROL_PIN_FLAG or pinCode).toShort())

        // 4: Command ID
        buffer.put(COMMAND_ALARM_ACK)

        // 5: Alert scope bitmask
        buffer.put((packet.scopeMask and 0xFF).toByte())

        // 6..9: UTC epoch seconds (UInt32)
        buffer.putInt(packet.ackTimestampSec.toInt())

        // 10: Explicit snooze minutes (0 = default adaptive snooze, 1..240 = explicit)
        buffer.put(packet.snoozeMinutes.coerceIn(0, 240).toByte())

        // 11: Flags
        var flags = 0
        if (packet.isFromMaster) flags = flags or FLAG_FROM_MASTER
        if (packet.isRelayed) flags = flags or FLAG_RELAYED
        if (packet.isCriticalHypo) flags = flags or FLAG_CRITICAL_HYPO
        buffer.put(flags.toByte())

        // 12: Sender ID (1..255)
        buffer.put(packet.senderId.coerceIn(1, 255).toByte())

        // 13: CRC-8 over bytes 0..12
        val bytes = buffer.array()
        bytes[BLE_ACK_PACKET_SIZE - 1] = BlePacketCodec.calculateCrc8(bytes, 0, BLE_ACK_PACKET_SIZE - 1)
        return bytes
    }

    /**
     * Decodes and validates a 14-byte BLE Alarm ACK payload.
     * Returns null if payload is a telemetry packet, corrupted (CRC mismatch), wrong command, or PIN mismatch.
     */
    fun decodeBleAckPacket(data: ByteArray?, expectedPin: String): AlarmAckPacket? {
        if (data == null || data.size != BLE_ACK_PACKET_SIZE) return null
        val cleanPin = expectedPin.uppercase().filter { it in 'A'..'Z' }
        if (cleanPin.length != 3) return null

        // 1. Verify CRC-8
        val expectedCrc = data[BLE_ACK_PACKET_SIZE - 1]
        val computedCrc = BlePacketCodec.calculateCrc8(data, 0, BLE_ACK_PACKET_SIZE - 1)
        if (expectedCrc != computedCrc) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // 2. Verify Magic "TU"
        if (buffer.get() != MAGIC_BYTE_1 || buffer.get() != MAGIC_BYTE_2) return null

        // 3. Verify Control flag + Family PIN
        val rawPinWord = buffer.short.toInt() and 0xFFFF
        if ((rawPinWord and CONTROL_PIN_FLAG) == 0) return null
        val packetPin = rawPinWord and 0x7FFF
        val myPin = BlePacketCodec.encodePinToShort(cleanPin).toInt() and 0x7FFF
        if (packetPin != myPin) return null

        // 4. Command ID
        if (buffer.get() != COMMAND_ALARM_ACK) return null

        val scopeMask = buffer.get().toInt() and 0xFF
        val ackTimestampSec = buffer.int.toLong() and 0xFFFFFFFFL
        val snoozeMinutes = (buffer.get().toInt() and 0xFF).coerceIn(0, 240)
        val flags = buffer.get().toInt() and 0xFF
        val senderId = (buffer.get().toInt() and 0xFF).coerceIn(1, 255)

        if (scopeMask == 0 || ackTimestampSec <= 0L) return null

        return AlarmAckPacket(
            scopeMask = scopeMask,
            ackTimestampSec = ackTimestampSec,
            snoozeMinutes = snoozeMinutes,
            isFromMaster = (flags and FLAG_FROM_MASTER) != 0,
            isRelayed = (flags and FLAG_RELAYED) != 0,
            isCriticalHypo = (flags and FLAG_CRITICAL_HYPO) != 0,
            senderId = senderId
        )
    }

    /**
     * Encodes an [AlarmAckPacket] into a compact UTF-8 text payload used both for local Wi-Fi LAN UDP
     * datagrams and Nightscout/xDrip Announcement notes.
     * Format: TIRUP_ACK|v1|<scopeMask>|<snoozeMin>|<ackTsSec>|<senderId>|<flags>|<authTag8Hex>
     */
    fun encodeTextAck(packet: AlarmAckPacket, sharedSecret: String = ""): String {
        var flags = 0
        if (packet.isFromMaster) flags = flags or FLAG_FROM_MASTER
        if (packet.isRelayed) flags = flags or FLAG_RELAYED
        if (packet.isCriticalHypo) flags = flags or FLAG_CRITICAL_HYPO

        val body = "${packet.scopeMask and 0xFF}|" +
            "${packet.snoozeMinutes.coerceIn(0, 240)}|" +
            "${packet.ackTimestampSec}|" +
            "${packet.senderId.coerceIn(1, 255)}|" +
            "$flags"
        val authTag = computeAuthTag(body, sharedSecret)
        return "$NOTE_PREFIX$body|$authTag"
    }

    /**
     * Decodes a text ACK payload (from UDP datagram or Nightscout/xDrip treatment notes).
     * When [expectedSecrets] contains non-blank secrets (e.g. Family PIN, LAN API Secret, Nightscout API Secret),
     * verifies that the auth tag matches at least one configured secret (or fallback secret if [allowUnauthenticatedFallback]).
     */
    fun decodeTextAck(
        text: String?,
        expectedSecrets: List<String> = emptyList(),
        allowUnauthenticatedFallback: Boolean = false
    ): AlarmAckPacket? {
        if (text.isNullOrBlank()) return null
        val startIdx = text.indexOf(NOTE_PREFIX)
        if (startIdx < 0) return null

        val rawLine = text.substring(startIdx + NOTE_PREFIX.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?: return null

        val parts = rawLine.split("|")
        if (parts.size < 6) return null

        val scopeMask = parts[0].toIntOrNull()?.and(0xFF) ?: return null
        val snoozeMinutes = parts[1].toIntOrNull()?.coerceIn(0, 240) ?: return null
        val ackTimestampSec = parts[2].toLongOrNull() ?: return null
        val senderId = parts[3].toIntOrNull()?.coerceIn(1, 255) ?: return null
        val flags = parts[4].toIntOrNull() ?: return null
        val providedTag = parts[5].trim().lowercase()

        if (scopeMask == 0 || ackTimestampSec <= 0L || providedTag.length != 8) return null

        val body = "${parts[0]}|${parts[1]}|${parts[2]}|${parts[3]}|${parts[4]}"
        val normalizedSecrets = expectedSecrets.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val authValid = if (normalizedSecrets.isNotEmpty()) {
            normalizedSecrets.any { secret -> computeAuthTag(body, secret) == providedTag } ||
                (allowUnauthenticatedFallback && computeAuthTag(body, "") == providedTag)
        } else {
            computeAuthTag(body, "") == providedTag
        }

        if (!authValid) return null

        return AlarmAckPacket(
            scopeMask = scopeMask,
            ackTimestampSec = ackTimestampSec,
            snoozeMinutes = snoozeMinutes,
            isFromMaster = (flags and FLAG_FROM_MASTER) != 0,
            isRelayed = (flags and FLAG_RELAYED) != 0,
            isCriticalHypo = (flags and FLAG_CRITICAL_HYPO) != 0,
            senderId = senderId
        )
    }

    fun encodeLanUdpAck(packet: AlarmAckPacket, sharedSecret: String): ByteArray {
        return encodeTextAck(packet, sharedSecret).toByteArray(Charsets.UTF_8)
    }

    fun decodeLanUdpAck(
        data: ByteArray?,
        length: Int = data?.size ?: 0,
        expectedSecrets: List<String>
    ): AlarmAckPacket? {
        if (data == null || length <= 0 || length > data.size) return null
        val str = String(data, 0, length, Charsets.UTF_8)
        return decodeTextAck(
            text = str,
            expectedSecrets = expectedSecrets,
            allowUnauthenticatedFallback = expectedSecrets.none { it.isNotBlank() }
        )
    }

    /**
     * Validates that an incoming ACK timestamp is fresh enough to silence an active alarm
     * and not an old/replayed broadcast from a previous episode.
     */
    fun isAckFresh(packet: AlarmAckPacket, nowSec: Long): Boolean {
        if (packet.ackTimestampSec <= 0L) return false
        val ageSec = nowSec - packet.ackTimestampSec
        return ageSec in -MAX_FUTURE_SKEW_SEC..MAX_ACK_AGE_SEC
    }

    /**
     * Clinical safety gate: when a patient's Master phone is actively sounding a Critical Hypo alarm (<3.0 mmol/L
     * or prolonged hypo with Emergency SMS countdown), remote followers can only silence it on Master
     * if the patient explicitly enabled [allowRemoteCriticalHypoDismissal].
     */
    fun shouldProtectMasterCriticalHypo(
        isDeviceMaster: Boolean,
        isLocalCriticalHypoActive: Boolean,
        allowRemoteCriticalHypoDismissal: Boolean
    ): Boolean {
        return isDeviceMaster && isLocalCriticalHypoActive && !allowRemoteCriticalHypoDismissal
    }

    private fun computeAuthTag(body: String, secret: String): String {
        val key = secret.trim().uppercase().ifEmpty { FALLBACK_SHARED_SECRET }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$body|$key".toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
