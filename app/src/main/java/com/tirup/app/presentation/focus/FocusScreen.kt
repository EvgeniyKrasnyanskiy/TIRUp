package com.tirup.app.presentation.focus

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.StreakBadge
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTarget
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val userSettings = state.userSettings
    val isRu = userSettings.language.equals("RU", ignoreCase = true)
    val targetMode = userSettings.targetMode
    val unit = userSettings.unit
    val goal = state.compensatorGoal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Header with Menu Button, Title & Streak
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
                        text = stringResource(R.string.focus_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = onSurface
                    )
                }

                StreakBadge(
                    streakDays = state.streakDays,
                    onClick = {
                        detailDialogInfo = Pair(
                            if (isRu) "Серия дней в целевом диапазоне (Стрик)" else "Target Range Day Streak",
                            if (isRu) "Текущая серия: ${state.streakDays} дн.\n\nКаждый день, когда суточный TIR удерживается выше целевого уровня (≥70%), увеличивает серию. Непрерывный контроль помогает закрепить стабильные привычки и защищает сосудистую систему."
                            else "Current streak: ${state.streakDays} days.\n\nEvery day when daily TIR stays above the target level (≥70%), your streak increases. Continuous control helps build consistent habits and protects vascular health."
                        )
                    }
                )
            }
        }

        // 1. Hero Card: Current Glucose
        item {
            HeroGlucoseCard(
                latestReading = state.latestReading,
                unit = unit,
                isRu = isRu,
                onClick = {
                    val r = state.latestReading
                    if (r != null) {
                        val rVal = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", r.valueMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                   else "${(r.valueMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        detailDialogInfo = Pair(
                            if (isRu) "Текущий уровень сахара" else "Current Glucose Level",
                            if (isRu) "Значение: $rVal\nНаправление тренда: ${r.trendArrow}\nВремя измерения: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}"
                            else "Value: $rVal\nTrend direction: ${r.trendArrow}\nTime: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}"
                        )
                    }
                }
            )
        }

        // 2. Goal Compensator: Mode switcher (TIR/TING) moved here, target goals on second line
        item {
            val currentScore = if (targetMode == TargetMode.TIR) state.statistics.tirPercent else state.statistics.tingPercent
            val targetGoal = if (targetMode == TargetMode.TIR) userSettings.targetRanges.tirGoalPercent else userSettings.targetRanges.tingGoalPercent

            val compMessage = if (isRu) {
                if (goal.recommendationRu.isNotEmpty()) goal.recommendationRu
                else "Удерживайте сахар в диапазоне для достижения цели."
            } else {
                if (goal.recommendationEn.isNotEmpty()) goal.recommendationEn
                else "Maintain glucose in target range to reach goal."
            }

            TargetCompensatorCard(
                message = compMessage,
                currentScore = currentScore,
                targetGoal = targetGoal,
                targetMode = targetMode,
                unit = unit,
                isRu = isRu,
                onModeChange = { mode -> viewModel.setTargetMode(mode) },
                onClick = {
                    val inRangeStr = TargetCompensatorCalculator.formatHoursMins(goal.inRangeMinutes, isRu)
                    val targetMinsStr = TargetCompensatorCalculator.formatHoursMins(goal.targetGoalMinutes, isRu)
                    val outOfRangeStr = TargetCompensatorCalculator.formatHoursMins(goal.outOfRangeMinutes, isRu)
                    val allowedOutStr = TargetCompensatorCalculator.formatHoursMins(goal.allowedOutMinutes, isRu)
                    val remainingDayStr = TargetCompensatorCalculator.formatHoursMins(goal.remainingMinutesToday, isRu)

                    val dialogBody = if (isRu) {
                        "Текущий ${targetMode.name}: ${String.format(Locale.US, "%.1f%%", currentScore)} (Цель: ≥$targetGoal%)\n\n" +
                        "• В диапазоне за сегодня: $inRangeStr (норма ≥$targetMinsStr)\n" +
                        "• Вне диапазона за сегодня: $outOfRangeStr (допустимый лимит: $allowedOutStr)\n" +
                        "• До конца суток осталось: $remainingDayStr\n\n" +
                        "Рекомендация: $compMessage"
                    } else {
                        "Current ${targetMode.name}: ${String.format(Locale.US, "%.1f%%", currentScore)} (Target: ≥$targetGoal%)\n\n" +
                        "• In range today: $inRangeStr (target ≥$targetMinsStr)\n" +
                        "• Out of range today: $outOfRangeStr (allowed limit: $allowedOutStr)\n" +
                        "• Remaining today: $remainingDayStr\n\n" +
                        "Recommendation: $compMessage"
                    }

                    detailDialogInfo = Pair(
                        if (isRu) "Компенсация цели" else "Goal Compensation",
                        dialogBody
                    )
                }
            )
        }

        // 3. Night Stability Indicator
        item {
            val nightStability = state.statistics.nightStability
            val hasNightData = nightStability.nightReadingsCount >= 6

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    detailDialogInfo = Pair(
                        if (isRu) "Ночной профиль сна" else "Night Sleep Profile",
                        if (!hasNightData) {
                            if (isRu) "Недостаточно данных для анализа ночи." else "Insufficient data for night analysis."
                        } else {
                            val sdStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.2f", nightStability.sdMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                        else "${(nightStability.sdMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                            if (isRu) "TIR за ночь: ${String.format(Locale.US, "%.1f%%", nightStability.tirPercent)}\nВариабельность (SD): $sdStr\nТочек: ${nightStability.nightReadingsCount}"
                            else "Night TIR: ${String.format(Locale.US, "%.1f%%", nightStability.tirPercent)}\nVariability (SD): $sdStr\nReadings: ${nightStability.nightReadingsCount}"
                        }
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isRu) "Ночной профиль" else "Night Sleep Profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariant
                            )
                            Text(
                                text = when {
                                    !hasNightData -> if (isRu) "Недостаточно данных (<30 мин)" else "Insufficient data (<30 min)"
                                    nightStability.isStable -> if (isRu) "Стабильный ночной профиль" else "Stable night profile"
                                    else -> if (isRu) "Обнаружены ночные колебания" else "Night fluctuations detected"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Icon(
                        imageVector = if (hasNightData && nightStability.isStable) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (hasNightData && nightStability.isStable) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 4. Compact 2x4 Metrics Grid
        item {
            val minVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.minOf { it.valueMmol } else 0.0
            val maxVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.maxOf { it.valueMmol } else 0.0

            val meanValStr = if (state.statistics.meanMmol > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", state.statistics.meanMmol)
                else String.format(Locale.US, "%d", (state.statistics.meanMmol * 18.0182).toInt())
            } else "--"

            val meanColor = when {
                state.statistics.meanMmol <= 0.0 -> onSurfaceVariant
                state.statistics.meanMmol <= 7.0 -> PrimaryEmerald
                state.statistics.meanMmol <= 7.8 -> ColorTargetSoft
                state.statistics.meanMmol <= 10.0 -> ColorHigh
                else -> ColorVeryHigh
            }

            val sdVal = state.statistics.sdMmol
            val sdValStr = if (sdVal > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", sdVal)
                else "${(sdVal * 18.0182).toInt()}"
            } else "--"
            val isSdGood = sdVal in 0.01..(if (targetMode == TargetMode.TING) 1.5 else 2.0)

            val cvValStr = if (state.statistics.cvPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.cvPercent) else "--"
            val isCvGood = state.statistics.cvPercent in 0.01..36.0

            val ea1cStr = if (state.statistics.gmiPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent) else "--"
            val isEa1cGood = state.statistics.gmiPercent in 0.01..7.0

            val griValStr = if (state.statistics.gri > 0.0) "${state.statistics.gri.toInt()}" else "--"
            val griColor = when {
                state.statistics.gri <= 0.0 -> onSurfaceVariant
                state.statistics.gri <= 20.0 -> PrimaryEmerald
                state.statistics.gri <= 40.0 -> ColorTargetSoft
                else -> ColorHigh
            }

            val gviValStr = if (state.statistics.gvi > 0.0) String.format(Locale.US, "%.2f", state.statistics.gvi) else "--"
            val isGviGood = state.statistics.gvi <= 1.2

            val pgsValStr = if (state.statistics.pgs > 0.0) String.format(Locale.US, "%.1f", state.statistics.pgs) else "--"
            val isPgsGood = state.statistics.pgs <= 35.0

            val minMaxValStr = if (minVal > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)}–${String.format(Locale.US, "%.1f", maxVal)}"
                else "${(minVal * 18.0182).toInt()}–${(maxVal * 18.0182).toInt()}"
            } else "--"
            val isMinMaxGood = maxVal <= 10.0 && minVal >= 3.9

            val glucoseUnitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль" else "mmol") else (if (isRu) "мг/дл" else "mg/dl")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1: Mean, SD, %CV, eA1c
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
                            val targetVal = if (unit == GlucoseUnit.MMOL_L) "≤7.8 ммоль/л" else "≤140 мг/дл"
                            detailDialogInfo = Pair(
                                if (isRu) "Средний сахар (Mean)" else "Average Glucose (Mean)",
                                if (isRu) "Среднее арифметическое измерений за сутки. Клиническая цель: $targetVal."
                                else "24h average glucose. Clinical target: $targetVal."
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
                            val targetSdStr = if (unit == GlucoseUnit.MMOL_L) "≤2.0 ммоль/л" else "≤36 мг/дл"
                            detailDialogInfo = Pair(
                                if (isRu) "Разброс (SD)" else "Standard Deviation (SD)",
                                if (isRu) "Стандартное отклонение сахара за сутки. Клиническая цель: $targetSdStr."
                                else "Daily standard deviation. Clinical target: $targetSdStr."
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
                                if (isRu) "Вариабельность (%CV)" else "Glucose Variability (%CV)",
                                if (isRu) "Коэффициент вариации (%CV = SD / Mean * 100%). Норма ATTD: ≤36.0%."
                                else "Coefficient of variation. Consensus target: ≤36.0%."
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
                                if (isRu) "Расчётный A1c по формуле ADAG. Клинический ориентир: ≤7.0%."
                                else "Estimated glycated hemoglobin (ADAG). Target: ≤7.0%."
                            )
                        }
                    )
                }

                // Row 2: GRI, GVI, PGS, Min/Max
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
                                if (isRu) "Интегральная оценка риска гипо- и гипергликемий (0–100 баллов). Зона A (низкий риск): ≤20 баллов."
                                else "Composite score of hypoglycemia and hyperglycemia risk (0–100). Low risk (Zone A): ≤20."
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
                                if (isRu) "Отношение реальной длины кривой сахара к идеальной гладкой траектории. Идеал здорового человека: ≤1.20."
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
                                if (isRu) "Комплексный балл качества контроля (TIR + Mean + CV). Чем ниже балл, тем ближе гликемия к норме (цель: ≤35.0)."
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
                            detailDialogInfo = Pair(
                                if (isRu) "Суточный диапазон сахара" else "Daily Glucose Range",
                                if (isRu) "Минимум за сегодня: $minStr\nМаксимум за сегодня: $maxStr"
                                else "Min today: $minStr\nMax today: $maxStr"
                            )
                        }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
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
private fun HeroGlucoseCard(
    latestReading: GlucoseReading?,
    unit: GlucoseUnit,
    isRu: Boolean,
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.current_glucose),
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Hero Value with Trend Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val valueColor = when {
                    latestReading == null -> onSurfaceVariant
                    latestReading.valueMmol < 3.0 -> ColorVeryLow
                    latestReading.valueMmol < 3.9 -> ColorLow
                    latestReading.valueMmol in 3.9..7.0 -> ColorTight
                    latestReading.valueMmol in 7.01..7.8 -> ColorTargetSoft
                    latestReading.valueMmol in 7.81..10.0 -> ColorTarget
                    latestReading.valueMmol in 10.01..13.9 -> ColorHigh
                    else -> ColorVeryHigh
                }

                val displayVal = if (latestReading != null) {
                    if (unit == GlucoseUnit.MMOL_L) {
                        String.format(Locale.US, "%.1f", latestReading.valueMmol)
                    } else {
                        String.format(Locale.US, "%d", (latestReading.valueMmol * 18.0182).toInt())
                    }
                } else "--"

                Text(
                    text = displayVal,
                    style = MaterialTheme.typography.displayLarge,
                    color = valueColor,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant
                    )
                    if (latestReading?.trendArrow?.isNotEmpty() == true) {
                        Text(
                            text = latestReading.trendArrow,
                            style = MaterialTheme.typography.headlineMedium,
                            color = valueColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val timeStr = if (latestReading != null) {
                DateUtils.getRelativeTimeSpanString(
                    latestReading.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else if (isRu) "Нет данных трансляции" else "No broadcast data"

            Text(
                text = if (isRu) "Последнее измерение: $timeStr" else "Latest reading: $timeStr",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TargetModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF2563EB) else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TargetCompensatorCard(
    message: String,
    currentScore: Double,
    targetGoal: Int,
    targetMode: TargetMode,
    unit: GlucoseUnit,
    isRu: Boolean,
    onModeChange: (TargetMode) -> Unit,
    onClick: () -> Unit
) {
    val progress = (currentScore / 100.0).toFloat().coerceIn(0f, 1f)
    val isGoalMet = currentScore >= targetGoal
    val progressColor = if (isGoalMet) PrimaryEmerald else ColorHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Left (Title + Subtitle target), Right (TIR/TING switcher)
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
                            text = stringResource(R.string.compensator_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface
                        )
                    }

                    // Target goal text on a new row under title
                    Text(
                        text = "${String.format(Locale.US, "%.0f%%", currentScore)} / ${if (isRu) "Цель:" else "Goal:"} ≥$targetGoal%",
                        style = MaterialTheme.typography.bodySmall,
                        color = progressColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Mode Selector: TIR vs TING on the right
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tirTitle = if (unit == GlucoseUnit.MMOL_L) "TIR" else "TIR"
                    val tingTitle = if (unit == GlucoseUnit.MMOL_L) "TING" else "TING"
                    TargetModeChip(
                        title = tirTitle,
                        selected = targetMode == TargetMode.TIR,
                        onClick = { onModeChange(TargetMode.TIR) }
                    )
                    TargetModeChip(
                        title = tingTitle,
                        selected = targetMode == TargetMode.TING,
                        onClick = { onModeChange(TargetMode.TING) }
                    )
                }
            }

            // Proportional Progress Bar (0..100% of the bar)
            val trackBg = if (isGoalMet) PrimaryEmerald.copy(alpha = 0.18f) else ColorHigh.copy(alpha = 0.18f)
            val activeColor = if (isGoalMet) Color(0xFF10B981) else Color(0xFFF59E0B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(trackBg)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    color = activeColor,
                    trackColor = Color.Transparent
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BentoMetricCompact(
    title: String,
    value: String,
    unit: String = "",
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        padding = 8.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
