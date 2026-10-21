package com.tirup.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StreakMotivatorDialog(
    streakDays: Int,
    isRu: Boolean,
    bestStreakDays: Int = 0,
    dailySummaries: List<com.tirup.app.domain.model.DailySummary> = emptyList(),
    todayTirPercent: Double = 0.0,
    todayTingPercent: Double = 0.0,
    todayStats: com.tirup.app.domain.model.GlucoseStatistics? = null,
    unit: com.tirup.app.domain.model.GlucoseUnit = com.tirup.app.domain.model.GlucoseUnit.MMOL_L,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flamePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )

    val daysWord = com.tirup.app.domain.util.PluralUtils.formatDays(streakDays, isRu)

    var showFlameEasterEgg by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Animated Glowing Flame Badge
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable { showFlameEasterEgg = true }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF7A00).copy(alpha = 0.35f),
                                    Color(0xFFFFB800).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFF7A00), Color(0xFFFFB800), PrimaryEmerald)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 38.sp
                    )
                }

                // Title and Streak Counter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isRu) "Дни в целевом диапазоне!" else "Target Range Streak!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF7A00).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF7A00).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isRu) "🔥 $daysWord подряд" else "🔥 $daysWord streak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF7A00),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        if (bestStreakDays > 0) {
                            val bestWord = com.tirup.app.domain.util.PluralUtils.formatDays(bestStreakDays, isRu)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFD700).copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = if (isRu) "🏆 Рекорд: $bestWord" else "🏆 Best: $bestWord",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // GitHub-Style 28-Day TIR Contribution Grid
                GitHubTirContributionGrid(
                    dailySummaries = dailySummaries,
                    todayTirPercent = todayTirPercent,
                    todayTingPercent = todayTingPercent,
                    todayStats = todayStats,
                    unit = unit,
                    isRu = isRu
                )

                // Clinical Motivation Body Text
                Text(
                    text = if (streakDays > 0) {
                        if (isRu) "Отличная дисциплина! Каждый день с TIR ≥70% защищает сосуды, зрение и почки от осложнений и сглаживает скачки сахара."
                        else "Outstanding discipline! Every day spent with TIR ≥70% strongly protects your vascular system, eyes, and kidneys from microvascular stress while reducing glucose swings."
                    } else {
                        if (isRu) "Начните новую серию прямо сегодня! Удерживайте сахар в целевом диапазоне (TIR ≥70%), чтобы начать новую серию стабильной компенсации."
                        else "Start a new streak today! Keep your glucose in target range (TIR ≥70%) to activate your streak."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // Bottom Affirmation Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (streakDays > 0) (if (isRu) "Так держать! 💪" else "Keep it up! 💪")
                               else (if (isRu) "Вперёд к цели! 🎯" else "Let's do it! 🎯"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showFlameEasterEgg) {
        AlertDialog(
            onDismissRequest = { showFlameEasterEgg = false },
            title = {
                Text(
                    text = if (isRu) "Огонёк диабетика! 🔥" else "Diabetic Power Flame! 🔥",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✨ 🌟 🎆 🏆 🎆 🌟 ✨", fontSize = 22.sp)
                    val recordGreeting = if (bestStreakDays > 0) {
                        val bestWord = com.tirup.app.domain.util.PluralUtils.formatDays(bestStreakDays, isRu)
                        if (isRu) "\n\n🏆 Твой рекорд за всё время: $bestWord в целевом диапазоне!"
                        else "\n\n🏆 Your all-time record: $bestWord in target range!"
                    } else ""

                    Text(
                        text = (if (isRu)
                            "Секретная искра разблокирована!\n\nКаждый час и день в целевом диапазоне бережёт твои сосуды, нервы и дарит отличную форму. Управлять диабетом — это ежедневный подвиг.\n\nПродолжай жечь и заряжать энергией! 🔥💪"
                        else
                            "Secret spark unlocked!\n\nEvery hour and day in target range protects your blood vessels, nerves, and brings peak energy. Managing diabetes is an everyday triumph.\n\nKeep the fire burning! 🔥💪") + recordGreeting,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFlameEasterEgg = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A00))
                ) {
                    Text(
                        if (streakDays > 0) (if (isRu) "Так держать! 💪" else "Keep it up! 💪")
                        else (if (isRu) "К новым рекордам! 🚀" else "To new records! 🚀")
                    )
                }
            }
        )
    }
}

@Composable
private fun GitHubTirContributionGrid(
    dailySummaries: List<com.tirup.app.domain.model.DailySummary>,
    todayTirPercent: Double,
    todayTingPercent: Double = 0.0,
    todayStats: com.tirup.app.domain.model.GlucoseStatistics? = null,
    unit: com.tirup.app.domain.model.GlucoseUnit = com.tirup.app.domain.model.GlucoseUnit.MMOL_L,
    isRu: Boolean
) {
    var selectedDetail by remember { mutableStateOf<String?>(null) }
    var selectedDayTimestamp by remember { mutableStateOf<Long?>(null) }
    var selectedDayForModal by remember { mutableStateOf<DayCellData?>(null) }
    var isTingMode by remember { mutableStateOf(false) }

    // 28 days aligned to Monday..Sunday ending this current week
    val daysData = remember(dailySummaries, todayTirPercent, todayTingPercent, isTingMode) {
        val list = mutableListOf<DayCellData>()
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        val currentDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7 // 0=Mon..6=Sun
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        // Monday of current week
        val currentWeekMonStart = todayStart - currentDayOfWeek * 86400000L
        // Grid starts on Monday 3 weeks prior (28 days total: 4 full weeks)
        val gridStartMon = currentWeekMonStart - 3 * 7 * 86400000L

        val dateFormat = SimpleDateFormat("d MMM", if (isRu) Locale("ru") else Locale.US)

        // Mathematical guaranteed check for today
        val millisPassedToday = (now - todayStart).coerceIn(0L, 86400000L)
        val hoursPassed = millisPassedToday / 3600000.0
        val hoursRemaining = 24.0 - hoursPassed
        val currentTargetGoal = if (isTingMode) 50.0 else 70.0
        val effectiveTodayVal = (if (isTingMode) todayTingPercent else todayTirPercent).coerceIn(0.0, 100.0)
        val inRangeHoursSoFar = hoursPassed * (effectiveTodayVal / 100.0)
        val minPossibleFinalVal = (inRangeHoursSoFar / 24.0) * 100.0
        val maxPossibleFinalVal = ((inRangeHoursSoFar + hoursRemaining) / 24.0) * 100.0

        val isGuaranteedWin = hoursPassed >= 4.0 && minPossibleFinalVal >= currentTargetGoal
        val isGuaranteedLoss = maxPossibleFinalVal < currentTargetGoal

        for (i in 0 until 28) {
            val dayStart = gridStartMon + i * 86400000L
            val isToday = abs(dayStart - todayStart) < 3600000L
            val isFuture = dayStart > todayStart + 3600000L

            val matchingSummary = dailySummaries.find { abs(it.dateTimestamp - dayStart) < 43200000L }

            val value: Double? = when {
                isFuture -> null
                isToday -> if (effectiveTodayVal > 0.0) effectiveTodayVal else (if (isTingMode) matchingSummary?.tingPercent else matchingSummary?.tirPercent)
                else -> matchingSummary?.takeIf { it.readingsCount >= 10 }?.let { if (isTingMode) it.tingPercent else it.tirPercent }
            }

            val dateLabel = dateFormat.format(Date(dayStart))
            list.add(
                DayCellData(
                    timestamp = dayStart,
                    dateLabel = dateLabel,
                    tir = value,
                    isToday = isToday,
                    isFuture = isFuture,
                    isGuaranteedWin = isToday && isGuaranteedWin,
                    isGuaranteedLoss = isToday && isGuaranteedLoss,
                    summary = matchingSummary
                )
            )
        }
        list
    }

    val dayHeaders = if (isRu) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                     else listOf("M", "T", "W", "T", "F", "S", "S")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTingMode) {
                        if (isRu) "Активность TING (3.9–7.8)" else "28-Day TING Activity"
                    } else {
                        if (isRu) "Активность TIR (3.9–10.0)" else "28-Day TIR Activity"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Mode Toggle Switch (TIR / TING)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isTingMode) ActionBlue else Color.Transparent)
                                .clickable {
                                    isTingMode = false
                                    selectedDetail = null
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "TIR",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!isTingMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTingMode) ActionBlue else Color.Transparent)
                                .clickable {
                                    isTingMode = true
                                    selectedDetail = null
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "TING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTingMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Grid of 4 rows x 7 days
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row: Mon..Sun
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dayHeaders.forEach { header ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // 4 weeks
                for (week in 0 until 4) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (dayInWeek in 0 until 7) {
                            val index = week * 7 + dayInWeek
                            val cell = daysData.getOrNull(index)
                            if (cell != null) {
                                val isSelected = selectedDayTimestamp == cell.timestamp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .then(
                                            when {
                                                cell.isFuture -> Modifier
                                                    .background(Color(0xFF1E293B).copy(alpha = 0.35f))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 0.8.dp,
                                                        color = if (isSelected) Color(0xFFFF7A00) else Color(0xFF334155).copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(5.dp)
                                                    )
                                                cell.isToday -> Modifier
                                                    .background(Color(0xFF1E293B))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.5.dp,
                                                        color = if (isSelected) Color(0xFFFF7A00) else Color.White,
                                                        shape = RoundedCornerShape(5.dp)
                                                    )
                                                else -> {
                                                    val lowBound = if (isTingMode) 45.0 else 67.0
                                                    val targetBound = if (isTingMode) 50.0 else 70.0
                                                    val highBound = if (isTingMode) 65.0 else 85.0
                                                    val solidColor = when {
                                                        cell.tir == null -> Color(0xFF263238)
                                                        cell.tir < lowBound -> Color(0xFFEF4444)
                                                        cell.tir < targetBound -> Color(0xFFF59E0B)
                                                        cell.tir < highBound -> Color(0xFF10B981)
                                                        else -> Color(0xFF34D399)
                                                    }
                                                    Modifier
                                                        .background(solidColor)
                                                        .then(
                                                            if (isSelected) Modifier.border(2.5.dp, Color(0xFFFF7A00), RoundedCornerShape(5.dp))
                                                            else Modifier
                                                        )
                                                }
                                            }
                                        )
                                        .clickable {
                                            selectedDayTimestamp = cell.timestamp
                                            val modeLabel = if (isTingMode) "TING" else "TIR"
                                            val lowBound = if (isTingMode) 45.0 else 67.0
                                            val targetBound = if (isTingMode) 50.0 else 70.0
                                            val highBound = if (isTingMode) 65.0 else 85.0

                                            if (cell.isFuture) {
                                                selectedDetail = "${cell.dateLabel}: " + if (isRu) "впереди" else "upcoming"
                                            } else {
                                                if (cell.isToday) {
                                                    val tirVal = cell.tir?.toInt() ?: 0
                                                    val winLossStatus = when {
                                                        cell.isGuaranteedWin -> if (isRu) " (Цель выполнена! ✓)" else " (Target reached! ✓)"
                                                        cell.isGuaranteedLoss && (cell.tir ?: 0.0) < lowBound -> if (isRu) " (Ниже цели)" else " (Below target)"
                                                        cell.isGuaranteedLoss -> if (isRu) " (Почти у цели ⚠️)" else " (Near target ⚠️)"
                                                        (cell.tir ?: 0.0) >= highBound -> if (isRu) " (В процессе, отлично ⭐)" else " (In progress, superb ⭐)"
                                                        (cell.tir ?: 0.0) >= targetBound -> if (isRu) " (В процессе, в норме ✓)" else " (In progress, in target ✓)"
                                                        (cell.tir ?: 0.0) >= lowBound -> if (isRu) " (В процессе, почти у цели ⚠️)" else " (In progress, near target ⚠️)"
                                                        else -> if (isRu) " (В процессе, цель ≥${targetBound.toInt()}%)" else " (In progress, target ≥${targetBound.toInt()}%)"
                                                    }
                                                    val dayPrefix = if (isRu) "Сегодня (${cell.dateLabel})" else "Today (${cell.dateLabel})"
                                                    selectedDetail = "$dayPrefix: $modeLabel $tirVal%$winLossStatus"
                                                } else {
                                                    val tirText = if (cell.tir != null) "${cell.tir.toInt()}%" else (if (isRu) "нет данных" else "no data")
                                                    val statusText = when {
                                                        cell.tir == null -> ""
                                                        cell.tir >= highBound -> if (isRu) " (Отлично ⭐)" else " (Superb ⭐)"
                                                        cell.tir >= targetBound -> if (isRu) " (В норме ✓)" else " (In target ✓)"
                                                        cell.tir >= lowBound -> if (isRu) " (Почти у цели ⚠️)" else " (Near target ⚠️)"
                                                        else -> if (isRu) " (Ниже цели)" else " (Below target)"
                                                    }
                                                    selectedDetail = "${cell.dateLabel}: $modeLabel $tirText$statusText"
                                                }
                                                selectedDayForModal = cell
                                            }
                                        },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    // Two-tone vertical progress fill for today
                                    if (cell.isToday && cell.tir != null) {
                                        val targetBound = if (isTingMode) 50.0 else 70.0
                                        val highBound = if (isTingMode) 65.0 else 85.0
                                        val lowBound = if (isTingMode) 45.0 else 67.0
                                        val fillFraction = (cell.tir / 100.0).toFloat().coerceIn(0f, 1f)
                                        val fillColor = when {
                                            cell.isGuaranteedWin || cell.tir >= highBound -> Color(0xFF34D399)
                                            cell.tir >= targetBound -> Color(0xFF10B981)
                                            cell.tir >= lowBound -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(fillFraction)
                                                .background(fillColor)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Interactive info banner on tap
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selectedDetail != null) ActionBlue.copy(alpha = 0.12f) else Color.Transparent,
                border = if (selectedDetail != null) BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cell = daysData.find { it.timestamp == selectedDayTimestamp }
                        if (cell != null && !cell.isFuture) {
                            selectedDayForModal = cell
                        }
                    }
            ) {
                Text(
                    text = selectedDetail ?: if (isRu) "Нажмите на день для подробной статистики 📊" else "Tap a day for full statistics 📊",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedDetail != null) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedDetail != null) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRu) "Меньше" else "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(5.dp))
                LegendCell(Color(0xFF263238)) // No data
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFFEF4444)) // <67%
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFFF59E0B)) // 67-69%
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFF10B981)) // 70-84%
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFF34D399)) // >=85%
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isRu) "Больше" else "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }

    if (selectedDayForModal != null) {
        DayDetailSummaryDialog(
            dayData = selectedDayForModal!!,
            todayStats = todayStats,
            unit = unit,
            isRu = isRu,
            onDismiss = { selectedDayForModal = null }
        )
    }
}

@Composable
private fun DayDetailSummaryDialog(
    dayData: DayCellData,
    todayStats: com.tirup.app.domain.model.GlucoseStatistics?,
    unit: com.tirup.app.domain.model.GlucoseUnit,
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    val fullDateFormat = remember(isRu) {
        SimpleDateFormat("EEEE, d MMMM", if (isRu) Locale("ru") else Locale.US)
    }
    val fullDateStr = remember(dayData.timestamp, isRu) {
        fullDateFormat.format(Date(dayData.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val summary = dayData.summary
    val isToday = dayData.isToday

    // Extract metrics
    val readingsCount = if (isToday) (todayStats?.totalCount ?: 0) else (summary?.readingsCount ?: 0)
    val hasData = if (isToday) (readingsCount >= 3 || (dayData.tir != null && dayData.tir > 0.0)) else (summary != null && readingsCount >= 10)

    val tir = if (isToday) (todayStats?.tirPercent ?: (dayData.tir ?: 0.0)) else (summary?.tirPercent ?: 0.0)
    val ting = if (isToday) (todayStats?.tingPercent ?: 0.0) else (summary?.tingPercent ?: 0.0)
    val tbrLow = if (isToday) (todayStats?.tbrLowPercent ?: 0.0) else (summary?.tbrLowPercent ?: 0.0)
    val tbrVeryLow = if (isToday) (todayStats?.tbrVeryLowPercent ?: 0.0) else (summary?.tbrVeryLowPercent ?: 0.0)
    val tarHigh = if (isToday) (todayStats?.tarHighPercent ?: 0.0) else (summary?.tarHighPercent ?: 0.0)
    val tarVeryHigh = if (isToday) (todayStats?.tarVeryHighPercent ?: 0.0) else (summary?.tarVeryHighPercent ?: 0.0)
    val meanMmol = if (isToday) (todayStats?.meanMmol ?: 0.0) else (summary?.meanMmol ?: 0.0)
    val sdMmol = if (isToday) (todayStats?.sdMmol ?: 0.0) else (summary?.sdMmol ?: 0.0)
    val cvPercent = if (isToday) (todayStats?.cvPercent ?: 0.0) else (summary?.cvPercent ?: 0.0)

    val unitStr = if (unit == com.tirup.app.domain.model.GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L")
                  else (if (isRu) "мг/дл" else "mg/dL")

    val meanStr = if (meanMmol > 0.0) {
        if (unit == com.tirup.app.domain.model.GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", meanMmol)
        else String.format(Locale.US, "%d", (meanMmol * 18.0182).roundToInt())
    } else "—"

    val sdStr = if (sdMmol > 0.0) {
        if (unit == com.tirup.app.domain.model.GlucoseUnit.MMOL_L) String.format(Locale.US, "±%.1f", sdMmol)
        else String.format(Locale.US, "±%d", (sdMmol * 18.0182).roundToInt())
    } else "—"

    val cvStr = if (cvPercent > 0.0) String.format(Locale.US, "%.1f%%", cvPercent) else "—"

    val expectedPoints = if (readingsCount > 300) 1440.0 else 288.0
    val coveragePercent = ((readingsCount / expectedPoints) * 100.0).coerceIn(0.0, 100.0).roundToInt()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fullDateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isToday) (if (isRu) "Сегодняшний день (в процессе)" else "Today (in progress)")
                                   else (if (isRu) "Суточный отчёт" else "Daily summary"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDismiss() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Status Badge
                val badge = when {
                    !hasData -> DayBadgeInfo(
                        text = if (isRu) "Мало данных (<10 замеров)" else "Insufficient data (<10)",
                        bg = Color(0xFF64748B).copy(alpha = 0.15f),
                        border = Color(0xFF64748B).copy(alpha = 0.35f),
                        textCol = Color(0xFF94A3B8)
                    )
                    isToday && dayData.isGuaranteedWin -> DayBadgeInfo(
                        text = if (isRu) "🏆 Цель выполнена досрочно! (≥70%)" else "🏆 Target reached early! (≥70%)",
                        bg = PrimaryEmerald.copy(alpha = 0.15f),
                        border = PrimaryEmerald.copy(alpha = 0.4f),
                        textCol = PrimaryEmerald
                    )
                    isToday && tir >= 70.0 -> DayBadgeInfo(
                        text = if (isRu) "✓ В норме (TIR ≥70% сейчас)" else "✓ In target (TIR ≥70% now)",
                        bg = PrimaryEmerald.copy(alpha = 0.15f),
                        border = PrimaryEmerald.copy(alpha = 0.4f),
                        textCol = PrimaryEmerald
                    )
                    isToday && tir >= 67.0 -> DayBadgeInfo(
                        text = if (isRu) "⚠️ Почти у цели (67–69%)" else "⚠️ Near target (67–69%)",
                        bg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = Color(0xFFF59E0B).copy(alpha = 0.4f),
                        textCol = Color(0xFFF59E0B)
                    )
                    isToday -> DayBadgeInfo(
                        text = if (isRu) "⏳ День продолжается (цель ≥70%)" else "⏳ In progress (target ≥70%)",
                        bg = ActionBlue.copy(alpha = 0.15f),
                        border = ActionBlue.copy(alpha = 0.4f),
                        textCol = ActionBlue
                    )
                    tir >= 85.0 -> DayBadgeInfo(
                        text = if (isRu) "⭐ Превосходная дисциплина (TIR ≥85%)" else "⭐ Outstanding control (TIR ≥85%)",
                        bg = Color(0xFF34D399).copy(alpha = 0.15f),
                        border = Color(0xFF34D399).copy(alpha = 0.4f),
                        textCol = Color(0xFF34D399)
                    )
                    tir >= 70.0 -> DayBadgeInfo(
                        text = if (isRu) "✓ Цель дня достигнута (TIR ≥70%)" else "✓ Day target met (TIR ≥70%)",
                        bg = PrimaryEmerald.copy(alpha = 0.15f),
                        border = PrimaryEmerald.copy(alpha = 0.4f),
                        textCol = PrimaryEmerald
                    )
                    tir >= 67.0 -> DayBadgeInfo(
                        text = if (isRu) "⚠️ Близко к цели (TIR 67–69%)" else "⚠️ Close to target (TIR 67–69%)",
                        bg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = Color(0xFFF59E0B).copy(alpha = 0.4f),
                        textCol = Color(0xFFF59E0B)
                    )
                    else -> DayBadgeInfo(
                        text = if (isRu) "Ниже целевого диапазона (<70%)" else "Below target range (<70%)",
                        bg = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = Color(0xFFEF4444).copy(alpha = 0.4f),
                        textCol = Color(0xFFEF4444)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badge.bg,
                    border = BorderStroke(1.dp, badge.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = badge.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = badge.textCol,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                if (hasData) {
                    // Continuous Stacked Progress Bar
                    DailyStackedRangeBar(
                        tir = tir,
                        tbrLow = tbrLow,
                        tbrVeryLow = tbrVeryLow,
                        tarHigh = tarHigh,
                        tarVeryHigh = tarVeryHigh
                    )

                    // Range Breakdown Section
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isRu) "Распределение по диапазонам" else "Time in Ranges",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // TIR
                            DayRangeDetailRow(
                                label = "TIR (В норме)",
                                range = "3.9–10.0",
                                target = "≥70%",
                                value = tir,
                                color = PrimaryEmerald,
                                isRu = isRu
                            )

                            // TING
                            DayRangeDetailRow(
                                label = "TING (Идеал)",
                                range = "3.9–7.8",
                                target = "≥50%",
                                value = ting,
                                color = Color(0xFF34D399),
                                isRu = isRu
                            )

                            // TBR Low
                            DayRangeDetailRow(
                                label = if (isRu) "TBR (Низкий)" else "TBR (Low)",
                                range = "3.0–3.8",
                                target = "<4%",
                                value = tbrLow,
                                color = Color(0xFFF59E0B),
                                isRu = isRu
                            )

                            // TBR Very Low
                            DayRangeDetailRow(
                                label = if (isRu) "TBR (Тяжёлая гипо)" else "TBR (Very Low)",
                                range = "<3.0",
                                target = "<1%",
                                value = tbrVeryLow,
                                color = Color(0xFFEF4444),
                                isRu = isRu
                            )

                            // TAR High
                            DayRangeDetailRow(
                                label = if (isRu) "TAR (Высокий)" else "TAR (High)",
                                range = "10.1–13.9",
                                target = "<25%",
                                value = tarHigh,
                                color = Color(0xFFF59E0B),
                                isRu = isRu
                            )

                            // TAR Very High
                            DayRangeDetailRow(
                                label = if (isRu) "TAR (Тяжёлая гипер)" else "TAR (Very High)",
                                range = ">13.9",
                                target = "<5%",
                                value = tarVeryHigh,
                                color = Color(0xFFEF4444),
                                isRu = isRu
                            )
                        }
                    }

                    // Quality Metrics Grid (2x2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DayMetricCard(
                                title = if (isRu) "Средний сахар" else "Mean Glucose",
                                value = "$meanStr $unitStr",
                                subtitle = if (meanMmol in 3.9..7.8) (if (isRu) "В норме ✓" else "In target ✓")
                                           else (if (isRu) "Цель: 3.9–10.0" else "Goal: 3.9–10.0"),
                                accentColor = if (meanMmol in 3.9..7.8) PrimaryEmerald else Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )

                            DayMetricCard(
                                title = if (isRu) "Вариабельность" else "Variability (CV)",
                                value = cvStr,
                                subtitle = if (cvPercent <= 36.0 && cvPercent > 0.0) (if (isRu) "Стабильно (≤36%) ✓" else "Stable (≤36%) ✓")
                                           else (if (isRu) "Повышена (>36%) ⚠️" else "High (>36%) ⚠️"),
                                accentColor = if (cvPercent <= 36.0 && cvPercent > 0.0) PrimaryEmerald else Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DayMetricCard(
                                title = if (isRu) "Отклонение (SD)" else "Std. Dev. (SD)",
                                value = "$sdStr $unitStr",
                                subtitle = if (isRu) "Разброс сахара" else "Glucose spread",
                                accentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            DayMetricCard(
                                title = if (isRu) "Замеры / Охват" else "Readings / Coverage",
                                value = "$readingsCount ${if (isRu) "точек" else "pts"}",
                                subtitle = "$coveragePercent% ${if (isRu) "суток" else "of day"}",
                                accentColor = if (coveragePercent >= 70) PrimaryEmerald else Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRu) "За этот день недостаточно данных от сенсора (менее 10 измерений)."
                                   else "Insufficient sensor data for this day (<10 readings).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                ) {
                    Text(
                        text = if (isRu) "Закрыть" else "Close",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyStackedRangeBar(
    tir: Double,
    tbrLow: Double,
    tbrVeryLow: Double,
    tarHigh: Double,
    tarVeryHigh: Double
) {
    val total = (tir + tbrLow + tbrVeryLow + tarHigh + tarVeryHigh).coerceAtLeast(1.0)
    val vLowWeight = (tbrVeryLow / total).toFloat().coerceIn(0f, 1f)
    val lowWeight = (tbrLow / total).toFloat().coerceIn(0f, 1f)
    val inRangeWeight = (tir / total).toFloat().coerceIn(0f, 1f)
    val highWeight = (tarHigh / total).toFloat().coerceIn(0f, 1f)
    val vHighWeight = (tarVeryHigh / total).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF334155))
    ) {
        if (vLowWeight > 0.001f) {
            Box(
                modifier = Modifier
                    .weight(vLowWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFEF4444))
            )
        }
        if (lowWeight > 0.001f) {
            Box(
                modifier = Modifier
                    .weight(lowWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFF59E0B))
            )
        }
        if (inRangeWeight > 0.001f) {
            Box(
                modifier = Modifier
                    .weight(inRangeWeight)
                    .fillMaxHeight()
                    .background(Color(0xFF10B981))
            )
        }
        if (highWeight > 0.001f) {
            Box(
                modifier = Modifier
                    .weight(highWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFF59E0B))
            )
        }
        if (vHighWeight > 0.001f) {
            Box(
                modifier = Modifier
                    .weight(vHighWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFEF4444))
            )
        }
    }
}

@Composable
private fun DayRangeDetailRow(
    label: String,
    range: String,
    target: String,
    value: Double,
    color: Color,
    isRu: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
            Text(
                text = "($range)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${if (isRu) "цель" else "target"} $target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(
                text = "${value.roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DayMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
    }
}

private data class DayBadgeInfo(
    val text: String,
    val bg: Color,
    val border: Color,
    val textCol: Color
)

@Composable
private fun LegendCell(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

private data class DayCellData(
    val timestamp: Long,
    val dateLabel: String,
    val tir: Double?,
    val isToday: Boolean,
    val isFuture: Boolean = false,
    val isGuaranteedWin: Boolean = false,
    val isGuaranteedLoss: Boolean = false,
    val summary: com.tirup.app.domain.model.DailySummary? = null
)
