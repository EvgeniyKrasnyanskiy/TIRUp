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
    var isPatternsDismissed by rememberSaveable { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header
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
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // Compact Period Selector (4 Primary + More Dropdown)
        item {
            CompactPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )
        }

        // Strategic Period Compensator Card
        item {
            val goal = state.compensatorGoal
            val stats = state.statistics
            val targetGoal = state.userSettings.targetRanges.tirGoalPercent
            val currentScore = stats.tirPercent
            val isGoalMet = currentScore >= targetGoal
            val progressColor = if (isGoalMet) PrimaryEmerald else ColorHigh
            val progress = (currentScore / 100.0).toFloat().coerceIn(0f, 1f)

            val periodTitle = stringResource(selectedPeriod.stringResId)
            val compMessage = if (isRu) goal.recommendationRu else goal.recommendationEn

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val daysStr = "${goal.successfulDaysCount} из ${goal.totalDaysWithData} дн."
                    val balanceSign = if (goal.balanceHours >= 0) "+" else ""
                    val balanceStr = "${balanceSign}${String.format(Locale.US, "%.1f", goal.balanceHours)} ч"
                    val nextTirStr = "${goal.neededDailyTirNextPeriod.toInt()}%"

                    val body = if (isRu) {
                        "Период анализа: $periodTitle\n\n" +
                        "• Текущий TIR: ${String.format(Locale.US, "%.1f%%", currentScore)} (Цель: ≥$targetGoal%)\n" +
                        "• Дней выше цели: $daysStr (${String.format(Locale.US, "%.0f%%", (goal.successfulDaysCount.toDouble() / goal.totalDaysWithData.coerceAtLeast(1).toDouble()) * 100.0)})\n" +
                        "• Баланс времени в норме: $balanceStr\n" +
                        "• Ориентир на следующий период: TIR ≥$nextTirStr в сутки\n\n" +
                        "Рекомендация:\n$compMessage"
                    } else {
                        "Analysis period: $periodTitle\n\n" +
                        "• Current TIR: ${String.format(Locale.US, "%.1f%%", currentScore)} (Target: ≥$targetGoal%)\n" +
                        "• Days above target: $daysStr\n" +
                        "• Time balance in range: $balanceStr\n" +
                        "• Next period target: TIR ≥$nextTirStr/day\n\n" +
                        "Recommendation:\n$compMessage"
                    }

                    detailDialogInfo = Pair(
                        if (isRu) "Компенсация за период" else "Period Goal Compensation",
                        body
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = progressColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRu) "Компенсация за период" else "Period Compensation",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                            }

                            Text(
                                text = "${String.format(Locale.US, "%.0f%%", currentScore)} TIR / ${if (isRu) "Цель:" else "Goal:"} ≥$targetGoal%",
                                style = MaterialTheme.typography.bodySmall,
                                color = progressColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (goal.totalDaysWithData > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = progressColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (isRu) "${goal.successfulDaysCount} из ${goal.totalDaysWithData} дн."
                                           else "${goal.successfulDaysCount}/${goal.totalDaysWithData} days",
                                    color = progressColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Progress Bar
                    val trackBg = if (isGoalMet) PrimaryEmerald.copy(alpha = 0.18f) else ColorHigh.copy(alpha = 0.18f)
                    val activeColor = if (isGoalMet) Color(0xFF10B981) else Color(0xFFF59E0B)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(trackBg)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp)),
                            color = activeColor,
                            trackColor = Color.Transparent
                        )
                    }

                    if (compMessage.isNotEmpty()) {
                        Text(
                            text = compMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                            lineHeight = 16.sp
                        )
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

        // 3. Compact 3x4 Metrics Grid (12 Core Clinical Parameters for Selected Period)
        item {
            val stats = state.statistics
            val unit = state.userSettings.unit
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
                            val targetVal = if (unit == GlucoseUnit.MMOL_L) "≤7.0–7.8 ммоль/л" else "≤126–140 мг/дл"
                            val healthyMean = if (unit == GlucoseUnit.MMOL_L) "4.5–5.8 ммоль/л" else "80–105 мг/дл"
                            val healthyFasting = if (unit == GlucoseUnit.MMOL_L) "3.3–5.5 ммоль/л" else "60–100 мг/дл"
                            detailDialogInfo = Pair(
                                if (isRu) "Средний сахар (Mean Glucose)" else "Mean Glucose for Period",
                                if (isRu) "Среднее арифметическое всех измерений за выбранный период.\n\n" +
                                        "• Клиническая цель при диабете: $targetVal.\n" +
                                        "• У здоровых людей без диабета: в среднем $healthyMean (натощак $healthyFasting).\n\n" +
                                        "💡 Факт о нормогликемии: исследования CGM у здоровых людей показывают, что после обильной углеводной еды сахар может кратковременно подскакивать до 8.5–10.0 ммоль/л, но возвращается к норме за 20–30 минут."
                                else "Average glucose over the selected period.\n\n" +
                                        "• Clinical target in diabetes: $targetVal.\n" +
                                        "• Healthy non-diabetic baseline: average $healthyMean (fasting $healthyFasting).\n\n" +
                                        "💡 CGM fact: healthy individuals can briefly touch 8.5–10.0 mmol/L after high-carb meals, but return to baseline within 20–30 minutes."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "eA1c",
                        value = ea1cStr,
                        unit = if (isRu) "гликир." else "est.",
                        valueColor = if (isEa1cGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Расчётный гликированный гемоглобин (eA1c)" else "Estimated Glycated Hemoglobin (eA1c)",
                                if (isRu) "Расчётный HbA1c по формуле ADAG за выбранный период: $ea1cStr (${stats.hba1cMmolMol} mmol/mol).\n\n" +
                                        "• У людей без диабета: 4.0–5.6% (20–38 ммоль/моль).\n" +
                                        "• Зона предиабета: 5.7–6.4%.\n" +
                                        "• Клиническая цель при диабете: ≤6.5–7.0% (≤48–53 ммоль/моль)."
                                else "Estimated glycated hemoglobin (ADAG): $ea1cStr (${stats.hba1cMmolMol} mmol/mol).\n\n" +
                                        "• Healthy non-diabetic baseline: 4.0–5.6% (20–38 mmol/mol).\n" +
                                        "• Prediabetes range: 5.7–6.4%.\n" +
                                        "• Clinical target in diabetes: ≤6.5–7.0% (≤48–53 mmol/mol)."
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
                            val targetSdStr = if (unit == GlucoseUnit.MMOL_L) "≤2.0–2.5 ммоль/л" else "≤36–45 мг/дл"
                            val healthySdStr = if (unit == GlucoseUnit.MMOL_L) "≤1.0–1.2 ммоль/л" else "≤18–22 мг/дл"
                            detailDialogInfo = Pair(
                                if (isRu) "Разброс сахара (SD)" else "Standard Deviation (SD)",
                                if (isRu) "Стандартное отклонение за период. Отражает амплитуду колебаний вокруг среднего значения.\n\n" +
                                        "• Клиническая цель при диабете: $targetSdStr.\n" +
                                        "• У здоровых людей без диабета: $healthySdStr (высокая плавность суточной кривой)."
                                else "Standard deviation reflects the amplitude of glucose swings.\n\n" +
                                        "• Clinical target in diabetes: $targetSdStr.\n" +
                                        "• Healthy non-diabetic baseline: $healthySdStr."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "%CV",
                        value = cvValStr,
                        unit = if (isRu) "вариац." else "var.",
                        valueColor = if (isCvGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Вариабельность глюкозы (%CV)" else "Glucose Variability (%CV)",
                                if (isRu) "Коэффициент вариации (%CV = SD / Mean * 100%). Главный международный маркер стабильности диабета.\n\n" +
                                        "• Консенсус ATTD/ADA: норма ≤36.0% (стабильный диабет). Выше 36% — лабильный диабет с частыми перепадами.\n" +
                                        "• У здоровых людей без диабета: 12–20%."
                                else "Coefficient of variation. Consensus target: ≤36.0%.\n\n" +
                                        "• Healthy non-diabetic baseline: 12–20%."
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
                        unit = if (isRu) "норма" else "target",
                        valueColor = tirColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Время в целевом диапазоне (TIR)" else "Time in Range (TIR)",
                                if (isRu) "Диапазон 3.9–10.0 ммоль/л (70–180 мг/дл).\n\n" +
                                        "• Международный клинический консенсус ADA/EASD/ATTD: норма ≥70% времени (не менее 16 ч 48 мин в сутки).\n" +
                                        "• У здоровых людей без диабета: 99–100% времени суток."
                                else "Target range 3.9–10.0 mmol/L (70–180 mg/dL).\n\n" +
                                        "• Consensus clinical target: ≥70% (>16h 48m per day).\n" +
                                        "• Healthy non-diabetic baseline: 99–100% of 24h."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "TING",
                        value = tingValStr,
                        unit = if (isRu) "узкий" else "tight",
                        valueColor = if (isTingGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Узкий целевой диапазон (TING)" else "Time in Tight Range (TING)",
                                if (isRu) "Диапазон строгой нормогликемии 3.9–7.8 ммоль/л (70–140 мг/дл).\n\n" +
                                        "• У здоровых людей без диабета: 95–99% времени суток.\n" +
                                        "• Клинический ориентир при диабете: ≥50% (рекомендуется для систем замкнутой петли AID).\n\n" +
                                        "💡 Примечание: даже у здоровых людей после плотного приёма простых углеводов сахар может кратковременно выходить до 8.5–9.5 ммоль/л, но быстро снижается."
                                else "Tight range 3.9–7.8 mmol/L (70–140 mg/dL).\n\n" +
                                        "• Healthy non-diabetic baseline: 95–99% of 24h.\n" +
                                        "• Clinical target in diabetes: ≥50% (for AID users)."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "TBR",
                        value = tbrValStr,
                        unit = if (isRu) "гипо" else "hypo",
                        valueColor = if (isTbrGood) PrimaryEmerald else ColorVeryHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Время в гипогликемии (TBR)" else "Time Below Range (TBR)",
                                if (isRu) "Уровень глюкозы ниже 3.9 ммоль/л (<70 мг/дл).\n\nСтрогая норма безопасности: <4% времени."
                                else "Glucose < 3.9 mmol/L (<70 mg/dL). Safety target: <4%."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "TAR",
                        value = tarValStr,
                        unit = if (isRu) "гипер" else "hyper",
                        valueColor = if (isTarGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Время в гипергликемии (TAR)" else "Time Above Range (TAR)",
                                if (isRu) "Уровень глюкозы выше 10.0 ммоль/л (>180 мг/дл).\n\nКлинический ориентир консенсуса ATTD: <25% времени."
                                else "Glucose > 10.0 mmol/L (>180 mg/dL). Clinical target: <25%."
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
                        unit = if (isRu) "риск" else "risk",
                        valueColor = griColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Индекс гликемического риска (GRI)" else "Glycemia Risk Index (GRI)",
                                if (isRu) "Интегральная оценка риска гипо- и гипергликемий (${stats.griLabel}). Зона A (низкий риск): ≤20 баллов."
                                else "Composite score of hypoglycemia and hyperglycemia risk. Low risk: ≤20."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "GVI",
                        value = gviValStr,
                        unit = if (isRu) "лабильн." else "lability",
                        valueColor = if (isGviGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Индекс лабильности (GVI)" else "Glycemic Variability Index (GVI)",
                                if (isRu) "Отношение реальной длины кривой сахара к идеальной траектории. Идеал: ≤1.20."
                                else "Curve trajectory length ratio. Healthy baseline: ≤1.20."
                            )
                        }
                    )

                    BentoMetricCompact(
                        title = "PGS",
                        value = pgsValStr,
                        unit = if (isRu) "статус" else "status",
                        valueColor = if (isPgsGood) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Гликемический статус (PGS)" else "Patient Glycemic Status (PGS)",
                                if (isRu) "Комплексный балл качества контроля (TIR + Mean + CV). Цель: ≤35.0."
                                else "Comprehensive management score (TIR + Mean + CV). Target: ≤35.0."
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
                            detailDialogInfo = Pair(
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

        // 4. Detected Clinical Patterns (Shown only when sufficient data and not dismissed)
        val hasSufficientData = state.statistics.daysCount >= 5 || state.statistics.totalCount >= 500
        if (!isPatternsDismissed && hasSufficientData && detectedPatterns.isNotEmpty()) {
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

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${detectedPatterns.size} ${if (isRu) "событ." else "events"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { isPatternsDismissed = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hide",
                                            tint = onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detectedPatterns.forEach { pattern ->
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
