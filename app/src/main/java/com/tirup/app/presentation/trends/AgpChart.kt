package com.tirup.app.presentation.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun AgpChart(
    bins: List<AGPPercentileBin>,
    targetRanges: TargetRanges,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    maxMmol: Float = 16f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceElevated)
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            if (bins.isEmpty()) return@Canvas

            fun yForMmol(mmol: Double): Float {
                val clamped = mmol.coerceIn(0.0, maxMmol.toDouble()).toFloat()
                return height - (clamped / maxMmol) * height
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
                color = PrimaryEmerald.copy(alpha = 0.07f),
                topLeft = Offset(0f, yTirHigh),
                size = androidx.compose.ui.geometry.Size(width, yTirLow - yTirHigh)
            )

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

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

            // Vertical 3-Hour Perpendiculars (Grid: 00, 03, 06, 09, 12, 15, 18, 21, 24)
            val vDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            val hours = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
            hours.forEach { hr ->
                val xPos = (hr.toFloat() / 24f) * width
                drawLine(
                    color = DarkBorder.copy(alpha = 0.7f),
                    start = Offset(xPos, 0f),
                    end = Offset(xPos, height),
                    strokeWidth = 1f,
                    pathEffect = vDashEffect
                )
            }

            // Filter valid bins with readings
            val validBins = bins.filter { it.readingsCount > 0 }
            if (validBins.size < 2) return@Canvas

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
            drawPath(
                path50,
                color = PrimaryEmerald,
                style = Stroke(width = 3.5f)
            )
        }
    }
}
