package com.tirup.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.GlucoseRangeCategory
import com.tirup.app.domain.model.TargetRanges

// Modern Youth Aesthetic Colors
val PrimaryEmerald = Color(0xFF10B981)
val PrimaryEmeraldDark = Color(0xFF059669)
val SecondaryTeal = Color(0xFF14B8A6)
val AccentCyan = Color(0xFF06B6D4)
val ActionBlue = Color(0xFF0284C7)

// Range Hex Values (consistent across Android Views, Canvas & Web)
const val HexColorVeryLow = "#EF4444"
const val HexColorLow = "#F59E0B"
const val HexColorTight = "#4ADE80"
const val HexColorTarget = "#10B981"
const val HexColorHigh = "#F59E0B"
const val HexColorVeryHigh = "#EF4444"

// Range Colors (consistent across themes, with light-theme accessible variants)
val ColorVeryLow = Color(0xFFEF4444)      // Urgent Red < 3.0
val ColorLow = Color(0xFFF59E0B)          // Warning Amber 3.0 - 3.8
val ColorTight = Color(0xFF4ADE80)        // Bright Pale Green (Dark Theme) 3.9 - 7.8
val ColorTightLight = Color(0xFF15803D)   // High-contrast Deep Emerald Green for Light Theme (WCAG AA > 4.5:1)
val ColorTargetSoft = Color(0xFF4ADE80)   // Pale Green 3.9 - 7.8
val ColorTarget = Color(0xFF10B981)       // Saturated Green / Emerald 7.9 - 10.0
val ColorTargetLight = Color(0xFF047857)  // High-contrast Deep Emerald for Light Theme
val ColorHigh = Color(0xFFF59E0B)         // Warning Amber 10.1 - 13.9
val ColorVeryHigh = Color(0xFFEF4444)     // Urgent Red >= 14.0

/**
 * Returns unified Compose Color for given glucose value in mmol/L strictly conforming to the 6-band clinical AGP/TIR standard:
 * < 3.0: ColorVeryLow (#EF4444)
 * 3.0..3.8: ColorLow (#F59E0B / #B45309 in light)
 * 3.9..7.8: ColorTight (#4ADE80 in dark / #15803D in light)
 * 7.9..10.0: ColorTarget (#10B981 in dark / #047857 in light)
 * 10.1..13.9: ColorHigh (#F59E0B / #B45309 in light)
 * >= 14.0: ColorVeryHigh (#EF4444)
 */
fun getGlucoseColor(valueMmol: Double, ranges: TargetRanges = TargetRanges(), isDark: Boolean = true): Color {
    if (valueMmol <= 0.0) return if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    return when (ranges.categorize(valueMmol)) {
        GlucoseRangeCategory.VERY_LOW -> ColorVeryLow
        GlucoseRangeCategory.LOW -> if (isDark) ColorLow else Color(0xFFB45309)
        GlucoseRangeCategory.TIGHT -> if (isDark) ColorTight else ColorTightLight
        GlucoseRangeCategory.TARGET -> if (isDark) ColorTarget else ColorTargetLight
        GlucoseRangeCategory.HIGH -> if (isDark) ColorHigh else Color(0xFFB45309)
        GlucoseRangeCategory.VERY_HIGH -> ColorVeryHigh
    }
}

/**
 * Returns Android graphics Color Int (e.g. for RemoteViews, Paint, Drawables).
 */
fun getGlucoseColorInt(valueMmol: Double, ranges: TargetRanges = TargetRanges()): Int {
    if (valueMmol <= 0.0) return 0xFF94A3B8.toInt()
    return when (ranges.categorize(valueMmol)) {
        GlucoseRangeCategory.VERY_LOW -> 0xFFEF4444.toInt()
        GlucoseRangeCategory.LOW -> 0xFFF59E0B.toInt()
        GlucoseRangeCategory.TIGHT -> 0xFF4ADE80.toInt()
        GlucoseRangeCategory.TARGET -> 0xFF10B981.toInt()
        GlucoseRangeCategory.HIGH -> 0xFFF59E0B.toInt()
        GlucoseRangeCategory.VERY_HIGH -> 0xFFEF4444.toInt()
    }
}

/**
 * Returns Hex color string (e.g. for Color.parseColor).
 */
fun getGlucoseColorHex(valueMmol: Double, ranges: TargetRanges = TargetRanges()): String {
    if (valueMmol <= 0.0) return "#94A3B8"
    return when (ranges.categorize(valueMmol)) {
        GlucoseRangeCategory.VERY_LOW -> HexColorVeryLow
        GlucoseRangeCategory.LOW -> HexColorLow
        GlucoseRangeCategory.TIGHT -> HexColorTight
        GlucoseRangeCategory.TARGET -> HexColorTarget
        GlucoseRangeCategory.HIGH -> HexColorHigh
        GlucoseRangeCategory.VERY_HIGH -> HexColorVeryHigh
    }
}

// Dark Palette
val DarkBg = Color(0xFF0B0F17)
val DarkSurface = Color(0xFF161E2E)
val DarkSurfaceElevated = Color(0xFF1E293B)
val DarkBorder = Color(0xFF334155)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFFE2E8F0) // High-contrast crisp silver-white
val TextMutedDark = Color(0xFFCBD5E1)

// Light Palette
val LightBg = Color(0xFFF1F5F9)          // Clean slate background
val LightSurface = Color(0xFFFFFFFF)     // Crisp white cards
val LightSurfaceElevated = Color(0xFFE2E8F0)
val LightBorder = Color(0xFFCBD5E1)
val TextPrimaryLight = Color(0xFF0F172A)  // Deep slate for high contrast
val TextSecondaryLight = Color(0xFF1E293B) // High-contrast deep slate
val TextMutedLight = Color(0xFF334155)

private val DarkColorScheme = darkColorScheme(
    primary = ActionBlue,
    onPrimary = Color.White,
    secondary = SecondaryTeal,
    onSecondary = Color.Black,
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = Color.White,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder
)

// Bento Grid Shape: smooth 24.dp rounded corners
val TirupShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val TirupTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun TIRUpTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TirupTypography,
        shapes = TirupShapes,
        content = content
    )
}
