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
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.StreakBadge
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
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

        // 1. Hero Card: Current Glucose + Mode Switcher (TIR/TING) + Today's Points Count
        item {
            HeroGlucoseCard(
                latestReading = state.latestReading,
                unit = unit,
                targetMode = targetMode,
                todayPointsCount = state.recentReadings.size,
                isRu = isRu,
                onModeChange = { mode -> viewModel.setTargetMode(mode) },
                onClick = {
                    val r = state.latestReading
                    if (r != null) {
                        val rVal = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", r.valueMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                   else "${(r.valueMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        detailDialogInfo = Pair(
                            if (isRu) "Текущий уровень сахара" else "Current Glucose Level",
                            if (isRu) "Значение: $rVal\nНаправление тренда: ${r.trendArrow}\nВремя измерения: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}\nВсего точек за сегодня: ${state.recentReadings.size}"
                            else "Value: $rVal\nTrend direction: ${r.trendArrow}\nTime: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}\nTotal readings today: ${state.recentReadings.size}"
                        )
                    }
                }
            )
        }

        // 2. Goal Compensator: dynamic progress bar reflecting % achieved
        item {
            val currentScore = if (targetMode == TargetMode.TIR) state.statistics.tirPercent else state.statistics.tingPercent
            val targetGoal = if (targetMode == TargetMode.TIR) userSettings.targetRanges.tirGoalPercent else userSettings.targetRanges.tingGoalPercent

            val compMessage = if (isRu) {
                when (goal.status) {
                    CompensatorStatus.EXCEEDING -> "Отличный темп! Вы опережаете цель. Поддерживайте ≥${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)} в оставшиеся ${goal.remainingDays} дн."
                    CompensatorStatus.REACHABLE -> "Для достижения цели удерживайте ${goal.targetMode.name} ≥${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)} в оставшиеся ${goal.remainingDays} дн."
                    CompensatorStatus.UNREALISTIC -> "Цель периода труднодостижима (${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)}). Сфокусируйтесь на сегодняшнем дне."
                }
            } else {
                when (goal.status) {
                    CompensatorStatus.EXCEEDING -> "Great pace! You are ahead of target. Maintain ≥${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)} over remaining ${goal.remainingDays} days."
                    CompensatorStatus.REACHABLE -> "To reach goal, keep ${goal.targetMode.name} ≥${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)} over remaining ${goal.remainingDays} days."
                    CompensatorStatus.UNREALISTIC -> "Period goal is difficult to reach (${String.format(Locale.US, "%.0f%%", goal.neededRemainingPercent)}). Focus on today."
                }
            }

            TargetCompensatorCard(
                message = compMessage,
                currentScore = currentScore,
                targetGoal = targetGoal,
                targetMode = targetMode,
                remainingDays = goal.remainingDays,
                isRu = isRu,
                onClick = {
                    detailDialogInfo = Pair(
                        if (isRu) "Компенсатор цели" else "Goal Compensator",
                        if (isRu) "Текущий показатель: ${String.format(Locale.US, "%.1f%%", currentScore)}\nЦелевой порог: ≥$targetGoal%\nСтатус: $compMessage\nОсталось дней в окне: ${goal.remainingDays} дн."
                        else "Current score: ${String.format(Locale.US, "%.1f%%", currentScore)}\nTarget goal: ≥$targetGoal%\nStatus: $compMessage\nDays remaining in window: ${goal.remainingDays} days."
                    )
                }
            )
        }

        // 3. 2x2 Bento Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val meanValStr = if (state.statistics.meanMmol > 0.0) {
                        if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", state.statistics.meanMmol)
                        else String.format(Locale.US, "%d", (state.statistics.meanMmol * 18.0182).toInt())
                    } else "--"

                    BentoMetricSmall(
                        title = if (isRu) "Средний сахар" else "Mean Glucose",
                        value = meanValStr,
                        unit = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL"),
                        valueColor = if (state.statistics.meanMmol in 3.9..7.8) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val targetVal = if (unit == GlucoseUnit.MMOL_L) "≤7.8 ${if (isRu) "ммоль/л" else "mmol/L"}" else "≤140 ${if (isRu) "мг/дл" else "mg/dL"}"
                            detailDialogInfo = Pair(
                                if (isRu) "Средний сахар (Mean)" else "Average Glucose (Mean)",
                                if (isRu) "Среднее арифметическое значение всех измерений за 24 часа. Целевое значение для устойчивой компенсации: $targetVal."
                                else "Arithmetic average of all readings over 24 hours. Clinical target for stable management: $targetVal."
                            )
                        }
                    )

                    BentoMetricSmall(
                        title = if (isRu) "Вариабельность (%CV)" else "Variability (%CV)",
                        value = if (state.statistics.cvPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.cvPercent) else "--",
                        unit = "",
                        valueColor = if (state.statistics.cvPercent <= 36.0) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Вариабельность (%CV)" else "Glucose Variability (%CV)",
                                if (isRu) "Коэффициент вариации глюкозы (%CV = SD / Mean * 100%). Норма по международным консенсусам: ≤36.0%. Показывает стабильность сахара без резких скачков."
                                else "Coefficient of variation (%CV = SD / Mean * 100%). International consensus target: ≤36.0%. Measures glucose stability without sharp fluctuations."
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoMetricSmall(
                        title = if (isRu) "Расчётный eA1c" else "Estimated eA1c",
                        value = if (state.statistics.gmiPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent) else "--",
                        unit = "",
                        valueColor = if (state.statistics.gmiPercent <= 7.0) PrimaryEmerald else ColorHigh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            detailDialogInfo = Pair(
                                if (isRu) "Расчётный гликированный гемоглобин (eA1c)" else "Estimated Glycated Hemoglobin (eA1c)",
                                if (isRu) "Оценка гликированного гемоглобина по формуле ADAG: (Mean + 2.59) / 1.59. Международный ориентир: ≤7.0%."
                                else "Estimated glycated hemoglobin based on ADAG formula: (Mean + 2.59) / 1.59. International clinical target: ≤7.0%."
                            )
                        }
                    )

                    BentoMetricSmall(
                        title = if (targetMode == TargetMode.TING) (if (isRu) "TING (Узкий)" else "TING (Tight)") else (if (isRu) "TIR (В диапазоне)" else "TIR (In Range)"),
                        value = String.format(Locale.US, "%.1f%%", if (targetMode == TargetMode.TING) state.statistics.tingPercent else state.statistics.tirPercent),
                        unit = "",
                        valueColor = PrimaryEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val rangeTitle = if (targetMode == TargetMode.TING) {
                                if (unit == GlucoseUnit.MMOL_L) "TING (3.9–7.8 ${if (isRu) "ммоль/л" else "mmol/L"})"
                                else "TING (70–140 ${if (isRu) "мг/дл" else "mg/dL"})"
                            } else {
                                if (unit == GlucoseUnit.MMOL_L) "TIR (3.9–10.0 ${if (isRu) "ммоль/л" else "mmol/L"})"
                                else "TIR (70–180 ${if (isRu) "мг/дл" else "mg/dL"})"
                            }
                            val rangeDesc = if (targetMode == TargetMode.TING) {
                                if (isRu) "Процент времени в узком идеальном диапазоне (${if (unit == GlucoseUnit.MMOL_L) "3.9–7.8 ммоль/л" else "70–140 мг/дл"}). Цель: ≥50%."
                                else "Time in tight ideal range (${if (unit == GlucoseUnit.MMOL_L) "3.9–7.8 mmol/L" else "70–140 mg/dL"}). Target: ≥50%."
                            } else {
                                if (isRu) "Процент времени в стандартном целевом диапазоне (${if (unit == GlucoseUnit.MMOL_L) "3.9–10.0 ммоль/л" else "70–180 мг/дл"}). Цель: ≥70%."
                                else "Time in standard target range (${if (unit == GlucoseUnit.MMOL_L) "3.9–10.0 mmol/L" else "70–180 mg/dL"}). Target: ≥70%."
                            }
                            detailDialogInfo = Pair(rangeTitle, rangeDesc)
                        }
                    )
                }
            }
        }

        // 4. Additional Daily Parameters: Today's Points & Min/Max Glucose
        item {
            val minVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.minOf { it.valueMmol } else 0.0
            val maxVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.maxOf { it.valueMmol } else 0.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoMetricSmall(
                    title = if (isRu) "Точек за сегодня" else "Readings Today",
                    value = "${state.recentReadings.size}",
                    unit = if (isRu) "изм." else "pts",
                    valueColor = PrimaryEmerald,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        detailDialogInfo = Pair(
                            if (isRu) "Точек за сегодня" else "Readings Today",
                            if (isRu) "Всего получено измерений за текущие сутки: ${state.recentReadings.size} точек.\nПокрытие сенсора: ${String.format(Locale.US, "%.1f%%", state.statistics.activeTimePercent)}"
                            else "Total readings received today: ${state.recentReadings.size} readings.\nSensor coverage: ${String.format(Locale.US, "%.1f%%", state.statistics.activeTimePercent)}"
                        )
                    }
                )

                val minMaxValStr = if (minVal > 0.0) {
                    if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)} – ${String.format(Locale.US, "%.1f", maxVal)}"
                    else "${(minVal * 18.0182).toInt()} – ${(maxVal * 18.0182).toInt()}"
                } else "--"

                BentoMetricSmall(
                    title = if (isRu) "Мин / Макс за сутки" else "Daily Min / Max",
                    value = minMaxValStr,
                    unit = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL"),
                    valueColor = if (maxVal <= 10.0 && minVal >= 3.9) PrimaryEmerald else ColorHigh,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val minStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)} ${if (isRu) "ммоль/л" else "mmol/L"}" else "${(minVal * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", maxVal)} ${if (isRu) "ммоль/л" else "mmol/L"}" else "${(maxVal * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        detailDialogInfo = Pair(
                            if (isRu) "Суточный диапазон" else "Daily Glucose Range",
                            if (isRu) "Минимальный сахар за сутки: $minStr\nМаксимальный сахар за сутки: $maxStr"
                            else "Minimum glucose today: $minStr\nMaximum glucose today: $maxStr"
                        )
                    }
                )
            }
        }

        // 5. Night Stability Indicator
        item {
            val nightStability = state.statistics.nightStability
            val hasNightData = nightStability.nightReadingsCount >= 6

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    detailDialogInfo = Pair(
                        if (isRu) "Ночной профиль сна (${userSettings.nightStartHour}:00–${userSettings.nightEndHour}:00)" else "Night Sleep Profile (${userSettings.nightStartHour}:00–${userSettings.nightEndHour}:00)",
                        if (!hasNightData) {
                            if (isRu) "Недостаточно ночных данных за текущие сутки (менее 30 минут измерений в ночное окно)." else "Insufficient night data today (less than 30 minutes of readings in night window)."
                        } else {
                            val sdStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.2f", nightStability.sdMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                        else "${(nightStability.sdMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                            if (isRu) "TIR за ночь: ${String.format(Locale.US, "%.1f%%", nightStability.tirPercent)}\nВариабельность (SD): $sdStr\nКоличество ночных точек: ${nightStability.nightReadingsCount}"
                            else "Night TIR: ${String.format(Locale.US, "%.1f%%", nightStability.tirPercent)}\nVariability (SD): $sdStr\nNight readings count: ${nightStability.nightReadingsCount}"
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
                            tint = if (hasNightData && nightStability.isStable) PrimaryEmerald else ColorHigh,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isRu) "Ночной профиль (${String.format(Locale.US, "%02d:00", userSettings.nightStartHour)}–${String.format(Locale.US, "%02d:00", userSettings.nightEndHour)})"
                                       else "Night Sleep Profile (${String.format(Locale.US, "%02d:00", userSettings.nightStartHour)}–${String.format(Locale.US, "%02d:00", userSettings.nightEndHour)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    !hasNightData -> if (isRu) "Недостаточно данных (<30 мин)" else "Insufficient data (<30 min)"
                                    nightStability.isStable -> if (isRu) "Стабильный ночной профиль" else "Stable night profile"
                                    else -> if (isRu) "Обнаружены ночные колебания" else "Night fluctuations detected"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = when {
                                    !hasNightData -> onSurfaceVariant
                                    nightStability.isStable -> PrimaryEmerald
                                    else -> ColorHigh
                                }
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
    targetMode: TargetMode,
    todayPointsCount: Int,
    isRu: Boolean,
    onModeChange: (TargetMode) -> Unit,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.current_glucose),
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariant
                )

                // Mode Selector: TIR vs TING
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tirTitle = if (unit == GlucoseUnit.MMOL_L) "TIR 3.9–10.0" else "TIR 70–180"
                    val tingTitle = if (unit == GlucoseUnit.MMOL_L) "TING 3.9–7.8" else "TING 70–140"
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

            Spacer(modifier = Modifier.height(16.dp))

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
                    latestReading.valueMmol in 3.9..7.8 -> ColorTight
                    latestReading.valueMmol in 7.9..10.0 -> ColorTight
                    latestReading.valueMmol in 10.1..13.9 -> ColorHigh
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

            val pointsSubtitle = if (todayPointsCount > 0) {
                if (isRu) " • Получено сегодня: $todayPointsCount точек" else " • Received today: $todayPointsCount readings"
            } else ""

            Text(
                text = if (isRu) "Последнее измерение: $timeStr$pointsSubtitle" else "Latest reading: $timeStr$pointsSubtitle",
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
    remainingDays: Int,
    isRu: Boolean,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRu) "Компенсатор цели" else "Goal Compensator",
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface
                    )
                }

                Text(
                    text = "${String.format(Locale.US, "%.0f%%", currentScore)} / ${if (isRu) "Цель:" else "Goal:"} ≥$targetGoal%",
                    style = MaterialTheme.typography.titleSmall,
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // Proportional Progress Bar (0..100% of the bar) - Wide, Vivid & Accented
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
private fun BentoMetricSmall(
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
