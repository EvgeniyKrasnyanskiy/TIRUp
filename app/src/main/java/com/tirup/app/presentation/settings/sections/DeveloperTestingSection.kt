package com.tirup.app.presentation.settings.sections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tirup.app.data.ble.BleBroadcaster
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.settings.dialogs.BleRangeHelpDialog
import com.tirup.app.presentation.theme.ActionBlue

private val TestButtonAmber = Color(0xFFEAB308)

@Composable
fun DeveloperTestingCard(
    settings: UserSettings,
    isRu: Boolean,
    isDevTestsUnlocked: Boolean,
    smsPermissionsLauncher: ActivityResultLauncher<Array<String>>,
    testCaregiverSosCountdownSec: Int,
    onStartCaregiverSosTest: () -> Unit,
    onCancelCaregiverSosTest: () -> Unit,
    onStartPatientRescueTest: (Int) -> Unit,
    onCancelPatientRescueTest: () -> Unit,
    onStartHeadsUpTest: (Int) -> Unit,
    onCancelHeadsUpTest: () -> Unit,
    onSendCaregiverSosTestSms: () -> Unit,
    onStartBleRangeTest: (Int) -> Unit,
    onCheckAndRequestBlePermissions: (BleBridgeRole) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isDevTestsUnlocked) return

    val context = LocalContext.current
    var testRescueCountdownSec by rememberSaveable { mutableIntStateOf(0) }
    var testHeadsUpCountdownSec by rememberSaveable { mutableIntStateOf(0) }
    var testSosSmsCooldownSec by rememberSaveable { mutableIntStateOf(0) }
    var bleRangeCooldownSec by rememberSaveable { mutableIntStateOf(0) }
    var showSosSmsSendConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showBleRangeHelpDialog by rememberSaveable { mutableStateOf(false) }

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🛠️", fontSize = 18.sp)
                Text(
                    text = if (isRu) "Тестирование систем" else "System Testing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ActionBlue
                )
            }

            Text(
                text = if (isRu)
                    "Инструменты проверки тревог и каналов связи:"
                else
                    "Alert and communication channel testing tools:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Test 1: Patient Rescue Screen (5 sec delay with Cancel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (testRescueCountdownSec == 0) {
                            Toast.makeText(
                                context,
                                if (isRu) "Заблокируйте экран! Экран спасения появится через 5 секунд..."
                                else "Lock your screen! Rescue screen in 5 seconds...",
                                Toast.LENGTH_SHORT
                            ).show()
                            testRescueCountdownSec = 5
                            onStartPatientRescueTest(5)
                        }
                    },
                    enabled = testRescueCountdownSec == 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
                ) {
                    Text(text = "🚨", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (testRescueCountdownSec > 0) {
                            if (isRu) "Запуск через ${testRescueCountdownSec}с..." else "Starting in ${testRescueCountdownSec}s..."
                        } else {
                            if (isRu) "Экран спасения (5 сек)" else "Rescue Screen (5s)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (testRescueCountdownSec == 0) TestButtonAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                if (testRescueCountdownSec > 0) {
                    OutlinedButton(
                        onClick = {
                            testRescueCountdownSec = 0
                            onCancelPatientRescueTest()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isRu) "Отмена" else "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Test 2: Follower SOS Screen & Siren preview (5s countdown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (testCaregiverSosCountdownSec == 0) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(
                                    context,
                                    if (isRu) "⚠️ Разрешение «Поверх других приложений» не дано — экран может не открыться!"
                                    else "⚠️ 'Display over other apps' not granted — screen may not open!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            Toast.makeText(
                                context,
                                if (isRu) "Заблокируйте экран! SOS-сирена включится через 5 секунд..."
                                else "Lock your screen! SOS siren in 5 seconds...",
                                Toast.LENGTH_SHORT
                            ).show()
                            onStartCaregiverSosTest()
                        }
                    },
                    enabled = testCaregiverSosCountdownSec == 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (testCaregiverSosCountdownSec == 0) TestButtonAmber.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (testCaregiverSosCountdownSec == 0) TestButtonAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (testCaregiverSosCountdownSec > 0) {
                            if (isRu) "🔴 SOS через ${testCaregiverSosCountdownSec}с..."
                            else "🔴 SOS in ${testCaregiverSosCountdownSec}s..."
                        } else {
                            if (isRu) "Экран SOS фоловера (5 сек)" else "Follower SOS screen (5s)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (testCaregiverSosCountdownSec == 0) TestButtonAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                if (testCaregiverSosCountdownSec > 0) {
                    OutlinedButton(
                        onClick = onCancelCaregiverSosTest,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isRu) "Отмена" else "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Test 3: Heads-Up Message Screen preview (5s countdown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (testHeadsUpCountdownSec == 0) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(
                                    context,
                                    if (isRu) "⚠️ Без разрешения «Полноэкранные уведомления» сообщение может не появиться!"
                                    else "⚠️ Without 'Full-screen notifications' permission the overlay may not show!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            Toast.makeText(
                                context,
                                if (isRu) "Заблокируйте экран! Важное SMS-сообщение появится через 5 секунд..."
                                else "Lock your screen! Heads-Up SMS-message in 5 seconds...",
                                Toast.LENGTH_SHORT
                            ).show()
                            testHeadsUpCountdownSec = 5
                            onStartHeadsUpTest(5)
                        }
                    },
                    enabled = testHeadsUpCountdownSec == 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (testHeadsUpCountdownSec == 0) TestButtonAmber.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "💬",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (testHeadsUpCountdownSec > 0) {
                            if (isRu) "Сообщение через ${testHeadsUpCountdownSec}с..."
                            else "Message in ${testHeadsUpCountdownSec}s..."
                        } else {
                            if (isRu) "Экран важное SMS (5 сек)" else "Important SMS screen (5s)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (testHeadsUpCountdownSec == 0) TestButtonAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                if (testHeadsUpCountdownSec > 0) {
                    OutlinedButton(
                        onClick = {
                            testHeadsUpCountdownSec = 0
                            onCancelHeadsUpTest()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isRu) "Отмена" else "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Test 4: Caregiver SOS SMS (60 sec cooldown) with confirmation dialog
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        smsPermissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                    } else {
                        showSosSmsSendConfirmDialog = true
                    }
                },
                enabled = testSosSmsCooldownSec == 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
            ) {
                Text(text = "✉️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (testSosSmsCooldownSec > 0) {
                        if (isRu) "Отправить SOS-SMS (${testSosSmsCooldownSec}с)" else "Send Follower SOS SMS (${testSosSmsCooldownSec}s)"
                    } else {
                        if (isRu) "Отправить SOS-SMS" else "Send Follower SOS SMS"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (testSosSmsCooldownSec == 0) TestButtonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            Text(
                text = if (isRu) "💡 Если окно не появляется на заблокированном экране — дайте разрешения: Приложения → Спец. доступ → Полноэкранные уведомления и Всплывающие окна в фоне."
                else "💡 If the screen doesn't appear on lockscreen — grant: Apps → Special Access → Full-screen notifications & Background pop-ups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Test 5: BLE Bridge range test (5 sec)
            val bleRole = settings.bleBridgeSettings.role
            val bleRangeButtonText = when {
                bleRangeCooldownSec > 0 -> {
                    if (isRu) "Тест дальности ($bleRangeCooldownSec с)..." else "Range testing (${bleRangeCooldownSec}s)..."
                }
                bleRole == BleBridgeRole.OBSERVER -> {
                    if (isRu) "Тест дальности: приём" else "Range Test: Receive"
                }
                bleRole == BleBridgeRole.BROADCASTER -> {
                    if (isRu) "Тест дальности: передача" else "Range Test: Broadcast"
                }
                else -> {
                    if (isRu) "Тест дальности BLE-моста" else "BLE Bridge Range Test"
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val isBt = BleBroadcaster.isBluetoothEnabled(context)
                        if (!isBt) {
                            Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                        } else {
                            if (bleRole == BleBridgeRole.OBSERVER) {
                                onCheckAndRequestBlePermissions(BleBridgeRole.OBSERVER)
                            } else {
                                onCheckAndRequestBlePermissions(BleBridgeRole.BROADCASTER)
                            }
                            bleRangeCooldownSec = 5
                            onStartBleRangeTest(5)
                        }
                    },
                    enabled = bleRangeCooldownSec == 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
                ) {
                    Text(text = "📡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bleRangeButtonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (bleRangeCooldownSec == 0) TestButtonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                IconButton(
                    onClick = { showBleRangeHelpDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = if (isRu) "Информация о тесте дальности" else "Range test info",
                        tint = ActionBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showSosSmsSendConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosSmsSendConfirmDialog = false },
            icon = { Text(text = "⚠️", fontSize = 28.sp) },
            title = {
                Text(
                    text = if (isRu) "Отправить тестовое SOS-SMS?" else "Send test SOS SMS?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isRu) "На номера доверенных контактов будут отправлены реальные SMS-сообщения. Убедитесь, что контакты предупреждены о тесте."
                    else "Real SMS messages will be sent to trusted contact numbers. Make sure contacts are aware this is a test."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosSmsSendConfirmDialog = false
                        testSosSmsCooldownSec = 60
                        onSendCaregiverSosTestSms()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(if (isRu) "Отправить" else "Send", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSosSmsSendConfirmDialog = false }) {
                    Text(if (isRu) "Отмена" else "Cancel")
                }
            }
        )
    }

    if (showBleRangeHelpDialog) {
        BleRangeHelpDialog(
            isRu = isRu,
            onDismiss = { showBleRangeHelpDialog = false }
        )
    }
}
