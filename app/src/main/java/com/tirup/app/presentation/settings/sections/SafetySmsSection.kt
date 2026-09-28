package com.tirup.app.presentation.settings.sections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import kotlinx.coroutines.delay

@Composable
fun DeviceRoleCard(
    settings: UserSettings,
    isRu: Boolean,
    isRoleSectionExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateAlertSettings: (AlertSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val alerts = settings.alertSettings
    val isFollower = alerts.isCaregiverRole

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRu) "Роль устройства в системе" else "Device Role in TIRUp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isFollower) {
                            if (isRu) "Текущая: 👁️ Фоловер (Наблюдатель)"
                            else "Active: 👁️ Follower (Observer)"
                        } else {
                            if (isRu) "Текущая: 👑 Мастер (Сенсор)"
                            else "Active: 👑 Master (Sensor)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFollower) PrimaryEmerald else ActionBlue
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isFollower) PrimaryEmerald else ActionBlue).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, (if (isFollower) PrimaryEmerald else ActionBlue).copy(alpha = 0.35f)),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isFollower) (if (isRu) "Фоловер" else "Follower") else (if (isRu) "Мастер" else "Master"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFollower) PrimaryEmerald else ActionBlue
                        )
                        Text(
                            text = if (isRoleSectionExpanded) "▲" else "▼",
                            fontSize = 11.sp,
                            color = if (isFollower) PrimaryEmerald else ActionBlue
                        )
                    }
                }
            }

            if (isRoleSectionExpanded) {
                Text(
                    text = if (isFollower) {
                        if (isRu) "Наблюдатель: приём данных, сирена при ночном SOS"
                        else "Follower: telemetry reception, loud siren on night SOS"
                    } else {
                        if (isRu) "Ведущее: сенсор глюкозы, локальные тревоги, авто-SOS близким"
                        else "Master: CGM sensor, local alarms, auto-SOS to contacts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Role Selectors: Master (👑) vs Follower (👁️)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Master button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isFollower) {
                                    onUpdateAlertSettings(alerts.copy(isCaregiverRole = false))
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isFollower) ActionBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (!isFollower) 1.5.dp else 0.8.dp,
                            color = if (!isFollower) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "👑",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRu) "Мастер" else "Master",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (!isFollower) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isFollower) ActionBlue else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRu) "(Сенсор)" else "(Sensor)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isFollower) ActionBlue.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Follower button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!isFollower) {
                                    onUpdateAlertSettings(alerts.copy(isCaregiverRole = true))
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isFollower) PrimaryEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isFollower) 1.5.dp else 0.8.dp,
                            color = if (isFollower) PrimaryEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "👁️",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRu) "Фоловер" else "Follower",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isFollower) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFollower) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRu) "(Наблюдатель)" else "(Observer)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFollower) PrimaryEmerald.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencySmsCard(
    settings: UserSettings,
    isRu: Boolean,
    isSmsCardExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    hasSendSmsPermission: Boolean,
    hasReceiveSmsPermission: Boolean,
    hasOverlayPermission: Boolean,
    smsPermissionsLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    overlayPermissionLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    locationPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    testCaregiverSosCountdownSec: Int,
    onStartCaregiverSosTest: () -> Unit,
    onCancelCaregiverSosTest: () -> Unit,
    onSendTestEmergencySms: () -> Unit,
    onUpdateAlertSettings: (AlertSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alerts = settings.alertSettings
    val isCaregiver = alerts.isCaregiverRole

    var hasAttemptedSmsRequest by rememberSaveable { mutableStateOf(false) }
    var testSmsCooldownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testSmsCooldownSec) {
        if (testSmsCooldownSec > 0) {
            delay(1000L)
            testSmsCooldownSec -= 1
        }
    }

    var localPrimaryPhone by rememberSaveable { mutableStateOf(alerts.emergencyContactPhone) }
    var localPrimaryName by rememberSaveable { mutableStateOf(alerts.emergencyContactName) }
    var localSecondaryPhone by rememberSaveable { mutableStateOf(alerts.secondaryEmergencyContactPhone) }
    var localSecondaryName by rememberSaveable { mutableStateOf(alerts.secondaryEmergencyContactName) }

    LaunchedEffect(alerts.emergencyContactPhone) {
        if (localPrimaryPhone != alerts.emergencyContactPhone) {
            localPrimaryPhone = alerts.emergencyContactPhone
        }
    }
    LaunchedEffect(alerts.emergencyContactName) {
        if (localPrimaryName != alerts.emergencyContactName) {
            localPrimaryName = alerts.emergencyContactName
        }
    }
    LaunchedEffect(alerts.secondaryEmergencyContactPhone) {
        if (localSecondaryPhone != alerts.secondaryEmergencyContactPhone) {
            localSecondaryPhone = alerts.secondaryEmergencyContactPhone
        }
    }
    LaunchedEffect(alerts.secondaryEmergencyContactName) {
        if (localSecondaryName != alerts.secondaryEmergencyContactName) {
            localSecondaryName = alerts.secondaryEmergencyContactName
        }
    }

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "🚨",
                        fontSize = 22.sp
                    )
                    Column {
                        Text(
                            text = if (isRu) "Экстренное SMS" else "Emergency SMS",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isCaregiver) {
                                if (isRu) "Приём SOS и сирена фоловера" else "Follower SOS reception & siren"
                            } else {
                                if (isRu) "Авто-отправка SMS близким при гипогликемии" else "Auto-send SMS to contacts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isMasterChecked = if (isCaregiver) alerts.isCaregiverSosWakeupEnabled else alerts.isEmergencySmsEnabled
                    Switch(
                        checked = isMasterChecked,
                        onCheckedChange = { isChecked ->
                            if (isCaregiver) {
                                if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                    smsPermissionsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                                }
                                onUpdateAlertSettings(alerts.copy(isCaregiverSosWakeupEnabled = isChecked))
                            } else {
                                if (isChecked) {
                                    val needed = mutableListOf<String>()
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                        needed.add(Manifest.permission.SEND_SMS)
                                    }
                                    if (alerts.isSmsQueryReplyEnabled &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                        needed.add(Manifest.permission.RECEIVE_SMS)
                                    }
                                    if (needed.isNotEmpty()) {
                                        smsPermissionsLauncher.launch(needed.toTypedArray())
                                    }
                                }
                                onUpdateAlertSettings(alerts.copy(isEmergencySmsEnabled = isChecked))
                            }
                            if (isChecked && !isSmsCardExpanded) onToggleExpanded()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (isCaregiver) PrimaryEmerald else Color(0xFFEF4444)
                        )
                    )

                    Icon(
                        imageVector = if (isSmsCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isSmsCardExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isSmsCardExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Permissions Check & Warnings
                    val missingSend = !isCaregiver && alerts.isEmergencySmsEnabled && !hasSendSmsPermission
                    val missingReceive = (if (isCaregiver) alerts.isCaregiverSosWakeupEnabled else (alerts.isSmsQueryReplyEnabled || alerts.isCaregiverSosWakeupEnabled)) && !hasReceiveSmsPermission
                    val hasMissingSms = missingSend || missingReceive

                    if (hasMissingSms) {
                        val needed = mutableListOf<String>()
                        if (missingSend) needed.add(Manifest.permission.SEND_SMS)
                        if (missingReceive) needed.add(Manifest.permission.RECEIVE_SMS)

                        val activity = context as? android.app.Activity
                        val isPermanentlyDenied = hasAttemptedSmsRequest && activity != null && needed.any { perm ->
                            !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) &&
                            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
                        }

                        val bannerTitle = if (missingSend && missingReceive) {
                            if (isRu) "Требуется доступ к SMS (отправка и приём)" else "SMS Permissions Required (Send & Receive)"
                        } else if (missingSend) {
                            if (isRu) "Требуется разрешение на отправку SMS" else "SMS Sending Permission Required"
                        } else {
                            if (isRu) "Требуется разрешение на приём SMS" else "SMS Receiving Permission Required"
                        }

                        val bannerDesc = if (isPermanentlyDenied) {
                            if (isRu) "Доступ заблокирован системой Android. Нажмите кнопку ниже, чтобы включить доступ к SMS в настройках приложения."
                            else "Permission was permanently denied. Tap below to enable SMS permission in App Settings."
                        } else if (missingSend && missingReceive) {
                            if (isRu) "Для авто-отправки экстренных сообщений близким и пробуждения фоловера при входящем SOS требуются разрешения на отправку и приём SMS."
                            else "SMS send and receive permissions required for auto-sending alerts to contacts and waking follower on SOS."
                        } else if (missingSend) {
                            if (isRu) "Для автоматической отправки экстренных сообщений близким при тяжёлой гипогликемии предоставьте системное разрешение на отправку SMS."
                            else "To automatically send emergency SMS to trusted contacts on severe low, grant SMS sending permission."
                        } else {
                            if (isRu) "Для распознавания входящих SOS-сообщений подопечного и включения сирены фоловера предоставьте разрешение на приём SMS."
                            else "To read incoming patient SOS messages and sound follower siren, grant SMS receiving permission."
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Text(
                                        text = bannerTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                Text(
                                    text = bannerDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                if (isPermanentlyDenied) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            )
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isRu) "Открыть настройки приложения" else "Open App Settings",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                hasAttemptedSmsRequest = true
                                                smsPermissionsLauncher.launch(needed.toTypedArray())
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (isRu) "Предоставить разрешение" else "Grant Permission",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = if (isRu) "Настройки" else "Settings",
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Caregiver Overlay Permission Warning Plate
                    val missingOverlay = isCaregiver && alerts.isCaregiverSosWakeupEnabled && !hasOverlayPermission
                    if (missingOverlay) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Text(
                                        text = if (isRu) "Разрешите показ поверх других приложений" else "Overlay Permission Required",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                                Text(
                                    text = if (isRu) "Без разрешения «Поверх других окон» экран тревоги фоловера не сможет пробить экран блокировки и включить дисплей при ночном SOS."
                                           else "Without 'Draw over other apps' permission, follower rescue screen cannot wake the device and bypass lockscreen during night SOS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isRu) "Разрешить «Поверх других приложений»" else "Grant Overlay Permission",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Primary phone input
                    OutlinedTextField(
                        value = localPrimaryPhone,
                        onValueChange = { phone ->
                            localPrimaryPhone = phone
                            onUpdateAlertSettings(alerts.copy(emergencyContactPhone = phone))
                        },
                        label = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Номер подопечного (белый список)" else "Patient phone (whitelist)"
                                } else {
                                    if (isRu) "Основной телефон близкого (+...)" else "Primary contact phone (+...)"
                                }
                            )
                        },
                        placeholder = {
                            Text(if (isCaregiver) "+7 900 123-45-67 (подопечный)" else "+7 900 123-45-67")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isCaregiver) Icons.Default.Person else Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (isCaregiver) PrimaryEmerald else ActionBlue
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Primary contact name
                    OutlinedTextField(
                        value = localPrimaryName,
                        onValueChange = { name ->
                            localPrimaryName = name
                            onUpdateAlertSettings(alerts.copy(emergencyContactName = name))
                        },
                        label = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Имя подопечного (необязательно)" else "Patient name (optional)"
                                } else {
                                    if (isRu) "Имя основного контакта (необязательно)" else "Primary contact name (optional)"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Сын, Дочь, Мама..." else "Son, Daughter, Relative..."
                                } else {
                                    if (isRu) "Мама, Муж, Доктор..." else "Mom, Spouse, Doctor..."
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Secondary phone input
                    OutlinedTextField(
                        value = localSecondaryPhone,
                        onValueChange = { phone ->
                            localSecondaryPhone = phone
                            onUpdateAlertSettings(alerts.copy(secondaryEmergencyContactPhone = phone))
                        },
                        label = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Второй номер подопечного (или 2-й подопечный)" else "Secondary patient phone"
                                } else {
                                    if (isRu) "Резервный телефон близкого (+...)" else "Secondary contact phone (+...)"
                                }
                            )
                        },
                        placeholder = {
                            Text(if (isCaregiver) "+7 900 765-43-21 (номер 2)" else "+7 900 765-43-21 (резерв)")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isCaregiver) Icons.Default.Person else Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (isCaregiver) PrimaryEmerald.copy(alpha = 0.8f) else ActionBlue
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Secondary contact name
                    OutlinedTextField(
                        value = localSecondaryName,
                        onValueChange = { name ->
                            localSecondaryName = name
                            onUpdateAlertSettings(alerts.copy(secondaryEmergencyContactName = name))
                        },
                        label = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Имя (подопечный 2 или запасной контакт)" else "Name (2nd patient or backup contact)"
                                } else {
                                    if (isRu) "Имя резервного контакта (необязательно)" else "Secondary contact name (optional)"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                if (isCaregiver) {
                                    if (isRu) "Второй ребёнок, Папа..." else "Second child, Dad..."
                                } else {
                                    if (isRu) "Папа, Бабушка..." else "Dad, Grandma..."
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (!isCaregiver) {
                        // ===== PATIENT-ONLY CONTROLS =====
                        // Delay picker (3 min / 5 min / 10 min)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isRu) "Ожидание реакции:" else "Reaction timeout:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val delayOptions = listOf(
                                    3 to if (isRu) "3 мин" else "3 min",
                                    5 to if (isRu) "5 мин" else "5 min",
                                    10 to if (isRu) "10 мин" else "10 min"
                                )
                                delayOptions.forEach { (mins, label) ->
                                    val isSelected = alerts.emergencySmsDelayMinutes == mins
                                    Surface(
                                        modifier = Modifier.clickable {
                                            onUpdateAlertSettings(alerts.copy(emergencySmsDelayMinutes = mins))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0x33EF4444) else Color.Transparent,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Attach coordinates switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (isRu) "Прикреплять геопозицию (GPS)" else "Attach GPS Location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "Ссылка на Google Maps в SMS для экстренного поиска" else "Google Maps link in SMS for swift finding",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = alerts.includeLocationInEmergencySms,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                    onUpdateAlertSettings(alerts.copy(includeLocationInEmergencySms = isChecked))
                                }
                            )
                        }

                        // SMS Query auto-reply toggle (offline internet fallback)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (isRu) "Отвечать на SMS-запросы близких" else "Reply to SMS queries from contact",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "При отсутствии интернета отправляет сахар и TIR в ответ на SMS («сахар», «?»)"
                                    else "Sends glucose and TIR via SMS when internet is down in reply to 'sugar' or '?'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = alerts.isSmsQueryReplyEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                        smsPermissionsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                                    }
                                    onUpdateAlertSettings(alerts.copy(isSmsQueryReplyEnabled = isChecked))
                                }
                            )
                        }

                        // Send test SMS button
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                    smsPermissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                                } else {
                                    onSendTestEmergencySms()
                                    testSmsCooldownSec = 60
                                }
                            },
                            enabled = testSmsCooldownSec == 0,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (testSmsCooldownSec == 0) ActionBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (testSmsCooldownSec == 0) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (testSmsCooldownSec > 0) {
                                    if (isRu) "Отправить тестовое SMS (${testSmsCooldownSec}с)" else "Send test SMS (${testSmsCooldownSec}s)"
                                } else {
                                    if (isRu) "Отправить тестовое SMS" else "Send test SMS"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (testSmsCooldownSec == 0) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    } else {
                        // ===== CAREGIVER-ONLY CONTROLS =====
                        // Test Caregiver Screen & Siren Button with 5s countdown so caregiver can lock phone
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (testCaregiverSosCountdownSec == 0) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                            Toast.makeText(
                                                context,
                                                if (isRu) "Внимание: для показа поверх заблокированного экрана включите «Поверх других приложений»!"
                                                else "Notice: Enable 'Display over other apps' to wake lockscreen!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (isRu) "Заблокируйте экран! Сирена включится через 5 секунд..."
                                            else "Lock your screen! Siren will sound in 5 seconds...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onStartCaregiverSosTest()
                                    }
                                },
                                enabled = testCaregiverSosCountdownSec == 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryEmerald,
                                    contentColor = Color.White,
                                    disabledContainerColor = PrimaryEmerald.copy(alpha = 0.5f),
                                    disabledContentColor = Color.White.copy(alpha = 0.8f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (testCaregiverSosCountdownSec > 0) {
                                        if (isRu) "Запуск через ${testCaregiverSosCountdownSec}с (заблокируйте экран)..."
                                        else "Starting in ${testCaregiverSosCountdownSec}s (lock screen)..."
                                    } else {
                                        if (isRu) "Проверить SOS-режим (5 сек)"
                                        else "Test SOS mode (5s)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
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

                        Text(
                            text = if (isRu) "💡 Тест сирены 100% и окна поверх экрана блокировки без отправки SMS. Заблокируйте телефон сразу после нажатия."
                                   else "💡 Tests 100% volume siren and lockscreen window without SMS. Lock your phone immediately after tapping.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Heads-Up SMS in TIRUp toggle (shared for both roles)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "📲 Важные SMS в TIRUp" else "📲 Important SMS in TIRUp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRu) "Показывать входящие SMS от фоловера/мастера в оверлее TIRUp"
                                       else "Show incoming SMS from follower/master in TIRUp overlay",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Checkbox(
                            checked = alerts.isHeadsUpMessagingEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateAlertSettings(alerts.copy(isHeadsUpMessagingEnabled = isChecked))
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = ActionBlue,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}
