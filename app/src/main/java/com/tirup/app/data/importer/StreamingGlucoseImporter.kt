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
            bufferedIn.mark(8)
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
                SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US),
                SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US),
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
        var headerChecked = false
        var isMgDlHeader = false

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

            if (!headerChecked) {
                headerChecked = true
                val headerLower = parts.map { it.lowercase() }

                for (i in headerLower.indices) {
                    val h = headerLower[i]
                    if (timestampCol == -1 && (h.contains("time") || h.contains("date") || h == "ts" || h == "timestamp" || h.contains("zeit") || h.contains("время") || h.contains("дата"))) {
                        timestampCol = i
                    }
                    if (valueCol == -1 && (h.contains("glucose") || h.contains("glukose") || h.contains("sgv") || h.contains("value") || h.contains("bg") || h.contains("сахар") || h.contains("глюкоза") || h.contains("historic") || h.contains("scan"))) {
                        valueCol = i
                        if (h.contains("mg/dl") || h.contains("mgdl") || h.contains("mg")) {
                            isMgDlHeader = true
                        }
                    }
                }

                // If no recognizable headers, check if row is already data (timestamp + value)
                if (timestampCol == -1 || valueCol == -1) {
                    timestampCol = 0
                    valueCol = if (parts.size > 1) 1 else 0
                    parseAndAddReading(parts, timestampCol, valueCol, isMgDlHeader, dateFormats)?.let { entity ->
                        chunk.add(entity)
                        onTimestampParsed(entity.timestamp)
                    }
                }
                line = reader.readLine()
                continue
            }

            parseAndAddReading(parts, timestampCol, valueCol, isMgDlHeader, dateFormats)?.let { entity ->
                chunk.add(entity)
                onTimestampParsed(entity.timestamp)
            }

            if (chunk.size >= CHUNK_SIZE) {
                insertChunk(chunk)
                importedInStream += chunk.size
                chunk.clear()
                onProgress(importedInStream)
            }

            line = reader.readLine()
        }

        return importedInStream
    }

    private suspend fun insertChunk(chunk: List<GlucoseReadingEntity>) {
        database.glucoseReadingDao().insertBatch(chunk)
    }

    private fun parseAndAddReading(
        parts: List<String>,
        timestampCol: Int,
        valueCol: Int,
        isMgDlHeader: Boolean,
        dateFormats: List<SimpleDateFormat>
    ): GlucoseReadingEntity? {
        if (timestampCol >= parts.size) return null
        val valCol = if (valueCol < parts.size) valueCol else parts.size - 1

        val rawTime = parts[timestampCol]
        val rawVal = parts[valCol].replace(',', '.')

        val glucoseVal = rawVal.toDoubleOrNull() ?: return null
        if (glucoseVal <= 0.0 || glucoseVal > 1000.0) return null

        val timestamp = parseTimestamp(rawTime, dateFormats) ?: return null

        // Convert mg/dL to mmol/L if needed (values > 35 are mg/dL)
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

    private fun parseTimestamp(raw: String, formats: List<SimpleDateFormat>): Long? {
        // Try epoch millis or seconds
        raw.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num // epoch millis
                num > 100_000_000L -> num * 1000L // epoch seconds
                else -> null
            }
        }

        // Try date string formats
        for (format in formats) {
            try {
                format.timeZone = TimeZone.getDefault()
                val date = format.parse(raw)
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
