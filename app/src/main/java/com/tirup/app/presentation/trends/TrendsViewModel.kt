package com.tirup.app.presentation.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TrendPeriod.PERIOD_14D)
    val selectedPeriod: StateFlow<TrendPeriod> = _selectedPeriod.asStateFlow()

    private val _uiState = MutableStateFlow(TrendsUiState(isLoading = true))
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                _selectedPeriod,
                settingsRepository.getSettings(),
                glucoseRepository.getLatestReading()
            ) { period, settings, latestReading ->
                Triple(period, settings, latestReading)
            }.flatMapLatest { (period, settings, latestReading) ->
                val now = System.currentTimeMillis()
                // Use latest reading timestamp or now as reference point
                val referenceTime = latestReading?.timestamp ?: now

                val startTime = if (period.days > 0) {
                    referenceTime - (period.days.toLong() * 86400000L)
                } else {
                    0L // All time
                }

                val endTime = if (period.days > 0) {
                    referenceTime + 86400000L
                } else {
                    Long.MAX_VALUE
                }

                glucoseRepository.getReadingsBetween(startTime, endTime).combine(
                    settingsRepository.getSettings()
                ) { readings, latestSettings ->
                    val stats = GlucoseMetricsCalculator.calculateStatistics(
                        readings = readings,
                        targetRanges = latestSettings.targetRanges,
                        nightStartHour = latestSettings.nightStartHour,
                        nightEndHour = latestSettings.nightEndHour,
                        language = latestSettings.language
                    )
                    val agpBins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)
                    val heatmap = AGPPercentilesCalculator.calculateHeatmap(
                        readings = readings,
                        targetRanges = latestSettings.targetRanges,
                        maxDays = if (period.days > 0) period.days.coerceAtMost(30) else 30
                    )

                    TrendsUiState(
                        selectedPeriod = period,
                        statistics = stats,
                        percentileBins = agpBins,
                        heatmapData = heatmap,
                        userSettings = latestSettings,
                        isLoading = false
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectPeriod(period: TrendPeriod) {
        _selectedPeriod.value = period
    }
}
