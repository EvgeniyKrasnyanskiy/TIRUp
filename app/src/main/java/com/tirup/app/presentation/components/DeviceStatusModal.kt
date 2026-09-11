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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import android.app.TimePickerDialog
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
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
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.domain.model.LancetStatus
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.daysRemaining
import com.tirup.app.domain.model.expiresAt
import com.tirup.app.domain.model.isExpired
import com.tirup.app.domain.model.millisRemaining
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal bottom-sheet-style dialog for managing CGM sensor, pump infusion set, and lancet lifecycles.
 */
@Composable
fun DeviceStatusModal(
    sensorStatus: SensorStatus,
    pumpSetStatus: PumpSetStatus,
    lancetStatus: LancetStatus,
    showSensor: Boolean = true,
    showPump: Boolean = true,
    showLancet: Boolean = true,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onNewSensor: (durationDays: Int, installedAt: Long) -> Unit,
    onNewPumpSet: (durationDays: Int, installedAt: Long) -> Unit,
    onNewLancet: (durationDays: Int, installedAt: Long) -> Unit
) {
    // Sensor confirmation state
    var showSensorConfirm by remember { mutableStateOf(false) }
    var pendingSensorDays by remember { mutableStateOf(sensorStatus.lastUsedDurationDays.coerceIn(1, 90)) }
    var sensorPickerDays by remember { mutableStateOf(sensorStatus.lastUsedDurationDays.coerceIn(1, 90)) }

    // Pump confirmation state
    var showPumpConfirm by remember { mutableStateOf(false) }
    var pendingPumpDays by remember { mutableStateOf(pumpSetStatus.lastUsedDurationDays.coerceIn(2, 7)) }
    var pumpPickerDays by remember { mutableStateOf(pumpSetStatus.lastUsedDurationDays.coerceIn(2, 7)) }

    // Lancet confirmation state
    var showLancetConfirm by remember { mutableStateOf(false) }
    var pendingLancetDays by remember { mutableStateOf(lancetStatus.lastUsedDurationDays.coerceIn(1, 7)) }
    var lancetPickerDays by remember { mutableStateOf(lancetStatus.lastUsedDurationDays.coerceIn(1, 7)) }

    var showSensorInfo by remember { mutableStateOf(false) }
    var showPumpInfo by remember { mutableStateOf(false) }
    var showLancetInfo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRu) "Устройства и расходники" else "Device Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // --- SENSOR SECTION ---
                if (showSensor) {
                    DeviceSection(
                        icon = "◉",
                        title = if (isRu) "Сенсор CGM" else "CGM Sensor",
                        installedAt = sensorStatus.installedAt,
                        expiresAt = sensorStatus.expiresAt,
                        millisRemaining = sensorStatus.millisRemaining,
                        daysRemaining = sensorStatus.daysRemaining,
                        pickerDays = sensorPickerDays,
                        pickerMin = 1,
                        pickerMax = 90,
                        isRu = isRu,
                        onPickerChange = { sensorPickerDays = it },
                        onNewClick = {
                            pendingSensorDays = sensorPickerDays
                            showSensorConfirm = true
                        },
                        onShowInfo = { showSensorInfo = true },
                        buttonLabel = if (isRu) "Новый сенсор" else "New Sensor"
                    )
                }

                // --- PUMP SET SECTION ---
                if (showPump) {
                    if (showSensor) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DeviceSection(
                        icon = "▣",
                        title = if (isRu) "Инфузионный набор" else "Infusion Set",
                        installedAt = pumpSetStatus.installedAt,
                        expiresAt = pumpSetStatus.expiresAt,
                        millisRemaining = pumpSetStatus.millisRemaining,
                        daysRemaining = pumpSetStatus.daysRemaining,
                        pickerDays = pumpPickerDays,
                        pickerMin = 2,
                        pickerMax = 7,
                        isRu = isRu,
                        onPickerChange = { pumpPickerDays = it },
                        onNewClick = {
                            pendingPumpDays = pumpPickerDays
                            showPumpConfirm = true
                        },
                        onShowInfo = { showPumpInfo = true },
                        buttonLabel = if (isRu) "Новый инфуз. набор" else "New Infusion Set"
                    )
                }

                // --- LANCET SECTION ---
                if (showLancet) {
                    if (showSensor || showPump) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DeviceSection(
                        icon = "📍",
                        title = if (isRu) "Ланцет (прокалыватель)" else "Lancet",
                        installedAt = lancetStatus.installedAt,
                        expiresAt = lancetStatus.expiresAt,
                        millisRemaining = lancetStatus.millisRemaining,
                        daysRemaining = lancetStatus.daysRemaining,
                        pickerDays = lancetPickerDays,
                        pickerMin = 1,
                        pickerMax = 7,
                        isRu = isRu,
                        onPickerChange = { lancetPickerDays = it },
                        onNewClick = {
                            pendingLancetDays = lancetPickerDays
                            showLancetConfirm = true
                        },
                        onShowInfo = { showLancetInfo = true },
                        buttonLabel = if (isRu) "Новый ланцет" else "New Lancet"
                    )
                }

                // Bottom Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isRu) "Закрыть" else "Close")
                    }
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
            onConfirm = { installedAt ->
                showSensorConfirm = false
                onNewSensor(pendingSensorDays, installedAt)
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
            onConfirm = { installedAt ->
                showPumpConfirm = false
                onNewPumpSet(pendingPumpDays, installedAt)
                onDismiss()
            },
            onDismiss = { showPumpConfirm = false }
        )
    }

    // Lancet confirm dialog
    if (showLancetConfirm) {
        CountdownConfirmDialog(
            title = if (isRu) "Новый ланцет" else "New Lancet",
            message = if (isRu) "Подтвердите замену ланцета на $pendingLancetDays дн." else "Confirm new lancet for $pendingLancetDays days.",
            isRu = isRu,
            onConfirm = { installedAt ->
                showLancetConfirm = false
                onNewLancet(pendingLancetDays, installedAt)
                onDismiss()
            },
            onDismiss = { showLancetConfirm = false }
        )
    }

    if (showSensorInfo) {
        AlertDialog(
            onDismissRequest = { showSensorInfo = false },
            title = { Text(if (isRu) "Срок службы сенсора" else "Sensor Lifespan") },
            text = { 
                Text(if (isRu) "Не рекомендуется носить сенсор дольше заявленного срока (обычно 14 дней), так как точность измерений может снизиться, а клей вызвать раздражение кожи." 
                     else "It is not recommended to wear the sensor longer than its specified lifespan (usually 14 days), as accuracy may degrade and adhesive may cause skin irritation.")
            },
            confirmButton = {
                TextButton(onClick = { showSensorInfo = false }) {
                    Text("OK", color = ActionBlue)
                }
            }
        )
    }

    if (showPumpInfo) {
        AlertDialog(
            onDismissRequest = { showPumpInfo = false },
            title = { Text(if (isRu) "Срок службы набора" else "Infusion Set Lifespan") },
            text = { 
                Text(if (isRu) "Не рекомендуется носить инфузионную канюлю дольше 3 дней (для тефлона) или 2 дней (для стали). Это повышает риск воспаления и ухудшения всасывания инсулина.\n\n💡 Автосинхронизация: TIRUp автоматически сбрасывает и обновляет этот счётчик, когда находит в xDrip+ запись с комментарием «канюля», «инфуз» или «cannula»." 
                     else "It is not recommended to wear the infusion cannula longer than 3 days (for teflon) or 2 days (for steel). This increases the risk of inflammation and poor insulin absorption.\n\n💡 Auto-sync: TIRUp automatically resets and updates this counter whenever you log a treatment in xDrip+ with a note like \"cannula\" or \"infusion\".")
            },
            confirmButton = {
                TextButton(onClick = { showPumpInfo = false }) {
                    Text("OK", color = ActionBlue)
                }
            }
        )
    }

    if (showLancetInfo) {
        AlertDialog(
            onDismissRequest = { showLancetInfo = false },
            title = { Text(if (isRu) "Срок службы ланцета" else "Lancet Lifespan") },
            text = { 
                Text(if (isRu) "Рекомендуется менять ланцет не реже 1 раза в неделю. При частом использовании игла тупится, травмирует пальцы и может стать источником микротравм кожи." 
                     else "It is recommended to change lancets at least weekly. Dull needles cause excess pain, calluses, and skin irritation.")
            },
            confirmButton = {
                TextButton(onClick = { showLancetInfo = false }) {
                    Text("OK", color = ActionBlue)
                }
            }
        )
    }
}

@Composable
private fun DeviceSection(
    icon: String,
    title: String,
    installedAt: Long,
    expiresAt: Long,
    millisRemaining: Long,
    daysRemaining: Int,
    pickerDays: Int,
    pickerMin: Int,
    pickerMax: Int,
    isRu: Boolean,
    onPickerChange: (Int) -> Unit,
    onNewClick: () -> Unit,
    onShowInfo: () -> Unit,
    buttonLabel: String
) {
    val isSet = installedAt > 0L
    val isExpired = isSet && millisRemaining <= 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header with title and info icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(icon, fontSize = 16.sp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onShowInfo, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = ActionBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isSet) {
            val color = when {
                isExpired -> MaterialTheme.colorScheme.error
                millisRemaining <= 24 * 3600_000L -> Color(0xFFF59E0B)
                else -> Color(0xFF22C55E)
            }

            val installedStr = SimpleDateFormat("dd.MM.yyyy (HH:mm)", Locale.getDefault()).format(Date(installedAt))
            val expiresStr = SimpleDateFormat("dd.MM.yyyy (HH:mm)", Locale.getDefault()).format(Date(expiresAt))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (isRu) "Уст: $installedStr" else "Inst: $installedStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (isRu) "До: $expiresStr" else "Exp: $expiresStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                val statusText = when {
                    isExpired -> {
                        val hours = (-millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
                        if (isRu) "Просрочен: -$hours ч" else "Expired: -$hours h"
                    }
                    millisRemaining < 3600_000L -> {
                        val mins = (millisRemaining / 60_000L).toInt().coerceAtLeast(1)
                        if (isRu) "Осталось: $mins мин" else "Remaining: $mins min"
                    }
                    millisRemaining < 24 * 3600_000L -> {
                        val hours = (millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
                        if (isRu) "Осталось: $hours ч" else "Remaining: $hours h"
                    }
                    else -> {
                        if (isRu) "Осталось: $daysRemaining дн." else "Remaining: $daysRemaining d"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, color.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Text(
                text = if (isRu) "Нет данных об установке" else "No installation data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Duration picker + install button row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DurationPickerCompact(
                value = pickerDays,
                min = pickerMin,
                max = pickerMax,
                label = if (isRu) "Срок (дн):" else "Days:",
                onChange = onPickerChange,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onNewClick,
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(buttonLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DurationPickerCompact(
    value: Int,
    min: Int,
    max: Int,
    label: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Confirmation dialog with time selection and countdown auto-dismiss (when unedited).
 */
@Composable
fun CountdownConfirmDialog(
    title: String,
    message: String,
    isRu: Boolean,
    onConfirm: (installedAt: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(5) }
    var animProgress by remember { mutableStateOf(1f) }
    var isTimeEdited by remember { mutableStateOf(false) }
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
        label = "countdown_progress"
    )

    LaunchedEffect(isTimeEdited) {
        if (!isTimeEdited) {
            animProgress = 0f
            while (secondsLeft > 0) {
                delay(1000L)
                secondsLeft--
            }
            onDismiss()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("d MMMM, HH:mm", if (isRu) Locale("ru") else Locale.ENGLISH) }

    val isToday = remember(selectedTimestamp) {
        val nowCal = Calendar.getInstance()
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        nowCal.get(Calendar.YEAR) == selCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
    }

    val displayTimeStr = if (isToday) {
        "${if (isRu) "Сегодня" else "Today"}, ${timeFormat.format(Date(selectedTimestamp))}"
    } else {
        dateFormat.format(Date(selectedTimestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = ActionBlue) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isRu) "Время установки:" else "Installation time:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayTimeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = {
                                    isTimeEdited = true
                                    val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val newCal = Calendar.getInstance().apply {
                                                timeInMillis = selectedTimestamp
                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                set(Calendar.MINUTE, minute)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            selectedTimestamp = newCal.timeInMillis
                                        },
                                        currentCal.get(Calendar.HOUR_OF_DAY),
                                        currentCal.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActionBlue.copy(alpha = 0.15f),
                                    contentColor = ActionBlue
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRu) "Изменить" else "Change", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isTimeEdited = true
                                    selectedTimestamp = System.currentTimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isRu) "Сейчас" else "Now", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    isTimeEdited = true
                                    selectedTimestamp = System.currentTimeMillis() - 3600_000L
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isRu) "-1 час" else "-1h", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    isTimeEdited = true
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = selectedTimestamp
                                        add(Calendar.DAY_OF_YEAR, -1)
                                    }
                                    selectedTimestamp = cal.timeInMillis
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isRu) "Вчера" else "Yesterday", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (!isTimeEdited) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTimestamp) },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Подтвердить" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = ActionBlue)) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}

/**
 * In-app alert dialog shown when a device has expired.
 */
@Composable
fun DeviceExpiredAlertDialog(
    deviceType: Int, // 0 = Sensor, 1 = Pump, 2 = Lancet
    isRu: Boolean,
    onDismiss: () -> Unit,
    onInstallNow: () -> Unit
) {
    val title = when (deviceType) {
        0 -> if (isRu) "⚠️ Сенсор CGM истёк" else "⚠️ CGM Sensor Expired"
        1 -> if (isRu) "⚠️ Инфузионный набор истёк" else "⚠️ Infusion Set Expired"
        else -> if (isRu) "⚠️ Ланцет истёк" else "⚠️ Lancet Expired"
    }
    val message = when (deviceType) {
        0 -> if (isRu) "Срок службы сенсора истёк. Рекомендуется установить новый сенсор заранее (1–2 дня на прогрев)."
             else "Sensor has expired. Install a new one early (needs 1–2 days warm-up)."
        1 -> if (isRu) "Срок инфузионного набора истёк. Замените набор немедленно во избежание воспаления и гипергликемии."
             else "Infusion set expired. Replace it immediately to avoid poor absorption and occlusion."
        else -> if (isRu) "Срок использования ланцета истёк. Установите новый ланцет."
             else "Lancet lifespan expired. Replace the lancet."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onInstallNow, colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)) {
                Text(if (isRu) "Установить сейчас" else "Install Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = ActionBlue)) {
                Text(if (isRu) "ОК" else "OK")
            }
        }
    )
}
