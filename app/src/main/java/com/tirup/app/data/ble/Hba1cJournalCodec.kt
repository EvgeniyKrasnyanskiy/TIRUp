package com.tirup.app.data.ble

import com.tirup.app.domain.model.LabHba1cRecord
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.zip.CRC32
import kotlin.math.abs

data class Hba1cMergeResult(
    val mergedRecords: List<LabHba1cRecord>,
    val addedCount: Int,
    val skippedDuplicates: Int
)

/**
 * Compact framed serializer and deduplicating merger for transferring HbA1c lab journal
 * records directly between two TIRUp instances over BLE GATT without touching other app data.
 *
 * Wire Frame (Big-Endian):
 * - Bytes 0..1 : Magic "HJ" (0x48, 0x4A)
 * - Bytes 2..5 : UTF-8 JSON payload byte length (Int)
 * - Bytes 6..9 : CRC-32 checksum of the UTF-8 JSON payload (Int)
 * - Bytes 10.. : UTF-8 JSON envelope {"type":"hba1c_journal","v":1,"records":[...]}
 */
object Hba1cJournalCodec {

    const val HEADER_SIZE = 10
    private const val MAGIC_0: Byte = 0x48 // 'H'
    private const val MAGIC_1: Byte = 0x4A // 'J'
    private const val MAX_PAYLOAD_BYTES = 128 * 1024 // 128 KB sanity cap

    fun encodeFrame(records: List<LabHba1cRecord>): ByteArray {
        val arr = JSONArray()
        for (rec in records) {
            val obj = JSONObject().apply {
                put("id", rec.id)
                put("ts", rec.timestamp)
                put("val", rec.valuePercent)
                put("lab", rec.labName)
                put("notes", rec.notes)
            }
            arr.put(obj)
        }
        val root = JSONObject().apply {
            put("type", "hba1c_journal")
            put("v", 1)
            put("records", arr)
        }
        val payload = root.toString().toByteArray(Charsets.UTF_8)
        val crc = computeCrc32(payload)

        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_0)
        buf.put(MAGIC_1)
        buf.putInt(payload.size)
        buf.putInt(crc)
        buf.put(payload)
        return buf.array()
    }

    /**
     * Parses the 10-byte header and returns Pair(payloadLength, expectedCrc32), or null if invalid.
     */
    fun parseHeader(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < HEADER_SIZE) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) return null
        val buf = ByteBuffer.wrap(bytes, 2, 8).order(ByteOrder.BIG_ENDIAN)
        val length = buf.int
        val crc = buf.int
        if (length < 2 || length > MAX_PAYLOAD_BYTES) return null
        return Pair(length, crc)
    }

    fun decodeFrame(frame: ByteArray): Result<List<LabHba1cRecord>> {
        val header = parseHeader(frame)
            ?: return Result.failure(IllegalArgumentException("Invalid HbA1c transfer header"))
        val (payloadLen, expectedCrc) = header
        val expectedTotal = HEADER_SIZE + payloadLen
        if (frame.size < expectedTotal) {
            return Result.failure(IllegalArgumentException("Truncated HbA1c frame: ${frame.size} < $expectedTotal"))
        }

        val payload = frame.copyOfRange(HEADER_SIZE, expectedTotal)
        val actualCrc = computeCrc32(payload)
        if (actualCrc != expectedCrc) {
            return Result.failure(IllegalArgumentException("CRC-32 mismatch in HbA1c transfer"))
        }

        return try {
            val root = JSONObject(String(payload, Charsets.UTF_8))
            if (root.optString("type") != "hba1c_journal") {
                return Result.failure(IllegalArgumentException("Unsupported payload type"))
            }
            val arr = root.optJSONArray("records") ?: JSONArray()
            val parsed = mutableListOf<LabHba1cRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val value = obj.optDouble("val", Double.NaN)
                val ts = obj.optLong("ts", 0L)
                if (value.isNaN() || value <= 0.0 || value > 30.0 || ts <= 0L) continue
                val id = obj.optLong("id", ts).takeIf { it > 0L } ?: ts
                parsed.add(
                    LabHba1cRecord(
                        id = id,
                        timestamp = ts,
                        valuePercent = value,
                        labName = obj.optString("lab", "").trim(),
                        notes = obj.optString("notes", "").trim()
                    )
                )
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merges incoming HbA1c records into existing records without overwriting existing history.
     * Records matching the same ID or the same calendar day with the same % value (within 0.05%)
     * are treated as duplicates; blank lab/notes fields on an existing duplicate are enriched if present.
     */
    fun mergeRecords(
        existing: List<LabHba1cRecord>,
        incoming: List<LabHba1cRecord>
    ): Hba1cMergeResult {
        val result = existing.toMutableList()
        var added = 0
        var skipped = 0

        for (inc in incoming) {
            val dupIndex = result.indexOfFirst { ex ->
                ex.id == inc.id || (isSameCalendarDay(ex.timestamp, inc.timestamp) && abs(ex.valuePercent - inc.valuePercent) < 0.05)
            }
            if (dupIndex >= 0) {
                val ex = result[dupIndex]
                val enrichedLab = ex.labName.ifBlank { inc.labName }
                val enrichedNotes = ex.notes.ifBlank { inc.notes }
                if (enrichedLab != ex.labName || enrichedNotes != ex.notes) {
                    result[dupIndex] = ex.copy(labName = enrichedLab, notes = enrichedNotes)
                }
                skipped++
            } else {
                // Ensure unique ID if colliding with another record
                val uniqueId = if (result.any { it.id == inc.id }) {
                    System.currentTimeMillis() + added + 1L
                } else {
                    inc.id
                }
                result.add(inc.copy(id = uniqueId))
                added++
            }
        }

        val sorted = result.sortedByDescending { it.timestamp }
        return Hba1cMergeResult(
            mergedRecords = sorted,
            addedCount = added,
            skippedDuplicates = skipped
        )
    }

    private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun computeCrc32(bytes: ByteArray): Int {
        val crc32 = CRC32()
        crc32.update(bytes)
        return crc32.value.toInt()
    }
}
