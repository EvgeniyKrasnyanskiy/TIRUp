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
    val importProgress: Float = 0f,
    val importedPointsCount: Int = 0,
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
    @android.annotation.SuppressLint("StaticFieldLeak")
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
    private val guidebookPdfGenerator = GuidebookPdfGenerator(context)

    init {
        observeLiveReport()
        observeHistoricalReport()
    }

    private fun observeLiveReport() {
        viewModelScope.launch {
            combine(
                _livePeriod,
                glucoseRepository.getLatestReading()
            ) { period, latest ->
                Pair(period, latest)
            }.flatMapLatest { (period, latest) ->
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
                        language = latestSettings.language,
                        unit = latestSettings.unit
                    )
                    Triple(readings, stats, latestSettings)
                }
            }.collect { (readings, stats, latestSettings) ->
                _uiState.value = _uiState.value.copy(
                    livePeriod = _livePeriod.value,
                    liveReadings = readings,
                    liveStatistics = stats,
                    userSettings = latestSettings
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
                        language = settings.language,
                        unit = settings.unit
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
        importHistoricalFiles(listOf(uri))
    }

    fun importHistoricalFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val totalFiles = uris.size
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                importProgress = 0.02f,
                importedPointsCount = 0,
                importMessage = if (totalFiles > 1) {
                    if (isRu) "Подготовка к импорту $totalFiles файлов..." else "Preparing to import $totalFiles files..."
                } else null
            )

            var cumulativePoints = 0
            var successfulFiles = 0
            val errors = mutableListOf<String>()

            for ((idx, uri) in uris.withIndex()) {
                val fileNum = idx + 1
                if (totalFiles > 1) {
                    _uiState.value = _uiState.value.copy(
                        importMessage = if (isRu) "Обработка файла $fileNum из $totalFiles..." else "Processing file $fileNum of $totalFiles..."
                    )
                }

                val result = streamingImporter.importHistoricalFromUri(uri) { subProgress, count ->
                    val overallProgress = ((idx.toFloat() + subProgress) / totalFiles.toFloat()).coerceIn(0.01f, 0.99f)
                    _uiState.value = _uiState.value.copy(
                        importProgress = overallProgress,
                        importedPointsCount = cumulativePoints + count
                    )
                }

                result.onSuccess { count ->
                    cumulativePoints += count
                    successfulFiles++
                }.onFailure { err ->
                    val detail = err.message?.takeIf { it.isNotBlank() } ?: "Error"
                    errors.add("№$fileNum: $detail")
                }
            }

            val finalMessage = if (errors.isEmpty()) {
                if (cumulativePoints > 0) {
                    if (totalFiles > 1) {
                        if (isRu) "Успешно импортировано $cumulativePoints измерений из $totalFiles файлов."
                        else "Successfully imported $cumulativePoints points from $totalFiles files."
                    } else {
                        if (isRu) "Импортировано $cumulativePoints измерений в исторический отчёт."
                        else "Imported $cumulativePoints points to historical report."
                    }
                } else {
                    if (isRu) "❌ В выбранных файлах не обнаружены данные измерений глюкозы."
                    else "❌ No glucose points found in selected files."
                }
            } else {
                if (successfulFiles > 0) {
                    val partErr = errors.joinToString("; ")
                    if (isRu) "Импортировано $cumulativePoints измерений ($successfulFiles из $totalFiles файлов). Ошибки: $partErr"
                    else "Imported $cumulativePoints points ($successfulFiles of $totalFiles files). Errors: $partErr"
                } else {
                    val prefix = if (isRu) "Ошибка импорта: " else "Import error: "
                    val allErr = errors.joinToString("; ")
                    prefix + allErr
                }
            }

            _uiState.value = _uiState.value.copy(
                isImporting = false,
                importProgress = if (successfulFiles > 0) 1f else 0f,
                importedPointsCount = cumulativePoints,
                importMessage = finalMessage
            )
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
                val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
                val subject = if (isRu) "TIRUp • Амбулаторный гликемический профиль (AGP)" else "TIRUp • Ambulatory Glucose Profile (AGP) Report"
                val chooser = if (isRu) "Поделиться отчётом AGP" else "Share AGP Report"
                dispatchShareIntent(pdfFile, subject, chooser)
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
                val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
                val subject = if (isRu) "TIRUp • Амбулаторный гликемический профиль (AGP)" else "TIRUp • Ambulatory Glucose Profile (AGP) Report"
                val chooser = if (isRu) "Поделиться отчётом AGP" else "Share AGP Report"
                dispatchShareIntent(pdfFile, subject, chooser)
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

    fun generateAndShareGuidebookPdf() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            guidebookPdfGenerator.generateGuidebookPdf(isRu)
                .onSuccess { pdfFile ->
                    val subject = if (isRu) "TIRUp • Справочник параметров CGM" else "TIRUp • CGM Parameters Guidebook"
                    val chooser = if (isRu) "Поделиться справочником CGM" else "Share CGM Guidebook"
                    dispatchShareIntent(pdfFile, subject, chooser)
                }.onFailure { error ->
                    _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
                }
        }
    }

    fun saveGuidebookPdfToDownloads() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            guidebookPdfGenerator.generateGuidebookPdf(isRu)
                .onSuccess { pdfFile ->
                    saveToPublicDownloads(pdfFile, "TIRUp_CGM_Parameters_Guide_${System.currentTimeMillis()}.pdf")
                }.onFailure { error ->
                    _events.emit(ReportEvent.Error(error.localizedMessage ?: "PDF Error"))
                }
        }
    }

    private suspend fun dispatchShareIntent(pdfFile: File, subject: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
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
