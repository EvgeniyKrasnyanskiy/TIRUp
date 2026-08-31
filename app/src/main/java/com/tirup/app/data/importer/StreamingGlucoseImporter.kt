package com.tirup.app.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.domain.repository.GlucoseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for URI: $uri"))

            val reader = BufferedReader(InputStreamReader(inputStream), 32768) // 32KB buffer
            val chunk = ArrayList<GlucoseReadingEntity>(CHUNK_SIZE)

            var lineIndex = 0
            var timestampCol = -1
            var valueCol = -1
            var headerChecked = false
            var isMgDlHeader = false

            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
                SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US)
            )

            reader.use { br ->
                var line: String? = br.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        line = br.readLine()
                        continue
                    }

                    // Delimiter detection (comma, semicolon, tab)
                    val delimiter = when {
                        trimmed.contains(";") -> ";"
                        trimmed.contains("\t") -> "\t"
                        else -> ","
                    }
                    val parts = trimmed.split(delimiter).map { it.trim('\"', ' ', '\'') }

                    if (!headerChecked) {
                        headerChecked = true
                        val headerLower = parts.map { it.lowercase() }
                        // Look for headers
                        for (i in headerLower.indices) {
                            val h = headerLower[i]
                            if (timestampCol == -1 && (h.contains("time") || h.contains("date") || h == "ts" || h == "timestamp")) {
                                timestampCol = i
                            }
                            if (valueCol == -1 && (h.contains("glucose") || h.contains("sgv") || h.contains("value") || h.contains("bg") || h.contains("сахар") || h.contains("глюкоза"))) {
                                valueCol = i
                                if (h.contains("mg/dl") || h.contains("mgdl")) {
                                    isMgDlHeader = true
                                }
                            }
                        }

                        // If not found in header, assume col 0 is timestamp and col 1 is value
                        if (timestampCol == -1 || valueCol == -1) {
                            timestampCol = 0
                            valueCol = 1
                            // If first line wasn't header text, parse it directly
                            parseAndAddReading(parts, timestampCol, valueCol, isMgDlHeader, dateFormats)?.let { entity ->
                                chunk.add(entity)
                                if (entity.timestamp < minTimestamp) minTimestamp = entity.timestamp
                                if (entity.timestamp > maxTimestamp) maxTimestamp = entity.timestamp
                            }
                        }
                        line = br.readLine()
                        continue
                    }

                    parseAndAddReading(parts, timestampCol, valueCol, isMgDlHeader, dateFormats)?.let { entity ->
                        chunk.add(entity)
                        if (entity.timestamp < minTimestamp) minTimestamp = entity.timestamp
                        if (entity.timestamp > maxTimestamp) maxTimestamp = entity.timestamp
                    }

                    lineIndex++

                    if (chunk.size >= CHUNK_SIZE) {
                        insertChunk(chunk)
                        totalImported += chunk.size
                        chunk.clear()
                        onProgress(totalImported)
                    }

                    line = br.readLine()
                }

                if (chunk.isNotEmpty()) {
                    insertChunk(chunk)
                    totalImported += chunk.size
                    chunk.clear()
                    onProgress(totalImported)
                }
            }

            // Recalculate daily summaries for the imported range
            if (totalImported > 0 && minTimestamp <= maxTimestamp) {
                repository.recalculateDailySummaries(minTimestamp, maxTimestamp)
            }

            Result.success(totalImported)
        } catch (e: Exception) {
            Log.e(TAG, "Streaming import failed", e)
            Result.failure(e)
        }
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
        if (timestampCol >= parts.size || valueCol >= parts.size) return null

        val rawTime = parts[timestampCol]
        val rawVal = parts[valueCol].replace(',', '.')

        val glucoseVal = rawVal.toDoubleOrNull() ?: return null
        if (glucoseVal <= 0.0 || glucoseVal > 60.0 && !isMgDlHeader && glucoseVal > 1000.0) return null

        // Parse timestamp
        val timestamp = parseTimestamp(rawTime, dateFormats) ?: return null

        // Normalize to mmol/L
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
            return if (num > 10_000_000_000L) num else num * 1000L
        }

        // Try date formats
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
