package com.tirup.app.data.repository

import android.content.Context
import android.content.SharedPreferences
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
            hasSeenOnboarding = hasSeenOnboarding
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
    }
}
