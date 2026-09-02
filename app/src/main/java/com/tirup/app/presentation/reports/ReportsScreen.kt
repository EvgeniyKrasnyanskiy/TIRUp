package com.tirup.app.presentation.reports

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
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
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGuidebookModal by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importHistoricalFile(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ReportEvent.SharePdf -> {
                    context.startActivity(event.shareIntent)
                }
                is ReportEvent.SavedToDownloads -> {
                    Toast.makeText(context, context.getString(R.string.pdf_saved_toast, event.filePath), Toast.LENGTH_LONG).show()
                }
                is ReportEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val isRu = state.userSettings.language.equals("RU", ignoreCase = true)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title with Menu
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Column {
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
            }
        }

        // Section 1: Live Broadcast Report Card
        item {
            LiveReportCard(
                state = state,
                viewModel = viewModel,
                onCardClick = { viewModel.showLiveDetails(true) }
            )
        }

        // Section 2: Historical File Report Card (xDrip CSV / ZIP)
        item {
            HistoricalReportCard(
                state = state,
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                viewModel = viewModel,
                onCardClick = { viewModel.showHistoricalDetails(true) }
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

    // Live Report AGP Sheet Preview Modal
    if (state.showLiveDetailDialog) {
        val minTs = state.liveReadings.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val maxTs = state.liveReadings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateRangeStr = "${fmt.format(Date(minTs))} — ${fmt.format(Date(maxTs))}"
        val periodName = when (state.livePeriod) {
            TrendPeriod.PERIOD_7D -> if (isRu) "7 Дней" else "7 Days"
            TrendPeriod.PERIOD_14D -> if (isRu) "14 Дней (AGP)" else "14 Days (AGP)"
            TrendPeriod.PERIOD_30D -> if (isRu) "30 Дней" else "30 Days"
            TrendPeriod.PERIOD_90D -> if (isRu) "90 Дней" else "90 Days"
            TrendPeriod.PERIOD_YEAR -> if (isRu) "1 Год" else "1 Year"
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
            onSavePdf = { viewModel.saveHistoricalPdfToDownloads() },
            onSharePdf = { viewModel.generateAndShareHistoricalPdf() },
            onDismiss = { viewModel.showHistoricalDetails(false) }
        )
    }

    // Parameters Guidebook & Clinical Disclaimer Modal
    if (showGuidebookModal) {
        ParametersGuidebookModal(
            isRu = isRu,
            onSavePdf = { viewModel.saveGuidebookPdfToDownloads() },
            onSharePdf = { viewModel.generateAndShareGuidebookPdf() },
            onDismiss = { showGuidebookModal = false }
        )
    }
}

@Composable
private fun LiveReportCard(
    state: ReportsUiState,
    viewModel: ReportsViewModel,
    onCardClick: () -> Unit
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
                        Text(
                            text = if (isRu) "${state.liveReadings.size} точек • ${stats.daysCount} дн. (нажмите для бланка)"
                                   else "${state.liveReadings.size} readings • ${stats.daysCount} days (tap to preview)",
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
            ReportMetricsColumn(stats = stats, unit = state.userSettings.unit, language = state.userSettings.language)

            // Bottom Buttons Row (Save PDF & Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.saveLivePdfToDownloads() },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionBlue,
                        contentColor = Color.White
                    ),
                    enabled = state.liveReadings.isNotEmpty() && !state.isGeneratingLive
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isRu) "Сохранить" else "Save PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    onCardClick: () -> Unit
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
                                if (isRu) "${hist.readings.size} точек • ${hist.dateRangeStr} (бланк)"
                                else "${hist.readings.size} readings • ${hist.dateRangeStr} (preview)"
                            } else {
                                if (isRu) "Импорт из xDrip CSV / ZIP" else "Import from xDrip CSV / ZIP"
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
                    horizontalArrangement = Arrangement.End
                ) {
                    if (state.isGeneratingHistorical || state.isImporting) {
                        CircularProgressIndicator(
                            color = Color(0xFF818CF8),
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else if (hist.hasData) {
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
                            if (isRu) " (${state.importedPointsCount} точек)" else " (${state.importedPointsCount} pts)"
                        } else ""
                        Text(
                            text = if (isRu) "Обработка и расчёт файла$ptsStr..." else "Processing and calculating$ptsStr...",
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
                ReportMetricsColumn(stats = hist.statistics, unit = state.userSettings.unit, language = state.userSettings.language)

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
                        Text(text = if (isRu) "Загрузить другой файл (CSV / ZIP)" else "Import Another File (CSV / ZIP)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveHistoricalPdfToDownloads() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ActionBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isRu) "Сохранить" else "Save PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        text = if (isRu) "Загрузить файл" else "Import File",
                        color = ActionBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.importMessage != null) {
                Text(
                    text = state.importMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryEmerald
                )
            }
        }
    }
}

@Composable
private fun ReportMetricsColumn(
    stats: GlucoseStatistics,
    unit: GlucoseUnit,
    language: String = "RU"
) {
    val isRu = language.equals("RU", ignoreCase = true)
    val isMmol = unit == GlucoseUnit.MMOL_L
    val meanVal = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt())
    val onSurface = MaterialTheme.colorScheme.onSurface
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ReportMetricRow(
            label = if (isRu) "$atEmoji Активное время сенсора" else "$atEmoji Active CGM Time",
            target = if (isRu) "Цель: ≥70.0%" else "Target: ≥70.0%",
            value = String.format(Locale.US, "%.1f%%", stats.activeTimePercent),
            valueColor = atColor
        )
        ReportMetricRow(
            label = if (isRu) "Mean BG (средний сахар)" else "Mean BG (average glucose)",
            target = if (isMmol) (if (isRu) "Цель: ≤7.8" else "Target: ≤7.8") else (if (isRu) "Цель: ≤140" else "Target: ≤140"),
            value = meanVal,
            valueColor = meanColor
        )
        ReportMetricRow(
            label = if (isRu) "eA1c (расчётный ГГ)" else "eA1c (estimated A1c)",
            target = if (isRu) "Цель: ≤7.0%" else "Target: ≤7.0%",
            value = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
            valueColor = gmiColor
        )
        val tirLabel = if (isMmol) {
            if (isRu) "TIR (3.9–10.0 ммоль/л)" else "TIR (3.9–10.0 mmol/L)"
        } else {
            if (isRu) "TIR (70–180 мг/дл)" else "TIR (70–180 mg/dL)"
        }
        ReportMetricRow(
            label = tirLabel,
            target = if (isRu) "Цель: ≥70%" else "Target: ≥70%",
            value = String.format(Locale.US, "%.0f%%", stats.tirPercent),
            valueColor = tirColor
        )
        val tingLabel = if (isMmol) {
            if (isRu) "TING (3.9–7.8 ммоль/л)" else "TING (3.9–7.8 mmol/L)"
        } else {
            if (isRu) "TING (70–140 мг/дл)" else "TING (70–140 mg/dL)"
        }
        ReportMetricRow(
            label = tingLabel,
            target = if (isRu) "Цель: ≥50%" else "Target: ≥50%",
            value = String.format(Locale.US, "%.0f%%", stats.tingPercent),
            valueColor = if (stats.tingPercent >= 50.0) PrimaryEmerald else ColorHigh
        )
        val tbrTotal = stats.tbrLowPercent + stats.tbrVeryLowPercent
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
        val tarTotal = stats.tarHighPercent + stats.tarVeryHighPercent
        val tarLabel = if (isMmol) {
            if (isRu) "TAR > 10.0 ммоль/л" else "TAR > 10.0 mmol/L"
        } else {
            if (isRu) "TAR > 180 мг/дл" else "TAR > 180 mg/dL"
        }
        ReportMetricRow(
            label = tarLabel,
            target = if (isRu) "Цель: <25%" else "Target: <25%",
            value = String.format(Locale.US, "%.0f%%", tarTotal),
            valueColor = if (tarTotal <= 25.0) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = if (isRu) "GVI (лабильность)" else "GVI (glycemic variability)",
            target = if (isRu) "Цель: ≤1.20" else "Target: ≤1.20",
            value = String.format(Locale.US, "%.2f", stats.gvi),
            valueColor = if (stats.gvi <= 1.20) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = if (isRu) "PGS (гликемический статус)" else "PGS (patient status)",
            target = if (isRu) "Цель: ≤35.0" else "Target: ≤35.0",
            value = String.format(Locale.US, "%.1f", stats.pgs),
            valueColor = if (stats.pgs <= 35.0) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = if (isRu) "CV (вариабельность)" else "CV (variability)",
            target = if (isRu) "Цель: ≤36.0%" else "Target: ≤36.0%",
            value = String.format(Locale.US, "%.1f%%", stats.cvPercent),
            valueColor = cvColor
        )
        ReportMetricRow(
            label = if (isRu) "GRI (риск гипо)" else "GRI (hypo risk)",
            target = if (isRu) "Цель: ≤40.0" else "Target: ≤40.0",
            value = String.format(Locale.US, "%.1f (%s)", stats.gri, stats.griLabel),
            valueColor = griColor
        )
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
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRu) "📖 Справочник" else "📖 Guidebook",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Смысл метрик, влияние на здоровье и дисклеймер CGM"
                               else "Metrics meaning, health impact, and CGM disclaimer",
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
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRu) "Справочник" else "Guidebook",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onSavePdf) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save PDF",
                                tint = ActionBlue
                            )
                        }
                        IconButton(onClick = onSharePdf) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = ActionBlue
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                title = if (isRu) "Mean BG (Средняя гликемия)" else "Mean BG (Average Glucose)",
                                target = if (isRu) "Цель: ≤7.8 ммоль/л (≤140 мг/дл)" else "Target: ≤7.8 mmol/L (≤140 mg/dL)",
                                desc = if (isRu) "Среднее арифметическое всех измерений. Отражает генеральный уровень гликемии и служит фундаментальной базой для расчёта большинства комплексных параметров."
                                       else "Arithmetic mean of all readings. Reflects overall glycemic baseline."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "eA1c (Расчётный гликированный гемоглобин)" else "eA1c (Estimated A1c)",
                                target = if (isRu) "Цель: ≤7.0% (≤53 ммоль/моль)" else "Target: ≤7.0% (≤53 mmol/mol)",
                                desc = if (isRu) "Математическая экстраполяция лабораторного HbA1c по формуле ADAG. Коррелирует с долгосрочным средним сахаром за 2–3 месяца без искажений от анемии или гемоглобинопатий."
                                       else "Mathematical projection of laboratory HbA1c based on the ADAG formula."
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
                                target = if (isRu) "Норма: ≥70.0% (>16 ч 48 мин/сут)" else "Target: ≥70.0% (>16h 48m/day)",
                                desc = if (isRu) "Золотой международный стандарт компенсации диабета. Каждые +10% TIR достоверно снижают риск ретинопатии на 64% и микроальбуминурии на 40%."
                                       else "Gold standard metric. Every 10% increase significantly reduces vascular complications."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TING (3.9–7.8 ммоль/л)" else "TING (70–140 mg/dL)",
                                target = if (isRu) "Норма: ≥50.0% (>12 ч/сут)" else "Target: ≥50.0% (>12h/day)",
                                desc = if (isRu) "Узкий целевой диапазон физиологической нормы здорового человека. Отражает ювелирную точность компенсации и минимальный риск сосудистого старения."
                                       else "Time in tight physiological norm. Demonstrates advanced metabolic control."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TBR (<3.9 ммоль/л и <3.0 ммоль/л)" else "TBR (<70 mg/dL and <54 mg/dL)",
                                target = if (isRu) "Норма: <4.0% (<1 ч/сут), <3.0 ммоль/л: <1.0%" else "Target: <4.0% (<1h/day), <54 mg/dL: <1.0%",
                                desc = if (isRu) "Главный параметр безопасности. Тяжёлая гипогликемия (<3.0) вызывает аритмии и неврологические нарушения. Должна быть сведена к минимуму."
                                       else "Safety priority. Low glucose triggers arrhythmias and cognitive impairment."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "TAR (>10.0 ммоль/л и >13.9 ммоль/л)" else "TAR (>180 mg/dL and >250 mg/dL)",
                                target = if (isRu) "Норма: <25.0% (<6 ч/сут), >13.9 ммоль/л: <5.0%" else "Target: <25.0% (<6h/day), >250 mg/dL: <5.0%",
                                desc = if (isRu) "Время в гипергликемии. Длительный высокий сахар повреждает гликокаликс капилляров, ведёт к дегидратации и накоплению токсичных метаболитов."
                                       else "Time in hyperglycemia. Sustained high glucose damages vascular endothelium."
                            )
                        }
                    }

                    // Section 3
                    item {
                        GuidebookSectionHeader(
                            title = if (isRu) "3. Вариабельность и лабильность" else "3. Glucose Variability & Lability"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidebookItemCard(
                                title = if (isRu) "%CV (Коэффициент вариации)" else "%CV (Coefficient of Variation)",
                                target = if (isRu) "Норма: ≤36.0%" else "Target: ≤36.0%",
                                desc = if (isRu) "Относительный разброс сахара (%CV = SD / Mean * 100%). При %CV >36% колебания становятся хаотичными, а риск скрытых ночных гипогликемий возрастает в 4 раза."
                                       else "Relative glucose swing indicator. %CV > 36% strongly correlates with hypoglycemia risk."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "SD (Стандартное отклонение)" else "SD (Standard Deviation)",
                                target = if (isRu) "Норма: ≤2.0 ммоль/л (≤36 мг/дл)" else "Target: ≤2.0 mmol/L (≤36 mg/dL)",
                                desc = if (isRu) "Абсолютная ширина разброса сахаров вокруг средней. Чем ниже SD, тем более гладкая и предсказуемая суточная кривая."
                                       else "Absolute spread around mean glucose. Lower SD means higher stability."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "GVI (Индекс гликемической лабильности)" else "GVI (Glycemic Variability Index)",
                                target = if (isRu) "Идеал здорового человека: ≤1.20" else "Healthy baseline: ≤1.20",
                                desc = if (isRu) "Отношение реальной длины кривой сахара к идеальной гладкой траектории. Выявляет пилообразные скачки сахара («американские горки»)."
                                       else "Trajectory distance ratio. Uncovers sharp up-and-down glucose rollercoasters."
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
                                target = if (isRu) "Отличный контроль: ≤35.0 баллов" else "Optimal control: ≤35.0 points",
                                desc = if (isRu) "Интегральная формула качества компенсации, объединяющая Mean BG, %CV и TIR в единый балл. Чем ниже балл, тем ближе гликемия к норме."
                                       else "Multi-factor status combining Mean BG, %CV, and TIR. Lower scores reflect better control."
                            )
                            GuidebookItemCard(
                                title = if (isRu) "GRI (Индекс гликемического риска)" else "GRI (Glycemia Risk Index)",
                                target = if (isRu) "Зона A (низкий риск): ≤20 баллов" else "Zone A (low risk): ≤20 points",
                                desc = if (isRu) "Современный валидированный индекс (0–100), учитывающий удельный вес гипо- и гипергликемий. Позволяет врачу мгновенно оценить безопасность терапии."
                                       else "Clinically validated index (0–100) weighting hypo- and hyperglycemia risks."
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
                                    text = if (isRu) "• Физиологическое запаздывание: датчики CGM измеряют концентрацию глюкозы в интерстициальной (межтканевой) жидкости, а не в крови. В период быстрых изменений отставание от капиллярной крови составляет 5–15 минут.\n\n• Погрешность сенсора (MARD): современный стандарт MARD составляет 8–10%. Возможны артефакты ночного сдавливания (компрессионные гипогликемии).\n\n• Назначение отчёта: данный аналитический отчёт носит информационно-ознакомительный характер и не является клиническим диагнозом. При расхождении самочувствия с показаниями CGM выполните замер по капле крови и обратитесь к лечащему врачу."
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
                        Button(
                            onClick = onSavePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ActionBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Сохранить" else "Save PDF",
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

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "Назад" else "Back",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
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
