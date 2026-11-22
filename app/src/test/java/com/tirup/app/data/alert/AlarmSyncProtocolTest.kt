package com.tirup.app.data.alert

import com.tirup.app.data.ble.BlePacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSyncProtocolTest {

    @Test
    fun bleAckPacket_roundTripAndBackwardCompatibilityWithLegacyCodec() {
        val pin = "WKV"
        val packet = AlarmAckPacket(
            scopeMask = AlarmSyncScope.HYPO or AlarmSyncScope.PREDICTIVE,
            ackTimestampSec = 1_795_390_000L,
            snoozeMinutes = 15,
            isFromMaster = true,
            isRelayed = false,
            isCriticalHypo = true,
            senderId = 42
        )

        val encoded = AlarmSyncProtocol.encodeBleAckPacket(packet, pin)
        assertEquals(AlarmSyncProtocol.BLE_ACK_PACKET_SIZE, encoded.size)
        // Magic "TU" preserved for existing Android hardware ScanFilters
        assertEquals(0x54.toByte(), encoded[0])
        assertEquals(0x55.toByte(), encoded[1])

        // Legacy v1.0.7 glucose telemetry decoder MUST safely reject ACK packets
        assertNull(BlePacketCodec.decodePacket(encoded, pin))

        // New decoder parses cleanly with matching PIN
        val decoded = AlarmSyncProtocol.decodeBleAckPacket(encoded, pin)
        assertNotNull(decoded)
        assertEquals(packet.scopeMask, decoded!!.scopeMask)
        assertEquals(packet.ackTimestampSec, decoded.ackTimestampSec)
        assertEquals(15, decoded.snoozeMinutes)
        assertTrue(decoded.isFromMaster)
        assertFalse(decoded.isRelayed)
        assertTrue(decoded.isCriticalHypo)
        assertEquals(42, decoded.senderId)

        // Wrong PIN rejected
        assertNull(AlarmSyncProtocol.decodeBleAckPacket(encoded, "ABC"))

        // Corrupted CRC rejected
        val corrupted = encoded.copyOf()
        corrupted[5] = (corrupted[5].toInt() xor 0xFF).toByte()
        assertNull(AlarmSyncProtocol.decodeBleAckPacket(corrupted, pin))
    }

    @Test
    fun bleAckDecoder_ignoresNormalGlucoseTelemetryPackets() {
        val pin = "TRP"
        val telemetryBytes = BlePacketCodec.encodePacket(
            timestampMs = 1_795_390_000_000L,
            valueMmol = 5.6,
            trendArrow = "→",
            rateOfChangeMmolPerMin = 0.0,
            iob = 1.2,
            batteryPercent = 85,
            pin = pin,
            cob = 12.0
        )

        // Telemetry decoder succeeds, ACK decoder safely ignores it
        assertNotNull(BlePacketCodec.decodePacket(telemetryBytes, pin))
        assertNull(AlarmSyncProtocol.decodeBleAckPacket(telemetryBytes, pin))
    }

    @Test
    fun lanUdpAndNightscoutTextAck_authenticatesAndRoundTrips() {
        val packet = AlarmAckPacket(
            scopeMask = AlarmSyncScope.HYPER,
            ackTimestampSec = 1_795_390_120L,
            snoozeMinutes = 45,
            isFromMaster = false,
            isRelayed = true,
            isCriticalHypo = false,
            senderId = 199
        )

        val udpBytes = AlarmSyncProtocol.encodeLanUdpAck(packet, "WKV")
        val decodedValid = AlarmSyncProtocol.decodeLanUdpAck(
            data = udpBytes,
            expectedSecrets = listOf("WKV", "myApiSecret")
        )
        assertNotNull(decodedValid)
        assertEquals(AlarmSyncScope.HYPER, decodedValid!!.scopeMask)
        assertEquals(45, decodedValid.snoozeMinutes)
        assertEquals(1_795_390_120L, decodedValid.ackTimestampSec)
        assertFalse(decodedValid.isFromMaster)
        assertTrue(decodedValid.isRelayed)
        assertFalse(decodedValid.isCriticalHypo)
        assertEquals(199, decodedValid.senderId)

        // Mismatched secret rejected
        assertNull(
            AlarmSyncProtocol.decodeLanUdpAck(
                data = udpBytes,
                expectedSecrets = listOf("ZZZ")
            )
        )

        // Nightscout note embedded inside text decodes cleanly
        val note = AlarmSyncProtocol.encodeTextAck(packet, "myApiSecret")
        val decodedNote = AlarmSyncProtocol.decodeTextAck(note, expectedSecrets = listOf("myApiSecret"))
        assertNotNull(decodedNote)
        assertEquals(packet.dedupKey, decodedNote!!.dedupKey)
    }

    @Test
    fun freshnessAndCriticalHypoSafetyPolicy() {
        val nowSec = 1_795_390_500L
        val fresh = AlarmAckPacket(AlarmSyncScope.ALL, nowSec - 15L)
        val stale = AlarmAckPacket(AlarmSyncScope.ALL, nowSec - 900L) // 15 min old
        val farFuture = AlarmAckPacket(AlarmSyncScope.ALL, nowSec + 500L)

        assertTrue(AlarmSyncProtocol.isAckFresh(fresh, nowSec))
        assertFalse(AlarmSyncProtocol.isAckFresh(stale, nowSec))
        assertFalse(AlarmSyncProtocol.isAckFresh(farFuture, nowSec))

        // Master + active critical hypo + protection ON (allowRemote=false) -> protected (true)
        assertTrue(
            AlarmSyncProtocol.shouldProtectMasterCriticalHypo(
                isDeviceMaster = true,
                isLocalCriticalHypoActive = true,
                allowRemoteCriticalHypoDismissal = false
            )
        )

        // Master + active critical hypo + user enabled optional remote dismissal -> NOT protected (false)
        assertFalse(
            AlarmSyncProtocol.shouldProtectMasterCriticalHypo(
                isDeviceMaster = true,
                isLocalCriticalHypoActive = true,
                allowRemoteCriticalHypoDismissal = true
            )
        )

        // Follower device -> ALWAYS accepts remote dismissal even during critical hypo
        assertFalse(
            AlarmSyncProtocol.shouldProtectMasterCriticalHypo(
                isDeviceMaster = false,
                isLocalCriticalHypoActive = true,
                allowRemoteCriticalHypoDismissal = false
            )
        )
    }
}
