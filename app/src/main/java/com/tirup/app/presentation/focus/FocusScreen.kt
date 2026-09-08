package com.tirup.app.presentation.focus

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import com.tirup.app.presentation.components.BentoCard
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.data.alert.ActiveAlertBanner
import com.tirup.app.data.alert.AlertTier
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.isExpired
import com.tirup.app.domain.model.daysRemaining
import com.tirup.app.presentation.components.DeviceStatusChips
import com.tirup.app.presentation.components.DeviceStatusModal
import com.tirup.app.presentation.components.DeviceExpiredAlertDialog
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.presentation.components.BentoMetricCompact
import com.tirup.app.presentation.components.MetricsOrderDialog
import com.tirup.app.presentation.components.StreakBadge
import com.tirup.app.presentation.components.StreakMotivatorDialog
import com.tirup.app.data.alert.AlertLogEntry
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.ble.BleBroadcaster
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTarget
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val dailyAlertLogs by GlucoseAlertManager.dailyAlertLogs.collectAsState()
    val isBleBroadcasting by BleBroadcaster.isBroadcasting.collectAsState()
    val broadcastRemainingSec by BleBroadcaster.broadcastRemainingSec.collectAsState()
    val nextHeartbeatRemainingSec by BleBroadcaster.nextHeartbeatRemainingSec.collectAsState()

    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMetricsOrderDialog by remember { mutableStateOf(false) }
    var showDailyAlertLogsDialog by rememberSaveable { mutableStateOf(false) }
    var focusCardMode by rememberSaveable { mutableStateOf(0) }


    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val userSettings = state.userSettings
    val isRu = userSettings.language.equals("RU", ignoreCase = true)
    val targetMode = userSettings.targetMode
    val unit = userSettings.unit
    val goal = state.compensatorGoal

    var showDeviceModal by remember { mutableStateOf(false) }
    val sensorStatus = state.sensorStatus
    val pumpSetStatus = state.pumpSetStatus
    val isPumpUser = userSettings.patientProfile.therapyType in listOf("Инсулиновая помпа", "Insulin Pump")
    val showExpiredSensorDialog = remember(sensorStatus.installedAt) { sensorStatus.isExpired }
    val showExpiredPumpDialog = remember(pumpSetStatus.installedAt) { isPumpUser && pumpSetStatus.isExpired }
    var sensorExpiredDismissed by rememberSaveable { mutableStateOf(false) }
    var pumpExpiredDismissed by rememberSaveable { mutableStateOf(false) }

    val shouldCelebrateStreak = state.streakDays >= 2 && state.streakDays > userSettings.lastStreakCelebratedDays
    var showStreakDialog by remember(shouldCelebrateStreak) { mutableStateOf(shouldCelebrateStreak) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header with Menu Button, Title & Streak
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Settings Menu",
                            tint = onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.focus_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (userSettings.isDeviceRemindersEnabled) {
                        DeviceStatusChips(
                            sensorStatus = sensorStatus,
                            pumpSetStatus = pumpSetStatus,
                            showPump = isPumpUser,
                            isRu = isRu,
                            onClick = { showDeviceModal = true }
                        )
                    }
                    StreakBadge(
                        streakDays = state.streakDays,
                        onClick = {
                            showStreakDialog = true
                        }
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

            if (userSettings.bleBridgeSettings.role != BleBridgeRole.DISABLED) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ActionBlue.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = ActionBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isRu) {
                                    "BLE-мост включён: ${if (userSettings.bleBridgeSettings.role == BleBridgeRole.BROADCASTER) "вещатель" else "приёмник"}. Для отключения откройте Дополнительные настройки."
                                } else {
                                    "BLE Bridge enabled: ${if (userSettings.bleBridgeSettings.role == BleBridgeRole.BROADCASTER) "broadcaster" else "observer"}. Disable it in Advanced Settings."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

        // 1. Hero Card: Current Glucose
        item {
            val bleSettings = userSettings.bleBridgeSettings
            val isObserver = bleSettings.role == com.tirup.app.domain.model.BleBridgeRole.OBSERVER
            val packetAgeMinutes = if (bleSettings.lastPacketTimestamp > 0L) {
                (System.currentTimeMillis() - bleSettings.lastPacketTimestamp) / 60_000L
            } else 999L
            val isMasterBatteryStale = packetAgeMinutes > 7
            val masterBattery = if (isObserver && (bleSettings.lastMasterBattery in 0..100 || bleSettings.lastPacketTimestamp > 0L)) {
                bleSettings.lastMasterBattery
            } else null

            HeroGlucoseCard(
                latestReading = state.latestReading,
                recentReadings = state.recentReadings,
                unit = unit,
                isRu = isRu,
                activeAlertBanner = state.activeAlertBanner,
                masterBatteryPct = masterBattery,
                isMasterBatteryStale = isMasterBatteryStale,
                isBroadcaster = bleSettings.role == BleBridgeRole.BROADCASTER,
                isBleBroadcasting = isBleBroadcasting,
                broadcastRemainingSec = broadcastRemainingSec,
                nextHeartbeatRemainingSec = nextHeartbeatRemainingSec,
                dailyAlertsCount = dailyAlertLogs.size,
                onAlertHistoryClick = { showDailyAlertLogsDialog = true },
                onBatteryClick = {
                    val title = if (isRu) "Заряд батареи вещателя" else "Broadcaster Battery"
                    val ageMins = if (bleSettings.lastPacketTimestamp > 0L) {
                        (System.currentTimeMillis() - bleSettings.lastPacketTimestamp) / 60000L
                    } else null
                    val ageStr = if (ageMins != null) {
                        val value = when {
                            ageMins < 60L -> ageMins to if (isRu) "мин." else "min"
                            ageMins < 1440L -> (ageMins / 60L) to if (isRu) "ч" else "h"
                            ageMins < 365L * 1440L -> (ageMins / 1440L) to if (isRu) "дн." else "d"
                            else -> (ageMins / (365L * 1440L)) to if (isRu) "лет" else "y"
                        }
                        if (isRu) " (обновлено ${value.first} ${value.second} назад)" else " (updated ${value.first}${value.second} ago)"
                    } else ""
                    val desc = if (isRu) {
                        if (isMasterBatteryStale) {
                            "Данные о заряде телефона-вещателя устарели (сигнал не обновлялся более 15 минут). Проверьте Bluetooth-соединение."
                        } else {
                            "Текущий уровень заряда батареи на смартфоне-вещателе: ${masterBattery ?: 0}%$ageStr.\n\nДанные передаются автоматически с каждым сигналом Bluetooth."
                                "Текущий уровень заряда батареи на смартфоне-вещателе: ${masterBattery ?: 0}%$ageStr.\n\nДанные передаются автоматически с каждым сигналом Bluetooth. Отключить BLE-мост можно в дополнительных настройках."
                        }
                    } else {
                        "Battery level on Broadcaster device: ${masterBattery ?: 0}%$ageStr.\n\nData is sent automatically with each Bluetooth signal. Disable BLE Bridge in Advanced Settings."
                    }
                    detailDialogInfo = Pair(title, desc)
                },
                onIobClick = {
                    val r = state.latestReading
                    val iobVal = r?.iob ?: 0.0
                    val title = if (isRu) "Активный инсулин (IoB)" else "Insulin on Board (IoB)"
                    val desc = if (isRu) {
                        "Расчетное количество активного короткого/ультракороткого инсулина в организме: ${String.format(Locale.US, "%.2f", iobVal)} Ед.\n\nУчитывает время действия предыдущих болюсов и помогает предотвратить опасное наслоение доз (инсулиновый стек)."
                    } else {
                        "Estimated remaining active insulin: ${String.format(Locale.US, "%.2f", iobVal)} U.\nHelps prevent dangerous insulin stacking."
                    }
                    detailDialogInfo = Pair(title, desc)
                },
                onCobClick = {
                    val r = state.latestReading
                    val cobVal = r?.cob ?: 0.0
                    val title = if (isRu) "Активные углеводы (CoB)" else "Carbs on Board (CoB)"
                    val desc = if (isRu) {
                        "Расчетное количество еще не усвоенных углеводов: ${String.format(Locale.US, "%.0f", cobVal)} г.\n\nПоказывает объем углеводов от недавних приемов пищи, который еще поступит в кровоток и может вызвать рост гликемии."
                    } else {
                        "Estimated unabsorbed carbs: ${String.format(Locale.US, "%.0f", cobVal)} g.\nShows pending carbohydrates from recent meals."
                    }
                    detailDialogInfo = Pair(title, desc)
                },
                onBleClick = {
                    if (isBleBroadcasting) {
                        val title = if (isRu) "BLE-мост: Передача" else "BLE Bridge: Broadcasting"
                        val desc = if (isRu) {
                            "Прямо сейчас вещатель передает сигнал Bluetooth в эфир (осталось $broadcastRemainingSec сек).\n\nТелефоны-приемники в радиусе 10–15 м с вашим семейным PIN получают свежий замер сахара, тренд и заряд батареи."
                        } else {
                            "Active BLE broadcast pulse in progress ($broadcastRemainingSec s remaining)."
                        }
                        detailDialogInfo = Pair(title, desc)
                    } else {
                        val min = nextHeartbeatRemainingSec / 60
                        val sec = nextHeartbeatRemainingSec % 60
                        val timeStr = String.format(Locale.US, "%d:%02d", min, sec)
                        val title = if (isRu) "BLE-мост: Режим ожидания" else "BLE Bridge: Idle"
                        val desc = if (isRu) {
                            "Вещатель находится в режиме ожидания. До контрольного сигнала (heartbeat): $timeStr.\n\nКак только от сенсора поступит свежий замер, вещатель немедленно передаст его в эфир и таймер сбросится обратно на 5:00."
                        } else {
                            "Broadcaster is idle. Heartbeat pulse in: $timeStr.\nArriving sensor readings are transmitted immediately, resetting the timer to 5:00."
                        }
                        detailDialogInfo = Pair(title, desc)
                    }
                },
                onClick = {
                    val r = state.latestReading
                    if (r != null) {
                        val rVal = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", r.valueMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                   else "${(r.valueMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        val iobInfo = if (r.iob != null && r.iob > 0.0) {
                            if (isRu) "\n\n💉 IoB (активный инсулин): ${String.format(Locale.US, "%.2f Ед", r.iob)}\nКоличество болюсного инсулина, которое продолжает активно действовать и снижать глюкозу (остаточное действие)."
                            else "\n\n💉 IoB (Insulin on Board): ${String.format(Locale.US, "%.2f U", r.iob)}\nActive bolus insulin remaining in the body that continues lowering glucose."
                        } else ""
                        val cobInfo = if (r.cob != null && r.cob > 0.0) {
                            if (isRu) "\n\n🍞 CoB (углеводы на борту): ${String.format(Locale.US, "%.0f г", r.cob)}\nКоличество активных углеводов в процессе усвоения в ЖКТ или требуемое для купирования потенциальной гипогликемии."
                            else "\n\n🍞 CoB (Carbs on Board): ${String.format(Locale.US, "%.0f g", r.cob)}\nActive carbohydrates currently digesting in the GI tract or required to treat potential low."
                        } else ""
                        detailDialogInfo = Pair(
                            if (isRu) "Текущий уровень сахара" else "Current Glucose Level",
                            if (isRu) "Значение: $rVal\nНаправление тренда: ${r.trendArrow}\nВремя измерения: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}$iobInfo$cobInfo"
                            else "Value: $rVal\nTrend direction: ${r.trendArrow}\nTime: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}$iobInfo$cobInfo"
                        )
                    }
                }
            )
        }

        // 2. Interactive 24-Hour Daily Glucose Chart (Pinch-to-zoom & Pan) with Metrics toggle
        item {
            val cal = remember(state.recentReadings) {
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
            }
            val startOfDay = cal.timeInMillis
            val todayOnlyReadings = remember(state.recentReadings, startOfDay) {
                val filtered = state.recentReadings.filter { it.timestamp >= startOfDay }
                if (filtered.isNotEmpty()) filtered else state.recentReadings
            }
            val minVal = if (todayOnlyReadings.isNotEmpty()) todayOnlyReadings.minOf { it.valueMmol } else 0.0
            val maxVal = if (todayOnlyReadings.isNotEmpty()) todayOnlyReadings.maxOf { it.valueMmol } else 0.0

            val meanValStr = if (state.statistics.meanMmol > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", state.statistics.meanMmol)
                else String.format(Locale.US, "%d", (state.statistics.meanMmol * 18.0182).toInt())
            } else "--"

            val meanColor = when {
                state.statistics.meanMmol <= 0.0 -> onSurfaceVariant
                state.statistics.meanMmol <= 7.0 -> PrimaryEmerald
                state.statistics.meanMmol <= 7.8 -> ColorTargetSoft
                state.statistics.meanMmol <= 10.0 -> ColorHigh
                else -> ColorVeryHigh
            }

            val sdVal = state.statistics.sdMmol
            val sdValStr = if (sdVal > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", sdVal)
                else "${(sdVal * 18.0182).toInt()}"
            } else "--"
            val isSdGood = sdVal in 0.01..(if (targetMode == TargetMode.TING) 1.5 else 2.0)

            val cvValStr = if (state.statistics.cvPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.cvPercent) else "--"
            val isCvGood = state.statistics.cvPercent in 0.01..36.0

            val ea1cStr = if (state.statistics.gmiPercent > 0.0) String.format(Locale.US, "%.1f%%", state.statistics.gmiPercent) else "--"
            val isEa1cGood = state.statistics.gmiPercent in 0.01..7.0

            val tirValStr = if (state.statistics.tirPercent > 0.0) "${state.statistics.tirPercent.toInt()}%" else "--"
            val tirColor = when {
                state.statistics.tirPercent <= 0.0 -> onSurfaceVariant
                state.statistics.tirPercent >= 70.0 -> PrimaryEmerald
                state.statistics.tirPercent >= 50.0 -> ColorHigh
                else -> ColorVeryHigh
            }

            val tingValStr = if (state.statistics.tingPercent > 0.0) "${state.statistics.tingPercent.toInt()}%" else "--"
            val isTingGood = state.statistics.tingPercent >= 50.0

            val tbrVal = state.statistics.tbrLowPercent + state.statistics.tbrVeryLowPercent
            val tbrValStr = if (tbrVal > 0.0) String.format(Locale.US, "%.1f%%", tbrVal) else "0%"
            val isTbrGood = tbrVal <= 4.0

            val tarVal = state.statistics.tarHighPercent + state.statistics.tarVeryHighPercent
            val tarValStr = if (tarVal > 0.0) "${tarVal.toInt()}%" else "0%"
            val isTarGood = tarVal <= 25.0

            val hasData = state.recentReadings.isNotEmpty() || state.statistics.meanMmol > 0.0
            val griValStr = if (hasData) "${state.statistics.gri.toInt()}" else "--"
            val griColor = when {
                !hasData -> onSurfaceVariant
                state.statistics.gri <= 20.0 -> PrimaryEmerald
                state.statistics.gri <= 40.0 -> ColorTargetSoft
                else -> ColorHigh
            }

            val gviValStr = if (state.statistics.gvi > 0.0) String.format(Locale.US, "%.2f", state.statistics.gvi) else "--"
            val isGviGood = state.statistics.gvi <= 1.2

            val pgsValStr = if (hasData) String.format(Locale.US, "%.1f", state.statistics.pgs) else "--"
            val isPgsGood = state.statistics.pgs <= 35.0

            val minMaxValStr = if (minVal > 0.0) {
                if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)}–${String.format(Locale.US, "%.1f", maxVal)}"
                else "${(minVal * 18.0182).toInt()}–${(maxVal * 18.0182).toInt()}"
            } else "--"
            val isMinMaxGood = maxVal <= 10.0 && minVal >= 3.9

            val glucoseUnitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль" else "mmol") else (if (isRu) "мг/дл" else "mg/dl")

            DailyGlucoseChart(
                readings = state.recentReadings,
                targetRanges = userSettings.targetRanges,
                unit = unit,
                isRu = isRu,
                treatments = state.treatments,
                showTreatments = userSettings.showTreatmentsOnChart,
                onDeleteTreatment = { viewModel.deleteTreatment(it) },
                selectedMode = focusCardMode,
                onModeChange = { focusCardMode = it },
                onConfigureMetricsClick = { showMetricsOrderDialog = true },
                metricsContent = {
                    @Composable
                    fun RenderMetricWidget(id: String, modifier: Modifier) {
                        when (id.lowercase()) {
                            "mean" -> BentoMetricCompact(
                                title = "Mean",
                                value = meanValStr,
                                unit = glucoseUnitStr,
                                valueColor = meanColor,
                                modifier = modifier,
                                onClick = {
                                    val unitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
                                    val targetVal = if (unit == GlucoseUnit.MMOL_L) "≤7.0–7.8 $unitStr" else "≤126–140 $unitStr"
                                    val healthyMean = if (unit == GlucoseUnit.MMOL_L) "4.5–5.8 $unitStr" else "80–105 $unitStr"
                                    val healthyFasting = if (unit == GlucoseUnit.MMOL_L) "3.3–5.5 $unitStr" else "60–100 $unitStr"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Средний сахар за сегодня (Mean)" else "Today's Mean Glucose",
                                        if (isRu) "Среднее арифметическое измерений с 00:00 до текущей минуты.\n\n" +
                                                "• Клиническая цель при диабете: $targetVal.\n" +
                                                "• У здоровых людей без диабета: средний сахар $healthyMean (натощак $healthyFasting).\n\n" +
                                                "💡 Факт о нормогликемии: у людей без диабета после углеводной еды сахар может кратковременно подскакивать до 8.5–10.0 ммоль/л, но быстро снижается за 20–30 минут."
                                        else "24h average glucose from 00:00 to now.\n\n" +
                                                "• Clinical target in diabetes: $targetVal.\n" +
                                                "• Healthy non-diabetic baseline: average $healthyMean (fasting $healthyFasting).\n\n" +
                                                "💡 CGM fact: healthy individuals can briefly touch ${if (unit == GlucoseUnit.MMOL_L) "8.5–10.0 mmol/L" else "150–180 mg/dL"} after high-carb meals, returning to baseline quickly."
                                    )
                                }
                            )
                            "ea1c" -> BentoMetricCompact(
                                title = "eA1c",
                                value = ea1cStr,
                                unit = if (isRu) "гликир." else "est.",
                                valueColor = if (isEa1cGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    detailDialogInfo = Pair(
                                        if (isRu) "Расчётный гликированный гемоглобин (eA1c)" else "Estimated Glycated Hemoglobin (eA1c)",
                                        if (isRu) "Расчётный HbA1c по формуле ADAG на основе сегодняшнего среднего сахара.\n\n" +
                                                "• Клиническая цель: ≤7.0% (при высокой вариабельности или у пожилых до 7.5–8.0%).\n" +
                                                "• У здоровых людей: 4.0–5.6%.\n\n" +
                                                "💡 Клинический нюанс: лабораторный HbA1c отражает средний сахар за 90-120 дней жизни эритроцитов, тогда как eA1c в TIRUp показывает проекцию сегодняшнего гликемического тренда."
                                        else "Calculated ADAG HbA1c from today's mean.\n\n" +
                                                "• Clinical target: ≤7.0%.\n" +
                                                "• Healthy non-diabetic baseline: 4.0–5.6%.\n\n" +
                                                "💡 Clinical note: lab HbA1c reflects 90-120 days of RBC turnover, while TIRUp eA1c reflects today's trend trajectory."
                                    )
                                }
                            )
                            "sd" -> BentoMetricCompact(
                                title = "SD",
                                value = sdValStr,
                                unit = glucoseUnitStr,
                                valueColor = if (isSdGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    val sdTarget = if (unit == GlucoseUnit.MMOL_L) {
                                        if (isRu) "≤2.0 ммоль/л (или ≤1.5 в узком режиме TING)" else "≤2.0 mmol/L (or ≤1.5 in TING mode)"
                                    } else {
                                        if (isRu) "≤36 мг/дл" else "≤36 mg/dL"
                                    }
                                    val sdHealthy = if (unit == GlucoseUnit.MMOL_L) {
                                        if (isRu) "0.7–1.2 ммоль/л" else "0.7–1.2 mmol/L"
                                    } else {
                                        if (isRu) "12–22 мг/дл" else "12–22 mg/dL"
                                    }
                                    detailDialogInfo = Pair(
                                        if (isRu) "Стандартное отклонение (SD)" else "Standard Deviation (SD)",
                                        if (isRu) "Показывает разброс (амплитуду качелей) сахара вокруг среднего значения.\n\n" +
                                                "• Клиническая цель: $sdTarget.\n" +
                                                "• У здоровых людей: $sdHealthy (разброс минимален).\n\n" +
                                                "💡 Почему это важно: даже при хорошем среднем сахаре высокий SD означает скрытые риски ночных гипогликемий и постпрандиальных пиков."
                                        else "Measures glucose swing amplitude around the mean.\n\n" +
                                                "• Clinical target: $sdTarget.\n" +
                                                "• Healthy non-diabetic baseline: $sdHealthy.\n\n" +
                                                "💡 Clinical value: a good mean with high SD indicates high vulnerability to post-meal spikes and night hypos."
                                    )
                                }
                            )
                            "cv" -> BentoMetricCompact(
                                title = "%CV",
                                value = cvValStr,
                                unit = if (isRu) "вариаб." else "var.",
                                valueColor = if (isCvGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    detailDialogInfo = Pair(
                                        if (isRu) "Коэффициент вариабельности (%CV)" else "Coefficient of Variation (%CV)",
                                        if (isRu) "Относительная стабильность сахара: (SD / Mean) × 100%.\n\n" +
                                                "• Международный консенсус ATTD/ADA: ≤36% указывает на стабильную гликемию. При >36% диабет считается нестабильным (лабильным) с высоким риском тяжелых гипогликемий.\n" +
                                                "• У здоровых людей без диабета: вариабельность составляет всего 10–18%.\n\n" +
                                                "💡 Золотое правило диабетологии: сначала стабилизируем %CV ≤36%, и лишь затем безопасно снижаем средний сахар!"
                                        else "Relative glycemic stability: (SD / Mean) × 100%.\n\n" +
                                                "• ATTD/ADA Consensus: ≤36% is stable. >36% indicates unstable diabetes with high hypo risk.\n" +
                                                "• Healthy non-diabetic baseline: 10–18%.\n\n" +
                                                "💡 Rule of thumb: stabilize %CV below 36% before aggressively lowering mean glucose!"
                                    )
                                }
                            )
                            "tir" -> BentoMetricCompact(
                                title = "TIR",
                                value = tirValStr,
                                unit = if (isRu) "в норме" else "in range",
                                valueColor = tirColor,
                                modifier = modifier,
                                onClick = {
                                    val tirLowStr = if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"
                                    val tirHighStr = if (unit == GlucoseUnit.MMOL_L) "10.0" else "180"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Время в целевом диапазоне (TIR)" else "Time in Range (TIR)",
                                        if (isRu) "Процент времени, когда сахар находился в границах $tirLowStr–$tirHighStr.\n\n" +
                                                "• Международная клиническая цель: ≥70% (каждые +10% TIR снижают риск диабетической ретинопатии на 64% и микроальбуминурии на 40%).\n" +
                                                "• У здоровых людей без диабета: TIR составляет 96–99% времени суток.\n\n" +
                                                "💡 Полноценный анализ требует непрерывного ношения сенсора."
                                        else "Percent of time glucose remained within $tirLowStr–$tirHighStr.\n\n" +
                                                "• Clinical goal: ≥70% (each +10% TIR cuts retinopathy risk by 64% and kidney damage by 40%).\n" +
                                                "• Healthy non-diabetic baseline: 96–99% of 24h period."
                                    )
                                }
                            )
                            "ting" -> BentoMetricCompact(
                                title = "TING",
                                value = tingValStr,
                                unit = if (isRu) "узкий" else "tight",
                                valueColor = if (isTingGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    val tingLowStr = if (unit == GlucoseUnit.MMOL_L) "3.9" else "70"
                                    val tingHighStr = if (unit == GlucoseUnit.MMOL_L) "7.8" else "140"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Время в узком диапазоне (TING)" else "Time in Tight Range (TING)",
                                        if (isRu) "Процент времени в строгом нормогликемическом окне $tingLowStr–$tingHighStr.\n\n" +
                                                "• Клиническая цель: ≥50% (особенно важна при беременности и для продвинутых систем AID/петля).\n" +
                                                "• У здоровых людей без диабета: TING составляет 88–95% времени.\n\n" +
                                                "💡 TING отражает филигранную компенсацию без постпрандиальных скачков."
                                        else "Percent of time within strict physiological range $tingLowStr–$tingHighStr.\n\n" +
                                                "• Clinical target: ≥50% (vital during pregnancy and automated insulin delivery).\n" +
                                                "• Healthy non-diabetic baseline: 88–95%."
                                    )
                                }
                            )
                            "tbr" -> BentoMetricCompact(
                                title = "TBR",
                                value = tbrValStr,
                                unit = if (isRu) "гипо" else "low",
                                valueColor = if (isTbrGood) PrimaryEmerald else ColorVeryHigh,
                                modifier = modifier,
                                onClick = {
                                    val tbrLowStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "<3.9 ммоль/л" else "<3.9 mmol/L") else (if (isRu) "<70 мг/дл" else "<70 mg/dL")
                                    val tbrVeryLowStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "<3.0 ммоль/л" else "<3.0 mmol/L") else (if (isRu) "<54 мг/дл" else "<54 mg/dL")
                                    detailDialogInfo = Pair(
                                        if (isRu) "Время ниже диапазона / Гипогликемия (TBR)" else "Time Below Range (TBR)",
                                        if (isRu) "Суммарный процент времени в гипогликемии ($tbrLowStr):\n\n" +
                                                "• Общий TBR (<3.9): строгая цель <4.0% (<1 часа в сутки).\n" +
                                                "• Тяжёлая гипогликемия ($tbrVeryLowStr, 2-й уровень): цель <1.0% (<15 минут в сутки).\n" +
                                                "• У здоровых людей: физиологические ночные просадки могут составлять 1.1–1.5%, но они безопасны благодаря сохранной контррегуляции глюкагона.\n\n" +
                                                "⚠️ Главный приоритет безопасности: сначала устраняем TBR, затем работаем над TAR!"
                                        else "Percent of time in hypoglycemia ($tbrLowStr):\n\n" +
                                                "• TBR (<3.9): target <4.0% (<1h/day).\n" +
                                                "• Severe Hypo ($tbrVeryLowStr): target <1.0% (<15m/day).\n" +
                                                "• Healthy baseline: 1.1–1.5% during sleep.\n\n" +
                                                "⚠️ First rule of CGM safety: eliminate hypos before attacking hypers!"
                                    )
                                }
                            )
                            "tar" -> BentoMetricCompact(
                                title = "TAR",
                                value = tarValStr,
                                unit = if (isRu) "гипер" else "high",
                                valueColor = if (isTarGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    val tarHighStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) ">10.0 ммоль/л" else ">10.0 mmol/L") else (if (isRu) ">180 мг/дл" else ">180 mg/dL")
                                    val tarVeryHighStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) ">13.9 ммоль/л" else ">13.9 mmol/L") else (if (isRu) ">250 мг/дл" else ">250 mg/dL")
                                    detailDialogInfo = Pair(
                                        if (isRu) "Время выше диапазона / Гипергликемия (TAR)" else "Time Above Range (TAR)",
                                        if (isRu) "Суммарный процент времени в гипергликемии ($tarHighStr):\n\n" +
                                                "• Общий TAR (>10.0): цель <25.0% (<6 часов в сутки).\n" +
                                                "• Тяжёлая гипергликемия ($tarVeryHighStr, 2-й уровень): цель <5.0% (<1.2 часа в сутки).\n" +
                                                "• У здоровых людей: TAR обычно <1-2%.\n\n" +
                                                "💡 Снижение TAR защищает эндотелий сосудов от глюкозотоксичности."
                                        else "Percent of time in hyperglycemia ($tarHighStr):\n\n" +
                                                "• Total TAR (>10.0): target <25.0% (<6h/day).\n" +
                                                "• Severe Hyper ($tarVeryHighStr): target <5.0% (<1.2h/day).\n" +
                                                "• Healthy baseline: <1–2%."
                                    )
                                }
                            )
                            "gri" -> BentoMetricCompact(
                                title = "GRI",
                                value = griValStr,
                                unit = if (isRu) "риск" else "risk",
                                valueColor = griColor,
                                modifier = modifier,
                                onClick = {
                                    detailDialogInfo = Pair(
                                        if (isRu) "Индекс гликемического риска (GRI)" else "Glycemia Risk Index (GRI)",
                                        if (isRu) "Формула Kovatchev (0-100 баллов). Взвешивает риски гипогликемии (с весом ×2.5) и гипергликемии.\n\n" +
                                                "• 0–20: Отлично (A, минимальный риск).\n" +
                                                "• 21–40: Хорошо (B).\n" +
                                                "• 41–60: Средний риск (C).\n" +
                                                "• 61–80: Высокий риск (D).\n" +
                                                "• 81–100: Очень высокий риск (E)."
                                        else "Kovatchev formula (0-100 pts), weighting hypo risk (×2.5) and hyper risk.\n\n" +
                                                "• 0–20: Excellent (A, lowest risk).\n" +
                                                "• 21–40: Good (B).\n" +
                                                "• 41–60: Moderate risk (C).\n" +
                                                "• 61–80: High risk (D).\n" +
                                                "• 81–100: Very high risk (E)."
                                    )
                                }
                            )
                            "gvi" -> BentoMetricCompact(
                                title = "GVI",
                                value = gviValStr,
                                unit = "",
                                valueColor = if (isGviGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    detailDialogInfo = Pair(
                                        if (isRu) "Индекс лабильности (GVI)" else "Glycemic Variability Index (GVI)",
                                        if (isRu) "Отношение реальной длины кривой сахара к идеальной гладкой траектории. Идеал здорового человека: ≤1.20."
                                        else "Curve trajectory length ratio. Healthy baseline: ≤1.20."
                                    )
                                }
                            )
                            "pgs" -> BentoMetricCompact(
                                title = "PGS",
                                value = pgsValStr,
                                unit = if (isRu) "статус" else "status",
                                valueColor = if (isPgsGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    detailDialogInfo = Pair(
                                        if (isRu) "Гликемический статус (PGS)" else "Patient Glycemic Status (PGS)",
                                        if (isRu) "Комплексный балл качества контроля (TIR + Mean + CV). Чем ниже балл, тем ближе гликемия к норме (цель: ≤35.0)."
                                        else "Comprehensive management score (TIR + Mean + CV). Target: ≤35.0."
                                    )
                                }
                            )
                            "minmax" -> BentoMetricCompact(
                                title = "Min/Max",
                                value = minMaxValStr,
                                unit = glucoseUnitStr,
                                valueColor = if (isMinMaxGood) PrimaryEmerald else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    val unitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
                                    val minStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)} $unitStr" else "${(minVal * 18.0182).toInt()} $unitStr"
                                    val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", maxVal)} $unitStr" else "${(maxVal * 18.0182).toInt()} $unitStr"
                                    val healthySpan = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "4.0–7.8 ммоль/л" else "4.0–7.8 mmol/L") else (if (isRu) "72–140 мг/дл" else "72–140 mg/dL")
                                    val healthyFasting = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "3.3–5.5 ммоль/л" else "3.3–5.5 mmol/L") else (if (isRu) "60–100 мг/дл" else "60–100 mg/dL")
                                    detailDialogInfo = Pair(
                                        if (isRu) "Суточный диапазон сахара (Min / Max)" else "Daily Glucose Range",
                                        if (isRu) "Экстремумы сахара за сегодня:\n" +
                                                "• Минимум: $minStr\n" +
                                                "• Максимум: $maxStr\n\n" +
                                                "• У здоровых людей без диабета: 96% времени сахар находится в коридоре $healthySpan (натощак $healthyFasting, ночью во сне возможны кратковременные физиологические спады до 3.3–3.8 ммоль/л).\n" +
                                                "• Клиническая цель при диабете: исключать падения ${if (unit == GlucoseUnit.MMOL_L) "<3.9 ммоль/л" else "<70 мг/дл"} и купировать пики ${if (unit == GlucoseUnit.MMOL_L) ">10.0 ммоль/л" else ">180 мг/дл"}."
                                        else "Extremes for today:\n" +
                                                "• Min: $minStr\n" +
                                                "• Max: $maxStr\n\n" +
                                                "• Healthy non-diabetic baseline: 96% within $healthySpan (fasting $healthyFasting).\n" +
                                                "• Clinical target in diabetes: avoid dips ${if (unit == GlucoseUnit.MMOL_L) "<3.9 mmol/L" else "<70 mg/dL"} and flatten spikes ${if (unit == GlucoseUnit.MMOL_L) ">10.0 mmol/L" else ">180 mg/dL"}."
                                    )
                                }
                            )
                        }
                    }

                    val safeMetricsOrder = if (userSettings.metricsOrder.isNotEmpty()) userSettings.metricsOrder
                    else com.tirup.app.domain.model.DEFAULT_METRICS_ORDER
                    val visibleMetrics = safeMetricsOrder.filterNot { id ->
                        userSettings.hiddenMetrics.any { it.equals(id, ignoreCase = true) }
                    }

                    if (visibleMetrics.isEmpty()) {
                        Text(
                            text = if (isRu) "Все параметры скрыты в настройках" else "All metrics are hidden in settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val chunkedMetrics = visibleMetrics.chunked(4)
                        for (chunk in chunkedMetrics) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (metricId in chunk) {
                                    RenderMetricWidget(id = metricId, modifier = Modifier.weight(1f))
                                }
                                if (chunk.size < 4) {
                                    for (i in 0 until (4 - chunk.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // 3. Goal Compensator: Mode switcher (TIR/TING) moved here, target goals on second line
        item {
            val currentScore = if (targetMode == TargetMode.TIR) state.statistics.tirPercent else state.statistics.tingPercent
            val targetGoal = if (targetMode == TargetMode.TIR) userSettings.targetRanges.tirGoalPercent else userSettings.targetRanges.tingGoalPercent

            val compMessage = if (isRu) {
                if (goal.recommendationRu.isNotEmpty()) goal.recommendationRu
                else "Удерживайте сахар в диапазоне для достижения цели."
            } else {
                if (goal.recommendationEn.isNotEmpty()) goal.recommendationEn
                else "Maintain glucose in target range to reach goal."
            }

            TargetCompensatorCard(
                message = compMessage,
                currentScore = currentScore,
                targetGoal = targetGoal,
                targetMode = targetMode,
                observedPointsCount = goal.observedPointsCount,
                activeMonitoringMinutes = goal.activeMonitoringMinutes,
                unit = unit,
                isRu = isRu,
                onModeChange = { mode -> viewModel.setTargetMode(mode) },
                onClick = {
                    val observedPtsStr = "${goal.observedPointsCount}"
                    val activeMonStr = TargetCompensatorCalculator.formatHoursMins(goal.activeMonitoringMinutes, isRu)
                    val inRangeStr = TargetCompensatorCalculator.formatHoursMins(goal.inRangeMinutes, isRu)
                    val targetMinsStr = TargetCompensatorCalculator.formatHoursMins(goal.targetGoalMinutes, isRu)
                    val outOfRangeStr = TargetCompensatorCalculator.formatHoursMins(goal.outOfRangeMinutes, isRu)
                    val allowedOutStr = TargetCompensatorCalculator.formatHoursMins(goal.allowedOutMinutes, isRu)
                    val remainingDayStr = TargetCompensatorCalculator.formatHoursMins(goal.remainingMinutesToday, isRu)

                    val dialogBody = if (isRu) {
                        "Текущий ${targetMode.name}: ${String.format(Locale.US, "%.1f%%", currentScore)} (Цель: ≥$targetGoal%)\n\n" +
                        "• Замеров за сутки: $observedPtsStr точек ($activeMonStr данных)\n" +
                        "• В диапазоне за сегодня: $inRangeStr (норма ≥$targetMinsStr)\n" +
                        "• Вне диапазона за сегодня: $outOfRangeStr (допустимый лимит: $allowedOutStr)\n" +
                        "• До конца суток осталось: $remainingDayStr\n\n" +
                        "Рекомендация: $compMessage"
                    } else {
                        "Current ${targetMode.name}: ${String.format(Locale.US, "%.1f%%", currentScore)} (Target: ≥$targetGoal%)\n\n" +
                        "• Readings today: $observedPtsStr pts ($activeMonStr data)\n" +
                        "• In range today: $inRangeStr (target ≥$targetMinsStr)\n" +
                        "• Out of range today: $outOfRangeStr (allowed limit: $allowedOutStr)\n" +
                        "• Remaining today: $remainingDayStr\n\n" +
                        "Recommendation: $compMessage"
                    }

                    detailDialogInfo = Pair(
                        if (isRu) "Достижение цели дня" else "Daily Goal Achievement",
                        dialogBody
                    )
                }
            )
        }

        // 3. Night Stability Indicator
        item {
            val nightStability = state.statistics.nightStability
            val hasNightData = nightStability.nightDurationMinutes >= 45 || (nightStability.nightReadingsCount >= 10 && nightStability.nightDurationMinutes >= 30)

            val sdFormatted = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", nightStability.sdMmol)
            else "${(nightStability.sdMmol * 18.0182).toInt()}"
            val sdTarget = if (unit == GlucoseUnit.MMOL_L) 1.5 else (1.5 * 18.0182)

            val nightStatusText = when {
                !hasNightData -> if (isRu) "Недостаточно данных (<1 ч сна)" else "Insufficient data (<1h sleep)"
                nightStability.isGrowthHormoneSpike -> if (isRu) "Всплеск сна (СТГ): max ${String.format(Locale.US, "%.1f", nightStability.maxMmol)}"
                                                       else "Deep-sleep surge (GH): max ${String.format(Locale.US, "%.1f", nightStability.maxMmol)}"
                nightStability.tbrPercent > 1.0 -> if (isRu) "Риск ночных гипо: TBR ${String.format(Locale.US, "%.1f%%", nightStability.tbrPercent)}"
                                                   else "Night hypo risk: TBR ${String.format(Locale.US, "%.1f%%", nightStability.tbrPercent)}"
                nightStability.tarPercent > 25.0 -> if (isRu) "Ночные подъёмы: TAR ${String.format(Locale.US, "%.0f%%", nightStability.tarPercent)}, SD $sdFormatted"
                                                    else "Night highs: TAR ${String.format(Locale.US, "%.0f%%", nightStability.tarPercent)}, SD $sdFormatted"
                nightStability.sdMmol > sdTarget -> if (isRu) "Высокая вариабельность: SD $sdFormatted"
                                                    else "High variability: SD $sdFormatted"
                nightStability.isStable -> if (isRu) "Стабильный профиль: TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)}, SD $sdFormatted"
                                           else "Stable profile: TIR ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)}, SD $sdFormatted"
                else -> if (isRu) "Обнаружены колебания сахара" else "Glucose fluctuations detected"
            }

            val statusColor = when {
                !hasNightData -> onSurfaceVariant
                nightStability.isStable -> PrimaryEmerald
                nightStability.isGrowthHormoneSpike -> ActionBlue
                nightStability.tbrPercent > 1.0 -> ColorVeryLow
                else -> ColorHigh
            }

            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    detailDialogInfo = Pair(
                        if (isRu) "Ночной профиль сна" else "Night Sleep Profile",
                        if (!hasNightData) {
                            if (isRu) "Недостаточно данных для ночного анализа (<1 ч измерений во время сна)."
                            else "Insufficient data for night analysis (<1h readings during sleep)."
                        } else {
                            val unitStr = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
                            val sdStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.2f", nightStability.sdMmol)} $unitStr"
                                        else "${(nightStability.sdMmol * 18.0182).toInt()} $unitStr"
                            val meanStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", nightStability.meanMmol)} $unitStr"
                                          else "${(nightStability.meanMmol * 18.0182).toInt()} $unitStr"
                            val minStr = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", nightStability.minMmol)
                                         else "${(nightStability.minMmol * 18.0182).toInt()}"
                            val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", nightStability.maxMmol)} $unitStr"
                                         else "${(nightStability.maxMmol * 18.0182).toInt()} $unitStr"

                            val hormoneNote = if (nightStability.isGrowthHormoneSpike) {
                                if (isRu) "\n\n🧬 Примечание: в первой половине ночи зафиксирован изолированный подъём сахара до $maxStr без предшествующей гипогликемии. У детей, подростков и людей до 25 лет это частый физиологический признак импульсного выброса соматотропного гормона (СТГ) в фазе глубокого сна."
                                else "\n\n🧬 Note: isolated early-night glucose surge to $maxStr without preceding hypoglycemia. Characteristic physiological sign of deep-sleep growth hormone secretion in youth under 25."
                            } else ""

                            if (isRu) {
                                "Статус: $nightStatusText\n\n" +
                                "• Средний сахар за ночь: $meanStr\n" +
                                "• Ночной размах: $minStr – $maxStr\n" +
                                "• Ночной TIR (3.9–10.0): ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)} (цель ≥70%)\n" +
                                "• Ночной TING (3.9–7.8): ${String.format(Locale.US, "%.0f%%", nightStability.tingPercent)} (цель ≥50%)\n" +
                                "• Разброс (SD): $sdStr (норма ≤1.5)\n" +
                                "• Вариабельность (%CV): ${String.format(Locale.US, "%.1f%%", nightStability.cvPercent)} (норма ≤36%)\n" +
                                "• Ночные гипо (TBR): ${String.format(Locale.US, "%.1f%%", nightStability.tbrPercent)}\n" +
                                "• Длительность сна: ${nightStability.nightDurationMinutes} мин (${nightStability.nightReadingsCount} точек)" +
                                hormoneNote
                            } else {
                                "Status: $nightStatusText\n\n" +
                                "• Night Mean: $meanStr\n" +
                                "• Night Range: $minStr – $maxStr\n" +
                                "• Night TIR (3.9–10.0): ${String.format(Locale.US, "%.0f%%", nightStability.tirPercent)}\n" +
                                "• Night TING (3.9–7.8): ${String.format(Locale.US, "%.0f%%", nightStability.tingPercent)}\n" +
                                "• Variability (SD): $sdStr\n" +
                                "• Coefficient of Var (%CV): ${String.format(Locale.US, "%.1f%%", nightStability.cvPercent)}\n" +
                                "• Night TBR: ${String.format(Locale.US, "%.1f%%", nightStability.tbrPercent)}\n" +
                                "• Sleep Duration: ${nightStability.nightDurationMinutes} min (${nightStability.nightReadingsCount} readings)" +
                                hormoneNote
                            }
                        }
                    )
                }
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
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isRu) "Ночной профиль" else "Night Sleep Profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariant
                            )
                            Text(
                                text = nightStatusText,
                                style = MaterialTheme.typography.titleMedium,
                                color = statusColor
                            )
                        }
                    }

                    Icon(
                        imageVector = if (hasNightData && nightStability.isStable) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
    }

    // Detail Popups
    if (detailDialogInfo != null) {
        AlertDialog(
            onDismissRequest = { detailDialogInfo = null },
            title = {
                Text(
                    text = detailDialogInfo!!.first,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = detailDialogInfo!!.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { detailDialogInfo = null }) {
                    Text(text = if (isRu) "Понятно" else "OK", color = ActionBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Streak Motivator Dialog




    // Device Status Modal
    if (showDeviceModal) {
        DeviceStatusModal(
            sensorStatus = sensorStatus,
            pumpSetStatus = pumpSetStatus,
            isPumpUser = isPumpUser,
            isRu = isRu,
            onDismiss = { showDeviceModal = false },
            onNewSensor = { days -> viewModel.updateSensorInstalled(days) },
            onNewPumpSet = { days -> viewModel.updatePumpSetInstalled(days) }
        )
    }

    // Expired sensor in-app alert
    if (showExpiredSensorDialog && !sensorExpiredDismissed) {
        DeviceExpiredAlertDialog(
            isSensor = true,
            daysExpired = -sensorStatus.daysRemaining,
            isRu = isRu,
            onDismiss = { sensorExpiredDismissed = true },
            onInstallNow = {
                sensorExpiredDismissed = true
                showDeviceModal = true
            }
        )
    }

    // Expired pump set in-app alert
    if (showExpiredPumpDialog && !pumpExpiredDismissed) {
        DeviceExpiredAlertDialog(
            isSensor = false,
            daysExpired = -pumpSetStatus.daysRemaining,
            isRu = isRu,
            onDismiss = { pumpExpiredDismissed = true },
            onInstallNow = {
                pumpExpiredDismissed = true
                showDeviceModal = true
            }
        )
    }

    if (showStreakDialog) {
        StreakMotivatorDialog(
            streakDays = state.streakDays,
            isRu = isRu,
            dailySummaries = state.recentDailySummaries,
            todayTirPercent = state.statistics.tirPercent,
            onDismiss = {
                showStreakDialog = false
                viewModel.markStreakCelebrated(state.streakDays)
            }
        )
    }

    // Metrics Order Configuration Dialog
    if (showMetricsOrderDialog) {
        MetricsOrderDialog(
            currentOrder = userSettings.metricsOrder,
            hiddenMetrics = userSettings.hiddenMetrics,
            isRu = isRu,
            onSave = { newOrder, hidden ->
                viewModel.updateMetricsConfiguration(newOrder, hidden)
            },
            onDismiss = { showMetricsOrderDialog = false }
        )
    }

    // Daily Alert Logs Dialog
    if (showDailyAlertLogsDialog) {
        DailyAlertLogsDialog(
            logs = dailyAlertLogs,
            isRu = isRu,
            onDismiss = { showDailyAlertLogsDialog = false }
        )
    }
}

@Composable
private fun BleTransmitterBadge(
    isBleBroadcasting: Boolean,
    broadcastRemainingSec: Int,
    nextHeartbeatRemainingSec: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isBleBroadcasting) {
        val transition = rememberInfiniteTransition(label = "BleWaves")
        val wave1Progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave1"
        )
        val wave2Progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, delayMillis = 450, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave2"
        )
        val wave3Progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, delayMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave3"
        )

        Surface(
            modifier = modifier
                .size(width = 62.dp, height = 24.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(10.dp),
            color = ActionBlue.copy(alpha = 0.14f),
            border = BorderStroke(0.8.dp, ActionBlue.copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width.coerceAtLeast(size.height) * 0.9f

                    val waves = listOf(wave1Progress, wave2Progress, wave3Progress)
                    for (prog in waves) {
                        if (prog > 0.05f) {
                            val r = prog * maxRadius
                            val alpha = (1f - prog).coerceIn(0f, 1f) * 0.85f
                            val strokeW = (2.2f * (1f - prog * 0.4f)).dp.toPx()

                            // 120-degree wave arc to the right (centered at 0°)
                            drawArc(
                                color = ActionBlue.copy(alpha = alpha),
                                startAngle = -60f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(center.x - r, center.y - r),
                                size = Size(r * 2, r * 2),
                                style = Stroke(width = strokeW)
                            )
                            // 120-degree wave arc to the left (centered at 180°)
                            drawArc(
                                color = ActionBlue.copy(alpha = alpha),
                                startAngle = 120f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(center.x - r, center.y - r),
                                size = Size(r * 2, r * 2),
                                style = Stroke(width = strokeW)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Broadcasting",
                    tint = ActionBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    } else {
        // Idle state: Bluetooth icon + countdown mm:ss to next periodic heartbeat
        val minutes = (nextHeartbeatRemainingSec / 60).coerceAtLeast(0)
        val seconds = (nextHeartbeatRemainingSec % 60).coerceAtLeast(0)
        val countdownText = String.format(Locale.US, "%d:%02d", minutes, seconds)

        Surface(
            modifier = modifier
                .size(width = 62.dp, height = 24.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(10.dp),
            color = ActionBlue.copy(alpha = 0.08f),
            border = BorderStroke(0.8.dp, ActionBlue.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Master",
                    tint = ActionBlue.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = ActionBlue.copy(alpha = 0.85f)
                )
            }
        }

    }
}

@Composable
private fun HeroGlucoseCard(
    latestReading: GlucoseReading?,
    recentReadings: List<GlucoseReading>,
    unit: GlucoseUnit,
    isRu: Boolean,
    activeAlertBanner: ActiveAlertBanner? = null,
    masterBatteryPct: Int? = null,
    isMasterBatteryStale: Boolean = false,
    isBroadcaster: Boolean = false,
    isBleBroadcasting: Boolean = false,
    broadcastRemainingSec: Int = 0,
    nextHeartbeatRemainingSec: Int = 300,
    dailyAlertsCount: Int = 0,
    onAlertHistoryClick: () -> Unit = {},
    onBatteryClick: () -> Unit = {},
    onIobClick: () -> Unit = {},
    onCobClick: () -> Unit = {},
    onBleClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val now = System.currentTimeMillis()
    val diffMinutes = if (latestReading != null) ((now - latestReading.timestamp) / 60000L).toInt().coerceAtLeast(0) else 0

    val isStale = diffMinutes > 5
    val valueColor = when {
        latestReading == null -> onSurfaceVariant
        isStale -> onSurfaceVariant.copy(alpha = 0.55f)
        latestReading.valueMmol < 3.0 -> ColorVeryLow
        latestReading.valueMmol < 3.9 -> ColorLow
        latestReading.valueMmol in 3.9..7.0 -> ColorTight
        latestReading.valueMmol in 7.01..7.8 -> ColorTargetSoft
        latestReading.valueMmol in 7.81..10.0 -> ColorTarget
        latestReading.valueMmol in 10.01..13.9 -> ColorHigh
        else -> ColorVeryHigh
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val heroBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val heroBorderColor = if (isDark) valueColor.copy(alpha = 0.38f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val heroBorderWidth = if (isDark) 1.2.dp else 1.dp

    val sorted = remember(recentReadings) { recentReadings.sortedBy { it.timestamp } }

    // Clinical 5-minute velocity delta (find point ~5 minutes ago in window 3.5..7.5 min)
    val delta5Min = remember(latestReading, sorted) {
        if (latestReading == null || sorted.size < 2) null
        else {
            val targetTime = latestReading.timestamp - 5 * 60_000L
            val candidate = sorted
                .filter { it.timestamp in (targetTime - 120_000L)..(targetTime + 120_000L) && it.timestamp != latestReading.timestamp }
                .minByOrNull { kotlin.math.abs(it.timestamp - targetTime) }
            val reference = candidate ?: sorted.filter { it.timestamp < latestReading.timestamp }.maxByOrNull { it.timestamp }
            if (reference != null) {
                val dtMin = ((latestReading.timestamp - reference.timestamp) / 60_000.0).coerceAtLeast(1.0)
                val diff = latestReading.valueMmol - reference.valueMmol
                val ratePerMin = diff / dtMin
                Triple(ratePerMin, diff, dtMin)
            } else null
        }
    }

    // Operational clinical status notification
    val statusInfo: Pair<String, Color>? = remember(latestReading, sorted, delta5Min, isRu) {
        if (latestReading == null) null
        else {
            val v = latestReading.valueMmol
            val rate = delta5Min?.first ?: 0.0

            when {
                v < 3.0 -> Pair(if (isRu) "🚨 Тяжёлая гипогликемия! Быстрые углеводы!" else "🚨 Severe low! Fast carbs now!", ColorVeryLow)
                v < 3.9 -> Pair(if (isRu) "🔻 Ниже целевого диапазона" else "🔻 Below target range", ColorLow)
                v > 13.9 -> Pair(if (isRu) "⚠️ Экстремальный сахар! Проверьте кетоны" else "⚠️ Very high! Check ketones", ColorVeryHigh)
                v > 10.0 -> Pair(if (isRu) "🔺 Выше целевого диапазона" else "🔺 Above target range", ColorHigh)
                rate <= -0.11 -> Pair(
                    if (unit == GlucoseUnit.MMOL_L) {
                        if (isRu) String.format(Locale.US, "⚡ Быстро падает (%.2f ммоль/л/мин)", rate)
                        else String.format(Locale.US, "⚡ Dropping fast (%.2f mmol/L/min)", rate)
                    } else {
                        val rateMg = rate * 18.0182
                        if (isRu) String.format(Locale.US, "⚡ Быстро падает (%.1f мг/дл/мин)", rateMg)
                        else String.format(Locale.US, "⚡ Dropping fast (%.1f mg/dL/min)", rateMg)
                    },
                    ColorLow
                )
                rate >= 0.11 -> Pair(
                    if (unit == GlucoseUnit.MMOL_L) {
                        if (isRu) String.format(Locale.US, "⚡ Быстро растёт (+%.2f ммоль/л/мин)", rate)
                        else String.format(Locale.US, "⚡ Rising fast (+%.2f mmol/L/min)", rate)
                    } else {
                        val rateMg = rate * 18.0182
                        if (isRu) String.format(Locale.US, "⚡ Быстро растёт (+%.1f мг/дл/мин)", rateMg)
                        else String.format(Locale.US, "⚡ Rising fast (+%.1f mg/dL/min)", rateMg)
                    },
                    ColorHigh
                )
                else -> {
                    // Scope continuous in-range status to the current day (00:00)
                    val nowMs = latestReading.timestamp
                    val calendar = java.util.Calendar.getInstance().apply {
                        timeInMillis = nowMs
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val startOfDay = calendar.timeInMillis

                    val todayReadings = sorted.filter { it.timestamp >= startOfDay }
                    val todayInRange = todayReadings.takeLastWhile { it.valueMmol in 3.9..10.0 }

                    if (todayInRange.isNotEmpty()) {
                        val isSinceMidnight = todayInRange.size == todayReadings.size && todayReadings.isNotEmpty()
                        val spanMs = nowMs - todayInRange.first().timestamp
                        val spanHours = spanMs / 3_600_000.0

                        if (isSinceMidnight && spanHours >= 0.5) {
                            Pair(
                                if (isRu) String.format(Locale.US, "✨ В норме с начала суток (%.1f ч)", spanHours)
                                else String.format(Locale.US, "✨ In target since midnight (%.1f h)", spanHours),
                                PrimaryEmerald
                            )
                        } else if (spanHours >= 0.5) {
                            val spanMin = (spanMs / 60_000L).toInt()
                            if (spanHours >= 1.0) {
                                Pair(
                                    if (isRu) String.format(Locale.US, "✨ В норме последние %.1f ч", spanHours)
                                    else String.format(Locale.US, "✨ In target for last %.1f h", spanHours),
                                    PrimaryEmerald
                                )
                            } else {
                                Pair(
                                    if (isRu) "✨ В норме последние $spanMin мин"
                                    else "✨ In target for last $spanMin min",
                                    PrimaryEmerald
                                )
                            }
                        } else {
                            Pair(if (isRu) "В целевом диапазоне" else "In target range", PrimaryEmerald)
                        }
                    } else {
                        Pair(if (isRu) "В целевом диапазоне" else "In target range", PrimaryEmerald)
                    }
                }
            }
        }
    }

    val timeLabel = when {
        latestReading == null -> if (isRu) "Нет данных" else "No data"
        diffMinutes <= 1 -> ""
        diffMinutes < 60 -> if (isRu) "$diffMinutes мин назад" else "${diffMinutes}m ago"
        else -> if (isRu) "${diffMinutes / 60} ч назад" else "${diffMinutes / 60}h ago"
    }

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = heroBgColor,
        borderColor = heroBorderColor,
        borderWidth = heroBorderWidth,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hasIob = (latestReading?.iob != null && latestReading.iob > 0.0)
            val hasCob = (latestReading?.cob != null && latestReading.cob > 0.0)
            val hasBattery = masterBatteryPct != null

            // Header Row: [ Bell History ] --- [ Badges: Battery/IoB/CoB ] --- [ BLE Master pulse ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Alert History Bell
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (dailyAlertsCount > 0) ColorHigh.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.8.dp, if (dailyAlertsCount > 0) ColorHigh.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.clickable { onAlertHistoryClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🔔", fontSize = 12.sp)
                        if (dailyAlertsCount > 0) {
                            Text(
                                text = "$dailyAlertsCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ColorHigh
                            )
                        }
                    }
                }

                // Center: Dynamic Badges (Battery, IoB, CoB)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasBattery) {
                        val grayColor = Color(0xFF94A3B8)
                        val (batText, batColor) = if (isMasterBatteryStale) {
                            Pair("📱 🔋 ?", grayColor)
                        } else {
                            val color = when {
                                masterBatteryPct!! <= 15 -> ColorVeryLow
                                masterBatteryPct <= 25 -> ColorHigh
                                else -> PrimaryEmerald
                            }
                            Pair("📱 🔋 $masterBatteryPct%", color)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = batColor.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, batColor.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onBatteryClick() }
                        ) {
                            Text(
                                text = batText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = batColor
                            )
                        }
                    }

                    if (hasIob) {
                        if (hasBattery) Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ActionBlue.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, ActionBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onIobClick() }
                        ) {
                            Text(
                                text = String.format(Locale.US, if (isRu) "💉 %.2f Ед" else "💉 %.2f U", latestReading!!.iob),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }
                    }

                    if (hasCob) {
                        if (hasIob || hasBattery) Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, PrimaryEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onCobClick() }
                        ) {
                            Text(
                                text = String.format(Locale.US, if (isRu) "🍞 %.0f г" else "🍞 %.0f g", latestReading!!.cob),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }
                    }
                }

                // Right: Master BLE Pulse Status or placeholder spacer
                if (isBroadcaster) {
                    BleTransmitterBadge(
                        isBleBroadcasting = isBleBroadcasting,
                        broadcastRemainingSec = broadcastRemainingSec,
                        nextHeartbeatRemainingSec = nextHeartbeatRemainingSec,
                        onClick = onBleClick
                    )
                } else {
                    Spacer(modifier = Modifier.width(62.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Large Hero Value with Trend Arrow and Delta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val displayVal = if (latestReading != null) {
                    if (unit == GlucoseUnit.MMOL_L) {
                        String.format(Locale.US, "%.1f", latestReading.valueMmol)
                    } else {
                        String.format(Locale.US, "%d", (latestReading.valueMmol * 18.0182).toInt())
                    }
                } else "--"

                Text(
                    text = displayVal,
                    style = MaterialTheme.typography.displayLarge,
                    color = valueColor,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = if (unit == GlucoseUnit.MMOL_L) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latestReading?.trendArrow?.isNotEmpty() == true) {
                            Text(
                                text = latestReading.trendArrow,
                                style = MaterialTheme.typography.headlineMedium,
                                color = valueColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (delta5Min != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val deltaVal = delta5Min.second
                            val deltaFormatted = if (unit == GlucoseUnit.MMOL_L) {
                                String.format(Locale.US, "%+.1f", deltaVal)
                            } else {
                                val mg = (deltaVal * 18.0182).roundToInt()
                                "${if (mg > 0) "+" else ""}$mg"
                            }
                            Text(
                                text = deltaFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                color = onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (activeAlertBanner != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val bannerColor = when (activeAlertBanner.tier) {
                    AlertTier.CRITICAL -> ColorVeryLow
                    AlertTier.MAIN -> if (activeAlertBanner.title.contains("гипо", ignoreCase = true) || activeAlertBanner.title.contains("low", ignoreCase = true)) ColorVeryLow else ColorHigh
                    AlertTier.PREDICTIVE -> if (activeAlertBanner.title.contains("гипо", ignoreCase = true) || activeAlertBanner.title.contains("low", ignoreCase = true)) ColorLow else ColorHigh
                    AlertTier.SIGNAL_LOSS -> ColorHigh
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = bannerColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeAlertBanner.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = bannerColor,
                            textAlign = TextAlign.Center
                        )
                        if (activeAlertBanner.message.isNotBlank() && activeAlertBanner.message != activeAlertBanner.title) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeAlertBanner.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (timeLabel.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (statusInfo != null || timeLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background((statusInfo?.second ?: PrimaryEmerald).copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (statusInfo != null) {
                        Text(
                            text = statusInfo.first,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusInfo.second,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (statusInfo != null && timeLabel.isNotEmpty()) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                    if (timeLabel.isNotEmpty()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                }
            }

            if (latestReading == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ActionBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "⏳", fontSize = 18.sp)
                        Text(
                            text = if (isRu) "Ожидание первого замера от источника (1–5 мин). Убедитесь, что в xDrip+ включено локальное вещание (Inter-app Broadcast) или активен BLE-мост."
                            else "Awaiting first reading (1–5 min). Ensure xDrip+ Inter-app Broadcast is enabled or BLE Bridge is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun TargetModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF2563EB) else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TargetCompensatorCard(
    message: String,
    currentScore: Double,
    targetGoal: Int,
    targetMode: TargetMode,
    observedPointsCount: Int,
    activeMonitoringMinutes: Int,
    unit: GlucoseUnit,
    isRu: Boolean,
    onModeChange: (TargetMode) -> Unit,
    onClick: () -> Unit
) {
    val progress = (currentScore / 100.0).toFloat().coerceIn(0f, 1f)
    val isGoalMet = currentScore >= targetGoal
    val progressColor = if (isGoalMet) PrimaryEmerald else ColorHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Left (Title + Subtitle target), Right (TIR/TING switcher)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.compensator_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface
                        )
                    }

                    // Target goal text on a new row under title
                    Text(
                        text = "${String.format(Locale.US, "%.0f%%", currentScore)} / ${if (isRu) "Цель:" else "Goal:"} ≥$targetGoal%",
                        style = MaterialTheme.typography.bodySmall,
                        color = progressColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Mode Selector: TIR vs TING on the right
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tirTitle = if (unit == GlucoseUnit.MMOL_L) "TIR" else "TIR"
                    val tingTitle = if (unit == GlucoseUnit.MMOL_L) "TING" else "TING"
                    TargetModeChip(
                        title = tirTitle,
                        selected = targetMode == TargetMode.TIR,
                        onClick = { onModeChange(TargetMode.TIR) }
                    )
                    TargetModeChip(
                        title = tingTitle,
                        selected = targetMode == TargetMode.TING,
                        onClick = { onModeChange(TargetMode.TING) }
                    )
                }
            }

            if (observedPointsCount > 0) {
                val durationStr = TargetCompensatorCalculator.formatHoursMins(activeMonitoringMinutes, isRu)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = if (isRu) "📊 $observedPointsCount измерений за сегодня ($durationStr мониторинга)"
                               else "📊 $observedPointsCount readings today ($durationStr active)",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Proportional Progress Bar (0..100% of the bar)
            val trackBg = if (isGoalMet) PrimaryEmerald.copy(alpha = 0.18f) else ColorHigh.copy(alpha = 0.18f)
            val activeColor = if (isGoalMet) Color(0xFF10B981) else Color(0xFFF59E0B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(trackBg)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    color = activeColor,
                    trackColor = Color.Transparent
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyAlertLogsDialog(
    logs: List<AlertLogEntry>,
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🔔", fontSize = 20.sp)
                Text(
                    text = if (isRu) "Журнал тревог" else "Alert Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = if (isRu) "Сегодня тревожных событий и выходов за целевой диапазон не зафиксировано. Отличная компенсация! ✨"
                           else "No alert events recorded today. Great glycemic control! ✨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs.size) { idx ->
                        val entry = logs[idx]
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
                        val (tierLabel, tierColor) = when (entry.tier) {
                            AlertTier.CRITICAL -> Pair(if (isRu) "🚨 КРИТИЧЕСКИЙ" else "🚨 CRITICAL", ColorVeryLow)
                            AlertTier.MAIN -> Pair(if (isRu) "🔔 ОСНОВНОЙ" else "🔔 MAIN", ColorHigh)
                            AlertTier.PREDICTIVE -> Pair(if (isRu) "⚡ УПРЕЖДАЮЩИЙ" else "⚡ PREDICTIVE", ActionBlue)
                            AlertTier.SIGNAL_LOSS -> Pair(if (isRu) "📡 ПОТЕРЯ СВЯЗИ" else "📡 SIGNAL LOSS", Color(0xFF94A3B8))
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, tierColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = tierColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = tierLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tierColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isRu) "Закрыть" else "Close", color = ActionBlue, fontWeight = FontWeight.Bold)
            }
        }
    )

}
