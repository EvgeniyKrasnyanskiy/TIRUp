package com.tirup.app.data.backup

import android.os.Environment
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class BackupSummary(
    val patientName: String,
    val readingsCount: Int,
    val exportedAt: Long,
    val earliestTimestamp: Long,
    val latestTimestamp: Long,
    val diabetesType: String,
    val backupFile: File
)

object AutoBackupManager {

    private const val TAG = "AutoBackupManager"
    private const val BACKUP_DIR_NAME = "TIRUp/Backups"
    private const val BACKUP_FILE_NAME = "tirup_backup.json"
    private const val BACKUP_BAK_NAME = "tirup_backup.json.bak"
    private const val BACKUP_TMP_NAME = "tirup_backup.json.tmp"

    fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getBackupDirectory(context: android.content.Context? = null): File {
        val root = Environment.getExternalStorageDirectory()
        val primaryDir = File(root, BACKUP_DIR_NAME)

        if (hasStoragePermission()) {
            if (!primaryDir.exists()) primaryDir.mkdirs()
            return primaryDir
        }

        // Fallback to app-specific external storage if system permission is not yet granted
        if (context != null) {
            val fallbackDir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            return fallbackDir
        }

        return primaryDir
    }

    fun getBackupFile(context: android.content.Context? = null): File {
        return File(getBackupDirectory(context), BACKUP_FILE_NAME)
    }

    private fun getBakFile(context: android.content.Context? = null): File {
        return File(getBackupDirectory(context), BACKUP_BAK_NAME)
    }

    private fun getTmpFile(context: android.content.Context? = null): File {
        return File(getBackupDirectory(context), BACKUP_TMP_NAME)
    }

    /**
     * Self-healing: if the main backup file is missing or corrupted,
     * but a valid .bak file exists, automatically restore .bak -> backup.json.
     */
    fun checkAndSelfHealBak(context: android.content.Context? = null): Boolean {
        return try {
            val mainFile = getBackupFile(context)
            val bakFile = getBakFile(context)

            if ((!mainFile.exists() || mainFile.length() == 0L) && bakFile.exists() && bakFile.length() > 0L) {
                Log.w(TAG, "Main backup missing/empty. Self-healing from .bak file...")
                if (mainFile.exists()) mainFile.delete()
                bakFile.renameTo(mainFile)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Self-heal check failed: ${e.message}", e)
            false
        }
    }

    /**
     * Reads a quick header summary of the backup file without loading all points into memory.
     */
    fun getBackupSummary(context: android.content.Context? = null): BackupSummary? {
        checkAndSelfHealBak(context)
        var file = getBackupFile(context)
        if (!file.exists() || file.length() == 0L) {
            if (context != null) {
                file = getBackupFile(null)
            }
            if (!file.exists() || file.length() == 0L) {
                return null
            }
        }

        return try {
            var patientName = ""
            var diabetesType = ""
            var readingsCount = 0
            var exportedAt = 0L
            var earliest = 0L
            var latest = 0L

            FileReader(file).use { fr ->
                JsonReader(fr).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "exportedAt" -> exportedAt = reader.nextLong()
                            "readingsCount" -> readingsCount = reader.nextInt()
                            "earliestTimestamp" -> earliest = reader.nextLong()
                            "latestTimestamp" -> latest = reader.nextLong()
                            "settings" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "patientProfile") {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "fullName" -> patientName = reader.nextString()
                                                "diabetesType" -> diabetesType = reader.nextString()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            BackupSummary(
                patientName = patientName,
                readingsCount = readingsCount,
                exportedAt = exportedAt,
                earliestTimestamp = earliest,
                latestTimestamp = latest,
                diabetesType = diabetesType,
                backupFile = file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup summary: ${e.message}", e)
            null
        }
    }

    /**
     * Performs an atomic transactional backup:
     * 1. Backup existing tirup_backup.json -> tirup_backup.json.bak
     * 2. Write new accumulation data to tirup_backup.json.tmp
     * 3. Rename .tmp -> tirup_backup.json
     * 4. Verify file is valid, then remove .bak
     */
    suspend fun performBackup(
        context: android.content.Context? = null,
        database: AppDatabase,
        settings: UserSettings
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dir = getBackupDirectory(context)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val mainFile = getBackupFile(context)
            val bakFile = getBakFile(context)
            val tmpFile = getTmpFile(context)

            // Step 1: Backup current file to .bak if exists
            if (mainFile.exists() && mainFile.length() > 0L) {
                if (bakFile.exists()) bakFile.delete()
                mainFile.copyTo(bakFile, overwrite = true)
            }

            // Step 2: Query all live readings
            val readings = database.glucoseReadingDao().getReadingsBetweenSync(0L, Long.MAX_VALUE)
            val earliest = readings.minOfOrNull { it.timestamp } ?: 0L
            val latest = readings.maxOfOrNull { it.timestamp } ?: 0L
            val now = System.currentTimeMillis()

            // Step 3: Stream write to tmp file
            if (tmpFile.exists()) tmpFile.delete()

            FileWriter(tmpFile).use { fw ->
                JsonWriter(fw).use { writer ->
                    writer.setIndent("  ")
                    writer.beginObject()

                    writer.name("version").value(1)
                    writer.name("appName").value("TIRUp")
                    writer.name("exportedAt").value(now)
                    writer.name("readingsCount").value(readings.size)
                    writer.name("earliestTimestamp").value(earliest)
                    writer.name("latestTimestamp").value(latest)

                    // User Settings
                    writer.name("settings")
                    writer.beginObject()
                    writer.name("language").value(settings.language)
                    writer.name("unit").value(settings.unit.name)
                    writer.name("targetMode").value(settings.targetMode.name)
                    writer.name("periodDays").value(settings.periodDays)
                    writer.name("nightStartHour").value(settings.nightStartHour)
                    writer.name("nightEndHour").value(settings.nightEndHour)
                    writer.name("themeMode").value(settings.themeMode.name)

                    // Target Ranges
                    writer.name("targetRanges")
                    writer.beginObject()
                    writer.name("tirLowMmol").value(settings.targetRanges.tirLowMmol)
                    writer.name("tirHighMmol").value(settings.targetRanges.tirHighMmol)
                    writer.name("tingHighMmol").value(settings.targetRanges.tingHighMmol)
                    writer.name("tirGoalPercent").value(settings.targetRanges.tirGoalPercent)
                    writer.name("tingGoalPercent").value(settings.targetRanges.tingGoalPercent)
                    writer.endObject()

                    // Patient Profile
                    val p = settings.patientProfile
                    writer.name("patientProfile")
                    writer.beginObject()
                    writer.name("fullName").value(p.fullName)
                    writer.name("gender").value(p.gender)
                    writer.name("birthYear").value(p.birthYear)
                    writer.name("birthMonth").value(p.birthMonth)
                    writer.name("heightCm").value(p.heightCm)
                    writer.name("weightKg").value(p.weightKg)
                    writer.name("diabetesType").value(p.diabetesType)
                    writer.name("diagnosisYear").value(p.diagnosisYear)
                    writer.name("therapyType").value(p.therapyType)
                    writer.endObject()

                    writer.endObject() // end settings

                    // Readings array
                    writer.name("readings")
                    writer.beginArray()
                    readings.forEach { r ->
                        writer.beginObject()
                        writer.name("t").value(r.timestamp)
                        writer.name("v").value(r.valueMmol)
                        if (!r.trendArrow.isNullOrEmpty()) writer.name("a").value(r.trendArrow)
                        writer.endObject()
                    }
                    writer.endArray()

                    writer.endObject() // end root
                }
            }

            // Step 4: Atomic swap
            if (mainFile.exists()) mainFile.delete()
            val renamed = tmpFile.renameTo(mainFile)
            if (!renamed || !mainFile.exists() || mainFile.length() == 0L) {
                // Rollback from .bak if rename failed
                if (bakFile.exists()) {
                    bakFile.renameTo(mainFile)
                }
                throw IllegalStateException("Failed to finalize backup file")
            }

            // Step 5: Success! Delete temporary .bak
            if (bakFile.exists()) {
                bakFile.delete()
            }

            Log.i(TAG, "Auto-backup successfully saved ${readings.size} readings to ${mainFile.absolutePath}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private val backupMutex = Mutex()

    /**
     * Checks if auto-backup is enabled and if 24 hours have elapsed since the last backup.
     * If so, executes atomic backup.
     */
    suspend fun maybeTriggerAutoBackup(
        context: android.content.Context? = null,
        database: AppDatabase,
        settingsRepository: SettingsRepository,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!backupMutex.tryLock()) return@withContext

        try {
            val settings = settingsRepository.getSettings().first()
            if (!settings.isAutoBackupEnabled) return@withContext

            val count = database.glucoseReadingDao().getTotalCount()
            if (count == 0L) return@withContext

            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            val elapsed = now - settings.lastBackupTimestamp

            if (force || elapsed >= oneDayMs || settings.lastBackupTimestamp == 0L) {
                val res = performBackup(context, database, settings)
                if (res.isSuccess) {
                    settingsRepository.updateSettings(settings.copy(lastBackupTimestamp = now))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "maybeTriggerAutoBackup error: ${e.message}")
        } finally {
            backupMutex.unlock()
        }
    }

    /**
     * Restores patient settings and all glucose readings from the backup file into AppDatabase.
     */
    suspend fun restoreBackup(
        context: android.content.Context? = null,
        database: AppDatabase,
        settingsRepository: SettingsRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            checkAndSelfHealBak(context)
            var file = getBackupFile(context)
            if (!file.exists() || file.length() == 0L) {
                if (context != null) {
                    file = getBackupFile(null)
                }
            }
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Backup file not found"))
            }

            var restoredSettings: UserSettings? = null
            val restoredReadings = mutableListOf<GlucoseReadingEntity>()

            FileReader(file).use { fr ->
                JsonReader(fr).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "settings" -> {
                                restoredSettings = parseSettings(reader)
                            }
                            "readings" -> {
                                parseReadings(reader, restoredReadings)
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            // 1. Update settings
            if (restoredSettings != null) {
                val s = restoredSettings!!
                settingsRepository.updateSettings(
                    s.copy(
                        hasSeenOnboarding = true,
                        lastBackupTimestamp = System.currentTimeMillis()
                    )
                )
            }

            // 2. Insert readings in batches of 1000
            val batchSize = 1000
            for (i in restoredReadings.indices step batchSize) {
                val end = (i + batchSize).coerceAtMost(restoredReadings.size)
                database.glucoseReadingDao().insertBatch(restoredReadings.subList(i, end))
            }

            Log.i(TAG, "Restored ${restoredReadings.size} readings and patient settings from backup.")
            Result.success(restoredReadings.size)
        } catch (e: Exception) {
            Log.e(TAG, "Restore backup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseSettings(reader: JsonReader): UserSettings {
        var language = "RU"
        var unit = GlucoseUnit.MMOL_L
        var targetMode = TargetMode.TIR
        var periodDays = 14
        var nightStart = 0
        var nightEnd = 6
        var themeMode = ThemeMode.DARK
        var targetRanges = TargetRanges()
        var profile = PatientProfile()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "language" -> language = reader.nextString()
                "unit" -> unit = try { GlucoseUnit.valueOf(reader.nextString()) } catch (_: Exception) { GlucoseUnit.MMOL_L }
                "targetMode" -> targetMode = try { TargetMode.valueOf(reader.nextString()) } catch (_: Exception) { TargetMode.TIR }
                "periodDays" -> periodDays = reader.nextInt()
                "nightStartHour" -> nightStart = reader.nextInt()
                "nightEndHour" -> nightEnd = reader.nextInt()
                "themeMode" -> themeMode = try { ThemeMode.valueOf(reader.nextString()) } catch (_: Exception) { ThemeMode.DARK }
                "targetRanges" -> {
                    var tirLow = 3.9
                    var tirHigh = 10.0
                    var tingHigh = 7.8
                    var tirGoal = 70
                    var tingGoal = 50
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "tirLowMmol" -> tirLow = reader.nextDouble()
                            "tirHighMmol" -> tirHigh = reader.nextDouble()
                            "tingHighMmol" -> tingHigh = reader.nextDouble()
                            "tirGoalPercent" -> tirGoal = reader.nextInt()
                            "tingGoalPercent" -> tingGoal = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    targetRanges = TargetRanges(
                        tirLowMmol = tirLow,
                        tirHighMmol = tirHigh,
                        tingHighMmol = tingHigh,
                        tirGoalPercent = tirGoal,
                        tingGoalPercent = tingGoal
                    )
                }
                "patientProfile" -> {
                    var name = ""
                    var gender = "M"
                    var bYear = 1990
                    var bMonth = 1
                    var height = ""
                    var weight = ""
                    var diabType = "СД1"
                    var diagYear = 2018
                    var therapy = "Инсулиновая помпа"
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "fullName" -> name = reader.nextString()
                            "gender" -> gender = reader.nextString()
                            "birthYear" -> bYear = reader.nextInt()
                            "birthMonth" -> bMonth = reader.nextInt()
                            "heightCm" -> height = reader.nextString()
                            "weightKg" -> weight = reader.nextString()
                            "diabetesType" -> diabType = reader.nextString()
                            "diagnosisYear" -> diagYear = reader.nextInt()
                            "therapyType" -> therapy = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    profile = PatientProfile(name, gender, bYear, bMonth, height, weight, diabType, diagYear, therapy)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return UserSettings(
            language = language,
            unit = unit,
            targetMode = targetMode,
            targetRanges = targetRanges,
            periodDays = periodDays,
            nightStartHour = nightStart,
            nightEndHour = nightEnd,
            themeMode = themeMode,
            patientProfile = profile,
            isAutoBackupEnabled = true,
            hasSeenOnboarding = true
        )
    }

    private fun parseReadings(reader: JsonReader, outList: MutableList<GlucoseReadingEntity>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var timestamp = 0L
            var value = 0.0
            var arrow = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "t" -> timestamp = reader.nextLong()
                    "v" -> value = reader.nextDouble()
                    "a" -> arrow = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (timestamp > 0L && value > 0.0) {
                outList.add(
                    GlucoseReadingEntity(
                        timestamp = timestamp,
                        valueMmol = value,
                        trendArrow = if (arrow.isNotBlank()) arrow else null
                    )
                )
            }
        }
        reader.endArray()
    }
}
