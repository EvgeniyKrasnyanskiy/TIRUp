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

/**
 * Interactive 24-hour daily glucose chart for the Focus screen.
 * Supports:
 * - Horizontal pinch-to-zoom (from 2h up to 24h window)
 * - Horizontal drag/pan scrolling
 * - Tap on readings to inspect exact value, timestamp, delta, and IoB
 * - Clinical target corridor (3.9 - 10.0 mmol/L / 70 - 180 mg/dL)
 * - Real-time "Now" indicator line
 * - Tab toggle between [📊 График] and [🔢 Параметры]
 */
@Composable
fun DailyGlucoseChart(
    readings: List<GlucoseReading>,
    targetRanges: TargetRanges,
    unit: GlucoseUnit,
    isRu: Boolean,
    modifier: Modifier = Modifier,
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

    var visibleMinutes by remember { mutableFloatStateOf(360f) }
    var windowStartMinute by remember {
        mutableFloatStateOf((currentMinuteOfDay - 300f).coerceIn(0f, (1440f - 360f).coerceAtLeast(0f)))
    }

    var selectedReading by remember { mutableStateOf<GlucoseReading?>(null) }
    var selectedGap by remember { mutableStateOf<DataGap?>(null) }

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
                // Selected Gap or Reading Inspector Banner
                if (selectedGap != null) {
                    val gap = selectedGap!!
                    val startTime = timeFormatter.format(Date(gap.startTimestamp))
                    val endTime = timeFormatter.format(Date(gap.endTimestamp))
                    val durText = if (gap.durationMinutes >= 60) {
                        val h = gap.durationMinutes / 60
                        val m = gap.durationMinutes % 60
                        if (m > 0) "${h}ч ${m}м" else "${h}ч"
                    } else "${gap.durationMinutes}м"

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
                                    text = if (isRu) "⚠️ Разрыв связи: $durText" else "⚠️ Signal Gap: $durText",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
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

                            if (sel.iob != null && sel.iob > 0.0) {
                                Text(
                                    text = String.format(Locale.US, "💉 %.2f U", sel.iob),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionBlue
                                )
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
                    .pointerInput(todayReadings, dataGaps) {
                        detectTapGestures { tapOffset ->
                            // Find reading or gap closest to tap
                            val chartWidth = size.width - 70f // right margin for labels
                            if (chartWidth > 0 && todayReadings.isNotEmpty()) {
                                val tapMinute = windowStartMinute + (tapOffset.x / chartWidth) * visibleMinutes

                                val tappedGap = dataGaps.firstOrNull { gap ->
                                    tapMinute in (gap.startMinute - 4f)..(gap.endMinute + 4f)
                                }

                                if (tappedGap != null) {
                                    selectedGap = if (selectedGap == tappedGap) null else tappedGap
                                    selectedReading = null
                                } else {
                                    val closest = todayReadings.minByOrNull { r ->
                                        val rMinute = (r.timestamp - startOfDay) / 60000f
                                        abs(rMinute - tapMinute)
                                    }
                                    selectedReading = if (closest != null && selectedReading == closest) null else closest
                                    selectedGap = null
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
                    if (m > 0) "${h}ч ${m}м" else "${h}ч"
                } else "${totalGapMinutes} мин"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRu) "📡 Активность сенсора: $sensorActivePercent%" else "📡 Sensor Active: $sensorActivePercent%",
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
                            text = if (isRu) "⚠️ <70% (TIR приблиз.)" else "⚠️ <70% (TIR approx.)",
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
