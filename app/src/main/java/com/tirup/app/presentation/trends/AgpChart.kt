package com.tirup.app.presentation.trends

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun AgpChart(
    bins: List<AGPPercentileBin>,
    targetRanges: TargetRanges,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    maxMmol: Float = 16f
) {
    val bgCol = MaterialTheme.colorScheme.surfaceVariant
    val textCol = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridCol = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgCol)
            .padding(top = 12.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val chartBottom = height - 22f
            val chartTop = 8f
            val chartHeight = chartBottom - chartTop

            if (bins.isEmpty()) return@Canvas

            fun yForMmol(mmol: Double): Float {
                val clamped = mmol.coerceIn(0.0, maxMmol.toDouble()).toFloat()
                return chartBottom - (clamped / maxMmol) * chartHeight
            }

            fun xForBin(index: Int): Float {
                return (index.toFloat() / (bins.size - 1).coerceAtLeast(1)) * width
            }

            // Draw target range band (3.9 - 10.0 mmol/L)
            val yTirLow = yForMmol(targetRanges.tirLowMmol)
            val yTirHigh = yForMmol(targetRanges.tirHighMmol)
            val yTingHigh = yForMmol(targetRanges.tingHighMmol)

            // Target background rect
            drawRect(
                color = PrimaryEmerald.copy(alpha = 0.08f),
                topLeft = Offset(0f, yTirHigh),
                size = Size(width, yTirLow - yTirHigh)
            )

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            // Horizontal target guides
            drawLine(
                color = ColorLow.copy(alpha = 0.4f),
                start = Offset(0f, yTirLow),
                end = Offset(width, yTirLow),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )

            drawLine(
                color = ColorLow.copy(alpha = 0.4f),
                start = Offset(0f, yTirHigh),
                end = Offset(width, yTirHigh),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )

            drawLine(
                color = ColorTight.copy(alpha = 0.3f),
                start = Offset(0f, yTingHigh),
                end = Offset(width, yTingHigh),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )

            // Vertical 3-Hour Perpendiculars (00:00 - 24:00)
            val hours = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
            hours.forEach { hr ->
                val xPos = (hr.toFloat() / 24f) * width
                drawLine(
                    color = gridCol,
                    start = Offset(xPos, chartTop),
                    end = Offset(xPos, chartBottom),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
            }

            // Filter valid bins with readings
            val validBins = bins.filter { it.readingsCount > 0 }
            if (validBins.size >= 2) {
                // 1. Draw 10-90th percentile outer cloud
                val path1090 = Path()
                path1090.moveTo(xForBin(validBins.first().binIndex), yForMmol(validBins.first().p90))
                validBins.forEach { bin ->
                    path1090.lineTo(xForBin(bin.binIndex), yForMmol(bin.p90))
                }
                validBins.reversed().forEach { bin ->
                    path1090.lineTo(xForBin(bin.binIndex), yForMmol(bin.p10))
                }
                path1090.close()
                drawPath(path1090, color = PrimaryEmerald.copy(alpha = 0.12f), style = Fill)

                // 2. Draw 25-75th percentile interquartile band
                val path2575 = Path()
                path2575.moveTo(xForBin(validBins.first().binIndex), yForMmol(validBins.first().p75))
                validBins.forEach { bin ->
                    path2575.lineTo(xForBin(bin.binIndex), yForMmol(bin.p75))
                }
                validBins.reversed().forEach { bin ->
                    path2575.lineTo(xForBin(bin.binIndex), yForMmol(bin.p25))
                }
                path2575.close()
                drawPath(path2575, color = PrimaryEmerald.copy(alpha = 0.28f), style = Fill)

                // 3. Draw 50th percentile (Median curve)
                val path50 = Path()
                var started = false
                validBins.forEach { bin ->
                    val x = xForBin(bin.binIndex)
                    val y = yForMmol(bin.p50)
                    if (!started) {
                        path50.moveTo(x, y)
                        started = true
                    } else {
                        path50.lineTo(x, y)
                    }
                }
                drawPath(path50, color = PrimaryEmerald, style = Stroke(width = 4f))
            }

            // Draw Native Text Labels: X-axis Hours & Y-axis Targets
            val labelPaint = Paint().apply {
                isAntiAlias = true
                textSize = 24f
                color = textCol
            }

            val hourLabels = listOf("00", "03", "06", "09", "12", "15", "18", "21", "24")
            hourLabels.forEachIndexed { idx, lbl ->
                val xPos = (idx.toFloat() / 8f) * width
                val textX = (xPos - 14f).coerceIn(2f, width - 28f)
                val textY = height - 4f
                drawContext.canvas.nativeCanvas.drawText(lbl, textX, textY, labelPaint)
            }

            // Y-axis target labels
            val highLabel = if (unit == GlucoseUnit.MMOL_L) "10.0" else "180"
            val lowLabel = if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"
            drawContext.canvas.nativeCanvas.drawText(highLabel, 6f, yTirHigh - 4f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText(lowLabel, 6f, yTirLow - 4f, labelPaint)
        }
    }
}
