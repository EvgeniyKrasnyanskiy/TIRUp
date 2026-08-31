package com.tirup.app.presentation.reports

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ReportEvent.SharePdf -> {
                    context.startActivity(event.shareIntent)
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
                                text = "Standard 14-Day AGP Sheet (A4)",
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

                    // Key Stats in Report
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "TIR (≥70%)", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                            Text(text = String.format(Locale.US, "%.1f%%", state.statistics.tirPercent), style = MaterialTheme.typography.titleLarge, color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(text = "%CV (≤36%)", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                            Text(text = String.format(Locale.US, "%.1f%%", state.statistics.cvPercent), style = MaterialTheme.typography.titleLarge, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(text = "GMI", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                            Text(text = String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent), style = MaterialTheme.typography.titleLarge, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        }
                    }

                    RangeDistributionBar(
                        tbrVeryLow = state.statistics.tbrVeryLowPercent,
                        tbrLow = state.statistics.tbrLowPercent,
                        tir = state.statistics.tirPercent,
                        tarHigh = state.statistics.tarHighPercent,
                        tarVeryHigh = state.statistics.tarVeryHighPercent
                    )
                }
            }
        }

        // Action Button: Generate & Share PDF
        item {
            Button(
                onClick = { viewModel.generateAndSharePdf() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryEmerald,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                ),
                enabled = !state.isGenerating && state.readings.isNotEmpty()
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Generating A4 Document…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_pdf),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
