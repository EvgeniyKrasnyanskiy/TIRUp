package com.tirup.app.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTarget
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .animateContentSize()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else Modifier
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun GlucoseValueFormatted(
    valueMmol: Double,
    unit: GlucoseUnit,
    fontSize: Int = 36
) {
    val displayValue = if (unit == GlucoseUnit.MMOL_L) {
        String.format(Locale.US, "%.1f", valueMmol)
    } else {
        String.format(Locale.US, "%d", (valueMmol * 18.0182).toInt())
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = displayValue,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = unit.label,
            fontSize = (fontSize / 2.5).toInt().sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = (fontSize / 7).dp)
        )
    }
}

@Composable
fun RangeCategoryColor(category: GlucoseRangeCategory): Color {
    return when (category) {
        GlucoseRangeCategory.VERY_LOW -> ColorVeryLow
        GlucoseRangeCategory.LOW -> ColorLow
        GlucoseRangeCategory.TIGHT -> ColorTight
        GlucoseRangeCategory.TARGET -> ColorTarget
        GlucoseRangeCategory.HIGH -> ColorHigh
        GlucoseRangeCategory.VERY_HIGH -> ColorVeryHigh
    }
}

@Composable
fun StreakBadge(
    streakDays: Int,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PrimaryEmerald.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = PrimaryEmerald,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.streak_days, streakDays),
                color = PrimaryEmerald,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun RangeDistributionBar(
    tbrVeryLow: Double,
    tbrLow: Double,
    tir: Double,
    tarHigh: Double,
    tarVeryHigh: Double,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp
) {
    val total = (tbrVeryLow + tbrLow + tir + tarHigh + tarVeryHigh).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (tbrVeryLow > 0) {
                Box(
                    modifier = Modifier
                        .weight((tbrVeryLow / total).toFloat().coerceAtLeast(0.001f))
                        .height(height)
                        .background(ColorVeryLow)
                )
            }
            if (tbrLow > 0) {
                Box(
                    modifier = Modifier
                        .weight((tbrLow / total).toFloat().coerceAtLeast(0.001f))
                        .height(height)
                        .background(ColorLow)
                )
            }
            if (tir > 0) {
                Box(
                    modifier = Modifier
                        .weight((tir / total).toFloat().coerceAtLeast(0.001f))
                        .height(height)
                        .background(ColorTight)
                )
            }
            if (tarHigh > 0) {
                Box(
                    modifier = Modifier
                        .weight((tarHigh / total).toFloat().coerceAtLeast(0.001f))
                        .height(height)
                        .background(ColorHigh)
                )
            }
            if (tarVeryHigh > 0) {
                Box(
                    modifier = Modifier
                        .weight((tarVeryHigh / total).toFloat().coerceAtLeast(0.001f))
                        .height(height)
                        .background(ColorVeryHigh)
                )
            }
        }
    }
}
