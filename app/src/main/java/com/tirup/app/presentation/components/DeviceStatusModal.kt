package com.tirup.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.daysRemaining
import com.tirup.app.domain.model.expiresAt
import com.tirup.app.domain.model.isExpired
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal bottom-sheet-style dialog for managing CGM sensor and pump infusion set lifecycle.
 */
@Composable
fun DeviceStatusModal(
    sensorStatus: SensorStatus,
    pumpSetStatus: PumpSetStatus,
    isPumpUser: Boolean,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onNewSensor: (durationDays: Int) -> Unit,
    onNewPumpSet: (durationDays: Int) -> Unit
) {
    // Sensor confirmation state
    var showSensorConfirm by remember { mutableStateOf(false) }
    var pendingSensorDays by remember { mutableStateOf(sensorStatus.lastUsedDurationDays.coerceIn(1, 90)) }
    var sensorPickerDays by remember { mutableStateOf(sensorStatus.lastUsedDurationDays.coerceIn(1, 90)) }

    // Pump confirmation state
    var showPumpConfirm by remember { mutableStateOf(false) }
    var pendingPumpDays by remember { mutableStateOf(pumpSetStatus.lastUsedDurationDays.coerceIn(2, 7)) }
    var pumpPickerDays by remember { mutableStateOf(pumpSetStatus.lastUsedDurationDays.coerceIn(2, 7)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Text(
                    text = if (isRu) "Устройства" else "Device Status",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // --- SENSOR SECTION ---
                SensorSection(
                    status = sensorStatus,
                    pickerDays = sensorPickerDays,
                    isRu = isRu,
                    onPickerChange = { sensorPickerDays = it },
                    onNewSensor = {
                        pendingSensorDays = sensorPickerDays
                        showSensorConfirm = true
                    }
                )

                // --- PUMP SET SECTION ---
                if (isPumpUser) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    PumpSetSection(
                        status = pumpSetStatus,
                        pickerDays = pumpPickerDays,
                        isRu = isRu,
                        onPickerChange = { pumpPickerDays = it },
                        onNewPumpSet = {
                            pendingPumpDays = pumpPickerDays
                            showPumpConfirm = true
                        }
                    )
                }

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isRu) "Закрыть" else "Close")
                }
            }
        }
    }

    // Sensor confirm dialog
    if (showSensorConfirm) {
        CountdownConfirmDialog(
            title = if (isRu) "Новый сенсор CGM" else "New CGM Sensor",
            message = if (isRu) "Подтвердите установку нового сенсора на $pendingSensorDays дн." else "Confirm new sensor for $pendingSensorDays days.",
            isRu = isRu,
            onConfirm = {
                showSensorConfirm = false
                onNewSensor(pendingSensorDays)
                onDismiss()
            },
            onDismiss = { showSensorConfirm = false }
        )
    }

    // Pump set confirm dialog
    if (showPumpConfirm) {
        CountdownConfirmDialog(
            title = if (isRu) "Новый инфузионный набор" else "New Infusion Set",
            message = if (isRu) "Подтвердите замену инфузионного набора на $pendingPumpDays дн." else "Confirm new infusion set for $pendingPumpDays days.",
            isRu = isRu,
            onConfirm = {
                showPumpConfirm = false
                onNewPumpSet(pendingPumpDays)
                onDismiss()
            },
            onDismiss = { showPumpConfirm = false }
        )
    }
}

@Composable
private fun SensorSection(
    status: SensorStatus,
    pickerDays: Int,
    isRu: Boolean,
    onPickerChange: (Int) -> Unit,
    onNewSensor: () -> Unit
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📡", fontSize = 20.sp)
            Text(
                text = if (isRu) "Сенсор CGM" else "CGM Sensor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (status.installedAt > 0L) {
            val days = status.daysRemaining
            val color = statusColor(days)
            val installedStr = sdf.format(Date(status.installedAt))
            val expiresStr = sdf.format(Date(status.expiresAt))
            Text(
                text = if (isRu) "Установлен: $installedStr  ·  Истекает: $expiresStr" else "Installed: $installedStr  ·  Expires: $expiresStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (days < 0) {
                        if (isRu) "Просрочен на ${-days} дн." else "Expired ${-days}d ago"
                    } else {
                        if (isRu) "Осталось: $days дн." else "Remaining: $days days"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Text(
                text = if (isRu) "Нет данных об установке" else "No installation data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Duration picker
        DurationPicker(
            value = pickerDays,
            min = 1,
            max = 90,
            label = if (isRu) "Срок (дн.)" else "Duration (days)",
            onChange = onPickerChange
        )

        Button(
            onClick = onNewSensor,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isRu) "Новый сенсор" else "New Sensor")
        }
    }
}

@Composable
private fun PumpSetSection(
    status: PumpSetStatus,
    pickerDays: Int,
    isRu: Boolean,
    onPickerChange: (Int) -> Unit,
    onNewPumpSet: () -> Unit
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💉", fontSize = 20.sp)
            Text(
                text = if (isRu) "Инфузионный набор" else "Infusion Set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (status.installedAt > 0L) {
            val days = status.daysRemaining
            val color = statusColor(days)
            val installedStr = sdf.format(Date(status.installedAt))
            val expiresStr = sdf.format(Date(status.expiresAt))
            Text(
                text = if (isRu) "Установлен: $installedStr  ·  Истекает: $expiresStr" else "Installed: $installedStr  ·  Expires: $expiresStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (days < 0) {
                        if (isRu) "Просрочен на ${-days} дн." else "Expired ${-days}d ago"
                    } else {
                        if (isRu) "Осталось: $days дн." else "Remaining: $days days"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Text(
                text = if (isRu) "Нет данных об установке" else "No installation data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DurationPicker(
            value = pickerDays,
            min = 2,
            max = 7,
            label = if (isRu) "Срок (дн.)" else "Duration (days)",
            onChange = onPickerChange
        )

        Button(
            onClick = onNewPumpSet,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isRu) "Новый инфузионный набор" else "New Infusion Set")
        }
    }
}

@Composable
private fun DurationPicker(
    value: Int,
    min: Int,
    max: Int,
    label: String,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

/**
 * Confirmation dialog with 3-second countdown auto-dismiss.
 * User can confirm immediately or wait for auto-cancel.
 */
@Composable
fun CountdownConfirmDialog(
    title: String,
    message: String,
    isRu: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(3) }
    var animProgress by remember { mutableStateOf(1f) }

    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
        label = "countdown_progress"
    )

    LaunchedEffect(Unit) {
        animProgress = 0f
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = if (isRu) "Автоотмена через $secondsLeft сек." else "Auto-cancel in $secondsLeft s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (isRu) "Подтвердить" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}

/**
 * In-app alert dialog shown when a device has expired, prompting user to act.
 */
@Composable
fun DeviceExpiredAlertDialog(
    isSensor: Boolean,
    daysExpired: Int,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onInstallNow: () -> Unit
) {
    val title = if (isSensor) {
        if (isRu) "⚠️ Сенсор CGM истёк" else "⚠️ CGM Sensor Expired"
    } else {
        if (isRu) "⚠️ Инфузионный набор истёк" else "⚠️ Infusion Set Expired"
    }
    val message = if (isSensor) {
        if (isRu) "Срок сенсора истёк $daysExpired дн. назад. Рекомендуется установить новый сенсор заранее — ему нужно 1–2 дня на прогрев."
        else "Sensor expired $daysExpired days ago. Install a new one early — it needs 1–2 days to warm up."
    } else {
        if (isRu) "Срок инфузионного набора истёк $daysExpired дн. назад. Замените набор."
        else "Infusion set expired $daysExpired days ago. Replace it now."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onInstallNow) {
                Text(if (isRu) "Установить сейчас" else "Install Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "ОК" else "OK")
            }
        }
    )
}

// Helper: color based on days remaining
@Composable
private fun statusColor(daysRemaining: Int): Color {
    return when {
        daysRemaining < 0 -> MaterialTheme.colorScheme.error
        daysRemaining <= 2 -> Color(0xFFF59E0B) // amber
        else -> Color(0xFF22C55E) // green
    }
}
