package com.tirup.app.domain.model

import java.util.Calendar

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class BmiCategory(val labelRu: String, val labelEn: String) {
    UNDERWEIGHT("Дефицит массы", "Underweight"),
    NORMAL("Норма", "Normal weight"),
    OVERWEIGHT("Избыточный вес", "Overweight"),
    OBESE_1("Ожирение I ст.", "Obesity Class I"),
    OBESE_2_3("Ожирение II–III ст.", "Severe Obesity"),
    PEDIATRIC_OBESE("Ожирение (≥95 перцентиля)", "Obesity (≥95th pct)");

    companion object {
        fun fromBmi(bmi: Double, age: Int = 30, gender: String = "M"): BmiCategory {
            if (age in 2..17) {
                val isFemale = gender.equals("F", ignoreCase = true)
                val cutoffs = if (isFemale) {
                    when (age) {
                        2 -> Triple(14.4, 18.0, 19.1)
                        3 -> Triple(14.0, 17.2, 18.1)
                        4 -> Triple(13.7, 16.8, 17.7)
                        5 -> Triple(13.5, 16.9, 18.2)
                        6 -> Triple(13.5, 17.2, 18.8)
                        7 -> Triple(13.6, 17.7, 19.6)
                        8 -> Triple(13.8, 18.3, 20.7)
                        9 -> Triple(14.0, 19.1, 21.8)
                        10 -> Triple(14.4, 20.0, 23.0)
                        11 -> Triple(14.8, 20.9, 24.1)
                        12 -> Triple(15.3, 21.7, 25.2)
                        13 -> Triple(15.8, 22.6, 26.2)
                        14 -> Triple(16.3, 23.3, 27.2)
                        15 -> Triple(16.7, 24.0, 28.0)
                        16 -> Triple(17.1, 24.5, 28.7)
                        else -> Triple(17.3, 24.9, 29.3)
                    }
                } else {
                    when (age) {
                        2 -> Triple(14.8, 18.2, 19.3)
                        3 -> Triple(14.4, 17.5, 18.3)
                        4 -> Triple(14.0, 16.9, 17.8)
                        5 -> Triple(13.8, 16.8, 17.9)
                        6 -> Triple(13.7, 17.0, 18.4)
                        7 -> Triple(13.7, 17.4, 19.2)
                        8 -> Triple(13.8, 18.1, 20.1)
                        9 -> Triple(14.0, 18.8, 21.1)
                        10 -> Triple(14.2, 19.6, 22.2)
                        11 -> Triple(14.6, 20.3, 23.2)
                        12 -> Triple(15.0, 21.1, 24.2)
                        13 -> Triple(15.5, 21.9, 25.2)
                        14 -> Triple(16.0, 22.7, 26.0)
                        15 -> Triple(16.6, 23.5, 26.8)
                        16 -> Triple(17.1, 24.2, 27.5)
                        else -> Triple(17.7, 24.9, 28.3)
                    }
                }

                return when {
                    bmi < cutoffs.first -> UNDERWEIGHT
                    bmi < cutoffs.second -> NORMAL
                    bmi < cutoffs.third -> OVERWEIGHT
                    else -> PEDIATRIC_OBESE
                }
            }

            return when {
                bmi < 18.5 -> UNDERWEIGHT
                bmi < 25.0 -> NORMAL
                bmi < 30.0 -> OVERWEIGHT
                bmi < 35.0 -> OBESE_1
                else -> OBESE_2_3
            }
        }
    }
}

data class PatientProfile(
    val fullName: String = "",
    val gender: String = "M", // "M" or "F"
    val birthYear: Int = 1990,
    val birthMonth: Int = 1,
    val heightCm: String = "",
    val weightKg: String = "",
    val diabetesType: String = "СД1", // "СД1", "СД2", "LADA", "MODY", "ГСД"
    val diagnosisYear: Int = 2018,
    val therapyType: String = "Инсулиновая помпа" // "Инсулиновая помпа", "Шприц-ручки (МДИ)", "Таблетки", "Диета"
) {
    val initials: String
        get() {
            val parts = fullName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> ""
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }

    val calculatedAge: Int
        get() {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            return (currentYear - birthYear).coerceAtLeast(0)
        }

    val calculatedDuration: Int
        get() {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            return (currentYear - diagnosisYear).coerceAtLeast(0)
        }

    val calculatedBmi: Double?
        get() {
            val hM = heightCm.toDoubleOrNull()?.let { it / 100.0 }
            val wKg = weightKg.toDoubleOrNull()
            return if (hM != null && wKg != null && hM > 0.5) wKg / (hM * hM) else null
        }
}

data class UserSettings(
    val language: String = "RU", // "RU" or "EN"
    val unit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val targetMode: TargetMode = TargetMode.TIR,
    val targetRanges: TargetRanges = TargetRanges(),
    val periodDays: Int = 14,
    val nightStartHour: Int = 0,
    val nightEndHour: Int = 6,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val patientProfile: PatientProfile = PatientProfile(),
    val isAutoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val hasSeenOnboarding: Boolean = false,
    val alertSettings: AlertSettings = AlertSettings()
)

fun localizeTherapyType(therapy: String, isRu: Boolean): String {
    return when (therapy.trim()) {
        "Инсулиновая помпа", "Insulin Pump" -> if (isRu) "Инсулиновая помпа" else "Insulin Pump"
        "Шприц-ручки (МДИ)", "Multiple Daily Injections (MDI)" -> if (isRu) "Шприц-ручки (МДИ)" else "Multiple Daily Injections (MDI)"
        "Пероральные препараты (Таблетки)", "Oral Medication (Pills)" -> if (isRu) "Пероральные препараты (Таблетки)" else "Oral Medication (Pills)"
        "Диетотерапия", "Diet Therapy" -> if (isRu) "Диетотерапия" else "Diet Therapy"
        else -> therapy
    }
}

fun localizeDiabetesType(diabetes: String, isRu: Boolean): String {
    return when (diabetes.trim()) {
        "СД1", "T1D" -> if (isRu) "СД1" else "T1D"
        "СД2", "T2D" -> if (isRu) "СД2" else "T2D"
        "ГСД", "GDM" -> if (isRu) "ГСД" else "GDM"
        "LADA" -> "LADA"
        "MODY" -> "MODY"
        else -> diabetes
    }
}

