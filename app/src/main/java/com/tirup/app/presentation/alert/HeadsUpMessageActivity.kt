package com.tirup.app.presentation.alert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TIRUpTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HeadsUpMessageActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Turn screen on and show over lockscreen even if locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Acquire WakeLock for 15 seconds to ensure display wakes up
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TIRUp:HeadsUpMessageWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(15_000L)
            }
        } catch (_: Exception) {}

        // 3. Gentle double haptic vibration (no screaming siren)
        try {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 140, 100, 140), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 140, 100, 140), -1)
            }
        } catch (_: Exception) {}

        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Сообщение"
        val senderPhone = intent.getStringExtra(EXTRA_SENDER_PHONE) ?: ""
        val messageText = intent.getStringExtra(EXTRA_MESSAGE_TEXT) ?: ""
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val isSenderMaster = intent.getBooleanExtra(EXTRA_IS_SENDER_MASTER, false)

        setContent {
            TIRUpTheme {
                HeadsUpMessageScreen(
                    senderName = senderName,
                    senderPhone = senderPhone,
                    messageText = messageText,
                    timestamp = timestamp,
                    isSenderMaster = isSenderMaster,
                    onDismiss = { finish() },
                    onSendReply = { reply ->
                        sendReplySms(senderPhone, reply)
                    }
                )
            }
        }
    }

    private fun sendReplySms(phone: String, message: String) {
        if (phone.isBlank()) {
            Toast.makeText(this, "Номер телефона не указан", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения на отправку SMS", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
            Toast.makeText(this, "✅ Ответ отправлен!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка отправки SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_SENDER_PHONE = "extra_sender_phone"
        const val EXTRA_MESSAGE_TEXT = "extra_message_text"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_IS_SENDER_MASTER = "extra_is_sender_master"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeadsUpMessageScreen(
    senderName: String,
    senderPhone: String,
    messageText: String,
    timestamp: Long,
    isSenderMaster: Boolean,
    onDismiss: () -> Unit,
    onSendReply: (String) -> Unit
) {
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var userInteracted by remember { mutableStateOf(false) }

    // 1. Two-phase dismiss timer:
    // Phase 1 (no touch): 20 seconds countdown
    // Phase 2 (after touch): 30 seconds hidden countdown, reveals warning only at <= 10s
    var remainingSeconds by remember { mutableIntStateOf(20) }

    LaunchedEffect(userInteracted, showReplyDialog) {
        if (showReplyDialog) return@LaunchedEffect
        remainingSeconds = if (!userInteracted) 20 else 30
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingSeconds--
        }
        if (!showReplyDialog) {
            onDismiss()
        }
    }

    // Determine Day vs Night mode for lighting effects
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val isNightTime = currentHour in 22..23 || currentHour in 0..6

    // Limit ambient / strobe lighting animation to the first 6 seconds
    var isLightingActive by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000L)
        isLightingActive = false
    }

    // Pulsing neon border animation: shifts smoothly between lighter and deeper sky blue
    val transition = rememberInfiniteTransition(label = "pulseBorder")
    val animatedBorderColor by transition.animateColor(
        initialValue = Color(0xFF38BDF8), // Light Cyan / Sky Blue
        targetValue = Color(0xFF0284C7),  // Deep vivid blue
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderColor"
    )

    // Daytime breathing ambient glow on screen edges: enhanced brightness (alpha 0.15..0.65)
    val daySideGlowAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sideGlow"
    )

    // Nighttime soft strobe beacon: alternating top and bottom vivid pulses (600ms cycle)
    val strobeTransition = rememberInfiniteTransition(label = "nightStrobe")
    val strobePhase by strobeTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "strobePhase"
    )
    val topStrobeAlpha = if (isLightingActive && isNightTime && strobePhase < 0.5f) {
        (Math.sin(strobePhase * 2 * Math.PI) * 0.75f).toFloat().coerceAtLeast(0f)
    } else 0f
    val bottomStrobeAlpha = if (isLightingActive && isNightTime && strobePhase >= 0.5f) {
        (Math.sin((strobePhase - 0.5f) * 2 * Math.PI) * 0.75f).toFloat().coerceAtLeast(0f)
    } else 0f

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    // Text Obfuscation: show first 3-4 words initially, toggle on tap
    var isTextObfuscated by remember { mutableStateOf(true) }
    val (previewText, hasHiddenWords) = remember(messageText) {
        val clean = messageText.trim()
        val words = clean.split(Regex("\\s+"))
        if (words.size > 4) {
            val preview = words.take(4).joinToString(" ")
            Pair(preview, true)
        } else {
            Pair(clean, false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E17)) // Deep dark slate background
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        userInteracted = true
                        // Reset to fresh 30-sec safety timeout on tap
                        remainingSeconds = 30
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // --- 1. Ambient Lighting Layers (Active for first 6 seconds) ---
        if (isLightingActive) {
            if (!isNightTime) {
                // Daytime Lateral Breathing Glow (vivid sky-blue ambient bleed)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(72.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    animatedBorderColor.copy(alpha = daySideGlowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(72.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    animatedBorderColor.copy(alpha = daySideGlowAlpha)
                                )
                            )
                        )
                )
            } else {
                // Nighttime Strobe Beacon (Top & Bottom moon-white glow)
                if (topStrobeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFE2E8F0).copy(alpha = topStrobeAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                if (bottomStrobeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFE2E8F0).copy(alpha = bottomStrobeAlpha)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // --- 2. Main Heads-Up Container with glowing pulsing outline ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .border(
                    width = 2.5.dp,
                    color = animatedBorderColor,
                    shape = RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B2B)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Sender role & time badge + Timer indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ActionBlue.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (isSenderMaster) "👑" else "👁️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = senderName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Timer status badge:
                        // Phase 1 (no interaction): always show countdown
                        // Phase 2 (interacted): hidden until remainingSeconds <= 10
                        if (!userInteracted || remainingSeconds <= 10) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (remainingSeconds <= 5) Color(0xFFEF4444).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (remainingSeconds <= 5) Color(0xFFEF4444).copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = "⏱ ${remainingSeconds}с",
                                    color = if (remainingSeconds <= 5) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Adaptive Central Message Body with Obfuscation / Full Reveal toggle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 340.dp)
                        .background(Color(0xFF132238), RoundedCornerShape(16.dp))
                        .clickable {
                            userInteracted = true
                            remainingSeconds = 30
                            if (hasHiddenWords) {
                                isTextObfuscated = !isTextObfuscated
                            }
                        }
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        val displayedText = if (hasHiddenWords && isTextObfuscated) {
                            "$previewText..."
                        } else {
                            messageText.ifBlank { "Сообщение без текста" }
                        }

                        Text(
                            text = displayedText,
                            color = Color.White,
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start
                        )

                        if (hasHiddenWords) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTextObfuscated) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isTextObfuscated) "Показать полностью" else "Скрыть детали",
                                    color = ActionBlue,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Big Green "OK!" Dismiss Button (Height 60dp)
                Button(
                    onClick = {
                        userInteracted = true
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ОК!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action 2: "Ответить" Quick Reply Button (Height 48dp)
                OutlinedButton(
                    onClick = {
                        userInteracted = true
                        showReplyDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ActionBlue
                    ),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ответить",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Quick Reply Modal Dialog (Strict 70 characters limit for single SMS part)
    if (showReplyDialog) {
        val quickTemplates = if (isSenderMaster) {
            // Templates for Follower replying to Master
            listOf("Принято 👍", "Выпей сок! 🧃", "Подколи 1 ед 💉", "Как самочувствие?")
        } else {
            // Templates for Master replying to Follower
            listOf("Выпил сок 🧃", "Уколол инсулин 💉", "Принято, всё под контролем 👌", "Перемеряю через 15м ⏱️")
        }

        AlertDialog(
            onDismissRequest = { showReplyDialog = false },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = ActionBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (senderPhone.isNotBlank()) "Быстрый ответ: $senderName ($senderPhone)" else "Быстрый ответ ($senderName)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Quick template chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTemplates.forEach { template ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ActionBlue.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, ActionBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    replyText = template.take(70)
                                }
                            ) {
                                Text(
                                    text = template,
                                    color = ActionBlue,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Reply text field
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { if (it.length <= 70) replyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Сообщение (до 70 симв.)", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("Напишите ответ...", color = Color(0xFF64748B)) },
                        maxLines = 3,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val charCount = replyText.length
                                Text(
                                    text = "$charCount / 70",
                                    color = if (charCount >= 65) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            onSendReply(replyText.trim())
                            showReplyDialog = false
                        }
                    },
                    enabled = replyText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Отправить SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplyDialog = false }) {
                    Text("Отмена", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
