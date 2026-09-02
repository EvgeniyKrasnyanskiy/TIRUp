package com.tirup.app.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.localizeDiabetesType
import com.tirup.app.domain.model.localizeTherapyType
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.trends.AgpChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgpSheetPreviewModal(
    title: String,
    periodLabel: String,
    dateRangeStr: String,
    readings: List<GlucoseReading>,
    statistics: GlucoseStatistics,
    userSettings: UserSettings,
    isGenerating: Boolean,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val patient = userSettings.patientProfile
    val isRu = userSettings.language.equals("RU", ignoreCase = true)
    val isMmol = userSettings.unit == GlucoseUnit.MMOL_L
    val agpBins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF8FAFC),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modal Top Toolbar (Theme adaptive)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRu) "Предпросмотр бланка AGP" else "AGP Report Sheet Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Printable A4 Sheet Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Header Banner
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = if (isRu) "АМБУЛАТОРНЫЙ ГЛЮКОЗНЫЙ ПРОФИЛЬ (AGP)" else "AMBULATORY GLUCOSE PROFILE (AGP)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                val pName = if (patient.fullName.isNotBlank()) patient.fullName else "___________________________________"
                                val pAge = if (patient.fullName.isNotBlank() || patient.birthYear != 1990) "${patient.calculatedAge} ${if (isRu) "лет" else "y.o."}" else "_______"
                                val pWeight = if (patient.weightKg.isNotBlank()) "${patient.weightKg} ${if (isRu) "кг" else "kg"}" else "_______"
                                val pHeight = if (patient.heightCm.isNotBlank()) "${patient.heightCm} ${if (isRu) "см" else "cm"}" else "_______"
                                val pType = localizeDiabetesType(patient.diabetesType, isRu)
                                val pDur = "${patient.calculatedDuration} ${if (isRu) "лет" else "yrs"}"
                                val pTherapy = localizeTherapyType(patient.therapyType, isRu)

                                val patientLine = if (isRu) {
                                    "Пациент: $pName • Возраст: $pAge • Вес: $pWeight • Рост: $pHeight • $pType ($pDur) • $pTherapy"
                                } else {
                                    "Patient: $pName • Age: $pAge • Weight: $pWeight • Height: $pHeight • $pType ($pDur) • $pTherapy"
                                }
                                Text(
                                    text = patientLine,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569)
                                )

                                val atEmoji = if (statistics.activeTimePercent >= 90.0) "🟢" else if (statistics.activeTimePercent >= 70.0) "🟡" else "🔴"
                                val periodLine = if (isRu) {
                                    "Период: $periodLabel ($dateRangeStr) • $atEmoji Активное время: ${String.format(Locale.US, "%.1f%%", statistics.activeTimePercent)} • Дней: ${statistics.daysCount} • Точек: ${statistics.totalCount}"
                                } else {
                                    "Period: $periodLabel ($dateRangeStr) • $atEmoji Active CGM Time: ${String.format(Locale.US, "%.1f%%", statistics.activeTimePercent)} • Days: ${statistics.daysCount} • Readings: ${statistics.totalCount}"
                                }
                                Text(
                                    text = periodLine,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569)
                                )

                                val genDateFmt = if (isRu) SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("ru")) else SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)
                                val genLine = if (isRu) {
                                    "Сформировано: ${genDateFmt.format(Date())} | Движок TIRUp"
                                } else {
                                    "Generated: ${genDateFmt.format(Date())} | TIRUp Engine"
                                }
                                Text(
                                    text = genLine,
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // 2. Metrics & Stats Boxes (Two Columns)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Left Box: Time in Ranges
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = if (isRu) "ВРЕМЯ В ДИАПАЗОНАХ (TIR/TING)" else "TIME IN RANGES (TIR/TING)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )

                                    val rangeLabels = if (isMmol) {
                                        listOf(
                                            if (isRu) "Очень высокий (≥14.0)" else "Very High (≥14.0)",
                                            if (isRu) "Высокий (10.1 - 13.9)" else "High (10.1 - 13.9)",
                                            if (isRu) "В целевом (3.9 - 10.0)" else "Target Range (3.9 - 10.0)",
                                            if (isRu) "В узком (3.9 - 7.8)" else "Tight Range (3.9 - 7.8)",
                                            if (isRu) "Низкий (3.0 - 3.8)" else "Low (3.0 - 3.8)",
                                            if (isRu) "Очень низкий (<3.0)" else "Very Low (<3.0)"
                                        )
                                    } else {
                                        listOf(
                                            if (isRu) "Очень высокий (≥250)" else "Very High (≥250)",
                                            if (isRu) "Высокий (180 - 250)" else "High (180 - 250)",
                                            if (isRu) "В целевом (70 - 180)" else "Target Range (70 - 180)",
                                            if (isRu) "В узком (70 - 140)" else "Tight Range (70 - 140)",
                                            if (isRu) "Низкий (54 - 69)" else "Low (54 - 69)",
                                            if (isRu) "Очень низкий (<54)" else "Very Low (<54)"
                                        )
                                    }

                                    ReportRangeBarItem(rangeLabels[0], statistics.tarVeryHighPercent, Color(0xFFEF4444))
                                    ReportRangeBarItem(rangeLabels[1], statistics.tarHighPercent, Color(0xFFF59E0B))
                                    ReportRangeBarItem(rangeLabels[2], statistics.tirPercent, Color(0xFF10B981))
                                    ReportRangeBarItem(rangeLabels[3], statistics.tingPercent, Color(0xFF14B8A6))
                                    ReportRangeBarItem(rangeLabels[4], statistics.tbrLowPercent, Color(0xFFF59E0B))
                                    ReportRangeBarItem(rangeLabels[5], statistics.tbrVeryLowPercent, Color(0xFFEF4444))
                                }
                            }

                            // Right Box: Statistics (Without duplicated Active Time)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.weight(1.15f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = if (isRu) "СТАТИСТИКА ГЛЮКОЗЫ И ЦЕЛИ" else "GLUCOSE STATISTICS & TARGETS",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )

                                    val meanStr = if (isMmol) {
                                        String.format(Locale.US, "%.1f %s (%s %.1f)", statistics.meanMmol, if (isRu) "ммоль/л" else "mmol/L", if (isRu) "Медиана" else "Median", statistics.medianMmol)
                                    } else {
                                        String.format(Locale.US, "%d %s (%s %d)", (statistics.meanMmol * 18.0182).toInt(), if (isRu) "мг/дл" else "mg/dL", if (isRu) "Медиана" else "Median", (statistics.medianMmol * 18.0182).toInt())
                                    }
                                    val sdStr = if (isMmol) {
                                        String.format(Locale.US, "%.2f", statistics.sdMmol)
                                    } else {
                                        String.format(Locale.US, "%d", (statistics.sdMmol * 18.0182).toInt())
                                    }

                                    ReportStatLine(if (isRu) "Средний сахар (Mean):" else "Average Glucose (Mean):", meanStr)
                                    ReportStatLine(if (isRu) "Вариабельность (%CV):" else "Glucose Variability (%CV):", String.format(Locale.US, "%.1f%% (%s ≤36.0%%) • SD: %s", statistics.cvPercent, if (isRu) "Цель" else "Target", sdStr))
                                    ReportStatLine(if (isRu) "Расчётный eA1c (GMI):" else "Estimated A1c (eA1c):", String.format(Locale.US, "%.1f%% (%d mmol/mol)", statistics.gmiPercent, statistics.hba1cMmolMol))
                                    ReportStatLine(if (isRu) "Индекс риска GRI:" else "Glycemia Risk Index (GRI):", String.format(Locale.US, "%.1f (%s, %s ≤40.0)", statistics.gri, statistics.griLabel, if (isRu) "цель" else "target"))
                                    ReportStatLine(if (isRu) "Индексы GVI / PGS:" else "Variability Indexes (GVI/PGS):", String.format(Locale.US, "GVI %.2f (≤1.20) • PGS %.1f (≤35.0)", statistics.gvi, statistics.pgs))
                                    val nStart = userSettings.nightStartHour
                                    val nEnd = userSettings.nightEndHour
                                    val nightStr = if (statistics.nightStability.isStable) {
                                        if (isRu) "Стабильный (TIR ${String.format(Locale.US, "%.0f%%", statistics.nightStability.tirPercent)})"
                                        else "Stable (TIR ${String.format(Locale.US, "%.0f%%", statistics.nightStability.tirPercent)})"
                                    } else {
                                        if (isRu) "Колебания" else "Fluctuations"
                                    }
                                    ReportStatLine(if (isRu) "Ночной профиль (${String.format(Locale.US, "%02d:00", nStart)}–${String.format(Locale.US, "%02d:00", nEnd)}):" else "Night Sleep Profile (${String.format(Locale.US, "%02d:00", nStart)}–${String.format(Locale.US, "%02d:00", nEnd)}):", nightStr)
                                }
                            }
                        }
                    }

                    // 3. Modal AGP Chart
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isRu) "СВОДНЫЙ СУТОЧНЫЙ ПРОФИЛЬ AGP (НАЛОЖЕНИЕ ${statistics.daysCount} ДНЕЙ ЗА 24 ЧАСА)" else "AMBULATORY GLUCOSE PROFILE (OVERLAY OF ${statistics.daysCount} DAYS / 24 HOURS)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (isRu) "Медиана 50% (зелёная линия), 25–75% диапазон и 10–90% перцентильное облако" else "50% Median (green line), 25–75% interquartile range and 10–90% percentile cloud",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B)
                                )

                                AgpChart(
                                    bins = agpBins,
                                    targetRanges = userSettings.targetRanges,
                                    unit = userSettings.unit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                )
                            }
                        }
                    }

                    // 4. Clinical Assessment & Notes with Status Icons
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isRu) "КЛИНИЧЕСКАЯ ОЦЕНКА" else "CLINICAL EVALUATION",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                statistics.clinicalSummary.evaluatedMetrics.forEach { item ->
                                    val itemColor = if (item.isMet) Color(0xFF059669) else if (item.isWarning) Color(0xFFD97706) else Color(0xFFDC2626)
                                    Text(
                                        text = "${item.symbol} ${item.title}: ${item.valueStr} (${item.targetStr})",
                                        fontSize = 10.5.sp,
                                        color = itemColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRu) "• Заключение: ${statistics.clinicalSummary.overallStatus}" else "• Conclusion: ${statistics.clinicalSummary.overallStatus}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF334155)
                                )

                                Text(
                                    text = if (isRu) "• Рекомендация: ${statistics.clinicalSummary.recommendation}" else "• Recommendation: ${statistics.clinicalSummary.recommendation}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isRu) "TIRUp Ambulatory Glucose Profile Report • Сгенерировано для консультаций и самоконтроля • Telegram: @diakia" else "TIRUp Ambulatory Glucose Profile Report • Generated for clinical consultations and self-monitoring • Telegram: @diakia",
                                    fontSize = 8.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Modal Bottom Action Buttons (Save PDF & Share)
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSavePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ActionBlue,
                                contentColor = Color.White
                            ),
                            enabled = !isGenerating
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isRu) "Сохранить" else "Save PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ActionBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ActionBlue
                            ),
                            enabled = !isGenerating
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isRu) "Поделиться" else "Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRangeBarItem(
    label: String,
    pct: Double,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 9.sp, color = Color(0xFF64748B))
            Text(text = String.format(Locale.US, "%.1f%%", pct), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((pct.toFloat() / 100f).coerceIn(0.01f, 1f))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun ReportStatLine(
    title: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, fontSize = 8.5.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
    }
}
