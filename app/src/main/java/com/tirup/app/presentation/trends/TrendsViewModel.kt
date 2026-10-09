package com.tirup.app.presentation.trends

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.calculator.WeeklyDigestCalculator
import com.tirup.app.domain.calculator.DetectedPattern
import com.tirup.app.domain.calculator.PatternSeverity
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
import kotlinx.coroutines.flow.first
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
        private const val PREFIX_DELETED_PATTERN = "deleted_pattern_"
        private const val PREFIX_INSIGHT = "dismissed_insight_"
        private const val PREFIX_FIRST_SEEN = "pattern_first_seen_"
    }

    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedPatternIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedPatternIds: StateFlow<Set<String>> = _dismissedPatternIds.asStateFlow()

    private val _expiredPatternIds = MutableStateFlow<Set<String>>(emptySet())
    val expiredPatternIds: StateFlow<Set<String>> = _expiredPatternIds.asStateFlow()

    private val _deletedPatternIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedPatternIds: StateFlow<Set<String>> = _deletedPatternIds.asStateFlow()

    private val _dismissedInsightIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedInsightIds: StateFlow<Set<String>> = _dismissedInsightIds.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TrendPeriod.PERIOD_7D)
    val selectedPeriod: StateFlow<TrendPeriod> = _selectedPeriod.asStateFlow()

    private val _customDays = MutableStateFlow(14)
    val customDays: StateFlow<Int> = _customDays.asStateFlow()

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
        val deletedPatterns = mutableSetOf<String>()

        allEntries.forEach { (key, value) ->
            val timestamp = (value as? Long) ?: return@forEach
            if (now - timestamp < DISMISSAL_COOLDOWN_MS) {
                if (key.startsWith(PREFIX_PATTERN)) {
                    activePatterns.add(key.removePrefix(PREFIX_PATTERN))
                } else if (key.startsWith(PREFIX_INSIGHT)) {
                    activeInsights.add(key.removePrefix(PREFIX_INSIGHT))
                } else if (key.startsWith(PREFIX_DELETED_PATTERN)) {
                    deletedPatterns.add(key.removePrefix(PREFIX_DELETED_PATTERN))
                }
            }
        }
        _dismissedPatternIds.value = activePatterns
        _dismissedInsightIds.value = activeInsights
        _deletedPatternIds.value = deletedPatterns

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

    fun registerDetectedPatterns(patterns: List<DetectedPattern>) {
        val sp = prefs ?: return
        val now = System.currentTimeMillis()
        val editor = sp.edit()
        var changed = false

        patterns.forEach { pattern ->
            if (pattern.id.isNotBlank() && pattern.id != "collecting_data") {
                val key = "$PREFIX_FIRST_SEEN${pattern.id}"
                val firstSeen = sp.getLong(key, 0L)
                if (firstSeen == 0L) {
                    editor.putLong(key, now)
                    changed = true
                }
            }
        }
        if (changed) {
            editor.apply()
        }
        _expiredPatternIds.value = emptySet()
    }

    @Suppress("UNUSED_PARAMETER")
    fun isPatternExpired(id: String, severity: PatternSeverity): Boolean {
        // Analytical patterns should remain visible in trends report for the selected interval
        return false
    }

    fun restorePattern(id: String) {
        val sp = prefs ?: return
        sp.edit()
            .remove("$PREFIX_PATTERN$id")
            .remove("$PREFIX_DELETED_PATTERN$id")
            .putLong("$PREFIX_FIRST_SEEN$id", System.currentTimeMillis())
            .apply()
        _dismissedPatternIds.value = _dismissedPatternIds.value - id
        _expiredPatternIds.value = _expiredPatternIds.value - id
        _deletedPatternIds.value = _deletedPatternIds.value - id
    }

    fun deleteArchivedPattern(id: String) {
        val sp = prefs ?: return
        val now = System.currentTimeMillis()
        sp.edit().putLong("$PREFIX_DELETED_PATTERN$id", now).apply()
        _deletedPatternIds.value = _deletedPatternIds.value + id
    }

    fun clearArchivedPatterns(patternIds: List<String>) {
        val sp = prefs ?: return
        val editor = sp.edit()
        val now = System.currentTimeMillis()
        patternIds.forEach { id ->
            editor.putLong("$PREFIX_DELETED_PATTERN$id", now)
        }
        editor.apply()
        _deletedPatternIds.value = _deletedPatternIds.value + patternIds
    }

    fun dismissInsight(id: String) {
        val now = System.currentTimeMillis()
        prefs?.edit()?.putLong("$PREFIX_INSIGHT$id", now)?.apply()
        _dismissedInsightIds.value = _dismissedInsightIds.value + id
    }

    fun openWeeklyDigest() {
        _isDigestSheetOpen.value = true
        if (_weeklyDigest.value == null) {
            viewModelScope.launch {
                try {
                    val settings = settingsRepository.getSettings().first()
                    val referenceTime = System.currentTimeMillis()
                    val digestStartTime = referenceTime - (14L * 86400000L)
                    val readings = glucoseRepository.getReadingsBetween(digestStartTime, referenceTime).first()
                    _weeklyDigest.value = WeeklyDigestCalculator.calculateForReferenceTimestamp(
                        allReadings = readings,
                        referenceTime = referenceTime,
                        settings = settings
                    )
                } catch (e: Exception) {
                    android.util.Log.e("TrendsViewModel", "Error calculating weekly digest eagerly: ${e.message}")
                }
            }
        }
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
                _customDays,
                glucoseRepository.getLatestReading()
            ) { period, customDays, latestReading ->
                Triple(period, customDays, latestReading)
            }.flatMapLatest { (period, customDays, latestReading) ->
                val now = System.currentTimeMillis()
                // Use latest reading timestamp or now as reference point
                val referenceTime = latestReading?.timestamp ?: now
                val effectiveDays = if (period == TrendPeriod.PERIOD_CUSTOM) customDays else period.days

                val startTime = when {
                    period == TrendPeriod.PERIOD_1D -> {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = referenceTime
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        cal.timeInMillis
                    }
                    effectiveDays > 0 -> referenceTime - (effectiveDays.toLong() * 86400000L)
                    else -> 0L // All time
                }

                val endTime = if (effectiveDays > 0) {
                    referenceTime + 86400000L
                } else {
                    Long.MAX_VALUE
                }

                // Query at least 14 days for weekly digest comparison
                val digestStartTime = referenceTime - (14L * 86400000L)
                val queryStartTime = if (effectiveDays > 0) minOf(startTime, digestStartTime) else 0L

                glucoseRepository.getReadingsBetween(queryStartTime, endTime).combine(
                    settingsRepository.getSettings()
                ) { allReadings, latestSettings ->
                    val periodReadings = if (effectiveDays > 0) {
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
                        maxDays = if (effectiveDays > 0) effectiveDays.coerceAtMost(30) else 30
                    )

                    val compensator = TargetCompensatorCalculator.calculateStrategicCompensator(
                        targetMode = TargetMode.TIR,
                        targetGoalPercent = latestSettings.targetRanges.tirGoalPercent.toDouble(),
                        readings = periodReadings,
                        periodDays = effectiveDays,
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
                        customDays = customDays,
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

    fun selectPeriod(period: TrendPeriod, customDays: Int? = null) {
        if (customDays != null && customDays > 0) {
            _customDays.value = customDays
        }
        _selectedPeriod.value = period
    }
}
