package com.tirup.app.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.HistoricalMeterReadingEntity
import com.tirup.app.data.local.entity.HistoricalReadingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

class StreamingGlucoseImporter(
    private val context: Context,
    private val database: AppDatabase
) {

    private class CountingInputStream(private val wrapped: InputStream) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = wrapped.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = wrapped.read(b, off, len)
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() = wrapped.close()
    }

    suspend fun importHistoricalFromUri(
        uri: Uri,
        clearPrevious: Boolean = true,
        seenTimestamps: MutableSet<Long>? = null,
        onProgress: (progress: Float, importedCount: Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalImported = 0

        try {
            if (clearPrevious) {
                // Clear previous historical dataset so each upload is fresh and isolated
                database.historicalReadingDao().clearAll()
                database.historicalMeterReadingDao().clearAll()
                seenTimestamps?.clear()
            }
            onProgress(0.02f, 0)

            val totalBytes: Long = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                } ?: -1L
            } catch (e: Exception) {
                -1L
            }

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
            val meterChunk = ArrayList<HistoricalMeterReadingEntity>(CHUNK_SIZE)

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


            if (isZip) {
                Log.d(TAG, "Detected ZIP archive. Scanning for glucose CSVs...")
                val tempZipFile = java.io.File.createTempFile("xdrip_import", ".zip", context.cacheDir)
                java.io.FileOutputStream(tempZipFile).use { out ->
                    bufferedIn.copyTo(out)
                }

                val zipFile = java.util.zip.ZipFile(tempZipFile)
                try {
                    val entries = zipFile.entries().asSequence().filter { !it.isDirectory }.toList()
                    val csvEntries = entries.filter {
                        val n = it.name.lowercase()
                        (n.endsWith(".csv") || n.endsWith(".tsv") || n.endsWith(".txt")) &&
                                !n.contains("treatment") && !n.contains("calibrat")
                    }
                    val calEntries = entries.filter {
                        val n = it.name.lowercase()
                        (n.endsWith(".csv") || n.endsWith(".tsv") || n.endsWith(".txt")) &&
                                (n.contains("treatment") || n.contains("calibrat"))
                    }

                    val totalUncompressed = csvEntries.sumOf { it.size }.coerceAtLeast(1L)
                    var bytesAccumulator = 0L

                    if (csvEntries.isNotEmpty()) {
                        for (entry in csvEntries) {
                            Log.d(TAG, "Processing CSV entry: ${entry.name} (size: ${entry.size})")
                            zipFile.getInputStream(entry).use { entryStream ->
                                val countingStream = CountingInputStream(entryStream)
                                val count = processCsvStream(
                                    inputStream = countingStream,
                                    chunk = chunk,
                                    meterChunk = meterChunk,
                                    dateFormats = dateFormats,
                                    seenTimestamps = seenTimestamps,
                                    onProgressUpdate = { inStreamCount ->
                                        val currentBytes = bytesAccumulator + countingStream.bytesRead
                                        val prog = (currentBytes.toFloat() / totalUncompressed.toFloat()).coerceIn(0.05f, 0.98f)
                                        onProgress(prog, totalImported + inStreamCount)
                                    }
                                )
                                bytesAccumulator += entry.size
                                totalImported += count
                            }
                        }
                    } else {
                        Log.w(TAG, "No valid CSV found in ZIP archive.")
                    }

                    // Also extract meter calibrations from treatments/calibrations entries if available
                    for (calEntry in calEntries) {
                        try {
                            Log.d(TAG, "Processing calibrations entry: ${calEntry.name}")
                            zipFile.getInputStream(calEntry).use { calStream ->
                                processCalibrationStream(calStream, meterChunk, dateFormats)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed parsing calibration entry: ${calEntry.name}", e)
                        }
                    }
                } finally {
                    try { zipFile.close() } catch (_: Exception) {}
                    try { tempZipFile.delete() } catch (_: Exception) {}
                }
            } else {
                Log.d(TAG, "Processing CSV/Text stream...")
                val countingStream = CountingInputStream(bufferedIn)
                val expectedBytes = if (totalBytes > 0) totalBytes else 1_000_000L
                val count = processCsvStream(
                    inputStream = countingStream,
                    chunk = chunk,
                    meterChunk = meterChunk,
                    dateFormats = dateFormats,
                    seenTimestamps = seenTimestamps,
                    onProgressUpdate = { inStreamCount ->
                        val prog = (countingStream.bytesRead.toFloat() / expectedBytes.toFloat()).coerceIn(0.05f, 0.98f)
                        onProgress(prog, inStreamCount)
                    }
                )
                totalImported += count
            }

            // Flush remaining chunks
            if (chunk.isNotEmpty()) {
                insertHistoricalChunk(chunk)
                totalImported += chunk.size
                chunk.clear()
            }
            if (meterChunk.isNotEmpty()) {
                database.historicalMeterReadingDao().insertBatch(meterChunk)
                meterChunk.clear()
            }
            onProgress(1.0f, totalImported)

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
        meterChunk: ArrayList<HistoricalMeterReadingEntity>,
        dateFormats: List<SimpleDateFormat>,
        seenTimestamps: MutableSet<Long>?,
        onProgressUpdate: (Int) -> Unit
    ): Int {
        var importedInStream = 0
        val reader = BufferedReader(InputStreamReader(inputStream), 32768)

        var dayCol = -1
        var timeCol = -1
        var singleTsCol = -1
        var valueCol = -1
        var meterCol = -1
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
                var tempMeterVal = -1

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

                    // Meter (Blood Glucose / Fingerstick / Calibration) columns
                    if (h == "udt_blood_glucose" || h == "bg_level" || h == "fingerstick" || h == "meter_bg" ||
                        h.contains("calibration") || h.contains("fingerstick") || (h.contains("meter") && !h.contains("meter_timestamp")) ||
                        h.contains("blood glucose") || h.contains("калибров") || h.contains("глюкометр")
                    ) {
                        tempMeterVal = i
                    }
                }

                if ((tempSingleTs != -1 || (tempDay != -1 && tempTime != -1)) && tempVal != -1) {
                    singleTsCol = tempSingleTs
                    dayCol = tempDay
                    timeCol = tempTime
                    valueCol = tempVal
                    meterCol = tempMeterVal
                    headerFound = true
                    line = reader.readLine()
                    continue
                }
            }

            // 2. Parse Reading
            var reading: HistoricalReadingEntity? = null

            // Check for Meter Glucose value in this row (fingerstick / calibration)
            if (meterCol != -1 && meterCol < parts.size) {
                val rawMeter = parts[meterCol].replace(',', '.').trim()
                if (rawMeter.isNotEmpty() && !rawMeter.equals("nan", ignoreCase = true)) {
                    val mVal = rawMeter.toDoubleOrNull()
                    if (mVal != null && mVal > 0.5) {
                        val ts = if (dayCol != -1 && timeCol != -1 && dayCol < parts.size && timeCol < parts.size) {
                            parseTimestamp("${parts[dayCol]} ${parts[timeCol]}", dateFormats)
                        } else if (singleTsCol != -1 && singleTsCol < parts.size) {
                            parseTimestamp(parts[singleTsCol], dateFormats)
                        } else null

                        if (ts != null) {
                            val valueMmol = if (mVal <= 30.0) mVal else mVal / 18.0182
                            if (valueMmol in 1.1..35.0) {
                                meterChunk.add(HistoricalMeterReadingEntity(timestamp = ts, valueMmol = valueMmol))
                            }
                        }
                    }
                }
            }

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
                            // Keep all sensor records without dropping duplicates, matching DiaKiaBot
                            if (valueMmol * 18.0182 > 38.0) {
                                reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
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
                                reading = HistoricalReadingEntity(timestamp = ts, valueMmol = valueMmol)
                            }
                        }
                    }
                }
            }

            // C. Auto-detection fallback (ONLY if no header was found)
            if (reading == null && !headerFound) {
                val auto = autoDetectRow(parts, dateFormats)
                if (auto != null) {
                    reading = auto.first
                    dayCol = auto.second
                    timeCol = auto.third
                    valueCol = auto.fourth
                    headerFound = true
                }
            }

            if (reading != null) {
                if (seenTimestamps != null && !seenTimestamps.add(reading.timestamp)) {
                    line = reader.readLine()
                    continue
                }
                chunk.add(reading)

                if (chunk.size >= CHUNK_SIZE) {
                    insertHistoricalChunk(chunk)
                    importedInStream += chunk.size
                    chunk.clear()
                    onProgressUpdate(importedInStream)
                }
            }

            line = reader.readLine()
        }

        return importedInStream
    }

    private suspend fun processCalibrationStream(
        inputStream: InputStream,
        meterChunk: ArrayList<HistoricalMeterReadingEntity>,
        dateFormats: List<SimpleDateFormat>
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 32 * 1024)
        val firstLine = reader.readLine() ?: return
        val delimiter = if (firstLine.contains(",")) "," else if (firstLine.contains(";")) ";" else "\t"
        var line: String? = firstLine

        var tsCol = -1
        var dayCol = -1
        var timeCol = -1
        var bgCol = -1
        var headerFound = false

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line = reader.readLine()
                continue
            }
            val parts = trimmed.split(delimiter).map { it.trim().trim('"', '\'') }
            if (parts.isEmpty()) {
                line = reader.readLine()
                continue
            }

            if (!headerFound) {
                val headerLower = parts.map { it.lowercase() }
                for (i in headerLower.indices) {
                    val h = headerLower[i]
                    if (tsCol == -1 && (h == "timestamp" || h == "mills" || h == "created_at" || h == "date" || h == "time" || h == "datetime")) {
                        tsCol = i
                    }
                    if (dayCol == -1 && (h == "day" || h.contains("дата"))) dayCol = i
                    if (timeCol == -1 && (h == "time" || h.contains("время"))) timeCol = i
                    if (bgCol == -1 && (h == "bg" || h == "glucose" || h == "value" || h.contains("meter") || h.contains("finger") || h.contains("blood"))) {
                        bgCol = i
                    }
                }
                if (bgCol != -1 && (tsCol != -1 || (dayCol != -1 && timeCol != -1))) {
                    headerFound = true
                    line = reader.readLine()
                    continue
                }
            }

            if (bgCol != -1 && bgCol < parts.size) {
                val rawVal = parts[bgCol].replace(',', '.').trim()
                val bg = rawVal.toDoubleOrNull()
                if (bg != null && bg > 0.5) {
                    val ts = if (dayCol != -1 && timeCol != -1 && dayCol < parts.size && timeCol < parts.size) {
                        parseTimestamp("${parts[dayCol]} ${parts[timeCol]}", dateFormats)
                    } else if (tsCol != -1 && tsCol < parts.size) {
                        parseTimestamp(parts[tsCol], dateFormats)
                    } else null

                    if (ts != null) {
                        val valueMmol = if (bg <= 30.0) bg else bg / 18.0182
                        if (valueMmol in 1.1..35.0) {
                            meterChunk.add(HistoricalMeterReadingEntity(timestamp = ts, valueMmol = valueMmol))
                            if (meterChunk.size >= CHUNK_SIZE) {
                                database.historicalMeterReadingDao().insertBatch(meterChunk)
                                meterChunk.clear()
                            }
                        }
                    }
                }
            }

            line = reader.readLine()
        }
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
