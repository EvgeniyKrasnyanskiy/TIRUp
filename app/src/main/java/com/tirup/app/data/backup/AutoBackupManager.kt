package com.tirup.app.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.data.local.entity.TreatmentEntity
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BleBridgeSettings
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.LancetStatus
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val patientName: String = "",
    val readingsCount: Int = 0,
    val exportedAt: Long = 0L,
    val earliestTimestamp: Long = 0L,
    val latestTimestamp: Long = 0L,
    val diabetesType: String = "",
    val backupFile: File? = null,
    val treatmentsCount: Int = 0,
    val backupLocation: String = "",
    val hasSettings: Boolean = false
)

data class BackupRestoreResult(
    val readingsRestored: Int,
    val treatmentsRestored: Int,
    val settingsRestored: Boolean
)

object AutoBackupManager {

    private const val TAG = "AutoBackupManager"
    const val SETTINGS_FILE_NAME = "tirup_settings.json"
    const val READINGS_FILE_NAME = "tirup_readings.csv"
    const val TREATMENTS_FILE_NAME = "tirup_treatments.csv"
    const val LEGACY_BACKUP_FILE_NAME = "tirup_backup.json"
    private const val BACKUP_DIR_NAME = "Backups"
    private const val ALARM_REQUEST_CODE = 9021

    fun getPublicBackupDirectory(): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(root, "TIRUp/$BACKUP_DIR_NAME")
        if (!dir.exists()) {
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                Log.w(TAG, "Could not create public backup dir: ${e.message}")
            }
        }
        return dir
    }

    fun getInternalBackupDirectory(context: Context? = null): File {
        if (context != null) {
            val dir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        val root = Environment.getExternalStorageDirectory()
        val dir = File(root, "Android/data/com.tirup.app/files/$BACKUP_DIR_NAME")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBackupDirectory(context: Context? = null): File {
        try {
            val pub = getPublicBackupDirectory()
            if (pub.exists() && pub.canWrite()) {
                return pub
            }
        } catch (_: Exception) {}
        return getInternalBackupDirectory(context)
    }

    fun getBackupFile(context: Context? = null): File {
        val dir = getBackupDirectory(context)
        val legacy = File(dir, LEGACY_BACKUP_FILE_NAME)
        if (legacy.exists() && legacy.length() > 0L) return legacy
        return File(dir, SETTINGS_FILE_NAME)
    }

    /**
     * Schedules exact 23:59:59 daily auto-backup alarm using AlarmManager.
     */
    fun scheduleNextDailyBackup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = android.content.Intent(context, com.tirup.app.data.receiver.BackupAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Daily auto-backup scheduled strictly at 23:59:59 for: ${calendar.time}")
        } catch (e: Exception) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.i(TAG, "Daily auto-backup scheduled via setAndAllowWhileIdle for: ${calendar.time}")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule backup alarm: ${e2.message}")
            }
        }
    }

    fun cancelDailyBackup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = android.content.Intent(context, com.tirup.app.data.receiver.BackupAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Daily auto-backup alarm cancelled.")
        }
    }

    private val backupMutex = Mutex()

    private fun isDifferentDay(t1: Long, t2: Long): Boolean {
        if (t1 <= 0L || t2 <= 0L) return true
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(java.util.Calendar.YEAR) != c2.get(java.util.Calendar.YEAR) ||
               c1.get(java.util.Calendar.DAY_OF_YEAR) != c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * Checks if auto-backup is enabled and if backup should be executed.
     */
    suspend fun maybeTriggerAutoBackup(
        context: Context? = null,
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
            val isMissedDay = isDifferentDay(now, settings.lastBackupTimestamp) && settings.lastBackupTimestamp > 0L

            if (force || isMissedDay || settings.lastBackupTimestamp == 0L) {
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
     * Checks all completed years prior to current calendar year.
     * If there are readings in database for that year and annual archive tirup_readings_YYYY.csv
     * does not yet exist or is empty, seals that year into an immutable archive file.
     */
    suspend fun archiveCompletedYears(
        context: Context? = null,
        database: AppDatabase
    ): List<Int> = withContext(Dispatchers.IO) {
        val archivedYears = mutableListOf<Int>()
        try {
            val pubDir = getPublicBackupDirectory()
            val internalDir = getInternalBackupDirectory(context)
            val primaryDir = if (pubDir.exists() && pubDir.canWrite()) pubDir else internalDir

            val earliest = database.glucoseReadingDao().getEarliestTimestamp() ?: return@withContext emptyList()
            val cal = java.util.Calendar.getInstance()
            val currentYear = cal.get(java.util.Calendar.YEAR)

            val earliestCal = java.util.Calendar.getInstance().apply { timeInMillis = earliest }
            val earliestYear = earliestCal.get(java.util.Calendar.YEAR)

            for (year in earliestYear until currentYear) {
                val yearReadingsFile = File(primaryDir, "tirup_readings_$year.csv")
                val yearTreatmentsFile = File(primaryDir, "tirup_treatments_$year.csv")

                val startOfYear = java.util.Calendar.getInstance().apply {
                    set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfYear = java.util.Calendar.getInstance().apply {
                    set(year, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                    set(java.util.Calendar.MILLISECOND, 999)
                }.timeInMillis

                val count = database.glucoseReadingDao().getCountBetween(startOfYear, endOfYear)
                if (count > 0 && (!yearReadingsFile.exists() || yearReadingsFile.length() == 0L)) {
                    writeReadingsCsvFile(yearReadingsFile, database, count, startOfYear, endOfYear)
                    val tCount = database.treatmentDao().getCountBetween(startOfYear, endOfYear)
                    if (tCount > 0) {
                        writeTreatmentsCsvFile(yearTreatmentsFile, database, tCount, startOfYear, endOfYear)
                    }

                    if (primaryDir.absolutePath != internalDir.absolutePath && internalDir.exists()) {
                        try {
                            if (yearReadingsFile.exists()) yearReadingsFile.copyTo(File(internalDir, yearReadingsFile.name), overwrite = true)
                            if (yearTreatmentsFile.exists()) yearTreatmentsFile.copyTo(File(internalDir, yearTreatmentsFile.name), overwrite = true)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error mirroring year archive $year: ${e.message}")
                        }
                    }

                    archivedYears.add(year)
                    Log.i(TAG, "Successfully sealed annual archive for year $year with $count readings")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "archiveCompletedYears error: ${e.message}", e)
        }
        archivedYears
    }

    /**
     * Manually seals a specific year's data into tirup_readings_YYYY.csv.
     */
    suspend fun archiveYear(
        year: Int,
        context: Context? = null,
        database: AppDatabase
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pubDir = getPublicBackupDirectory()
            val internalDir = getInternalBackupDirectory(context)
            val primaryDir = if (pubDir.exists() && pubDir.canWrite()) pubDir else internalDir

            val yearReadingsFile = File(primaryDir, "tirup_readings_$year.csv")
            val yearTreatmentsFile = File(primaryDir, "tirup_treatments_$year.csv")

            val startOfYear = java.util.Calendar.getInstance().apply {
                set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfYear = java.util.Calendar.getInstance().apply {
                set(year, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis

            val count = database.glucoseReadingDao().getCountBetween(startOfYear, endOfYear)
            if (count > 0) {
                writeReadingsCsvFile(yearReadingsFile, database, count, startOfYear, endOfYear)
                val tCount = database.treatmentDao().getCountBetween(startOfYear, endOfYear)
                if (tCount > 0) {
                    writeTreatmentsCsvFile(yearTreatmentsFile, database, tCount, startOfYear, endOfYear)
                }

                if (primaryDir.absolutePath != internalDir.absolutePath && internalDir.exists()) {
                    try {
                        if (yearReadingsFile.exists()) yearReadingsFile.copyTo(File(internalDir, yearReadingsFile.name), overwrite = true)
                        if (yearTreatmentsFile.exists()) yearTreatmentsFile.copyTo(File(internalDir, yearTreatmentsFile.name), overwrite = true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error mirroring year archive $year: ${e.message}")
                    }
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "archiveYear $year failed: ${e.message}", e)
            false
        }
    }

    /**
     * Returns list of years for which archived CSV files exist in backup directory.
     */
    fun getArchivedYears(context: Context? = null): List<Int> {
        val dir = getBackupDirectory(context)
        val files = dir.listFiles { _, name ->
            name.matches(Regex("tirup_readings_\\d{4}\\.csv"))
        } ?: return emptyList()

        return files.mapNotNull { f ->
            f.name.removePrefix("tirup_readings_").removeSuffix(".csv").toIntOrNull()
        }.sorted()
    }

    /**
     * Performs a complete backup:
     * 1. Seals past completed calendar years into immutable tirup_readings_YYYY.csv.
     * 2. Writes tirup_settings.json.
     * 3. Writes active readings into tirup_readings.csv (active calendar year or all if 1 year).
     * 4. Writes active treatments into tirup_treatments.csv.
     * 5. Writes legacy tirup_backup.json for backward compatibility.
     * Replicates to both Documents/TIRUp/Backups/ and Android/data/.../Backups/.
     */
    suspend fun performBackup(
        context: Context? = null,
        database: AppDatabase,
        settings: UserSettings
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val pubDir = getPublicBackupDirectory()
            val internalDir = getInternalBackupDirectory(context)
            val primaryDir = if (pubDir.exists() && pubDir.canWrite()) pubDir else internalDir

            // Step 0: Ensure past completed years are archived
            archiveCompletedYears(context, database)

            val totalReadings = database.glucoseReadingDao().getTotalCount()
            val totalTreatments = database.treatmentDao().getTotalCount()
            val earliest = database.glucoseReadingDao().getEarliestTimestamp() ?: 0L
            val latest = database.glucoseReadingDao().getLatestTimestamp() ?: 0L
            val now = System.currentTimeMillis()

            val currentYearStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val isMultiYear = earliest > 0L && earliest < currentYearStart
            val activeReadingsCount = if (isMultiYear) {
                database.glucoseReadingDao().getCountBetween(currentYearStart, now)
            } else {
                totalReadings
            }
            val activeStartTime = if (isMultiYear) currentYearStart else null
            val activeEndTime = if (isMultiYear) now else null

            // 1. Write Settings JSON
            val settingsFile = File(primaryDir, SETTINGS_FILE_NAME)
            writeSettingsJsonFile(settingsFile, settings, now)

            // 2. Write Readings CSV (active period for Zero-Lag)
            val readingsFile = File(primaryDir, READINGS_FILE_NAME)
            writeReadingsCsvFile(readingsFile, database, activeReadingsCount, activeStartTime, activeEndTime)

            // 3. Write Treatments CSV
            val treatmentsFile = File(primaryDir, TREATMENTS_FILE_NAME)
            writeTreatmentsCsvFile(treatmentsFile, database, totalTreatments)

            // 4. Write Legacy Backup JSON (for full backward compatibility)
            val legacyFile = File(primaryDir, LEGACY_BACKUP_FILE_NAME)
            writeLegacyBackupJsonFile(
                legacyFile,
                database,
                settings,
                activeReadingsCount,
                if (isMultiYear) currentYearStart else earliest,
                latest,
                now,
                activeStartTime,
                activeEndTime
            )

            // 5. Mirror to internalDir if primaryDir is public
            if (primaryDir.absolutePath != internalDir.absolutePath && internalDir.exists()) {
                try {
                    if (settingsFile.exists()) settingsFile.copyTo(File(internalDir, SETTINGS_FILE_NAME), overwrite = true)
                    if (readingsFile.exists()) readingsFile.copyTo(File(internalDir, READINGS_FILE_NAME), overwrite = true)
                    if (treatmentsFile.exists()) treatmentsFile.copyTo(File(internalDir, TREATMENTS_FILE_NAME), overwrite = true)
                    if (legacyFile.exists()) legacyFile.copyTo(File(internalDir, LEGACY_BACKUP_FILE_NAME), overwrite = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Mirroring backup to internal storage encountered warning: ${e.message}")
                }
            }

            Log.i(TAG, "Backup successfully completed: $activeReadingsCount active readings ($totalReadings total across all years), $totalTreatments treatments in ${primaryDir.absolutePath}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun writeSettingsJsonFile(file: File, settings: UserSettings, exportedAt: Long) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileWriter(tmp).use { fw ->
            JsonWriter(fw).use { writer ->
                writer.setIndent("  ")
                writer.beginObject()
                writer.name("version").value(2)
                writer.name("appName").value("TIRUp")
                writer.name("exportedAt").value(exportedAt)
                writer.name("settings")
                writeSettingsObject(writer, settings)
                writer.endObject()
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    private fun writeSettingsObject(writer: JsonWriter, settings: UserSettings) {
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

        writer.name("isAutoBackupEnabled").value(settings.isAutoBackupEnabled)
        writer.name("isLockscreenNotificationEnabled").value(settings.isLockscreenNotificationEnabled)
        writer.name("widgetBackgroundOpacity").value(settings.widgetBackgroundOpacity)
        writer.name("isFloatingBubbleEnabled").value(settings.isFloatingBubbleEnabled)
        writer.name("isFloatingBubbleAlwaysVisible").value(settings.isFloatingBubbleAlwaysVisible)

        writer.name("isDeviceRemindersEnabled").value(settings.isDeviceRemindersEnabled)
        writer.name("isSensorReminderEnabled").value(settings.isSensorReminderEnabled)
        writer.name("isPumpReminderEnabled").value(settings.isPumpReminderEnabled)
        writer.name("isLancetReminderEnabled").value(settings.isLancetReminderEnabled)

        writer.name("sensorStatus")
        writer.beginObject()
        writer.name("installedAt").value(settings.sensorStatus.installedAt)
        writer.name("durationDays").value(settings.sensorStatus.durationDays)
        writer.name("lastUsedDurationDays").value(settings.sensorStatus.lastUsedDurationDays)
        writer.endObject()

        writer.name("pumpSetStatus")
        writer.beginObject()
        writer.name("installedAt").value(settings.pumpSetStatus.installedAt)
        writer.name("durationDays").value(settings.pumpSetStatus.durationDays)
        writer.name("lastUsedDurationDays").value(settings.pumpSetStatus.lastUsedDurationDays)
        writer.endObject()

        writer.name("lancetStatus")
        writer.beginObject()
        writer.name("installedAt").value(settings.lancetStatus.installedAt)
        writer.name("durationDays").value(settings.lancetStatus.durationDays)
        writer.name("lastUsedDurationDays").value(settings.lancetStatus.lastUsedDurationDays)
        writer.endObject()

        // Alert Settings
        val a = settings.alertSettings
        writer.name("alertSettings")
        writer.beginObject()
        writer.name("isAlertsMasterEnabled").value(a.isAlertsMasterEnabled)
        writer.name("isPredictiveEnabled").value(a.isPredictiveEnabled)
        writer.name("predictiveMinutesAhead").value(a.predictiveMinutesAhead)
        writer.name("isPredictiveVibrate").value(a.isPredictiveVibrate)
        writer.name("isPredictiveFlash").value(a.isPredictiveFlash)
        writer.name("isMainEnabled").value(a.isMainEnabled)
        writer.name("mainConsecutivePoints").value(a.mainConsecutivePoints)
        writer.name("isMainVibrate").value(a.isMainVibrate)
        writer.name("isMainFlash").value(a.isMainFlash)
        writer.name("mainLowThresholdMmol").value(a.mainLowThresholdMmol)
        writer.name("mainHighThresholdMmol").value(a.mainHighThresholdMmol)
        writer.name("isCriticalEnabled").value(a.isCriticalEnabled)
        writer.name("criticalHypoMinutes").value(a.criticalHypoMinutes)
        writer.name("criticalHyperMinutes").value(a.criticalHyperMinutes)
        writer.name("isCriticalVibrate").value(a.isCriticalVibrate)
        writer.name("isCriticalFlash").value(a.isCriticalFlash)
        writer.name("criticalHypoPauseUntilTimestamp").value(a.criticalHypoPauseUntilTimestamp)
        writer.name("isCriticalHypoPermanentDisabled").value(a.isCriticalHypoPermanentDisabled)
        writer.name("isSignalLossEnabled").value(a.isSignalLossEnabled)
        writer.name("signalLossMinutes").value(a.signalLossMinutes)
        writer.name("isSignalLossVibrate").value(a.isSignalLossVibrate)
        writer.name("isSignalLossFlash").value(a.isSignalLossFlash)
        writer.name("snoozeHypoMinutes").value(a.snoozeHypoMinutes)
        writer.name("snoozeHyperMinutes").value(a.snoozeHyperMinutes)
        writer.name("isLastChanceAlertEnabled").value(a.isLastChanceAlertEnabled)
        writer.name("lastChanceBufferMinutes").value(a.lastChanceBufferMinutes)
        writer.name("isEmergencySmsEnabled").value(a.isEmergencySmsEnabled)
        writer.name("emergencyContactPhone").value(a.emergencyContactPhone)
        writer.name("emergencyContactName").value(a.emergencyContactName)
        writer.name("secondaryEmergencyContactPhone").value(a.secondaryEmergencyContactPhone)
        writer.name("secondaryEmergencyContactName").value(a.secondaryEmergencyContactName)
        writer.name("emergencySmsDelayMinutes").value(a.emergencySmsDelayMinutes)
        writer.name("includeLocationInEmergencySms").value(a.includeLocationInEmergencySms)
        writer.name("lastEmergencySmsTimestamp").value(a.lastEmergencySmsTimestamp)
        writer.name("isSmsQueryReplyEnabled").value(a.isSmsQueryReplyEnabled)
        writer.endObject()

        // BLE Bridge Settings
        val ble = settings.bleBridgeSettings
        writer.name("bleBridgeSettings")
        writer.beginObject()
        writer.name("role").value(ble.role.name)
        writer.name("isEnabled").value(ble.isEnabled)
        writer.name("familyPin").value(ble.familyPin)
        writer.name("transmitBattery").value(ble.transmitBattery)
        writer.endObject()

        // HbA1c Lab Records
        writer.name("isHba1cReminderEnabled").value(settings.isHba1cReminderEnabled)
        writer.name("hba1cSkippedQuarterTimestamp").value(settings.hba1cSkippedQuarterTimestamp)
        writer.name("hba1cRemindersCountInCycle").value(settings.hba1cRemindersCountInCycle)
        writer.name("lastHba1cReminderTimestamp").value(settings.lastHba1cReminderTimestamp)
        writer.name("lastYearEndDigestShownYear").value(settings.lastYearEndDigestShownYear)
        writer.name("hba1cRecords")
        writer.beginArray()
        for (rec in settings.hba1cRecords) {
            writer.beginObject()
            writer.name("id").value(rec.id)
            writer.name("timestamp").value(rec.timestamp)
            writer.name("valuePercent").value(rec.valuePercent)
            writer.name("labName").value(rec.labName)
            writer.name("notes").value(rec.notes)
            writer.endObject()
        }
        writer.endArray()

        writer.endObject()
    }

    private suspend fun writeReadingsCsvFile(
        file: File,
        database: AppDatabase,
        totalCount: Long,
        startTime: Long? = null,
        endTime: Long? = null
    ) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val pageSize = 5000
        var offset = 0

        FileWriter(tmp).use { fw ->
            val bw = BufferedWriter(fw, 32768)
            bw.write("Timestamp,DateTime,Glucose_mmol,TrendArrow,IOB,COB\n")
            while (offset < totalCount) {
                val page = if (startTime != null && endTime != null) {
                    database.glucoseReadingDao().getReadingsBetweenPaginated(startTime, endTime, pageSize, offset)
                } else {
                    database.glucoseReadingDao().getReadingsPaginated(limit = pageSize, offset = offset)
                }
                if (page.isEmpty()) break
                for (r in page) {
                    val dt = dateFormat.format(Date(r.timestamp))
                    val arrow = r.trendArrow ?: ""
                    val iob = r.iob?.toString() ?: ""
                    val cob = r.cob?.toString() ?: ""
                    bw.write("${r.timestamp},$dt,${r.valueMmol},$arrow,$iob,$cob\n")
                }
                offset += page.size
            }
            bw.flush()
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    private suspend fun writeTreatmentsCsvFile(
        file: File,
        database: AppDatabase,
        totalCount: Long,
        startTime: Long? = null,
        endTime: Long? = null
    ) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val pageSize = 1000
        var offset = 0

        FileWriter(tmp).use { fw ->
            val bw = BufferedWriter(fw, 16384)
            bw.write("Timestamp,DateTime,InsulinUnits,CarbsGrams,Notes,Source\n")
            while (offset < totalCount) {
                val page = if (startTime != null && endTime != null) {
                    database.treatmentDao().getTreatmentsBetweenPaginated(startTime, endTime, pageSize, offset)
                } else {
                    database.treatmentDao().getTreatmentsPaginated(limit = pageSize, offset = offset)
                }
                if (page.isEmpty()) break
                for (t in page) {
                    val dt = dateFormat.format(Date(t.timestamp))
                    val ins = t.insulinUnits?.toString() ?: ""
                    val carbs = t.carbsGrams?.toString() ?: ""
                    val notes = escapeCsv(t.notes ?: "")
                    val src = escapeCsv(t.source)
                    bw.write("${t.timestamp},$dt,$ins,$carbs,$notes,$src\n")
                }
                offset += page.size
            }
            bw.flush()
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    private suspend fun writeLegacyBackupJsonFile(
        file: File,
        database: AppDatabase,
        settings: UserSettings,
        totalCount: Long,
        earliest: Long,
        latest: Long,
        now: Long,
        startTime: Long? = null,
        endTime: Long? = null
    ) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileWriter(tmp).use { fw ->
            JsonWriter(fw).use { writer ->
                writer.setIndent("  ")
                writer.beginObject()
                writer.name("version").value(1)
                writer.name("appName").value("TIRUp")
                writer.name("exportedAt").value(now)
                writer.name("readingsCount").value(totalCount)
                writer.name("earliestTimestamp").value(earliest)
                writer.name("latestTimestamp").value(latest)

                writer.name("settings")
                writeSettingsObject(writer, settings)

                writer.name("readings")
                writer.beginArray()
                val pageSize = 5000
                var offset = 0
                while (offset < totalCount) {
                    val page = if (startTime != null && endTime != null) {
                        database.glucoseReadingDao().getReadingsBetweenPaginated(startTime, endTime, pageSize, offset)
                    } else {
                        database.glucoseReadingDao().getReadingsPaginated(limit = pageSize, offset = offset)
                    }
                    if (page.isEmpty()) break
                    page.forEach { r ->
                        writer.beginObject()
                        writer.name("t").value(r.timestamp)
                        writer.name("v").value(r.valueMmol)
                        if (!r.trendArrow.isNullOrEmpty()) writer.name("a").value(r.trendArrow)
                        if (r.iob != null) writer.name("iob").value(r.iob)
                        if (r.cob != null) writer.name("cob").value(r.cob)
                        writer.endObject()
                    }
                    offset += page.size
                }
                writer.endArray()
                writer.endObject()
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    private fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    /**
     * Reads quick header summary from the backup directory.
     */
    fun getBackupSummary(context: Context? = null): BackupSummary? {
        val pubDir = getPublicBackupDirectory()
        val intDir = getInternalBackupDirectory(context)

        val candidateDirs = listOf(pubDir, intDir).distinctBy { it.absolutePath }
        for (dir in candidateDirs) {
            val settingsFile = File(dir, SETTINGS_FILE_NAME)
            val readingsFile = File(dir, READINGS_FILE_NAME)
            val legacyFile = File(dir, LEGACY_BACKUP_FILE_NAME)

            if (settingsFile.exists() && settingsFile.length() > 0L) {
                val summary = readSummaryFromFiles(dir, settingsFile, readingsFile)
                if (summary != null) return summary
            }

            if (legacyFile.exists() && legacyFile.length() > 0L) {
                val summary = readSummaryFromLegacyJson(legacyFile)
                if (summary != null) return summary
            }
        }
        return null
    }

    private fun readSummaryFromFiles(dir: File, settingsFile: File, readingsFile: File): BackupSummary? {
        return try {
            var patientName = ""
            var diabetesType = ""
            var exportedAt = settingsFile.lastModified()

            FileReader(settingsFile).use { fr ->
                JsonReader(fr).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "exportedAt" -> exportedAt = reader.nextLong()
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

            var readingsCount = 0
            var earliest = 0L
            var latest = 0L
            if (readingsFile.exists() && readingsFile.length() > 0L) {
                BufferedReader(FileReader(readingsFile)).use { br ->
                    br.readLine() // Header
                    var isFirst = true
                    while (true) {
                        val line = br.readLine() ?: break
                        if (line.isNotBlank()) {
                            readingsCount++
                            val ts = line.substringBefore(',').toLongOrNull()
                            if (ts != null && ts > 0L) {
                                if (isFirst) {
                                    earliest = ts
                                    isFirst = false
                                }
                                latest = ts
                            }
                        }
                    }
                }
            }

            var treatmentsCount = 0
            val treatFile = File(dir, TREATMENTS_FILE_NAME)
            if (treatFile.exists() && treatFile.length() > 0L) {
                BufferedReader(FileReader(treatFile)).use { br ->
                    br.readLine() // Header
                    while (br.readLine() != null) {
                        treatmentsCount++
                    }
                }
            }

            BackupSummary(
                patientName = patientName,
                readingsCount = readingsCount,
                treatmentsCount = treatmentsCount,
                exportedAt = exportedAt,
                earliestTimestamp = earliest,
                latestTimestamp = latest,
                diabetesType = diabetesType,
                backupFile = settingsFile,
                backupLocation = dir.absolutePath,
                hasSettings = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "readSummaryFromFiles error: ${e.message}")
            null
        }
    }

    private fun readSummaryFromLegacyJson(file: File): BackupSummary? {
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
                treatmentsCount = 0,
                exportedAt = exportedAt,
                earliestTimestamp = earliest,
                latestTimestamp = latest,
                diabetesType = diabetesType,
                backupFile = file,
                backupLocation = file.parent ?: "",
                hasSettings = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "readSummaryFromLegacyJson error: ${e.message}")
            null
        }
    }

    /**
     * Creates a ZIP archive containing tirup_settings.json, tirup_readings.csv, and tirup_treatments.csv.
     * The file is created in context.cacheDir/backups/ and ready to be shared via FileProvider or saved to SAF.
     */
    suspend fun createZipBackup(
        context: Context,
        database: AppDatabase,
        settings: UserSettings
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "backups")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val zipFile = File(cacheDir, "tirup_backup_$dateStr.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Settings JSON
            zos.putNextEntry(ZipEntry(SETTINGS_FILE_NAME))
            val sw = StringWriter()
            JsonWriter(sw).use { jw ->
                jw.setIndent("  ")
                jw.beginObject()
                jw.name("version").value(2)
                jw.name("appName").value("TIRUp")
                jw.name("exportedAt").value(System.currentTimeMillis())
                jw.name("settings")
                writeSettingsObject(jw, settings)
                jw.endObject()
            }
            zos.write(sw.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Readings CSV
            zos.putNextEntry(ZipEntry(READINGS_FILE_NAME))
            val readingsBw = BufferedWriter(OutputStreamWriter(zos, Charsets.UTF_8))
            readingsBw.write("Timestamp,DateTime,Glucose_mmol,TrendArrow,IOB,COB\n")
            val totalReadings = database.glucoseReadingDao().getTotalCount()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            var offset = 0
            while (offset < totalReadings) {
                val page = database.glucoseReadingDao().getReadingsPaginated(limit = 5000, offset = offset)
                if (page.isEmpty()) break
                for (r in page) {
                    val dt = dateFormat.format(Date(r.timestamp))
                    readingsBw.write("${r.timestamp},$dt,${r.valueMmol},${r.trendArrow ?: ""},${r.iob ?: ""},${r.cob ?: ""}\n")
                }
                offset += page.size
            }
            readingsBw.flush()
            zos.closeEntry()

            // 3. Treatments CSV
            val totalTreatments = database.treatmentDao().getTotalCount()
            if (totalTreatments > 0) {
                zos.putNextEntry(ZipEntry(TREATMENTS_FILE_NAME))
                val treatBw = BufferedWriter(OutputStreamWriter(zos, Charsets.UTF_8))
                treatBw.write("Timestamp,DateTime,InsulinUnits,CarbsGrams,Notes,Source\n")
                var tOffset = 0
                while (tOffset < totalTreatments) {
                    val page = database.treatmentDao().getTreatmentsPaginated(limit = 1000, offset = tOffset)
                    if (page.isEmpty()) break
                    for (t in page) {
                        val dt = dateFormat.format(Date(t.timestamp))
                        treatBw.write("${t.timestamp},$dt,${t.insulinUnits ?: ""},${t.carbsGrams ?: ""},${escapeCsv(t.notes ?: "")},${escapeCsv(t.source)}\n")
                    }
                    tOffset += page.size
                }
                treatBw.flush()
                zos.closeEntry()
            }
        }
        zipFile
    }

    /**
     * Inspects a backup file chosen by user (ZIP, JSON or CSV) without full loading into database.
     */
    suspend fun inspectBackupUri(context: Context, uri: Uri): BackupSummary? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bufferedIn = BufferedInputStream(inputStream, 65536)
            bufferedIn.mark(32)
            val header = ByteArray(16)
            val readBytes = bufferedIn.read(header, 0, 16)
            bufferedIn.reset()

            val isZip = readBytes >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()

            if (isZip) {
                inspectZipBackup(bufferedIn)
            } else {
                inspectFlatBackup(bufferedIn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "inspectBackupUri error: ${e.message}", e)
            null
        }
    }

    private fun inspectZipBackup(inputStream: InputStream): BackupSummary? {
        var patientName = ""
        var diabetesType = ""
        var readingsCount = 0
        var treatmentsCount = 0
        var exportedAt = 0L
        var earliest = 0L
        var latest = 0L
        var hasSettings = false

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                when {
                    name.equals(SETTINGS_FILE_NAME, ignoreCase = true) -> {
                        hasSettings = true
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        JsonReader(StringReader(content)).use { reader ->
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "exportedAt" -> exportedAt = reader.nextLong()
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
                    name.equals(READINGS_FILE_NAME, ignoreCase = true) -> {
                        val br = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
                        br.readLine() // Header
                        var isFirst = true
                        while (true) {
                            val line = br.readLine() ?: break
                            if (line.isNotBlank()) {
                                readingsCount++
                                val ts = line.substringBefore(',').toLongOrNull()
                                if (ts != null && ts > 0L) {
                                    if (isFirst) {
                                        earliest = ts
                                        isFirst = false
                                    }
                                    latest = ts
                                }
                            }
                        }
                    }
                    name.equals(TREATMENTS_FILE_NAME, ignoreCase = true) -> {
                        val br = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
                        br.readLine() // Header
                        while (br.readLine() != null) {
                            treatmentsCount++
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        return BackupSummary(
            patientName = patientName,
            readingsCount = readingsCount,
            treatmentsCount = treatmentsCount,
            exportedAt = exportedAt,
            earliestTimestamp = earliest,
            latestTimestamp = latest,
            diabetesType = diabetesType,
            backupLocation = "ZIP Archive",
            hasSettings = hasSettings
        )
    }

    private fun inspectFlatBackup(inputStream: InputStream): BackupSummary? {
        val br = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        br.mark(2048)
        val firstChar = br.read().toChar()
        br.reset()

        if (firstChar == '{') {
            // JSON backup
            return try {
                var patientName = ""
                var diabetesType = ""
                var readingsCount = 0
                var exportedAt = 0L
                var earliest = 0L
                var latest = 0L
                var hasSettings = false

                JsonReader(br).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "exportedAt" -> exportedAt = reader.nextLong()
                            "readingsCount" -> readingsCount = reader.nextInt()
                            "earliestTimestamp" -> earliest = reader.nextLong()
                            "latestTimestamp" -> latest = reader.nextLong()
                            "settings" -> {
                                hasSettings = true
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
                            "readings" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    readingsCount++
                                    reader.skipValue()
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }

                BackupSummary(
                    patientName = patientName,
                    readingsCount = readingsCount,
                    treatmentsCount = 0,
                    exportedAt = exportedAt,
                    earliestTimestamp = earliest,
                    latestTimestamp = latest,
                    diabetesType = diabetesType,
                    backupLocation = "JSON File",
                    hasSettings = hasSettings
                )
            } catch (e: Exception) {
                Log.e(TAG, "inspect JSON error: ${e.message}")
                null
            }
        } else {
            // CSV backup
            val line = br.readLine() ?: return null
            var count = 0
            val isReadings = line.contains("Glucose", ignoreCase = true) || line.contains("BG", ignoreCase = true)
            while (br.readLine() != null) {
                count++
            }
            return BackupSummary(
                readingsCount = if (isReadings) count else 0,
                treatmentsCount = if (!isReadings) count else 0,
                backupLocation = "CSV File",
                hasSettings = false
            )
        }
    }

    /**
     * Restores backup from a local folder or default location (for backward compatibility).
     */
    suspend fun restoreBackup(
        context: Context? = null,
        database: AppDatabase,
        settingsRepository: SettingsRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pubDir = getPublicBackupDirectory()
            val intDir = getInternalBackupDirectory(context)
            val candidateDirs = listOf(pubDir, intDir).distinctBy { it.absolutePath }

            for (dir in candidateDirs) {
                val settingsFile = File(dir, SETTINGS_FILE_NAME)
                val readingsFile = File(dir, READINGS_FILE_NAME)
                val treatmentsFile = File(dir, TREATMENTS_FILE_NAME)
                val legacyFile = File(dir, LEGACY_BACKUP_FILE_NAME)

                if (settingsFile.exists() || readingsFile.exists()) {
                    var restoredCount = 0
                    if (settingsFile.exists() && settingsFile.length() > 0L) {
                        FileReader(settingsFile).use { fr ->
                            JsonReader(fr).use { reader ->
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "settings") {
                                        val s = parseSettings(reader)
                                        applySettings(s, settingsRepository)
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                        }
                    }

                    if (readingsFile.exists() && readingsFile.length() > 0L) {
                        restoredCount = restoreReadingsFromCsvStream(FileInputStream(readingsFile), database)
                    }

                    if (treatmentsFile.exists() && treatmentsFile.length() > 0L) {
                        restoreTreatmentsFromCsvStream(FileInputStream(treatmentsFile), database)
                    }

                    return@withContext Result.success(restoredCount)
                }

                if (legacyFile.exists() && legacyFile.length() > 0L) {
                    val res = restoreLegacyJson(legacyFile, database, settingsRepository)
                    if (res.isSuccess) return@withContext res
                }
            }

            Result.failure(IllegalStateException("No backup files found in Documents/TIRUp/Backups or app storage"))
        } catch (e: Exception) {
            Log.e(TAG, "restoreBackup error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Full restore from user-selected URI (ZIP, JSON or CSV).
     */
    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
        database: AppDatabase,
        settingsRepository: SettingsRepository
    ): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream"))

            val bufferedIn = BufferedInputStream(inputStream, 65536)
            bufferedIn.mark(32)
            val header = ByteArray(16)
            val readBytes = bufferedIn.read(header, 0, 16)
            bufferedIn.reset()

            val isZip = readBytes >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()

            var readingsRestored = 0
            var treatmentsRestored = 0
            var settingsRestored = false

            if (isZip) {
                ZipInputStream(bufferedIn).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.substringAfterLast('/')
                        when {
                            name.equals(SETTINGS_FILE_NAME, ignoreCase = true) -> {
                                val content = zis.readBytes().toString(Charsets.UTF_8)
                                JsonReader(StringReader(content)).use { reader ->
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        if (reader.nextName() == "settings") {
                                            val s = parseSettings(reader)
                                            applySettings(s, settingsRepository)
                                            settingsRestored = true
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                            }
                            name.equals(READINGS_FILE_NAME, ignoreCase = true) -> {
                                readingsRestored += restoreReadingsFromCsvStream(zis, database, closeStream = false)
                            }
                            name.equals(TREATMENTS_FILE_NAME, ignoreCase = true) -> {
                                treatmentsRestored += restoreTreatmentsFromCsvStream(zis, database, closeStream = false)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } else {
                val br = BufferedReader(InputStreamReader(bufferedIn, Charsets.UTF_8))
                br.mark(1024)
                val firstChar = br.read().toChar()
                br.reset()

                if (firstChar == '{') {
                    // JSON format (either new or legacy)
                    var restoredSettings: UserSettings? = null
                    val restoredReadings = mutableListOf<GlucoseReadingEntity>()

                    JsonReader(br).use { reader ->
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

                    if (restoredSettings != null) {
                        applySettings(restoredSettings!!, settingsRepository)
                        settingsRestored = true
                    }

                    val batchSize = 1000
                    for (i in restoredReadings.indices step batchSize) {
                        val end = (i + batchSize).coerceAtMost(restoredReadings.size)
                        database.glucoseReadingDao().insertBatch(restoredReadings.subList(i, end))
                    }
                    readingsRestored = restoredReadings.size
                } else {
                    // CSV format
                    val firstLine = br.readLine() ?: ""
                    if (firstLine.contains("Insulin", ignoreCase = true) || firstLine.contains("Carbs", ignoreCase = true)) {
                        treatmentsRestored = restoreTreatmentsFromCsvStream(bufferedIn, database)
                    } else {
                        readingsRestored = restoreReadingsFromCsvStream(bufferedIn, database)
                    }
                }
            }

            Result.success(
                BackupRestoreResult(
                    readingsRestored = readingsRestored,
                    treatmentsRestored = treatmentsRestored,
                    settingsRestored = settingsRestored
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromUri error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun applySettings(s: UserSettings, settingsRepository: SettingsRepository) {
        val current = settingsRepository.getSettings().first()
        val mergedPump = if (s.pumpSetStatus.installedAt > 0L) s.pumpSetStatus else current.pumpSetStatus
        val mergedSensor = if (s.sensorStatus.installedAt > 0L) s.sensorStatus else current.sensorStatus
        val mergedLancet = if (s.lancetStatus.installedAt > 0L) s.lancetStatus else current.lancetStatus
        settingsRepository.updateSettings(
            s.copy(
                hasSeenOnboarding = true,
                lastBackupTimestamp = System.currentTimeMillis(),
                pumpSetStatus = mergedPump,
                sensorStatus = mergedSensor,
                lancetStatus = mergedLancet
            )
        )
    }

    private fun restoreReadingsFromCsvStream(inputStream: InputStream, database: AppDatabase, closeStream: Boolean = true): Int {
        var count = 0
        try {
            val br = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            br.readLine() ?: return 0 // Header
            val batch = ArrayList<GlucoseReadingEntity>(1000)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            while (true) {
                val line = br.readLine() ?: break
                if (line.isBlank()) continue
                val parts = line.split(',')
                if (parts.size >= 3) {
                    var ts = parts[0].toLongOrNull() ?: 0L
                    if (ts <= 0L && parts[1].isNotBlank()) {
                        try {
                            ts = dateFormat.parse(parts[1])?.time ?: 0L
                        } catch (_: Exception) {}
                    }
                    val bg = parts[2].toDoubleOrNull() ?: 0.0
                    if (ts > 0L && bg > 0.0) {
                        val arrow = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                        val iob = parts.getOrNull(4)?.toDoubleOrNull()
                        val cob = parts.getOrNull(5)?.toDoubleOrNull()
                        batch.add(
                            GlucoseReadingEntity(
                                timestamp = ts,
                                valueMmol = bg,
                                trendArrow = arrow,
                                iob = iob,
                                cob = cob
                            )
                        )
                        count++
                        if (batch.size >= 1000) {
                            runBlockingSafe { database.glucoseReadingDao().insertBatch(batch) }
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                runBlockingSafe { database.glucoseReadingDao().insertBatch(batch) }
            }
        } finally {
            if (closeStream) {
                try { inputStream.close() } catch (_: Exception) {}
            }
        }
        return count
    }

    private fun restoreTreatmentsFromCsvStream(inputStream: InputStream, database: AppDatabase, closeStream: Boolean = true): Int {
        var count = 0
        try {
            val br = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            br.readLine() ?: return 0 // Header
            val batch = ArrayList<TreatmentEntity>(500)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            while (true) {
                val line = br.readLine() ?: break
                if (line.isBlank()) continue
                val parts = parseCsvLine(line)
                if (parts.size >= 2) {
                    var ts = parts[0].toLongOrNull() ?: 0L
                    if (ts <= 0L && parts[1].isNotBlank()) {
                        try {
                            ts = dateFormat.parse(parts[1])?.time ?: 0L
                        } catch (_: Exception) {}
                    }
                    val insulin = parts.getOrNull(2)?.toDoubleOrNull()
                    val carbs = parts.getOrNull(3)?.toDoubleOrNull()
                    val notes = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
                    val source = parts.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "XDRIP"

                    if (ts > 0L && (insulin != null || carbs != null || notes != null)) {
                        batch.add(
                            TreatmentEntity(
                                timestamp = ts,
                                insulinUnits = insulin,
                                carbsGrams = carbs,
                                notes = notes,
                                source = source
                            )
                        )
                        count++
                        if (batch.size >= 500) {
                            runBlockingSafe { database.treatmentDao().insertBatch(batch) }
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                runBlockingSafe { database.treatmentDao().insertBatch(batch) }
            }
        } finally {
            if (closeStream) {
                try { inputStream.close() } catch (_: Exception) {}
            }
        }
        return count
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun runBlockingSafe(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            block()
        }
    }

    private suspend fun restoreLegacyJson(
        file: File,
        database: AppDatabase,
        settingsRepository: SettingsRepository
    ): Result<Int> {
        return try {
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

            if (restoredSettings != null) {
                applySettings(restoredSettings!!, settingsRepository)
            }

            val batchSize = 1000
            for (i in restoredReadings.indices step batchSize) {
                val end = (i + batchSize).coerceAtMost(restoredReadings.size)
                database.glucoseReadingDao().insertBatch(restoredReadings.subList(i, end))
            }
            Result.success(restoredReadings.size)
        } catch (e: Exception) {
            Log.e(TAG, "restoreLegacyJson error: ${e.message}", e)
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

        var isAutoBackupEnabled = true
        var isLockscreenEnabled = true
        var widgetOpacity = 85
        var isFloatingBubble = false
        var isFloatingBubbleAlwaysVisible = false
        var alertSettings = AlertSettings()
        var bleBridgeSettings = BleBridgeSettings()

        var isDeviceReminders = true
        var isSensorReminder = true
        var isPumpReminder = true
        var isLancetReminder = true
        var sensorStatus = SensorStatus()
        var pumpSetStatus = PumpSetStatus()
        var lancetStatus = LancetStatus()
        var hba1cRecords = emptyList<com.tirup.app.domain.model.LabHba1cRecord>()
        var isHba1cReminder = true
        var hba1cSkippedQuarterTimestamp = 0L
        var hba1cRemindersCountInCycle = 0
        var lastHba1cReminderTimestamp = 0L
        var lastYearEndDigestShownYear = 0

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
                "isAutoBackupEnabled" -> isAutoBackupEnabled = reader.nextBoolean()
                "isLockscreenNotificationEnabled" -> isLockscreenEnabled = reader.nextBoolean()
                "widgetBackgroundOpacity" -> widgetOpacity = reader.nextInt()
                "isFloatingBubbleEnabled" -> isFloatingBubble = reader.nextBoolean()
                "isFloatingBubbleAlwaysVisible" -> isFloatingBubbleAlwaysVisible = reader.nextBoolean()
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
                "alertSettings" -> {
                    alertSettings = parseAlertSettings(reader)
                }
                "bleBridgeSettings" -> {
                    var role = BleBridgeRole.DISABLED
                    var isEnabled = true
                    var familyPin = ""
                    var transmitBattery = true
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "role" -> role = try { BleBridgeRole.valueOf(reader.nextString()) } catch (_: Exception) { BleBridgeRole.DISABLED }
                            "isEnabled" -> isEnabled = reader.nextBoolean()
                            "familyPin" -> familyPin = reader.nextString()
                            "transmitBattery" -> transmitBattery = reader.nextBoolean()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    bleBridgeSettings = BleBridgeSettings(role = role, isEnabled = isEnabled, familyPin = familyPin, transmitBattery = transmitBattery)
                }
                "isDeviceRemindersEnabled" -> isDeviceReminders = reader.nextBoolean()
                "isSensorReminderEnabled" -> isSensorReminder = reader.nextBoolean()
                "isPumpReminderEnabled" -> isPumpReminder = reader.nextBoolean()
                "isLancetReminderEnabled" -> isLancetReminder = reader.nextBoolean()
                "sensorStatus" -> {
                    var installedAt = 0L
                    var duration = 14
                    var lastUsed = 14
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "installedAt" -> installedAt = reader.nextLong()
                            "durationDays" -> duration = reader.nextInt()
                            "lastUsedDurationDays" -> lastUsed = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    sensorStatus = SensorStatus(installedAt, duration, lastUsed)
                }
                "pumpSetStatus" -> {
                    var installedAt = 0L
                    var duration = 3
                    var lastUsed = 3
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "installedAt" -> installedAt = reader.nextLong()
                            "durationDays" -> duration = reader.nextInt()
                            "lastUsedDurationDays" -> lastUsed = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    pumpSetStatus = PumpSetStatus(installedAt, duration, lastUsed)
                }
                "lancetStatus" -> {
                    var installedAt = 0L
                    var duration = 7
                    var lastUsed = 7
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "installedAt" -> installedAt = reader.nextLong()
                            "durationDays" -> duration = reader.nextInt()
                            "lastUsedDurationDays" -> lastUsed = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    lancetStatus = LancetStatus(installedAt, duration, lastUsed)
                }
                "isHba1cReminderEnabled" -> isHba1cReminder = reader.nextBoolean()
                "hba1cSkippedQuarterTimestamp" -> hba1cSkippedQuarterTimestamp = reader.nextLong()
                "hba1cRemindersCountInCycle" -> hba1cRemindersCountInCycle = reader.nextInt()
                "lastHba1cReminderTimestamp" -> lastHba1cReminderTimestamp = reader.nextLong()
                "lastYearEndDigestShownYear" -> lastYearEndDigestShownYear = reader.nextInt()
                "hba1cRecords" -> {
                    val list = mutableListOf<com.tirup.app.domain.model.LabHba1cRecord>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var id = 0L
                        var timestamp = 0L
                        var valuePercent = 0.0
                        var labName = ""
                        var notes = ""
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> id = reader.nextLong()
                                "timestamp" -> timestamp = reader.nextLong()
                                "valuePercent" -> valuePercent = reader.nextDouble()
                                "labName" -> labName = reader.nextString()
                                "notes" -> notes = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        list.add(com.tirup.app.domain.model.LabHba1cRecord(id, timestamp, valuePercent, labName, notes))
                    }
                    reader.endArray()
                    hba1cRecords = list
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
            isAutoBackupEnabled = isAutoBackupEnabled,
            isLockscreenNotificationEnabled = isLockscreenEnabled,
            widgetBackgroundOpacity = widgetOpacity,
            isFloatingBubbleEnabled = isFloatingBubble,
            isFloatingBubbleAlwaysVisible = isFloatingBubbleAlwaysVisible,
            alertSettings = alertSettings,
            bleBridgeSettings = bleBridgeSettings,
            isDeviceRemindersEnabled = isDeviceReminders,
            isSensorReminderEnabled = isSensorReminder,
            isPumpReminderEnabled = isPumpReminder,
            isLancetReminderEnabled = isLancetReminder,
            sensorStatus = sensorStatus,
            pumpSetStatus = pumpSetStatus,
            lancetStatus = lancetStatus,
            hba1cRecords = hba1cRecords,
            isHba1cReminderEnabled = isHba1cReminder,
            hba1cSkippedQuarterTimestamp = hba1cSkippedQuarterTimestamp,
            hba1cRemindersCountInCycle = hba1cRemindersCountInCycle,
            lastHba1cReminderTimestamp = lastHba1cReminderTimestamp,
            lastYearEndDigestShownYear = lastYearEndDigestShownYear,
            hasSeenOnboarding = true
        )
    }

    private fun parseAlertSettings(reader: JsonReader): AlertSettings {
        var alertsMaster = true
        var predEnabled = true
        var predMin = 15
        var predVib = true
        var predFlash = false
        var mainEnabled = true
        var mainPoints = 5
        var mainVib = true
        var mainFlash = false
        var mainLow = 3.9
        var mainHigh = 10.0
        var critEnabled = true
        var critHypoMin = 20
        var critHyperMin = 90
        var critVib = true
        var critFlash = true
        var critPause = 0L
        var critPermDisabled = false
        var sigLossEnabled = true
        var sigLossMin = 20
        var sigLossVib = true
        var sigLossFlash = false
        var snoozeHypo = 15
        var snoozeHyper = 45
        var lastChanceEnabled = true
        var lastChanceBuffer = 90
        var emergencySmsEnabled = false
        var emergencyContactPhone = ""
        var emergencyContactName = ""
        var secondaryPhone = ""
        var secondaryName = ""
        var emergencySmsDelay = 5
        var includeLocation = true
        var lastEmergencySms = 0L
        var smsQueryReply = true

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "isAlertsMasterEnabled" -> alertsMaster = reader.nextBoolean()
                "isPredictiveEnabled" -> predEnabled = reader.nextBoolean()
                "predictiveMinutesAhead" -> predMin = reader.nextInt()
                "isPredictiveVibrate" -> predVib = reader.nextBoolean()
                "isPredictiveFlash" -> predFlash = reader.nextBoolean()
                "isMainEnabled" -> mainEnabled = reader.nextBoolean()
                "mainConsecutivePoints" -> mainPoints = reader.nextInt()
                "isMainVibrate" -> mainVib = reader.nextBoolean()
                "isMainFlash" -> mainFlash = reader.nextBoolean()
                "mainLowThresholdMmol" -> mainLow = reader.nextDouble()
                "mainHighThresholdMmol" -> mainHigh = reader.nextDouble()
                "isCriticalEnabled" -> critEnabled = reader.nextBoolean()
                "criticalHypoMinutes" -> critHypoMin = reader.nextInt()
                "criticalHyperMinutes" -> critHyperMin = reader.nextInt()
                "isCriticalVibrate" -> critVib = reader.nextBoolean()
                "isCriticalFlash" -> critFlash = reader.nextBoolean()
                "criticalHypoPauseUntilTimestamp" -> critPause = reader.nextLong()
                "isCriticalHypoPermanentDisabled" -> critPermDisabled = reader.nextBoolean()
                "isSignalLossEnabled" -> sigLossEnabled = reader.nextBoolean()
                "signalLossMinutes" -> sigLossMin = reader.nextInt()
                "isSignalLossVibrate" -> sigLossVib = reader.nextBoolean()
                "isSignalLossFlash" -> sigLossFlash = reader.nextBoolean()
                "snoozeHypoMinutes" -> snoozeHypo = reader.nextInt()
                "snoozeHyperMinutes" -> snoozeHyper = reader.nextInt()
                "isLastChanceAlertEnabled" -> lastChanceEnabled = reader.nextBoolean()
                "lastChanceBufferMinutes" -> lastChanceBuffer = reader.nextInt()
                "isEmergencySmsEnabled" -> emergencySmsEnabled = reader.nextBoolean()
                "emergencyContactPhone" -> emergencyContactPhone = reader.nextString()
                "emergencyContactName" -> emergencyContactName = reader.nextString()
                "secondaryEmergencyContactPhone" -> secondaryPhone = reader.nextString()
                "secondaryEmergencyContactName" -> secondaryName = reader.nextString()
                "emergencySmsDelayMinutes" -> emergencySmsDelay = reader.nextInt()
                "includeLocationInEmergencySms" -> includeLocation = reader.nextBoolean()
                "lastEmergencySmsTimestamp" -> lastEmergencySms = reader.nextLong()
                "isSmsQueryReplyEnabled" -> smsQueryReply = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return AlertSettings(
            isAlertsMasterEnabled = alertsMaster,
            isPredictiveEnabled = predEnabled,
            predictiveMinutesAhead = predMin,
            isPredictiveVibrate = predVib,
            isPredictiveFlash = predFlash,
            isMainEnabled = mainEnabled,
            mainConsecutivePoints = mainPoints,
            isMainVibrate = mainVib,
            isMainFlash = mainFlash,
            mainLowThresholdMmol = mainLow,
            mainHighThresholdMmol = mainHigh,
            isCriticalEnabled = critEnabled,
            criticalHypoMinutes = critHypoMin,
            criticalHyperMinutes = critHyperMin,
            isCriticalVibrate = critVib,
            isCriticalFlash = critFlash,
            criticalHypoPauseUntilTimestamp = critPause,
            isCriticalHypoPermanentDisabled = critPermDisabled,
            isSignalLossEnabled = sigLossEnabled,
            signalLossMinutes = sigLossMin,
            isSignalLossVibrate = sigLossVib,
            isSignalLossFlash = sigLossFlash,
            snoozeHypoMinutes = snoozeHypo,
            snoozeHyperMinutes = snoozeHyper,
            isLastChanceAlertEnabled = lastChanceEnabled,
            lastChanceBufferMinutes = lastChanceBuffer,
            isEmergencySmsEnabled = emergencySmsEnabled,
            emergencyContactPhone = emergencyContactPhone,
            emergencyContactName = emergencyContactName,
            secondaryEmergencyContactPhone = secondaryPhone,
            secondaryEmergencyContactName = secondaryName,
            emergencySmsDelayMinutes = emergencySmsDelay,
            includeLocationInEmergencySms = includeLocation,
            lastEmergencySmsTimestamp = lastEmergencySms,
            isSmsQueryReplyEnabled = smsQueryReply
        )
    }

    private fun parseReadings(reader: JsonReader, outList: MutableList<GlucoseReadingEntity>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var timestamp = 0L
            var value = 0.0
            var arrow = ""
            var iob: Double? = null
            var cob: Double? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "t" -> timestamp = reader.nextLong()
                    "v" -> value = reader.nextDouble()
                    "a" -> arrow = reader.nextString()
                    "iob" -> iob = reader.nextDouble()
                    "cob" -> cob = reader.nextDouble()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (timestamp > 0L && value > 0.0) {
                outList.add(
                    GlucoseReadingEntity(
                        timestamp = timestamp,
                        valueMmol = value,
                        trendArrow = if (arrow.isNotBlank()) arrow else null,
                        iob = iob,
                        cob = cob
                    )
                )
            }
        }
        reader.endArray()
    }
}
