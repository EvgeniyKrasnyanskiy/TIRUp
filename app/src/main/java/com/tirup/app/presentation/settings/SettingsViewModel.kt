package com.tirup.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val settingsRepository: SettingsRepository,
    private val glucoseRepository: GlucoseRepository
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

    fun updateThresholds(
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

    fun updatePatientProfile(profile: PatientProfile) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val updated = _uiState.value.userSettings.copy(patientProfile = profile)
            settingsRepository.updateSettings(updated)
            _uiState.update {
                it.copy(
                    userSettings = updated,
                    infoMessage = if (isRu) "Профиль пациента сохранён." else "Patient profile saved."
                )
            }
        }
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
