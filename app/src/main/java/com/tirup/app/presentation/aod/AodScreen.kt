package com.tirup.app.presentation.aod

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
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
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun AodScreen(
    settingsFlow: kotlinx.coroutines.flow.Flow<com.tirup.app.domain.model.UserSettings>,
    latestReadingFlow: kotlinx.coroutines.flow.Flow<com.tirup.app.domain.model.GlucoseReading?>,
    recentReadingsFlow: kotlinx.coroutines.flow.Flow<List<com.tirup.app.domain.model.GlucoseReading>>,
    onSetWindowBrightness: (Float) -> Unit,
    onSaveBrightness: (Float) -> Unit = {},
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

    // Flashlight state (Double-tap initiates 15s smooth increase; Single tap pauses/resumes)
    var isFlashlightActive by remember { mutableStateOf(false) }
    var isFlashlightPaused by remember { mutableStateOf(false) }
    var flashlightProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isFlashlightActive, isFlashlightPaused) {
        if (isFlashlightActive && !isFlashlightPaused) {
            val stepDelay = 80L
            val stepIncrement = 80f / 15_000f
            while (isFlashlightActive && !isFlashlightPaused && flashlightProgress < 1.0f) {
                delay(stepDelay)
                flashlightProgress = (flashlightProgress + stepIncrement).coerceAtMost(1.0f)
            }
        }
    }

    // Brightness adjustment state & HUD
    var currentBrightness by remember { mutableFloatStateOf(aod.customBrightness.coerceIn(0.005f, 1.0f)) }
    var isBrightnessOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(aod.customBrightness) {
        if (!isBrightnessOverlayVisible) {
            currentBrightness = aod.customBrightness.coerceIn(0.005f, 1.0f)
        }
    }

    LaunchedEffect(isBrightnessOverlayVisible) {
        if (isBrightnessOverlayVisible) {
            delay(1500L)
            isBrightnessOverlayVisible = false
        }
    }

    // Pulse awake timer state for PULSE_ON_UPDATE mode
    var isAwake by remember { mutableStateOf(aod.displayMode == AodDisplayMode.ALWAYS_ON) }
    var lastAwakeTriggerTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Anti-Burn-In pixel jitter offsets (-28dp .. +28dp)
    var jitterOffsetX by remember { mutableIntStateOf(0) }
    var jitterOffsetY by remember { mutableIntStateOf(0) }

    fun shiftAntiBurnIn() {
        jitterOffsetX = Random.nextInt(-28, 29)
        jitterOffsetY = Random.nextInt(-36, 37)
    }

    // Auto-hide bottom hint after 10 seconds of inactivity to protect AMOLED display
    var isHintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lastAwakeTriggerTimestamp) {
        isHintVisible = true
        delay(10_000L)
        isHintVisible = false
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
            onSetWindowBrightness(currentBrightness)
        }
    }

    // Periodic Anti-Burn-In repositioning every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            shiftAntiBurnIn()
        }
    }

    // Sync window brightness with flashlight progression or AoD brightness
    LaunchedEffect(flashlightProgress, isFlashlightActive, currentBrightness, isAwake, aod.displayMode) {
        if (isFlashlightActive) {
            val b = 0.05f + (0.95f * flashlightProgress)
            onSetWindowBrightness(b)
        } else {
            if (isAwake || aod.displayMode == AodDisplayMode.ALWAYS_ON) {
                onSetWindowBrightness(currentBrightness)
            } else {
                onSetWindowBrightness(0.001f)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture detector: tap to wake / pause flashlight, double tap for flashlight
            .pointerInput(aod.displayMode, isFlashlightActive) {
                detectTapGestures(
                    onTap = {
                        if (isFlashlightActive) {
                            // Single tap during flashlight: pause or resume ramping!
                            isFlashlightPaused = !isFlashlightPaused
                        } else {
                            // Single tap wakes screen in PULSE_ON_UPDATE
                            lastAwakeTriggerTimestamp = System.currentTimeMillis()
                            isAwake = true
                            onSetWindowBrightness(currentBrightness)
                        }
                    },
                    onDoubleTap = {
                        if (!isFlashlightActive) {
                            isFlashlightActive = true
                            isFlashlightPaused = false
                            flashlightProgress = 0.05f
                        } else {
                            isFlashlightActive = false
                            isFlashlightPaused = false
                            flashlightProgress = 0f
                        }
                    }
                )
            }
            // Vertical drag for brightness, horizontal swipe for exit
            .pointerInput(isFlashlightActive) {
                var totalDragX = 0f
                var totalDragY = 0f
                var isVerticalDrag = false
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                        isVerticalDrag = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (!isFlashlightActive) {
                            if (isVerticalDrag || (abs(totalDragY) > 8f && abs(totalDragY) > abs(totalDragX) * 1.1f)) {
                                isVerticalDrag = true
                                currentBrightness = (currentBrightness - (dragAmount.y / 450f)).coerceIn(0.005f, 1.0f)
                                onSetWindowBrightness(currentBrightness)
                                isBrightnessOverlayVisible = true
                            }
                        }
                    },
                    onDragEnd = {
                        if (abs(totalDragX) > 80f && abs(totalDragX) > abs(totalDragY) * 1.5f) {
                            onExit()
                        } else if (isVerticalDrag) {
                            onSaveBrightness(currentBrightness)
                        }
                    }
                )
            }
    ) {
        val isLandscape = maxWidth > maxHeight
        val topPadding = if (isLandscape) 4.dp else 36.dp
        val bottomPadding = if (isLandscape) 4.dp else 28.dp
        val glucoseFontSize = if (isLandscape) (maxHeight.value * 0.65f).coerceIn(210f, 280f).sp else 140.sp
        val arrowFontSize = glucoseFontSize

        // -------------------------------------------------------------
        // 1. Flashlight Overlay: Smooth warm white ramp with interactive slider & pause
        // -------------------------------------------------------------
        if (isFlashlightActive || flashlightProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFBEB).copy(alpha = flashlightProgress))
            ) {
                val overlayTextColor = if (flashlightProgress < 0.45f) Color(0xFFF8FAFC) else Color(0xFF1E293B)
                val pct = (flashlightProgress * 100).roundToInt()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding, start = 20.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFlashlightPaused) {
                                stringResource(R.string.aod_flashlight_paused, pct)
                            } else {
                                stringResource(R.string.aod_flashlight_ramping, pct)
                            },
                            color = overlayTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                isFlashlightActive = false
                                isFlashlightPaused = false
                                flashlightProgress = 0f
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Off",
                                tint = overlayTextColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.material3.Slider(
                        value = flashlightProgress,
                        onValueChange = { newValue ->
                            flashlightProgress = newValue
                            isFlashlightPaused = true
                            onSetWindowBrightness(0.05f + 0.95f * newValue)
                        },
                        valueRange = 0.05f..1.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFFF59E0B),
                            activeTrackColor = Color(0xFFF59E0B),
                            inactiveTrackColor = overlayTextColor.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Brightness HUD overlay during vertical drag (Placed at TopCenter above glucose)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isBrightnessOverlayVisible && !isFlashlightActive,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 36.dp else 68.dp)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = Color(0xEE1E293B),
                border = BorderStroke(1.dp, Color(0xFF475569)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🔆", fontSize = 18.sp)
                    Text(
                        text = stringResource(R.string.aod_brightness_hud, (currentBrightness * 100).roundToInt()),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3. AOD Glucose Display Content (Visible when isAwake or ALWAYS_ON)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = (isAwake || aod.displayMode == AodDisplayMode.ALWAYS_ON) && !isFlashlightActive,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top Bar: Exit button & Current Clock with Anti-Burn-In Jitter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset((jitterOffsetX / 2).dp.roundToPx(), (jitterOffsetY / 2).dp.roundToPx()) }
                        .padding(top = topPadding, start = 24.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val currentTimeStr = timeFormat.format(Date())
                    Text(
                        text = currentTimeStr,
                        color = Color(0xFF666666),
                        fontSize = if (isLandscape) 14.sp else 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit AOD",
                            tint = Color(0xFF555555)
                        )
                    }
                }

                // Center Jitter Area (Anti-Burn-In) with Huge Glucose & Trend
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(jitterOffsetX.dp.roundToPx(), jitterOffsetY.dp.roundToPx()) }
                        .padding(horizontal = if (isLandscape) 8.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val r = reading
                    val glucoseValMmol = r?.valueMmol ?: 0.0

                    // Color mapping: muted soft tones for night vision & OLED safety
                    val glucoseColor = when {
                        glucoseValMmol <= 0.0 -> Color(0xFF555555)
                        glucoseValMmol < ranges.veryLowThresholdMmol -> Color(0xFF991B1B)
                        glucoseValMmol < ranges.tirLowMmol -> Color(0xFFB45309)
                        glucoseValMmol > ranges.veryHighThresholdMmol -> Color(0xFF9A3412)
                        glucoseValMmol > ranges.tirHighMmol -> Color(0xFFB45309)
                        else -> Color(0xFF047857)
                    }

                    val glucoseText = if (r != null && r.valueMmol > 0.0) {
                        if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", r.valueMmol)
                        else String.format(Locale.US, "%.0f", r.getValue(GlucoseUnit.MG_DL))
                    } else "--"

                    val arrow = r?.trendArrow ?: ""

                    // Giant Glucose Number (dominating width/height in landscape)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = glucoseText,
                            fontSize = glucoseFontSize,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = glucoseColor,
                            letterSpacing = (-2).sp,
                            textAlign = TextAlign.Center
                        )

                        if (arrow.isNotBlank()) {
                            Text(
                                text = arrow,
                                fontSize = arrowFontSize,
                                fontWeight = FontWeight.Bold,
                                color = glucoseColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = if (isLandscape) 10.dp else 6.dp, bottom = if (isLandscape) 4.dp else 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 1.dp else 6.dp))

                    // Reading Delta & Timestamp Age (Fully Localized)
                    val ageMins = if (r != null && r.timestamp > 0L) {
                        ((System.currentTimeMillis() - r.timestamp) / 60000L).coerceAtLeast(0L)
                    } else 0L

                    val ageStr = if (ageMins <= 1L) {
                        stringResource(R.string.just_now)
                    } else {
                        stringResource(R.string.minutes_ago, ageMins.toInt())
                    }

                    val deltaStr = if (delta5Min != null && abs(delta5Min) >= 0.1) {
                        val sign = if (delta5Min > 0) "+" else ""
                        if (unit == GlucoseUnit.MMOL_L) "$sign${String.format(Locale.US, "%.1f", delta5Min)}"
                        else "$sign${(delta5Min * 18.0182).toInt()}"
                    } else ""

                    Text(
                        text = listOf(deltaStr, ageStr).filter { it.isNotBlank() }.joinToString(" • "),
                        color = Color(0xFF666666),
                        fontSize = if (isLandscape) 14.sp else 17.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Hint: Auto-fades out after 10s to prevent burn-in; increased brightness (+35%)
                AnimatedVisibility(
                    visible = isHintVisible,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(600)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                ) {
                    Text(
                        text = stringResource(R.string.aod_gesture_hint),
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
