package com.tirup.app.presentation.trends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
import java.util.Locale

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Screen Title
        item {
            Text(
                text = stringResource(R.string.trends_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimaryDark
            )
        }

        // Period Selector Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(TrendPeriod.values()) { period ->
                    val isSelected = period == state.selectedPeriod
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryEmerald else DarkBorder.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else DarkBorder),
                        onClick = { viewModel.selectPeriod(period) }
                    ) {
                        Text(
                            text = stringResource(period.stringResId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) androidx.compose.ui.graphics.Color.Black else TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // AGP Percentile Chart Card
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.agp_curve_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = stringResource(
                                R.string.agp_target_range,
                                state.userSettings.targetRanges.tirLowMmol,
                                state.userSettings.targetRanges.tirHighMmol
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "• 50% Median", color = PrimaryEmerald, fontSize = 11.sp)
                        Text(text = "• 25-75% Interquartile", color = PrimaryEmerald.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(text = "• 10-90% Range", color = PrimaryEmerald.copy(alpha = 0.3f), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AgpChart(
                        bins = state.percentileBins,
                        targetRanges = state.userSettings.targetRanges,
                        unit = state.userSettings.unit
                    )
                }
            }
        }

        // Summary Statistics Card for Period
        item {
            PeriodMetricsCard(state = state)
        }

        // 24-Hour Heatmap
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = stringResource(R.string.heatmap_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HeatmapChart(daysData = state.heatmapData)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PeriodMetricsCard(state: TrendsUiState) {
    val stats = state.statistics
    val unit = state.userSettings.unit

    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.tir_breakdown),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryDark
            )

            RangeDistributionBar(
                tbrVeryLow = stats.tbrVeryLowPercent,
                tbrLow = stats.tbrLowPercent,
                tir = stats.tirPercent,
                tarHigh = stats.tarHighPercent,
                tarVeryHigh = stats.tarVeryHighPercent,
                height = 18.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = stringResource(R.string.mode_tir), style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                    Text(text = String.format(Locale.US, "%.1f%%", stats.tirPercent), style = MaterialTheme.typography.titleMedium, color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = stringResource(R.string.mode_ting), style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                    Text(text = String.format(Locale.US, "%.1f%%", stats.tingPercent), style = MaterialTheme.typography.titleMedium, color = ColorTight, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = stringResource(R.string.card_mean), style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                    val meanStr = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", stats.meanMmol) else String.format(Locale.US, "%d", (stats.meanMmol * 18.0182).toInt())
                    Text(text = "$meanStr ${unit.label}", style = MaterialTheme.typography.titleMedium, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = stringResource(R.string.card_cv), style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                    val isCvGood = stats.cvPercent <= 36.0 && stats.cvPercent > 0
                    Text(text = String.format(Locale.US, "%.1f%%", stats.cvPercent), style = MaterialTheme.typography.titleMedium, color = if (isCvGood) PrimaryEmerald else ColorLow, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
