package com.tirup.app.data.ble

import com.tirup.app.domain.model.LabHba1cRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class Hba1cJournalCodecTest {

    @Test
    fun testRoundTripEncodeAndDecodeWithCyrillic() {
        val records = listOf(
            LabHba1cRecord(
                id = 1001L,
                timestamp = 1_760_000_000_000L,
                valuePercent = 6.2,
                labName = "Инвитро",
                notes = "Натощак, венозная кровь"
            ),
            LabHba1cRecord(
                id = 1002L,
                timestamp = 1_752_000_000_000L,
                valuePercent = 6.8,
                labName = "Гемотест",
                notes = ""
            )
        )

        val frame = Hba1cJournalCodec.encodeFrame(records)
        val header = Hba1cJournalCodec.parseHeader(frame)
        assertNotNull(header)
        assertEquals(frame.size - Hba1cJournalCodec.HEADER_SIZE, header!!.first)

        val decoded = Hba1cJournalCodec.decodeFrame(frame)
        assertTrue(decoded.isSuccess)
        val list = decoded.getOrThrow()
        assertEquals(2, list.size)
        assertEquals(6.2, list[0].valuePercent, 0.001)
        assertEquals("Инвитро", list[0].labName)
        assertEquals("Натощак, венозная кровь", list[0].notes)
        assertEquals(6.8, list[1].valuePercent, 0.001)
        assertEquals("Гемотест", list[1].labName)
    }

    @Test
    fun testCorruptedCrcIsRejected() {
        val frame = Hba1cJournalCodec.encodeFrame(
            listOf(LabHba1cRecord(id = 1L, timestamp = 1_760_000_000_000L, valuePercent = 5.9))
        )
        frame[frame.size - 1] = (frame[frame.size - 1].toInt() xor 0xFF).toByte()
        assertTrue(Hba1cJournalCodec.decodeFrame(frame).isFailure)
    }

    @Test
    fun testBadMagicOrTruncatedFrameIsRejected() {
        assertNull(Hba1cJournalCodec.parseHeader(byteArrayOf(0x00, 0x01, 0, 0, 0, 5, 0, 0, 0, 0)))
        val frame = Hba1cJournalCodec.encodeFrame(
            listOf(LabHba1cRecord(id = 1L, timestamp = 1_760_000_000_000L, valuePercent = 6.1))
        )
        val truncated = frame.copyOfRange(0, frame.size - 5)
        assertTrue(Hba1cJournalCodec.decodeFrame(truncated).isFailure)
    }

    @Test
    fun testMergeRecordsDeduplicatesAndEnrichesMetadata() {
        val day1Morning = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day1Evening = day1Morning + 6 * 3_600_000L
        val day2 = day1Morning + 90L * 86_400_000L

        val existing = listOf(
            LabHba1cRecord(id = 10L, timestamp = day1Morning, valuePercent = 6.4, labName = "", notes = "")
        )
        val incoming = listOf(
            // Same calendar day & same value, but with lab name -> duplicate, should enrich labName
            LabHba1cRecord(id = 99L, timestamp = day1Evening, valuePercent = 6.4, labName = "Инвитро", notes = "Вена"),
            // New newer record -> should be added and sorted first
            LabHba1cRecord(id = 100L, timestamp = day2, valuePercent = 6.0, labName = "KDL", notes = "Отлично")
        )

        val result = Hba1cJournalCodec.mergeRecords(existing, incoming)
        assertEquals(1, result.addedCount)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(2, result.mergedRecords.size)
        // Sorted descending by timestamp
        assertEquals(6.0, result.mergedRecords[0].valuePercent, 0.001)
        assertEquals("KDL", result.mergedRecords[0].labName)
        // Existing record enriched with incoming metadata
        assertEquals(6.4, result.mergedRecords[1].valuePercent, 0.001)
        assertEquals("Инвитро", result.mergedRecords[1].labName)
        assertEquals("Вена", result.mergedRecords[1].notes)
    }
}
