package com.tirup.app.presentation.trends

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var targetMode by remember { mutableStateOf(TargetMode.TIR) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Settings Menu",
                            tint = onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.trends_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = onSurface
                    )
                }

                // Mode Selector: TIR vs TING (Compact Pills)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (targetMode == TargetMode.TIR) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier.clickable { targetMode = TargetMode.TIR }
                    ) {
                        Text(
                            text = "TIR",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (targetMode == TargetMode.TIR) Color.White else onSurfaceVariant,
                            fontWeight = if (targetMode == TargetMode.TIR) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (targetMode == TargetMode.TING) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier.clickable { targetMode = TargetMode.TING }
                    ) {
                        Text(
                            text = "TING",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (targetMode == TargetMode.TING) Color.White else onSurfaceVariant,
                            fontWeight = if (targetMode == TargetMode.TING) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Compact Period Selector (4 Primary + More Dropdown)
        item {
            CompactPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )
        }

        val isRu = state.userSettings.language.equals("RU", ignoreCase = true)
        val isMmol = state.userSettings.unit == GlucoseUnit.MMOL_L

        // AGP 24h Modal Day Chart
        item {
            BentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        detailDialogInfo = Pair(
                            if (isRu) "Амбулаторный профиль глюкозы (AGP)" else "Ambulatory Glucose Profile (AGP)",
                            if (isRu) "Международный стандарт визуализации CGM: суточные профили за все дни накладываются на 24 часа. Сплошная линия — медиана 50%, тёмная полоса — 25-75% размах, светлое облако — 10-90% перцентили."
                            else "International CGM visualization standard: daily profiles from all days overlaid across 24 hours. Solid line is 50% median, darker band is 25-75% IQR, light cloud is 10-90% percentiles."
                        )
                    },
                cornerRadius = 24.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.agp_curve_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
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

        // TIR / Range Distribution Breakdown
        item {
            val stats = state.statistics
            val tbrTotal = stats.tbrLowPercent + stats.tbrVeryLowPercent
            val tarTotal = stats.tarHighPercent + stats.tarVeryHighPercent

            BentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        detailDialogInfo = Pair(
                            if (isRu) "Распределение времени в диапазонах" else "Time in Ranges Breakdown",
                            if (isRu) "Целевой диапазон (TIR): норма ≥70%.\nУзкий (TING): норма ≥50%.\nНизкий (TBR): норма <4%.\nВысокий (TAR): норма <25%."
                            else "Target range (TIR): target ≥70%.\nTight (TING): target ≥50%.\nLow (TBR): target <4%.\nHigh (TAR): target <25%."
                        )
                    }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isRu) "Распределение по диапазонам" else "Time in Ranges Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface
                        )

                        Text(
                            text = if (targetMode == TargetMode.TING) {
                                if (isRu) String.format(Locale.US, "%.1f%% TING (цель ≥50%%)", stats.tingPercent)
                                else String.format(Locale.US, "%.1f%% TING (goal ≥50%%)", stats.tingPercent)
                            } else {
                                if (isRu) String.format(Locale.US, "%.1f%% TIR (цель ≥70%%)", stats.tirPercent)
                                else String.format(Locale.US, "%.1f%% TIR (goal ≥70%%)", stats.tirPercent)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    RangeDistributionBar(
                        tbrVeryLow = stats.tbrVeryLowPercent,
                        tbrLow = stats.tbrLowPercent,
                        tir = stats.tirPercent,
                        tarHigh = stats.tarHighPercent,
                        tarVeryHigh = stats.tarVeryHighPercent,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TBR: ${String.format(Locale.US, "%.1f%%", tbrTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tbrTotal <= 4.0) onSurfaceVariant else ColorVeryHigh
                        )
                        Text(
                            text = "TING: ${String.format(Locale.US, "%.1f%%", stats.tingPercent)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "TAR: ${String.format(Locale.US, "%.1f%%", tarTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tarTotal <= 25.0) onSurfaceVariant else ColorHigh
                        )
                    }
                }
            }
        }

        // Summary Metrics: Variant A (Apple Health Style: 1 Full-Width Card + 2 Equal Column Cards)
        // 1. Mean Glucose (Full Width Hero Bento Card)
        item {
            val meanValStr = if (isMmol) String.format(Locale.US, "%.1f", state.statistics.meanMmol)
                             else String.format(Locale.US, "%d", (state.statistics.meanMmol * 18.0182).toInt())
            val unitStr = if (isMmol) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
            val isMeanGood = state.statistics.meanMmol in 3.9..7.8

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val valStr = if (isMmol) "${String.format(Locale.US, "%.1f", state.statistics.meanMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                 else "${(state.statistics.meanMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                    val targetVal = if (isMmol) "≤7.8 ${if (isRu) "ммоль/л" else "mmol/L"}" else "≤140 ${if (isRu) "мг/дл" else "mg/dL"}"
                    detailDialogInfo = Pair(
                        if (isRu) "Средний сахар (Mean)" else "Average Glucose (Mean)",
                        if (isRu) "Средний уровень сахара за выбранный период: $valStr. Клиническая цель: $targetVal."
                        else "Average glucose over the selected period: $valStr. Clinical target: $targetVal."
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isRu) "Средний сахар за период" else "Mean Glucose for Period",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = meanValStr,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (isMeanGood) PrimaryEmerald else ColorHigh,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = unitStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isMeanGood) PrimaryEmerald else ColorHigh).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (isMeanGood) (if (isRu) "В норме" else "In Target") else (if (isRu) "Выше цели" else "Above Target"),
                            color = if (isMeanGood) PrimaryEmerald else ColorHigh,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 2. Variability (%CV) & Estimated eA1c (Two Half-Width Bento Cards)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrendSummaryCard(
                    title = if (isRu) "Вариабельность (%CV)" else "Variability (%CV)",
                    value = String.format(Locale.US, "%.1f%%", state.statistics.cvPercent),
                    unit = "",
                    valueColor = if (state.statistics.cvPercent <= 36.0) PrimaryEmerald else ColorHigh,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        detailDialogInfo = Pair(
                            if (isRu) "Вариабельность (%CV)" else "Glucose Variability (%CV)",
                            if (isRu) "Коэффициент вариабельности: ${String.format(Locale.US, "%.1f%%", state.statistics.cvPercent)}. Норма: ≤36.0%. Меньшее значение означает более стабильную гликемию."
                            else "Coefficient of variation: ${String.format(Locale.US, "%.1f%%", state.statistics.cvPercent)}. Target: ≤36.0%. Lower value indicates more stable glycemia."
                        )
                    }
                )

                TrendSummaryCard(
                    title = if (isRu) "Расчётный eA1c" else "Estimated eA1c",
                    value = String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent),
                    unit = "",
                    valueColor = if (state.statistics.gmiPercent <= 7.0) PrimaryEmerald else ColorHigh,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        detailDialogInfo = Pair(
                            if (isRu) "Расчётный HbA1c (ADAG)" else "Estimated HbA1c (ADAG)",
                            if (isRu) "Расчётный гликированный гемоглобин: ${String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent)} (${state.statistics.hba1cMmolMol} mmol/mol). Цель: ≤7.0%."
                            else "Estimated glycated hemoglobin: ${String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent)} (${state.statistics.hba1cMmolMol} mmol/mol). Target: ≤7.0%."
                        )
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // Detail Popups
    if (detailDialogInfo != null) {
        val isRu = state.userSettings.language.equals("RU", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { detailDialogInfo = null },
            title = {
                Text(
                    text = detailDialogInfo!!.first,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = detailDialogInfo!!.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { detailDialogInfo = null }) {
                    Text(text = if (isRu) "Понятно" else "OK", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
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
    var expandedDropdown by remember { mutableStateOf(false) }

    val primaryPeriods = listOf(
        TrendPeriod.PERIOD_7D,
        TrendPeriod.PERIOD_14D,
        TrendPeriod.PERIOD_30D,
        TrendPeriod.PERIOD_90D
    )

    val isDropdownSelected = selectedPeriod !in primaryPeriods

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        primaryPeriods.forEach { period ->
            val isSelected = selectedPeriod == period
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) ActionBlue else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ActionBlue else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPeriodSelected(period) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(period.stringResId),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // Dropdown Menu Button
        Box(modifier = Modifier.weight(0.7f)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDropdownSelected) ActionBlue else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isDropdownSelected) ActionBlue else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDropdown = true }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isDropdownSelected) stringResource(selectedPeriod.stringResId) else "•••",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDropdownSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isDropdownSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.period_year)) },
                    onClick = {
                        onPeriodSelected(TrendPeriod.PERIOD_YEAR)
                        expandedDropdown = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.period_all)) },
                    onClick = {
                        onPeriodSelected(TrendPeriod.PERIOD_ALL)
                        expandedDropdown = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendSummaryCard(
    title: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp
        )
    }
}
