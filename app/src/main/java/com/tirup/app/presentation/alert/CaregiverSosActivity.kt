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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.data.alert.CaregiverSosAlarmManager

class CaregiverSosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn on screen and show over lockscreen even if locked / password-protected
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

        val patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: "Близкий"
        val glucoseDisplay = intent.getStringExtra(EXTRA_GLUCOSE) ?: "Критич. гипо"
        val trendArrow = intent.getStringExtra(EXTRA_TREND) ?: ""
        val delayMinutes = intent.getIntExtra(EXTRA_MINUTES, 5)
        val senderPhone = intent.getStringExtra(EXTRA_SENDER_PHONE) ?: ""
        val mapsUrl = intent.getStringExtra(EXTRA_MAPS_URL)
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)

        setContent {
            val isAlarmActive by CaregiverSosAlarmManager.isSosAlarmActive.collectAsState()

            CaregiverSosScreen(
                isAlarmActive = isAlarmActive,
                patientName = patientName,
                glucoseDisplay = glucoseDisplay,
                trendArrow = trendArrow,
                delayMinutes = delayMinutes,
                senderPhone = senderPhone,
                mapsUrl = mapsUrl,
                isTest = isTest,
                onCallPatient = {
                    if (senderPhone.isNotBlank()) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${senderPhone.trim()}"))
                        startActivity(dialIntent)
                    }
                },
                onOpenMap = {
                    if (!mapsUrl.isNullOrBlank()) {
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                        startActivity(mapIntent)
                    }
                },
                onDismissAlarm = {
                    CaregiverSosAlarmManager.dismissSosAlarm(this)
                },
                onCloseScreen = {
                    CaregiverSosAlarmManager.dismissSosAlarm(this)
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
                CaregiverSosAlarmManager.dismissSosAlarm(this)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_PATIENT_NAME = "extra_patient_name"
        const val EXTRA_GLUCOSE = "extra_glucose"
        const val EXTRA_TREND = "extra_trend"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_SENDER_PHONE = "extra_sender_phone"
        const val EXTRA_MAPS_URL = "extra_maps_url"
        const val EXTRA_IS_TEST = "extra_is_test"
    }
}

@Composable
fun CaregiverSosScreen(
    isAlarmActive: Boolean,
    patientName: String,
    glucoseDisplay: String,
    trendArrow: String,
    delayMinutes: Int,
    senderPhone: String,
    mapsUrl: String?,
    isTest: Boolean,
    onCallPatient: () -> Unit,
    onOpenMap: () -> Unit,
    onDismissAlarm: () -> Unit,
    onCloseScreen: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_alarm_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Pulsate screen background between dark slate and urgent emergency red during alarm
    val strobeBgColor by infiniteTransition.animateColor(
        initialValue = Color(0xFF07090E),
        targetValue = Color(0xFF6B0E0E),
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobe_bg"
    )
    val currentBg = if (isAlarmActive) strobeBgColor else Color(0xFF07090E)

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
                // Pulsating Warning Badge
                Box(
                    modifier = Modifier
                        .scale(if (isAlarmActive) pulseScale else 1.0f)
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

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (isTest) "ТЕСТ SOS-ТРЕВОГИ" else "КРИТИЧЕСКАЯ ГИПОГЛИКЕМИЯ!",
                    color = Color(0xFFEF4444),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isTest) "Проверка экстренного канала опекуна" else "Сирена на телефоне пациента $delayMinutes мин без ответа!",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E).copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = patientName,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = glucoseDisplay,
                                color = Color(0xFFF87171),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (trendArrow.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = trendArrow,
                                    color = Color(0xFFF87171),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (senderPhone.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Телефон: $senderPhone",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Huge 140dp button: Dismiss / Silence Alarm (shown when alarm is active)
                if (isAlarmActive) {
                    Button(
                        onClick = onDismissAlarm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Отключить тревогу",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action 2: Call Patient (Solid Emerald Green Button)
                if (senderPhone.isNotBlank()) {
                    Button(
                        onClick = onCallPatient,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Позвонить: $patientName",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action 3: Open Map (if coordinates provided)
                if (!mapsUrl.isNullOrBlank()) {
                    Button(
                        onClick = onOpenMap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Показать координаты",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action 4: Close Window (Positioned below all others)
                Button(
                    onClick = onCloseScreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155)
                    ),
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
