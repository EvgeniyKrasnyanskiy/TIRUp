package com.tirup.app.data.backup

import android.os.Environment
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.GlucoseReadingEntity
import com.tirup.app.domain.model.AlertSettings
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
    private const val BACKUP_DIR_NAME = "Backups"
    private const val BACKUP_FILE_NAME = "tirup_backup.json"
    private const val BACKUP_BAK_NAME = "tirup_backup.json.bak"
    private const val BACKUP_TMP_NAME = "tirup_backup.json.tmp"
    private const val ALARM_REQUEST_CODE = 9021

    fun getBackupDirectory(context: android.content.Context? = null): File {
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
     * Schedules exact 23:59:59 daily auto-backup alarm using AlarmManager.
     */
    fun scheduleNextDailyBackup(context: android.content.Context) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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

    fun cancelDailyBackup(context: android.content.Context) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
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

                    writer.name("isAutoBackupEnabled").value(settings.isAutoBackupEnabled)
                    writer.name("isLockscreenNotificationEnabled").value(settings.isLockscreenNotificationEnabled)
                    writer.name("widgetBackgroundOpacity").value(settings.widgetBackgroundOpacity)
                    writer.name("isFloatingBubbleEnabled").value(settings.isFloatingBubbleEnabled)

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
                        if (r.iob != null) writer.name("iob").value(r.iob)
                        if (r.cob != null) writer.name("cob").value(r.cob)
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

    private fun isDifferentDay(t1: Long, t2: Long): Boolean {
        if (t1 <= 0L || t2 <= 0L) return true
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(java.util.Calendar.YEAR) != c2.get(java.util.Calendar.YEAR) ||
               c1.get(java.util.Calendar.DAY_OF_YEAR) != c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * Checks if auto-backup is enabled and if backup should be executed:
     * - If forced (e.g. 23:59:59 alarm or profile update)
     * - Or if a previous day's backup was missed (e.g. phone was off at 23:59:59)
     * - Or if it's the very first backup
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

        var isAutoBackupEnabled = true
        var isLockscreenEnabled = false
        var widgetOpacity = 85
        var isFloatingBubble = false
        var alertSettings = AlertSettings()

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
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    alertSettings = AlertSettings(
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
                        lastChanceBufferMinutes = lastChanceBuffer
                    )
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
            alertSettings = alertSettings,
            hasSeenOnboarding = true
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
