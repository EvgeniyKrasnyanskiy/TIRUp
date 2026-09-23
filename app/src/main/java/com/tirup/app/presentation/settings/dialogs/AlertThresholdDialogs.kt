package com.tirup.app.presentation.settings.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun MainThresholdDialog(
    initialLow: Double,
    initialHigh: Double,
    isRu: Boolean,
    onSave: (low: Double, high: Double) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var lowVal by remember { mutableStateOf(initialLow) }
    var highVal by remember { mutableStateOf(initialHigh) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isRu) "Диапазон основных тревог" else "Main Alert Thresholds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (isRu) "Срабатывает при подтверждении 5 точек подряд за пределами заданного диапазона."
                    else "Triggers when 5 consecutive readings fall outside this range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Low threshold
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "Порог гипогликемии:" else "Low threshold:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f ммоль/л", lowVal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorLow
                        )
                    }
                    Slider(
                        value = lowVal.toFloat(),
                        onValueChange = { lowVal = (Math.round(it * 10.0) / 10.0) },
                        valueRange = 3.0f..5.0f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorLow,
                            activeTrackColor = ColorLow
                        )
                    )
                }

                // High threshold
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "Порог гипергликемии:" else "High threshold:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f ммоль/л", highVal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorHigh
                        )
                    }
                    Slider(
                        value = highVal.toFloat(),
                        onValueChange = { highVal = (Math.round(it * 10.0) / 10.0) },
                        valueRange = 7.0f..15.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorHigh,
                            activeTrackColor = ColorHigh
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(lowVal, highVal)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Сохранить" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onResetDefault()
                    onDismiss()
                }
            ) {
                Text(if (isRu) "Сброс к норме (3.9 - 10.0)" else "Default (3.9 - 10.0)")
            }
        }
    )
}

@Composable
fun CriticalThresholdDialog(
    initialLow: Double,
    initialHigh: Double,
    isRu: Boolean,
    onSave: (low: Double, high: Double) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var lowVal by remember { mutableStateOf(initialLow) }
    var highVal by remember { mutableStateOf(initialHigh) }
    val currentlyPlayingTag by com.tirup.app.data.alert.MedicalSoundPlayer.currentlyPlayingTag.collectAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val handleSoundClick: (String, () -> Unit) -> Unit = { tag, action ->
        val now = System.currentTimeMillis()
        if (now - lastClickTime >= 400L) {
            lastClickTime = now
            if (currentlyPlayingTag == tag) {
                com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
            } else {
                com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
                action()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
            onDismiss()
        },
        title = {
            Text(
                text = if (isRu) "Пороги критических тревог" else "Critical Alert Thresholds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (isRu) "При выходе за эти границы включается громкая сирена, полноэкранное окно спасения поверх блокировки и отсчёт таймера SOS фоловерам."
                    else "Crossing these thresholds triggers maximum loud siren, full-screen rescue window over lockscreen, and follower SOS countdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Critical Low threshold
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "Критическая гипогликемия:" else "Critical hypoglycemia:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f ммоль/л", lowVal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorVeryLow
                        )
                    }
                    Slider(
                        value = lowVal.toFloat(),
                        onValueChange = { lowVal = (Math.round(it * 10.0) / 10.0) },
                        valueRange = 2.5f..4.5f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorVeryLow,
                            activeTrackColor = ColorVeryLow
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("2.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (isRu) "По умолчанию: 3.0" else "Default: 3.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val isHypoPlaying = (currentlyPlayingTag == "EXTRA_HYPO")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow).copy(alpha = 0.35f)),
                            modifier = Modifier.clickable {
                                handleSoundClick("EXTRA_HYPO") { com.tirup.app.data.alert.MedicalSoundPlayer.playExtraHypoSiren() }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHypoPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isHypoPlaying) (if (isRu) "⏹️ Стоп" else "⏹️ Stop")
                                           else (if (isRu) "Тест Экстра-ГИПО (50с)" else "Test Extra-HYPO (50s)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow
                                )
                            }
                        }
                    }
                }

                // Critical High threshold
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "Критическая гипергликемия:" else "Critical hyperglycemia:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f ммоль/л", highVal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorHigh
                        )
                    }
                    Slider(
                        value = highVal.toFloat(),
                        onValueChange = { highVal = (Math.round(it * 10.0) / 10.0) },
                        valueRange = 11.0f..16.0f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorHigh,
                            activeTrackColor = ColorHigh
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("11.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (isRu) "По умолчанию: 13.9" else "Default: 13.9", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("16.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val isHyperPlaying = (currentlyPlayingTag == "EXTRA_HYPER")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh).copy(alpha = 0.35f)),
                            modifier = Modifier.clickable {
                                handleSoundClick("EXTRA_HYPER") { com.tirup.app.data.alert.MedicalSoundPlayer.playExtraHyperAlarm() }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHyperPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isHyperPlaying) (if (isRu) "⏹️ Стоп" else "⏹️ Stop")
                                           else (if (isRu) "Тест Экстра-ГИПЕР (16с)" else "Test Extra-HYPER (16s)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
                    onSave(lowVal, highVal)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Сохранить" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
                    onResetDefault()
                    onDismiss()
                }
            ) {
                Text(if (isRu) "Сброс к норме (<3.0 / >13.9)" else "Default (<3.0 / >13.9)")
            }
        }
    )
}

@Composable
fun PredictiveHorizonDialog(
    currentMinutesAhead: Int,
    isRu: Boolean,
    onSelectMinutes: (Int) -> Unit,
    onInfoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(10, 15, 20, 25, 30, 35, 40)
    var selectedMinutes by remember(currentMinutesAhead) { mutableStateOf(currentMinutesAhead) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ActionBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Горизонт предиктивной тревоги" else "Predictive Alert Horizon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ActionBlue)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRu) "За сколько минут алгоритм предупреждает о прогнозируемом выходе за границы диапазона:"
                           else "How many minutes in advance the algorithm alerts before predicted limit crossing:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { min ->
                        val isSelected = selectedMinutes == min
                        val isDefault = min == 15
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ActionBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ActionBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMinutes = min }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isRu) "$min минут" else "$min minutes",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ActionBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isRu) "стандарт" else "default",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryEmerald,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ActionBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectMinutes(selectedMinutes)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Применить" else "Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}

@Composable
fun PredictiveInfoDialog(
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Горизонт предиктивной тревоги" else "Predictive Alert Horizon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = if (isRu) "Чем меньше горизонт, тем точнее предсказание. 10 мин — высокая точность, 20 мин — умеренная." else "The shorter the horizon, the more accurate the prediction. 10 min = high accuracy, 20 min = moderate.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun CriticalHypoSafetyDialog(
    alertSettings: AlertSettings,
    isRu: Boolean,
    onResumeAndEnable: () -> Unit,
    onPauseTwoHours: () -> Unit,
    onDisablePermanently: () -> Unit,
    onDismiss: () -> Unit
) {
    var isAcknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ColorVeryLow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Защита от тяжёлой гипогликемии" else "Severe Hypo Safety Guard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isRu) "Критическая сирена предупреждает о падении сахара ниже 3.0 ммоль/л и спасает от потери сознания и комы во сне.\n\nВ соответствии с клиническими стандартами безопасности рекомендуется ставить оповещение на временную паузу."
                           else "The critical siren alerts you when glucose drops below 3.0 mmol/L, preventing nocturnal unconsciousness and coma.\n\nPer clinical safety guidelines, a temporary pause is strongly recommended over permanent disabling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAcknowledged = !isAcknowledged }
                            .padding(10.dp)
                    ) {
                        Checkbox(
                            checked = isAcknowledged,
                            onCheckedChange = { isAcknowledged = it },
                            colors = CheckboxDefaults.colors(checkedColor = ColorVeryLow)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "Я осознаю смертельный риск гипогликемической комы и беру ответственность на себя"
                                   else "I acknowledge the life-threatening risk of severe hypoglycemia and assume full responsibility",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (alertSettings.criticalHypoPauseUntilTimestamp > System.currentTimeMillis()) {
                    Button(
                        onClick = {
                            onResumeAndEnable()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text(
                            text = if (isRu) "▶️ Снять паузу" else "▶️ Resume",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onPauseTwoHours()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text(
                            text = if (isRu) "⏸️ Приостановить на 2 часа" else "⏸️ Pause for 2 Hours",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (isAcknowledged) {
                    OutlinedButton(
                        onClick = {
                            onDisablePermanently()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorVeryLow),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorVeryLow)
                    ) {
                        Text(
                            text = if (isRu) "Отключить навсегда" else "Disable Permanently",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isRu) "Отмена (Оставить паузу)" else "Cancel (Keep Pause)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun ExtraAlertSoundsInfoDialog(
    lowThresholdMmol: Double,
    highThresholdMmol: Double,
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    val currentlyPlayingTag by com.tirup.app.data.alert.MedicalSoundPlayer.currentlyPlayingTag.collectAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val handleSoundClick: (String, () -> Unit) -> Unit = { tag, action ->
        val now = System.currentTimeMillis()
        if (now - lastClickTime >= 400L) {
            lastClickTime = now
            if (currentlyPlayingTag == tag) {
                com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
            } else {
                com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
                action()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "ℹ️", fontSize = 22.sp)
                Text(
                    text = if (isRu) "Экстра звуки тревог" else "Extra Alert Sounds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isRu)
                        "Для экстремальных значений гликемии в TIRUp предусмотрены уникальные математически синтезированные звуки тревог, резко отличающиеся от стандартных сигналов:"
                    else
                        "For extreme glucose excursions, TIRUp features dedicated mathematically synthesized alert sounds distinct from standard chimes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Extra-HYPO card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ColorVeryLow.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ColorVeryLow.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRu) "🚨 Экстра-ГИПО (< ${String.format(Locale.US, "%.1f", lowThresholdMmol)} ммоль/л)"
                                   else "🚨 Extra-HYPO (< ${String.format(Locale.US, "%.1f", lowThresholdMmol)} mmol/L)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorVeryLow
                        )
                        Text(
                            text = if (isRu)
                                "50-секундная мощная сирена ГО / GDH с плавной частотной модуляцией (450–850 Гц) со второй гармоникой на 100% громкости. Срабатывает мгновенно по первой точке для гарантированного пробуждения из глубокого сна как пациента, так и фоловера."
                            else
                                "50-second continuous civil defense air-raid siren sweeping smoothly between 450 Hz and 850 Hz with 2nd harmonic. Triggers immediately to wake patient or follower.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val isHypoPlaying = (currentlyPlayingTag == "EXTRA_HYPO")
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, (if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow).copy(alpha = 0.45f)),
                                modifier = Modifier.clickable {
                                    handleSoundClick("EXTRA_HYPO") {
                                        com.tirup.app.data.alert.MedicalSoundPlayer.playExtraHypoSiren()
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHypoPlaying) Icons.Default.Close else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isHypoPlaying) (if (isRu) "⏹️ Стоп" else "⏹️ Stop")
                                               else (if (isRu) "Тест сирены (50с)" else "Test siren (50s)"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isHypoPlaying) MaterialTheme.colorScheme.error else ColorVeryLow
                                    )
                                }
                            }
                        }
                    }
                }

                // Extra-HYPER card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ColorHigh.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ColorHigh.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRu) "⚠️ Экстра-ГИПЕР (> ${String.format(Locale.US, "%.1f", highThresholdMmol)} ммоль/л)"
                                   else "⚠️ Extra-HYPER (> ${String.format(Locale.US, "%.1f", highThresholdMmol)} mmol/L)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorHigh
                        )
                        Text(
                            text = if (isRu)
                                "16-секундный резкий пульсирующий сигнал высокой тональности (1760/2349 Гц), резко контрастирующий с сиреной гипогликемии. Предупреждает о критической гипергликемии и необходимости контроля подколки/кетонов."
                            else
                                "16-second piercing high-frequency pulsed alert (1760/2349 Hz), sharply distinguishing hyperglycemia from hypo alarms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val isHyperPlaying = (currentlyPlayingTag == "EXTRA_HYPER")
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, (if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh).copy(alpha = 0.45f)),
                                modifier = Modifier.clickable {
                                    handleSoundClick("EXTRA_HYPER") {
                                        com.tirup.app.data.alert.MedicalSoundPlayer.playExtraHyperAlarm()
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHyperPlaying) Icons.Default.Close else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isHyperPlaying) (if (isRu) "⏹️ Стоп" else "⏹️ Stop")
                                               else (if (isRu) "Тест сигнала (16с)" else "Test alarm (16s)"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isHyperPlaying) MaterialTheme.colorScheme.error else ColorHigh
                                    )
                                }
                            }
                        }
                    }
                }

                // Threshold configuration note
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚙️", fontSize = 14.sp)
                        Text(
                            text = if (isRu) "Пороги включения этих сигналов настраиваются в соседней плашке диапазонов."
                                   else "Activation thresholds are adjusted in the adjacent range badge.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    com.tirup.app.data.alert.MedicalSoundPlayer.stopAll()
                    onDismiss()
                }
            ) {
                Text(text = if (isRu) "Понятно" else "Got it", fontWeight = FontWeight.Bold, color = ActionBlue)
            }
        }
    )
}
