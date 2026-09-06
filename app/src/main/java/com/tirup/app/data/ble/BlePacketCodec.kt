package com.tirup.app.data.ble

import com.tirup.app.domain.model.BleGlucosePacket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object BlePacketCodec {

    const val MANUFACTURER_ID = 0x5455 // "TU" (TIRUp)
    private const val PACKET_SIZE = 16
    private const val MAGIC_BYTE_1: Byte = 0x54 // 'T'
    private const val MAGIC_BYTE_2: Byte = 0x55 // 'U'

    /**
     * Serializes telemetry into a compact 16-byte payload for BLE advertising Manufacturer Data.
     */
    fun encodePacket(
        timestampMs: Long,
        valueMmol: Double,
        trendArrow: String,
        rateOfChangeMmolPerMin: Double,
        iob: Double,
        batteryPercent: Int,
        pin: String
    ): ByteArray {
        val buffer = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)

        // 0..1: Magic ("TU")
        buffer.put(MAGIC_BYTE_1)
        buffer.put(MAGIC_BYTE_2)

        // 2..3: Family PIN (3 uppercase letters encoded as UInt16, 0..17575)
        val pinCode = encodePinToShort(pin)
        buffer.putShort(pinCode)

        // 4..7: Timestamp in seconds (UInt32)
        val epochSeconds = (timestampMs / 1000L).toInt()
        buffer.putInt(epochSeconds)

        // 8..9: Glucose value (mmol/L * 100, e.g. 5.50 -> 550)
        val glucoseRaw = (valueMmol * 100.0).roundToInt().coerceIn(0, 65535)
        buffer.putShort(glucoseRaw.toShort())

        // 10: Trend Arrow (upper 4 bits) + Rate of Change (lower 4 bits)
        val arrowCode = encodeArrow(trendArrow)
        // Rate of change mapped: -0.35..+0.35 mmol/L/min into 0..15 with midpoint 7
        val rateCode = ((rateOfChangeMmolPerMin * 20.0).roundToInt() + 7).coerceIn(0, 15)
        val trendByte = ((arrowCode shl 4) or rateCode).toByte()
        buffer.put(trendByte)

        // 11..12: IoB (units * 100, e.g. 1.50 -> 150)
        val iobRaw = (iob.coerceAtLeast(0.0) * 100.0).roundToInt().coerceIn(0, 65535)
        buffer.putShort(iobRaw.toShort())

        // 13: Battery (0..100%, 255 if unknown/disabled)
        val batByte = if (batteryPercent in 0..100) batteryPercent.toByte() else 255.toByte()
        buffer.put(batByte)

        // 14: Flags / Reserved
        buffer.put(0.toByte())

        // 15: CRC-8 over bytes 0..14
        val bytes = buffer.array()
        val crc = calculateCrc8(bytes, 0, PACKET_SIZE - 1)
        bytes[PACKET_SIZE - 1] = crc

        return bytes
    }

    /**
     * Deserializes and validates a 16-byte payload from BLE advertising.
     * Returns null if payload is invalid, corrupted (CRC error), or PIN does not match.
     */
    fun decodePacket(data: ByteArray?, expectedPin: String): BleGlucosePacket? {
        if (data == null || data.size < PACKET_SIZE) return null

        // 1. Verify CRC-8
        val expectedCrc = data[PACKET_SIZE - 1]
        val computedCrc = calculateCrc8(data, 0, PACKET_SIZE - 1)
        if (expectedCrc != computedCrc) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // 2. Verify Magic
        val b0 = buffer.get()
        val b1 = buffer.get()
        if (b0 != MAGIC_BYTE_1 || b1 != MAGIC_BYTE_2) return null

        // 3. Verify Family PIN
        val packetPin = buffer.short
        val myPin = encodePinToShort(expectedPin)
        if (packetPin != myPin) return null

        // 4. Timestamp (seconds -> ms)
        val epochSeconds = buffer.int.toLong() and 0xFFFFFFFFL
        val timestampMs = epochSeconds * 1000L

        // 5. Glucose
        val glucoseRaw = buffer.short.toInt() and 0xFFFF
        val valueMmol = glucoseRaw / 100.0

        // 6. Trend & Rate of Change
        val trendByte = buffer.get().toInt() and 0xFF
        val arrowCode = (trendByte shr 4) and 0x0F
        val rateCode = trendByte and 0x0F
        val arrow = decodeArrow(arrowCode)
        val rateOfChange = (rateCode - 7) / 20.0

        // 7. IoB
        val iobRaw = buffer.short.toInt() and 0xFFFF
        val iob = iobRaw / 100.0

        // 8. Battery
        val batRaw = buffer.get().toInt() and 0xFF
        val battery = if (batRaw <= 100) batRaw else -1

        return BleGlucosePacket(
            timestamp = timestampMs,
            valueMmol = valueMmol,
            trendArrow = arrow,
            rateOfChangeMmolPerMin = rateOfChange,
            iob = iob,
            batteryPercent = battery
        )
    }

    private fun encodeArrow(arrow: String): Int {
        return when (arrow) {
            "⇈", "DoubleUp", "↑↑" -> 1
            "↑", "SingleUp" -> 2
            "↗", "FortyFiveUp" -> 3
            "→", "Flat" -> 4
            "↘", "FortyFiveDown" -> 5
            "↓", "SingleDown" -> 6
            "⇊", "DoubleDown", "↓↓" -> 7
            else -> 0
        }
    }

    private fun decodeArrow(code: Int): String {
        return when (code) {
            1 -> "⇈"
            2 -> "↑"
            3 -> "↗"
            4 -> "→"
            5 -> "↘"
            6 -> "↓"
            7 -> "⇊"
            else -> "→"
        }
    }

    /**
     * Bijective encoding of 3 uppercase letters ('A'..'Z') into a 16-bit Short (0..17575).
     * 26 * 26 * 26 = 17,576 combinations.
     */
    fun encodePinToShort(pin: String): Short {
        val clean = pin.uppercase().filter { it in 'A'..'Z' }.padEnd(3, 'A').take(3)
        val code = (clean[0] - 'A') * 676 + (clean[1] - 'A') * 26 + (clean[2] - 'A')
        return code.toShort()
    }

    /**
     * Bijective decoding of 16-bit Short back to 3 uppercase letters ('A'..'Z').
     */
    fun decodeShortToPin(codeShort: Short): String {
        val code = codeShort.toInt() and 0xFFFF
        val c0 = 'A' + (code / 676).coerceIn(0, 25)
        val c1 = 'A' + ((code / 26) % 26).coerceIn(0, 25)
        val c2 = 'A' + (code % 26).coerceIn(0, 25)
        return "$c0$c1$c2"
    }

    /**
     * Generates a completely random 3-letter uppercase PIN (e.g. "WKV", "TRP").
     */
    fun generateRandomPin(): String {
        return (1..3).map { ('A'..'Z').random() }.joinToString("")
    }

    /**
     * Standard CRC-8 (Polynomial 0x07, Initial 0x00).
     */
    fun calculateCrc8(bytes: ByteArray, offset: Int, length: Int): Byte {
        var crc = 0x00
        for (i in offset until offset + length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if ((crc and 0x80) != 0) {
                    (crc shl 1) xor 0x07
                } else {
                    crc shl 1
                }
                crc = crc and 0xFF
            }
        }
        return crc.toByte()
    }
}
