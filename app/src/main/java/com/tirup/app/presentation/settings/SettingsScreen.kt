package com.tirup.app.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Calendar
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.userSettings
    val profile = settings.patientProfile
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Настройки сохраняются автоматически",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Section 1: Patient Profile with Dropdowns and Dynamic Calculations
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Профиль пациента (для мед. отчётов)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 1. Full Name (Text input)
                    OutlinedTextField(
                        value = profile.fullName,
                        onValueChange = { newName ->
                            viewModel.autoUpdatePatientProfile(profile.copy(fullName = newName))
                        },
                        label = { Text("ФИО пациента") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 2. Birth Year (with live calculated age)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownYearSelector(
                            label = "Год рождения",
                            selectedYear = profile.birthYear,
                            yearRange = (currentYear - 100)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newYear ->
                                viewModel.autoUpdatePatientProfile(profile.copy(birthYear = newYear))
                            }
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Возраст", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("${profile.calculatedAge} лет", style = MaterialTheme.typography.bodyMedium, color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3. Height & Weight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = profile.heightCm,
                            onValueChange = { newHeight ->
                                viewModel.autoUpdatePatientProfile(profile.copy(heightCm = newHeight))
                            },
                            label = { Text("Рост (см)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = profile.weightKg,
                            onValueChange = { newWeight ->
                                viewModel.autoUpdatePatientProfile(profile.copy(weightKg = newWeight))
                            },
                            label = { Text("Вес (кг)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // 4. Diabetes Type & Diagnosis Year (with live duration)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownChoiceSelector(
                            label = "Тип диабета",
                            selectedOption = profile.diabetesType,
                            options = listOf("СД1", "СД2", "LADA", "MODY", "ГСД"),
                            modifier = Modifier.weight(1f),
                            onOptionSelected = { newType ->
                                viewModel.autoUpdatePatientProfile(profile.copy(diabetesType = newType))
                            }
                        )

                        DropdownYearSelector(
                            label = "Диагноз с года",
                            selectedYear = profile.diagnosisYear,
                            yearRange = (currentYear - 60)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newDiagYear ->
                                viewModel.autoUpdatePatientProfile(profile.copy(diagnosisYear = newDiagYear))
                            }
                        )
                    }

                    // 5. Therapy Type Dropdown
                    DropdownChoiceSelector(
                        label = "Вид терапии",
                        selectedOption = profile.therapyType,
                        options = listOf(
                            "Инсулиновая помпа",
                            "Шприц-ручки (МДИ)",
                            "Пероральные препараты (Таблетки)",
                            "Диетотерапия"
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onOptionSelected = { newTherapy ->
                            viewModel.autoUpdatePatientProfile(profile.copy(therapyType = newTherapy))
                        }
                    )
                }
            }
        }

        // Section 2: Preferences (Language & Unit)
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.pref_language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = "Русский",
                                isSelected = settings.language.equals("RU", ignoreCase = true),
                                onClick = { viewModel.setLanguage("RU") }
                            )
                            LanguageChip(
                                label = "English",
                                isSelected = settings.language.equals("EN", ignoreCase = true),
                                onClick = { viewModel.setLanguage("EN") }
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
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.pref_unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = "mmol/L",
                                isSelected = settings.unit == GlucoseUnit.MMOL_L,
                                onClick = { viewModel.setUnit(GlucoseUnit.MMOL_L) }
                            )
                            LanguageChip(
                                label = "mg/dL",
                                isSelected = settings.unit == GlucoseUnit.MG_DL,
                                onClick = { viewModel.setUnit(GlucoseUnit.MG_DL) }
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
                            Icon(imageVector = Icons.Default.Brightness4, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Тема", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LanguageChip(
                                label = "🌙 Тёмная",
                                isSelected = settings.themeMode == com.tirup.app.domain.model.ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.DARK) }
                            )
                            LanguageChip(
                                label = "☀️ Светлая",
                                isSelected = settings.themeMode == com.tirup.app.domain.model.ThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.LIGHT) }
                            )
                            LanguageChip(
                                label = "⚙️ Авто",
                                isSelected = settings.themeMode == com.tirup.app.domain.model.ThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.SYSTEM) }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Target Thresholds & Sleep Window
        item {
            var tirLow by remember(settings.targetRanges.tirLowMmol) { mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tirLowMmol)) }
            var tirHigh by remember(settings.targetRanges.tirHighMmol) { mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tirHighMmol)) }
            var tingHigh by remember(settings.targetRanges.tingHighMmol) { mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tingHighMmol)) }
            var tirGoal by remember(settings.targetRanges.tirGoalPercent) { mutableStateOf(settings.targetRanges.tirGoalPercent.toString()) }

            fun triggerThresholdUpdate() {
                val low = tirLow.toDoubleOrNull() ?: 3.9
                val high = tirHigh.toDoubleOrNull() ?: 10.0
                val tHigh = tingHigh.toDoubleOrNull() ?: 7.8
                val goal = tirGoal.toIntOrNull() ?: 70
                viewModel.autoUpdateThresholds(
                    tirLow = low,
                    tirHigh = high,
                    tingHigh = tHigh,
                    tirGoal = goal,
                    tingGoal = 50,
                    nightStart = settings.nightStartHour,
                    nightEnd = settings.nightEndHour
                )
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.section_targets),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tirLow,
                            onValueChange = {
                                tirLow = it
                                triggerThresholdUpdate()
                            },
                            label = { Text(stringResource(R.string.target_tir_low)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tirHigh,
                            onValueChange = {
                                tirHigh = it
                                triggerThresholdUpdate()
                            },
                            label = { Text(stringResource(R.string.target_tir_high)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tingHigh,
                            onValueChange = {
                                tingHigh = it
                                triggerThresholdUpdate()
                            },
                            label = { Text("Верх TING (узкий)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tirGoal,
                            onValueChange = {
                                tirGoal = it
                                triggerThresholdUpdate()
                            },
                            label = { Text(stringResource(R.string.target_tir_percent)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Night Profile Hours (Sleep window)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownHourSelector(
                            label = "Начало сна",
                            selectedHour = settings.nightStartHour,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newStart ->
                                viewModel.autoUpdateThresholds(
                                    tirLow = settings.targetRanges.tirLowMmol,
                                    tirHigh = settings.targetRanges.tirHighMmol,
                                    tingHigh = settings.targetRanges.tingHighMmol,
                                    tirGoal = settings.targetRanges.tirGoalPercent,
                                    tingGoal = settings.targetRanges.tingGoalPercent,
                                    nightStart = newStart,
                                    nightEnd = settings.nightEndHour
                                )
                            }
                        )

                        DropdownHourSelector(
                            label = "Конец сна",
                            selectedHour = settings.nightEndHour,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newEnd ->
                                viewModel.autoUpdateThresholds(
                                    tirLow = settings.targetRanges.tirLowMmol,
                                    tirHigh = settings.targetRanges.tirHighMmol,
                                    tingHigh = settings.targetRanges.tingHighMmol,
                                    tirGoal = settings.targetRanges.tirGoalPercent,
                                    tingGoal = settings.targetRanges.tingGoalPercent,
                                    nightStart = settings.nightStartHour,
                                    nightEnd = newEnd
                                )
                            }
                        )
                    }
                }
            }
        }

        // Section 4: Data Management (Clear Data)
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.clear_data),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.clear_data_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedButton(
                        onClick = { viewModel.showClearConfirm(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ColorVeryLow),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ColorVeryLow
                        )
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.clear_data))
                    }

                    if (state.infoMessage != null) {
                        Text(
                            text = state.infoMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryEmerald
                        )
                    }
                }
            }
        }

        // Section 5: Community Telegram Text Link
        item {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Telegram-канал проекта — @diakia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0284C7),
                    style = TextStyle(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/diakia"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        // Section 6: Bottom Back Button
        item {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryEmerald,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Назад",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearConfirm(false) },
            title = { Text(text = stringResource(R.string.clear_data)) },
            text = { Text(text = stringResource(R.string.clear_data_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData() }
                ) {
                    Text(text = stringResource(R.string.action_confirm), color = ColorVeryLow)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showClearConfirm(false) }
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun DropdownYearSelector(
    label: String,
    selectedYear: Int,
    yearRange: IntProgression,
    modifier: Modifier = Modifier,
    onYearSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedYear.toString(),
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
            yearRange.reversed().forEach { yr ->
                DropdownMenuItem(
                    text = { Text("$yr г.") },
                    onClick = {
                        onYearSelected(yr)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DropdownHourSelector(
    label: String,
    selectedHour: Int,
    modifier: Modifier = Modifier,
    onHourSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = String.format(Locale.US, "%02d:00", selectedHour),
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
                    text = { Text(String.format(Locale.US, "%02d:00", hr)) },
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
fun DropdownChoiceSelector(
    label: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedOption,
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
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onOptionSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) PrimaryEmerald else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

