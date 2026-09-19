package com.tirup.app.presentation.settings.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.domain.calculator.CarbRecommendationCalculator
import com.tirup.app.domain.model.BmiCategory
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.localizeDiabetesType
import com.tirup.app.domain.model.localizeTherapyType
import com.tirup.app.domain.util.PluralUtils
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.settings.BmiDetailInfoDialog
import com.tirup.app.presentation.settings.CarbRecommendationDetailDialog
import com.tirup.app.presentation.settings.LanguageChip
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Locale

@Composable
fun PatientProfileSummaryCard(
    profile: PatientProfile,
    isRu: Boolean,
    onEditClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val hasName = profile.fullName.isNotBlank()
    val displayName = if (hasName) profile.shortName else (if (isRu) "Мой профиль" else "My Profile")

    val ageStr = if (profile.birthYear > 1900) PluralUtils.formatYears(profile.calculatedAge, isRu) else ""
    val diagStr = if (profile.diabetesType.isNotBlank()) localizeDiabetesType(profile.diabetesType, isRu) else ""
    val durStr = if (profile.calculatedDuration > 0) "${if (isRu) "стаж" else "duration"} ${profile.calculatedDuration} ${if (isRu) "л." else "y."}" else ""
    val bmi = profile.calculatedBmi
    val bmiStr = if (bmi != null) {
        val cat = BmiCategory.fromBmi(bmi, profile.calculatedAge, profile.gender)
        val bmiLabel = if (isRu) "ИМТ" else "BMI"
        String.format(Locale.US, "%s %.1f (%s)", bmiLabel, bmi, if (isRu) cat.labelRu else cat.labelEn)
    } else ""
    val carbStr = if (bmi != null) {
        val cat = BmiCategory.fromBmi(bmi, profile.calculatedAge, profile.gender)
        val carbRec = CarbRecommendationCalculator.calculate(profile.calculatedAge, profile.gender, cat)
        val xeUnit = if (isRu) "ХЕ/сут" else "BU/day"
        String.format(Locale.US, "%.0f–%.0f %s", carbRec.dailyXeRange.start, carbRec.dailyXeRange.endInclusive, xeUnit)
    } else ""

    val subtitleParts = listOf(ageStr, diagStr, durStr, bmiStr, carbStr).filter { it.isNotBlank() }
    val subtitle = if (subtitleParts.isNotEmpty()) {
        subtitleParts.joinToString(" • ")
    } else {
        if (isRu) "Нажмите для заполнения мед. профиля" else "Tap to edit clinical report profile"
    }

    val isFemale = profile.gender.equals("F", ignoreCase = true)
    val avatarBg = if (isFemale) Color(0xFFC026D3) else ActionBlue

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditClick
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasName) onSurfaceVariant else ActionBlue,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
fun PatientProfileEditDialog(
    profile: PatientProfile,
    isRu: Boolean,
    currentYear: Int,
    onProfileChange: (PatientProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var localProfile by remember(profile) { mutableStateOf(profile) }

    val hM = localProfile.heightCm.toDoubleOrNull()?.let { it / 100.0 }
    val wKg = localProfile.weightKg.toDoubleOrNull()
    val bmi = if (hM != null && wKg != null && hM > 0.5) wKg / (hM * hM) else null

    var showBmiGuide by remember { mutableStateOf(false) }
    var showCarbGuide by remember { mutableStateOf(false) }
    var isBmiExpanded by rememberSaveable { mutableStateOf(false) }
    var isCarbExpanded by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            onProfileChange(localProfile)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (localProfile.gender == "F") Color(0xFFC026D3) else ActionBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Мой профиль" else "My Profile",
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
                        value = localProfile.fullName,
                        onValueChange = { newName ->
                            localProfile = localProfile.copy(fullName = newName)
                        },
                        label = { Text(if (isRu) "ФИО пациента" else "Full Name") },
                        placeholder = { Text(if (isRu) "Фамилия Имя Отчество" else "Last First Middle") },
                        supportingText = {
                            Text(
                                if (isRu) "Для экстренных SMS берётся имя (2-е слово: Фамилия Имя Отчество)"
                                else "For short SMS, first name (2nd word) is used",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
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
                                isSelected = localProfile.gender == "M",
                                onClick = { localProfile = localProfile.copy(gender = "M") }
                            )
                            LanguageChip(
                                label = if (isRu) "Женский ♀" else "Female ♀",
                                isSelected = localProfile.gender == "F",
                                onClick = { localProfile = localProfile.copy(gender = "F") }
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
                            selectedYear = localProfile.birthYear,
                            yearRange = (currentYear - 100)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newYear ->
                                localProfile = localProfile.copy(birthYear = newYear)
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
                                Text(PluralUtils.formatYears(localProfile.calculatedAge, isRu), style = MaterialTheme.typography.bodyMedium, color = ActionBlue, fontWeight = FontWeight.Bold)
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
                            value = localProfile.heightCm,
                            onValueChange = { newHeight ->
                                localProfile = localProfile.copy(heightCm = newHeight)
                            },
                            label = { Text(if (isRu) "Рост (см)" else "Height (cm)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = localProfile.weightKg,
                            onValueChange = { newWeight ->
                                localProfile = localProfile.copy(weightKg = newWeight)
                            },
                            label = { Text(if (isRu) "Вес (кг)" else "Weight (kg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                if (bmi != null) {
                    val age = localProfile.calculatedAge
                    val isChild = age in 2..17
                    val category = BmiCategory.fromBmi(bmi, age, localProfile.gender)
                    val catColor = when (category) {
                        BmiCategory.UNDERWEIGHT -> ActionBlue
                        BmiCategory.NORMAL -> PrimaryEmerald
                        BmiCategory.OVERWEIGHT -> ColorHigh
                        BmiCategory.OBESE_1, BmiCategory.OBESE_2_3, BmiCategory.PEDIATRIC_OBESE -> ColorVeryHigh
                    }
                    val scaleNote = if (isChild) {
                        val sexStr = if (localProfile.gender == "F") (if (isRu) "девочек" else "girls") else (if (isRu) "мальчиков" else "boys")
                        val ageStr = PluralUtils.formatYears(age, isRu)
                        if (isRu) "Педиатрическая шкала ВОЗ: перцентили ($sexStr, $ageStr)"
                        else "WHO Pediatric scale: percentiles ($sexStr, $ageStr)"
                    } else {
                        if (isRu) "Шкала ВОЗ для взрослых (норма 18.5–24.9 кг/м²)"
                        else "WHO Adult scale (normal 18.5–24.9 kg/m²)"
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBmiExpanded = !isBmiExpanded }
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = if (isRu) "ИМТ:" else "BMI:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.1f (%s)", bmi, if (isRu) category.labelRu else category.labelEn),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = catColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ActionBlue.copy(alpha = 0.15f),
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable { showBmiGuide = true }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "BMI Guide",
                                                    tint = ActionBlue,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isBmiExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isBmiExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (isRu) "Точное значение:" else "Exact BMI:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = String.format(Locale.US, if (isRu) "%.2f кг/м²" else "%.2f kg/m²", bmi),
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
                    }

                    val carbRec = CarbRecommendationCalculator.calculate(
                        age = age,
                        gender = localProfile.gender,
                        bmiCategory = category
                    )
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCarbExpanded = !isCarbExpanded }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restaurant,
                                            contentDescription = null,
                                            tint = ActionBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = carbRec.formatDailySummary(isRu),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ActionBlue.copy(alpha = 0.15f),
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable { showCarbGuide = true }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Carb Guide",
                                                    tint = ActionBlue,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isCarbExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isCarbExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = if (isRu) "Ориентир по приёмам пищи:" else "Mealtime Distribution:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isRu) "• Завтрак (20–25%):" else "• Breakfast:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(carbRec.distribution.formatBreakfast(isRu), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isRu) "• Обед (30–35%):" else "• Lunch:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(carbRec.distribution.formatLunch(isRu), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isRu) "• Ужин (25–30%):" else "• Dinner:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(carbRec.distribution.formatDinner(isRu), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isRu) "• Перекусы (10–15%):" else "• Snacks:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(carbRec.distribution.formatSnacks(isRu), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }

                                        if (carbRec.clinicalWarningRu != null) {
                                            Text(
                                                text = if (isRu) carbRec.clinicalWarningRu else (carbRec.clinicalWarningEn ?: ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ColorVeryHigh,
                                                lineHeight = 14.sp
                                            )
                                        } else {
                                            Text(
                                                text = if (isRu) carbRec.clinicalRationaleRu else carbRec.clinicalRationaleEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
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
                            selectedOption = localizeDiabetesType(localProfile.diabetesType, isRu),
                            options = if (isRu) listOf("СД1", "СД2", "LADA", "MODY", "ГСД") else listOf("T1D", "T2D", "LADA", "MODY", "GDM"),
                            modifier = Modifier.weight(1f),
                            onOptionSelected = { newType ->
                                localProfile = localProfile.copy(diabetesType = newType)
                            }
                        )

                        DropdownYearSelector(
                            label = if (isRu) "Диагноз с года" else "Diagnosed Year",
                            selectedYear = localProfile.diagnosisYear,
                            yearRange = (currentYear - 60)..currentYear,
                            modifier = Modifier.weight(1f),
                            onYearSelected = { newDiagYear ->
                                localProfile = localProfile.copy(diagnosisYear = newDiagYear)
                            }
                        )
                    }
                }

                item {
                    // 5. Therapy Type Dropdown
                    DropdownChoiceSelector(
                        label = if (isRu) "Вид терапии" else "Therapy Type",
                        selectedOption = localizeTherapyType(localProfile.therapyType, isRu),
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
                            localProfile = localProfile.copy(therapyType = newTherapy)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onProfileChange(localProfile)
                    onDismiss()
                }
            ) {
                Text(
                    text = if (isRu) "Готово" else "Done",
                    fontWeight = FontWeight.Bold,
                    color = ActionBlue
                )
            }
        }
    )

    if (showBmiGuide) {
        BmiDetailInfoDialog(
            isRu = isRu,
            onDismiss = { showBmiGuide = false }
        )
    }

    if (showCarbGuide) {
        CarbRecommendationDetailDialog(
            isRu = isRu,
            onDismiss = { showCarbGuide = false }
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
