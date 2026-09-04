package com.tirup.app.presentation.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.alert.ActiveAlertBanner
import com.tirup.app.data.alert.AlertTier
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.calculator.GlucoseTrendPredictor
import com.tirup.app.domain.calculator.PredictedEvent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FocusViewModel(
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context? = null
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
                settingsRepository.getSettings(),
                GlucoseAlertManager.activeAlertBanner
            ) { latest, recent, streak, settings, alertBanner ->
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


                val compensator = TargetCompensatorCalculator.calculateDailyCompensator(
                    targetMode = settings.targetMode,
                    targetPercent = targetPercent,
                    latestReading = latest,
                    recentReadings = effectiveReadings,
                    targetRanges = settings.targetRanges,
                    language = settings.language
                )

                // Compute alert banner: if GlucoseAlertManager has an active banner, use it.
                // Otherwise, calculate live predictive trend (e.g. "Быстро падает ... Возможна ГИПО в HH:mm")
                val effectiveAlertBanner = alertBanner ?: run {
                    if (latest != null && recent.size >= 5) {
                        val sorted = recent.sortedBy { it.timestamp }
                        val prediction = GlucoseTrendPredictor.predictTrend(
                            readings = sorted.takeLast(12),
                            targetRanges = settings.targetRanges,
                            minutesAhead = 15,
                            useTingForHigh = settings.targetMode == TargetMode.TING
                        )
                        val isRu = settings.language.equals("RU", ignoreCase = true)
                        val now = latest.timestamp
                        if (prediction.event == PredictedEvent.PREDICTED_LOW) {
                            val minutesUntil = prediction.minutesUntilCrossing ?: 15
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now + minutesUntil * 60_000L))
                            val title = if (isRu) "📉 Скоро гипогликемия (в $timeStr)" else "📉 Predicted Low (at $timeStr)"
                            val message = if (isRu) {
                                String.format(Locale.US, "Быстро падает (%.2f ммоль/л/мин). Возможна ГИПО в %s", abs(prediction.rateOfChangeMmolPerMin), timeStr)
                            } else {
                                String.format(Locale.US, "Dropping fast (%.2f mmol/L/min). Possible low at %s", abs(prediction.rateOfChangeMmolPerMin), timeStr)
                            }
                            ActiveAlertBanner(
                                tier = AlertTier.PREDICTIVE,
                                title = title,
                                message = message,
                                timestamp = now
                            )
                        } else if (prediction.event == PredictedEvent.PREDICTED_HIGH) {
                            val minutesUntil = prediction.minutesUntilCrossing ?: 15
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now + minutesUntil * 60_000L))
                            val title = if (isRu) "📈 Скоро гипергликемия (в $timeStr)" else "📈 Predicted High (at $timeStr)"
                            val message = if (isRu) {
                                String.format(Locale.US, "Быстро растёт (%.2f ммоль/л/мин). Выход из нормы в %s", prediction.rateOfChangeMmolPerMin, timeStr)
                            } else {
                                String.format(Locale.US, "Rising fast (%.2f mmol/L/min). Leaving target at %s", prediction.rateOfChangeMmolPerMin, timeStr)
                            }
                            ActiveAlertBanner(
                                tier = AlertTier.PREDICTIVE,
                                title = title,
                                message = message,
                                timestamp = now
                            )
                        } else null
                    } else null
                }

                FocusUiState(
                    latestReading = latest,
                    recentReadings = effectiveReadings,
                    statistics = stats,
                    compensatorGoal = compensator,
                    streakDays = streak,
                    userSettings = settings,
                    activeAlertBanner = effectiveAlertBanner,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
                val latest = newState.latestReading
                if (context != null && latest != null && newState.userSettings.isLockscreenNotificationEnabled) {
                    GlucoseAlertManager.updateLockscreenNotification(
                        context = context,
                        latestReading = latest,
                        todayReadings = newState.recentReadings,
                        settings = newState.userSettings
                    )
                }
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
