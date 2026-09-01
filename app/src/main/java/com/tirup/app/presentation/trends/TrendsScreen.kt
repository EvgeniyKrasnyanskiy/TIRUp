package com.tirup.app.presentation.trends

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
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
import java.util.Locale

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Header
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
                Text(
                    text = stringResource(R.string.trends_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimaryDark
                )
            }
        }

        // Compact Period Selector (4 Primary + More Dropdown)
        item {
            CompactPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )
        }

        // AGP 24h Modal Day Chart
        item {
            BentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        detailDialogInfo = Pair(
                            "Амбулаторный профиль глюкозы (AGP)",
                            "Международный стандарт визуализации CGM: суточные профили за все дни накладываются на 24 часа. Сплошная линия — медиана 50%, тёмная полоса — 25-75% размах, светлое облако — 10-90% перцентили."
                        )
                    },
                cornerRadius = 24.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.agp_curve_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem(color = PrimaryEmerald, label = stringResource(R.string.agp_median))
                        LegendItem(color = PrimaryEmerald.copy(alpha = 0.5f), label = stringResource(R.string.agp_interquartile))
                        LegendItem(color = PrimaryEmerald.copy(alpha = 0.2f), label = stringResource(R.string.agp_outer))
                    }

                    AgpChart(
                        bins = state.percentileBins,
                        targetRanges = state.userSettings.targetRanges,
                        unit = state.userSettings.unit
                    )
                }
            }
        }

        // TIR Distribution Breakdown
        item {
            BentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        detailDialogInfo = Pair(
                            "Распределение времени в диапазонах",
                            "Целевой диапазон (TIR 3.9-10.0 ммоль/л): норма ≥70%.\nНизкий (TBR <3.9 ммоль/л): норма <4%.\nОчень низкий (<3.0 ммоль/л): норма <1%.\nВысокий (TAR >10.0 ммоль/л): норма <25%."
                        )
                    }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.tir_breakdown),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%% TIR", state.statistics.tirPercent),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.statistics.tirPercent >= state.userSettings.targetRanges.tirGoalPercent) PrimaryEmerald else ColorHigh
                        )
                    }

                    RangeDistributionBar(
                        tbrVeryLow = state.statistics.tbrVeryLowPercent,
                        tbrLow = state.statistics.tbrLowPercent,
                        tir = state.statistics.tirPercent,
                        tarHigh = state.statistics.tarHighPercent,
                        tarVeryHigh = state.statistics.tarVeryHighPercent,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Breakdown percentages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "TBR: ${String.format(Locale.US, "%.1f%%", state.statistics.tbrLowPercent + state.statistics.tbrVeryLowPercent)}", style = MaterialTheme.typography.bodySmall, color = TextMutedDark)
                        Text(text = "TING: ${String.format(Locale.US, "%.1f%%", state.statistics.tingPercent)}", style = MaterialTheme.typography.bodySmall, color = ColorTight)
                        Text(text = "TAR: ${String.format(Locale.US, "%.1f%%", state.statistics.tarHighPercent + state.statistics.tarVeryHighPercent)}", style = MaterialTheme.typography.bodySmall, color = TextMutedDark)
                    }
                }
            }
        }

        // Summary Statistics Grid
        item {
            TrendsStatsGrid(
                state = state,
                onStatClick = { title, desc ->
                    detailDialogInfo = Pair(title, desc)
                }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Detail Dialog
    detailDialogInfo?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { detailDialogInfo = null },
            title = { Text(text = title, color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = { Text(text = message, color = TextSecondaryDark, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { detailDialogInfo = null }) {
                    Text(text = stringResource(R.string.action_close), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CompactPeriodSelector(
    selectedPeriod: TrendPeriod,
    onPeriodSelected: (TrendPeriod) -> Unit
) {
    var expandedMore by remember { mutableStateOf(false) }

    val primaryPeriods = listOf(
        TrendPeriod.PERIOD_7D,
        TrendPeriod.PERIOD_14D,
        TrendPeriod.PERIOD_30D,
        TrendPeriod.PERIOD_90D
    )

    val isOverflowSelected = selectedPeriod == TrendPeriod.PERIOD_YEAR || selectedPeriod == TrendPeriod.PERIOD_ALL

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        primaryPeriods.forEach { period ->
            val isSelected = period == selectedPeriod
            val label = when (period) {
                TrendPeriod.PERIOD_7D -> stringResource(R.string.period_7d)
                TrendPeriod.PERIOD_14D -> stringResource(R.string.period_14d)
                TrendPeriod.PERIOD_30D -> stringResource(R.string.period_30d)
                TrendPeriod.PERIOD_90D -> stringResource(R.string.period_90d)
                else -> ""
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) PrimaryEmerald else DarkSurfaceElevated,
                border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else DarkBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPeriodSelected(period) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.Black else TextSecondaryDark,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Overflow "..." Dropdown button
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOverflowSelected) PrimaryEmerald else DarkSurfaceElevated,
                border = BorderStroke(1.dp, if (isOverflowSelected) PrimaryEmerald else DarkBorder),
                modifier = Modifier
                    .width(44.dp)
                    .clickable { expandedMore = true }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More periods",
                        tint = if (isOverflowSelected) Color.Black else TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expandedMore,
                onDismissRequest = { expandedMore = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.period_year)) },
                    onClick = {
                        onPeriodSelected(TrendPeriod.PERIOD_YEAR)
                        expandedMore = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.period_all)) },
                    onClick = {
                        onPeriodSelected(TrendPeriod.PERIOD_ALL)
                        expandedMore = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendsStatsGrid(
    state: TrendsUiState,
    onStatClick: (String, String) -> Unit
) {
    val stats = state.statistics
    val isMmol = state.userSettings.unit == GlucoseUnit.MMOL_L

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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mean
        BentoCard(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    onStatClick("Средний сахар", "Среднее значение за выбранный период. Цель: ≤7.0 ммоль/л.")
                }
        ) {
            Column {
                Text(text = stringResource(R.string.card_mean), style = MaterialTheme.typography.bodySmall, color = TextMutedDark)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = meanColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // CV
        BentoCard(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    onStatClick("Вариабельность (%CV)", "Коэффициент вариабельности гликемии. Цель: ≤36.0%.")
                }
        ) {
            Column {
                Text(text = stringResource(R.string.card_cv), style = MaterialTheme.typography.bodySmall, color = TextMutedDark)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format(Locale.US, "%.1f%%", stats.cvPercent),
                    style = MaterialTheme.typography.titleMedium,
                    color = cvColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // GMI
        BentoCard(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    onStatClick("Расчётный HbA1c (GMI)", "Оценка гликированного гемоглобина. Цель: ≤6.5-7.0%.")
                }
        ) {
            Column {
                Text(text = stringResource(R.string.card_gmi), style = MaterialTheme.typography.bodySmall, color = TextMutedDark)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                    style = MaterialTheme.typography.titleMedium,
                    color = gmiColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = color,
            modifier = Modifier.size(10.dp)
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
    }
}
