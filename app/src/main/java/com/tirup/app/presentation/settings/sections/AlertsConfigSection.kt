package com.tirup.app.presentation.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.data.alert.AlertTier
import com.tirup.app.data.alert.MedicalSoundPlayer
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsConfigSection(
    settings: UserSettings,
    isRu: Boolean,
    currentlyPlayingTag: String?,
    onUpdateAlertSettings: (AlertSettings) -> Unit,
    onTestAlert: (AlertTier) -> Unit,
    onTestCaregiverSosScreen: () -> Unit,
    onPlayTestSound: (Int) -> Unit,
    onSoundClick: (String, () -> Unit) -> Unit,
    onShowCriticalHypoSafetyDialog: () -> Unit,
    onShowCriticalThresholdDialog: () -> Unit,
    onShowMainThresholdDialog: () -> Unit,
    onShowPredictiveHorizonDialog: () -> Unit,
    onMasterOffHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAlertsExpanded by rememberSaveable { mutableStateOf(false) }
    val alerts = settings.alertSettings

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main 4-Tier Alerts BentoCard
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (alerts.isAlertsMasterEnabled) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    onMasterOffHint()
                                    onUpdateAlertSettings(
                                        alerts.copy(
                                            isAlertsMasterEnabled = false,
                                            criticalHypoPauseUntilTimestamp = System.currentTimeMillis() + 2 * 3600 * 1000L
                                        )
                                    )
                                } else {
                                    onUpdateAlertSettings(
                                        alerts.copy(
                                            isAlertsMasterEnabled = true,
                                            criticalHypoPauseUntilTimestamp = 0L
                                        )
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
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
                    val isAlertsPaused = alerts.alertsMuteUntilTimestamp > nowMs
                    val isCriticalPaused = alerts.criticalHypoPauseUntilTimestamp > nowMs || isAlertsPaused
                    val pauseTargetMs = maxOf(alerts.criticalHypoPauseUntilTimestamp, alerts.alertsMuteUntilTimestamp)
                    val remSec = if (isCriticalPaused) {
                        ((pauseTargetMs - nowMs) / 1000L).coerceAtLeast(0)
                    } else 0L
                    val remHours = remSec / 3600
                    val remMin = ((remSec % 3600) / 60).coerceAtLeast(1)
                    val resumeTime = if (isCriticalPaused) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(pauseTargetMs))
                    } else ""

                    val criticalBadge = if (isCriticalPaused) {
                        if (remHours > 0) "⏳ ${remHours}ч ${remMin}м" else "⏳ ${remMin}м"
                    } else null

                    if (isAlertsPaused) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ActionBlue.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "⏸️ Все тревоги на паузе" else "⏸️ All alarms paused",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue
                                    )
                                    Text(
                                        text = if (remHours > 0) {
                                            if (isRu) "Осталось: ${remHours}ч ${remMin}м (до $resumeTime)" else "Remaining: ${remHours}h ${remMin}m (until $resumeTime)"
                                        } else {
                                            if (isRu) "Осталось: ${remMin}м (до $resumeTime)" else "Remaining: ${remMin}m (until $resumeTime)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onUpdateAlertSettings(
                                            alerts.copy(
                                                alertsMuteUntilTimestamp = 0L,
                                                criticalHypoPauseUntilTimestamp = 0L
                                            )
                                        )
                                    }
                                ) {
                                    Text(
                                        text = if (isRu) "Возобновить" else "Resume",
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Independent Volume Control for Tiers 1-2
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Громкость упреждающих тревог" else "Alert Volume (Tiers 1–2)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRu) "Независима от звука уведомлений телефона" else "Independent of phone ringtone volume",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        onPlayTestSound(alerts.alertVolumePercent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isRu) "Тест 🔔" else "Test 🔔",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Slider(
                                    value = alerts.alertVolumePercent.toFloat(),
                                    onValueChange = { newVal ->
                                        val stepped = (kotlin.math.round(newVal / 5f) * 5f).toInt().coerceIn(20, 100)
                                        if (stepped != alerts.alertVolumePercent) {
                                            onUpdateAlertSettings(alerts.copy(alertVolumePercent = stepped))
                                        }
                                    },
                                    valueRange = 20f..100f,
                                    steps = 15,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ActionBlue,
                                        activeTrackColor = ActionBlue
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${alerts.alertVolumePercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionBlue,
                                    modifier = Modifier.width(44.dp)
                                )
                            }

                            Text(
                                text = if (isRu) "ℹ️ Критические тревоги (затяжная гипогликемия, потеря связи) всегда звучат на максимальной громкости (100%)."
                                else "ℹ️ Critical alarms (prolonged hypo, signal loss) always sound at maximum volume (100%).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tier 1: Predictive (Soft)
                    AlertTierConfigRow(
                        title = if (isRu) "1. Предиктивные (умные за ${alerts.predictiveMinutesAhead} мин)" else "1. Predictive (Smart ~${alerts.predictiveMinutesAhead} min)",
                        subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                        else if (isRu) "Мягкий сигнал прогноза до выхода за диапазон" else "Soft early warning before crossing limits",
                        enabled = isMaster && alerts.isPredictiveEnabled,
                        onEnabledChange = { isChecked ->
                            if (isChecked) {
                                onUpdateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isPredictiveEnabled = true))
                            } else {
                                onUpdateAlertSettings(alerts.copy(isPredictiveEnabled = false))
                            }
                        },
                        vibrate = alerts.isPredictiveVibrate,
                        onVibrateChange = { onUpdateAlertSettings(alerts.copy(isPredictiveVibrate = it)) },
                        flash = alerts.isPredictiveFlash,
                        onFlashChange = { onUpdateAlertSettings(alerts.copy(isPredictiveFlash = it)) },
                        accentColor = ActionBlue,
                        onTestClick = { onSoundClick(AlertTier.PREDICTIVE.name) { onTestAlert(AlertTier.PREDICTIVE) } },
                        isTesting = (currentlyPlayingTag == AlertTier.PREDICTIVE.name),
                        isRu = isRu,
                        thresholdBadge = if (isRu) "⏱️ Горизонт: ${alerts.predictiveMinutesAhead} мин" else "⏱️ Horizon: ${alerts.predictiveMinutesAhead} min",
                        onThresholdClick = onShowPredictiveHorizonDialog
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tier 2: Main (3-5 points confirmed)
                    AlertTierConfigRow(
                        title = if (isRu) "2. Основные (3–5 точек вне нормы)" else "2. Main (3–5 points confirmed)",
                        subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                        else if (isRu) "Тройной сигнал (3 точки для 5-мин / 5 точек для 1-мин). Глушится при падении с IoB" else "Triple beep (3 pts for 5-min / 5 pts for 1-min). Muted on drop with IoB",
                        enabled = isMaster && alerts.isMainEnabled,
                        onEnabledChange = { isChecked ->
                            if (isChecked) {
                                onUpdateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isMainEnabled = true))
                            } else {
                                onUpdateAlertSettings(alerts.copy(isMainEnabled = false))
                            }
                        },
                        vibrate = alerts.isMainVibrate,
                        onVibrateChange = { onUpdateAlertSettings(alerts.copy(isMainVibrate = it)) },
                        flash = alerts.isMainFlash,
                        onFlashChange = { onUpdateAlertSettings(alerts.copy(isMainFlash = it)) },
                        accentColor = ColorHigh,
                        onTestClick = { onSoundClick(AlertTier.MAIN.name) { onTestAlert(AlertTier.MAIN) } },
                        isTesting = (currentlyPlayingTag == AlertTier.MAIN.name),
                        isRu = isRu,
                        thresholdBadge = "< ${String.format(Locale.US, "%.1f", alerts.mainLowThresholdMmol)}  |  > ${String.format(Locale.US, "%.1f", alerts.mainHighThresholdMmol)}",
                        onThresholdClick = onShowMainThresholdDialog
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
                        else -> if (isRu) "Опасные (12с): <3.9 (>20 мин) или >10.0 (>90 мин). Критические: экстренные сирены и экран спасения"
                        else "Dangerous (12s): <3.9 (>20 min) or >10.0 (>90 min). Critical: emergency alarms & rescue screen"
                    }

                    // Tier 3: Critical (Prolonged / Extreme)
                    val isCriticalInPauseState = isCriticalPaused && !alerts.isCriticalHypoPermanentDisabled
                    val isCriticalEffectiveEnabled = if (isCriticalInPauseState) true else (isMaster && alerts.isCriticalEnabled && !alerts.isCriticalHypoPermanentDisabled)
                    val tier3Tag = if (alerts.isCaregiverRole) "CAREGIVER_SOS" else AlertTier.CRITICAL.name

                    AlertTierConfigRow(
                        title = if (isRu) "3. Опасные и критические" else "3. Dangerous & Critical",
                        subtitle = criticalSub,
                        enabled = isCriticalEffectiveEnabled,
                        onEnabledChange = { isEnabled ->
                            if (!isEnabled) {
                                onShowCriticalHypoSafetyDialog()
                            } else {
                                onUpdateAlertSettings(
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
                        onVibrateChange = { onUpdateAlertSettings(alerts.copy(isCriticalVibrate = it)) },
                        flash = alerts.isCriticalFlash,
                        onFlashChange = { onUpdateAlertSettings(alerts.copy(isCriticalFlash = it)) },
                        accentColor = ColorVeryLow,
                        onTestClick = {
                            onSoundClick(tier3Tag) {
                                if (alerts.isCaregiverRole) {
                                    onTestCaregiverSosScreen()
                                } else {
                                    MedicalSoundPlayer.playSound(
                                        AlertTier.CRITICAL,
                                        alerts.alertVolumePercent
                                    )
                                }
                            }
                        },
                        isTesting = (currentlyPlayingTag == tier3Tag),
                        isRu = isRu,
                        timerBadge = criticalBadge,
                        isPaused = isCriticalInPauseState,
                        thresholdBadge = if (isRu) "🚨 Критические тревоги (< ${String.format(Locale.US, "%.1f", alerts.criticalLowThresholdMmol)}  или  > ${String.format(Locale.US, "%.1f", alerts.criticalHighThresholdMmol)})"
                        else "🚨 Critical Alerts (< ${String.format(Locale.US, "%.1f", alerts.criticalLowThresholdMmol)}  or  > ${String.format(Locale.US, "%.1f", alerts.criticalHighThresholdMmol)})",
                        onThresholdClick = onShowCriticalThresholdDialog
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tier 4: Signal Loss (20-25 min)
                    AlertTierConfigRow(
                        title = if (isRu) "4. Потеря сигнала сенсора (20–25 мин)" else "4. Signal Loss (20–25 min)",
                        subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                        else if (isRu) "Нисходящий сигнал через 20–25 мин с нарастающим интервалом (➔ 40 ➔ 80 мин)" else "Descending tone after 20–25 min with increasing interval (➔ 40 ➔ 80 min)",
                        enabled = isMaster && alerts.isSignalLossEnabled,
                        onEnabledChange = { isChecked ->
                            if (isChecked) {
                                onUpdateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isSignalLossEnabled = true))
                            } else {
                                onUpdateAlertSettings(alerts.copy(isSignalLossEnabled = false))
                            }
                        },
                        vibrate = alerts.isSignalLossVibrate,
                        onVibrateChange = { onUpdateAlertSettings(alerts.copy(isSignalLossVibrate = it)) },
                        flash = alerts.isSignalLossFlash,
                        onFlashChange = { onUpdateAlertSettings(alerts.copy(isSignalLossFlash = it)) },
                        accentColor = Color(0xFF8B5CF6),
                        onTestClick = { onSoundClick(AlertTier.SIGNAL_LOSS.name) { onTestAlert(AlertTier.SIGNAL_LOSS) } },
                        isTesting = (currentlyPlayingTag == AlertTier.SIGNAL_LOSS.name),
                        isRu = isRu
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Low Battery Alert Row (<15%, <10%, <5%)
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
                                text = if (isRu) "🔋 Критический разряд телефона" else "🔋 Low Phone Battery Alert",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Предупреждать при разряде ниже 15%, 10% и 5%, чтобы не прерывать мониторинг глюкозы"
                                else "Alert when phone battery drops below 15%, 10%, and 5% to prevent monitoring cutoff",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = alerts.isLowBatteryAlertEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateAlertSettings(alerts.copy(isLowBatteryAlertEnabled = isChecked))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFEF4444)
                            )
                        )
                    }

                    if (alerts.isLowBatteryAlertEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRu) "Тест звуковых сигналов по порогам:" else "Test alert tones by threshold:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BatterySoundTestChip(
                                modifier = Modifier.weight(1f),
                                thresholdText = "<15%",
                                tierLabel = if (isRu) "Мягкий" else "Soft",
                                accentColor = Color(0xFFF59E0B),
                                isPlaying = (currentlyPlayingTag == "BATTERY_15"),
                                isRu = isRu,
                                onClick = {
                                    onSoundClick("BATTERY_15") {
                                        MedicalSoundPlayer.playSound(
                                            AlertTier.PREDICTIVE,
                                            alerts.alertVolumePercent,
                                            customTag = "BATTERY_15"
                                        )
                                    }
                                }
                            )
                            BatterySoundTestChip(
                                modifier = Modifier.weight(1f),
                                thresholdText = "<10%",
                                tierLabel = if (isRu) "Тройной" else "Main",
                                accentColor = Color(0xFFEA580C),
                                isPlaying = (currentlyPlayingTag == "BATTERY_10"),
                                isRu = isRu,
                                onClick = {
                                    onSoundClick("BATTERY_10") {
                                        MedicalSoundPlayer.playSound(
                                            AlertTier.MAIN,
                                            alerts.alertVolumePercent,
                                            customTag = "BATTERY_10"
                                        )
                                    }
                                }
                            )
                            BatterySoundTestChip(
                                modifier = Modifier.weight(1f),
                                thresholdText = "<5%",
                                tierLabel = if (isRu) "Тревога" else "Alarm",
                                accentColor = Color(0xFFEF4444),
                                isPlaying = (currentlyPlayingTag == "BATTERY_5"),
                                isRu = isRu,
                                onClick = {
                                    onSoundClick("BATTERY_5") {
                                        MedicalSoundPlayer.playSound(
                                            AlertTier.CRITICAL,
                                            alerts.alertVolumePercent,
                                            customTag = "BATTERY_5"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Daily Compensator (Last Chance TIR) BentoCard
        BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                            onUpdateAlertSettings(alerts.copy(isLastChanceAlertEnabled = isChecked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

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
                                        onUpdateAlertSettings(alerts.copy(lastChanceBufferMinutes = mins))
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
    onThresholdClick: (() -> Unit)? = null,
    isTesting: Boolean = false
) {
    val effectiveAccent = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else accentColor

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = effectiveAccent.copy(alpha = if (isPaused) 0.05f else 0.08f),
        border = BorderStroke(1.dp, effectiveAccent.copy(alpha = if (isPaused) 0.20f else 0.35f))
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
                        color = effectiveAccent
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
                            color = effectiveAccent.copy(alpha = if (isPaused) 0.08f else 0.14f),
                            border = BorderStroke(1.dp, effectiveAccent.copy(alpha = if (isPaused) 0.25f else 0.4f)),
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
                                    color = effectiveAccent
                                )
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = effectiveAccent,
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
                            color = effectiveAccent.copy(alpha = if (isPaused) 0.10f else 0.16f),
                            border = BorderStroke(1.dp, effectiveAccent.copy(alpha = if (isPaused) 0.25f else 0.45f))
                        ) {
                            Text(
                                text = timerBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = effectiveAccent,
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
                                    tint = effectiveAccent,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (isPaused) effectiveAccent.copy(alpha = 0.40f) else effectiveAccent
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
                                colors = CheckboxDefaults.colors(checkedColor = effectiveAccent)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вибро" else "Vibrate",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPaused) effectiveAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onFlashChange(!flash) }
                        ) {
                            Checkbox(
                                checked = flash,
                                onCheckedChange = onFlashChange,
                                colors = CheckboxDefaults.colors(checkedColor = effectiveAccent)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вспышка" else "Flash",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPaused) effectiveAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val btnColor = if (isTesting) MaterialTheme.colorScheme.error else effectiveAccent
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = btnColor.copy(alpha = if (isPaused) 0.10f else 0.16f),
                        border = BorderStroke(1.dp, btnColor.copy(alpha = if (isPaused) 0.25f else 0.5f)),
                        modifier = Modifier.clickable { onTestClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isTesting) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = btnColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isTesting) (if (isRu) "Стоп" else "Stop")
                                else (if (isRu) "Тест" else "Test"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = btnColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySoundTestChip(
    modifier: Modifier = Modifier,
    thresholdText: String,
    tierLabel: String,
    accentColor: Color,
    isPlaying: Boolean,
    isRu: Boolean,
    onClick: () -> Unit
) {
    val btnColor = if (isPlaying) MaterialTheme.colorScheme.error else accentColor
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = btnColor.copy(alpha = if (isPlaying) 0.16f else 0.08f),
        border = BorderStroke(1.dp, btnColor.copy(alpha = if (isPlaying) 0.55f else 0.28f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = btnColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = thresholdText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = btnColor
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isPlaying) (if (isRu) "Стоп" else "Stop") else tierLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlaying) btnColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
