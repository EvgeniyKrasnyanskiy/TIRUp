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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
import com.tirup.app.presentation.trends.CompactPeriodSelector
import kotlinx.coroutines.flow.collectLatest
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
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = stringResource(R.string.reports_subtitle),
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

    // Live Report Detail Dialog
    if (state.showLiveDetailDialog) {
        ReportSummaryDialog(
            title = stringResource(R.string.report_live_title),
            stats = state.liveStatistics,
            unit = state.userSettings.unit,
            tirGoal = state.userSettings.targetRanges.tirGoalPercent,
            tingGoal = state.userSettings.targetRanges.tingGoalPercent,
            onDismiss = { viewModel.showLiveDetails(false) }
        )
    }

    // Historical Report Detail Dialog
    if (state.showHistoricalDetailDialog) {
        ReportSummaryDialog(
            title = stringResource(R.string.report_historical_title),
            stats = state.historicalReport.statistics,
            unit = state.userSettings.unit,
            dateRange = state.historicalReport.dateRangeStr,
            tirGoal = state.userSettings.targetRanges.tirGoalPercent,
            tingGoal = state.userSettings.targetRanges.tingGoalPercent,
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
            // Header Row: Title & Action Icons
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
                            text = "${state.liveReadings.size} точек • ${stats.daysCount} дн.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedDark
                        )
                    }
                }

                // Action Icons (Save & Share)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isGeneratingLive) {
                        CircularProgressIndicator(
                            color = PrimaryEmerald,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.saveLivePdfToDownloads() },
                            enabled = state.liveReadings.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save PDF",
                                tint = if (state.liveReadings.isNotEmpty()) PrimaryEmerald else TextMutedDark
                            )
                        }
                        IconButton(
                            onClick = { viewModel.generateAndShareLivePdf() },
                            enabled = state.liveReadings.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = if (state.liveReadings.isNotEmpty()) PrimaryEmerald else TextMutedDark
                            )
                        }
                    }
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

            // Parameters listed line by line
            ReportMetricsColumn(stats = stats, unit = state.userSettings.unit)
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
                            imageVector = Icons.Default.FileUpload,
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
                            text = if (hist.hasData) "${hist.readings.size} точек • ${hist.dateRangeStr}" else "xDrip CSV или ZIP файл",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedDark
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isGeneratingHistorical) {
                        CircularProgressIndicator(
                            color = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (hist.hasData) {
                        IconButton(onClick = onPickFile) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Replace File",
                                tint = Color(0xFF818CF8)
                            )
                        }
                        IconButton(onClick = { viewModel.saveHistoricalPdfToDownloads() }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save PDF",
                                tint = PrimaryEmerald
                            )
                        }
                        IconButton(onClick = { viewModel.generateAndShareHistoricalPdf() }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = PrimaryEmerald
                            )
                        }
                        IconButton(onClick = { viewModel.clearHistoricalReport() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear",
                                tint = ColorHigh
                            )
                        }
                    }
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

                // Metrics line by line
                ReportMetricsColumn(stats = hist.statistics, unit = state.userSettings.unit)

                // Upload another file button
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    enabled = !state.isImporting
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Загрузить другой файл", color = TextSecondaryDark, fontSize = 13.sp)
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
                        text = stringResource(R.string.import_and_report),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ReportMetricRow(label = "TIR в целевом (3.9–10.0)", target = "Цель: ≥70%", value = String.format(Locale.US, "%.1f%%", stats.tirPercent), valueColor = tirColor)
        ReportMetricRow(label = "Средний сахар (Mean)", target = "Цель: ≤7.0", value = meanVal, valueColor = meanColor)
        ReportMetricRow(label = "Вариабельность (%CV)", target = "Цель: ≤36.0%", value = String.format(Locale.US, "%.1f%%", stats.cvPercent), valueColor = cvColor)
        ReportMetricRow(label = "Расчётный HbA1c (GMI)", target = "Цель: ≤6.5%", value = String.format(Locale.US, "%.1f%%", stats.gmiPercent), valueColor = gmiColor)
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

@Composable
private fun ReportSummaryDialog(
    title: String,
    stats: GlucoseStatistics,
    unit: GlucoseUnit,
    dateRange: String = "",
    tirGoal: Int = 70,
    tingGoal: Int = 50,
    onDismiss: () -> Unit
) {
    val isMmol = unit == GlucoseUnit.MMOL_L
    val meanVal = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt())
    val meanColor = when {
        stats.meanMmol <= 0.0 -> TextPrimaryDark
        stats.meanMmol <= 7.0 -> ColorTight
        stats.meanMmol <= 8.5 -> ColorHigh
        else -> ColorVeryHigh
    }

    val cvColor = if (stats.cvPercent <= 36.0) PrimaryEmerald else ColorHigh
    val gmiColor = when {
        stats.gmiPercent <= 6.5 -> PrimaryEmerald
        stats.gmiPercent <= 7.0 -> ColorHigh
        else -> ColorVeryHigh
    }
    val tirColor = if (stats.tirPercent >= tirGoal) PrimaryEmerald else if (stats.tirPercent >= 50.0) ColorHigh else ColorVeryHigh
    val tingColor = if (stats.tingPercent >= tingGoal) ColorTight else ColorHigh
    val tbrColor = if ((stats.tbrLowPercent + stats.tbrVeryLowPercent) < 4.0) PrimaryEmerald else ColorVeryLow
    val severeHypoColor = if (stats.tbrVeryLowPercent < 1.0) PrimaryEmerald else ColorVeryLow

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dateRange.isNotEmpty()) {
                    Text(text = "Период: $dateRange", style = MaterialTheme.typography.bodyMedium, color = TextMutedDark)
                }
                Text(text = "Точек данных: ${stats.totalCount} (дней: ${stats.daysCount})", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)

                Spacer(modifier = Modifier.height(4.dp))

                DialogTargetRow(label = "TIR в целевом (3.9–10.0):", value = String.format(Locale.US, "%.1f%% (цель ≥%d%%)", stats.tirPercent, tirGoal), color = tirColor)
                DialogTargetRow(label = "TING в узком (3.9–7.8):", value = String.format(Locale.US, "%.1f%% (цель ≥%d%%)", stats.tingPercent, tingGoal), color = tingColor)
                DialogTargetRow(label = "Средний сахар (Mean):", value = "$meanVal (цель ≤7.0)", color = meanColor)
                DialogTargetRow(label = "Вариабельность (%CV):", value = "${String.format(Locale.US, "%.1f%%", stats.cvPercent)} (цель ≤36.0%)", color = cvColor)
                DialogTargetRow(label = "Расчётный HbA1c (GMI):", value = "${String.format(Locale.US, "%.1f%%", stats.gmiPercent)} (цель ≤6.5%)", color = gmiColor)
                DialogTargetRow(label = "Гипогликемии (TBR <3.9):", value = "${String.format(Locale.US, "%.1f%%", stats.tbrLowPercent + stats.tbrVeryLowPercent)} (норма <4.0%)", color = tbrColor)
                DialogTargetRow(label = "Тяжёлая гипогликемия (<3.0):", value = "${String.format(Locale.US, "%.1f%%", stats.tbrVeryLowPercent)} (норма <1.0%)", color = severeHypoColor)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DialogTargetRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
    }
}
