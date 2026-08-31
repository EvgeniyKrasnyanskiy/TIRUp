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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
import com.tirup.app.presentation.trends.TrendPeriod
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFileAndGenerate(uri)
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Title
        item {
            Column {
                Text(
                    text = stringResource(R.string.reports_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimaryDark
                )
                Text(
                    text = stringResource(R.string.reports_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Period Selection Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.report_period_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondaryDark
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TrendPeriod.values()) { period ->
                        val isSelected = period == selectedPeriod
                        val label = when (period) {
                            TrendPeriod.PERIOD_7D -> stringResource(R.string.period_7d)
                            TrendPeriod.PERIOD_14D -> stringResource(R.string.period_14d)
                            TrendPeriod.PERIOD_30D -> stringResource(R.string.period_30d)
                            TrendPeriod.PERIOD_90D -> stringResource(R.string.period_90d)
                            TrendPeriod.PERIOD_YEAR -> stringResource(R.string.period_year)
                            TrendPeriod.PERIOD_ALL -> stringResource(R.string.period_all)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryEmerald else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else DarkBorder),
                            modifier = Modifier.clickable { viewModel.selectPeriod(period) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Color.Black else TextSecondaryDark,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Preview Bento Card
        item {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                backgroundColor = DarkSurfaceElevated
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Standard A4 AGP Medical Sheet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "${state.readings.size} readings • ${state.statistics.daysCount} active days",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedDark
                            )
                        }
                    }

                    RangeDistributionBar(
                        tbrVeryLow = state.statistics.tbrVeryLowPercent,
                        tbrLow = state.statistics.tbrLowPercent,
                        tir = state.statistics.tirPercent,
                        tarHigh = state.statistics.tarHighPercent,
                        tarVeryHigh = state.statistics.tarVeryHighPercent,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Key Clinical Summaries
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.card_mean),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedDark
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f %s", state.statistics.meanMmol, stringResource(R.string.unit_mmol)),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.card_cv),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedDark
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", state.statistics.cvPercent),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (state.statistics.cvPercent <= 36.0) PrimaryEmerald else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.card_gmi),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedDark
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Button 1: Save to Downloads
                Button(
                    onClick = { viewModel.savePdfToDownloads() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryEmerald,
                        contentColor = Color.Black
                    ),
                    enabled = !state.isGenerating && state.readings.isNotEmpty()
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.save_pdf_downloads),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Button 2: Share / Send
                OutlinedButton(
                    onClick = { viewModel.generateAndSharePdf() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PrimaryEmerald),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryEmerald
                    ),
                    enabled = !state.isGenerating && state.readings.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_pdf),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                // Button 3: Import & Report directly on this screen
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondaryDark
                    ),
                    enabled = !state.isImporting
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            color = PrimaryEmerald,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.import_and_report),
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }

                if (state.importMessage != null) {
                    Text(
                        text = state.importMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryEmerald,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
