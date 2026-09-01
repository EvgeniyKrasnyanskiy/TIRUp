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
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).apply { isLenient = false },
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { isLenient = false },
                SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply { isLenient = false },
                SimpleDateFormat("MM/dd/yyyy hh:mm:ss a", Locale.US).apply { isLenient = false },
                SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).apply { isLenient = false },
                SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).apply { isLenient = false },
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).apply { isLenient = false }
            )

            val seenTimestamps = HashSet<Long>()

            if (isZip) {
                Log.d(TAG, "Detected ZIP archive. Scanning for glucose CSVs...")
                val tempZipFile = java.io.File.createTempFile("xdrip_import", ".zip", context.cacheDir)
                java.io.FileOutputStream(tempZipFile).use { out ->
                    bufferedIn.copyTo(out)
                }

                val zipFile = java.util.zip.ZipFile(tempZipFile)
                val entries = zipFile.entries().asSequence().filter { !it.isDirectory }.toList()
                val csvEntries = entries.filter {
                    val n = it.name.lowercase()
                    (n.endsWith(".csv") || n.endsWith(".tsv") || n.endsWith(".txt")) &&
                            !n.contains("treatment") && !n.contains("calibrat")
                }

                if (csvEntries.isNotEmpty()) {
                    for (entry in csvEntries) {
                        Log.d(TAG, "Processing CSV entry: ${entry.name} (size: ${entry.size})")
                        zipFile.getInputStream(entry).use { entryStream ->
                            val count = processCsvStream(entryStream, chunk, dateFormats, seenTimestamps, onProgress)
                            totalImported += count
                        }
                    }
                } else {
                    Log.w(TAG, "No valid CSV found in ZIP archive.")
                }

                zipFile.close()
                tempZipFile.delete()
            } else {
                Log.d(TAG, "Processing CSV/Text stream...")
                val count = processCsvStream(bufferedIn, chunk, dateFormats, seenTimestamps, onProgress)
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
        seenTimestamps: HashSet<Long>,
        onProgress: (Int) -> Unit
    ): Int {
        var importedInStream = 0
        val reader = BufferedReader(InputStreamReader(inputStream), 32768)

        var dayCol = -1
        var timeCol = -1
        var singleTsCol = -1
        var valueCol = -1
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

                    // Strict CGM columns only (ignore bg_level, bolus, carbs which are events/treatments)
                    if (h == "udt_cgms") {
                        tempVal = i
                    } else if (tempVal == -1 && (h.contains("historic") || h == "glucose" || h == "sgv" || h.contains("glukose") || h.contains("сахар") || h.contains("глюкоза") || h == "value")) {
                        tempVal = i
                    }
                }

                if ((tempSingleTs != -1 || (tempDay != -1 && tempTime != -1)) && tempVal != -1) {
                    singleTsCol = tempSingleTs
                    dayCol = tempDay
                    timeCol = tempTime
                    valueCol = tempVal
                    headerFound = true
                    line = reader.readLine()
                    continue
                }
            }

            // 2. Parse Reading
            var reading: HistoricalReadingEntity? = null

            // A. Separate DAY and TIME columns (xDrip export format)
            if (dayCol != -1 && timeCol != -1 && valueCol != -1 && dayCol < parts.size && timeCol < parts.size) {
                val rawVal = if (valueCol < parts.size) parts[valueCol].replace(',', '.').trim() else ""
                // Skip rows without sensor glucose (calibrations, bolus, carbs, NaN)
                if (rawVal.isNotEmpty() && !rawVal.equals("nan", ignoreCase = true)) {
                    val gVal = rawVal.toDoubleOrNull()
                    if (gVal != null && gVal > 0.0) {
                        val combinedDateTime = "${parts[dayCol]} ${parts[timeCol]}"
                        val ts = parseTimestamp(combinedDateTime, dateFormats)
                        if (ts != null) {
                            // Match glycemia_processor.py is_probably_mmol heuristic: if <= 30.0 -> mmol/L, else mg/dL
                            val valueMmol = if (gVal <= 30.0) gVal else gVal / 18.0182
                            // xDrip CUTOFF filter (> 38.0 mg/dL ~ 2.109 mmol/L)
                            if (valueMmol * 18.0182 > 38.0) {
                                // Deduplicate by timestamp (matching DiaKiaBot drop_duplicates)
                                if (seenTimestamps.add(ts)) {
                                    reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                                }
                            }
                        }
                    }
                }
            }

            // B. Single Timestamp Column (Juggluco, LibreView, Dexcom)
            if (reading == null && singleTsCol != -1 && valueCol != -1 && singleTsCol < parts.size) {
                val rawVal = if (valueCol < parts.size) parts[valueCol].replace(',', '.').trim() else ""
                if (rawVal.isNotEmpty() && !rawVal.equals("nan", ignoreCase = true)) {
                    val gVal = rawVal.toDoubleOrNull()
                    if (gVal != null && gVal > 0.0) {
                        val rawTime = parts[singleTsCol]
                        val ts = parseTimestamp(rawTime, dateFormats)
                        if (ts != null) {
                            val valueMmol = if (gVal <= 30.0) gVal else gVal / 18.0182
                            if (valueMmol * 18.0182 > 38.0) {
                                if (seenTimestamps.add(ts)) {
                                    reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                                }
                            }
                        }
                    }
                }
            }

            // C. Auto-detection fallback (ONLY if no header was found)
            if (reading == null && !headerFound) {
                val auto = autoDetectRow(parts, dateFormats)
                if (auto != null) {
                    if (seenTimestamps.add(auto.first.timestamp)) {
                        reading = auto.first
                    }
                    dayCol = auto.second
                    timeCol = auto.third
                    valueCol = auto.fourth
                    headerFound = true
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
                                if (gVal != null && gVal > 0.0) {
                                    val valueMmol = if (gVal <= 30.0) gVal else gVal / 18.0182
                                    if (valueMmol * 18.0182 >= 38.0) {
                                        val entity = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                                        return Quad(entity, i, j, k)
                                    }
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
                        if (gVal != null && gVal > 0.0) {
                            val valueMmol = if (gVal <= 30.0) gVal else gVal / 18.0182
                            if (valueMmol * 18.0182 >= 38.0) {
                                val entity = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                                return Quad(entity, -1, -1, k)
                            }
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
