package com.tirup.app.presentation.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File

data class ReportsUiState(
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val userSettings: UserSettings = UserSettings(),
    val isGenerating: Boolean = false,
    val generatedPdfFile: File? = null,
    val readings: List<GlucoseReading> = emptyList()
)

sealed interface ReportEvent {
    data class SharePdf(val shareIntent: Intent) : ReportEvent
    data class Error(val message: String) : ReportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val context: Context,
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    private val pdfGenerator = AgpPdfGenerator(context)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                glucoseRepository.getLatestReading(),
                settingsRepository.getSettings()
            ) { latest, settings ->
                Pair(latest, settings)
            }.flatMapLatest { (latest, settings) ->
                val now = System.currentTimeMillis()
                val referenceTime = latest?.timestamp ?: now
                val past14d = referenceTime - (14L * 86400000L)

                glucoseRepository.getReadingsBetween(past14d, referenceTime + 86400000L).combine(
                    settingsRepository.getSettings()
                ) { readings, latestSettings ->
                    val stats = GlucoseMetricsCalculator.calculateStatistics(readings, latestSettings.targetRanges)
                    ReportsUiState(
                        statistics = stats,
                        userSettings = latestSettings,
                        readings = readings
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun generateAndSharePdf() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true)

            val currentState = _uiState.value
            val result = pdfGenerator.generateAgpReport(
                readings = currentState.readings,
                statistics = currentState.statistics,
                userSettings = currentState.userSettings
            )

            result.onSuccess { pdfFile ->
                _uiState.value = _uiState.value.copy(isGenerating = false, generatedPdfFile = pdfFile)

                // Build Share Intent
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "TIRUp Ambulatory Glucose Profile (AGP) Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Share AGP Report").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                _events.emit(ReportEvent.SharePdf(chooser))
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGenerating = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Generation failed"))
            }
        }
    }
}
