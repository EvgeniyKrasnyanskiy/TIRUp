package com.tirup.app.presentation.focus

import android.text.format.DateUtils
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
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.CompensatorStatus
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.BentoMetricCompact
import com.tirup.app.presentation.components.MetricsOrderDialog
import com.tirup.app.presentation.components.StreakBadge
import com.tirup.app.presentation.components.StreakMotivatorDialog
import androidx.compose.foundation.BorderStroke
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTarget
import com.tirup.app.presentation.theme.ColorTargetSoft
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var detailDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMetricsOrderDialog by remember { mutableStateOf(false) }
    var focusCardMode by rememberSaveable { mutableStateOf(0) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val userSettings = state.userSettings
    val isRu = userSettings.language.equals("RU", ignoreCase = true)
    val targetMode = userSettings.targetMode
    val unit = userSettings.unit
    val goal = state.compensatorGoal

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

                StreakBadge(
                    streakDays = state.streakDays,
                    onClick = {
                        detailDialogInfo = Pair(
                            if (isRu) "Серия дней в целевом диапазоне (Стрик)" else "Target Range Day Streak",
                            if (isRu) "Текущая серия: ${state.streakDays} дн.\n\nКаждый день, когда суточный TIR удерживается выше целевого уровня (≥70%), увеличивает серию. Непрерывный контроль помогает закрепить стабильные привычки и защищает сосудистую систему."
                            else "Current streak: ${state.streakDays} days.\n\nEvery day when daily TIR stays above the target level (≥70%), your streak increases. Continuous control helps build consistent habits and protects vascular health."
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

        // 1. Hero Card: Current Glucose
        item {
            HeroGlucoseCard(
                latestReading = state.latestReading,
                recentReadings = state.recentReadings,
                unit = unit,
                isRu = isRu,
                onClick = {
                    val r = state.latestReading
                    if (r != null) {
                        val rVal = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", r.valueMmol)} ${if (isRu) "ммоль/л" else "mmol/L"}"
                                   else "${(r.valueMmol * 18.0182).toInt()} ${if (isRu) "мг/дл" else "mg/dL"}"
                        detailDialogInfo = Pair(
                            if (isRu) "Текущий уровень сахара" else "Current Glucose Level",
                            if (isRu) "Значение: $rVal\nНаправление тренда: ${r.trendArrow}\nВремя измерения: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}"
                            else "Value: $rVal\nTrend direction: ${r.trendArrow}\nTime: ${DateUtils.getRelativeTimeSpanString(r.timestamp)}"
                        )
                    }
                }
            )
        }

        // 2. Interactive 24-Hour Daily Glucose Chart (Pinch-to-zoom & Pan) with Metrics toggle
        item {
            val minVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.minOf { it.valueMmol } else 0.0
            val maxVal = if (state.recentReadings.isNotEmpty()) state.recentReadings.maxOf { it.valueMmol } else 0.0

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

            val griValStr = if (state.statistics.gri > 0.0) "${state.statistics.gri.toInt()}" else "--"
            val griColor = when {
                state.statistics.gri <= 0.0 -> onSurfaceVariant
                state.statistics.gri <= 20.0 -> PrimaryEmerald
                state.statistics.gri <= 40.0 -> ColorTargetSoft
                else -> ColorHigh
            }

            val gviValStr = if (state.statistics.gvi > 0.0) String.format(Locale.US, "%.2f", state.statistics.gvi) else "--"
            val isGviGood = state.statistics.gvi <= 1.2

            val pgsValStr = if (state.statistics.pgs > 0.0) String.format(Locale.US, "%.1f", state.statistics.pgs) else "--"
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
                                    val targetVal = if (unit == GlucoseUnit.MMOL_L) "≤7.0–7.8 ммоль/л" else "≤126–140 мг/дл"
                                    val healthyMean = if (unit == GlucoseUnit.MMOL_L) "4.5–5.8 ммоль/л" else "80–105 мг/дл"
                                    val healthyFasting = if (unit == GlucoseUnit.MMOL_L) "3.3–5.5 ммоль/л" else "60–100 мг/дл"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Средний сахар за сегодня (Mean)" else "Today's Mean Glucose",
                                        if (isRu) "Среднее арифметическое измерений с 00:00 до текущей минуты.\n\n" +
                                                "• Клиническая цель при диабете: $targetVal.\n" +
                                                "• У здоровых людей без диабета: средний сахар $healthyMean (натощак $healthyFasting).\n\n" +
                                                "💡 Факт о нормогликемии: у людей без диабета после углеводной еды сахар может кратковременно подскакивать до 8.5–10.0 ммоль/л, но быстро снижается за 20–30 минут."
                                        else "24h average glucose from 00:00 to now.\n\n" +
                                                "• Clinical target in diabetes: $targetVal.\n" +
                                                "• Healthy non-diabetic baseline: average $healthyMean (fasting $healthyFasting).\n\n" +
                                                "💡 CGM fact: healthy individuals can briefly touch 8.5–10.0 mmol/L after high-carb meals, returning to baseline quickly."
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
                                    val sdTarget = if (unit == GlucoseUnit.MMOL_L) "≤2.0 ммоль/л (или ≤1.5 в узком режиме TING)" else "≤36 мг/дл"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Стандартное отклонение (SD)" else "Standard Deviation (SD)",
                                        if (isRu) "Показывает разброс (амплитуду качелей) сахара вокруг среднего значения.\n\n" +
                                                "• Клиническая цель: $sdTarget.\n" +
                                                "• У здоровых людей: 0.7–1.2 ммоль/л (разброс минимален).\n\n" +
                                                "💡 Почему это важно: даже при хорошем среднем сахаре высокий SD означает скрытые риски ночных гипогликемий и постпрандиальных пиков."
                                        else "Measures glucose swing amplitude around the mean.\n\n" +
                                                "• Clinical target: $sdTarget.\n" +
                                                "• Healthy non-diabetic baseline: 0.7–1.2 mmol/L.\n\n" +
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
                                valueColor = if (isTbrGood) onSurfaceVariant else ColorVeryHigh,
                                modifier = modifier,
                                onClick = {
                                    val tbrLowStr = if (unit == GlucoseUnit.MMOL_L) "<3.9 ммоль/л" else "<70 мг/дл"
                                    val tbrVeryLowStr = if (unit == GlucoseUnit.MMOL_L) "<3.0 ммоль/л" else "<54 мг/дл"
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
                                valueColor = if (isTarGood) onSurfaceVariant else ColorHigh,
                                modifier = modifier,
                                onClick = {
                                    val tarHighStr = if (unit == GlucoseUnit.MMOL_L) ">10.0 ммоль/л" else ">180 мг/дл"
                                    val tarVeryHighStr = if (unit == GlucoseUnit.MMOL_L) ">13.9 ммоль/л" else ">250 мг/дл"
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
                                unit = if (isRu) "линия" else "line",
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
                                    val minStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", minVal)} ммоль/л" else "${(minVal * 18.0182).toInt()} мг/дл"
                                    val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", maxVal)} ммоль/л" else "${(maxVal * 18.0182).toInt()} мг/дл"
                                    val healthySpan = if (unit == GlucoseUnit.MMOL_L) "4.0–7.8 ммоль/л" else "72–140 мг/дл"
                                    val healthyFasting = if (unit == GlucoseUnit.MMOL_L) "3.3–5.5 ммоль/л" else "60–100 мг/дл"
                                    detailDialogInfo = Pair(
                                        if (isRu) "Суточный диапазон сахара (Min / Max)" else "Daily Glucose Range",
                                        if (isRu) "Экстремумы сахара за сегодня:\n" +
                                                "• Минимум: $minStr\n" +
                                                "• Максимум: $maxStr\n\n" +
                                                "• У здоровых людей без диабета: 96% времени сахар находится в коридоре $healthySpan (натощак $healthyFasting, ночью во сне возможны кратковременные физиологические спады до 3.3–3.8 ммоль/л).\n" +
                                                "• Клиническая цель при диабете: исключать падения <3.9 и купировать пики >10.0 ммоль/л."
                                        else "Extremes for today:\n" +
                                                "• Min: $minStr\n" +
                                                "• Max: $maxStr\n\n" +
                                                "• Healthy non-diabetic baseline: 96% within $healthySpan (fasting $healthyFasting).\n" +
                                                "• Clinical target in diabetes: avoid dips <3.9 and flatten spikes >10.0 mmol/L."
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
                        if (isRu) "Компенсация цели" else "Goal Compensation",
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
                            val sdStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.2f", nightStability.sdMmol)} ммоль/л"
                                        else "${(nightStability.sdMmol * 18.0182).toInt()} мг/дл"
                            val meanStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", nightStability.meanMmol)} ммоль/л"
                                          else "${(nightStability.meanMmol * 18.0182).toInt()} мг/дл"
                            val minStr = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", nightStability.minMmol)
                                         else "${(nightStability.minMmol * 18.0182).toInt()}"
                            val maxStr = if (unit == GlucoseUnit.MMOL_L) "${String.format(Locale.US, "%.1f", nightStability.maxMmol)} ммоль/л"
                                         else "${(nightStability.maxMmol * 18.0182).toInt()} мг/дл"

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
                    Text(text = if (isRu) "Понятно" else "OK", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Streak Motivator Dialog
    if (showStreakDialog) {
        StreakMotivatorDialog(
            streakDays = state.streakDays,
            isRu = isRu,
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
}

@Composable
private fun HeroGlucoseCard(
    latestReading: GlucoseReading?,
    recentReadings: List<GlucoseReading>,
    unit: GlucoseUnit,
    isRu: Boolean,
    onClick: () -> Unit
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val now = System.currentTimeMillis()
    val diffMinutes = if (latestReading != null) ((now - latestReading.timestamp) / 60000L).toInt().coerceAtLeast(0) else 0

    // Operational clinical status notification
    val statusInfo: Pair<String, Color>? = remember(latestReading, recentReadings, isRu) {
        if (latestReading == null) null
        else {
            val v = latestReading.valueMmol
            val sorted = recentReadings.sortedBy { it.timestamp }
            val rate = if (sorted.size >= 2) {
                val prev = sorted[sorted.size - 2]
                val dtMin = ((latestReading.timestamp - prev.timestamp) / 60000.0).coerceAtLeast(1.0)
                (latestReading.valueMmol - prev.valueMmol) / dtMin
            } else 0.0

            when {
                v < 3.0 -> Pair(if (isRu) "🚨 Тяжёлая гипогликемия! Быстрые углеводы!" else "🚨 Severe low! Fast carbs now!", ColorVeryLow)
                v < 3.9 -> Pair(if (isRu) "🔻 Ниже целевого диапазона" else "🔻 Below target range", ColorLow)
                v > 13.9 -> Pair(if (isRu) "⚠️ Экстремальный сахар! Проверьте кетоны" else "⚠️ Very high! Check ketones", ColorVeryHigh)
                v > 10.0 -> Pair(if (isRu) "🔺 Выше целевого диапазона" else "🔺 Above target range", ColorHigh)
                rate <= -0.12 -> Pair(
                    if (isRu) String.format(Locale.US, "⚡ Быстро падает (%.2f ммоль/л/мин)", rate)
                    else String.format(Locale.US, "⚡ Dropping fast (%.2f mmol/L/min)", rate),
                    ColorLow
                )
                rate >= 0.12 -> Pair(
                    if (isRu) String.format(Locale.US, "⚡ Быстро растёт (+%.2f ммоль/л/мин)", rate)
                    else String.format(Locale.US, "⚡ Rising fast (+%.2f mmol/L/min)", rate),
                    ColorHigh
                )
                else -> {
                    val inRangeCount = sorted.takeLastWhile { it.valueMmol in 3.9..10.0 }.size
                    if (inRangeCount >= 12) {
                        val hours = inRangeCount * 5 / 60.0
                        Pair(
                            if (isRu) String.format(Locale.US, "✨ В норме последние %.1f ч", hours)
                            else String.format(Locale.US, "✨ In target for last %.1f h", hours),
                            PrimaryEmerald
                        )
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
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hasIob = (latestReading?.iob != null && latestReading.iob > 0.0)
            val hasCob = (latestReading?.cob != null && latestReading.cob > 0.0)

            if (hasIob || hasCob) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasIob) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ActionBlue.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, ActionBlue.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = String.format(Locale.US, if (isRu) "💉 %.2f Ед IoB" else "💉 %.2f U IoB", latestReading!!.iob),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }
                    }

                    if (hasCob) {
                        if (hasIob) Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, PrimaryEmerald.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = String.format(Locale.US, if (isRu) "🍞 %.0f г CoB" else "🍞 %.0f g CoB", latestReading!!.cob),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Large Hero Value with Trend Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
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
                    if (latestReading?.trendArrow?.isNotEmpty() == true) {
                        Text(
                            text = latestReading.trendArrow,
                            style = MaterialTheme.typography.headlineMedium,
                            color = valueColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (statusInfo != null || timeLabel.isNotEmpty()) {
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
                        text = if (isRu) "📊 $observedPointsCount точек за сегодня ($durationStr мониторинга)"
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
