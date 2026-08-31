package com.tirup.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.importer.StreamingGlucoseImporter
import com.tirup.app.domain.model.GlucoseUnit
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
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val importMessage: String? = null,
    val showClearDialog: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val glucoseRepository: GlucoseRepository,
    private val streamingImporter: StreamingGlucoseImporter
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
        }
    }

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(unit = unit)
            settingsRepository.updateSettings(updated)
        }
    }

    fun updateThresholds(
        tirLow: Double,
        tirHigh: Double,
        tingHigh: Double,
        tirGoal: Int,
        tingGoal: Int
    ) {
        viewModelScope.launch {
            val updatedRanges = TargetRanges(
                tirLowMmol = tirLow,
                tirHighMmol = tirHigh,
                tingHighMmol = tingHigh,
                tirGoalPercent = tirGoal,
                tingGoalPercent = tingGoal
            )
            val updated = _uiState.value.userSettings.copy(targetRanges = updatedRanges)
            settingsRepository.updateSettings(updated)
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            _uiState.update { it.copy(isImporting = true, importedCount = 0, importMessage = null) }

            val result = streamingImporter.importFromUri(uri) { count ->
                _uiState.update { it.copy(importedCount = count) }
            }

            result.onSuccess { total ->
                val msg = if (total > 0) {
                    if (isRu) "Успешно импортировано $total записей." else "Successfully imported $total readings."
                } else {
                    if (isRu) "Записи сахара не найдены. Убедитесь, что внутри архива или CSV есть данные сахара." else "No glucose readings found in selected file."
                }
                _uiState.update { it.copy(isImporting = false, importMessage = msg) }
            }.onFailure { error ->
                val prefix = if (isRu) "Ошибка импорта: " else "Import error: "
                _uiState.update { it.copy(isImporting = false, importMessage = prefix + (error.localizedMessage ?: error.message)) }
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
            val msg = if (isRu) "Все данные успешно очищены." else "All data successfully cleared."
            _uiState.update { it.copy(showClearDialog = false, importMessage = msg) }
        }
    }
}
