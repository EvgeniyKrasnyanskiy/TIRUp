package com.tirup.app.presentation.trends

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.calculator.WeeklyDigestCalculator
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.WeeklyDigest
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Calendar

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

    private val _weeklyDigest = MutableStateFlow<WeeklyDigest?>(null)
    val weeklyDigest: StateFlow<WeeklyDigest?> = _weeklyDigest.asStateFlow()

    private val _isDigestBannerDismissed = MutableStateFlow(false)
    val isDigestBannerDismissed: StateFlow<Boolean> = _isDigestBannerDismissed.asStateFlow()

    private val _isDigestSheetOpen = MutableStateFlow(false)
    val isDigestSheetOpen: StateFlow<Boolean> = _isDigestSheetOpen.asStateFlow()

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

        val cal = Calendar.getInstance()
        val currentWeek = cal.get(Calendar.WEEK_OF_YEAR)
        val currentYear = cal.get(Calendar.YEAR)
        val dismissedWeek = sp.getInt("dismissed_digest_week", -1)
        val dismissedYear = sp.getInt("dismissed_digest_year", -1)
        _isDigestBannerDismissed.value = (currentWeek == dismissedWeek && currentYear == dismissedYear)
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

    fun openWeeklyDigest() {
        _isDigestSheetOpen.value = true
    }

    fun closeWeeklyDigest() {
        _isDigestSheetOpen.value = false
    }

    fun dismissWeeklyDigestBanner() {
        _isDigestBannerDismissed.value = true
        val cal = Calendar.getInstance()
        val currentWeek = cal.get(Calendar.WEEK_OF_YEAR)
        val currentYear = cal.get(Calendar.YEAR)
        prefs?.edit()
            ?.putInt("dismissed_digest_week", currentWeek)
            ?.putInt("dismissed_digest_year", currentYear)
            ?.apply()
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

                // Query at least 14 days for weekly digest comparison
                val digestStartTime = referenceTime - (14L * 86400000L)
                val queryStartTime = if (period.days > 0) minOf(startTime, digestStartTime) else 0L

                glucoseRepository.getReadingsBetween(queryStartTime, endTime).combine(
                    settingsRepository.getSettings()
                ) { allReadings, latestSettings ->
                    val periodReadings = if (period.days > 0) {
                        allReadings.filter { it.timestamp in startTime..endTime }
                    } else {
                        allReadings
                    }

                    _weeklyDigest.value = WeeklyDigestCalculator.calculateForReferenceTimestamp(
                        allReadings = allReadings,
                        referenceTime = referenceTime,
                        settings = latestSettings
                    )

                    val stats = GlucoseMetricsCalculator.calculateStatistics(
                        readings = periodReadings,
                        targetRanges = latestSettings.targetRanges,
                        nightStartHour = latestSettings.nightStartHour,
                        nightEndHour = latestSettings.nightEndHour,
                        language = latestSettings.language,
                        unit = latestSettings.unit
                    )
                    val agpBins = AGPPercentilesCalculator.calculatePercentiles(periodReadings, binsCount = 48)
                    val heatmap = AGPPercentilesCalculator.calculateHeatmap(
                        readings = periodReadings,
                        targetRanges = latestSettings.targetRanges,
                        maxDays = if (period.days > 0) period.days.coerceAtMost(30) else 30
                    )

                    val compensator = TargetCompensatorCalculator.calculateStrategicCompensator(
                        targetMode = TargetMode.TIR,
                        targetGoalPercent = latestSettings.targetRanges.tirGoalPercent.toDouble(),
                        readings = periodReadings,
                        periodDays = period.days,
                        targetRanges = latestSettings.targetRanges,
                        language = latestSettings.language
                    )

                    val actualDaysCount = if (periodReadings.isNotEmpty()) {
                        val cal = java.util.Calendar.getInstance()
                        periodReadings.map { r ->
                            cal.timeInMillis = r.timestamp
                            cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
                        }.distinct().size
                    } else 0

                    TrendsUiState(
                        selectedPeriod = period,
                        statistics = stats,
                        compensatorGoal = compensator,
                        percentileBins = agpBins,
                        heatmapData = heatmap,
                        userSettings = latestSettings,
                        actualDaysCount = actualDaysCount,
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
