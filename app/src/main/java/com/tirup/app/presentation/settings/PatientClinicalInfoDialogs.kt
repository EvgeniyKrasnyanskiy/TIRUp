package com.tirup.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
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
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun BmiDetailInfoDialog(
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = ActionBlue.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ActionBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isRu) "ИМТ (Индекс Кетле)" else "Body Mass Index (BMI)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRu) "Клинический справочник ВОЗ и ISPAD" else "WHO & ISPAD Clinical Guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Definition & Formula
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Что такое ИМТ и формула расчёта" else "Definition & Formula",
                        badge = "кг/м²"
                    ) {
                        Text(
                            text = if (isRu)
                                "Индекс массы тела (индекс Кетле) — общепринятый показатель соответствия массы человека и его роста:\n\n" +
                                        "• Формула: ИМТ = Вес (кг) / [Рост (м)]²\n\n" +
                                        "Позволяет оценить дефицит, норму или степень избытка массы для выбора терапевтических целей при диабете."
                            else
                                "Body Mass Index (BMI / Quetelet index) is an internationally recognized metric of weight-for-height:\n\n" +
                                        "• Formula: BMI = Weight (kg) / [Height (m)]²\n\n" +
                                        "Used to assess underweight, normal weight, and obesity to optimize diabetes care goals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 2: WHO Adult Scale
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Классификация ВОЗ для взрослых (18+ лет)" else "WHO Adult Classification (18+)",
                        badge = if (isRu) "Взрослые" else "Adults"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BmiGradeRow("< 18.5", if (isRu) "Дефицит массы тела" else "Underweight", ActionBlue)
                            BmiGradeRow("18.5 – 24.9", if (isRu) "Нормальная масса" else "Normal weight", PrimaryEmerald)
                            BmiGradeRow("25.0 – 29.9", if (isRu) "Избыточный вес (предожирение)" else "Overweight", ColorHigh)
                            BmiGradeRow("30.0 – 34.9", if (isRu) "Ожирение I степени" else "Obesity Class I", ColorVeryHigh)
                            BmiGradeRow("≥ 35.0", if (isRu) "Ожирение II–III степени (морбидное)" else "Severe / Class II–III Obesity", ColorVeryHigh)
                        }
                    }
                }

                // Section 3: Pediatric Percentiles
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Особенности у детей и подростков (2–17 лет)" else "Pediatric Features (Ages 2–17)",
                        badge = if (isRu) "ISPAD / ВОЗ" else "ISPAD / WHO"
                    ) {
                        Text(
                            text = if (isRu)
                                "У детей фиксированные границы (18.5 и 25) НЕ применяются, так как тело активно растёт и меняются пропорции.\n\n" +
                                        "Оценка проводится по перцентильным кривым ВОЗ (отдельно для мальчиков и девочек по годам):\n" +
                                        "• < 5-го перцентиля: Дефицит массы\n" +
                                        "• 5-й – 85-й перцентиль: Норма для данного возраста\n" +
                                        "• 85-й – 95-й перцентиль: Избыточный вес\n" +
                                        "• ≥ 95-го перцентиля: Педиатрическое ожирение"
                            else
                                "Fixed cutoffs (18.5 and 25) CANNOT be used for children due to rapid growth and changing body composition.\n\n" +
                                        "Assessment uses WHO age- and sex-specific growth percentiles (z-scores):\n" +
                                        "• < 5th percentile: Underweight\n" +
                                        "• 5th – 85th percentile: Normal healthy weight\n" +
                                        "• 85th – 95th percentile: Overweight\n" +
                                        "• ≥ 95th percentile: Pediatric Obesity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 4: Significance for Diabetes
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Значение ИМТ для компенсации диабета" else "Clinical Impact on Diabetes",
                        badge = if (isRu) "Инсулин" else "Insulin"
                    ) {
                        Text(
                            text = if (isRu)
                                "• Инсулинорезистентность: при повышенном ИМТ висцеральный жир снижает чувствительность рецепторов, что требует повышенных доз инсулина (высокий углеводный коэффициент УК и фактор чувствительности ФЧИ).\n\n" +
                                        "• Дефицит массы: повышает риск стремительных ночных гипогликемий из-за сниженного депо гликогена в печени.\n\n" +
                                        "• Ограничение метода: ИМТ не разделяет мышцы и жир (у тренированных людей показатель может быть ложно завышен)."
                            else
                                "• Insulin Resistance: excess visceral fat decreases insulin receptor sensitivity, increasing bolus ICR and basal insulin requirements.\n\n" +
                                        "• Underweight: increases risk of rapid nocturnal hypoglycemia due to depleted hepatic glycogen stores.\n\n" +
                                        "• Limitations: BMI does not differentiate between muscle mass and fat tissue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Sources
                item {
                    Text(
                        text = if (isRu)
                            "Источники: WHO Child Growth Standards, ISPAD Clinical Practice Consensus Guidelines, Алгоритмы специализированной мед. помощи больным СД (ЭНЦ РФ)."
                        else
                            "Sources: WHO Child Growth Standards, ISPAD Clinical Practice Consensus Guidelines, ADA Standards of Medical Care in Diabetes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isRu) "Понятно" else "Got it",
                    fontWeight = FontWeight.Bold,
                    color = ActionBlue
                )
            }
        }
    )
}

@Composable
fun CarbRecommendationDetailDialog(
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryEmerald.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isRu) "Суточная норма углеводов и ХЕ" else "Daily Carbohydrate & BU Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRu) "Клинические стандарты ISPAD, ADA и ЭНЦ" else "ISPAD, ADA & Endocrinology Guidelines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Brain & Physiological Minimum
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Базовый физиологический минимум" else "Physiological Brain Minimum",
                        badge = "≥ 130 г/сут"
                    ) {
                        Text(
                            text = if (isRu)
                                "Головной мозг взрослого человека и эритроциты потребляют около 120–130 г чистой глюкозы в сутки в состоянии покоя.\n\n" +
                                        "Поэтому международные консенсусы (ADA, ЭНЦ) определяют базовый физиологический минимум питания — не менее 130 г углеводов в сутки независимо от диабета."
                            else
                                "The adult brain and red blood cells consume ~120–130 g of pure glucose per day even at complete rest.\n\n" +
                                        "Therefore, international guidelines (ADA, EASD) define the baseline physiological carbohydrate intake at ≥130 g/day regardless of diabetes status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 2: What is Bread Unit (XE)
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Что такое Хлебная Единица (ХЕ)?" else "What is a Bread Unit (BU / XE)?",
                        badge = if (isRu) "1 ХЕ = 10–12 г" else "1 BU = 10–12 g"
                    ) {
                        Text(
                            text = if (isRu)
                                "Хлебная Единица (ХЕ) — общепринятая мера для оценки количества углеводов в продуктах:\n\n" +
                                        "• 1 ХЕ = 10–12 граммов чистых углеводов (в расчётах TIRUp принят стандарт 12 г).\n" +
                                        "• Используется для быстрого и точного расчёта болюса инсулина по индивидуальному углеводному коэффициенту (УК, ед/ХЕ)."
                            else
                                "A Bread Unit (BU / XE) is an internationally recognized standard for counting carbohydrates:\n\n" +
                                        "• 1 BU = 10–12 grams of pure carbohydrates (TIRUp uses the 12 g standard).\n" +
                                        "• Used to rapidly calculate insulin meal boluses based on individual Insulin-to-Carb Ratios (ICR).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 3: Pediatric Warning (ISPAD)
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ColorVeryHigh.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ColorVeryHigh.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isRu) "⚠️ Предупреждение ISPAD для детей и подростков" else "⚠️ ISPAD Warning for Children & Teens",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorVeryHigh
                            )
                            Text(
                                text = if (isRu)
                                    "Кетогенные и экстремально низкоуглеводные диеты (<130 г/сут) детям и подросткам с сахарным диабетом СТРОГО ПРОТИВОПОКАЗАНЫ:\n\n" +
                                            "1. Углеводы стимулируют синтез гормона роста и ИФР-1. Их дефицит ведёт к задержке линейного роста.\n" +
                                            "2. Риск остеопении (хрупкости костей) и субклинического кетоза.\n" +
                                            "3. Опасность эугликемического диабетического кетоацидоза (ДКА).\n\n" +
                                            "Норма углеводов у детей: 45–55% от суточного калоража (130–160 г в 2–5 лет, до 250–300 г в пубертате)."
                                else
                                    "Ketogenic and strict low-carb diets (<130 g/day) are STRICTLY CONTRAINDICATED in children and adolescents with diabetes:\n\n" +
                                            "1. Carbohydrates are mandatory for growth hormone and IGF-1 secretion; deficiency causes linear growth stunting.\n" +
                                            "2. Elevated risk of bone mineral density loss and chronic subclinical ketosis.\n" +
                                            "3. Risk of fatal euglycemic diabetic ketoacidosis (DKA).\n\n" +
                                            "Target: 45–55% of daily energy (130–160 g at ages 2–5, up to 250–300 g during puberty).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Section 4: Daily Meal Distribution
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Распределение по приёмам пищи" else "Mealtime Distribution",
                        badge = if (isRu) "Баланс" else "Balance"
                    ) {
                        Text(
                            text = if (isRu)
                                "• Завтрак (20–25%): утром физиологическая резистентность выше («феномен утренней зари»), поэтому завтрак делают умеренным по углеводам.\n\n" +
                                        "• Обед (30–35%): основной углеводный приём дня с высоким содержанием клетчатки.\n\n" +
                                        "• Ужин (25–30%): сбалансированный приём без избытка быстрых углеводов для спокойной ночи.\n\n" +
                                        "• Перекусы (10–15%): второй завтрак или полдник (по индивидуальной схеме)."
                            else
                                "• Breakfast (20–25%): physiological morning resistance (\"dawn phenomenon\") warrants moderate carb density.\n\n" +
                                        "• Lunch (30–35%): primary carb-rich meal emphasizing complex fibers.\n\n" +
                                        "• Dinner (25–30%): balanced dinner avoiding high-GI surges for nocturnal glucose stability.\n\n" +
                                        "• Snacks (10–15%): optional mid-day or evening snacks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 5: Food Quality
                item {
                    ClinicalSectionCard(
                        title = if (isRu) "Качество углеводов и клетчатка" else "Carb Quality & Dietary Fiber",
                        badge = "ГИ < 55"
                    ) {
                        Text(
                            text = if (isRu)
                                "При диабете приоритет отдаётся медленным углеводам с низким гликемическим индексом (гречка, бурый рис, бобовые, цельные злаки, овощи).\n\n" +
                                        "Клетчатка (≥30 г/сут для взрослых) формирует гелевую сетку в кишечнике, замедляет всасывание сахара и сглаживает постпрандиальные пики."
                            else
                                "Diabetes management favors low-glycemic index (<55) complex carbohydrates: buckwheat, brown rice, legumes, and whole grains.\n\n" +
                                        "Dietary fiber (≥30 g/day for adults) blunts glucose absorption kinetics and prevents postprandial glycemic spikes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Sources
                item {
                    Text(
                        text = if (isRu)
                            "Источники: ISPAD Clinical Practice Consensus Guidelines 2022/2024, American Diabetes Association (ADA) Nutrition Standards, Клинические рекомендации «Сахарный диабет 1 и 2 типа» Минздрава РФ."
                        else
                            "Sources: ISPAD Clinical Practice Consensus Guidelines 2022/2024, ADA Nutrition Therapy for Adults With Diabetes, Russian Endocrinology Research Center.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isRu) "Понятно" else "Got it",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                )
            }
        }
    )
}

@Composable
private fun ClinicalSectionCard(
    title: String,
    badge: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun BmiGradeRow(
    range: String,
    label: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accentColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = range,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}
