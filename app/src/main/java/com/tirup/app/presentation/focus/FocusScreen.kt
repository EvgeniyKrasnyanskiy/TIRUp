package com.tirup.app.presentation.focus

import android.text.format.DateUtils
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
import com.tirup.app.presentation.components.RangeDistributionBar
import com.tirup.app.presentation.components.StreakBadge
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
import java.util.Locale

@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

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
                            tint = TextPrimaryDark
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.focus_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                }

                StreakBadge(streakDays = state.streakDays)
            }
        }

        // Hero Card: Current Glucose
        item {
            CurrentGlucoseHeroCard(
                latest = state.latestReading,
                unit = state.userSettings.unit,
                targetMode = state.userSettings.targetMode,
                onToggleTargetMode = { viewModel.toggleTargetMode() },
                onCardClick = {
                    detailDialogInfo = Pair(
                        "Текущий сахар",
                        "Показывает последнее значение, полученное по локальной трансляции из xDrip+/Juggluco. Стрелка указывает скорость и направление изменения гликемии."
                    )
                }
            )
        }

        // Target Compensator Bento Card
        item {
            TargetCompensatorCard(
                state = state,
                onCardClick = {
                    detailDialogInfo = Pair(
                        "Компенсатор цели",
                        "Рассчитывает необходимый % времени в целевом диапазоне на оставшиеся дни, чтобы достичь и закрепить желаемый клинический результат."
                    )
                }
            )
        }

        // 2x2 Bento Metrics Grid
        item {
            BentoMetricsGrid(
                state = state,
                onMetricClick = { title, desc ->
                    detailDialogInfo = Pair(title, desc)
                }
            )
        }

        // Night Stability Insight Card (00:00 - 06:00)
        item {
            NightStabilityCard(
                state = state,
                onCardClick = {
                    detailDialogInfo = Pair(
                        "Ночной профиль",
                        "Анализирует стабильность сахара в период с 00:00 до 06:00. Отсутствие ночных гипо- и гипергликемий является ключевым показателем правильности базального инсулина."
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Interactive Clinical Info Dialog
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
private fun CurrentGlucoseHeroCard(
    latest: GlucoseReading?,
    unit: GlucoseUnit,
    targetMode: TargetMode,
    onToggleTargetMode: () -> Unit,
    onCardClick: () -> Unit
) {
    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        cornerRadius = 28.dp,
        backgroundColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.current_glucose),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondaryDark
                )

                // Mode toggle (TIR vs TING)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PrimaryEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                    onClick = onToggleTargetMode
                ) {
                    Text(
                        text = if (targetMode == TargetMode.TIR) stringResource(R.string.mode_tir) else stringResource(R.string.mode_ting),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (latest != null) {
                val displayVal = if (unit == GlucoseUnit.MMOL_L) {
                    String.format(Locale.US, "%.1f", latest.valueMmol)
                } else {
                    String.format(Locale.US, "%d", (latest.valueMmol * 18.0182).toInt())
                }
                val unitLabel = if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"

                // Category color for hero number
                val valColor = when {
                    latest.valueMmol < 3.0 -> ColorVeryLow
                    latest.valueMmol < 3.9 -> ColorLow
                    latest.valueMmol <= 7.8 -> ColorTight
                    latest.valueMmol <= 10.0 -> PrimaryEmerald
                    latest.valueMmol <= 13.9 -> ColorHigh
                    else -> ColorVeryHigh
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayVal,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        color = valColor,
                        lineHeight = 54.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = unitLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMutedDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (!latest.trendArrow.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = latest.trendArrow,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = valColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    latest.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )

                Text(
                    text = stringResource(R.string.last_reading, relativeTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedDark
                )
            } else {
                Text(
                    text = stringResource(R.string.no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TargetCompensatorCard(
    state: FocusUiState,
    onCardClick: () -> Unit
) {
    val goal = state.compensatorGoal
    val isTir = goal.targetMode == TargetMode.TIR
    val modeName = if (isTir) stringResource(R.string.mode_tir) else stringResource(R.string.mode_ting)

    val currentScore = if (isTir) state.statistics.tirPercent else state.statistics.tingPercent
    val progress = (currentScore / goal.targetGoalPercent.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        cornerRadius = 24.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.compensator_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )
                }

                Text(
                    text = stringResource(R.string.target_label, goal.targetGoalPercent.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = PrimaryEmerald,
                trackColor = DarkBorder
            )

            Spacer(modifier = Modifier.height(12.dp))

            val guidanceText = when (goal.status) {
                CompensatorStatus.EXCEEDING -> stringResource(
                    R.string.compensator_desc_exceeding,
                    goal.neededRemainingPercent.coerceIn(0.0, 100.0),
                    goal.remainingDays
                )
                CompensatorStatus.UNREALISTIC -> stringResource(
                    R.string.compensator_desc_impossible
                )
                CompensatorStatus.REACHABLE -> stringResource(
                    R.string.compensator_desc_reach,
                    modeName,
                    goal.targetGoalPercent.toInt(),
                    goal.neededRemainingPercent.coerceIn(0.0, 100.0),
                    goal.remainingDays
                )
            }

            Text(
                text = guidanceText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun BentoMetricsGrid(
    state: FocusUiState,
    onMetricClick: (String, String) -> Unit
) {
    val stats = state.statistics
    val isMmol = state.userSettings.unit == GlucoseUnit.MMOL_L

    // Color logic
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

    val isTirMode = state.userSettings.targetMode == TargetMode.TIR
    val tirScore = if (isTirMode) stats.tirPercent else stats.tingPercent
    val tirGoal = if (isTirMode) state.userSettings.targetRanges.tirGoalPercent else state.userSettings.targetRanges.tingGoalPercent
    val tirColor = when {
        stats.totalCount == 0 -> TextPrimaryDark
        tirScore >= tirGoal -> PrimaryEmerald
        tirScore >= 50.0 -> ColorHigh
        else -> ColorVeryHigh
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Mean & %CV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mean
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onMetricClick(
                            "Средний сахар (Mean)",
                            "Среднее значение уровня сахара за рассматриваемый период. Клиническая цель: ≤7.0 ммоль/л (≤126 мг/дл)."
                        )
                    }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_mean),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isMmol) String.format(Locale.US, "%.1f mmol/L", stats.meanMmol) else String.format(Locale.US, "%d mg/dL", (stats.meanMmol * 18.0182).toInt()),
                        style = MaterialTheme.typography.titleLarge,
                        color = meanColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // %CV
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onMetricClick(
                            "Вариабельность (%CV)",
                            "Коэффициент вариабельности глюкозы. Клинический консенсус рекомендует держать %CV ≤ 36.0% для снижения риска гипогликемий."
                        )
                    }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_cv),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.cvPercent),
                        style = MaterialTheme.typography.titleLarge,
                        color = cvColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Row 2: GMI & TIR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // GMI
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onMetricClick(
                            "Расчётный HbA1c (GMI)",
                            "Glucose Management Indicator — расчётный гликированный гемоглобин по формуле консенсуса (3.31 + 0.431 * Mean). Цель: ≤6.5-7.0%."
                        )
                    }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_gmi),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                        style = MaterialTheme.typography.titleLarge,
                        color = gmiColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // TIR
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onMetricClick(
                            "Время в диапазоне",
                            "Процент времени нахождения сахара в целевом диапазоне (3.9–10.0 ммоль/л для TIR, 3.9–7.8 ммоль/л для TING). Цель: ≥70%."
                        )
                    }
            ) {
                Column {
                    Text(
                        text = if (isTirMode) stringResource(R.string.mode_tir) else stringResource(R.string.mode_ting),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", tirScore),
                        style = MaterialTheme.typography.titleLarge,
                        color = tirColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Range Distribution Bar
        BentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onMetricClick(
                        "Распределение по диапазонам",
                        "Красный: Очень низкий (<3.0) и Очень высокий (≥14.0)\nЖёлтый: Низкий (3.0-3.8) и Высокий (10.1-13.9)\nЗелёный: Целевой диапазон (3.9-10.0)"
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tir_breakdown),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedDark
                )
                RangeDistributionBar(
                    tbrVeryLow = stats.tbrVeryLowPercent,
                    tbrLow = stats.tbrLowPercent,
                    tir = stats.tirPercent,
                    tarHigh = stats.tarHighPercent,
                    tarVeryHigh = stats.tarVeryHighPercent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NightStabilityCard(
    state: FocusUiState,
    onCardClick: () -> Unit
) {
    val night = state.statistics.nightStability

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = if (night.isStable) PrimaryEmerald else ColorHigh,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.card_night),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (night.isStable) stringResource(R.string.night_stable) else stringResource(R.string.night_unstable),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (night.isStable) PrimaryEmerald else ColorHigh
                    )
                }
            }

            Icon(
                imageVector = if (night.isStable) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (night.isStable) PrimaryEmerald else ColorHigh,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
