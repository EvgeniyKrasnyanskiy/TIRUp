package com.tirup.app.presentation.reports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.importer.StreamingGlucoseImporter
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import com.tirup.app.presentation.trends.TrendPeriod
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
import java.io.FileInputStream
import java.io.FileOutputStream

data class ReportsUiState(
    val selectedPeriod: TrendPeriod = TrendPeriod.PERIOD_14D,
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val userSettings: UserSettings = UserSettings(),
    val isGenerating: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val generatedPdfFile: File? = null,
    val readings: List<GlucoseReading> = emptyList()
)

sealed interface ReportEvent {
    data class SharePdf(val shareIntent: Intent) : ReportEvent
    data class SavedToDownloads(val filePath: String) : ReportEvent
    data class Error(val message: String) : ReportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val context: Context,
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository,
    private val streamingImporter: StreamingGlucoseImporter
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TrendPeriod.PERIOD_14D)
    val selectedPeriod: StateFlow<TrendPeriod> = _selectedPeriod.asStateFlow()

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
                _selectedPeriod,
                glucoseRepository.getLatestReading(),
                settingsRepository.getSettings()
            ) { period, latest, settings ->
                Triple(period, latest, settings)
            }.flatMapLatest { (period, latest, settings) ->
                val now = System.currentTimeMillis()
                val referenceTime = latest?.timestamp ?: now
                val startTime = if (period.days > 0) {
                    referenceTime - (period.days.toLong() * 86400000L)
                } else {
                    0L
                }
                val endTime = if (period.days > 0) referenceTime + 86400000L else Long.MAX_VALUE

                glucoseRepository.getReadingsBetween(startTime, endTime).combine(
                    settingsRepository.getSettings()
                ) { readings, latestSettings ->
                    val stats = GlucoseMetricsCalculator.calculateStatistics(readings, latestSettings.targetRanges)
                    ReportsUiState(
                        selectedPeriod = period,
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

    fun selectPeriod(period: TrendPeriod) {
        _selectedPeriod.value = period
    }

    fun importFileAndGenerate(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importMessage = null)
            val result = streamingImporter.importFromUri(uri)
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)

            result.onSuccess { total ->
                val msg = if (total > 0) {
                    if (isRu) "Импортировано $total точек. Отчёт обновлён." else "Imported $total points. Report updated."
                } else {
                    if (isRu) "Файл не содержит точек сахара." else "No glucose readings found in file."
                }
                _uiState.value = _uiState.value.copy(isImporting = false, importMessage = msg)
            }.onFailure { err ->
                val prefix = if (isRu) "Ошибка: " else "Error: "
                _uiState.value = _uiState.value.copy(isImporting = false, importMessage = prefix + err.message)
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

    fun savePdfToDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true)

            val currentState = _uiState.value
            val result = pdfGenerator.generateAgpReport(
                readings = currentState.readings,
                statistics = currentState.statistics,
                userSettings = currentState.userSettings
            )

            result.onSuccess { pdfFile ->
                val fileName = "TIRUp_AGP_Report_${System.currentTimeMillis()}.pdf"
                var savedPath = ""

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                FileInputStream(pdfFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            savedPath = "Downloads/$fileName"
                        }
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val destFile = File(downloadsDir, fileName)
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        savedPath = destFile.absolutePath
                    }

                    _uiState.value = _uiState.value.copy(isGenerating = false, generatedPdfFile = pdfFile)
                    _events.emit(ReportEvent.SavedToDownloads(savedPath))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isGenerating = false)
                    _events.emit(ReportEvent.Error("Save failed: ${e.message}"))
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGenerating = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Generation failed"))
            }
        }
    }
}
