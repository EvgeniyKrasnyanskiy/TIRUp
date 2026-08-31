package com.tirup.app.presentation.focus

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.tirup.app.presentation.components.GlucoseValueFormatted
import com.tirup.app.presentation.components.RangeCategoryColor
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
    onNavigateToReports: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Top Header with Title & Streak
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.focus_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryEmerald
                        )
                    }
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
                onToggleTargetMode = { viewModel.toggleTargetMode() }
            )
        }

        // Target Compensator Bento Card
        item {
            TargetCompensatorCard(state = state)
        }

        // 2x2 Bento Metrics Grid
        item {
            BentoMetricsGrid(state = state)
        }

        // Night Stability Insight Card (00:00 - 06:00)
        item {
            NightStabilityCard(state = state)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun CurrentGlucoseHeroCard(
    latest: GlucoseReading?,
    unit: GlucoseUnit,
    targetMode: TargetMode,
    onToggleTargetMode: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlucoseValueFormatted(
                        valueMmol = latest.valueMmol,
                        unit = unit,
                        fontSize = 54
                    )
                    if (!latest.trendArrow.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = latest.trendArrow,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryEmerald
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
private fun TargetCompensatorCard(state: FocusUiState) {
    val goal = state.compensatorGoal
    val isTir = goal.targetMode == TargetMode.TIR
    val modeName = if (isTir) stringResource(R.string.mode_tir) else stringResource(R.string.mode_ting)

    val currentScore = if (isTir) state.statistics.tirPercent else state.statistics.tingPercent
    val progress = (currentScore / goal.targetGoalPercent.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
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
                        imageVector = Icons.Default.TrendingUp,
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

            // Progress bar
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

            // Compensator guidance message
            val guidanceText = when (goal.status) {
                CompensatorStatus.EXCEEDING -> stringResource(
                    R.string.compensator_desc_exceeding,
                    goal.neededRemainingPercent
                )
                CompensatorStatus.UNREALISTIC -> stringResource(
                    R.string.compensator_desc_impossible,
                    goal.neededRemainingPercent
                )
                CompensatorStatus.REACHABLE -> stringResource(
                    R.string.compensator_desc_reach,
                    modeName,
                    goal.targetGoalPercent.toInt(),
                    goal.neededRemainingPercent,
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
private fun BentoMetricsGrid(state: FocusUiState) {
    val stats = state.statistics
    val unit = state.userSettings.unit

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Mean Glucose
            BentoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 24.dp
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_mean),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val displayMean = if (unit == GlucoseUnit.MMOL_L) {
                        String.format(Locale.US, "%.1f", stats.meanMmol)
                    } else {
                        String.format(Locale.US, "%d", (stats.meanMmol * 18.0182).toInt())
                    }
                    Text(
                        text = "$displayMean ${unit.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }
            }

            // Card 2: Variability (%CV)
            BentoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 24.dp
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_cv),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val isCvGood = stats.cvPercent <= 36.0 && stats.cvPercent > 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", stats.cvPercent),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCvGood) PrimaryEmerald else ColorLow
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 3: Est. A1c (GMI)
            BentoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 24.dp
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.card_gmi),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }
            }

            // Card 4: 24h TIR %
            BentoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 24.dp
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.mode_tir),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", stats.tirPercent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald
                    )
                }
            }
        }

        // Breakdown distribution bar
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column {
                Text(
                    text = stringResource(R.string.tir_breakdown),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(10.dp))
                RangeDistributionBar(
                    tbrVeryLow = stats.tbrVeryLowPercent,
                    tbrLow = stats.tbrLowPercent,
                    tir = stats.tirPercent,
                    tarHigh = stats.tarHighPercent,
                    tarVeryHigh = stats.tarVeryHighPercent
                )
            }
        }
    }
}

@Composable
private fun NightStabilityCard(state: FocusUiState) {
    val night = state.statistics.nightStability

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.card_night),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (night.isStable) stringResource(R.string.night_stable) else stringResource(R.string.night_unstable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (night.isStable) PrimaryEmerald else ColorLow
                    )
                }
            }

            if (night.nightReadingsCount > 0) {
                Text(
                    text = String.format(Locale.US, "%.1f%% TIR", night.tirPercent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                )
            }
        }
    }
}
