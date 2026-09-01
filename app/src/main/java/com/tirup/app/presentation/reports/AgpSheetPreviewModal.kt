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
import com.tirup.app.domain.model.UserSettings
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
                // Modal Top Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Предпросмотр бланка AGP",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
                                    text = "АМБУЛАТОРНЫЙ ГЛЮКОЗНЫЙ ПРОФИЛЬ (AGP)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                val pName = if (patient.fullName.isNotBlank()) patient.fullName else "___________________________________"
                                val pAge = if (patient.fullName.isNotBlank() || patient.birthYear != 1990) "${patient.calculatedAge} лет" else "_______"
                                val pWeight = if (patient.weightKg.isNotBlank()) "${patient.weightKg} кг" else "_______"
                                val pHeight = if (patient.heightCm.isNotBlank()) "${patient.heightCm} см" else "_______"
                                val pType = patient.diabetesType
                                val pDur = "${patient.calculatedDuration} лет"
                                val pTherapy = patient.therapyType

                                Text(
                                    text = "Пациент: $pName • Возраст: $pAge • Вес: $pWeight • Рост: $pHeight • $pType ($pDur) • $pTherapy",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569)
                                )

                                val atEmoji = if (statistics.activeTimePercent >= 90.0) "🟢" else if (statistics.activeTimePercent >= 70.0) "🟡" else "🔴"
                                Text(
                                    text = "Период: $periodLabel ($dateRangeStr) • $atEmoji Активное время: ${String.format(Locale.US, "%.1f%%", statistics.activeTimePercent)} • Дней: ${statistics.daysCount} • Точек: ${statistics.totalCount}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569)
                                )

                                Text(
                                    text = "Сформировано: ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("ru")).format(Date())} | Движок TIRUp",
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
                                        text = "ВРЕМЯ В ДИАПАЗОНАХ (TIR/TING)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )

                                    ReportRangeBarItem("Очень высокий (≥14.0)", statistics.tarVeryHighPercent, Color(0xFFEF4444))
                                    ReportRangeBarItem("Высокий (10.1 - 13.9)", statistics.tarHighPercent, Color(0xFFF59E0B))
                                    ReportRangeBarItem("В целевом (3.9 - 10.0)", statistics.tirPercent, Color(0xFF10B981))
                                    ReportRangeBarItem("В узком (3.9 - 7.8)", statistics.tingPercent, Color(0xFF14B8A6))
                                    ReportRangeBarItem("Низкий (3.0 - 3.8)", statistics.tbrLowPercent, Color(0xFFF59E0B))
                                    ReportRangeBarItem("Очень низкий (<3.0)", statistics.tbrVeryLowPercent, Color(0xFFEF4444))
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
                                        text = "СТАТИСТИКА ГЛЮКОЗЫ И ЦЕЛИ",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )

                                    ReportStatLine("Средний сахар (Mean):", String.format(Locale.US, "%.1f ммоль/л (Медиана %.1f)", statistics.meanMmol, statistics.medianMmol))
                                    ReportStatLine("Вариабельность (%CV):", String.format(Locale.US, "%.1f%% (Цель ≤36.0%%) • SD: %.2f", statistics.cvPercent, statistics.sdMmol))
                                    ReportStatLine("Расчётный eA1c (GMI):", String.format(Locale.US, "%.1f%% (%d mmol/mol)", statistics.gmiPercent, statistics.hba1cMmolMol))
                                    ReportStatLine("Индекс риска GRI:", String.format(Locale.US, "%.1f (%s, цель ≤40.0)", statistics.gri, statistics.griLabel))
                                    ReportStatLine("Индексы GVI / PGS:", String.format(Locale.US, "GVI %.2f (≤1.20) • PGS %.1f (≤35.0)", statistics.gvi, statistics.pgs))
                                    val nStart = userSettings.nightStartHour
                                    val nEnd = userSettings.nightEndHour
                                    val nightStr = if (statistics.nightStability.isStable) "Стабильный (TIR ${String.format(Locale.US, "%.0f%%", statistics.nightStability.tirPercent)})" else "Колебания"
                                    ReportStatLine("Ночной профиль (${String.format(Locale.US, "%02d:00", nStart)}–${String.format(Locale.US, "%02d:00", nEnd)}):", nightStr)
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
                                    text = "СВОДНЫЙ СУТОЧНЫЙ ПРОФИЛЬ AGP (НАЛОЖЕНИЕ ${statistics.daysCount} ДНЕЙ ЗА 24 ЧАСА)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Медиана 50% (зелёная линия), 25–75% диапазон и 10–90% перцентильное облако",
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
                                    text = "КЛИНИЧЕСКАЯ ОЦЕНКА",
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
                                    text = "• Заключение: ${statistics.clinicalSummary.overallStatus}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF334155)
                                )

                                Text(
                                    text = "• Рекомендация: ${statistics.clinicalSummary.recommendation}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
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
                                containerColor = PrimaryEmerald,
                                contentColor = Color.Black
                            ),
                            enabled = !isGenerating
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Сохранить PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PrimaryEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF059669)
                            ),
                            enabled = !isGenerating
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Поделиться", fontWeight = FontWeight.Bold)
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
