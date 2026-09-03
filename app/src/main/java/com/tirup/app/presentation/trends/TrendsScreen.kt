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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tirup.app.domain.calculator.PatternRecognitionEngine
import com.tirup.app.domain.calculator.PatternSeverity
import com.tirup.app.domain.calculator.TirAdvisorEngine
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.BentoMetricCompact
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
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
    var dismissedPatternIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var agpCardMode by rememberSaveable { mutableStateOf(0) } // 0 = Chart, 1 = Metrics

    val context = androidx.compose.ui.platform.LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isRu = state.userSettings.language.equals("RU", ignoreCase = true)
    val isMmol = state.userSettings.unit == GlucoseUnit.MMOL_L

    val detectedPatterns = remember(state.percentileBins, state.statistics, isMmol) {
        PatternRecognitionEngine.analyze(
            bins = state.percentileBins,
            stats = state.statistics,
            isMmol = isMmol
        )
    }

    val visiblePatterns = remember(detectedPatterns, dismissedPatternIds) {
        detectedPatterns.filter { it.id !in dismissedPatternIds }
    }

    val tirInsights = remember(state.percentileBins, state.statistics) {
        TirAdvisorEngine.generateInsights(
            bins = state.percentileBins,
            stats = state.statistics
        )
    }

    LaunchedEffect(detectedPatterns) {
        detectedPatterns.forEach { pattern ->
            if (pattern.id !in dismissedPatternIds) {
                com.tirup.app.data.alert.GlucoseAlertManager.notifyPatternIfNew(context, pattern, isRu)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header with pinned Compact Period Selector
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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

                // Pinned Compact Period Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    CompactPeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { viewModel.selectPeriod(it) }
                    )
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



        // 1. Detected Clinical Patterns (Placed at the very top under Period Selector)
        val hasSufficientData = state.statistics.daysCount >= 3 || state.statistics.totalCount >= 100
        if (hasSufficientData && visiblePatterns.isNotEmpty()) {
            item {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRu) "Обнаруженные паттерны" else "Detected Glycemic Patterns",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                            }

                            Text(
                                text = "${visiblePatterns.size} ${if (isRu) "событ." else "events"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visiblePatterns.forEach { pattern ->
                                val badgeColor = when (pattern.severity) {
                                    PatternSeverity.ALERT -> ColorVeryLow
                                    PatternSeverity.WARNING -> ColorHigh
                                    PatternSeverity.POSITIVE -> PrimaryEmerald
                                    PatternSeverity.INFO -> ActionBlue
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = pattern.icon,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isRu) pattern.titleRu else pattern.titleEn,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isRu) pattern.descriptionRu else pattern.descriptionEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { dismissedPatternIds = dismissedPatternIds + pattern.id },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Time in Ranges Distribution (TIR, TING, TBR, TAR)
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

                        val tirColor = if (stats.tirPercent >= 70.0) PrimaryEmerald else ColorHigh
                        Text(
                            text = if (isRu) "${String.format(Locale.US, "%.1f%%", stats.tirPercent)} TIR (цель ≥70%)"
                                   else "${String.format(Locale.US, "%.1f%%", stats.tirPercent)} TIR (goal ≥70%)",
                            style = MaterialTheme.typography.titleSmall,
                            color = tirColor,
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
                            color = onSurfaceVariant,
                            fontWeight = FontWeight.Medium
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

        // 2. AGP 24h Modal Day Chart
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Selector Toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (agpCardMode == 0) ActionBlue else Color.Transparent,
                                modifier = Modifier.clickable { agpCardMode = 0 }
                            ) {
                                Text(
                                    text = if (isRu) "📊 График" else "📊 Chart",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (agpCardMode == 0) Color.White else onSurfaceVariant,
                                    fontWeight = if (agpCardMode == 0) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (agpCardMode == 1) ActionBlue else Color.Transparent,
                                modifier = Modifier.clickable { agpCardMode = 1 }
                            ) {
                                Text(
                                    text = if (isRu) "🔢 Параметры" else "🔢 Metrics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (agpCardMode == 1) Color.White else onSurfaceVariant,
                                    fontWeight = if (agpCardMode == 1) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (agpCardMode == 0) {
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
                    } else {
                        AgpMetricsGrid(
                            stats = state.statistics,
                            unit = state.userSettings.unit,
                            isRu = isRu,
                            onSurfaceVariant = onSurfaceVariant,
                            onMetricClick = { title, body -> detailDialogInfo = Pair(title, body) }
                        )
                    }
                }
            }
        }

        // 3. Actionable Clinical Guidance to Boost TIR
        if (tirInsights.isNotEmpty()) {
            item {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💡", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRu) "Фокусы для роста TIR" else "Actionable TIR Focus",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                            }
                            Text(
                                text = "${tirInsights.size} ${if (isRu) "совета" else "tips"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tirInsights.forEach { insight ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = insight.icon,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isRu) insight.titleRu else insight.titleEn,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onSurface,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = ActionBlue.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = if (isRu) insight.badgeRu else insight.badgeEn,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ActionBlue,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isRu) insight.adviceRu else insight.adviceEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = onSurfaceVariant,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
    }

    // Detail Popups
    if (detailDialogInfo != null) {
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

@Composable
private fun AgpMetricsGrid(
    stats: com.tirup.app.domain.model.GlucoseStatistics,
    unit: GlucoseUnit,
    isRu: Boolean,
    onSurfaceVariant: Color,
    onMetricClick: (String, String) -> Unit
) {
    val minVal = stats.minMmol
    val maxVal = stats.maxMmol

    val meanValStr = if (stats.meanMmol > 0.0) {
        if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", stats.meanMmol)
        else String.format(Locale.US, "%d", (stats.meanMmol * 18.0182).toInt())
    } else "--"

    val meanColor = when {
        stats.meanMmol <= 0.0 -> onSurfaceVariant
        stats.meanMmol <= 7.0 -> PrimaryEmerald
        stats.meanMmol <= 7.8 -> ColorTargetSoft
        stats.meanMmol <= 10.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    val sdVal = stats.sdMmol
    val sdValStr = if (sdVal > 0.0) {
        if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", sdVal)
        else "${(sdVal * 18.0182).toInt()}"
    } else "--"
    val isSdGood = sdVal in 0.01..2.0

    val cvValStr = if (stats.cvPercent > 0.0) String.format(Locale.US, "%.1f%%", stats.cvPercent) else "--"
    val isCvGood = stats.cvPercent in 0.01..36.0

    val ea1cStr = if (stats.gmiPercent > 0.0) String.format(Locale.US, "%.1f%%", stats.gmiPercent) else "--"
    val isEa1cGood = stats.gmiPercent in 0.01..7.0

    val tirValStr = if (stats.tirPercent > 0.0) "${stats.tirPercent.toInt()}%" else "--"
    val tirColor = when {
        stats.tirPercent <= 0.0 -> onSurfaceVariant
        stats.tirPercent >= 70.0 -> PrimaryEmerald
        stats.tirPercent >= 50.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    val tingValStr = if (stats.tingPercent > 0.0) "${stats.tingPercent.toInt()}%" else "--"
    val isTingGood = stats.tingPercent >= 50.0

    val tbrVal = stats.tbrLowPercent + stats.tbrVeryLowPercent
    val tbrValStr = if (tbrVal > 0.0) String.format(Locale.US, "%.1f%%", tbrVal) else "0%"
    val isTbrGood = tbrVal <= 4.0

    val tarVal = stats.tarHighPercent + stats.tarVeryHighPercent
    val tarValStr = if (tarVal > 0.0) "${tarVal.toInt()}%" else "0%"
    val isTarGood = tarVal <= 25.0

    val griValStr = if (stats.gri > 0.0) "${stats.gri.toInt()}" else "--"
    val griColor = when {
        stats.gri <= 0.0 -> onSurfaceVariant
        stats.gri <= 20.0 -> PrimaryEmerald
        stats.gri <= 40.0 -> ColorTargetSoft
        else -> ColorHigh
    }

    val gviValStr = if (stats.gvi > 0.0) String.format(Locale.US, "%.2f", stats.gvi) else "--"
    val isGviGood = stats.gvi <= 1.2

    val pgsValStr = if (stats.pgs > 0.0) String.format(Locale.US, "%.1f", stats.pgs) else "--"
    val isPgsGood = stats.pgs <= 35.0

    val minMaxValStr = if (minVal > 0.0) {
        if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)}–${String.format(Locale.US, "%.1f", maxVal)}"
        else "${(minVal * 18.0182).toInt()}–${(maxVal * 18.0182).toInt()}"
    } else "--"
    val isMinMaxGood = maxVal <= 10.0 && minVal >= 3.9

    val glucoseUnitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль" else "mmol") else (if (isRu) "мг/дл" else "mg/dl")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: Mean, eA1c, SD, %CV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BentoMetricCompact(
                title = "Mean",
                value = meanValStr,
                unit = glucoseUnitStr,
                valueColor = meanColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Средний сахар (Mean Glucose)" else "Mean Glucose",
                        if (isRu) "Среднее арифметическое всех значений глюкозы за выбранный период. Клиническая цель: ≤7.0–7.8 ммоль/л."
                        else "Arithmetic average of all glucose readings over the selected period. Clinical target: ≤7.0–7.8 mmol/L."
                    )
                }
            )
            BentoMetricCompact(
                title = "eA1c",
                value = ea1cStr,
                unit = "%",
                valueColor = if (isEa1cGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Оценочный гликированный гемоглобин (eA1c / GMI)" else "Estimated HbA1c (GMI)",
                        if (isRu) "Расчётный показатель гликированного гемоглобина на основе среднего сахара CGM. Клиническая цель: ≤7.0%."
                        else "Estimated glycated hemoglobin calculated from mean CGM sensor glucose. Clinical target: ≤7.0%."
                    )
                }
            )
            BentoMetricCompact(
                title = "SD",
                value = sdValStr,
                unit = glucoseUnitStr,
                valueColor = if (isSdGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Стандартное отклонение (SD)" else "Standard Deviation (SD)",
                        if (isRu) "Мера разброса сахара вокруг средней линии. Чем меньше значение, тем более предсказуем профиль. Клиническая цель: ≤2.0 ммоль/л."
                        else "Measure of glucose spread around the mean. Lower values indicate greater stability. Clinical target: ≤2.0 mmol/L."
                    )
                }
            )
            BentoMetricCompact(
                title = "%CV",
                value = cvValStr,
                unit = "",
                valueColor = if (isCvGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Коэффициент вариации (%CV)" else "Coefficient of Variation (%CV)",
                        if (isRu) "Ключевой маркер стабильности диабета (%CV = SD / Mean * 100). Клинический международный консенсус: норма ≤36%."
                        else "Primary gold-standard marker of glycemic variability (%CV = SD / Mean * 100). Clinical consensus target: ≤36%."
                    )
                }
            )
        }

        // Row 2: TIR, TING, TBR, TAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BentoMetricCompact(
                title = "TIR",
                value = tirValStr,
                unit = "",
                valueColor = tirColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Время в целевом диапазоне (TIR)" else "Time in Range (TIR)",
                        if (isRu) "Процент времени в коридоре 3.9–10.0 ммоль/л. Золотой стандарт контроля диабета. Клиническая цель: ≥70%."
                        else "Percentage of time within 3.9–10.0 mmol/L. Gold standard of CGM therapy. Clinical target: ≥70%."
                    )
                }
            )
            BentoMetricCompact(
                title = "TING",
                value = tingValStr,
                unit = "",
                valueColor = if (isTingGood) PrimaryEmerald else onSurfaceVariant,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Узкий целевой диапазон (TING)" else "Time in Tight Range (TING)",
                        if (isRu) "Процент времени в узком физиологическом коридоре 3.9–7.8 ммоль/л. Клиническая цель для нормогликемии: ≥50%."
                        else "Percentage of time in tight euglycemic corridor 3.9–7.8 mmol/L. Clinical target: ≥50%."
                    )
                }
            )
            BentoMetricCompact(
                title = "TBR",
                value = tbrValStr,
                unit = "",
                valueColor = if (isTbrGood) PrimaryEmerald else ColorVeryLow,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Время ниже диапазона (TBR)" else "Time Below Range (TBR)",
                        if (isRu) "Суммарное время гипогликемии (<3.9 ммоль/л). Критически важный параметр безопасности. Клиническая цель: <4%."
                        else "Total time spent in hypoglycemia (<3.9 mmol/L). Vital safety target. Clinical recommendation: <4%."
                    )
                }
            )
            BentoMetricCompact(
                title = "TAR",
                value = tarValStr,
                unit = "",
                valueColor = if (isTarGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Время выше диапазона (TAR)" else "Time Above Range (TAR)",
                        if (isRu) "Суммарное время гипергликемии (>10.0 ммоль/л). Снижает риск долгосрочных осложнений. Клиническая цель: <25%."
                        else "Total time spent in hyperglycemia (>10.0 mmol/L). Lowers long-term vascular risk. Clinical target: <25%."
                    )
                }
            )
        }

        // Row 3: GRI, GVI, PGS, Min/Max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BentoMetricCompact(
                title = "GRI",
                value = griValStr,
                unit = "",
                valueColor = griColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Индекс риска гликемии (GRI)" else "Glycemia Risk Index (GRI)",
                        if (isRu) "Комплексный клинический индекс от 0 до 100, объединяющий риски гипогликемии и гипергликемии. Норма: ≤20 (Зона А), 21–40 (Зона B)."
                        else "Composite clinical score from 0 to 100 balancing hypo and hyper risk. Target: ≤20 (Zone A), 21–40 (Zone B)."
                    )
                }
            )
            BentoMetricCompact(
                title = "GVI",
                value = gviValStr,
                unit = "",
                valueColor = if (isGviGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Индекс вариабельности (GVI)" else "Glycemic Variability Index (GVI)",
                        if (isRu) "Отношение реальной длины кривой глюкозы к идеальной прямой линии. Идеал без скачков: 1.0–1.2."
                        else "Ratio of actual glucose curve length to an ideal straight line. Ideal target: 1.0–1.2."
                    )
                }
            )
            BentoMetricCompact(
                title = "PGS",
                value = pgsValStr,
                unit = "",
                valueColor = if (isPgsGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    onMetricClick(
                        if (isRu) "Персональный балл гликемии (PGS)" else "Personal Glycemic Score (PGS)",
                        if (isRu) "Штрафной балл за выходы за пределы нормы, вариабельность и экстремумы. Клиническая цель: ≤35 баллов."
                        else "Composite penalty score for out-of-range events, variability, and extremes. Target: ≤35."
                    )
                }
            )
            BentoMetricCompact(
                title = "Min/Max",
                value = minMaxValStr,
                unit = glucoseUnitStr,
                valueColor = if (isMinMaxGood) PrimaryEmerald else ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    val minStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)} ммоль/л" else "${(minVal * 18.0182).toInt()} мг/дл"
                    val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", maxVal)} ммоль/л" else "${(maxVal * 18.0182).toInt()} мг/дл"
                    val healthySpan = if (unit == GlucoseUnit.MMOL_L) "4.0–7.8 ммоль/л" else "72–140 мг/дл"
                    val healthyFasting = if (unit == GlucoseUnit.MMOL_L) "3.3–5.5 ммоль/л" else "60–100 мг/дл"
                    onMetricClick(
                        if (isRu) "Экстремумы сахара (Min / Max)" else "Glucose Range for Period",
                        if (isRu) "Фактический размах за период:\n" +
                                "• Минимум: $minStr\n" +
                                "• Максимум: $maxStr\n\n" +
                                "• У здоровых людей без диабета: 96% времени сахар находится в коридоре $healthySpan (натощак $healthyFasting, ночью во сне возможны кратковременные физиологические спады до 3.3–3.8 ммоль/л).\n" +
                                "• Клиническая цель при диабете: исключать падения <3.9 и сглаживать пики >10.0 ммоль/л."
                        else "Observed range for period:\n" +
                                "• Min: $minStr\n" +
                                "• Max: $maxStr\n\n" +
                                "• Healthy non-diabetic baseline: 96% of day within $healthySpan (fasting $healthyFasting).\n" +
                                "• Clinical target in diabetes: avoid dips <3.9 and flatten spikes >10.0 mmol/L."
                    )
                }
            )
        }
    }
}
