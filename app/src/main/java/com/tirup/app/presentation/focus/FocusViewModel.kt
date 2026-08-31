package com.tirup.app.presentation.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel(
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState(isLoading = true))
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                glucoseRepository.getLatestReading(),
                glucoseRepository.getRecentReadings(288), // last 24h of 5-min readings
                glucoseRepository.getStreakDays(),
                settingsRepository.getSettings()
            ) { latest, recent, streak, settings ->
                val stats = GlucoseMetricsCalculator.calculateStatistics(recent, settings.targetRanges)

                // Calculate compensator for selected mode (e.g. 7-day or 14-day window)
                val targetPercent = if (settings.targetMode == TargetMode.TIR) {
                    settings.targetRanges.tirGoalPercent.toDouble()
                } else {
                    settings.targetRanges.tingGoalPercent.toDouble()
                }

                val currentScore = if (settings.targetMode == TargetMode.TIR) {
                    stats.tirPercent
                } else {
                    stats.tingPercent
                }

                val compensator = TargetCompensatorCalculator.calculateCompensator(
                    targetMode = settings.targetMode,
                    targetPercent = targetPercent,
                    totalDays = settings.periodDays,
                    pastDays = (settings.periodDays / 2).coerceAtLeast(1),
                    pastAveragePercent = currentScore
                )

                FocusUiState(
                    latestReading = latest,
                    recentReadings = recent,
                    statistics = stats,
                    compensatorGoal = compensator,
                    streakDays = streak,
                    userSettings = settings,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun toggleTargetMode() {
        val currentSettings = _uiState.value.userSettings
        val newMode = if (currentSettings.targetMode == TargetMode.TIR) {
            TargetMode.TING
        } else {
            TargetMode.TIR
        }
        viewModelScope.launch {
            settingsRepository.updateSettings(currentSettings.copy(targetMode = newMode))
        }
    }
}
