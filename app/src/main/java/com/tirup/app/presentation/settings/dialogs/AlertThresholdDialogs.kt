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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.getValue
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            text = if (isRu) "▶️ Снять паузу и включить сейчас" else "▶️ Resume and Enable Now",
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
                        text = if (isRu) "Отмена (Оставить включённым)" else "Cancel (Keep Enabled)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
