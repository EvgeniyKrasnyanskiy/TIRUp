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

@Composable
fun StreakMotivatorDialog(
    streakDays: Int,
    isRu: Boolean,
    bestStreakDays: Int = 0,
    dailySummaries: List<com.tirup.app.domain.model.DailySummary> = emptyList(),
    todayTirPercent: Double = 0.0,
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

    val daysWord = if (isRu) {
        val rem10 = streakDays % 10
        val rem100 = streakDays % 100
        when {
            rem100 in 11..19 -> "$streakDays дней"
            rem10 == 1 -> "$streakDays день"
            rem10 in 2..4 -> "$streakDays дня"
            else -> "$streakDays дней"
        }
    } else {
        if (streakDays == 1) "1 day" else "$streakDays days"
    }

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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isRu) "Дни в целевом диапазоне!" else "Target Range Streak!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF7A00).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF7A00).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isRu) "🔥 $daysWord подряд в норме" else "🔥 $daysWord streak in target",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7A00),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    if (bestStreakDays > 0) {
                        val bestWord = if (isRu) {
                            val rem10 = bestStreakDays % 10
                            val rem100 = bestStreakDays % 100
                            when {
                                rem100 in 11..19 -> "$bestStreakDays дней"
                                rem10 == 1 -> "$bestStreakDays день"
                                rem10 in 2..4 -> "$bestStreakDays дня"
                                else -> "$bestStreakDays дней"
                            }
                        } else {
                            if (bestStreakDays == 1) "1 day" else "$bestStreakDays days"
                        }
                        Text(
                            text = if (isRu) "Лучшая серия: $bestWord" else "Best streak: $bestWord",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // GitHub-Style 28-Day TIR Contribution Grid
                GitHubTirContributionGrid(
                    dailySummaries = dailySummaries,
                    todayTirPercent = todayTirPercent,
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
                        text = if (streakDays > 0) (if (isRu) "Так держать!" else "Proud of this! Continue")
                               else (if (isRu) "Ок" else "OK"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubTirContributionGrid(
    dailySummaries: List<com.tirup.app.domain.model.DailySummary>,
    todayTirPercent: Double,
    isRu: Boolean
) {
    var selectedDetail by remember { mutableStateOf<String?>(null) }

    // Prepare 28 days list (4 weeks of 7 days) ending today
    val daysData = remember(dailySummaries, todayTirPercent) {
        val list = mutableListOf<DayCellData>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        val dateFormat = SimpleDateFormat("d MMM", if (isRu) Locale("ru") else Locale.US)

        for (i in 27 downTo 0) {
            val dayStart = todayStart - i * 86400000L
            val isToday = i == 0
            val matchingSummary = dailySummaries.find { abs(it.dateTimestamp - dayStart) < 43200000L }

            val tir: Double? = if (isToday) {
                if (todayTirPercent > 0.0) todayTirPercent else matchingSummary?.tirPercent
            } else {
                matchingSummary?.takeIf { it.readingsCount >= 10 }?.tirPercent
            }

            val dateLabel = dateFormat.format(Date(dayStart))
            list.add(DayCellData(dayStart, dateLabel, tir, isToday))
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
                    text = if (isRu) "Активность TIR (28 дней)" else "28-Day TIR Activity",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRu) "Сетка контроля" else "Control Grid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                                val cellColor = when {
                                    cell.tir == null -> Color(0xFF263238) // no data (dark slate)
                                    cell.tir < 70.0 -> Color(0xFFEF4444)   // below target (red)
                                    cell.tir < 85.0 -> Color(0xFF10B981)   // target reached (emerald)
                                    else -> Color(0xFF059669)              // exceptional target (dark emerald)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(cellColor)
                                        .then(
                                            if (cell.isToday) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            val tirText = if (cell.tir != null) "${cell.tir.toInt()}%" else (if (isRu) "нет данных" else "no data")
                                            val statusText = when {
                                                cell.tir == null -> ""
                                                cell.tir >= 85.0 -> if (isRu) " (Отлично ⭐)" else " (Superb ⭐)"
                                                cell.tir >= 70.0 -> if (isRu) " (В норме ✓)" else " (In target ✓)"
                                                else -> if (isRu) " (Ниже цели)" else " (Below target)"
                                            }
                                            selectedDetail = "${cell.dateLabel}: TIR $tirText$statusText"
                                        }
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Interactive info banner on tap
            Text(
                text = selectedDetail ?: if (isRu) "Нажмите на день для деталей" else "Tap a day for details",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedDetail != null) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selectedDetail != null) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

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
                Spacer(modifier = Modifier.width(6.dp))
                LegendCell(Color(0xFF263238)) // No data
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFFEF4444)) // <70%
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFF10B981)) // 70-84%
                Spacer(modifier = Modifier.width(3.dp))
                LegendCell(Color(0xFF059669)) // >=85%
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRu) "Больше" else "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

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
    val isToday: Boolean
)
