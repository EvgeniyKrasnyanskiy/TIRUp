package com.tirup.app.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.domain.repository.GlucoseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

class StreamingGlucoseImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: GlucoseRepository
) {

    suspend fun importFromUri(
        uri: Uri,
        onProgress: (importedCount: Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalImported = 0
        var minTimestamp = Long.MAX_VALUE
        var maxTimestamp = Long.MIN_VALUE

        try {
            val rawInputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for URI: $uri"))

            val bufferedIn = BufferedInputStream(rawInputStream, 65536)
            bufferedIn.mark(16)
            val headerBytes = ByteArray(4)
            val bytesRead = bufferedIn.read(headerBytes, 0, 4)
            bufferedIn.reset()

            val isZip = bytesRead == 4 &&
                    headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()

            val chunk = ArrayList<GlucoseReadingEntity>(CHUNK_SIZE)

            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US),
                SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US),
                SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US),
                SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.US),
                SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            )

            if (isZip) {
                Log.d(TAG, "Detected ZIP archive. Streaming entries...")
                val zipIn = ZipInputStream(bufferedIn)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && (entryName.endsWith(".csv") || entryName.endsWith(".tsv") || entryName.endsWith(".txt") || entryName.endsWith(".dat") || !entryName.contains("."))) {
                        Log.d(TAG, "Processing archive entry: ${entry.name}")
                        val count = processStream(zipIn, chunk, dateFormats, onProgress) { ts ->
                            if (ts < minTimestamp) minTimestamp = ts
                            if (ts > maxTimestamp) maxTimestamp = ts
                        }
                        totalImported += count
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            } else {
                Log.d(TAG, "Processing standard text/CSV stream...")
                val count = processStream(bufferedIn, chunk, dateFormats, onProgress) { ts ->
                    if (ts < minTimestamp) minTimestamp = ts
                    if (ts > maxTimestamp) maxTimestamp = ts
                }
                totalImported += count
            }

            // Flush remaining chunk
            if (chunk.isNotEmpty()) {
                insertChunk(chunk)
                totalImported += chunk.size
                chunk.clear()
                onProgress(totalImported)
            }

            // Recalculate daily summaries for the imported range
            if (totalImported > 0 && minTimestamp <= maxTimestamp) {
                repository.recalculateDailySummaries(minTimestamp, maxTimestamp)
            }

            Log.d(TAG, "Import completed. Total readings imported: $totalImported")
            Result.success(totalImported)
        } catch (e: Exception) {
            Log.e(TAG, "Streaming import failed", e)
            Result.failure(e)
        }
    }

    private suspend fun processStream(
        inputStream: InputStream,
        chunk: ArrayList<GlucoseReadingEntity>,
        dateFormats: List<SimpleDateFormat>,
        onProgress: (Int) -> Unit,
        onTimestampParsed: (Long) -> Unit
    ): Int {
        var importedInStream = 0
        val reader = BufferedReader(InputStreamReader(inputStream), 32768)

        var timestampCol = -1
        var valueCol = -1
        var fallbackValueCol = -1 // For LibreView (Historic vs Scan)
        var isMgDlHeader = false
        var headerFound = false

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line = reader.readLine()
                continue
            }

            // Delimiter detection (tab, comma, semicolon)
            val delimiter = when {
                trimmed.contains("\t") -> "\t"
                trimmed.contains(";") -> ";"
                else -> ","
            }
            val parts = trimmed.split(delimiter).map { it.trim('\"', ' ', '\'') }

            // 1. Check if line is a table header
            if (!headerFound) {
                val headerLower = parts.map { it.lowercase() }
                var tempTsCol = -1
                var tempValCol = -1
                var tempScanCol = -1
                var tempIsMgDl = false

                for (i in headerLower.indices) {
                    val h = headerLower[i]
                    if (tempTsCol == -1 && (h.contains("time") || h.contains("date") || h == "ts" || h == "timestamp" || h.contains("zeit") || h.contains("время") || h.contains("дата"))) {
                        tempTsCol = i
                    }
                    if (h.contains("historic") || (tempValCol == -1 && (h.contains("glucose") || h.contains("glukose") || h.contains("sgv") || h.contains("value") || h.contains("bg") || h.contains("сахар") || h.contains("глюкоза")))) {
                        tempValCol = i
                        if (h.contains("mg/dl") || h.contains("mgdl") || h.contains("mg")) {
                            tempIsMgDl = true
                        }
                    }
                    if (h.contains("scan")) {
                        tempScanCol = i
                    }
                }

                if (tempTsCol != -1 && tempValCol != -1) {
                    timestampCol = tempTsCol
                    valueCol = tempValCol
                    fallbackValueCol = tempScanCol
                    isMgDlHeader = tempIsMgDl
                    headerFound = true
                    line = reader.readLine()
                    continue
                }
            }

            // 2. Try parsing reading with known columns
            var reading: GlucoseReadingEntity? = null
            if (timestampCol != -1 && valueCol != -1) {
                reading = parseWithColumns(parts, timestampCol, valueCol, fallbackValueCol, isMgDlHeader, dateFormats)
            }

            // 3. Fallback heuristic: search any column for timestamp & glucose value
            if (reading == null) {
                val autoParsed = autoDetectAndParse(parts, dateFormats)
                if (autoParsed != null) {
                    val (entity, detectedTsCol, detectedValCol) = autoParsed
                    reading = entity
                    if (timestampCol == -1) {
                        timestampCol = detectedTsCol
                        valueCol = detectedValCol
                        headerFound = true
                    }
                }
            }

            if (reading != null) {
                chunk.add(reading)
                onTimestampParsed(reading.timestamp)

                if (chunk.size >= CHUNK_SIZE) {
                    insertChunk(chunk)
                    importedInStream += chunk.size
                    chunk.clear()
                    onProgress(importedInStream)
                }
            }

            line = reader.readLine()
        }

        return importedInStream
    }

    private suspend fun insertChunk(chunk: List<GlucoseReadingEntity>) {
        database.glucoseReadingDao().insertBatch(chunk)
    }

    private fun parseWithColumns(
        parts: List<String>,
        timestampCol: Int,
        valueCol: Int,
        fallbackCol: Int,
        isMgDlHeader: Boolean,
        dateFormats: List<SimpleDateFormat>
    ): GlucoseReadingEntity? {
        if (timestampCol >= parts.size) return null

        val rawTime = parts[timestampCol]
        var rawVal = if (valueCol < parts.size) parts[valueCol].replace(',', '.') else ""
        if (rawVal.isEmpty() && fallbackCol != -1 && fallbackCol < parts.size) {
            rawVal = parts[fallbackCol].replace(',', '.')
        }

        val glucoseVal = rawVal.toDoubleOrNull() ?: return null
        if (glucoseVal <= 0.0 || glucoseVal > 1000.0) return null

        val timestamp = parseTimestamp(rawTime, dateFormats) ?: return null

        val valueMmol = if (isMgDlHeader || glucoseVal > 35.0) {
            glucoseVal / 18.0182
        } else {
            glucoseVal
        }

        return GlucoseReadingEntity(
            timestamp = timestamp,
            valueMmol = valueMmol
        )
    }

    private fun autoDetectAndParse(
        parts: List<String>,
        dateFormats: List<SimpleDateFormat>
    ): Triple<GlucoseReadingEntity, Int, Int>? {
        var foundTs: Long? = null
        var foundTsCol = -1
        var foundVal: Double? = null
        var foundValCol = -1

        for (i in parts.indices) {
            val cell = parts[i]
            if (foundTs == null) {
                val ts = parseTimestamp(cell, dateFormats)
                if (ts != null && ts > 946684800000L) { // after year 2000
                    foundTs = ts
                    foundTsCol = i
                    continue
                }
            }

            if (foundVal == null) {
                val clean = cell.replace(',', '.')
                val num = clean.toDoubleOrNull()
                if (num != null && num in 1.0..600.0) {
                    foundVal = num
                    foundValCol = i
                }
            }
        }

        if (foundTs != null && foundVal != null) {
            val valueMmol = if (foundVal > 35.0) foundVal / 18.0182 else foundVal
            val entity = GlucoseReadingEntity(timestamp = foundTs, valueMmol = valueMmol)
            return Triple(entity, foundTsCol, foundValCol)
        }
        return null
    }

    private fun parseTimestamp(raw: String, formats: List<SimpleDateFormat>): Long? {
        val trimmed = raw.trim()
        // Try epoch millis or seconds
        trimmed.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num // epoch millis
                num > 900_000_000L -> num * 1000L // epoch seconds
                else -> null
            }
        }

        // Try date string formats
        for (format in formats) {
            try {
                format.timeZone = TimeZone.getDefault()
                val date = format.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    companion object {
        private const val TAG = "GlucoseImporter"
        private const val CHUNK_SIZE = 5000
    }
}
