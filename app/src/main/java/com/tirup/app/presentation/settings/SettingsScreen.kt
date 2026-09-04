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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.domain.model.BmiCategory
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.localizeDiabetesType
import com.tirup.app.domain.model.localizeTherapyType
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.HelpAndDisclaimerDialog
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showCriticalHypoSafetyDialog by rememberSaveable { mutableStateOf(false) }
    var showMainThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var masterOffHintVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(masterOffHintVisible) {
        if (masterOffHintVisible) {
            delay(3000L)
            masterOffHintVisible = false
        }
    }

    val isRu = settings.language.equals("RU", ignoreCase = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = if (isRu) "Настройки сохраняются автоматически" else "Settings are saved automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = "Help",
                        tint = ActionBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // Top Card: Patient Profile Summary (Opens Edit Dialog on Tap)
        item {
            PatientProfileSummaryCard(
                profile = profile,
                isRu = isRu,
                onClick = { showProfileDialog = true }
            )
        }

        // Section 1: Display Settings (Always Visible at the Top)
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
                            Text(text = if (isRu) "Тема" else "Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = if (isRu) "🌙 Тёмная" else "🌙 Dark",
                                isSelected = settings.themeMode == com.tirup.app.domain.model.ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.DARK) }
                            )
                            LanguageChip(
                                label = if (isRu) "☀️ Светлая" else "☀️ Light",
                                isSelected = settings.themeMode != com.tirup.app.domain.model.ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.LIGHT) }
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Smart Alerts (3 Tiers) - Master Card
        item {
            var isAlertsExpanded by rememberSaveable { mutableStateOf(false) }
            val alerts = settings.alertSettings

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAlertsExpanded = !isAlertsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (alerts.isAlertsMasterEnabled) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = if (isRu) "Тревоги (4 уровня)" else "Alarms (4 Tiers)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (!alerts.isAlertsMasterEnabled) {
                                        if (isRu) "Все тревоги выключены" else "All alarms disabled"
                                    } else {
                                        if (isRu) "Предиктивные, основные, критические, связь" else "Predictive, main, critical, signal loss"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!alerts.isAlertsMasterEnabled) ColorVeryLow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = alerts.isAlertsMasterEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (!isEnabled) {
                                        masterOffHintVisible = true
                                        viewModel.updateAlertSettings(
                                            alerts.copy(
                                                isAlertsMasterEnabled = false,
                                                criticalHypoPauseUntilTimestamp = System.currentTimeMillis() + 2 * 3600 * 1000L
                                            )
                                        )
                                    } else {
                                        masterOffHintVisible = false
                                        viewModel.updateAlertSettings(
                                            alerts.copy(
                                                isAlertsMasterEnabled = true,
                                                criticalHypoPauseUntilTimestamp = 0L
                                            )
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryEmerald
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isAlertsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isAlertsExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))

                        if (!alerts.isAlertsMasterEnabled) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ColorVeryLow.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ColorVeryLow.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isRu) "⚠️ Оповещения выключены. Вы можете настроить параметры или бессрочно отключить критическую тревогу гипо ниже."
                                           else "⚠️ Master alerts are disabled. You can configure parameters or permanently disable critical hypo below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorVeryLow,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        val isMaster = alerts.isAlertsMasterEnabled
                        val nowMs = System.currentTimeMillis()
                        val isCriticalPaused = alerts.criticalHypoPauseUntilTimestamp > nowMs
                        val remSec = if (isCriticalPaused) ((alerts.criticalHypoPauseUntilTimestamp - nowMs) / 1000L).coerceAtLeast(0) else 0L
                        val remHours = remSec / 3600
                        val remMin = ((remSec % 3600) / 60).coerceAtLeast(1)
                        val resumeTime = if (isCriticalPaused) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alerts.criticalHypoPauseUntilTimestamp)) else ""

                        val criticalBadge = if (isCriticalPaused) {
                            if (remHours > 0) "⏳ ${remHours}ч ${remMin}м" else "⏳ ${remMin}м"
                        } else null

                        // Tier 1: Predictive (Soft)
                        AlertTierConfigRow(
                            title = if (isRu) "1. Предиктивные (умные за 15 мин)" else "1. Predictive (Smart ~15 min)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Мягкий сигнал прогноза до выхода за диапазон" else "Soft early warning before crossing limits",
                            enabled = isMaster && alerts.isPredictiveEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isPredictiveEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isPredictiveEnabled = false))
                                }
                            },
                            vibrate = alerts.isPredictiveVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isPredictiveVibrate = it)) },
                            flash = alerts.isPredictiveFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isPredictiveFlash = it)) },
                            accentColor = ActionBlue,
                            onTestClick = { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.PREDICTIVE) },
                            isRu = isRu
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tier 2: Main (5 points confirmed)
                        AlertTierConfigRow(
                            title = if (isRu) "2. Основные (5 точек вне нормы)" else "2. Main (5 points confirmed)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Тройной сигнал при подтверждённом выходе" else "Triple beep on confirmed out-of-range",
                            enabled = isMaster && alerts.isMainEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isMainEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isMainEnabled = false))
                                }
                            },
                            vibrate = alerts.isMainVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isMainVibrate = it)) },
                            flash = alerts.isMainFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isMainFlash = it)) },
                            accentColor = ColorHigh,
                            onTestClick = { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.MAIN) },
                            isRu = isRu,
                            thresholdBadge = "< ${alerts.mainLowThresholdMmol}  |  > ${alerts.mainHighThresholdMmol}",
                            onThresholdClick = { showMainThresholdDialog = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val criticalSub = when {
                            isCriticalPaused -> {
                                if (remHours > 0) {
                                    if (isRu) "⏳ Пауза: ещё ${remHours} ч ${remMin} мин (авто-возобновление в $resumeTime)"
                                    else "⏳ Paused: ${remHours}h ${remMin}m left (auto-resumes at $resumeTime)"
                                } else {
                                    if (isRu) "⏳ Пауза: ещё ${remMin} мин (авто-возобновление в $resumeTime)"
                                    else "⏳ Paused: ${remMin}m left (auto-resumes at $resumeTime)"
                                }
                            }
                            alerts.isCriticalHypoPermanentDisabled -> {
                                if (isRu) "⚠️ Отключено осознанно под вашу ответственность" else "⚠️ Permanently disabled at own risk"
                            }
                            !isMaster -> {
                                if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)"
                            }
                            !alerts.isCriticalEnabled -> {
                                if (isRu) "Выключено пользователем" else "Disabled by user"
                            }
                            else -> if (isRu) "Сирена ~12 сек при гипо >20 мин, гипер >90 мин или <3.0 / >13.9" else "Siren ~12s on hypo >20m, hyper >90m or <3.0 / >13.9"
                        }

                        // Tier 3: Critical (Prolonged / Extreme)
                        val isCriticalInPauseState = isCriticalPaused && !alerts.isCriticalHypoPermanentDisabled
                        val isCriticalEffectiveEnabled = if (isCriticalInPauseState) true else (isMaster && alerts.isCriticalEnabled && !alerts.isCriticalHypoPermanentDisabled)

                        AlertTierConfigRow(
                            title = if (isRu) "3. Критические и затяжные («кричащие»)" else "3. Critical & Prolonged (Alarms)",
                            subtitle = criticalSub,
                            enabled = isCriticalEffectiveEnabled,
                            onEnabledChange = { isEnabled ->
                                if (!isEnabled) {
                                    showCriticalHypoSafetyDialog = true
                                } else {
                                    viewModel.updateAlertSettings(
                                        alerts.copy(
                                            isAlertsMasterEnabled = true,
                                            isCriticalEnabled = true,
                                            criticalHypoPauseUntilTimestamp = 0L,
                                            isCriticalHypoPermanentDisabled = false
                                        )
                                    )
                                }
                            },
                            vibrate = alerts.isCriticalVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isCriticalVibrate = it)) },
                            flash = alerts.isCriticalFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isCriticalFlash = it)) },
                            accentColor = if (isCriticalInPauseState) ColorHigh else ColorVeryLow,
                            onTestClick = { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.CRITICAL) },
                            isRu = isRu,
                            timerBadge = criticalBadge,
                            isPaused = isCriticalInPauseState
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        // Tier 4: Signal Loss (>20 min)
                        AlertTierConfigRow(
                            title = if (isRu) "4. Потеря сигнала сенсора (>20 мин)" else "4. Signal Loss (>20 min)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Нисходящий сигнал с нарастающим интервалом (20 ➔ 40 ➔ 80 мин)" else "Descending tone with geometric backoff (20 ➔ 40 ➔ 80 min)",
                            enabled = isMaster && alerts.isSignalLossEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isSignalLossEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isSignalLossEnabled = false))
                                }
                            },
                            vibrate = alerts.isSignalLossVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isSignalLossVibrate = it)) },
                            flash = alerts.isSignalLossFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isSignalLossFlash = it)) },
                            accentColor = Color(0xFF8B5CF6),
                            onTestClick = { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.SIGNAL_LOSS) },
                            isRu = isRu
                        )
                    }
                }
            }
        }

        // Section: Daily Compensator (Last Chance TIR) - Separate from Alerts
        item {
            val alerts = settings.alertSettings
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "⏳ Последний шанс для TIR" else "⏳ Last Chance for Daily TIR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Предупреждать вечером, если сахар вне нормы и запас времени до срыва цели на исходе (1 раз в сутки)"
                                else "Alert in evening when out of range and margin before target failure is running out (once a day)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = alerts.isLastChanceAlertEnabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updateAlertSettings(alerts.copy(isLastChanceAlertEnabled = isChecked))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }

                    // 3 segmented buttons: 1ч, 1.5ч, 2ч
                    if (alerts.isLastChanceAlertEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isRu) "Запас времени:" else "Time margin:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val options = listOf(
                                    Triple(60, if (isRu) "1 ч" else "1 h", "red"),
                                    Triple(90, if (isRu) "1.5 ч" else "1.5 h", "pale_green"),
                                    Triple(120, if (isRu) "2 ч" else "2 h", "green")
                                )
                                options.forEach { (mins, label, colorType) ->
                                    val isSelected = alerts.lastChanceBufferMinutes == mins
                                    val (bg, textColor, borderColor) = when (colorType) {
                                        "red" -> if (isSelected) {
                                            Triple(Color(0x33EF4444), Color(0xFFF87171), Color(0x80EF4444))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                        "pale_green" -> if (isSelected) {
                                            Triple(Color(0x2E10B981), Color(0xFF34D399), Color(0x6610B981))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                        else -> if (isSelected) {
                                            Triple(Color(0xFF059669), Color.White, Color(0xFF10B981))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.clickable {
                                            viewModel.updateAlertSettings(alerts.copy(lastChanceBufferMinutes = mins))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = bg,
                                        border = BorderStroke(1.dp, borderColor)
                                    ) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Expandable Additional Settings Header
        item {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAdvancedSettings = !showAdvancedSettings }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = ActionBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isRu) "Дополнительные настройки" else "Advanced Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRu) "Время сна, стандарты ATTD/ADA, очистка данных"
                                       else "Sleep window, ATTD/ADA standards, clear data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showAdvancedSettings) {
        // Section 3: Clinical Targets & Sleep Window
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isRu) "Клинические стандарты (ATTD / ADA)" else "Clinical Standards (ATTD / ADA)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Informational standard badge
                    val isMmol = settings.unit == GlucoseUnit.MMOL_L
                    val tirRangeStr = if (isMmol) "3.9 — 10.0 ммоль/л" else "70 — 180 mg/dL"
                    val tingRangeStr = if (isMmol) "3.9 — 7.8 ммоль/л" else "70 — 140 mg/dL"

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
                                    text = "TIR (цель ≥70%):",
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
                                    text = "TING (цель ≥50%):",
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

                    Text(
                        text = if (isRu) "Ночной профиль (окно сна)" else "Night Profile (Sleep Window)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Night Profile Hours (Sleep window)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownHourSelector(
                            label = if (isRu) "Начало сна" else "Sleep Start",
                            selectedHour = settings.nightStartHour,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newStart ->
                                viewModel.autoUpdateNightHours(
                                    nightStart = newStart,
                                    nightEnd = settings.nightEndHour
                                )
                            }
                        )

                        DropdownHourSelector(
                            label = if (isRu) "Конец сна" else "Sleep End",
                            selectedHour = settings.nightEndHour,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newEnd ->
                                viewModel.autoUpdateNightHours(
                                    nightStart = settings.nightStartHour,
                                    nightEnd = newEnd
                                )
                            }
                        )
                    }
                }
            }
        }

        // Section: Lockscreen Notification (Постоянное уведомление на экране блокировки)
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                                text = if (isRu) "Постоянный статус с сахаром, стрелкой тренда и TIR на экране блокировки и в панели уведомлений (как в GDH)"
                                else "Ongoing status with current glucose, trend arrow and TIR on lockscreen and notification shade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = settings.isLockscreenNotificationEnabled,
                            onCheckedChange = { viewModel.setLockscreenNotificationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }
            }
        }

        // Section: Floating Glucose Bubble (Плавающий пузырёк поверх всех окон)
        item {
            val context = LocalContext.current
            BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                                text = if (isRu) "Компактный кружок поверх всех приложений. Можно перетаскивать, тап открывает TIRUp, пульсирует при гипо"
                                else "Compact bubble over apps. Draggable, tap opens app, pulses on low glucose",
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
                                        viewModel.toggleFloatingBubble(true)
                                    }
                                } else {
                                    viewModel.toggleFloatingBubble(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }
            }
        }

        // Section: Widget Background Opacity with Live Interactive Preview
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                            color = PrimaryEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${settings.widgetBackgroundOpacity}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Live Interactive Preview Box on simulated wallpaper
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
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
                            .padding(10.dp),
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
                                .height(76.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "5.8",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "→",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryEmerald
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "+0.2",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isRu) "В норме ещё 2ч 15м" else "In range 2h 15m left",
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "TIR 84%",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald
                                    )
                                    Text(
                                        text = "IoB 1.2 U",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }

                    // Slider from 0 to 100%
                    Slider(
                        value = settings.widgetBackgroundOpacity.toFloat(),
                        onValueChange = { newVal ->
                            viewModel.updateWidgetBackgroundOpacity(newVal.toInt())
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryEmerald,
                            activeTrackColor = PrimaryEmerald,
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
                            color = PrimaryEmerald
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

        // Section 4: Auto-Backup
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Ежедневный автобэкап" else "Daily Auto-Backup",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Ежедневно в 00:00 в Android/data/.../Backups" else "Daily at 00:00 in Android/data/.../Backups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.isAutoBackupEnabled,
                            onCheckedChange = { viewModel.toggleAutoBackup(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }

                    if (settings.isAutoBackupEnabled) {
                        if (settings.lastBackupTimestamp > 0L) {
                            val fmt = SimpleDateFormat("dd.MM.yyyy 'в' HH:mm", Locale.getDefault())
                            val lastDateStr = fmt.format(Date(settings.lastBackupTimestamp))
                            Text(
                                text = if (isRu) "Последний бэкап: $lastDateStr" else "Last backup: $lastDateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryEmerald
                            )
                        } else {
                            Text(
                                text = if (isRu) "Запланирован на сегодня в 00:00" else "Scheduled for today at 00:00",
                                style = MaterialTheme.typography.bodySmall,
                                color = ActionBlue
                            )
                        }
                    }
                }
            }
        }

        // Section: Data Source Integration & App Info
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isRu) "ℹ️ Источник данных и синхронизация" else "ℹ️ Data Source & Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (isRu) "Для непрерывной передачи сахара, а также активного инсулина (IoB) и углеводов (CoB), в приложении xDrip+ необходимо активировать:\n• Broadcast Locally (Локальный броадкаст)\n• Broadcast Service API (в Inter-app settings)\n• Pebble Broadcast / веб-сервер (порт 17580)"
                        else "For uninterrupted streaming of glucose, active insulin (IoB) and carbs (CoB), enable in xDrip+:\n• Broadcast Locally\n• Broadcast Service API (in Inter-app settings)\n• Pebble Broadcast / web server (port 17580)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isRu) "Инструкция по настройке xDrip+ / GDH" else "xDrip+ / GDH Setup Instructions")
                    }
                }
            }
        }

        // Section 5: Data Management (Clear Data)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = if (isRu) "Telegram-канал проекта — @diakia" else "Project Telegram channel — @diakia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0284C7),
                    style = TextStyle(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/diakia"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
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
                    containerColor = ActionBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Назад" else "Back",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
    }

    if (showHelpDialog) {
        HelpAndDisclaimerDialog(
            isRu = isRu,
            onPrintManual = { viewModel.printOrShareUserManual() },
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showMainThresholdDialog) {
        var lowVal by remember { mutableStateOf(settings.alertSettings.mainLowThresholdMmol) }
        var highVal by remember { mutableStateOf(settings.alertSettings.mainHighThresholdMmol) }

        AlertDialog(
            onDismissRequest = { showMainThresholdDialog = false },
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
                        viewModel.updateAlertSettings(
                            settings.alertSettings.copy(
                                mainLowThresholdMmol = lowVal,
                                mainHighThresholdMmol = highVal
                            )
                        )
                        showMainThresholdDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Text(if (isRu) "Сохранить" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        lowVal = 3.9
                        highVal = 10.0
                    }
                ) {
                    Text(if (isRu) "Сброс к норме (3.9 - 10.0)" else "Default (3.9 - 10.0)")
                }
            }
        )
    }

    if (showCriticalHypoSafetyDialog) {
        var isAcknowledged by remember { mutableStateOf(false) }
        val alerts = settings.alertSettings
        AlertDialog(
            onDismissRequest = { showCriticalHypoSafetyDialog = false },
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
                    if (alerts.criticalHypoPauseUntilTimestamp > System.currentTimeMillis()) {
                        Button(
                            onClick = {
                                viewModel.updateAlertSettings(
                                    alerts.copy(
                                        isAlertsMasterEnabled = true,
                                        isCriticalEnabled = true,
                                        criticalHypoPauseUntilTimestamp = 0L,
                                        isCriticalHypoPermanentDisabled = false
                                    )
                                )
                                showCriticalHypoSafetyDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
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
                                viewModel.updateAlertSettings(
                                    alerts.copy(
                                        isCriticalEnabled = false,
                                        criticalHypoPauseUntilTimestamp = System.currentTimeMillis() + 2 * 3600 * 1000L,
                                        isCriticalHypoPermanentDisabled = false
                                    )
                                )
                                showCriticalHypoSafetyDialog = false
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
                                viewModel.updateAlertSettings(
                                    alerts.copy(
                                        isCriticalEnabled = false,
                                        isCriticalHypoPermanentDisabled = true,
                                        criticalHypoPauseUntilTimestamp = 0L
                                    )
                                )
                                showCriticalHypoSafetyDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ColorVeryLow),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorVeryLow)
                        ) {
                            Text(
                                text = if (isRu) "Отключить навсегда" else "Disable Permanently",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = { showCriticalHypoSafetyDialog = false },
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

    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearConfirm(false) },
            title = { Text(text = stringResource(R.string.clear_data), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(text = stringResource(R.string.clear_data_confirm), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData() }
                ) {
                    Text(text = stringResource(R.string.action_confirm), color = ColorVeryLow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showClearConfirm(false) }
                ) {
                    Text(text = stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showProfileDialog) {
        PatientProfileEditDialog(
            profile = profile,
            isRu = isRu,
            currentYear = currentYear,
            onProfileChange = { updated ->
                viewModel.autoUpdatePatientProfile(updated)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    // Centered 3-second floating HUD banner on turning Master alerts off
    AnimatedVisibility(
        visible = masterOffHintVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.88f),
        exit = fadeOut() + scaleOut(targetScale = 0.88f),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF00F172A),
            shadowElevation = 16.dp,
            border = BorderStroke(1.2.dp, ColorVeryLow.copy(alpha = 0.85f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ColorVeryLow,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isRu) "Оповещения отключены.\nКритическая сирена (<3.0) на паузе 2 часа для вашей безопасности."
                           else "Alerts turned off.\nCritical siren (<3.0) paused for 2h for your safety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
}

@Composable
private fun PatientProfileSummaryCard(
    profile: PatientProfile,
    isRu: Boolean,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val hasName = profile.fullName.isNotBlank()
    val displayName = if (hasName) profile.fullName else (if (isRu) "Профиль пациента" else "Patient Profile")

    val ageStr = if (profile.birthYear > 1900) "${profile.calculatedAge} ${if (isRu) "лет" else "y.o."}" else ""
    val diagStr = if (profile.diabetesType.isNotBlank()) localizeDiabetesType(profile.diabetesType, isRu) else ""
    val durStr = if (profile.calculatedDuration > 0) "${if (isRu) "стаж" else "duration"} ${profile.calculatedDuration} ${if (isRu) "л." else "y."}" else ""
    val bmi = profile.calculatedBmi
    val bmiStr = if (bmi != null) {
        val cat = BmiCategory.fromBmi(bmi, profile.calculatedAge, profile.gender)
        String.format(Locale.US, "ИМТ %.1f (%s)", bmi, if (isRu) cat.labelRu else cat.labelEn)
    } else ""

    val subtitleParts = listOf(ageStr, diagStr, durStr, bmiStr).filter { it.isNotBlank() }
    val subtitle = if (subtitleParts.isNotEmpty()) {
        subtitleParts.joinToString(" • ")
    } else {
        if (isRu) "Нажмите для заполнения мед. профиля" else "Tap to edit clinical report profile"
    }

    val isFemale = profile.gender.equals("F", ignoreCase = true)
    val avatarBg = if (isFemale) Color(0xFFC026D3) else PrimaryEmerald

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = avatarBg,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (profile.initials.isNotBlank()) {
                            Text(
                                text = profile.initials,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasName) onSurfaceVariant else ActionBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Edit Profile",
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientProfileEditDialog(
    profile: PatientProfile,
    isRu: Boolean,
    currentYear: Int,
    onProfileChange: (PatientProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val hM = profile.heightCm.toDoubleOrNull()?.let { it / 100.0 }
    val wKg = profile.weightKg.toDoubleOrNull()
    val bmi = if (hM != null && wKg != null && hM > 0.5) wKg / (hM * hM) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (profile.gender == "F") Color(0xFFC026D3) else PrimaryEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Профиль пациента" else "Patient Profile",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    // 1. Full Name (Text input)
                    OutlinedTextField(
                        value = profile.fullName,
                        onValueChange = { newName ->
                            onProfileChange(profile.copy(fullName = newName))
                        },
                        label = { Text(if (isRu) "ФИО пациента" else "Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    // Gender selector (M / F)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Пол:" else "Gender:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = if (isRu) "Мужской ♂" else "Male ♂",
                                isSelected = profile.gender == "M",
                                onClick = { onProfileChange(profile.copy(gender = "M")) }
                            )
                            LanguageChip(
                                label = if (isRu) "Женский ♀" else "Female ♀",
                                isSelected = profile.gender == "F",
                                onClick = { onProfileChange(profile.copy(gender = "F")) }
                            )
                        }
                    }
                }

                item {
                    // 2. Birth Year (with live calculated age)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownYearSelector(
                            label = if (isRu) "Год рождения" else "Birth Year",
                            selectedYear = profile.birthYear,
                            yearRange = (currentYear - 100)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newYear ->
                                onProfileChange(profile.copy(birthYear = newYear))
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
                                Text(if (isRu) "Возраст" else "Age", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${profile.calculatedAge} ${if (isRu) "лет" else "y.o."}", style = MaterialTheme.typography.bodyMedium, color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    // 3. Height & Weight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = profile.heightCm,
                            onValueChange = { newHeight ->
                                onProfileChange(profile.copy(heightCm = newHeight))
                            },
                            label = { Text(if (isRu) "Рост (см)" else "Height (cm)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = profile.weightKg,
                            onValueChange = { newWeight ->
                                onProfileChange(profile.copy(weightKg = newWeight))
                            },
                            label = { Text(if (isRu) "Вес (кг)" else "Weight (kg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                if (bmi != null) {
                    val age = profile.calculatedAge
                    val isChild = age in 2..17
                    val category = BmiCategory.fromBmi(bmi, age, profile.gender)
                    val catColor = when (category) {
                        BmiCategory.UNDERWEIGHT -> ActionBlue
                        BmiCategory.NORMAL -> PrimaryEmerald
                        BmiCategory.OVERWEIGHT -> ColorHigh
                        BmiCategory.OBESE_1, BmiCategory.OBESE_2_3, BmiCategory.PEDIATRIC_OBESE -> ColorVeryHigh
                    }
                    val scaleNote = if (isChild) {
                        val sexStr = if (profile.gender == "F") (if (isRu) "девочек" else "girls") else (if (isRu) "мальчиков" else "boys")
                        if (isRu) "Педиатрическая шкала ВОЗ: перцентили для $sexStr $age лет"
                        else "WHO Pediatric scale: percentiles for $sexStr age $age"
                    } else {
                        if (isRu) "Шкала ВОЗ для взрослых (норма 18.5–24.9 кг/м²)"
                        else "WHO Adult scale (normal 18.5–24.9 kg/m²)"
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isRu) "ИМТ (индекс массы тела):" else "BMI:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = String.format(Locale.US, "%.1f кг/м²", bmi),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isRu) "Оценка ВОЗ:" else "WHO Assessment:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (isRu) category.labelRu else category.labelEn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = catColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = scaleNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    // 4. Diabetes Type & Diagnosis Year
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownChoiceSelector(
                            label = if (isRu) "Тип диабета" else "Diabetes Type",
                            selectedOption = localizeDiabetesType(profile.diabetesType, isRu),
                            options = if (isRu) listOf("СД1", "СД2", "LADA", "MODY", "ГСД") else listOf("T1D", "T2D", "LADA", "MODY", "GDM"),
                            modifier = Modifier.weight(1f),
                            onOptionSelected = { newType ->
                                onProfileChange(profile.copy(diabetesType = newType))
                            }
                        )

                        DropdownYearSelector(
                            label = if (isRu) "Диагноз с года" else "Diagnosed Year",
                            selectedYear = profile.diagnosisYear,
                            yearRange = (currentYear - 60)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newDiagYear ->
                                onProfileChange(profile.copy(diagnosisYear = newDiagYear))
                            }
                        )
                    }
                }

                item {
                    // 5. Therapy Type Dropdown
                    DropdownChoiceSelector(
                        label = if (isRu) "Вид терапии" else "Therapy Type",
                        selectedOption = localizeTherapyType(profile.therapyType, isRu),
                        options = if (isRu) listOf(
                            "Инсулиновая помпа",
                            "Шприц-ручки (МДИ)",
                            "Пероральные препараты (Таблетки)",
                            "Диетотерапия"
                        ) else listOf(
                            "Insulin Pump",
                            "Multiple Daily Injections (MDI)",
                            "Oral Medication (Pills)",
                            "Diet Therapy"
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onOptionSelected = { newTherapy ->
                            onProfileChange(profile.copy(therapyType = newTherapy))
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isRu) "Готово" else "Done",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                )
            }
        }
    )
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
        color = if (isSelected) ActionBlue else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) ActionBlue else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onClick() }
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
private fun AlertTierConfigRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    vibrate: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    flash: Boolean,
    onFlashChange: (Boolean) -> Unit,
    accentColor: Color,
    onTestClick: () -> Unit,
    isRu: Boolean,
    timerBadge: String? = null,
    isPaused: Boolean = false,
    thresholdBadge: String? = null,
    onThresholdClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) ColorHigh else accentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thresholdBadge != null && onThresholdClick != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { onThresholdClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = thresholdBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor
                                )
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (timerBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = timerBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) ColorHigh else accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        thumbContent = if (isPaused) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Paused",
                                    tint = ColorHigh,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (isPaused) ColorHigh else accentColor
                        )
                    )
                }
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onVibrateChange(!vibrate) }
                        ) {
                            Checkbox(
                                checked = vibrate,
                                onCheckedChange = onVibrateChange,
                                colors = CheckboxDefaults.colors(checkedColor = accentColor)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вибро" else "Vibrate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onFlashChange(!flash) }
                        ) {
                            Checkbox(
                                checked = flash,
                                onCheckedChange = onFlashChange,
                                colors = CheckboxDefaults.colors(checkedColor = accentColor)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вспышка" else "Flash",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onTestClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isRu) "Тест" else "Test",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

