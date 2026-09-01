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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
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
                        tint = TextPrimaryDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Медицинский отчёт",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Стандартизированный экспорт бланка AGP для врача",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedDark
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

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Live Report AGP Sheet Preview Modal
    if (state.showLiveDetailDialog) {
        val minTs = state.liveReadings.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val maxTs = state.liveReadings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateRangeStr = "${fmt.format(Date(minTs))} — ${fmt.format(Date(maxTs))}"
        val periodName = when (state.livePeriod) {
            TrendPeriod.PERIOD_7D -> "7 Дней"
            TrendPeriod.PERIOD_14D -> "14 Дней (AGP)"
            TrendPeriod.PERIOD_30D -> "30 Дней"
            TrendPeriod.PERIOD_90D -> "90 Дней"
            TrendPeriod.PERIOD_YEAR -> "1 Год"
            TrendPeriod.PERIOD_ALL -> "Всё время"
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
            periodLabel = "Исторический отчёт",
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
}

@Composable
private fun LiveReportCard(
    state: ReportsUiState,
    viewModel: ReportsViewModel,
    onCardClick: () -> Unit
) {
    val stats = state.liveStatistics

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        cornerRadius = 24.dp,
        backgroundColor = DarkSurfaceElevated
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
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "${state.liveReadings.size} точек • ${stats.daysCount} дн. (нажмите для бланка)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedDark
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
            ReportMetricsColumn(stats = stats, unit = state.userSettings.unit)

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
                        containerColor = PrimaryEmerald,
                        contentColor = Color.Black
                    ),
                    enabled = state.liveReadings.isNotEmpty() && !state.isGeneratingLive
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Сохранить PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.generateAndShareLivePdf() },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PrimaryEmerald),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryEmerald
                    ),
                    enabled = state.liveReadings.isNotEmpty() && !state.isGeneratingLive
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Поделиться", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hist.hasData) { onCardClick() },
        cornerRadius = 24.dp,
        backgroundColor = DarkSurfaceElevated
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row
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
                    Column {
                        Text(
                            text = stringResource(R.string.report_historical_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (hist.hasData) "${hist.readings.size} точек • ${hist.dateRangeStr} (бланк)" else "Импорт из xDrip CSV / ZIP",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedDark
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrimaryEmerald
                    )
                    Text(
                        text = "Импорт и обработка данных...",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.SemiBold
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
                ReportMetricsColumn(stats = hist.statistics, unit = state.userSettings.unit)

                // Action Buttons Row (Save PDF, Share, and Load File)
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
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Сохранить PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.generateAndShareHistoricalPdf() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrimaryEmerald),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryEmerald
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Поделиться", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondaryDark
                        )
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Загрузить файл", fontSize = 12.sp)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    enabled = !state.isImporting
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            color = PrimaryEmerald,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = PrimaryEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Загрузить файл",
                        color = TextPrimaryDark,
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
    unit: GlucoseUnit
) {
    val isMmol = unit == GlucoseUnit.MMOL_L
    val meanVal = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt())
    val meanColor = when {
        stats.meanMmol <= 0.0 -> TextPrimaryDark
        stats.meanMmol <= 7.0 -> ColorTight
        stats.meanMmol <= 8.5 -> ColorHigh
        else -> ColorVeryHigh
    }

    val cvColor = when {
        stats.cvPercent <= 0.0 -> TextPrimaryDark
        stats.cvPercent <= 36.0 -> PrimaryEmerald
        else -> ColorHigh
    }

    val gmiColor = when {
        stats.gmiPercent <= 0.0 -> TextPrimaryDark
        stats.gmiPercent <= 6.5 -> PrimaryEmerald
        stats.gmiPercent <= 7.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    val tirColor = when {
        stats.tirPercent <= 0.0 -> TextPrimaryDark
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
            label = "$atEmoji Активное время сенсора",
            target = "Цель: ≥70.0%",
            value = String.format(Locale.US, "%.1f%%", stats.activeTimePercent),
            valueColor = atColor
        )
        ReportMetricRow(
            label = "Mean BG (средний сахар)",
            target = "Цель: ≤7.8",
            value = meanVal,
            valueColor = meanColor
        )
        ReportMetricRow(
            label = "eA1c (расчётный ГГ)",
            target = "Цель: ≤7.0%",
            value = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
            valueColor = gmiColor
        )
        ReportMetricRow(
            label = "TIR (3.9–10.0 ммоль/л)",
            target = "Цель: ≥70%",
            value = String.format(Locale.US, "%.0f%%", stats.tirPercent),
            valueColor = tirColor
        )
        ReportMetricRow(
            label = "TING (3.9–7.8 ммоль/л)",
            target = "Цель: ≥50%",
            value = String.format(Locale.US, "%.0f%%", stats.tingPercent),
            valueColor = if (stats.tingPercent >= 50.0) PrimaryEmerald else ColorHigh
        )
        val tbrTotal = stats.tbrLowPercent + stats.tbrVeryLowPercent
        ReportMetricRow(
            label = "TBR < 3.9 ммоль/л",
            target = "Цель: <4%",
            value = String.format(Locale.US, "%.1f%%", tbrTotal),
            valueColor = if (tbrTotal <= 4.0) PrimaryEmerald else ColorVeryHigh
        )
        val tarTotal = stats.tarHighPercent + stats.tarVeryHighPercent
        ReportMetricRow(
            label = "TAR > 10.0 ммоль/л",
            target = "Цель: <25%",
            value = String.format(Locale.US, "%.0f%%", tarTotal),
            valueColor = if (tarTotal <= 25.0) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = "GVI (лабильность)",
            target = "Цель: ≤1.20",
            value = String.format(Locale.US, "%.2f", stats.gvi),
            valueColor = if (stats.gvi <= 1.20) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = "PGS (гликемический статус)",
            target = "Цель: ≤35.0",
            value = String.format(Locale.US, "%.1f", stats.pgs),
            valueColor = if (stats.pgs <= 35.0) PrimaryEmerald else ColorHigh
        )
        ReportMetricRow(
            label = "Вариабельность (%CV)",
            target = "Цель: ≤36.0%",
            value = String.format(Locale.US, "%.1f%%", stats.cvPercent),
            valueColor = cvColor
        )
        ReportMetricRow(
            label = "GRI (индекс риска)",
            target = "Цель: ≤40.0",
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
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = DarkBorder.copy(alpha = 0.6f)
            ) {
                Text(
                    text = target,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedDark,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 10.sp
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
