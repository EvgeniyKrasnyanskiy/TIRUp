package com.tirup.app.presentation.trends

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.WeeklyDigest
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDigestSheet(
    digest: WeeklyDigest,
    isRu: Boolean,
    unit: GlucoseUnit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isMmol = unit == GlucoseUnit.MMOL_L

    val dateFormat = SimpleDateFormat(if (isRu) "d MMMM" else "MMM d", if (isRu) Locale("ru") else Locale.US)
    val currentPeriodStr = "${dateFormat.format(Date(digest.currentWeekStart))} — ${dateFormat.format(Date(digest.currentWeekEnd))}"
    val prevPeriodStr = "${dateFormat.format(Date(digest.previousWeekStart))} — ${dateFormat.format(Date(digest.previousWeekEnd))}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📊",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (isRu) "Воскресный дайджест" else "Weekly Sunday Digest",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$currentPeriodStr (${if (isRu) "vs" else "vs"} $prevPeriodStr)",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (isRu) "Закрыть" else "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sufficiency Chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (digest.hasSufficientData) PrimaryEmerald.copy(alpha = 0.15f) else ColorVeryHigh.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (digest.hasSufficientData) PrimaryEmerald.copy(alpha = 0.4f) else ColorVeryHigh.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (digest.hasSufficientData) PrimaryEmerald else ColorVeryHigh)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (digest.hasSufficientData) {
                            if (isRu) "Данные CGM полные (активность ${digest.currentStats.activeTimePercent.roundToInt()}%, стандарт ≥70%)"
                            else "CGM data sufficient (${digest.currentStats.activeTimePercent.roundToInt()}% active, standard ≥70%)"
                        } else {
                            if (isRu) "Накопление данных (<70% покрытия). Предварительная сводка"
                            else "Accumulating data (<70% coverage). Preliminary summary"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (digest.hasSufficientData) PrimaryEmerald else ColorVeryHigh
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Comparative Table Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isRu) "СРАВНЕНИЕ С ПРОШЛОЙ НЕДЕЛЕЙ" else "COMPARISON WITH PREVIOUS WEEK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionBlue,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Column headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "Метрика" else "Metric",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = if (isRu) "Эта нед." else "This wk",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isRu) "Прошлая" else "Last wk",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isRu) "Динамика" else "Delta",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row: TIR
                    DigestMetricRow(
                        label = "TIR (3.9–10.0)",
                        currentValue = "${digest.currentStats.tirPercent.roundToInt()}%",
                        prevValue = "${digest.previousStats.tirPercent.roundToInt()}%",
                        delta = digest.tirDelta,
                        isHigherBetter = true,
                        accentColor = PrimaryEmerald
                    )

                    // Row: TING
                    DigestMetricRow(
                        label = "TING (3.9–7.8)",
                        currentValue = "${digest.currentStats.tingPercent.roundToInt()}%",
                        prevValue = "${digest.previousStats.tingPercent.roundToInt()}%",
                        delta = digest.tingDelta,
                        isHigherBetter = true,
                        accentColor = ColorTight
                    )

                    // Row: TBR
                    val currTbr = digest.totalTbrCurrent
                    val prevTbr = digest.totalTbrPrevious
                    DigestMetricRow(
                        label = if (isRu) "TBR (гипо)" else "TBR (low)",
                        currentValue = String.format(Locale.US, "%.1f%%", currTbr),
                        prevValue = String.format(Locale.US, "%.1f%%", prevTbr),
                        delta = digest.tbrDelta,
                        isHigherBetter = false,
                        accentColor = if (currTbr > 4.0) ColorVeryLow else PrimaryEmerald
                    )

                    // Row: CV
                    DigestMetricRow(
                        label = if (isRu) "CV (разброс)" else "CV (volatility)",
                        currentValue = "${digest.currentStats.cvPercent.roundToInt()}%",
                        prevValue = "${digest.previousStats.cvPercent.roundToInt()}%",
                        delta = digest.cvDelta,
                        isHigherBetter = false,
                        accentColor = if (digest.currentStats.cvPercent <= 36.0) PrimaryEmerald else ColorHigh
                    )

                    // Row: Mean glucose
                    val meanCurrStr = if (isMmol) {
                        String.format(Locale.US, "%.1f", digest.currentStats.meanMmol)
                    } else {
                        (digest.currentStats.meanMmol * 18.0182).roundToInt().toString()
                    }
                    val meanPrevStr = if (isMmol) {
                        String.format(Locale.US, "%.1f", digest.previousStats.meanMmol)
                    } else {
                        (digest.previousStats.meanMmol * 18.0182).roundToInt().toString()
                    }
                    val unitLabel = if (isMmol) (if (isRu) "ммоль/л" else "mmol/L") else "mg/dL"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Средний сахар" else "Mean Glucose",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "$meanCurrStr $unitLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$meanPrevStr $unitLabel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (digest.meanDeltaMmol >= 0) "+${digest.meanDeltaMmol}" else "${digest.meanDeltaMmol}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    // Row: Hypo episodes
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Эпизоды гипо" else "Hypo Episodes",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "${digest.hypoCountCurrent}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (digest.hypoCountCurrent > 0) ColorVeryLow else PrimaryEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${digest.hypoCountPrevious}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        val hypoDiff = digest.hypoCountCurrent - digest.hypoCountPrevious
                        val hypoDiffStr = if (hypoDiff > 0) "+$hypoDiff" else "$hypoDiff"
                        Text(
                            text = hypoDiffStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hypoDiff > 0) ColorVeryLow else if (hypoDiff < 0) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Clinical Insights Section
            Text(
                text = if (isRu) "КЛИНИЧЕСКИЕ ИНСАЙТЫ НЕДЕЛИ" else "WEEKLY CLINICAL INSIGHTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ActionBlue,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = digest.headline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    digest.keyInsights.forEach { insight ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = insight,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Doctor's Advice / Recommendation Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ActionBlue.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "💡",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Column {
                        Text(
                            text = if (isRu) "Фокус на следующую неделю" else "Focus for next week",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActionBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = digest.recommendation,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            append("📊 TIRUp — ").append(if (isRu) "Воскресный дайджест" else "Weekly Sunday Digest").append("\n")
                            append(currentPeriodStr).append("\n\n")
                            append("• TIR: ").append(digest.currentStats.tirPercent.roundToInt()).append("% (Δ ")
                            if (digest.tirDelta >= 0) append("+")
                            append(digest.tirDelta).append("%)\n")
                            append("• TING: ").append(digest.currentStats.tingPercent.roundToInt()).append("%\n")
                            append("• TBR: ").append(String.format(Locale.US, "%.1f%%", digest.totalTbrCurrent)).append("\n")
                            append("• CV: ").append(digest.currentStats.cvPercent.roundToInt()).append("%\n")
                            append("• ").append(if (isRu) "Эпизоды гипо" else "Hypo episodes").append(": ").append(digest.hypoCountCurrent).append("\n\n")
                            append(digest.headline).append("\n\n")
                            digest.keyInsights.forEach { append("• ").append(it).append("\n") }
                            append("\n💡 ").append(digest.recommendation)
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, if (isRu) "Поделиться дайджестом" else "Share Digest"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ActionBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRu) "Поделиться" else "Share",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    )
                ) {
                    Text(
                        text = if (isRu) "Закрыть" else "Close",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DigestMetricRow(
    label: String,
    currentValue: String,
    prevValue: String,
    delta: Double,
    isHigherBetter: Boolean,
    accentColor: Color
) {
    val deltaPositiveGood = if (isHigherBetter) delta > 0 else delta < 0
    val deltaNegativeBad = if (isHigherBetter) delta < 0 else delta > 0

    val deltaColor = when {
        deltaPositiveGood -> PrimaryEmerald
        deltaNegativeBad -> ColorVeryLow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val deltaStr = if (delta > 0) "+$delta%" else "$delta%"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = currentValue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = prevValue,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = deltaStr,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = deltaColor,
            modifier = Modifier.weight(1.1f)
        )
    }
}
