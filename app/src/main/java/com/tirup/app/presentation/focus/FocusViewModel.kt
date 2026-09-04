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
                glucoseRepository.getRecentReadings(1440), // up to 24h of 1-min readings
                glucoseRepository.getStreakDays(),
                settingsRepository.getSettings()
            ) { latest, recent, streak, settings ->
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis
                val todayReadings = recent.filter { it.timestamp >= startOfDay }
                val effectiveReadings = if (todayReadings.isNotEmpty()) todayReadings else recent

                val stats = GlucoseMetricsCalculator.calculateStatistics(
                    readings = effectiveReadings,
                    targetRanges = settings.targetRanges,
                    nightStartHour = settings.nightStartHour,
                    nightEndHour = settings.nightEndHour,
                    language = settings.language,
                    unit = settings.unit
                )

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

                val compensator = TargetCompensatorCalculator.calculateDailyCompensator(
                    targetMode = settings.targetMode,
                    targetPercent = targetPercent,
                    latestReading = latest,
                    recentReadings = effectiveReadings,
                    targetRanges = settings.targetRanges,
                    language = settings.language
                )

                FocusUiState(
                    latestReading = latest,
                    recentReadings = effectiveReadings,
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

    fun setTargetMode(mode: TargetMode) {
        val currentSettings = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(currentSettings.copy(targetMode = mode))
        }
    }

    fun markStreakCelebrated(days: Int) {
        val currentSettings = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(currentSettings.copy(lastStreakCelebratedDays = days))
        }
    }

    fun updateMetricsConfiguration(newOrder: List<String>, hidden: List<String>) {
        val currentSettings = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(
                currentSettings.copy(
                    metricsOrder = newOrder,
                    hiddenMetrics = hidden
                )
            )
        }
    }

    fun updateMetricsOrder(newOrder: List<String>) {
        updateMetricsConfiguration(newOrder, _uiState.value.userSettings.hiddenMetrics)
    }
}
