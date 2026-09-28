package com.tirup.app.presentation.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.AodDisplayMode
import com.tirup.app.domain.model.AodSettings
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ActionBlue else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) ActionBlue else MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun DropdownHourSelector(
    label: String,
    selectedHour: Int,
    isRu: Boolean = true,
    modifier: Modifier = Modifier,
    onHourSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hourSuffix = if (isRu) " ч" else " h"

    Box(modifier = modifier) {
        OutlinedTextField(
            value = "$selectedHour$hourSuffix",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (0..23).forEach { hr ->
                DropdownMenuItem(
                    text = { Text("$hr$hourSuffix") },
                    onClick = {
                        onHourSelected(hr)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DisplayPreferencesCard(
    settings: UserSettings,
    isRu: Boolean,
    onSetLanguage: (String) -> Unit,
    onSetUnit: (GlucoseUnit) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetShowTreatmentsOnChart: (Boolean) -> Unit,
    onSetShowPredictionOnChart: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.section_preferences),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Language Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.pref_language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageChip(
                        label = "Русский",
                        isSelected = settings.language.equals("RU", ignoreCase = true),
                        onClick = { onSetLanguage("RU") }
                    )
                    LanguageChip(
                        label = "English",
                        isSelected = settings.language.equals("EN", ignoreCase = true),
                        onClick = { onSetLanguage("EN") }
                    )
                }
            }

            // Unit Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.pref_unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageChip(
                        label = "mmol/L",
                        isSelected = settings.unit == GlucoseUnit.MMOL_L,
                        onClick = { onSetUnit(GlucoseUnit.MMOL_L) }
                    )
                    LanguageChip(
                        label = "mg/dL",
                        isSelected = settings.unit == GlucoseUnit.MG_DL,
                        onClick = { onSetUnit(GlucoseUnit.MG_DL) }
                    )
                }
            }

            // Theme Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isRu) "Тема" else "Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageChip(
                        label = if (isRu) "🌙 Тёмная" else "🌙 Dark",
                        isSelected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onSetThemeMode(ThemeMode.DARK) }
                    )
                    LanguageChip(
                        label = if (isRu) "☀️ Светлая" else "☀️ Light",
                        isSelected = settings.themeMode != ThemeMode.DARK,
                        onClick = { onSetThemeMode(ThemeMode.LIGHT) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Show Treatments On Chart Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text("💉🍽️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isRu) "Метки болюсов и еды на графике" else "Insulin & Meal Marks on Chart",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Switch(
                    checked = settings.showTreatmentsOnChart,
                    onCheckedChange = { onSetShowTreatmentsOnChart(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text("🔮", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isRu) "Линия прогноза на графике (25 мин)" else "Trend Forecast on Chart (25m)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Фиолетовые точки и пунктир экстраполяции" else "Purple points & extrapolation trajectory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = settings.showPredictionOnChart,
                    onCheckedChange = { onSetShowPredictionOnChart(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }
        }
    }
}

@Composable
fun WeeklyDigestCard(
    settings: UserSettings,
    isRu: Boolean,
    onSetWeeklyDigestEnabled: (Boolean) -> Unit,
    onTriggerImmediately: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (isRu) "📅 Воскресный дайджест" else "📅 Sunday Digest",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Еженедельный клинический отчёт каждое воскресенье в 20:00 (динамика TIR/TING, вариабельность CV, гипо, сравнение с прошлой неделей)"
                        else "Weekly clinical summary every Sunday at 8:00 PM (TIR/TING dynamics, CV, hypos, and week-over-week comparison)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = settings.isWeeklyDigestEnabled,
                    onCheckedChange = { isChecked ->
                        onSetWeeklyDigestEnabled(isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }

            if (settings.isWeeklyDigestEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTriggerImmediately,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                ) {
                    Text(
                        text = if (isRu) "Сформировать сейчас вручную" else "Generate digest now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceRemindersCard(
    settings: UserSettings,
    isRu: Boolean,
    onSetDeviceRemindersEnabled: (Boolean) -> Unit,
    onSetSensorReminderEnabled: (Boolean) -> Unit,
    onSetPumpReminderEnabled: (Boolean) -> Unit,
    onSetLancetReminderEnabled: (Boolean) -> Unit,
    onSetHba1cReminderEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (isRu) "Напоминания об устройствах" else "Device Reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Уведомления о замене сенсора CGM, инфузионного набора и ланцета"
                        else "Notifications for CGM sensor, infusion set, and lancet changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = settings.isDeviceRemindersEnabled,
                    onCheckedChange = { isChecked ->
                        onSetDeviceRemindersEnabled(isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }

            if (settings.isDeviceRemindersEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sensor checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSetSensorReminderEnabled(!settings.isSensorReminderEnabled) }
                    ) {
                        Checkbox(
                            checked = settings.isSensorReminderEnabled,
                            onCheckedChange = { onSetSensorReminderEnabled(it) },
                            colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                            modifier = Modifier
                                .scale(0.85f)
                                .size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isRu) "Сенсор" else "Sensor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Infusion set checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSetPumpReminderEnabled(!settings.isPumpReminderEnabled) }
                    ) {
                        Checkbox(
                            checked = settings.isPumpReminderEnabled,
                            onCheckedChange = { onSetPumpReminderEnabled(it) },
                            colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                            modifier = Modifier
                                .scale(0.85f)
                                .size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isRu) "Инф. набор" else "Inf. set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Lancet checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSetLancetReminderEnabled(!settings.isLancetReminderEnabled) }
                    ) {
                        Checkbox(
                            checked = settings.isLancetReminderEnabled,
                            onCheckedChange = { onSetLancetReminderEnabled(it) },
                            colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                            modifier = Modifier
                                .scale(0.85f)
                                .size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isRu) "Ланцет" else "Lancet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // HbA1c 90-day Checkup Reminder Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (isRu) "Контроль HbA1c (раз в 90 дней)" else "HbA1c Checkup (every 90 days)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRu) "Напоминание о сдаче крови на гликированный гемоглобин и сверка с 90-дневным GMI"
                               else "Quarterly reminder to test lab HbA1c and correlate with 90-day sensor GMI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = settings.isHba1cReminderEnabled,
                    onCheckedChange = { isChecked ->
                        onSetHba1cReminderEnabled(isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }
        }
    }
}

@Composable
fun ClinicalStandardsCard(
    settings: UserSettings,
    isRu: Boolean,
    onUpdateNightHours: (nightStart: Int, nightEnd: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isRu) "Клинические стандарты (ATTD / ADA)" else "Clinical Standards (ATTD / ADA)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Informational standard badge
            val isMmol = settings.unit == GlucoseUnit.MMOL_L
            val tirRangeStr = if (isMmol) (if (isRu) "3.9 — 10.0 ммоль/л" else "3.9 — 10.0 mmol/L") else "70 — 180 mg/dL"
            val tingRangeStr = if (isMmol) (if (isRu) "3.9 — 7.8 ммоль/л" else "3.9 — 7.8 mmol/L") else "70 — 140 mg/dL"

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "TIR (цель ≥70%):" else "TIR (target ≥70%):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tirRangeStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "TING (цель ≥50%):" else "TING (target ≥50%):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tingRangeStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorTight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isRu) "Ночной профиль (окно сна)" else "Night Profile (Sleep Window)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRu) "Приблизительные часы сна (с шагом в 1 час)" else "Approximate sleep hours (1-hour step)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Night Profile Hours (Sleep window)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DropdownHourSelector(
                    label = if (isRu) "Начало сна" else "Sleep Start",
                    selectedHour = settings.nightStartHour,
                    isRu = isRu,
                    modifier = Modifier.weight(1f),
                    onHourSelected = { newStart ->
                        onUpdateNightHours(newStart, settings.nightEndHour)
                    }
                )

                DropdownHourSelector(
                    label = if (isRu) "Конец сна" else "Sleep End",
                    selectedHour = settings.nightEndHour,
                    isRu = isRu,
                    modifier = Modifier.weight(1f),
                    onHourSelected = { newEnd ->
                        onUpdateNightHours(settings.nightStartHour, newEnd)
                    }
                )
            }
        }
    }
}

@Composable
fun LockscreenNotificationCard(
    settings: UserSettings,
    isRu: Boolean,
    onSetLockscreenNotificationEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRu) "Уведомление на экране блокировки" else "Lockscreen Notification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Постоянный статус с сахаром, стрелкой тренда и TIR на экране блокировки и в панели уведомлений"
                        else "Ongoing status with current glucose, trend arrow and TIR on lockscreen and notification shade",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = settings.isLockscreenNotificationEnabled,
                    onCheckedChange = { onSetLockscreenNotificationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }
        }
    }
}

@Composable
fun FloatingGlucoseBubbleCard(
    settings: UserSettings,
    isRu: Boolean,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleFloatingBubbleAlwaysVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRu) "Плавающий пузырёк с сахаром" else "Floating Glucose Bubble",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Появляется только вне нормы (<3.9 или >10.0). При гипо (<3.9) пульсирует волнами. Тап глушит звук и скрывает на 15 мин (гипо) / 45 мин (гипер, до 60 мин при IoB). Свободно перемещается"
                        else "Shown only out of range (<3.9 or >10.0). Ripple pulse waves on hypo (<3.9). Tap silences and snoozes for 15m (hypo) / 45m (hyper, up to 60m with IoB). Draggable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = settings.isFloatingBubbleEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                onToggleFloatingBubble(true)
                            }
                        } else {
                            onToggleFloatingBubble(false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }

            if (settings.isFloatingBubbleEnabled) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Отображать постоянно" else "Always visible",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRu) "В норме (3.9–10.0) — мини-кружок (50%), тап открывает TIRUp, удержание 3 сек отключает. Вне нормы — тревожный режим (тап глушит/снузит, удержание открывает TIRUp)"
                            else "In target (3.9–10.0) — mini-circle (50%), tap opens TIRUp, 3s hold turns off. Out of range — alarm mode (tap silences/snoozes, hold opens TIRUp)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = settings.isFloatingBubbleAlwaysVisible,
                        onCheckedChange = { isChecked ->
                            onToggleFloatingBubbleAlwaysVisible(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AlwaysOnDisplayCard(
    settings: UserSettings,
    isRu: Boolean,
    onUpdateAodSettings: (AodSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAodExpanded by rememberSaveable { mutableStateOf(false) }
    val aod = settings.aodSettings

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAodExpanded = !isAodExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌙", fontSize = 22.sp)
                    Column {
                        Text(
                            text = if (isRu) "Ночной экран (AoD)" else "Night screen (AoD)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (aod.isEnabled) {
                                if (isRu) "Активен • ${if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) "Пробуждение при замере (0% батареи)" else "Всегда включен (1% яркости)"}"
                                else "Enabled • ${if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) "Pulse on update (0% battery)" else "Always on (1% brightness)"}"
                            } else {
                                if (isRu) "Выключен" else "Disabled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (aod.isEnabled) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = aod.isEnabled,
                        onCheckedChange = { isEnabled ->
                            onUpdateAodSettings(aod.copy(isEnabled = isEnabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryEmerald
                        )
                    )
                    IconButton(
                        onClick = { isAodExpanded = !isAodExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isAodExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isAodExpanded) "Свернуть" else "Развернуть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isAodExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // OLED Notice Alert (Adaptive to light/dark themes)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Text(
                            text = if (isRu) "Режим оптимизирован под экраны AMOLED/OLED: абсолютно черный фон (#000000) полностью отключает пиксели матрицы, а микро-смещение Anti-Burn-In защищает экран от выгорания."
                            else "Optimized for AMOLED/OLED: true black background (#000000) turns off matrix pixels, and Anti-Burn-In micro-jitter protects display.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Display Mode Selector
                Text(
                    text = if (isRu) "Режим работы дисплея:" else "Display Mode:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onUpdateAodSettings(aod.copy(displayMode = AodDisplayMode.PULSE_ON_UPDATE))
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) 1.5.dp else 0.8.dp,
                            color = if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isRu) "⚡ Просыпаться" else "⚡ Pulse Wake",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (aod.displayMode == AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Экран 0% света. Зажигается на 5 сек при новом сахаре или тапе" else "0% light. Turns on for 5s on new reading or tap",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onUpdateAodSettings(aod.copy(displayMode = AodDisplayMode.ALWAYS_ON))
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (aod.displayMode == AodDisplayMode.ALWAYS_ON) ActionBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (aod.displayMode == AodDisplayMode.ALWAYS_ON) 1.5.dp else 0.8.dp,
                            color = if (aod.displayMode == AodDisplayMode.ALWAYS_ON) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isRu) "👁️ Всегда включен" else "👁️ Always On",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (aod.displayMode == AodDisplayMode.ALWAYS_ON) ActionBlue else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Непрерывно горит на минимальной физической яркости (1%)" else "Continuously visible at minimum physical brightness (1%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Auto-start on charger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "🔌 Автозапуск при ночной зарядке" else "🔌 Auto-start on night charge",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Активировать AOD при подключении зарядного устройства с ${aod.autoChargeStartHour}:00 до ${aod.autoChargeEndHour}:00"
                            else "Launch AOD automatically when plugged in between ${aod.autoChargeStartHour}:00 and ${aod.autoChargeEndHour}:00",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aod.autoChargeEnabled,
                        onCheckedChange = { autoCharge ->
                            onUpdateAodSettings(aod.copy(autoChargeEnabled = autoCharge))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

                // Launch Test Button
                Button(
                    onClick = {
                        val aodIntent = Intent(context, com.tirup.app.presentation.aod.AodActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(aodIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isRu) "🌙 Запустить AOD сейчас для проверки" else "🌙 Launch AOD now for test",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun WidgetPreviewCard(
    settings: UserSettings,
    isRu: Boolean,
    onUpdateWidgetBackgroundOpacity: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRu) "Прозрачность подложки виджетов" else "Widget Background Opacity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "Плавная регулировка прозрачности под ваши обои" else "Adjust transparency to match your home wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${settings.widgetBackgroundOpacity}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Interactive Preview Box on simulated wallpaper (5x1 Strip Widget)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F2027),
                                Color(0xFF203A43),
                                Color(0xFF2C5364)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A).copy(alpha = settings.widgetBackgroundOpacity / 100f),
                    border = BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = (settings.widgetBackgroundOpacity / 100f) * 0.22f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Left: Glucose + stacked [ Arrow / Delta ]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "5.8",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "→",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "+0.2",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // 2. TIR & Compensator
                        Column(verticalArrangement = Arrangement.Center) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "TIR: 84%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isRu) "+1ч 45м" else "+1h 45m",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // 3. Ranges TBR / TAR
                        Column(verticalArrangement = Arrangement.Center) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "TBR: 1%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "TAR: 15%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // 4. Treatments IoB & CoB
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💉 1.2 U",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = "🍞 25g",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        // 5. Telemetry: Age & Battery
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isRu) "2м наз." else "2m ago",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "🔋 95%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            // Slider from 0 to 100%
            Slider(
                value = settings.widgetBackgroundOpacity.toFloat(),
                onValueChange = { newVal ->
                    onUpdateWidgetBackgroundOpacity(newVal.toInt())
                },
                valueRange = 0f..100f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = ActionBlue,
                    activeTrackColor = ActionBlue,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isRu) "0% (Текст)" else "0% (Text)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isRu) "85% (Стандарт)" else "85% (Default)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isRu) "100% (Глубокий)" else "100% (Solid)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
