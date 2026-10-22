package com.tirup.app.presentation.reports

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.calculator.SensorAccuracyGrade
import com.tirup.app.domain.calculator.SensorVerificationResult
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun SensorVerificationCard(
    verification: SensorVerificationResult,
    isRu: Boolean,
    modifier: Modifier = Modifier,
    isLightMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val gradeColor = when (verification.accuracyGrade) {
        SensorAccuracyGrade.EXCELLENT -> PrimaryEmerald
        SensorAccuracyGrade.GOOD -> if (isLightMode) Color(0xFF0284C7) else Color(0xFF38BDF8)
        SensorAccuracyGrade.MODERATE -> Color(0xFFF59E0B)
        SensorAccuracyGrade.POOR -> Color(0xFFEF4444)
    }

    val gradeLabel = if (isRu) verification.accuracyGrade.labelRu else verification.accuracyGrade.labelEn
    val sign = if (verification.meanBiasMmol >= 0) "+" else ""

    val blueAccent = if (isLightMode) ActionBlue else Color(0xFF38BDF8)
    val cardBg = if (isLightMode) Color(0xFFF0F9FF) else ActionBlue.copy(alpha = 0.12f)
    val cardBorder = if (isLightMode) Color(0xFF7DD3FC).copy(alpha = 0.7f) else ActionBlue.copy(alpha = 0.40f)
    val badgeBg = if (isLightMode) Color(0xFFE0F2FE) else ActionBlue.copy(alpha = 0.10f)
    val badgeBorder = if (isLightMode) Color(0xFF7DD3FC).copy(alpha = 0.5f) else ActionBlue.copy(alpha = 0.25f)
    val titleColor = if (isLightMode) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (isLightMode) Color(0xFF475569) else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (isLightMode) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface

    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Title, Grade Badge & Info Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🎯", fontSize = 14.sp)
                    Text(
                        text = if (isRu) "Верификация сенсора (ISO 15197)" else "Sensor Verification (ISO 15197)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = titleColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = gradeColor.copy(alpha = if (isLightMode) 0.12f else 0.18f),
                        border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = gradeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = gradeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    if (onClick != null) {
                        Surface(
                            shape = CircleShape,
                            color = ActionBlue.copy(alpha = if (isLightMode) 0.12f else 0.15f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = if (isRu) "Справка" else "Help",
                                    tint = ActionBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3 Stat Badges: MARD, Bias, ISO Pass
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // MARD
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                    border = BorderStroke(0.8.dp, badgeBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtextColor
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", verification.mardPercent)}%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = blueAccent
                        )
                    }
                }

                // Mean Bias
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                    border = BorderStroke(0.8.dp, badgeBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isRu) "Смещение" else "Bias",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtextColor
                        )
                        Text(
                            text = "$sign${String.format(Locale.US, "%.2f", verification.meanBiasMmol)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = valueColor
                        )
                    }
                }

                // ISO 15197 Pass %
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                    border = BorderStroke(0.8.dp, badgeBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ISO 15197",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtextColor
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", verification.isoPassPercent)}%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (verification.isoPassPercent >= 95.0) PrimaryEmerald else blueAccent
                        )
                    }
                }
            }

            // Footer Subtitle with pairs count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRu) {
                        "${verification.pairedPointsCount} парных замеров (окно ±7 мин)"
                    } else {
                        "${verification.pairedPointsCount} paired checks (±7m window)"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = subtextColor
                )

                if (onClick != null) {
                    Text(
                        text = if (isRu) "Справка ➔" else "Help ➔",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = blueAccent
                    )
                }
            }
        }
    }
}

@Composable
fun SensorAccuracyInfoDialog(
    onDismiss: () -> Unit,
    isRu: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Верификация сенсора (ISO & MARD)" else "Sensor Verification (ISO & MARD)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: MARD
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isRu) "1. Что такое MARD?" else "1. What is MARD?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRu) {
                            "MARD (Mean Absolute Relative Difference) — средняя абсолютная относительная разница между показаниями сенсора CGM и контрольными измерениями глюкометра по капле крови.\n" +
                            "Это главный международный эталон оценки точности сенсоров непрерывного мониторинга глюкозы.\n\n" +
                            "Клинические критерии MARD:\n" +
                            "• < 10.0% — Превосходная точность (уровень заводской калибровки современных CGM)\n" +
                            "• 10.0% – 14.0% — Хорошая клиническая точность\n" +
                            "• 14.0% – 18.0% — Приемлемая точность (рекомендуется перепроверка перед болюсом)\n" +
                            "• > 18.0% — Низкая точность (возможен сдвиг калибровки, перегиб усика датчика или ночное прижатие)"
                        } else {
                            "MARD (Mean Absolute Relative Difference) is the average absolute relative discrepancy between CGM interstitial glucose readings and reference capillary blood glucose measurements.\n" +
                            "It serves as the global clinical gold standard for continuous glucose monitor accuracy.\n\n" +
                            "Clinical Accuracy Scale:\n" +
                            "• < 10.0% — Excellent Accuracy (factory-calibrated CGM benchmark)\n" +
                            "• 10.0% – 14.0% — Good Clinical Accuracy\n" +
                            "• 14.0% – 18.0% — Moderate Accuracy (fingerstick verification recommended before boluses)\n" +
                            "• > 18.0% — Low Accuracy (calibration drift, sensor filament issue, or compression)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Section 2: Bias
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isRu) "2. Системное смещение (Mean Bias)" else "2. Systemic Bias (Mean Bias)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRu) {
                            "Показывает направленную разницу между сенсором и глюкометром:\n" +
                            "• Знак «+» (положительное смещение) — сенсор систематически завышает показатели по сравнению с глюкометром.\n" +
                            "• Знак «-» (отрицательное смещение) — сенсор систематически занижает сахар."
                        } else {
                            "Shows directional difference between CGM and meter:\n" +
                            "• '+' (positive bias) — sensor systematically reads higher than capillary meter.\n" +
                            "• '-' (negative bias) — sensor systematically reads lower than capillary meter."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Section 3: ISO 15197:2013 & Interpretation
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isRu) "3. Клинический стандарт ISO 15197:2013" else "3. ISO 15197:2013 Standard",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRu) {
                            "Международный стандарт точности систем мониторинга глюкозы (адаптирован для проверки CGM):\n" +
                            "• При сахаре < 5.55 ммоль/л (100 мг/дл): расхождение сенсора с глюкометром не более ±0.83 ммоль/л (±15 мг/дл).\n" +
                            "• При сахаре ≥ 5.55 ммоль/л: расхождение не более ±15% от замера глюкометра.\n\n" +
                            "Клиническая интерпретация допуска ISO:\n" +
                            "• ≥ 95.0% — Эталон стандарта: наивысшая надёжность, болюсы можно вводить по сенсору.\n" +
                            "• 70.0% – 94.0% — Приемлемая точность для повседневного контроля трендов.\n" +
                            "• < 70.0% — Значительная часть замеров выходит за допустимый коридор. При расчёте болюса и подозрении на гипогликемию обязательна перепроверка по пальцу."
                        } else {
                            "International accuracy standard for glucose monitoring systems:\n" +
                            "• Glucose < 5.55 mmol/L (100 mg/dL): deviation within ±0.83 mmol/L (±15 mg/dL).\n" +
                            "• Glucose ≥ 5.55 mmol/L: deviation within ±15%.\n\n" +
                            "Clinical Interpretation:\n" +
                            "• ≥ 95.0% — Gold Standard: sensor is clinically proven safe for non-adjunctive insulin dosing.\n" +
                            "• 70.0% – 94.0% — Fair accuracy for trend observation.\n" +
                            "• < 70.0% — Substantial deviation from reference. Fingerstick check is required before dosing or treating hypos."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Section 4: Sample Size & Low Pair Count Impact
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isRu) "4. Почему процент ISO может быть около 50%? Влияние выборки" else "4. Why is ISO ~50%? Sample Size Impact",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRu) {
                            "• Малое количество парных замеров: при 10–15 замерах (или меньше) каждое единичное несовпадение резко роняет процент. Если из 12 пар всего 5–6 точек вышли за коридор (например, из-за скрытого лага или калибровки), показатель составит ровно 50%.\n" +
                            "• В клинических испытаниях (FDA/CE) стандарт ISO оценивают на сотнях измерений за полный срок службы датчика. При небольшом числе домашних замеров процент ISO имеет низкую статистическую мощность.\n" +
                            "• Погрешность самого глюкометра (Error Stacking): домашний глюкометр тоже имеет допуск ±15% по ISO 15197. Если глюкометр ошибся на +9%, а сенсор на -8%, суммарная разница составит 17% (точка не зачтётся), хотя оба прибора технически исправны.\n" +
                            "• При малом числе замеров ориентируйтесь в первую очередь на MARD и среднее смещение (Bias)."
                        } else {
                            "• Low paired points count: with 10–15 pairs or fewer, single outliers drastically depress the pass percentage. In 12 pairs, just 5–6 misses drop the score to 50%.\n" +
                            "• In official clinical trials (FDA/CE), ISO compliance is measured on hundreds of points across multiple days. A handful of home checks has low statistical power.\n" +
                            "• Reference meter error stacking: the handheld meter itself has up to ±15% allowable error under ISO 15197. If the meter is +9% high and CGM is -8% low, their paired discrepancy is 17% (failing ISO), even though both devices are operating normally.\n" +
                            "• For small datasets, prioritize MARD and Mean Bias as more robust metrics."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Section 5: Physiology & Filtering
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isRu) "5. Физиология и алгоритм фильтрации TIRUp" else "5. Physiology & TIRUp Filtration Algorithm",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRu) {
                            "• Сенсор измеряет глюкозу в межтканевой (интерстициальной) жидкости под кожей, а не в цельной крови. У современных сенсоров средний лаг составляет около 3–6 минут, но у части измерений он превышает 7 минут, а при быстрых изменениях глюкозы может доходить до 10 минут и более.\n" +
                            "• Окно сопоставления ±7 минут — международный клинический стандарт протоколов испытаний (FDA / CLSI POCT05). Оно подбирает ближайшую точку сенсора при 5-минутном шаге с запасом на рассинхрон часов.\n" +
                            "• Фильтрация динамики: зоны быстрого изменения сахара (> 0.12 ммоль/л/мин) автоматически исключаются алгоритмом TIRUp, чтобы не штрафовать исправный сенсор за естественное запаздывание диффузии в моменты скачков."
                        } else {
                            "• Sensors measure interstitial fluid glucose, not capillary blood. In modern CGMs, the median physiological lag is about 3–6 minutes, but for a subset of readings it exceeds 7 minutes, and during rapid glucose excursions it can reach 10 minutes or more.\n" +
                            "• The ±7-minute matching window is an international clinical trial benchmark (FDA / CLSI POCT05), pairing closest points in 5-minute sensor grids with tolerance for clock drift.\n" +
                            "• Dynamic filtration: rapid rate-of-change periods (> 0.12 mmol/L/min) are automatically excluded by TIRUp so the sensor is not penalized for natural diffusion lag during spikes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isRu) "Понятно" else "Got it", color = ActionBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}
