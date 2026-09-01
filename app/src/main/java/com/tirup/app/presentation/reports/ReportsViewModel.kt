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
import com.tirup.app.data.local.AppDatabase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoricalReportData(
    val hasData: Boolean = false,
    val readings: List<GlucoseReading> = emptyList(),
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val dateRangeStr: String = "",
    val fileName: String = ""
)

data class ReportsUiState(
    val livePeriod: TrendPeriod = TrendPeriod.PERIOD_14D,
    val liveStatistics: GlucoseStatistics = GlucoseStatistics(),
    val liveReadings: List<GlucoseReading> = emptyList(),
    val historicalReport: HistoricalReportData = HistoricalReportData(),
    val userSettings: UserSettings = UserSettings(),
    val isGeneratingLive: Boolean = false,
    val isGeneratingHistorical: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val showLiveDetailDialog: Boolean = false,
    val showHistoricalDetailDialog: Boolean = false
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
    private val streamingImporter: StreamingGlucoseImporter,
    private val database: AppDatabase
) : ViewModel() {

    private val _livePeriod = MutableStateFlow(TrendPeriod.PERIOD_14D)
    val livePeriod: StateFlow<TrendPeriod> = _livePeriod.asStateFlow()

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    private val pdfGenerator = AgpPdfGenerator(context)

    init {
        observeLiveReport()
        observeHistoricalReport()
    }

    private fun observeLiveReport() {
        viewModelScope.launch {
            combine(
                _livePeriod,
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
                    val stats = GlucoseMetricsCalculator.calculateStatistics(
                        readings = readings,
                        targetRanges = latestSettings.targetRanges,
                        nightStartHour = latestSettings.nightStartHour,
                        nightEndHour = latestSettings.nightEndHour,
                        language = latestSettings.language
                    )
                    Pair(readings, stats)
                }
            }.collect { (readings, stats) ->
                _uiState.value = _uiState.value.copy(
                    livePeriod = _livePeriod.value,
                    liveReadings = readings,
                    liveStatistics = stats
                )
            }
        }
    }

    private fun observeHistoricalReport() {
        viewModelScope.launch {
            combine(
                database.historicalReadingDao().getAllReadings().map { entities ->
                    entities.map { it.toDomain() }
                },
                settingsRepository.getSettings()
            ) { readings, settings ->
                _uiState.value = _uiState.value.copy(userSettings = settings)

                if (readings.isEmpty()) {
                    HistoricalReportData(hasData = false)
                } else {
                    val stats = GlucoseMetricsCalculator.calculateStatistics(
                        readings = readings,
                        targetRanges = settings.targetRanges,
                        nightStartHour = settings.nightStartHour,
                        nightEndHour = settings.nightEndHour,
                        language = settings.language
                    )
                    val minTs = readings.minOf { it.timestamp }
                    val maxTs = readings.maxOf { it.timestamp }
                    val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val rangeStr = "${fmt.format(Date(minTs))} — ${fmt.format(Date(maxTs))}"

                    HistoricalReportData(
                        hasData = true,
                        readings = readings,
                        statistics = stats,
                        dateRangeStr = rangeStr
                    )
                }
            }.collect { histData ->
                _uiState.value = _uiState.value.copy(historicalReport = histData)
            }
        }
    }

    fun selectLivePeriod(period: TrendPeriod) {
        _livePeriod.value = period
    }

    fun importHistoricalFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importMessage = null)
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)

            val result = streamingImporter.importHistoricalFromUri(uri)
            result.onSuccess { total ->
                val msg = if (total > 0) {
                    if (isRu) "Импортировано $total точек в исторический отчёт." else "Imported $total points to historical report."
                } else {
                    if (isRu) "Файл не содержит распознанных точек сахара." else "No glucose points found in file."
                }
                _uiState.value = _uiState.value.copy(isImporting = false, importMessage = msg)
            }.onFailure { err ->
                val prefix = if (isRu) "Ошибка импорта: " else "Import error: "
                _uiState.value = _uiState.value.copy(isImporting = false, importMessage = prefix + err.message)
            }
        }
    }

    fun clearHistoricalReport() {
        viewModelScope.launch {
            database.historicalReadingDao().clearAll()
            _uiState.value = _uiState.value.copy(
                historicalReport = HistoricalReportData(hasData = false),
                importMessage = null
            )
        }
    }

    fun showLiveDetails(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLiveDetailDialog = show)
    }

    fun showHistoricalDetails(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHistoricalDetailDialog = show)
    }

    fun generateAndShareLivePdf() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingLive = true)
            val currentState = _uiState.value

            val result = pdfGenerator.generateAgpReport(
                readings = currentState.liveReadings,
                statistics = currentState.liveStatistics,
                userSettings = currentState.userSettings,
                selectedPeriod = _livePeriod.value
            )

            result.onSuccess { pdfFile ->
                _uiState.value = _uiState.value.copy(isGeneratingLive = false)
                dispatchShareIntent(pdfFile)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGeneratingLive = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
            }
        }
    }

    fun saveLivePdfToDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingLive = true)
            val currentState = _uiState.value

            val result = pdfGenerator.generateAgpReport(
                readings = currentState.liveReadings,
                statistics = currentState.liveStatistics,
                userSettings = currentState.userSettings,
                selectedPeriod = _livePeriod.value
            )

            result.onSuccess { pdfFile ->
                _uiState.value = _uiState.value.copy(isGeneratingLive = false)
                saveToPublicDownloads(pdfFile, "TIRUp_Live_AGP_${System.currentTimeMillis()}.pdf")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGeneratingLive = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
            }
        }
    }

    fun generateAndShareHistoricalPdf() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingHistorical = true)
            val hist = _uiState.value.historicalReport

            val result = pdfGenerator.generateAgpReport(
                readings = hist.readings,
                statistics = hist.statistics,
                userSettings = _uiState.value.userSettings,
                selectedPeriod = TrendPeriod.PERIOD_ALL
            )

            result.onSuccess { pdfFile ->
                _uiState.value = _uiState.value.copy(isGeneratingHistorical = false)
                dispatchShareIntent(pdfFile)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGeneratingHistorical = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
            }
        }
    }

    fun saveHistoricalPdfToDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingHistorical = true)
            val hist = _uiState.value.historicalReport

            val result = pdfGenerator.generateAgpReport(
                readings = hist.readings,
                statistics = hist.statistics,
                userSettings = _uiState.value.userSettings,
                selectedPeriod = TrendPeriod.PERIOD_ALL
            )

            result.onSuccess { pdfFile ->
                _uiState.value = _uiState.value.copy(isGeneratingHistorical = false)
                saveToPublicDownloads(pdfFile, "TIRUp_Historical_AGP_${System.currentTimeMillis()}.pdf")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isGeneratingHistorical = false)
                _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
            }
        }
    }

    private suspend fun dispatchShareIntent(pdfFile: File) {
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
    }

    private suspend fun saveToPublicDownloads(pdfFile: File, fileName: String) {
        try {
            var savedPath = ""
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
            _events.emit(ReportEvent.SavedToDownloads(savedPath))
        } catch (e: Exception) {
            _events.emit(ReportEvent.Error("Save failed: ${e.message}"))
        }
    }
}
