package com.tirup.app.presentation.aod

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.AodDisplayMode
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun AodScreen(
    settingsFlow: kotlinx.coroutines.flow.Flow<com.tirup.app.domain.model.UserSettings>,
    latestReadingFlow: kotlinx.coroutines.flow.Flow<com.tirup.app.domain.model.GlucoseReading?>,
    recentReadingsFlow: kotlinx.coroutines.flow.Flow<List<com.tirup.app.domain.model.GlucoseReading>>,
    onSetWindowBrightness: (Float) -> Unit,
    onExit: () -> Unit
) {
    val settings by settingsFlow.collectAsState(initial = com.tirup.app.domain.model.UserSettings())
    val reading by latestReadingFlow.collectAsState(initial = null)
    val recentReadings by recentReadingsFlow.collectAsState(initial = emptyList())
    val aod = settings.aodSettings
    val unit = settings.unit
    val ranges = settings.targetRanges

    val sorted = remember(recentReadings) { recentReadings.sortedBy { it.timestamp } }
    val delta5Min = remember(reading, sorted) {
        if (reading == null || sorted.size < 2) null
        else {
            val targetTime = reading!!.timestamp - 5 * 60_000L
            val candidate = sorted
                .filter { it.timestamp in (targetTime - 120_000L)..(targetTime + 120_000L) && it.timestamp != reading!!.timestamp }
                .minByOrNull { kotlin.math.abs(it.timestamp - targetTime) }
            val reference = candidate ?: sorted.filter { it.timestamp < reading!!.timestamp }.maxByOrNull { it.timestamp }
            if (reference != null) {
                reading!!.valueMmol - reference.valueMmol
            } else null
        }
    }

    // Flashlight state (Double-tap initiates 15s smooth increase to 100% white screen)
    var isFlashlightActive by remember { mutableStateOf(false) }
    var flashlightTargetAlpha by remember { mutableFloatStateOf(0f) }
    val animatedFlashlightAlpha by animateFloatAsState(
        targetValue = flashlightTargetAlpha,
        animationSpec = tween(
            durationMillis = if (flashlightTargetAlpha > 0f) 15_000 else 300,
            easing = LinearEasing
        ),
        label = "FlashlightRamp"
    )

    // Pulse awake timer state for PULSE_ON_UPDATE mode
    var isAwake by remember { mutableStateOf(aod.displayMode == AodDisplayMode.ALWAYS_ON) }
    var lastAwakeTriggerTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Anti-Burn-In pixel jitter offsets (-24dp .. +24dp)
    var jitterOffsetX by remember { mutableIntStateOf(0) }
    var jitterOffsetY by remember { mutableIntStateOf(0) }

    fun shiftAntiBurnIn() {
        jitterOffsetX = Random.nextInt(-28, 29)
        jitterOffsetY = Random.nextInt(-36, 37)
    }

    // React to new incoming glucose readings: wake screen and jitter position
    LaunchedEffect(reading?.timestamp) {
        if (reading != null) {
            shiftAntiBurnIn()
            lastAwakeTriggerTimestamp = System.currentTimeMillis()
            isAwake = true
        }
    }

    // Pulse timer countdown: screen sleeps after pulseDurationSeconds in PULSE_ON_UPDATE mode
    LaunchedEffect(lastAwakeTriggerTimestamp, aod.displayMode, isFlashlightActive) {
        if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE && !isFlashlightActive) {
            val durationMs = aod.pulseDurationSeconds.coerceAtLeast(3) * 1000L
            delay(durationMs)
            isAwake = false
            onSetWindowBrightness(0.001f) // Ultra-dim sleeping window
        } else if (aod.displayMode == AodDisplayMode.ALWAYS_ON && !isFlashlightActive) {
            isAwake = true
            onSetWindowBrightness(0.01f)
        }
    }

    // Periodic Anti-Burn-In repositioning every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            shiftAntiBurnIn()
        }
    }

    // Sync window brightness with flashlight progression
    LaunchedEffect(animatedFlashlightAlpha, isFlashlightActive) {
        if (isFlashlightActive) {
            // Brightness follows ramp from 0.05f to 1.0f
            val b = 0.05f + (0.95f * animatedFlashlightAlpha)
            onSetWindowBrightness(b)
        } else {
            if (isAwake || aod.displayMode == AodDisplayMode.ALWAYS_ON) {
                onSetWindowBrightness(0.01f)
            } else {
                onSetWindowBrightness(0.001f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture detector: tap to wake, double tap for flashlight, swipe to exit
            .pointerInput(aod.displayMode, isFlashlightActive) {
                detectTapGestures(
                    onTap = {
                        if (isFlashlightActive) {
                            // Tap immediately switches off flashlight
                            isFlashlightActive = false
                            flashlightTargetAlpha = 0f
                        } else {
                            // Single tap wakes up the screen for pulse duration
                            lastAwakeTriggerTimestamp = System.currentTimeMillis()
                            isAwake = true
                            onSetWindowBrightness(0.01f)
                        }
                    },
                    onDoubleTap = {
                        // Double tap: toggle smooth 15-second flashlight ramp
                        if (!isFlashlightActive) {
                            isFlashlightActive = true
                            flashlightTargetAlpha = 1.0f
                        } else {
                            isFlashlightActive = false
                            flashlightTargetAlpha = 0f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        // Any swipe gesture exceeding 80px triggers exit back to app
                        if (abs(totalDragX) > 80f || abs(totalDragY) > 80f) {
                            onExit()
                        }
                    }
                )
            }
    ) {
        // -------------------------------------------------------------
        // 1. Flashlight Overlay: Smooth warm white ramp (0 -> 100% in 15s)
        // -------------------------------------------------------------
        if (animatedFlashlightAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFBEB).copy(alpha = animatedFlashlightAlpha))
            ) {
                // Flashlight hint & quick close icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Фонарик (нажмите для выключения)",
                        color = Color.Black.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = {
                            isFlashlightActive = false
                            flashlightTargetAlpha = 0f
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Off",
                            tint = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. AOD Glucose Display Content (Visible when isAwake or ALWAYS_ON)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = (isAwake || aod.displayMode == AodDisplayMode.ALWAYS_ON) && !isFlashlightActive,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top Bar: Exit button & Current Clock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, start = 24.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val currentTimeStr = timeFormat.format(Date())
                    Text(
                        text = currentTimeStr,
                        color = Color(0xFF555555),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit AOD",
                            tint = Color(0xFF444444)
                        )
                    }
                }

                // Center Jitter Area (Anti-Burn-In) with Huge Glucose & Trend
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(jitterOffsetX.dp.roundToPx(), jitterOffsetY.dp.roundToPx()) }
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val r = reading
                    val glucoseValMmol = r?.valueMmol ?: 0.0

                    // Color mapping: muted soft tones for night vision & OLED safety
                    val glucoseColor = when {
                        glucoseValMmol <= 0.0 -> Color(0xFF555555)
                        glucoseValMmol < ranges.veryLowThresholdMmol -> Color(0xFF991B1B) // Muted deep red
                        glucoseValMmol < ranges.tirLowMmol -> Color(0xFFB45309)         // Muted amber
                        glucoseValMmol > ranges.veryHighThresholdMmol -> Color(0xFF9A3412) // Muted orange-red
                        glucoseValMmol > ranges.tirHighMmol -> Color(0xFFB45309)        // Muted amber
                        else -> Color(0xFF047857)                                      // Muted emerald green
                    }

                    val glucoseText = if (r != null && r.valueMmol > 0.0) {
                        if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", r.valueMmol)
                        else String.format(Locale.US, "%.0f", r.getValue(GlucoseUnit.MG_DL))
                    } else "--"

                    val arrow = r?.trendArrow ?: ""

                    // Giant Glucose Number (dominating 80-90% width)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = glucoseText,
                            fontSize = 110.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = glucoseColor,
                            letterSpacing = (-2).sp,
                            textAlign = TextAlign.Center
                        )

                        if (arrow.isNotBlank()) {
                            Text(
                                text = arrow,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold,
                                color = glucoseColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Reading Delta & Timestamp Age
                    val ageMins = if (r != null && r.timestamp > 0L) {
                        ((System.currentTimeMillis() - r.timestamp) / 60000L).coerceAtLeast(0L)
                    } else 0L

                    val ageStr = if (ageMins <= 1L) "только что" else "$ageMins мин назад"
                    val deltaStr = if (delta5Min != null && abs(delta5Min) >= 0.1) {
                        val sign = if (delta5Min > 0) "+" else ""
                        if (unit == GlucoseUnit.MMOL_L) "$sign${String.format(Locale.US, "%.1f", delta5Min)}"
                        else "$sign${(delta5Min * 18.0182).toInt()}"
                    } else ""

                    Text(
                        text = listOf(deltaStr, ageStr).filter { it.isNotBlank() }.joinToString(" • "),
                        color = Color(0xFF555555),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Hint
                Text(
                    text = "Двойной тап: фонарик • Свайп: выход",
                    color = Color(0xFF333333),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                )
            }
        }
    }
}
