package com.tirup.app.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
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
    padding: Dp = 18.dp,
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
        Box(modifier = Modifier.padding(padding)) {
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
    var showNumber by remember { mutableStateOf(false) }

    LaunchedEffect(streakDays) {
        if (streakDays > 0) {
            while (true) {
                delay(3500L)
                showNumber = !showNumber
            }
        } else {
            showNumber = false
        }
    }

    val isDark = isSystemInDarkTheme()
    val amberBase = Color(0xFFF59E0B) // Amber
    val amberBg = amberBase.copy(alpha = if (isDark) 0.25f else 0.18f)
    val amberBorder = amberBase.copy(alpha = if (isDark) 0.65f else 0.70f)
    val amberText = if (isDark) Color.White else Color.Black // Crisp white in dark theme, pure black in light theme

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (streakDays > 0) amberBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.2.dp,
            if (streakDays > 0) amberBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .widthIn(min = 34.dp)
                .height(30.dp)
                .padding(horizontal = 8.dp)
        ) {
            if (streakDays == 0) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                AnimatedContent(
                    targetState = showNumber,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(380)) { height -> height } + fadeIn(animationSpec = tween(380)))
                            .togetherWith(
                                slideOutVertically(animationSpec = tween(380)) { height -> -height } + fadeOut(animationSpec = tween(380))
                            )
                    },
                    label = "streakBadgeSlideUpAnim"
                ) { isNumber ->
                    if (isNumber) {
                        Text(
                            text = "$streakDays",
                            color = amberText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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

@Composable
fun BentoMetricCompact(
    title: String,
    value: String,
    @Suppress("UNUSED_PARAMETER") unit: String = "",
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        padding = 8.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
