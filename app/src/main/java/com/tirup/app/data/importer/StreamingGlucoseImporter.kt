package com.tirup.app.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.HistoricalReadingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

class StreamingGlucoseImporter(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun importHistoricalFromUri(
        uri: Uri,
        onProgress: (importedCount: Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalImported = 0

        try {
            // Clear previous historical dataset so each upload is fresh and isolated
            database.historicalReadingDao().clearAll()

            val rawInputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for URI: $uri"))

            val bufferedIn = BufferedInputStream(rawInputStream, 65536)
            bufferedIn.mark(16)
            val headerBytes = ByteArray(16)
            val bytesRead = bufferedIn.read(headerBytes, 0, 16)
            bufferedIn.reset()

            val isZip = bytesRead >= 4 &&
                    headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()

            val chunk = ArrayList<HistoricalReadingEntity>(CHUNK_SIZE)

            val dateFormats = listOf(
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US),
                SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US),
                SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US),
                SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            )

            if (isZip) {
                Log.d(TAG, "Detected ZIP archive. Processing entries...")
                val zipIn = ZipInputStream(bufferedIn)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && (entryName.endsWith(".csv") || entryName.endsWith(".tsv") || entryName.endsWith(".txt") || !entryName.contains("."))) {
                        Log.d(TAG, "Processing CSV entry inside zip: ${entry.name}")
                        val count = processCsvStream(zipIn, chunk, dateFormats, onProgress)
                        totalImported += count
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            } else {
                Log.d(TAG, "Processing CSV/Text stream...")
                val count = processCsvStream(bufferedIn, chunk, dateFormats, onProgress)
                totalImported += count
            }

            // Flush remaining chunk
            if (chunk.isNotEmpty()) {
                insertHistoricalChunk(chunk)
                totalImported += chunk.size
                chunk.clear()
                onProgress(totalImported)
            }

            Log.d(TAG, "Historical import completed. Total points: $totalImported")
            Result.success(totalImported)
        } catch (e: Exception) {
            Log.e(TAG, "Historical streaming import failed", e)
            Result.failure(e)
        }
    }

    private suspend fun processCsvStream(
        inputStream: InputStream,
        chunk: ArrayList<HistoricalReadingEntity>,
        dateFormats: List<SimpleDateFormat>,
        onProgress: (Int) -> Unit
    ): Int {
        var importedInStream = 0
        val reader = BufferedReader(InputStreamReader(inputStream), 32768)

        var dayCol = -1
        var timeCol = -1
        var singleTsCol = -1
        var valueCol = -1
        var fallbackValueCol = -1
        var isMgDlHeader = false
        var headerFound = false

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line = reader.readLine()
                continue
            }

            // Delimiter detection (semicolon, comma, tab)
            val delimiter = when {
                trimmed.contains(";") -> ";"
                trimmed.contains("\t") -> "\t"
                else -> ","
            }
            val parts = trimmed.split(delimiter).map { it.trim('\"', ' ', '\'') }

            // 1. Check for Header Row (DAY;TIME;UDT_CGMS or Date,Time,Glucose or Device Timestamp,Historic)
            if (!headerFound) {
                val headerLower = parts.map { it.lowercase() }
                var tempDay = -1
                var tempTime = -1
                var tempSingleTs = -1
                var tempVal = -1
                var tempFallbackVal = -1
                var tempIsMgDl = false

                for (i in headerLower.indices) {
                    val h = headerLower[i]
                    if (h == "day" || h == "date" || h == "дата" || h == "день") {
                        tempDay = i
                    }
                    if (h == "time" || h == "время" || h == "zeit") {
                        tempTime = i
                    }
                    if (tempSingleTs == -1 && (h.contains("timestamp") || h == "ts" || h.contains("device timestamp") || h.contains("meter_timestamp"))) {
                        tempSingleTs = i
                    }

                    if (h == "udt_cgms" || h.contains("historic") || h == "glucose" || h == "sgv" || h == "bg_level" || h == "bg" || h.contains("glukose") || h.contains("сахар") || h.contains("глюкоза") || h == "value") {
                        if (tempVal == -1) {
                            tempVal = i
                        } else if (tempFallbackVal == -1) {
                            tempFallbackVal = i
                        }
                        if (h.contains("mg/dl") || h.contains("mgdl") || h.contains("mg") || h == "udt_cgms") {
                            tempIsMgDl = true
                        }
                    }

                    if (h.contains("scan")) {
                        tempFallbackVal = i
                    }
                }

                if ((tempSingleTs != -1 || (tempDay != -1 && tempTime != -1)) && tempVal != -1) {
                    singleTsCol = tempSingleTs
                    dayCol = tempDay
                    timeCol = tempTime
                    valueCol = tempVal
                    fallbackValueCol = tempFallbackVal
                    isMgDlHeader = tempIsMgDl
                    headerFound = true
                    line = reader.readLine()
                    continue
                }
            }

            // 2. Parse Reading
            var reading: HistoricalReadingEntity? = null

            // A. Separate DAY and TIME columns (xDrip export format)
            if (dayCol != -1 && timeCol != -1 && valueCol != -1 && dayCol < parts.size && timeCol < parts.size) {
                val combinedDateTime = "${parts[dayCol]} ${parts[timeCol]}"
                val ts = parseTimestamp(combinedDateTime, dateFormats)
                var rawVal = if (valueCol < parts.size) parts[valueCol].replace(',', '.') else ""
                if (rawVal.isEmpty() && fallbackValueCol != -1 && fallbackValueCol < parts.size) {
                    rawVal = parts[fallbackValueCol].replace(',', '.')
                }
                val gVal = rawVal.toDoubleOrNull()
                if (ts != null && gVal != null && gVal > 0.0) {
                    val valueMmol = if (isMgDlHeader || gVal > 35.0) gVal / 18.0182 else gVal
                    reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                }
            }

            // B. Single Timestamp Column (Juggluco, LibreView, Dexcom)
            if (reading == null && singleTsCol != -1 && valueCol != -1 && singleTsCol < parts.size) {
                val rawTime = parts[singleTsCol]
                val ts = parseTimestamp(rawTime, dateFormats)
                var rawVal = if (valueCol < parts.size) parts[valueCol].replace(',', '.') else ""
                if (rawVal.isEmpty() && fallbackValueCol != -1 && fallbackValueCol < parts.size) {
                    rawVal = parts[fallbackValueCol].replace(',', '.')
                }
                val gVal = rawVal.toDoubleOrNull()
                if (ts != null && gVal != null && gVal > 0.0) {
                    val valueMmol = if (isMgDlHeader || gVal > 35.0) gVal / 18.0182 else gVal
                    reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                }
            }

            // C. Auto-detection fallback
            if (reading == null) {
                val auto = autoDetectRow(parts, dateFormats)
                if (auto != null) {
                    reading = auto.first
                    if (!headerFound) {
                        dayCol = auto.second
                        timeCol = auto.third
                        valueCol = auto.fourth
                        headerFound = true
                    }
                }
            }

            if (reading != null) {
                chunk.add(reading)

                if (chunk.size >= CHUNK_SIZE) {
                    insertHistoricalChunk(chunk)
                    importedInStream += chunk.size
                    chunk.clear()
                    onProgress(importedInStream)
                }
            }

            line = reader.readLine()
        }

        return importedInStream
    }

    private suspend fun insertHistoricalChunk(chunk: List<HistoricalReadingEntity>) {
        database.historicalReadingDao().insertBatch(chunk)
    }

    private fun autoDetectRow(
        parts: List<String>,
        dateFormats: List<SimpleDateFormat>
    ): Quad<HistoricalReadingEntity, Int, Int, Int>? {
        // Check if 2 columns form a valid Date and Time
        for (i in parts.indices) {
            for (j in parts.indices) {
                if (i != j) {
                    val combined = "${parts[i]} ${parts[j]}"
                    val ts = parseTimestamp(combined, dateFormats)
                    if (ts != null && ts > 946684800000L) {
                        for (k in parts.indices) {
                            if (k != i && k != j) {
                                val clean = parts[k].replace(',', '.')
                                val gVal = clean.toDoubleOrNull()
                                if (gVal != null && gVal in 1.0..600.0) {
                                    val valueMmol = if (gVal > 35.0) gVal / 18.0182 else gVal
                                    val entity = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                                    return Quad(entity, i, j, k)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check if single column is Timestamp and another is Glucose
        for (i in parts.indices) {
            val ts = parseTimestamp(parts[i], dateFormats)
            if (ts != null && ts > 946684800000L) {
                for (k in parts.indices) {
                    if (k != i) {
                        val clean = parts[k].replace(',', '.')
                        val gVal = clean.toDoubleOrNull()
                        if (gVal != null && gVal in 1.0..600.0) {
                            val valueMmol = if (gVal > 35.0) gVal / 18.0182 else gVal
                            val entity = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                            return Quad(entity, -1, -1, k)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun parseTimestamp(raw: String, formats: List<SimpleDateFormat>): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        trimmed.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num
                num > 900_000_000L -> num * 1000L
                else -> null
            }
        }

        for (format in formats) {
            try {
                format.timeZone = TimeZone.getDefault()
                val date = format.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        private const val TAG = "HistoricalImporter"
        private const val CHUNK_SIZE = 5000
    }
}
