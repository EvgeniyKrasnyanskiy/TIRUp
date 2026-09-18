package com.tirup.app.presentation.alert

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.data.alert.GlucoseAlertManager
import kotlinx.coroutines.delay
import java.util.Locale

class PatientCriticalHypoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn on screen and display over lockscreen even under password / PIN
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

        val glucoseDisplay = intent.getStringExtra(EXTRA_GLUCOSE) ?: "2.8"
        val trendArrow = intent.getStringExtra(EXTRA_TREND) ?: "⇊"
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)
        val primaryContactPhone = intent.getStringExtra(EXTRA_PRIMARY_CONTACT_PHONE) ?: ""
        val primaryContactName = intent.getStringExtra(EXTRA_PRIMARY_CONTACT_NAME) ?: ""

        setContent {
            PatientCriticalHypoScreen(
                glucoseDisplay = glucoseDisplay,
                trendArrow = trendArrow,
                isTest = isTest,
                primaryContactPhone = primaryContactPhone,
                primaryContactName = primaryContactName,
                onConfirmCarbs = {
                    GlucoseAlertManager.silenceCurrentSoundOnly()
                    GlucoseAlertManager.dismissCriticalAlarm(this, fromUser = true)
                },
                onCallContact = { phone ->
                    if (phone.isNotBlank()) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
                        startActivity(dialIntent)
                    }
                },
                onClose = {
                    GlucoseAlertManager.silenceCurrentSoundOnly()
                    finish()
                }
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Any volume key or power button silences the siren
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                GlucoseAlertManager.silenceCurrentSoundOnly()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_GLUCOSE = "extra_glucose"
        const val EXTRA_TREND = "extra_trend"
        const val EXTRA_IS_TEST = "extra_is_test"
        const val EXTRA_PRIMARY_CONTACT_PHONE = "extra_primary_contact_phone"
        const val EXTRA_PRIMARY_CONTACT_NAME = "extra_primary_contact_name"
    }
}

@Composable
fun PatientCriticalHypoScreen(
    glucoseDisplay: String,
    trendArrow: String,
    isTest: Boolean,
    primaryContactPhone: String,
    primaryContactName: String,
    onConfirmCarbs: () -> Unit,
    onCallContact: (String) -> Unit,
    onClose: () -> Unit
) {
    var isCarbsConfirmed by rememberSaveable { mutableStateOf(false) }
    var remainingTimerSec by rememberSaveable { mutableIntStateOf(15 * 60) }

    // 15-minute countdown after carbs confirmation
    LaunchedEffect(isCarbsConfirmed) {
        if (isCarbsConfirmed) {
            while (remainingTimerSec > 0) {
                delay(1000L)
                remainingTimerSec--
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hypo_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Pulsate screen background between dark slate and urgent emergency red until carbs confirmed
    val strobeBgColor by infiniteTransition.animateColor(
        initialValue = Color(0xFF07090E),
        targetValue = Color(0xFF6B0E0E),
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobe_bg"
    )
    val currentBg = if (!isCarbsConfirmed) strobeBgColor else Color(0xFF07090E)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = currentBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Warning Badge
                Box(
                    modifier = Modifier
                        .scale(if (!isCarbsConfirmed) pulseScale else 1.0f)
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isTest) "ТЕСТ: КРИТИЧЕСКАЯ ГИПОГЛИКЕМИЯ" else "КРИТИЧЕСКАЯ ГИПОГЛИКЕМИЯ!",
                    color = Color(0xFFEF4444),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isCarbsConfirmed) "Сирена отключена. Углеводы приняты." else "Срочно примите быстрые углеводы!",
                    color = if (isCarbsConfirmed) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Glucose Value Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E).copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = glucoseDisplay,
                                color = Color(0xFFF87171),
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ммоль/л",
                                color = Color(0xFF94A3B8),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (trendArrow.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = trendArrow,
                                    color = Color(0xFFF87171),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rule 15 Box or 15-Minute Countdown
                if (!isCarbsConfirmed) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C14)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "⚡ ПРАВИЛО 15 Г БЫСТРЫХ УГЛЕВОДОВ",
                                color = Color(0xFFFBBF24),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Примите 15–20 г быстрых углеводов прямо сейчас:\n• 150–200 мл фруктового сока или лимонада\n• 4–5 кусочков сахара или декстроза\n• Тёплый сладкий чай",
                                color = Color(0xFFFEF3C7),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    // Timer Card: "Контроль через 15:00"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F291E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(24.dp)
                                )
                                val min = remainingTimerSec / 60
                                val sec = remainingTimerSec % 60
                                val timeFormatted = String.format(Locale.US, "%02d:%02d", min, sec)
                                Text(
                                    text = if (remainingTimerSec > 0) "Контроль через $timeFormatted" else "Пора перемерить сахар!",
                                    color = Color(0xFF34D399),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Мембрана сенсора и кровь восстанавливают уровень глюкозы через 15 минут.",
                                color = Color(0xFFA7F3D0),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action 1: Huge 140dp Button: "Принял углеводы (Отключить сирену)"
                if (!isCarbsConfirmed) {
                    Button(
                        onClick = {
                            isCarbsConfirmed = true
                            onConfirmCarbs()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Принял углеводы",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Отключить сирену",
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action 2: Call Primary Caregiver Contact
                if (primaryContactPhone.isNotBlank()) {
                    Button(
                        onClick = { onCallContact(primaryContactPhone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        val contactLabel = primaryContactName.ifBlank { "опекуну" }
                        Text(
                            text = "Позвонить: $contactLabel",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action 3: Close Window
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Закрыть окно",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
