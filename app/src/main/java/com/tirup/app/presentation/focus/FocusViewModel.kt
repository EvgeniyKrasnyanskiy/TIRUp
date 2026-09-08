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
import com.tirup.app.domain.model.DailySummary
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.Treatment
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

private data class GlucoseDataTuple(
    val latest: GlucoseReading?,
    val recent: List<GlucoseReading>,
    val treatments: List<Treatment>,
    val summaries: List<DailySummary>
)

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
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val endOfDay = startOfDay + 86400000L - 1

            val glucoseDataFlow = combine(
                glucoseRepository.getLatestReading(),
                glucoseRepository.getRecentReadings(1440), // up to 24h of 1-min readings
                glucoseRepository.getTreatmentsBetween(startOfDay, endOfDay + 60_000L),
                glucoseRepository.getDailySummariesBetween(startOfDay - 30 * 86400000L, endOfDay)
            ) { latest, recent, treatments, summaries ->
                GlucoseDataTuple(latest, recent, treatments, summaries)
            }

            combine(
                glucoseDataFlow,
                glucoseRepository.getStreakDays(),
                settingsRepository.getSettings(),
                GlucoseAlertManager.activeAlertBanner
            ) { tuple, streak, settings, alertBanner ->
                val latest = tuple.latest
                val recent = tuple.recent
                val treatments = tuple.treatments
                val summaries = tuple.summaries
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
                            val title = if (isRu) "📉 Прогноз гипогликемии (через $minutesUntil мин)" else "📉 Predicted Low (in $minutesUntil min)"
                            val message = if (isRu) {
                                String.format(Locale.US, "Глюкоза падает (%.2f ммоль/л/мин).\nРекомендуется принять быстрые углеводы.", abs(prediction.rateOfChangeMmolPerMin))
                            } else {
                                String.format(Locale.US, "Glucose dropping (%.2f mmol/L/min).\nTake carbs now.", abs(prediction.rateOfChangeMmolPerMin))
                            }
                            ActiveAlertBanner(
                                tier = AlertTier.PREDICTIVE,
                                title = title,
                                message = message,
                                timestamp = now
                            )
                        } else if (prediction.event == PredictedEvent.PREDICTED_HIGH) {
                            val minutesUntil = prediction.minutesUntilCrossing ?: 15
                            val title = if (isRu) "📈 Прогноз гипергликемии (через $minutesUntil мин)" else "📈 Predicted High (in $minutesUntil min)"
                            val message = if (isRu) {
                                String.format(Locale.US, "Глюкоза растёт (%.2f ммоль/л/мин).\nПроверьте дозу инсулина.", prediction.rateOfChangeMmolPerMin)
                            } else {
                                String.format(Locale.US, "Glucose rising (%.2f mmol/L/min).\nCheck insulin.", prediction.rateOfChangeMmolPerMin)
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
                    treatments = treatments,
                    statistics = stats,
                    compensatorGoal = compensator,
                    streakDays = streak,
                    userSettings = settings,
                        sensorStatus = settings.sensorStatus,
                        pumpSetStatus = settings.pumpSetStatus,
                    activeAlertBanner = effectiveAlertBanner,
                    recentDailySummaries = summaries,
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
                        settings = newState.userSettings,
                        streakDays = newState.streakDays
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

    fun deleteTreatment(treatmentId: Long) {
        viewModelScope.launch {
            glucoseRepository.deleteTreatmentById(treatmentId)
        }
    }
    fun updateSensorInstalled(durationDays: Int) {
        val currentSettings = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(
                currentSettings.copy(
                    sensorStatus = SensorStatus(
                        installedAt = System.currentTimeMillis(),
                        durationDays = durationDays,
                        lastUsedDurationDays = durationDays
                    )
                )
            )
        }
    }

    fun updatePumpSetInstalled(durationDays: Int) {
        val currentSettings = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(
                currentSettings.copy(
                    pumpSetStatus = PumpSetStatus(
                        installedAt = System.currentTimeMillis(),
                        durationDays = durationDays,
                        lastUsedDurationDays = durationDays
                    )
                )
            )
        }
    }
}
