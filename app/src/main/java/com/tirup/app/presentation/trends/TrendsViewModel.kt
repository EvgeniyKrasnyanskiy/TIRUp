package com.tirup.app.presentation.trends

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.TargetMode
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
    private val settingsRepository: SettingsRepository,
    context: Context? = null
) : ViewModel() {

    companion object {
        const val DISMISSAL_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000L // 14 days
        private const val PREFS_NAME = "tirup_trends_dismissed"
        private const val PREFIX_PATTERN = "dismissed_pattern_"
        private const val PREFIX_INSIGHT = "dismissed_insight_"
    }

    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedPatternIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedPatternIds: StateFlow<Set<String>> = _dismissedPatternIds.asStateFlow()

    private val _dismissedInsightIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedInsightIds: StateFlow<Set<String>> = _dismissedInsightIds.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TrendPeriod.PERIOD_14D)
    val selectedPeriod: StateFlow<TrendPeriod> = _selectedPeriod.asStateFlow()

    private val _uiState = MutableStateFlow(TrendsUiState(isLoading = true))
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        loadDismissedItems()
        observeData()
    }

    private fun loadDismissedItems() {
        val sp = prefs ?: return
        val now = System.currentTimeMillis()
        val allEntries = sp.all
        val activePatterns = mutableSetOf<String>()
        val activeInsights = mutableSetOf<String>()

        allEntries.forEach { (key, value) ->
            val timestamp = (value as? Long) ?: return@forEach
            if (now - timestamp < DISMISSAL_COOLDOWN_MS) {
                if (key.startsWith(PREFIX_PATTERN)) {
                    activePatterns.add(key.removePrefix(PREFIX_PATTERN))
                } else if (key.startsWith(PREFIX_INSIGHT)) {
                    activeInsights.add(key.removePrefix(PREFIX_INSIGHT))
                }
            }
        }
        _dismissedPatternIds.value = activePatterns
        _dismissedInsightIds.value = activeInsights
    }

    fun dismissPattern(id: String) {
        val now = System.currentTimeMillis()
        prefs?.edit()?.putLong("$PREFIX_PATTERN$id", now)?.apply()
        _dismissedPatternIds.value = _dismissedPatternIds.value + id
    }

    fun dismissInsight(id: String) {
        val now = System.currentTimeMillis()
        prefs?.edit()?.putLong("$PREFIX_INSIGHT$id", now)?.apply()
        _dismissedInsightIds.value = _dismissedInsightIds.value + id
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
                        language = latestSettings.language,
                        unit = latestSettings.unit
                    )
                    val agpBins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)
                    val heatmap = AGPPercentilesCalculator.calculateHeatmap(
                        readings = readings,
                        targetRanges = latestSettings.targetRanges,
                        maxDays = if (period.days > 0) period.days.coerceAtMost(30) else 30
                    )

                    val compensator = TargetCompensatorCalculator.calculateStrategicCompensator(
                        targetMode = TargetMode.TIR,
                        targetGoalPercent = latestSettings.targetRanges.tirGoalPercent.toDouble(),
                        readings = readings,
                        periodDays = period.days,
                        targetRanges = latestSettings.targetRanges,
                        language = latestSettings.language
                    )

                    TrendsUiState(
                        selectedPeriod = period,
                        statistics = stats,
                        compensatorGoal = compensator,
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
