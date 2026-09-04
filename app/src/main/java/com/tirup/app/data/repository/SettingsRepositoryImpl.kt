package com.tirup.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepositoryImpl(
    context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tirup_user_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())

    override fun getSettings(): Flow<UserSettings> = _settingsFlow.asStateFlow()

    override suspend fun updateSettings(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_LANG, settings.language)
            .putString(KEY_UNIT, settings.unit.name)
            .putString(KEY_TARGET_MODE, settings.targetMode.name)
            .putFloat(KEY_TIR_LOW, settings.targetRanges.tirLowMmol.toFloat())
            .putFloat(KEY_TIR_HIGH, settings.targetRanges.tirHighMmol.toFloat())
            .putFloat(KEY_TING_HIGH, settings.targetRanges.tingHighMmol.toFloat())
            .putInt(KEY_TIR_GOAL, settings.targetRanges.tirGoalPercent)
            .putInt(KEY_TING_GOAL, settings.targetRanges.tingGoalPercent)
            .putInt(KEY_PERIOD_DAYS, settings.periodDays)
            .putInt(KEY_NIGHT_START, settings.nightStartHour)
            .putInt(KEY_NIGHT_END, settings.nightEndHour)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_PATIENT_NAME, settings.patientProfile.fullName)
            .putString(KEY_PATIENT_GENDER, settings.patientProfile.gender)
            .putInt(KEY_PATIENT_BIRTH_YEAR, settings.patientProfile.birthYear)
            .putInt(KEY_PATIENT_BIRTH_MONTH, settings.patientProfile.birthMonth)
            .putString(KEY_PATIENT_HEIGHT, settings.patientProfile.heightCm)
            .putString(KEY_PATIENT_WEIGHT, settings.patientProfile.weightKg)
            .putString(KEY_DIABETES_TYPE, settings.patientProfile.diabetesType)
            .putInt(KEY_DIAGNOSIS_YEAR, settings.patientProfile.diagnosisYear)
            .putString(KEY_THERAPY_TYPE, settings.patientProfile.therapyType)
            .putBoolean(KEY_IS_AUTO_BACKUP_ENABLED, settings.isAutoBackupEnabled)
            .putLong(KEY_LAST_BACKUP_TIMESTAMP, settings.lastBackupTimestamp)
            .putBoolean(KEY_HAS_SEEN_ONBOARDING, settings.hasSeenOnboarding)
            .putInt(KEY_LAST_STREAK_CELEBRATED_DAYS, settings.lastStreakCelebratedDays)
            .putString(KEY_METRICS_ORDER, settings.metricsOrder.joinToString(","))
            .putString(KEY_HIDDEN_METRICS, settings.hiddenMetrics.joinToString(","))
            .putBoolean(KEY_IS_LOCKSCREEN_NOTIFICATION_ENABLED, settings.isLockscreenNotificationEnabled)
            // Alert Settings
            .putBoolean(KEY_ALERT_MASTER_ENABLED, settings.alertSettings.isAlertsMasterEnabled)
            .putBoolean(KEY_ALERT_PREDICTIVE_ENABLED, settings.alertSettings.isPredictiveEnabled)
            .putInt(KEY_ALERT_PREDICTIVE_MINUTES, settings.alertSettings.predictiveMinutesAhead)
            .putBoolean(KEY_ALERT_PREDICTIVE_VIBRATE, settings.alertSettings.isPredictiveVibrate)
            .putBoolean(KEY_ALERT_PREDICTIVE_FLASH, settings.alertSettings.isPredictiveFlash)
            .putBoolean(KEY_ALERT_MAIN_ENABLED, settings.alertSettings.isMainEnabled)
            .putInt(KEY_ALERT_MAIN_POINTS, settings.alertSettings.mainConsecutivePoints)
            .putBoolean(KEY_ALERT_MAIN_VIBRATE, settings.alertSettings.isMainVibrate)
            .putBoolean(KEY_ALERT_MAIN_FLASH, settings.alertSettings.isMainFlash)
            .putBoolean(KEY_ALERT_CRITICAL_ENABLED, settings.alertSettings.isCriticalEnabled)
            .putInt(KEY_ALERT_CRITICAL_HYPO_MIN, settings.alertSettings.criticalHypoMinutes)
            .putInt(KEY_ALERT_CRITICAL_HYPER_MIN, settings.alertSettings.criticalHyperMinutes)
            .putBoolean(KEY_ALERT_CRITICAL_VIBRATE, settings.alertSettings.isCriticalVibrate)
            .putBoolean(KEY_ALERT_CRITICAL_FLASH, settings.alertSettings.isCriticalFlash)
            .putLong(KEY_ALERT_CRITICAL_HYPO_PAUSE, settings.alertSettings.criticalHypoPauseUntilTimestamp)
            .putBoolean(KEY_ALERT_CRITICAL_HYPO_PERM_DISABLED, settings.alertSettings.isCriticalHypoPermanentDisabled)
            .putInt(KEY_ALERT_SNOOZE_HYPO, settings.alertSettings.snoozeHypoMinutes)
            .putInt(KEY_ALERT_SNOOZE_HYPER, settings.alertSettings.snoozeHyperMinutes)
            .putBoolean(KEY_ALERT_SIGNAL_LOSS_ENABLED, settings.alertSettings.isSignalLossEnabled)
            .putBoolean(KEY_ALERT_SIGNAL_LOSS_FLASH, settings.alertSettings.isSignalLossFlash)
            .putBoolean(KEY_ALERT_LAST_CHANCE_ENABLED, settings.alertSettings.isLastChanceAlertEnabled)
            .putInt(KEY_ALERT_LAST_CHANCE_BUFFER, settings.alertSettings.lastChanceBufferMinutes)
            .apply()

        _settingsFlow.value = settings
    }

    private fun loadSettings(): UserSettings {
        val lang = prefs.getString(KEY_LANG, "RU") ?: "RU"
        val unitName = prefs.getString(KEY_UNIT, GlucoseUnit.MMOL_L.name) ?: GlucoseUnit.MMOL_L.name
        val unit = try {
            GlucoseUnit.valueOf(unitName)
        } catch (_: Exception) {
            GlucoseUnit.MMOL_L
        }

        val modeName = prefs.getString(KEY_TARGET_MODE, TargetMode.TIR.name) ?: TargetMode.TIR.name
        val targetMode = try {
            TargetMode.valueOf(modeName)
        } catch (_: Exception) {
            TargetMode.TIR
        }

        val themeModeName = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeName)
        } catch (_: Exception) {
            ThemeMode.DARK
        }

        val tirLow = prefs.getFloat(KEY_TIR_LOW, 3.9f).toDouble()
        val tirHigh = prefs.getFloat(KEY_TIR_HIGH, 10.0f).toDouble()
        val tingHigh = prefs.getFloat(KEY_TING_HIGH, 7.8f).toDouble()
        val tirGoal = prefs.getInt(KEY_TIR_GOAL, 70)
        val tingGoal = prefs.getInt(KEY_TING_GOAL, 50)
        val periodDays = prefs.getInt(KEY_PERIOD_DAYS, 14)
        val nightStart = prefs.getInt(KEY_NIGHT_START, 0)
        val nightEnd = prefs.getInt(KEY_NIGHT_END, 6)
        val isAutoBackupEnabled = prefs.getBoolean(KEY_IS_AUTO_BACKUP_ENABLED, true)
        val lastBackupTimestamp = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        val hasSeenOnboarding = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

        val profile = PatientProfile(
            fullName = prefs.getString(KEY_PATIENT_NAME, "") ?: "",
            gender = prefs.getString(KEY_PATIENT_GENDER, "M") ?: "M",
            birthYear = prefs.getInt(KEY_PATIENT_BIRTH_YEAR, 1990),
            birthMonth = prefs.getInt(KEY_PATIENT_BIRTH_MONTH, 1),
            heightCm = prefs.getString(KEY_PATIENT_HEIGHT, "") ?: "",
            weightKg = prefs.getString(KEY_PATIENT_WEIGHT, "") ?: "",
            diabetesType = prefs.getString(KEY_DIABETES_TYPE, "СД1") ?: "СД1",
            diagnosisYear = prefs.getInt(KEY_DIAGNOSIS_YEAR, 2018),
            therapyType = prefs.getString(KEY_THERAPY_TYPE, "Инсулиновая помпа") ?: "Инсулиновая помпа"
        )

        return UserSettings(
            language = lang,
            unit = unit,
            targetMode = targetMode,
            targetRanges = TargetRanges(
                tirLowMmol = tirLow,
                tirHighMmol = tirHigh,
                tingHighMmol = tingHigh,
                tirGoalPercent = tirGoal,
                tingGoalPercent = tingGoal
            ),
            periodDays = periodDays,
            nightStartHour = nightStart,
            nightEndHour = nightEnd,
            themeMode = themeMode,
            patientProfile = profile,
            isAutoBackupEnabled = isAutoBackupEnabled,
            lastBackupTimestamp = lastBackupTimestamp,
            hasSeenOnboarding = hasSeenOnboarding,
            lastStreakCelebratedDays = prefs.getInt(KEY_LAST_STREAK_CELEBRATED_DAYS, 0),
            metricsOrder = prefs.getString(KEY_METRICS_ORDER, null)?.split(",")?.filter { it.isNotBlank() }
                ?: com.tirup.app.domain.model.DEFAULT_METRICS_ORDER,
            hiddenMetrics = prefs.getString(KEY_HIDDEN_METRICS, null)?.split(",")?.filter { it.isNotBlank() }
                ?: emptyList(),
            isLockscreenNotificationEnabled = prefs.getBoolean(KEY_IS_LOCKSCREEN_NOTIFICATION_ENABLED, false),
            alertSettings = AlertSettings(
                isAlertsMasterEnabled = prefs.getBoolean(KEY_ALERT_MASTER_ENABLED, true),
                isPredictiveEnabled = prefs.getBoolean(KEY_ALERT_PREDICTIVE_ENABLED, true),
                predictiveMinutesAhead = prefs.getInt(KEY_ALERT_PREDICTIVE_MINUTES, 15),
                isPredictiveVibrate = prefs.getBoolean(KEY_ALERT_PREDICTIVE_VIBRATE, true),
                isPredictiveFlash = prefs.getBoolean(KEY_ALERT_PREDICTIVE_FLASH, false),
                isMainEnabled = prefs.getBoolean(KEY_ALERT_MAIN_ENABLED, true),
                mainConsecutivePoints = prefs.getInt(KEY_ALERT_MAIN_POINTS, 5),
                isMainVibrate = prefs.getBoolean(KEY_ALERT_MAIN_VIBRATE, true),
                isMainFlash = prefs.getBoolean(KEY_ALERT_MAIN_FLASH, false),
                isCriticalEnabled = prefs.getBoolean(KEY_ALERT_CRITICAL_ENABLED, true),
                criticalHypoMinutes = prefs.getInt(KEY_ALERT_CRITICAL_HYPO_MIN, 20),
                criticalHyperMinutes = prefs.getInt(KEY_ALERT_CRITICAL_HYPER_MIN, 90),
                isCriticalVibrate = prefs.getBoolean(KEY_ALERT_CRITICAL_VIBRATE, true),
                isCriticalFlash = prefs.getBoolean(KEY_ALERT_CRITICAL_FLASH, true),
                criticalHypoPauseUntilTimestamp = prefs.getLong(KEY_ALERT_CRITICAL_HYPO_PAUSE, 0L),
                isCriticalHypoPermanentDisabled = prefs.getBoolean(KEY_ALERT_CRITICAL_HYPO_PERM_DISABLED, false),
                snoozeHypoMinutes = prefs.getInt(KEY_ALERT_SNOOZE_HYPO, 15),
                snoozeHyperMinutes = prefs.getInt(KEY_ALERT_SNOOZE_HYPER, 45),
                isSignalLossEnabled = prefs.getBoolean(KEY_ALERT_SIGNAL_LOSS_ENABLED, true),
                isSignalLossFlash = prefs.getBoolean(KEY_ALERT_SIGNAL_LOSS_FLASH, false),
                isLastChanceAlertEnabled = prefs.getBoolean(KEY_ALERT_LAST_CHANCE_ENABLED, true),
                lastChanceBufferMinutes = prefs.getInt(KEY_ALERT_LAST_CHANCE_BUFFER, 90)
            )
        )
    }

    companion object {
        private const val KEY_LANG = "key_language"
        private const val KEY_UNIT = "key_unit"
        private const val KEY_TARGET_MODE = "key_target_mode"
        private const val KEY_TIR_LOW = "key_tir_low"
        private const val KEY_TIR_HIGH = "key_tir_high"
        private const val KEY_TING_HIGH = "key_ting_high"
        private const val KEY_TIR_GOAL = "key_tir_goal"
        private const val KEY_TING_GOAL = "key_ting_goal"
        private const val KEY_PERIOD_DAYS = "key_period_days"
        private const val KEY_NIGHT_START = "key_night_start"
        private const val KEY_NIGHT_END = "key_night_end"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_PATIENT_NAME = "key_patient_name"
        private const val KEY_PATIENT_GENDER = "key_patient_gender"
        private const val KEY_PATIENT_BIRTH_YEAR = "key_patient_birth_year"
        private const val KEY_PATIENT_BIRTH_MONTH = "key_patient_birth_month"
        private const val KEY_PATIENT_HEIGHT = "key_patient_height"
        private const val KEY_PATIENT_WEIGHT = "key_patient_weight"
        private const val KEY_DIABETES_TYPE = "key_diabetes_type"
        private const val KEY_DIAGNOSIS_YEAR = "key_diagnosis_year"
        private const val KEY_THERAPY_TYPE = "key_therapy_type"
        private const val KEY_IS_AUTO_BACKUP_ENABLED = "key_is_auto_backup_enabled"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "key_last_backup_timestamp"
        private const val KEY_HAS_SEEN_ONBOARDING = "key_has_seen_onboarding"
        private const val KEY_LAST_STREAK_CELEBRATED_DAYS = "key_last_streak_celebrated_days"
        private const val KEY_METRICS_ORDER = "key_metrics_order"
        private const val KEY_HIDDEN_METRICS = "key_hidden_metrics"
        private const val KEY_IS_LOCKSCREEN_NOTIFICATION_ENABLED = "key_is_lockscreen_notification_enabled"

        private const val KEY_ALERT_MASTER_ENABLED = "key_alert_master_enabled"
        private const val KEY_ALERT_PREDICTIVE_ENABLED = "key_alert_predictive_enabled"
        private const val KEY_ALERT_PREDICTIVE_MINUTES = "key_alert_predictive_minutes"
        private const val KEY_ALERT_PREDICTIVE_VIBRATE = "key_alert_predictive_vibrate"
        private const val KEY_ALERT_PREDICTIVE_FLASH = "key_alert_predictive_flash"
        private const val KEY_ALERT_MAIN_ENABLED = "key_alert_main_enabled"
        private const val KEY_ALERT_MAIN_POINTS = "key_alert_main_points"
        private const val KEY_ALERT_MAIN_VIBRATE = "key_alert_main_vibrate"
        private const val KEY_ALERT_MAIN_FLASH = "key_alert_main_flash"
        private const val KEY_ALERT_CRITICAL_ENABLED = "key_alert_critical_enabled"
        private const val KEY_ALERT_CRITICAL_HYPO_MIN = "key_alert_critical_hypo_min"
        private const val KEY_ALERT_CRITICAL_HYPER_MIN = "key_alert_critical_hyper_min"
        private const val KEY_ALERT_CRITICAL_VIBRATE = "key_alert_critical_vibrate"
        private const val KEY_ALERT_CRITICAL_FLASH = "key_alert_critical_flash"
        private const val KEY_ALERT_CRITICAL_HYPO_PAUSE = "key_alert_critical_hypo_pause"
        private const val KEY_ALERT_CRITICAL_HYPO_PERM_DISABLED = "key_alert_critical_hypo_perm_disabled"
        private const val KEY_ALERT_SNOOZE_HYPO = "key_alert_snooze_hypo"
        private const val KEY_ALERT_SNOOZE_HYPER = "key_alert_snooze_hyper"
        private const val KEY_ALERT_SIGNAL_LOSS_ENABLED = "key_alert_signal_loss_enabled"
        private const val KEY_ALERT_SIGNAL_LOSS_FLASH = "key_alert_signal_loss_flash"
        private const val KEY_ALERT_LAST_CHANCE_ENABLED = "key_alert_last_chance_enabled"
        private const val KEY_ALERT_LAST_CHANCE_BUFFER = "key_alert_last_chance_buffer"
    }
}
