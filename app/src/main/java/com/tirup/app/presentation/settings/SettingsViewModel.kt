package com.tirup.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val showClearDialog: Boolean = false,
    val infoMessage: String? = null
)

class SettingsViewModel(
    private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val glucoseRepository: GlucoseRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(userSettings = settings) }
            }
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(language = language)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(unit = unit)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun setThemeMode(mode: com.tirup.app.domain.model.ThemeMode) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(themeMode = mode)
            settingsRepository.updateSettings(updated)
        }
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(hasSeenOnboarding = hasSeen)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdateNightHours(nightStart: Int, nightEnd: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(
                nightStartHour = nightStart,
                nightEndHour = nightEnd
            )
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdateThresholds(
        tirLow: Double,
        tirHigh: Double,
        tingHigh: Double,
        tirGoal: Int,
        tingGoal: Int,
        nightStart: Int,
        nightEnd: Int
    ) {
        viewModelScope.launch {
            val updatedRanges = TargetRanges(
                tirLowMmol = tirLow,
                tirHighMmol = tirHigh,
                tingHighMmol = tingHigh,
                tirGoalPercent = tirGoal,
                tingGoalPercent = tingGoal
            )
            val updated = _uiState.value.userSettings.copy(
                targetRanges = updatedRanges,
                nightStartHour = nightStart,
                nightEndHour = nightEnd
            )
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdatePatientProfile(profile: PatientProfile) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(patientProfile = profile)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            AutoBackupManager.maybeTriggerAutoBackup(context, database, settingsRepository, force = true)
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isAutoBackupEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (enabled) {
                AutoBackupManager.scheduleNextDailyBackup(context)
                AutoBackupManager.maybeTriggerAutoBackup(context, database, settingsRepository, force = true)
            } else {
                AutoBackupManager.cancelDailyBackup(context)
            }
        }
    }

    fun updateAlertSettings(alerts: AlertSettings) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(alertSettings = alerts)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun testAlert(tier: com.tirup.app.data.alert.AlertTier) {
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        com.tirup.app.data.alert.GlucoseAlertManager.sendTestAlert(context, tier, isRu)
    }

    fun showClearConfirm(show: Boolean) {
        _uiState.update { it.copy(showClearDialog = show) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            glucoseRepository.clearAllData()
            val msg = if (isRu) "Все данные трансляции успешно очищены." else "All broadcast data cleared."
            _uiState.update { it.copy(showClearDialog = false, infoMessage = msg) }
        }
    }
}
