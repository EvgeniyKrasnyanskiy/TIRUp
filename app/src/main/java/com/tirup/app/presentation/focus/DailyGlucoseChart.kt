package com.tirup.app.presentation.focus

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.calculator.GlucoseTrendPredictor
import com.tirup.app.domain.calculator.ForecastPoint
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTarget
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class DataGap(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMinutes: Int,
    val startMinute: Float,
    val endMinute: Float
)

data class TreatmentCluster(
    val treatments: List<Treatment>,
    val isInsulin: Boolean
) {
    val id: Long get() = treatments.first().id
    val timestamp: Long get() = treatments.first().timestamp
    val totalInsulin: Double get() = treatments.sumOf { it.insulinUnits ?: 0.0 }
    val totalCarbs: Double get() = treatments.sumOf { it.carbsGrams ?: 0.0 }
    val isSingle: Boolean get() = treatments.size == 1
    val isCombo: Boolean get() = isSingle && treatments.first().isCombo
    val notes: String? get() = treatments.mapNotNull { it.notes?.trim()?.takeIf { n -> n.isNotEmpty() } }.joinToString("; ").takeIf { it.isNotEmpty() }

    val displayText: String get() {
        return if (isInsulin) {
            val doses = treatments.mapNotNull { it.insulinUnits }
            if (doses.size <= 1) {
                val ins = totalInsulin
                if (ins == ins.toInt().toDouble()) "${ins.toInt()}U" else String.format(Locale.US, "%.1fU", ins)
            } else {
                val joined = doses.joinToString("+") {
                    if (it == it.toInt().toDouble()) "${it.toInt()}" else String.format(Locale.US, "%.1f", it)
                } + "U"
                if (joined.length <= 8) {
                    joined
                } else {
                    val ins = totalInsulin
                    val sumStr = if (ins == ins.toInt().toDouble()) "${ins.toInt()}" else String.format(Locale.US, "%.1f", ins)
                    "${sumStr}U (+)"
                }
            }
        } else {
            val doses = treatments.mapNotNull { it.carbsGrams }
            if (doses.size <= 1) {
                "${totalCarbs.toInt()}g"
            } else {
                val joined = doses.joinToString("+") { "${it.toInt()}" } + "g"
                if (joined.length <= 8) {
                    joined
                } else {
                    "${totalCarbs.toInt()}g (+)"
                }
            }
        }
    }
}

internal fun clusterTreatments(
    treatments: List<Treatment>,
    isInsulin: Boolean,
    thresholdMs: Long = 5 * 60 * 1000L
): List<TreatmentCluster> {
    val items = treatments.filter { if (isInsulin) it.hasInsulin else it.hasCarbs }
        .sortedBy { it.timestamp }
    if (items.isEmpty()) return emptyList()

    val clusters = mutableListOf<TreatmentCluster>()
    var currentGroup = mutableListOf<Treatment>()

    for (tr in items) {
        if (currentGroup.isEmpty()) {
            currentGroup.add(tr)
        } else {
            val first = currentGroup.first()
            if (tr.timestamp - first.timestamp <= thresholdMs) {
                currentGroup.add(tr)
            } else {
                clusters.add(TreatmentCluster(currentGroup.toList(), isInsulin))
                currentGroup = mutableListOf(tr)
            }
        }
    }
    if (currentGroup.isNotEmpty()) {
        clusters.add(TreatmentCluster(currentGroup.toList(), isInsulin))
    }
    return clusters
}

/**
 * Interactive 24-hour daily glucose chart for the Focus screen.
 * Supports:
 * - Horizontal pinch-to-zoom (from 2h up to 24h window)
 * - Horizontal drag/pan scrolling
 * - Tap on readings to inspect exact value, timestamp, delta, and IoB
 * - Clinical target corridor (3.9 - 10.0 mmol/L / 70 - 180 mg/dL)
 * - Real-time "Now" indicator line
 * - Tab toggle between [📊 График] and [🔢 Параметры]
 * - Insulin bolus 💉 and meal/carb 🍽️ treatment markers overlay with 5m clustering & multi-tier staggering
 */
@Composable
fun DailyGlucoseChart(
    readings: List<GlucoseReading>,
    targetRanges: TargetRanges,
    unit: GlucoseUnit,
    isRu: Boolean,
    modifier: Modifier = Modifier,
    treatments: List<Treatment> = emptyList(),
    showTreatments: Boolean = true,
    showPrediction: Boolean = true,
    onDeleteTreatment: ((Long) -> Unit)? = null,
    selectedMode: Int = 0,
    onModeChange: (Int) -> Unit = {},
    onConfigureMetricsClick: (() -> Unit)? = null,
    metricsContent: (@Composable () -> Unit)? = null
) {
    val now = System.currentTimeMillis()
    val calendar = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val startOfDay = calendar.timeInMillis
    val currentMinuteOfDay = ((now - startOfDay) / 60000f).coerceIn(0f, 1440f)

    val todayReadings = remember(readings, startOfDay) {
        val filtered = readings.filter { it.timestamp >= startOfDay }
        if (filtered.isNotEmpty()) filtered.sortedBy { it.timestamp }
        else readings.sortedBy { it.timestamp }
    }

    val todayTreatments = remember(treatments, startOfDay, showTreatments) {
        if (!showTreatments) return@remember emptyList()
        val filtered = treatments.filter { it.timestamp >= startOfDay }
        if (filtered.isNotEmpty()) filtered.sortedBy { it.timestamp }
        else treatments.sortedBy { it.timestamp }
    }

    val insulinClusters = remember(todayTreatments) {
        clusterTreatments(todayTreatments, isInsulin = true)
    }

    val carbsClusters = remember(todayTreatments) {
        clusterTreatments(todayTreatments, isInsulin = false)
    }

    var visibleMinutes by remember { mutableFloatStateOf(360f) }
    var windowStartMinute by remember {
        mutableFloatStateOf((currentMinuteOfDay - 300f).coerceIn(0f, (1440f - 360f).coerceAtLeast(0f)))
    }

    var selectedReading by remember { mutableStateOf<GlucoseReading?>(null) }
    var selectedGap by remember { mutableStateOf<DataGap?>(null) }
    var selectedTreatmentCluster by remember { mutableStateOf<TreatmentCluster?>(null) }
    var selectedForecastPoint by remember { mutableStateOf<ForecastPoint?>(null) }

    val forecastPoints = remember(todayReadings, showPrediction) {
        if (!showPrediction) emptyList()
        else GlucoseTrendPredictor.generateForecastPoints(todayReadings, horizonMinutes = 25, stepMinutes = 5)
    }

    val dataGaps = remember(todayReadings, startOfDay) {
        val gaps = mutableListOf<DataGap>()
        for (i in 1 until todayReadings.size) {
            val prev = todayReadings[i - 1]
            val curr = todayReadings[i]
            val dtMs = curr.timestamp - prev.timestamp
            if (dtMs > 20 * 60_000L) {
                val durMin = (dtMs / 60_000L).toInt()
                val mStart = (prev.timestamp - startOfDay) / 60000f
                val mEnd = (curr.timestamp - startOfDay) / 60000f
                gaps.add(DataGap(prev.timestamp, curr.timestamp, durMin, mStart, mEnd))
            }
        }
        gaps
    }

    val totalGapMinutes = remember(dataGaps) { dataGaps.sumOf { it.durationMinutes } }
    val monitoredMinutes = remember(todayReadings, now) {
        if (todayReadings.isEmpty()) 0
        else ((now - todayReadings.first().timestamp) / 60000L).toInt().coerceIn(1, 1440)
    }
    val sensorActivePercent = remember(monitoredMinutes, totalGapMinutes) {
        if (monitoredMinutes <= 0) 100
        else {
            val activeMin = (monitoredMinutes - totalGapMinutes).coerceAtLeast(0)
            ((activeMin.toDouble() / monitoredMinutes) * 100.0).roundToInt().coerceIn(0, 100)
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        padding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Mode Toggle (Chart vs Metrics) on the left, Zoom Scale / Settings on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Selector Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedMode == 0) ActionBlue else Color.Transparent,
                        modifier = Modifier.clickable { onModeChange(0) }
                    ) {
                        Text(
                            text = if (isRu) "📊 График" else "📊 Chart",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedMode == 0) Color.White else onSurfaceVariant,
                            fontWeight = if (selectedMode == 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedMode == 1) ActionBlue else Color.Transparent,
                        modifier = Modifier.clickable { onModeChange(1) }
                    ) {
                        Text(
                            text = if (isRu) "🔢 Параметры" else "🔢 Metrics",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedMode == 1) Color.White else onSurfaceVariant,
                            fontWeight = if (selectedMode == 1) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedMode == 0) {
                        val zoomHours = (visibleMinutes / 60f)
                        val zoomLabel = if (zoomHours >= 1f) {
                            if (isRu) String.format(Locale.US, "🔍 %.0f ч", zoomHours)
                            else String.format(Locale.US, "🔍 %.0fh", zoomHours)
                        } else {
                            if (isRu) "${visibleMinutes.toInt()} мин" else "${visibleMinutes.toInt()}m"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, outlineColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = zoomLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (selectedMode == 1 && onConfigureMetricsClick != null) {
                        IconButton(
                            onClick = onConfigureMetricsClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Configure parameters",
                                tint = onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (selectedMode == 1) {
                metricsContent?.invoke()
            } else {
                // Selected Treatment Cluster, Gap or Reading Inspector Banner
                if (selectedTreatmentCluster != null) {
                    val cluster = selectedTreatmentCluster!!
                    val trTime = timeFormatter.format(Date(cluster.timestamp))
                    val bannerColor = if (cluster.isCombo) Color(0xFF8B5CF6)
                    else if (cluster.isInsulin) ActionBlue
                    else Color(0xFFF59E0B)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bannerColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "⏱ $trTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface
                                )

                                if (cluster.isInsulin) {
                                    val totalIns = cluster.totalInsulin
                                    val totalStr = if (totalIns == totalIns.toInt().toDouble()) "${totalIns.toInt()}" else String.format(Locale.US, "%.1f", totalIns)
                                    val insDetail = if (cluster.isSingle) {
                                        if (isRu) "💉 $totalStr Ед" else "💉 $totalStr U"
                                    } else {
                                        val breakdown = cluster.treatments.mapNotNull { it.insulinUnits }.joinToString("+") {
                                            if (it == it.toInt().toDouble()) "${it.toInt()}" else String.format(Locale.US, "%.1f", it)
                                        }
                                        if (isRu) "💉 $breakdown=$totalStr Ед (${cluster.treatments.size} подколки)"
                                        else "💉 $breakdown=${totalStr}U (${cluster.treatments.size} boluses)"
                                    }
                                    Text(
                                        text = insDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue
                                    )

                                    if (cluster.isCombo) {
                                        val carbsG = cluster.treatments.first().carbsGrams ?: 0.0
                                        val xeStr = String.format(Locale.US, "%.1f", carbsG / 12.0)
                                        Text(
                                            text = if (isRu) "🍽️ ${carbsG.toInt()} г ($xeStr ХЕ)" else "🍽️ ${carbsG.toInt()} g ($xeStr XE)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                } else {
                                    val totalCarbs = cluster.totalCarbs
                                    val xeStr = String.format(Locale.US, "%.1f", totalCarbs / 12.0)
                                    val carbsDetail = if (cluster.isSingle) {
                                        if (isRu) "🍽️ ${totalCarbs.toInt()} г ($xeStr ХЕ)" else "🍽️ ${totalCarbs.toInt()} g ($xeStr XE)"
                                    } else {
                                        val breakdown = cluster.treatments.mapNotNull { it.carbsGrams }.joinToString("+") { "${it.toInt()}" }
                                        if (isRu) "🍽️ $breakdown=${totalCarbs.toInt()} г ($xeStr ХЕ, ${cluster.treatments.size} приёма)"
                                        else "🍽️ $breakdown=${totalCarbs.toInt()} g ($xeStr XE, ${cluster.treatments.size} meals)"
                                    }
                                    Text(
                                        text = carbsDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }

                                val noteText = cluster.notes
                                if (!noteText.isNullOrBlank()) {
                                    val truncatedNote = if (noteText.length > 14) noteText.take(13) + "…" else noteText
                                    Text(
                                        text = "• $truncatedNote",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onDeleteTreatment != null) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = if (isRu) "Удалить метку" else "Delete mark",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                cluster.treatments.forEach { onDeleteTreatment(it.id) }
                                                selectedTreatmentCluster = null
                                            }
                                    )
                                }

                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { selectedTreatmentCluster = null }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                } else if (selectedGap != null) {
                    val gap = selectedGap!!
                    val startTime = timeFormatter.format(Date(gap.startTimestamp))
                    val endTime = timeFormatter.format(Date(gap.endTimestamp))
                    val durText = if (gap.durationMinutes >= 60) {
                        val h = gap.durationMinutes / 60
                        val m = gap.durationMinutes % 60
                        if (isRu) {
                            if (m > 0) "${h}ч ${m}м" else "${h}ч"
                        } else {
                            if (m > 0) "${h}h ${m}m" else "${h}h"
                        }
                    } else {
                        if (isRu) "${gap.durationMinutes}м" else "${gap.durationMinutes}m"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⏱ $startTime — $endTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRu) "⚠️ Потеря сигнала: $durText" else "⚠️ Signal Gap: $durText",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                } else if (selectedForecastPoint != null) {
                    val fp = selectedForecastPoint!!
                    val fpVal = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", fp.valueMmol)
                    else "${(fp.valueMmol * 18.0182).toInt()}"
                    val fpUnit = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
                    val fpTime = timeFormatter.format(Date(fp.timestamp))
                    val purpleColor = Color(0xFFA855F7)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = purpleColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, purpleColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🔮 ${if (isRu) "Прогноз" else "Forecast"} $fpTime (+${fp.minutesAhead}${if (isRu) "м" else "m"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "~$fpVal $fpUnit",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = purpleColor
                                )
                            }

                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { selectedForecastPoint = null }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                } else if (selectedReading != null) {
                    val sel = selectedReading!!
                    val selVal = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", sel.valueMmol)
                    else "${(sel.valueMmol * 18.0182).toInt()}"
                    val selUnit = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
                    val selTime = timeFormatter.format(Date(sel.timestamp))

                    val selColor = when {
                        sel.valueMmol < 3.0 -> ColorVeryLow
                        sel.valueMmol < 3.9 -> ColorLow
                        sel.valueMmol in 3.9..7.0 -> ColorTight
                        sel.valueMmol in 7.01..7.8 -> ColorTargetSoft
                        sel.valueMmol in 7.81..10.0 -> ColorTarget
                        sel.valueMmol in 10.01..13.9 -> ColorHigh
                        else -> ColorVeryHigh
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = selColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, selColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⏱ $selTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$selVal $selUnit ${sel.trendArrow}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = selColor
                                )
                            }

                            if ((sel.iob != null && sel.iob > 0.0) || (sel.cob != null && sel.cob > 0.0)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (sel.iob != null && sel.iob > 0.0) {
                                        Text(
                                            text = String.format(Locale.US, "💉 %.2f U", sel.iob),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ActionBlue
                                        )
                                    }
                                    if (sel.cob != null && sel.cob > 0.0) {
                                        Text(
                                            text = String.format(Locale.US, "🍞 %.0f g", sel.cob),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            // Interactive Chart Canvas with Pinch-to-Zoom and Horizontal Drag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceBg.copy(alpha = 0.5f))
                    .pointerInput(todayReadings, dataGaps, todayTreatments, insulinClusters, carbsClusters) {
                        detectTapGestures { tapOffset ->
                            // Find reading, treatment cluster, or gap closest to tap
                            val chartWidth = size.width - 70f // right margin for labels
                            if (chartWidth > 0 && (todayReadings.isNotEmpty() || insulinClusters.isNotEmpty() || carbsClusters.isNotEmpty())) {
                                val tapMinute = windowStartMinute + (tapOffset.x / chartWidth) * visibleMinutes
                                val toleranceMin = (visibleMinutes / 30f).coerceIn(6f, 30f)

                                val candidateClusters = (insulinClusters + carbsClusters).filter { cl ->
                                    val clMinute = (cl.timestamp - startOfDay) / 60000f
                                    abs(clMinute - tapMinute) <= toleranceMin
                                }

                                val tappedCluster = if (candidateClusters.isNotEmpty()) {
                                    val prefersInsulin = tapOffset.y < (size.height * 0.5f)
                                    candidateClusters.minByOrNull { cl ->
                                        val clMinute = (cl.timestamp - startOfDay) / 60000f
                                        val timeDiff = abs(clMinute - tapMinute)
                                        val yPenalty = if (cl.isInsulin == prefersInsulin) 0f else 15f
                                        timeDiff + yPenalty
                                    }
                                } else null

                                if (tappedCluster != null) {
                                    selectedTreatmentCluster = if (selectedTreatmentCluster == tappedCluster) null else tappedCluster
                                    selectedReading = null
                                    selectedGap = null
                                    selectedForecastPoint = null
                                } else {
                                    selectedTreatmentCluster = null
                                    val tappedGap = dataGaps.firstOrNull { gap ->
                                        tapMinute in (gap.startMinute - 4f)..(gap.endMinute + 4f)
                                    }

                                    if (tappedGap != null) {
                                        selectedGap = if (selectedGap == tappedGap) null else tappedGap
                                        selectedReading = null
                                        selectedForecastPoint = null
                                    } else {
                                        val closestFp = if (forecastPoints.isNotEmpty()) {
                                            forecastPoints.minByOrNull { fp ->
                                                val fMinute = (fp.timestamp - startOfDay) / 60000f
                                                abs(fMinute - tapMinute)
                                            }
                                        } else null

                                        val closestReading = todayReadings.minByOrNull { r ->
                                            val rMinute = (r.timestamp - startOfDay) / 60000f
                                            abs(rMinute - tapMinute)
                                        }

                                        val distFp = closestFp?.let { abs((it.timestamp - startOfDay) / 60000f - tapMinute) } ?: Float.MAX_VALUE
                                        val distR = closestReading?.let { abs((it.timestamp - startOfDay) / 60000f - tapMinute) } ?: Float.MAX_VALUE

                                        if (distFp < distR && distFp <= 12f) {
                                            selectedForecastPoint = if (selectedForecastPoint == closestFp) null else closestFp
                                            selectedReading = null
                                            selectedGap = null
                                            selectedTreatmentCluster = null
                                        } else {
                                            selectedReading = if (closestReading != null && selectedReading == closestReading) null else closestReading
                                            selectedForecastPoint = null
                                            selectedGap = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // 1. Zoom (pinch)
                            val newVisible = (visibleMinutes / zoom).coerceIn(120f, 1440f)
                            val chartWidth = (size.width - 70f).coerceAtLeast(10f)
                            val centroidRatio = (centroid.x / chartWidth).coerceIn(0f, 1f)
                            val centerMinute = windowStartMinute + centroidRatio * visibleMinutes
                            windowStartMinute = (centerMinute - centroidRatio * newVisible).coerceIn(0f, 1440f - newVisible)
                            visibleMinutes = newVisible

                            // 2. Pan (horizontal drag)
                            val minutesPerPx = visibleMinutes / chartWidth
                            windowStartMinute = (windowStartMinute - pan.x * minutesPerPx).coerceIn(0f, 1440f - visibleMinutes)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val chartRight = width - 64f // space for Y-axis numbers
                    val chartBottom = height - 22f // space for time labels
                    val chartTop = 10f
                    val chartHeight = chartBottom - chartTop

                    if (chartRight <= 0 || chartHeight <= 0) return@Canvas

                    // Y scale: max glucose value on screen (minimum 15.0 mmol/L)
                    val visibleMaxMmol = todayReadings.filter { r ->
                        val m = (r.timestamp - startOfDay) / 60000f
                        m in (windowStartMinute - 10f)..(windowStartMinute + visibleMinutes + 10f)
                    }.maxOfOrNull { it.valueMmol } ?: 10.0
                    val maxMmol = max(16.0, visibleMaxMmol + 1.5).toFloat()

                    fun yForMmol(mmol: Double): Float {
                        val clamped = mmol.coerceIn(0.0, maxMmol.toDouble()).toFloat()
                        return chartBottom - (clamped / maxMmol) * chartHeight
                    }

                    fun xForMinute(minute: Float): Float {
                        return ((minute - windowStartMinute) / visibleMinutes) * chartRight
                    }

                    // 1. Draw Target Range Band (3.9 - 10.0)
                    val yTirLow = yForMmol(targetRanges.tirLowMmol)
                    val yTirHigh = yForMmol(targetRanges.tirHighMmol)
                    drawRect(
                        color = PrimaryEmerald.copy(alpha = 0.08f),
                        topLeft = Offset(0f, yTirHigh),
                        size = Size(chartRight, (yTirLow - yTirHigh).coerceAtLeast(0f))
                    )

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    // Target range threshold lines
                    drawLine(
                        color = ColorLow.copy(alpha = 0.45f),
                        start = Offset(0f, yTirLow),
                        end = Offset(chartRight, yTirLow),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = ColorHigh.copy(alpha = 0.45f),
                        start = Offset(0f, yTirHigh),
                        end = Offset(chartRight, yTirHigh),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )

                    // 7.8 Tight range line (if visible)
                    val yTing = yForMmol(targetRanges.tingHighMmol)
                    drawLine(
                        color = ColorTargetSoft.copy(alpha = 0.25f),
                        start = Offset(0f, yTing),
                        end = Offset(chartRight, yTing),
                        strokeWidth = 1.0f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
                    )

                    // 2. Vertical Time Grid & Bottom Labels
                    val textPaint = Paint().apply {
                        color = onSurfaceVariant.toArgb()
                        textSize = 24f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT
                    }

                    val stepMinutes = when {
                        visibleMinutes <= 240f -> 60  // Every hour
                        visibleMinutes <= 600f -> 120 // Every 2 hours
                        visibleMinutes <= 1000f -> 180 // Every 3 hours
                        else -> 360                   // Every 6 hours
                    }

                    val firstGridMinute = ((windowStartMinute / stepMinutes).toInt() * stepMinutes)
                    for (m in firstGridMinute..(windowStartMinute + visibleMinutes).toInt() step stepMinutes) {
                        if (m in 0..1440) {
                            val x = xForMinute(m.toFloat())
                            if (x in 0f..chartRight) {
                                // Subtle vertical grid line
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.18f),
                                    start = Offset(x, chartTop),
                                    end = Offset(x, chartBottom),
                                    strokeWidth = 1.0f
                                )

                                // Time label: HH:00
                                val h = m / 60
                                val minPart = m % 60
                                val timeStr = String.format(Locale.US, "%02d:%02d", h, minPart)
                                val textWidth = textPaint.measureText(timeStr)
                                drawContext.canvas.nativeCanvas.drawText(
                                    timeStr,
                                    (x - textWidth / 2f).coerceIn(4f, chartRight - textWidth - 4f),
                                    height - 4f,
                                    textPaint
                                )
                            }
                        }
                    }

                    // 3. Current Time ("Now") vertical line
                    if (currentMinuteOfDay in windowStartMinute..(windowStartMinute + visibleMinutes)) {
                        val xNow = xForMinute(currentMinuteOfDay)
                        drawLine(
                            color = PrimaryEmerald,
                            start = Offset(xNow, chartTop),
                            end = Offset(xNow, chartBottom),
                            strokeWidth = 2.0f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                        )
                        drawCircle(
                            color = PrimaryEmerald,
                            radius = 3.5f,
                            center = Offset(xNow, chartTop + 4f)
                        )
                    }

                    // 4. Draw Glucose Trend Line & Points
                    val visibleReadings = todayReadings.filter { r ->
                        val m = (r.timestamp - startOfDay) / 60000f
                        m in (windowStartMinute - 20f)..(windowStartMinute + visibleMinutes + 20f)
                    }

                    if (visibleReadings.isNotEmpty()) {
                        // Draw continuous segments and dashed gap connections (>20 min)
                        val solidPath = Path()
                        var solidStarted = false
                        var prevReading: GlucoseReading? = null

                        visibleReadings.forEach { r ->
                            val m = (r.timestamp - startOfDay) / 60000f
                            val x = xForMinute(m)
                            val y = yForMmol(r.valueMmol)

                            if (prevReading != null) {
                                val prevM = (prevReading!!.timestamp - startOfDay) / 60000f
                                val prevX = xForMinute(prevM)
                                val prevY = yForMmol(prevReading!!.valueMmol)
                                val dtMs = r.timestamp - prevReading!!.timestamp

                                if (dtMs > 20 * 60_000L) {
                                    // Gap > 20 min: flush existing solid path
                                    if (solidStarted) {
                                        drawPath(
                                            path = solidPath,
                                            color = PrimaryEmerald.copy(alpha = 0.65f),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                        )
                                        solidPath.reset()
                                        solidStarted = false
                                    }

                                    // Draw dashed line for the gap
                                    drawLine(
                                        color = onSurfaceVariant.copy(alpha = 0.45f),
                                        start = Offset(prevX, prevY),
                                        end = Offset(x, y),
                                        strokeWidth = 2.0f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )

                                    // If gap is wide enough on screen, draw subtle badge in middle
                                    val midX = (prevX + x) / 2f
                                    val midY = (prevY + y) / 2f
                                    if (abs(x - prevX) >= 42f && midX in 0f..chartRight) {
                                        val durMin = (dtMs / 60_000L).toInt()
                                        val gapLabel = if (durMin >= 60) {
                                            val h = durMin / 60
                                            val minPart = durMin % 60
                                            if (minPart > 0) "${h}ч ${minPart}м" else "${h}ч"
                                        } else "${durMin}м"
                                        val gapPaint = Paint().apply {
                                            color = onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                                            textSize = 20f
                                            isAntiAlias = true
                                            textAlign = Paint.Align.CENTER
                                        }
                                        drawContext.canvas.nativeCanvas.drawText(
                                            "❓ $gapLabel",
                                            midX,
                                            midY - 8f,
                                            gapPaint
                                        )
                                    }
                                } else {
                                    if (!solidStarted) {
                                        solidPath.moveTo(prevX, prevY)
                                        solidStarted = true
                                    }
                                    solidPath.lineTo(x, y)
                                }
                            }
                            prevReading = r
                        }

                        if (solidStarted) {
                            drawPath(
                                path = solidPath,
                                color = PrimaryEmerald.copy(alpha = 0.65f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                            )
                        }

                        // Draw individual dots
                        val isDataStale = (now - visibleReadings.last().timestamp) > 5 * 60000L
                        val lastIndex = visibleReadings.lastIndex

                        visibleReadings.forEachIndexed { index, r ->
                            val m = (r.timestamp - startOfDay) / 60000f
                            val x = xForMinute(m)
                            val y = yForMmol(r.valueMmol)

                            val isLast = (index == lastIndex)
                            val dotColor = when {
                                isLast && isDataStale -> onSurfaceVariant.copy(alpha = 0.55f)
                                r.valueMmol < 3.0 -> ColorVeryLow
                                r.valueMmol < 3.9 -> ColorLow
                                r.valueMmol in 3.9..7.0 -> ColorTight
                                r.valueMmol in 7.01..7.8 -> ColorTargetSoft
                                r.valueMmol in 7.81..10.0 -> ColorTarget
                                r.valueMmol in 10.01..13.9 -> ColorHigh
                                else -> ColorVeryHigh
                            }

                            val isSelected = (selectedReading == r)
                            val radius = if (isSelected) 6.5f else if (visibleMinutes <= 360f) 3.8f else 2.5f

                            // Outer glow if selected
                            if (isSelected) {
                                drawCircle(
                                    color = dotColor.copy(alpha = 0.35f),
                                    radius = radius * 2f,
                                    center = Offset(x, y)
                                )
                            }

                            drawCircle(
                                color = dotColor,
                                radius = radius,
                                center = Offset(x, y)
                            )
                        }

                        // 4.0. Draw 25-Minute Trend Forecast (Purple dashed trajectory & dots)
                        if (forecastPoints.isNotEmpty()) {
                            val latestActual = visibleReadings.last()
                            val latestM = (latestActual.timestamp - startOfDay) / 60000f
                            val latestX = xForMinute(latestM)
                            val latestY = yForMmol(latestActual.valueMmol)

                            val forecastColor = Color(0xFFA855F7) // Purple
                            val forecastPath = Path()
                            forecastPath.moveTo(latestX, latestY)

                            forecastPoints.forEach { fp ->
                                val fm = (fp.timestamp - startOfDay) / 60000f
                                val fx = xForMinute(fm)
                                val fy = yForMmol(fp.valueMmol)
                                forecastPath.lineTo(fx, fy)
                            }

                            // Dashed purple trajectory line
                            drawPath(
                                path = forecastPath,
                                color = forecastColor.copy(alpha = 0.75f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.0f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            )

                            // Forecast dots (+5m, +10m, +15m, +20m, +25m)
                            val dotRadius = if (visibleMinutes <= 360f) 3.5f else 2.2f
                            forecastPoints.forEach { fp ->
                                val fm = (fp.timestamp - startOfDay) / 60000f
                                val fx = xForMinute(fm)
                                val fy = yForMmol(fp.valueMmol)

                                if (fx in -10f..(chartRight + 10f)) {
                                    val isFpSelected = (selectedForecastPoint == fp)
                                    val curRadius = if (isFpSelected) dotRadius * 1.5f else dotRadius

                                    // Subtle outer glow
                                    drawCircle(
                                        color = forecastColor.copy(alpha = if (isFpSelected) 0.45f else 0.22f),
                                        radius = curRadius * (if (isFpSelected) 2.2f else 1.7f),
                                        center = Offset(fx, fy)
                                    )
                                    // Main dot
                                    drawCircle(
                                        color = forecastColor.copy(alpha = 0.9f),
                                        radius = curRadius,
                                        center = Offset(fx, fy)
                                    )
                                }
                            }
                        }
                    }

                    // 4.1. Draw Treatments Overlay (Insulin 💉 and Carbs 🍽️)
                    val insulinPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 20f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                    val carbsPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 20f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }

                    // 4.1.1. Draw Insulin Clusters (Top Area with Staggering)
                    val visibleInsulinClusters = insulinClusters.filter { cl ->
                        val m = (cl.timestamp - startOfDay) / 60000f
                        m in (windowStartMinute - 15f)..(windowStartMinute + visibleMinutes + 15f)
                    }

                    var lastInsulinRight0 = -Float.MAX_VALUE
                    var lastInsulinRight1 = -Float.MAX_VALUE

                    visibleInsulinClusters.forEach { cluster ->
                        val m = (cluster.timestamp - startOfDay) / 60000f
                        val x = xForMinute(m)

                        if (x in -20f..(chartRight + 20f)) {
                            val isSelected = (selectedTreatmentCluster == cluster)
                            val insText = cluster.displayText
                            val textW = insulinPaint.measureText(insText)
                            val badgeW = textW + 16f
                            val badgeH = 22f
                            val badgeLeft = (x - badgeW / 2f).coerceIn(2f, chartRight - badgeW - 2f)

                            // Multi-tier staggering: choose Level 0 or Level 1 to prevent overlapping
                            val margin = 4f
                            val level = when {
                                badgeLeft >= lastInsulinRight0 + margin -> 0
                                badgeLeft >= lastInsulinRight1 + margin -> 1
                                else -> if (lastInsulinRight0 <= lastInsulinRight1) 0 else 1
                            }
                            if (level == 0) {
                                lastInsulinRight0 = badgeLeft + badgeW
                            } else {
                                lastInsulinRight1 = badgeLeft + badgeW
                            }

                            val badgeTop = chartTop + 6f + (level * 24f)

                            // Vertical dashed guideline connecting pin down through glucose chart
                            if (level > 0) {
                                drawLine(
                                    color = ActionBlue.copy(alpha = if (isSelected) 0.85f else 0.4f),
                                    start = Offset(x, chartTop),
                                    end = Offset(x, badgeTop),
                                    strokeWidth = if (isSelected) 2.5f else 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                            }
                            drawLine(
                                color = ActionBlue.copy(alpha = if (isSelected) 0.85f else 0.4f),
                                start = Offset(x, badgeTop + badgeH),
                                end = Offset(x, chartBottom),
                                strokeWidth = if (isSelected) 2.5f else 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            )

                            // Glow if selected
                            if (isSelected) {
                                drawRoundRect(
                                    color = ActionBlue.copy(alpha = 0.35f),
                                    topLeft = Offset(badgeLeft - 3f, badgeTop - 3f),
                                    size = Size(badgeW + 6f, badgeH + 6f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                )
                            }

                            // Insulin Badge Background
                            drawRoundRect(
                                color = ActionBlue,
                                topLeft = Offset(badgeLeft, badgeTop),
                                size = Size(badgeW, badgeH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )

                            // Insulin Text
                            drawContext.canvas.nativeCanvas.drawText(
                                insText,
                                badgeLeft + badgeW / 2f,
                                badgeTop + 16.5f,
                                insulinPaint
                            )
                        }
                    }

                    // 4.1.2. Draw Carbs Clusters (Bottom Area with Staggering)
                    val visibleCarbsClusters = carbsClusters.filter { cl ->
                        val m = (cl.timestamp - startOfDay) / 60000f
                        m in (windowStartMinute - 15f)..(windowStartMinute + visibleMinutes + 15f)
                    }

                    var lastCarbsRight0 = -Float.MAX_VALUE
                    var lastCarbsRight1 = -Float.MAX_VALUE

                    visibleCarbsClusters.forEach { cluster ->
                        val m = (cluster.timestamp - startOfDay) / 60000f
                        val x = xForMinute(m)

                        if (x in -20f..(chartRight + 20f)) {
                            val isSelected = (selectedTreatmentCluster == cluster)
                            val carbsText = cluster.displayText
                            val textW = carbsPaint.measureText(carbsText)
                            val badgeW = textW + 16f
                            val badgeH = 22f
                            val badgeLeft = (x - badgeW / 2f).coerceIn(2f, chartRight - badgeW - 2f)

                            // Multi-tier staggering: choose Level 0 or Level 1 to prevent overlapping
                            val margin = 4f
                            val level = when {
                                badgeLeft >= lastCarbsRight0 + margin -> 0
                                badgeLeft >= lastCarbsRight1 + margin -> 1
                                else -> if (lastCarbsRight0 <= lastCarbsRight1) 0 else 1
                            }
                            if (level == 0) {
                                lastCarbsRight0 = badgeLeft + badgeW
                            } else {
                                lastCarbsRight1 = badgeLeft + badgeW
                            }

                            val badgeTop = chartBottom - 26f - (level * 24f)

                            // Vertical dashed guideline up if no insulin at this same time
                            val hasInsulinAtSameTime = visibleInsulinClusters.any {
                                val insM = (it.timestamp - startOfDay) / 60000f
                                abs(xForMinute(insM) - x) < 4f
                            }

                            if (!hasInsulinAtSameTime) {
                                drawLine(
                                    color = Color(0xFFF59E0B).copy(alpha = if (isSelected) 0.85f else 0.4f),
                                    start = Offset(x, chartTop + 10f),
                                    end = Offset(x, badgeTop),
                                    strokeWidth = if (isSelected) 2.5f else 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                                if (level > 0) {
                                    drawLine(
                                        color = Color(0xFFF59E0B).copy(alpha = if (isSelected) 0.85f else 0.4f),
                                        start = Offset(x, badgeTop + badgeH),
                                        end = Offset(x, chartBottom),
                                        strokeWidth = if (isSelected) 2.5f else 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                    )
                                }
                            }

                            // Glow if selected
                            if (isSelected) {
                                drawRoundRect(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.35f),
                                    topLeft = Offset(badgeLeft - 3f, badgeTop - 3f),
                                    size = Size(badgeW + 6f, badgeH + 6f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                )
                            }

                            // Carbs Badge Background
                            drawRoundRect(
                                color = Color(0xFFF59E0B),
                                topLeft = Offset(badgeLeft, badgeTop),
                                size = Size(badgeW, badgeH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )

                            // Carbs Text
                            drawContext.canvas.nativeCanvas.drawText(
                                carbsText,
                                badgeLeft + badgeW / 2f,
                                badgeTop + 16.5f,
                                carbsPaint
                            )
                        }
                    }

                    // 5. Y-Axis Value Labels on the right side
                    val yLabelPaint = Paint().apply {
                        color = onSurfaceVariant.copy(alpha = 0.8f).toArgb()
                        textSize = 22f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                    }

                    val targetsToDraw = listOf(
                        Pair(targetRanges.tirLowMmol, ColorLow),
                        Pair(targetRanges.tingHighMmol, ColorTargetSoft),
                        Pair(targetRanges.tirHighMmol, ColorHigh),
                        Pair(maxMmol.toDouble() * 0.9, onSurfaceVariant)
                    )

                    targetsToDraw.forEach { (mmol, col) ->
                        val y = yForMmol(mmol)
                        val labelText = if (unit == GlucoseUnit.MMOL_L) {
                            String.format(Locale.US, "%.1f", mmol)
                        } else {
                            "${(mmol * 18.0182).toInt()}"
                        }
                        yLabelPaint.color = col.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            chartRight + 8f,
                            y + 8f,
                            yLabelPaint
                        )
                    }
                }
            }

            // Sensor Activity and Gap Status Banner
            if (todayReadings.isNotEmpty() && totalGapMinutes > 0) {
                val gapDurationStr = if (totalGapMinutes >= 60) {
                    val h = totalGapMinutes / 60
                    val m = totalGapMinutes % 60
                    if (isRu) {
                        if (m > 0) "${h}ч ${m}м" else "${h}ч"
                    } else {
                        if (m > 0) "${h}h ${m}m" else "${h}h"
                    }
                } else {
                    if (isRu) "${totalGapMinutes} мин" else "${totalGapMinutes}m"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRu) "📡 Работа сенсора: $sensorActivePercent%" else "📡 Sensor Working: $sensorActivePercent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (sensorActivePercent >= 70) PrimaryEmerald else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "(разрывы: $gapDurationStr)" else "(gaps: $gapDurationStr)",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    if (sensorActivePercent < 70) {
                        Text(
                            text = if (isRu) "⚠️ <70% (мало данных)" else "⚠️ <70% (insufficient data)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            }
            }
        }
    }
}
