package com.tirup.app.presentation.reports

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.MetricsOrderDialog
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.trends.CompactPeriodSelector
import com.tirup.app.presentation.trends.TrendPeriod
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenHba1c: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGuidebookModal by remember { mutableStateOf(false) }
    var showXdripExportHelp by remember { mutableStateOf(false) }
    var showMetricsOrderDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isRu = state.userSettings.language.equals("RU", ignoreCase = true)

    LaunchedEffect(state.importMessage) {
        val msg = state.importMessage
        if (msg != null && (msg.contains("❌") || msg.contains("Ошибка") || msg.contains("Error"))) {
            importErrorMessage = msg
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.importHistoricalFiles(uris)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ReportEvent.SharePdf -> {
                    context.startActivity(event.shareIntent)
                }
                is ReportEvent.SavedToDownloads -> {
                    val message = context.getString(R.string.pdf_saved_toast, event.filePath)
                    val actionLabel = if (isRu) "Открыть" else "Open"
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openSavedFileFolder(context, event.filePath)
                    }
                }
                is ReportEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header with Menu and HbA1c Chip
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Settings Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reports_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val latestHba1c = state.userSettings.latestHba1cRecord
                val now = System.currentTimeMillis()
                val freshnessColor = if (latestHba1c != null) {
                    val ageDays = ((now - latestHba1c.timestamp) / 86_400_000L).coerceAtLeast(0)
                    when {
                        ageDays < 30 -> ColorTight
                        ageDays <= 360 -> PrimaryEmerald
                        else -> ColorHigh
                    }
                } else {
                    ColorHigh
                }

                val valueColor = if (latestHba1c != null) {
                    val v = latestHba1c.valuePercent
                    when {
                        v < 6.1 -> ColorTight
                        v <= 7.0 -> PrimaryEmerald
                        v <= 8.0 -> ColorHigh
                        else -> ColorVeryLow
                    }
                } else {
                    ColorHigh
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = freshnessColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.2.dp, freshnessColor.copy(alpha = 0.55f)),
                    modifier = Modifier.clickable { onOpenHba1c() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = freshnessColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (latestHba1c != null) {
                            Text(
                                text = "HbA1c: ",
                                color = freshnessColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", latestHba1c.valuePercent),
                                color = valueColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = "HbA1c: +",
                                color = freshnessColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // Section 1: Live Broadcast Report Card
            item {
                LiveReportCard(
                    state = state,
                    viewModel = viewModel,
                    onCardClick = { viewModel.showLiveDetails(true) },
                    onReorderClick = { showMetricsOrderDialog = true }
                )
            }

            // Section 2: Historical File Report Card (xDrip CSV / ZIP)
            item {
                HistoricalReportCard(
                    state = state,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                    viewModel = viewModel,
                    onCardClick = { viewModel.showHistoricalDetails(true) },
                    onHelpClick = { showXdripExportHelp = true },
                    onReorderClick = { showMetricsOrderDialog = true }
                )
            }

            // Section 3: Clinical Parameters Guidebook & CGM Disclaimer
            item {
                GuidebookCard(
                    isRu = isRu,
                    onClick = { showGuidebookModal = true }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Metrics Order Dialog
    if (showMetricsOrderDialog) {
        MetricsOrderDialog(
            currentOrder = state.userSettings.metricsOrder,
            hiddenMetrics = state.userSettings.hiddenMetrics,
            isRu = isRu,
            onSave = { newOrder, hidden -> viewModel.updateMetricsConfiguration(newOrder, hidden) },
            onDismiss = { showMetricsOrderDialog = false }
        )
    }

    // Live Report AGP Sheet Preview Modal
    if (state.showLiveDetailDialog) {
        val dateRangeStr = remember(state.liveReadings) {
            val minTs = state.liveReadings.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val maxTs = state.liveReadings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            "${fmt.format(Date(minTs))} — ${fmt.format(Date(maxTs))}"
        }
        val periodName = when (state.livePeriod) {
            TrendPeriod.PERIOD_7D -> if (isRu) "7 дней" else "7 Days"
            TrendPeriod.PERIOD_14D -> if (isRu) "14 дней (AGP)" else "14 Days (AGP)"
            TrendPeriod.PERIOD_30D -> if (isRu) "30 дней" else "30 Days"
            TrendPeriod.PERIOD_90D -> if (isRu) "90 дней" else "90 Days"
            TrendPeriod.PERIOD_YEAR -> if (isRu) "1 год" else "1 Year"
            TrendPeriod.PERIOD_ALL -> if (isRu) "Всё время" else "All Time"
        }

        AgpSheetPreviewModal(
            title = stringResource(R.string.report_live_title),
            periodLabel = periodName,
            dateRangeStr = dateRangeStr,
            readings = state.liveReadings,
            statistics = state.liveStatistics,
            userSettings = state.userSettings,
            isGenerating = state.isGeneratingLive,
            snackbarHostState = snackbarHostState,
            onSavePdf = { viewModel.saveLivePdfToDownloads() },
            onSharePdf = { viewModel.generateAndShareLivePdf() },
            onDismiss = { viewModel.showLiveDetails(false) }
        )
    }

    // Historical Report AGP Sheet Preview Modal
    if (state.showHistoricalDetailDialog) {
        val hist = state.historicalReport
        AgpSheetPreviewModal(
            title = stringResource(R.string.report_historical_title),
            periodLabel = if (isRu) "Исторический отчёт" else "Historical Report",
            dateRangeStr = hist.dateRangeStr,
            readings = hist.readings,
            statistics = hist.statistics,
            userSettings = state.userSettings,
            isGenerating = state.isGeneratingHistorical,
            snackbarHostState = snackbarHostState,
            onSavePdf = { viewModel.saveHistoricalPdfToDownloads() },
            onSharePdf = { viewModel.generateAndShareHistoricalPdf() },
            onDismiss = { viewModel.showHistoricalDetails(false) }
        )
    }

    // Parameters Guidebook & Clinical Disclaimer Modal
    if (showGuidebookModal) {
        ParametersGuidebookModal(
            isRu = isRu,
            snackbarHostState = snackbarHostState,
            onSavePdf = { viewModel.saveGuidebookPdfToDownloads() },
            onSharePdf = { viewModel.generateAndShareGuidebookPdf() },
            onDismiss = { showGuidebookModal = false }
        )
    }

    if (showXdripExportHelp) {
        AlertDialog(
            onDismissRequest = { showXdripExportHelp = false },
            title = {
                Text(
                    text = if (isRu) "Как экспортировать файл из xDrip+" else "How to export file from xDrip+",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isRu) {
                        "1️⃣ Откройте приложение xDrip+ на смартфоне.\n\n" +
                        "2️⃣ Нажмите на три точки ⋮ (Меню) в верхнем правом углу экрана.\n\n" +
                        "3️⃣ Выберите пункт «Import / Export features» (Функции импорта / экспорта).\n\n" +
                        "4️⃣ Нажмите «Export CSV (SiDiary format)» (Импорт файлов xDrip+).\n\n" +
                        "5️⃣ Выберите дату начала экспорта данных. Приложение автоматически сформирует архив.\n\n" +
                        "📂 Где найти готовый файл:\nВнутренняя память смартфона → папка xdrip → файл с именем exportCSV...zip\n\n" +
                        "💡 Поддержка пакетов: можно выбрать сразу несколько файлов или ZIP-архивов одновременно (рекомендуется до 50–100 файлов за раз для стабильности). TIRUp объединит их без дубликатов."
                    } else {
                        "1️⃣ Open the xDrip+ application on your phone.\n\n" +
                        "2️⃣ Tap the three dots ⋮ (Menu) in the upper right corner.\n\n" +
                        "3️⃣ Select 'Import / Export features'.\n\n" +
                        "4️⃣ Tap 'Export CSV (SiDiary format)' to export xDrip+ files.\n\n" +
                        "5️⃣ Choose the start date. xDrip+ will automatically generate the export archive.\n\n" +
                        "📂 File location:\nInternal storage → xdrip → file named exportCSV...zip\n\n" +
                        "💡 Batch import: you can select multiple files or ZIP archives at once (recommended up to 50–100 files per batch for performance). TIRUp will merge them without duplicates."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showXdripExportHelp = false }) {
                    Text(text = if (isRu) "Понятно" else "Got it", color = ActionBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (importErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = {
                Text(
                    text = if (isRu) "Ошибка импорта" else "Import Error",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = importErrorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { importErrorMessage = null }) {
                    Text(text = if (isRu) "Понятно" else "Got it", color = ActionBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (state.isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = ActionBlue,
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp
                    )
                    Text(
                        text = if (isRu) "Импорт данных..." else "Importing data...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val ptsStr = if (state.importedPointsCount > 0) {
                        if (isRu) "Загружено ${state.importedPointsCount} измерений" else "Loaded ${state.importedPointsCount} pts"
                    } else ""
                    if (ptsStr.isNotEmpty()) {
                        Text(
                            text = ptsStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val percentInt = (state.importProgress * 100).toInt().coerceIn(0, 100)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.importProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ActionBlue,
                        trackColor = ActionBlue.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "$percentInt%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ActionBlue
                    )
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun LiveReportCard(
    state: ReportsUiState,
    viewModel: ReportsViewModel,
    onCardClick: () -> Unit,
    onReorderClick: () -> Unit
) {
    val stats = state.liveStatistics
    val isRu = state.userSettings.language.equals("RU", ignoreCase = true)

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        cornerRadius = 24.dp,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Title & PDF Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.report_live_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val displayDays = minOf(stats.daysCount, state.livePeriod.days)
                        Text(
                            text = if (isRu) "${state.liveReadings.size} измерений • $displayDays дн. (нажмите для предпросмотра)"
                                   else "${state.liveReadings.size} readings • $displayDays days (tap to preview)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.isGeneratingLive) {
                    CircularProgressIndicator(
                        color = PrimaryEmerald,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Compact Period Selector for Live Report
            CompactPeriodSelector(
                selectedPeriod = state.livePeriod,
                onPeriodSelected = { viewModel.selectLivePeriod(it) }
            )

            // Range Bar
            RangeDistributionBar(
                tbrVeryLow = stats.tbrVeryLowPercent,
                tbrLow = stats.tbrLowPercent,
                tir = stats.tirPercent,
                tarHigh = stats.tarHighPercent,
                tarVeryHigh = stats.tarVeryHighPercent,
                modifier = Modifier.fillMaxWidth()
            )

            // Parameters with Active Time at the very TOP
            ReportMetricsColumn(
                stats = stats,
                unit = state.userSettings.unit,
                language = state.userSettings.language,
                metricsOrder = state.userSettings.metricsOrder,
                hiddenMetrics = state.userSettings.hiddenMetrics,
                onReorderClick = onReorderClick
            )

            // Bottom Buttons Row (Save PDF & Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveLivePdfToDownloads() },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    ),
                    enabled = state.liveReadings.isNotEmpty() && !state.isGeneratingLive
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = ActionBlue)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isRu) "Сохранить" else "Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.generateAndShareLivePdf() },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    ),
                    enabled = state.liveReadings.isNotEmpty() && !state.isGeneratingLive
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isRu) "Поделиться" else "Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoricalReportCard(
    state: ReportsUiState,
    onPickFile: () -> Unit,
    viewModel: ReportsViewModel,
    onCardClick: () -> Unit,
    onHelpClick: () -> Unit,
    onReorderClick: () -> Unit
) {
    val hist = state.historicalReport
    val isRu = state.userSettings.language.equals("RU", ignoreCase = true)

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hist.hasData) { onCardClick() },
        cornerRadius = 24.dp,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = stringResource(R.string.report_historical_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hist.hasData) {
                                if (isRu) "${hist.readings.size} измерений • ${hist.dateRangeStr} (предпросмотр)"
                                else "${hist.readings.size} readings • ${hist.dateRangeStr} (preview)"
                            } else {
                                if (isRu) "Импорт из xDrip+ CSV / ZIP" else "Import from xDrip+ CSV / ZIP"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.isGeneratingHistorical || state.isImporting) {
                        CircularProgressIndicator(
                            color = Color(0xFF818CF8),
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        val context = LocalContext.current
                        Surface(
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage("com.eveningoutpost.dexdrip")
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isRu) "xDrip+ не найден на устройстве" else "xDrip+ is not installed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("🩸", fontSize = 11.sp)
                                Text(
                                    text = "xDrip+",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        if (hist.hasData) {
                            IconButton(
                                onClick = { viewModel.clearHistoricalReport() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear",
                                    tint = ColorHigh,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onHelpClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Help",
                                    tint = ActionBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ptsStr = if (state.importedPointsCount > 0) {
                            if (isRu) " (${state.importedPointsCount} измерений)" else " (${state.importedPointsCount} pts)"
                        } else ""
                        val defaultMsg = if (isRu) "Обработка и расчёт$ptsStr..." else "Processing and calculating$ptsStr..."
                        Text(
                            text = if (state.importMessage != null && !state.importMessage.startsWith("❌")) state.importMessage else defaultMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        val percentInt = (state.importProgress * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = "$percentInt%",
                            style = MaterialTheme.typography.titleSmall,
                            color = ActionBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.importProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = ActionBlue,
                        trackColor = ActionBlue.copy(alpha = 0.15f)
                    )
                }
            }

            if (hist.hasData) {
                // Range Bar
                RangeDistributionBar(
                    tbrVeryLow = hist.statistics.tbrVeryLowPercent,
                    tbrLow = hist.statistics.tbrLowPercent,
                    tir = hist.statistics.tirPercent,
                    tarHigh = hist.statistics.tarHighPercent,
                    tarVeryHigh = hist.statistics.tarVeryHighPercent,
                    modifier = Modifier.fillMaxWidth()
                )

                // Parameters with Active Time at the very TOP
                ReportMetricsColumn(
                    stats = hist.statistics,
                    unit = state.userSettings.unit,
                    language = state.userSettings.language,
                    metricsOrder = state.userSettings.metricsOrder,
                    hiddenMetrics = state.userSettings.hiddenMetrics,
                    onReorderClick = onReorderClick
                )

                // Action Buttons: Import File above, Save & Share below
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ActionBlue
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = ActionBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isRu) "Загрузить другие файлы (CSV / ZIP)" else "Import Other Files (CSV / ZIP)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.saveHistoricalPdfToDownloads() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ActionBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ActionBlue
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = ActionBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isRu) "Сохранить" else "Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.generateAndShareHistoricalPdf() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ActionBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ActionBlue
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isRu) "Поделиться" else "Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    ),
                    enabled = !state.isImporting
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            color = ActionBlue,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = ActionBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isRu) "Загрузить файлы (CSV / ZIP)" else "Import Files (CSV / ZIP)",
                        color = ActionBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.importMessage != null) {
                val isError = state.importMessage.contains("❌") ||
                        state.importMessage.contains("Ошибка") ||
                        state.importMessage.contains("Error")
                Text(
                    text = state.importMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else PrimaryEmerald
                )
            }
        }
    }
}

@Composable
private fun ReportMetricsColumn(
    stats: GlucoseStatistics,
    unit: GlucoseUnit,
    language: String = "RU",
    metricsOrder: List<String> = com.tirup.app.domain.model.DEFAULT_METRICS_ORDER,
    hiddenMetrics: List<String> = emptyList(),
    onReorderClick: (() -> Unit)? = null
) {
    val isRu = language.equals("RU", ignoreCase = true)
    val isMmol = unit == GlucoseUnit.MMOL_L
    val onSurface = MaterialTheme.colorScheme.onSurface

    val meanVal = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt())
    val meanColor = when {
        stats.meanMmol <= 0.0 -> onSurface
        stats.meanMmol <= 7.0 -> ColorTight
        stats.meanMmol <= 7.8 -> ColorTargetSoft
        stats.meanMmol <= 8.5 -> ColorHigh
        else -> ColorVeryHigh
    }

    val cvColor = when {
        stats.cvPercent <= 0.0 -> onSurface
        stats.cvPercent <= 36.0 -> PrimaryEmerald
        else -> ColorHigh
    }

    val gmiColor = when {
        stats.gmiPercent <= 0.0 -> onSurface
        stats.gmiPercent <= 6.5 -> PrimaryEmerald
        stats.gmiPercent <= 7.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    val tirColor = when {
        stats.tirPercent <= 0.0 -> onSurface
        stats.tirPercent >= 70.0 -> PrimaryEmerald
        stats.tirPercent >= 50.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    val griColor = if (stats.gri <= 40.0) PrimaryEmerald else ColorHigh

    val atColor = when {
        stats.activeTimePercent >= 90.0 -> PrimaryEmerald
        stats.activeTimePercent >= 70.0 -> ColorHigh
        else -> ColorVeryHigh
    }
    val atEmoji = if (stats.activeTimePercent >= 90.0) "🟢" else if (stats.activeTimePercent >= 70.0) "🟡" else "🔴"

    val sdValStr = if (stats.sdMmol > 0.0) {
        if (isMmol) String.format(Locale.US, "%.2f mmol/L", stats.sdMmol)
        else String.format(Locale.US, "%d mg/dL", (stats.sdMmol * 18.0182).toInt())
    } else "--"

    val minVal = stats.minMmol
    val maxVal = stats.maxMmol
    val minMaxValStr = if (minVal > 0.0) {
        if (isMmol) "${String.format(Locale.US, "%.1f", minVal)}–${String.format(Locale.US, "%.1f", maxVal)}"
        else "${(minVal * 18.0182).toInt()}–${(maxVal * 18.0182).toInt()}"
    } else "--"

    val tbrTotal = stats.tbrLowPercent + stats.tbrVeryLowPercent
    val tarTotal = stats.tarHighPercent + stats.tarVeryHighPercent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRu) "Клинические параметры" else "Clinical Parameters",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onReorderClick != null) {
                IconButton(
                    onClick = onReorderClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 1. Active sensor time ALWAYS first
        ReportMetricRow(
            label = if (isRu) "$atEmoji Активное время сенсора" else "$atEmoji Active CGM Time",
            target = if (isRu) "Цель: ≥70.0%" else "Target: ≥70.0%",
            value = String.format(Locale.US, "%.1f%%", stats.activeTimePercent),
            valueColor = atColor
        )

        // 2. User-ordered parameters
        val safeOrder = if (metricsOrder.isNotEmpty()) metricsOrder else com.tirup.app.domain.model.DEFAULT_METRICS_ORDER
        val visibleOrder = safeOrder.filterNot { id ->
            hiddenMetrics.any { it.equals(id, ignoreCase = true) }
        }
        for (id in visibleOrder) {
            when (id.lowercase()) {
                "mean" -> ReportMetricRow(
                    label = if (isRu) "Mean BG (средний сахар)" else "Mean BG (average glucose)",
                    target = if (isMmol) (if (isRu) "Цель: ≤7.8" else "Target: ≤7.8") else (if (isRu) "Цель: ≤140" else "Target: ≤140"),
                    value = meanVal,
                    valueColor = meanColor
                )
                "ea1c" -> ReportMetricRow(
                    label = if (isRu) "eA1c (расчётный ГГ)" else "eA1c (estimated A1c)",
                    target = if (isRu) "Цель: ≤7.0%" else "Target: ≤7.0%",
                    value = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                    valueColor = gmiColor
                )
                "sd" -> ReportMetricRow(
                    label = if (isRu) "SD (стандартное отклонение)" else "SD (standard deviation)",
                    target = if (isMmol) (if (isRu) "Цель: ≤2.0" else "Target: ≤2.0") else (if (isRu) "Цель: ≤36" else "Target: ≤36"),
                    value = sdValStr,
                    valueColor = if (stats.sdMmol in 0.01..2.0) PrimaryEmerald else ColorHigh
                )
                "cv" -> ReportMetricRow(
                    label = if (isRu) "CV (вариабельность)" else "CV (variability)",
                    target = if (isRu) "Цель: ≤36.0%" else "Target: ≤36.0%",
                    value = String.format(Locale.US, "%.1f%%", stats.cvPercent),
                    valueColor = cvColor
                )
                "tir" -> {
                    val tirLabel = if (isMmol) {
                        if (isRu) "TIR (3.9–10.0 ммоль/л)" else "TIR (3.9–10.0 mmol/L)"
                    } else {
                        if (isRu) "TIR (70–180 мг/дл)" else "TIR (70–180 mg/dL)"
                    }
                    ReportMetricRow(
                        label = tirLabel,
                        target = if (isRu) "Цель: ≥70%" else "Target: ≥70%",
                        value = String.format(Locale.US, "%.1f%%", stats.tirPercent),
                        valueColor = tirColor
                    )
                }
                "ting" -> {
                    val tingLabel = if (isMmol) {
                        if (isRu) "TING (3.9–7.8 ммоль/л)" else "TING (3.9–7.8 mmol/L)"
                    } else {
                        if (isRu) "TING (70–140 мг/дл)" else "TING (70–140 mg/dL)"
                    }
                    ReportMetricRow(
                        label = tingLabel,
                        target = if (isRu) "Цель: ≥50%" else "Target: ≥50%",
                        value = String.format(Locale.US, "%.1f%%", stats.tingPercent),
                        valueColor = if (stats.tingPercent >= 50.0) PrimaryEmerald else ColorHigh
                    )
                }
                "tbr" -> {
                    val tbrLabel = if (isMmol) {
                        if (isRu) "TBR < 3.9 ммоль/л" else "TBR < 3.9 mmol/L"
                    } else {
                        if (isRu) "TBR < 70 мг/дл" else "TBR < 70 mg/dL"
                    }
                    ReportMetricRow(
                        label = tbrLabel,
                        target = if (isRu) "Цель: <4%" else "Target: <4%",
                        value = String.format(Locale.US, "%.1f%%", tbrTotal),
                        valueColor = if (tbrTotal <= 4.0) PrimaryEmerald else ColorVeryHigh
                    )
                }
                "tar" -> {
                    val tarLabel = if (isMmol) {
                        if (isRu) "TAR > 10.0 ммоль/л" else "TAR > 10.0 mmol/L"
                    } else {
                        if (isRu) "TAR > 180 мг/дл" else "TAR > 180 mg/dL"
                    }
                    ReportMetricRow(
                        label = tarLabel,
                        target = if (isRu) "Цель: <25%" else "Target: <25%",
                        value = String.format(Locale.US, "%.1f%%", tarTotal),
                        valueColor = if (tarTotal <= 25.0) PrimaryEmerald else ColorHigh
                    )
                }
                "gri" -> ReportMetricRow(
                    label = if (isRu) "GRI (риск гипо)" else "GRI (hypo risk)",
                    target = if (isRu) "Цель: ≤40.0" else "Target: ≤40.0",
                    value = String.format(Locale.US, "%.1f (%s)", stats.gri, stats.griLabel),
                    valueColor = griColor
                )
                "gvi" -> ReportMetricRow(
                    label = if (isRu) "GVI (лабильность)" else "GVI (glycemic variability)",
                    target = if (isRu) "Цель: ≤1.20" else "Target: ≤1.20",
                    value = String.format(Locale.US, "%.2f", stats.gvi),
                    valueColor = if (stats.gvi <= 1.20) PrimaryEmerald else ColorHigh
                )
                "pgs" -> ReportMetricRow(
                    label = if (isRu) "PGS (гликемический статус)" else "PGS (patient status)",
                    target = if (isRu) "Цель: ≤35.0" else "Target: ≤35.0",
                    value = String.format(Locale.US, "%.1f", stats.pgs),
                    valueColor = if (stats.pgs <= 35.0) PrimaryEmerald else ColorHigh
                )
                "minmax" -> ReportMetricRow(
                    label = if (isRu) "Min / Max (суточный размах)" else "Min / Max (daily span)",
                    target = if (isMmol) (if (isRu) "Цель: 3.9–10.0" else "Target: 3.9–10.0") else (if (isRu) "Цель: 70–180" else "Target: 70–180"),
                    value = minMaxValStr,
                    valueColor = if (maxVal <= 10.0 && minVal >= 3.9 && minVal > 0.0) PrimaryEmerald else ColorHigh
                )
            }
        }
    }
}

@Composable
private fun ReportMetricRow(
    label: String,
    target: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Text(
                    text = target,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun GuidebookCard(
    isRu: Boolean,
    onClick: () -> Unit
) {
    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 24.dp,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRu) "📖 Справочник параметров CGM" else "📖 CGM Parameters Guide",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Клинические нормы ATTD/ADA и смысл метрик"
                               else "ATTD/ADA clinical targets, metrics meaning & disclaimer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametersGuidebookModal(
    isRu: Boolean,
    snackbarHostState: SnackbarHostState,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Modal Top Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRu) "Справочник параметров CGM" else "CGM Clinical Parameters Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(6.dp)) }

                    // Section 1
                    item {
                        GuidebookSectionHeader(
                            title = if (isRu) "1. Основные показатели контроля" else "1. Core Glycemic Control Metrics"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidebookItemCard(
                                title = if (isRu) "Активное время сенсора (Sensor Active Time)" else "Sensor Active Time (Wear Time)",
                                target = if (isRu) "Цель: ≥70.0% (≥10 из 14 дней) • Идеал: ≥90%" else "Target: ≥70.0% (≥10 of 14 days) • Ideal: ≥90%",
                                desc = if (isRu) "Международный консенсус ATTD/ADA: если активное время работы сенсора составляет менее 70%, накопленных данных статистически недостаточно. Отчёт считается нерепрезентативным, и на его основе нельзя принимать клинические решения по коррекции доз инсулина или схемы терапии (высокий риск пропущенных скрытых гипогликемий в слепых окнах). Для надёжного расчёта eA1c/GMI требуется непрерывный мониторинг не менее 14 дней."
                                       else "International ATTD/ADA consensus: wear time <70% is statistically insufficient and clinically non-representative. Therapy adjustments should not be made based on such reports due to risk of undetected occult hypoglycemia. Robust eA1c/GMI calculation requires at least 14 days of continuous wear."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "Mean BG (Средняя гликемия)" else "Mean BG (Average Glucose)",
                                target = if (isRu) "Цель: ≤7.8 ммоль/л (≤140 мг/дл) • Норма здоровых: 4.5–5.8 ммоль/л" else "Target: ≤7.8 mmol/L (≤140 mg/dL) • Non-diabetic: 4.5–5.8 mmol/L",
                                desc = if (isRu) "Среднее арифметическое всех измерений. У людей без диабета уровень глюкозы натощак составляет 3.9–5.5 ммоль/л, а после еды не превышает 7.8 ммоль/л. При диабете средний сахар 8.5 ммоль/л соответствует eA1c ~7.5%, а 10.0 ммоль/л — eA1c ~8.6%. Служит фундаментом расчёта вариабельности и гликированного гемоглобина."
                                       else "Arithmetic mean of all readings. In healthy individuals, fasting glucose is 3.9–5.5 mmol/L and postprandial peak stays ≤7.8 mmol/L. For diabetes, an average of 8.5 mmol/L corresponds to ~7.5% eA1c, while 10.0 mmol/L corresponds to ~8.6% eA1c."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "eA1c (Расчётный гликированный гемоглобин)" else "eA1c (Estimated A1c)",
                                target = if (isRu) "Цель: ≤7.0% (≤53 ммоль/моль) • Норма: 4.0–5.6% • Предиабет: 5.7–6.4%" else "Target: ≤7.0% (≤53 mmol/mol) • Normal: 4.0–5.6% • Prediabetes: 5.7–6.4%",
                                desc = if (isRu) "Математическая экстраполяция лабораторного HbA1c по формуле ADAG. Отражает средний уровень глюкозы за 2–3 месяца. В отличие от лабораторного анализа крови, CGM-расчёт не искажается анемией, гемоглобинопатиями или частыми кровопотерями. Для детей и при планировании беременности цель может снижаться до <6.5%, а для пожилых пациентов с риском гипогликемий — смягчаться до <7.5–8.0% по назначению врача."
                                       else "Mathematical projection of laboratory HbA1c based on the ADAG formula over 2-3 months. Unlike blood tests, CGM calculation is not distorted by anemia or hemoglobin variants. Individual targets may vary: <6.5% in pregnancy or pediatric care, or relaxed to <7.5-8.0% for frail elderly patients."
                            )
                        }
                    }

                    // Section 2
                    item {
                        GuidebookSectionHeader(
                            title = if (isRu) "2. Время в целевых диапазонах (Time in Range)" else "2. Time in Range (ATTD Consensus)"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidebookItemCard(
                                title = if (isRu) "TIR (3.9–10.0 ммоль/л)" else "TIR (70–180 mg/dL)",
                                target = if (isRu) "Цель: ≥70.0% (>16 ч 48 мин/сут) • Здоровые: 96–99%" else "Target: ≥70.0% (>16h 48m/day) • Non-diabetic: 96–99%",
                                desc = if (isRu) "Золотой международный стандарт компенсации диабета. Каждые +10% TIR достоверно снижают риск диабетической ретинопатии на 64% и микроальбуминурии на 40%. У здоровых людей без диабета TIR составляет 96–99%. Для беременных с СД1 целевой коридор сужается до 3.5–7.8 ммоль/л (норма ≥70%). У пациентов старшей возрастной группы с высоким риском гипогликемий цель может быть снижена до ≥50%."
                                       else "Gold standard metric of diabetes management. Every +10% TIR reduces retinopathy risk by 64% and microalbuminuria by 40%. Non-diabetic individuals spend 96-99% in this range. In T1D pregnancy, the target corridor is tightened to 3.5–7.8 mmol/L (≥70%). For high-risk or frail elderly patients, target may be relaxed to ≥50%."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TING (3.9–7.8 ммоль/л)" else "TING (70–140 mg/dL)",
                                target = if (isRu) "Цель: ≥50.0% (>12 ч/сут) • Здоровые: >90% времени" else "Target: ≥50.0% (>12h/day) • Non-diabetic: >90%",
                                desc = if (isRu) "Время в узкой физиологической норме (Tight in Normal Glucose). Отражает функционирование здоровой поджелудочной железы и ювелирную точность инсулинотерапии. Нахождение в диапазоне 3.9–7.8 ммоль/л максимально защищает сосудистый эндотелий от окислительного стресса и снижает сердечно-сосудистые риски."
                                       else "Time in Tight Physiological Norm. Reflects non-diabetic glucose levels and peak insulin therapy precision. Staying within 3.9–7.8 mmol/L protects vascular endothelium from oxidative damage and reduces long-term cardiovascular risks."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TBR (<3.9 ммоль/л и <3.0 ммоль/л)" else "TBR (<70 mg/dL and <54 mg/dL)",
                                target = if (isRu) "Суммарно: <4.0% (<58 мин/сут) • Тяжёлая (<3.0): <1.0% (<14 мин)" else "Total: <4.0% (<58m/day) • Severe (<3.0): <1.0% (<14m)",
                                desc = if (isRu) "Главный приоритет безопасности. Уровень 1 (3.0–3.8 ммоль/л) требует купирования быстрыми углеводами (15 г). Уровень 2 (<3.0 ммоль/л) — критическая гипогликемия, провоцирующая сердечные аритмии, судороги и потерю сознания. Для пациентов с синдромом нарушенного распознавания гипогликемии (hypo-unawareness) цель ужесточается до TBR <1% суммарно (<14 мин/сут)."
                                       else "Primary safety priority. Level 1 (3.0–3.8 mmol/L) requires fast-acting carbohydrates. Level 2 (<3.0 mmol/L) is severe clinical hypoglycemia carrying risks of cardiac arrhythmia and loss of consciousness. For patients with impaired hypoglycemia awareness, target is tightened to <1% total (<14 min/day)."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TAR (>10.0 ммоль/л и >13.9 ммоль/л)" else "TAR (>180 mg/dL and >250 mg/dL)",
                                target = if (isRu) "Суммарно: <25.0% (<6 ч/сут) • Тяжёлая (>13.9): <5.0% (<1 ч 12 мин)" else "Total: <25.0% (<6h/day) • Severe (>13.9): <5.0% (<1h 12m)",
                                desc = if (isRu) "Время в гипергликемии. Уровень 1 (10.1–13.9 ммоль/л) ускоряет гликирование белков и утомляемость. Уровень 2 (>13.9 ммоль/л) — критическая зона глюкозотоксичности, резкой дегидратации и риска диабетического кетоацидоза (ДКА). Снижение TAR достигается точным расчётом углеводных коэффициентов и факторов чувствительности к инсулину."
                                       else "Time above range. Level 1 (10.1–13.9 mmol/L) accelerates protein glycation and microvascular strain. Level 2 (>13.9 mmol/L) represents severe hyperglycemia with acute dehydration and diabetic ketoacidosis (DKA) risks. Minimizing TAR requires accurate carb counting and insulin sensitivity factor tuning."
                            )
                        }
                    }

                    // Section 3
                    item {
                        GuidebookSectionHeader(
                            title = if (isRu) "3. Вариабельность и качество кривой" else "3. Glucose Variability & Lability"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidebookItemCard(
                                title = if (isRu) "%CV (Коэффициент вариации)" else "%CV (Coefficient of Variation)",
                                target = if (isRu) "Стабильный профиль: ≤36.0% • Высокая вариабельность: >36.0% • Здоровые: 15–25%" else "Stable Profile: ≤36.0% • High Variability: >36.0% • Non-diabetic: 15–25%",
                                desc = if (isRu) "Относительный размах колебаний сахара (%CV = SD / Mean * 100%). Клинический консенсус ATTD: при %CV > 36% компенсация считается лабильной и нестабильной — риск тяжёлой ночной гипогликемии возрастает в 4 раза даже при нормальном среднем сахаре. Снижение %CV достигается сглаживанием постпрандиальных пиков и стабильным базальным профилем."
                                       else "Relative glycemic spread (%CV = SD / Mean * 100%). International ATTD consensus: %CV > 36% indicates unstable glycemic control, escalating severe nocturnal hypoglycemia risk fourfold even with normal average glucose. Target stability is achieved by curbing post-meal spikes."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "SD (Стандартное отклонение)" else "SD (Standard Deviation)",
                                target = if (isRu) "Цель: ≤2.0 ммоль/л (≤36 мг/дл) • Идеал: ≤1.4 ммоль/л" else "Target: ≤2.0 mmol/L (≤36 mg/dL) • Ideal: ≤1.4 mmol/L",
                                desc = if (isRu) "Абсолютная ширина разброса сахаров вокруг средней арифметической. Практическое клиническое «правило трети»: SD не должно превышать 1/3 от величины среднего сахара (например, при Mean 6.0 ммоль/л SD должно быть ≤2.0). Чем меньше SD, тем ближе суточный график к ровной горизонтальной линии без резких перепадов."
                                       else "Absolute dispersion of glucose values around the mean. Clinical 'one-third rule': SD should not exceed 1/3 of mean glucose (e.g. at Mean 6.0 mmol/L, target SD ≤2.0). Lower SD values reflect a smooth, predictable glucose curve without sudden cliffs or spikes."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "GVI (Индекс гликемической лабильности)" else "GVI (Glycemic Variability Index)",
                                target = if (isRu) "Идеал здоровых: 1.00–1.20 • Умеренная: 1.21–1.50 • Лабильная (качели): >1.50" else "Healthy Baseline: 1.00–1.20 • Moderate: 1.21–1.50 • High Lability: >1.50",
                                desc = if (isRu) "Отношение фактической суммарной длины кривой сахара к идеальной гладкой траектории (алгоритм Service/Kovatchev). Оценивает микро-извилистость и «зигзагообразность» суточной кривой. Выявляет скрытые частые колебания (глюкозные американские горки), которые не всегда улавливаются средним сахаром и HbA1c."
                                       else "Ratio of actual continuous glucose curve length to the theoretical ideal flat trajectory (Service/Kovatchev algorithm). Unmasks rapid micro-oscillations and rollercoaster dynamics that may remain hidden behind an ostensibly normal HbA1c or mean glucose."
                            )
                        }
                    }

                    // Section 4
                    item {
                        GuidebookSectionHeader(
                            title = if (isRu) "4. Комплексные интегральные индексы" else "4. Composite Clinical Indices"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidebookItemCard(
                                title = if (isRu) "PGS (Гликемический статус пациента)" else "PGS (Patient Glycemic Status)",
                                target = if (isRu) "Отличный: ≤35.0 • Хороший: 35.1–70.0 • Удовлетворительный: 70.1–100.0 • Внимание: >100" else "Optimal: ≤35.0 • Good: 35.1–70.0 • Fair: 70.1–100.0 • Needs Action: >100",
                                desc = if (isRu) "Интегральный клинический балл качества компенсации, объединяющий средний уровень гликемии (Mean BG), вариабельность GVI и время в целевом диапазоне (TIR). Чем меньше балл PGS, тем ближе показатели к идеальному физиологическому профилю здорового человека. Позволяет одним числом отслеживать суммарную динамику от месяца к месяцу."
                                       else "Integrated clinical score combining average glucose (Mean BG), trajectory variability (GVI), and time in target (TIR). Lower values reflect superior glycemic control. Provides a single unified metric to track month-over-month clinical progress."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "GRI (Индекс гликемического риска)" else "GRI (Glycemia Risk Index)",
                                target = if (isRu) "Зона A (0–20): очень низкий • B (21–40): низкий • C (41–60): средний • D–E (>60): высокий" else "Zone A (0–20): Very Low • B (21–40): Low • C (41–60): Moderate • D–E (>60): High",
                                desc = if (isRu) "Современный валидированный индекс риска (0–100), разработанный международным консорциумом диабетологов (Klonoff et al. 2022). Штрафует гипогликемию с повышенным коэффициентом 3.0, а гипергликемию — 1.6. Клиническая цель: нахождение в Зонах A и B (GRI ≤ 40.0 баллов), что гарантирует максимальную безопасность сахароснижающей терапии и отсутствие риска комы."
                                       else "Validated clinical risk index (0–100) established by international consensus (Klonoff et al. 2022). Emphasizes safety by weighting hypoglycemia with a 3.0 factor and hyperglycemia with 1.6. Clinical target is staying in Zones A & B (GRI ≤ 40.0 points), signifying high therapeutic safety."
                            )
                        }
                    }

                    // Section 5: Clinical Disclaimer
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7), // Amber 100
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)), // Amber 500
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isRu) "⚠️ Важные примечания:"
                                           else "⚠️ Clinical Notice Regarding Continuous Glucose Monitoring (CGM):",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E) // Amber 800
                                )
                                Text(
                                    text = if (isRu) "• Физиологическое запаздывание: датчики CGM измеряют концентрацию глюкозы в интерстициальной (межтканевой) жидкости, а не в крови. В период быстрых изменений отставание от капиллярной крови составляет 5–15 минут.\n\n• Погрешность сенсора (MARD): современный стандарт MARD составляет 8–10%. Возможны ложные занижения показаний при сдавливании сенсора во сне (compression lows).\n\n• Назначение отчёта: данный аналитический отчёт носит информационно-ознакомительный характер и не является клиническим диагнозом. При расхождении самочувствия с показаниями CGM выполните замер глюкометром по капле крови и обратитесь к лечащему врачу."
                                           else "• Physiological Lag: CGM sensors measure interstitial fluid; physiological lag relative to blood is 5–15 minutes during rapid fluctuations.\n\n• Sensor MARD: standard accuracy error is 8–10%. Compression lows during sleep may occur.\n\n• Informational Use: this report does not replace clinical consultation. Always verify unusual readings with a fingerstick blood glucose test.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F), // Amber 900
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Bottom Action Bar (Save PDF, Share & Back)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSavePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ActionBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ActionBlue
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Сохранить" else "Save",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ActionBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ActionBlue
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Поделиться" else "Share",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "Назад" else "Back",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 70.dp, start = 12.dp, end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun GuidebookSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = PrimaryEmerald,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun GuidebookItemCard(
    title: String,
    target: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PrimaryEmerald.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = target,
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Opens system file manager to the Downloads directory or opens the saved PDF report directly.
 */
internal fun openSavedFileFolder(context: android.content.Context, filePath: String) {
    val file = java.io.File(filePath)
    val isInDownloads = filePath.contains("Download", ignoreCase = true)

    if (isInDownloads) {
        try {
            val downloadsIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(downloadsIntent)
            return
        } catch (_: Exception) {}
    }

    // 1. Try to open the parent folder directly via DocumentsUI
    try {
        val relativePath = if (filePath.contains("Documents/TIRUp/Backups", ignoreCase = true)) {
            "Documents%2FTIRUp%2FBackups"
        } else if (filePath.contains("Documents/TIRUp", ignoreCase = true)) {
            "Documents%2FTIRUp"
        } else if (filePath.contains("Documents", ignoreCase = true)) {
            "Documents"
        } else {
            "Download"
        }
        val folderUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$relativePath")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, "vnd.android.document/directory")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    // 2. Try to open the parent folder via FileProvider resource/folder
    try {
        val parentFolder = file.parentFile ?: file
        val folderUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            parentFolder
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, "resource/folder")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    // 3. Try to open the file directly with appropriate MIME type
    try {
        if (file.exists()) {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "csv" -> "text/csv"
                "zip" -> "application/zip"
                else -> "*/*"
            }
            val viewFileIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(viewFileIntent, file.name).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }
    } catch (_: Exception) {}

    // 4. Fallback to ACTION_VIEW_DOWNLOADS
    try {
        val downloadsIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(downloadsIntent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, filePath, android.widget.Toast.LENGTH_SHORT).show()
    }
}
