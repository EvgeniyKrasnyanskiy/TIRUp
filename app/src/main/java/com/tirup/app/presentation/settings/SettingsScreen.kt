package com.tirup.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tirup.app.R
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextPrimaryDark
import com.tirup.app.presentation.theme.TextSecondaryDark
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.userSettings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimaryDark
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimaryDark
                )
            }
        }

        // Section 1: Patient Profile (for Medical Reports)
        item {
            var fullName by remember(settings.patientProfile.fullName) { mutableStateOf(settings.patientProfile.fullName) }
            var age by remember(settings.patientProfile.age) { mutableStateOf(settings.patientProfile.age) }
            var heightCm by remember(settings.patientProfile.heightCm) { mutableStateOf(settings.patientProfile.heightCm) }
            var weightKg by remember(settings.patientProfile.weightKg) { mutableStateOf(settings.patientProfile.weightKg) }
            var diabetesType by remember(settings.patientProfile.diabetesType) { mutableStateOf(settings.patientProfile.diabetesType) }
            var diabetesDuration by remember(settings.patientProfile.diabetesDurationYears) { mutableStateOf(settings.patientProfile.diabetesDurationYears) }
            var therapyType by remember(settings.patientProfile.therapyType) { mutableStateOf(settings.patientProfile.therapyType) }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Профиль пациента (для отчётов)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("ФИО пациента") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            label = { Text("Возраст (лет)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it },
                            label = { Text("Вес (кг)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it },
                            label = { Text("Рост (см)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = diabetesType,
                            onValueChange = { diabetesType = it },
                            label = { Text("Тип (СД1, СД2)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = diabetesDuration,
                            onValueChange = { diabetesDuration = it },
                            label = { Text("Стаж (лет)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = therapyType,
                        onValueChange = { therapyType = it },
                        label = { Text("Терапия (Помпа / Шприц-ручки / Таблетки)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            viewModel.updatePatientProfile(
                                PatientProfile(
                                    fullName = fullName.trim(),
                                    age = age.trim(),
                                    heightCm = heightCm.trim(),
                                    weightKg = weightKg.trim(),
                                    diabetesType = diabetesType.trim(),
                                    diabetesDurationYears = diabetesDuration.trim(),
                                    therapyType = therapyType.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Сохранить профиль", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Preferences (Language & Unit)
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(R.string.section_preferences),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
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
                            Text(text = stringResource(R.string.pref_language), style = MaterialTheme.typography.bodyMedium, color = TextSecondaryDark)
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
                            Text(text = stringResource(R.string.pref_unit), style = MaterialTheme.typography.bodyMedium, color = TextSecondaryDark)
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
                }
            }
        }

        // Section 3: Clinical Target Thresholds & Sleep Window
        item {
            var tirLow by remember(settings.targetRanges.tirLowMmol) {
                mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tirLowMmol))
            }
            var tirHigh by remember(settings.targetRanges.tirHighMmol) {
                mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tirHighMmol))
            }
            var tingHigh by remember(settings.targetRanges.tingHighMmol) {
                mutableStateOf(String.format(Locale.US, "%.1f", settings.targetRanges.tingHighMmol))
            }
            var tirGoal by remember(settings.targetRanges.tirGoalPercent) {
                mutableStateOf(settings.targetRanges.tirGoalPercent.toString())
            }
            var tingGoal by remember(settings.targetRanges.tingGoalPercent) {
                mutableStateOf(settings.targetRanges.tingGoalPercent.toString())
            }
            var nightStart by remember(settings.nightStartHour) {
                mutableStateOf(String.format(Locale.US, "%02d:00", settings.nightStartHour))
            }
            var nightEnd by remember(settings.nightEndHour) {
                mutableStateOf(String.format(Locale.US, "%02d:00", settings.nightEndHour))
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.section_targets),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tirLow,
                            onValueChange = { tirLow = it },
                            label = { Text(stringResource(R.string.target_tir_low)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tirHigh,
                            onValueChange = { tirHigh = it },
                            label = { Text(stringResource(R.string.target_tir_high)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tingHigh,
                            onValueChange = { tingHigh = it },
                            label = { Text(stringResource(R.string.target_ting_high)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tirGoal,
                            onValueChange = { tirGoal = it },
                            label = { Text(stringResource(R.string.target_tir_percent)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Night Profile Hours
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = nightStart,
                            onValueChange = { nightStart = it },
                            label = { Text("Начало ночи (сон)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = nightEnd,
                            onValueChange = { nightEnd = it },
                            label = { Text("Конец ночи") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            val low = tirLow.toDoubleOrNull() ?: 3.9
                            val high = tirHigh.toDoubleOrNull() ?: 10.0
                            val tHigh = tingHigh.toDoubleOrNull() ?: 7.8
                            val goal = tirGoal.toIntOrNull() ?: 70
                            val tGoal = tingGoal.toIntOrNull() ?: 50

                            val nStart = nightStart.replace(":00", "").trim().toIntOrNull() ?: 0
                            val nEnd = nightEnd.replace(":00", "").trim().toIntOrNull() ?: 6

                            viewModel.updateThresholds(low, high, tHigh, goal, tGoal, nStart.coerceIn(0, 23), nEnd.coerceIn(0, 23))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_confirm), fontWeight = FontWeight.Bold)
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
                        color = TextPrimaryDark
                    )

                    Text(
                        text = stringResource(R.string.clear_data_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMutedDark
                    )

                    OutlinedButton(
                        onClick = { viewModel.showClearConfirm(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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

        item { Spacer(modifier = Modifier.height(32.dp)) }
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
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) PrimaryEmerald else DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else DarkBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.Black else TextSecondaryDark,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
