package com.tirup.app.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePacketCodecTest {

    @Test
    fun testEncodeAndDecodeRoundtripSuccess() {
        val now = 1725580800000L // rounded to seconds
        val pin = "TRP"

        val encoded = BlePacketCodec.encodePacket(
            timestampMs = now,
            valueMmol = 6.42,
            trendArrow = "⇈",
            rateOfChangeMmolPerMin = 0.15,
            iob = 2.35,
            batteryPercent = 88,
            pin = pin
        )

        assertEquals(16, encoded.size)

        val packet = BlePacketCodec.decodePacket(encoded, expectedPin = "TRP")
        assertNotNull(packet)
        assertEquals(now, packet!!.timestamp)
        assertEquals(6.42, packet.valueMmol, 0.01)
        assertEquals("⇈", packet.trendArrow)
        assertEquals(0.15, packet.rateOfChangeMmolPerMin, 0.05)
        assertEquals(2.35, packet.iob, 0.01)
        assertEquals(88, packet.batteryPercent)
    }

    @Test
    fun testWrongPinReturnsNull() {
        val encoded = BlePacketCodec.encodePacket(
            timestampMs = 1725580800000L,
            valueMmol = 5.5,
            trendArrow = "→",
            rateOfChangeMmolPerMin = 0.0,
            iob = 0.0,
            batteryPercent = 50,
            pin = "TRP"
        )

        val packet = BlePacketCodec.decodePacket(encoded, expectedPin = "SKY")
        assertNull(packet)
    }

    @Test
    fun testCorruptedByteFailsCrc() {
        val encoded = BlePacketCodec.encodePacket(
            timestampMs = 1725580800000L,
            valueMmol = 7.8,
            trendArrow = "↘",
            rateOfChangeMmolPerMin = -0.05,
            iob = 1.2,
            batteryPercent = 75,
            pin = "ABC"
        )

        // Corrupt a byte
        encoded[8] = (encoded[8].toInt() xor 0xFF).toByte()

        val packet = BlePacketCodec.decodePacket(encoded, expectedPin = "ABC")
        assertNull(packet)
    }

    @Test
    fun testDoubleDownArrowAndNegativeRate() {
        val now = 1725580800000L
        val encoded = BlePacketCodec.encodePacket(
            timestampMs = now,
            valueMmol = 3.9,
            trendArrow = "⇊",
            rateOfChangeMmolPerMin = -0.25,
            iob = 4.5,
            batteryPercent = 100,
            pin = "XYZ"
        )

        val packet = BlePacketCodec.decodePacket(encoded, expectedPin = "XYZ")
        assertNotNull(packet)
        assertEquals("⇊", packet!!.trendArrow)
        assertEquals(3.9, packet.valueMmol, 0.01)
        assertEquals(-0.25, packet.rateOfChangeMmolPerMin, 0.05)
        assertEquals(4.5, packet.iob, 0.01)
        assertEquals(100, packet.batteryPercent)
    }

    @Test
    fun testBatteryDisabledReturnsMinusOne() {
        val encoded = BlePacketCodec.encodePacket(
            timestampMs = 1725580800000L,
            valueMmol = 5.0,
            trendArrow = "→",
            rateOfChangeMmolPerMin = 0.0,
            iob = 0.0,
            batteryPercent = -1,
            pin = "TRP"
        )

        val packet = BlePacketCodec.decodePacket(encoded, expectedPin = "TRP")
        assertNotNull(packet)
        assertEquals(-1, packet!!.batteryPercent)
    }

    @Test
    fun testPinEncodingBijection() {
        val testPins = listOf("AAA", "ZZZ", "TRP", "SKY", "ABC", "WKV", "GLU")
        for (p in testPins) {
            val encoded = BlePacketCodec.encodePinToShort(p)
            val decoded = BlePacketCodec.decodeShortToPin(encoded)
            assertEquals("Mismatch for pin $p", p, decoded)
        }
    }

    @Test
    fun testGenerateRandomPin() {
        repeat(20) {
            val pin = BlePacketCodec.generateRandomPin()
            assertEquals(3, pin.length)
            assertTrue("PIN $pin must contain only A-Z", pin.all { it in 'A'..'Z' })
        }
    }
}
